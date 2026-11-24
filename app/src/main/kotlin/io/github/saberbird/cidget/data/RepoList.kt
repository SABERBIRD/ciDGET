package io.github.saberbird.cidget.data

import io.github.saberbird.cidget.github.ApiError
import io.github.saberbird.cidget.github.ApiResult
import io.github.saberbird.cidget.github.RepoSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** A repo the user can pick, and the token that can see it (D23). Names only, no tokens. */
@Serializable
data class CachedRepo(val fullName: String, val isPrivate: Boolean, val archived: Boolean, val tokenId: String)

/** The cached list of pickable repos shown by the widget's `ls` view and the settings picker. */
@Serializable
data class RepoList(
    val repos: List<CachedRepo> = emptyList(),
    val fetchedAt: Instant? = null,
    /** Error of the last refresh; null if it succeeded. */
    val error: ApiError? = null,
    val refreshing: Boolean = false,
)

/** One JSON file `repos.json` written atomically; reads come from memory after the first load. */
class RepoListStore(
    private val file: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val state = MutableStateFlow<RepoList?>(null)

    private suspend fun loaded(): RepoList {
        state.value?.let { return it }
        return mutex.withLock {
            state.value ?: withContext(io) {
                runCatching { json.decodeFromString(RepoList.serializer(), file.readText()) }.getOrNull() ?: RepoList()
            }.also { state.value = it }
        }
    }

    suspend fun get(): RepoList = loaded()

    /** Emits the list now and after every change. */
    fun observe(): Flow<RepoList> = flow {
        loaded()
        emitAll(state.filterNotNull().distinctUntilChanged())
    }

    suspend fun update(transform: (RepoList) -> RepoList): RepoList {
        loaded()
        return mutex.withLock {
            val next = transform(state.value ?: RepoList())
            withContext(io) {
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(json.encodeToString(RepoList.serializer(), next))
                if (!tmp.renameTo(file)) {
                    file.delete()
                    check(tmp.renameTo(file)) { "cannot write repo list" }
                }
            }
            state.value = next
            next
        }
    }

    /** Drops the list (a token was removed; it may have held private names). */
    suspend fun clear() {
        mutex.withLock {
            withContext(io) { file.delete() }
            state.value = RepoList()
        }
    }
}

/** Loads the repos of every usable token into [RepoListStore]. Runs in a worker, never in the widget. */
class RepoListRepository(
    private val tokens: TokenStore,
    private val store: RepoListStore,
    private val listFor: suspend (tokenId: String) -> ApiResult<List<RepoSummary>>,
    private val clock: Clock,
    private val onChanged: suspend () -> Unit = {},
) {
    /** Marks the list as refreshing so the widget shows it at once. */
    suspend fun prepare() {
        store.update { it.copy(refreshing = true) }
        onChanged()
    }

    /** Fetches `/user/repos` for each `OK` token; a failing token keeps its old entries. */
    suspend fun refresh(): RefreshOutcome {
        val okIds = tokens.summaries.value.filter { it.state == TokenState.OK }.map { it.id }
        if (okIds.isEmpty()) {
            store.update { it.copy(refreshing = false, error = if (tokens.hasToken()) ApiError.TokenInvalid else ApiError.NoToken) }
            onChanged()
            return RefreshOutcome.SUCCESS
        }
        var firstError: ApiError? = null
        val fresh = LinkedHashMap<String, List<CachedRepo>>()
        try {
            for (id in okIds) {
                when (val r = listFor(id)) {
                    is ApiResult.Ok -> fresh[id] = r.value.map { CachedRepo(it.fullName, it.isPrivate, it.archived, id) }
                    is ApiResult.Err -> {
                        if (firstError == null) firstError = r.error
                        // Shown as `! 401 token` on that token in the settings; the other tokens keep working.
                        if (r.error == ApiError.TokenInvalid) runCatching { tokens.markInvalid(id) }
                    }
                }
            }
        } finally {
            store.update { old ->
                val kept = old.repos.filter { it.tokenId !in fresh.keys && it.tokenId in okIds }
                val merged = (fresh.values.flatten() + kept).distinctBy { it.fullName }
                old.copy(
                    repos = if (fresh.isEmpty()) old.repos else merged,
                    fetchedAt = if (fresh.isEmpty()) old.fetchedAt else clock.now(),
                    error = firstError,
                    refreshing = false,
                )
            }
            onChanged()
        }
        return when (firstError) {
            ApiError.Offline, ApiError.Timeout, is ApiError.Server -> RefreshOutcome.RETRY
            else -> RefreshOutcome.SUCCESS
        }
    }
}
