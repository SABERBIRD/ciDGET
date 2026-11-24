package io.github.saberbird.cidget.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * One JSON file per repo in [dir] (`files/snapshots/{owner}__{repo}.json`), written atomically (temp file
 * then rename). Reads come from memory after the first load, so the widget renders instantly from cache.
 * Unreadable files are skipped, never fatal.
 */
class SnapshotStore(
    private val dir: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val state = MutableStateFlow<Map<String, RepoSnapshot>?>(null)

    private suspend fun loaded(): Map<String, RepoSnapshot> {
        state.value?.let { return it }
        return mutex.withLock { state.value ?: readAll().also { state.value = it } }
    }

    private suspend fun readAll(): Map<String, RepoSnapshot> = withContext(io) {
        dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().mapNotNull { f ->
            runCatching { json.decodeFromString(RepoSnapshot.serializer(), f.readText()) }.getOrNull()
        }.associateBy { it.repo }
    }

    /** Snapshot of [repo], or null. */
    suspend fun get(repo: String): RepoSnapshot? = loaded()[repo]

    /** Emits the snapshot of [repo] (null if none) now and after every change. */
    fun observe(repo: String): Flow<RepoSnapshot?> = flow {
        loaded()
        emitAll(state.filterNotNull().map { it[repo] }.distinctUntilChanged())
    }

    /** Repos that have a snapshot. */
    suspend fun repos(): Set<String> = loaded().keys

    /** Replaces the snapshot of [snapshot].repo. */
    suspend fun put(snapshot: RepoSnapshot) {
        loaded()
        mutex.withLock {
            withContext(io) { write(snapshot) }
            state.value = state.value.orEmpty() + (snapshot.repo to snapshot)
        }
    }

    /** Applies [transform] to the current snapshot (an empty one if none) and stores the result. */
    suspend fun update(repo: String, transform: (RepoSnapshot) -> RepoSnapshot): RepoSnapshot {
        loaded()
        return mutex.withLock {
            val next = transform(state.value.orEmpty()[repo] ?: RepoSnapshot(repo))
            withContext(io) { write(next) }
            state.value = state.value.orEmpty() + (repo to next)
            next
        }
    }

    /** Deletes the snapshots of every repo not in [keep]. */
    suspend fun deleteExcept(keep: Set<String>) {
        loaded()
        mutex.withLock {
            val current = state.value.orEmpty()
            withContext(io) { current.keys.filter { it !in keep }.forEach { fileFor(it).delete() } }
            state.value = current.filterKeys { it in keep }
        }
    }

    /** Deletes every snapshot (token deleted or replaced). */
    suspend fun deleteAll() {
        mutex.withLock {
            withContext(io) { dir.listFiles().orEmpty().forEach { it.delete() } }
            state.value = emptyMap()
        }
    }

    private fun fileFor(repo: String) = File(dir, repo.replace("/", "__") + ".json")

    private fun write(snapshot: RepoSnapshot) {
        dir.mkdirs()
        val target = fileFor(snapshot.repo)
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeText(json.encodeToString(RepoSnapshot.serializer(), snapshot))
        if (!tmp.renameTo(target)) {
            target.delete()
            check(tmp.renameTo(target)) { "cannot write snapshot" }
        }
    }
}
