package io.github.saberbird.cidget.data

import io.github.saberbird.cidget.github.ApiError
import io.github.saberbird.cidget.github.ApiResult
import io.github.saberbird.cidget.github.FetchSettings
import io.github.saberbird.cidget.github.RepoState
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.hours

/** Fetches the state of one repo with the token [tokenId] (null = default); production calls `GitHubClient.fetchRepoState`. */
fun interface RepoFetcher {
    suspend fun fetch(tokenId: String?, owner: String, repo: String, settings: FetchSettings): ApiResult<RepoState>
}

/** What the worker should do after a refresh. */
enum class RefreshOutcome { SUCCESS, RETRY }

/**
 * One refresh of one repo (ARCHITECTURE.md §2–§3): skips while rate limited or while the token is invalid,
 * flags `refreshing` around the call, keeps the old data on errors and records the error.
 *
 * @param onChanged called with the repo after its snapshot changed, so the widgets showing it redraw.
 */
class RefreshRepository(
    private val tokens: TokenStore,
    private val snapshots: SnapshotStore,
    private val fetcher: RepoFetcher,
    /** Id of the token a repo uses (from the widget that shows it), or null for the default. */
    private val tokenIdFor: suspend (String) -> String?,
    private val clock: Clock,
    private val onChanged: suspend (repo: String) -> Unit,
) {
    /** True if [repo] may be refreshed now: a usable token exists and no rate-limit wait is running. */
    private suspend fun canRefresh(repo: String): Boolean {
        if (tokens.stateOf(tokenIdFor(repo)) != TokenState.OK) return false
        if (repo.substringBefore('/', "").isEmpty() || repo.substringAfter('/', "").isEmpty()) return false
        val limited = snapshots.get(repo)?.rateLimitedUntil
        return limited == null || limited <= clock.now()
    }

    /**
     * Called before enqueueing a manual refresh: if it can run, shows the `[...]` indicator at once by
     * setting `refreshing` and returns true. Returns false when the refresh would be skipped anyway.
     */
    suspend fun prepare(repo: String): Boolean {
        if (!canRefresh(repo)) return false
        snapshots.update(repo) { it.copy(refreshing = true) }
        onChanged(repo)
        return true
    }

    suspend fun refresh(repo: String): RefreshOutcome {
        if (!canRefresh(repo)) {
            // A manual refresh may have shown `[...]` before the token became invalid or a rate limit began.
            if (snapshots.get(repo)?.refreshing == true) {
                snapshots.update(repo) { it.copy(refreshing = false) }
                onChanged(repo)
            }
            return RefreshOutcome.SUCCESS
        }
        val owner = repo.substringBefore('/')
        val name = repo.substringAfter('/')
        val before = snapshots.get(repo)
        val start = clock.now()
        val tokenId = tokens.resolveId(tokenIdFor(repo))

        if (before?.refreshing != true) {
            // A manual refresh already shows `[...]` (prepare); only background refreshes set it here.
            snapshots.update(repo) { it.copy(refreshing = true) }
            onChanged(repo)
        }
        try {
            val skipBranches = before != null && before.branchesForbidden &&
                before.branchesForbiddenCheckedAt?.let { start - it < RECHECK_FORBIDDEN } == true
            val result = fetcher.fetch(tokenId, owner, name, FetchSettings(branchesForbidden = skipBranches))
            val now = clock.now()
            // The token file may be briefly unreadable (Keystore busy); that must not fail the refresh.
            result.meta.tokenExpiresAt?.let { runCatching { tokens.updateExpiry(tokenId, it) } }
            val limit = result.meta.rateLimit
            val reset = limit?.resetAt
            val metaLimited = if (limit?.remaining == 0 && reset != null && reset > now) reset else null
            return when (result) {
                is ApiResult.Ok -> {
                    val s = result.value
                    snapshots.update(repo) {
                        it.copy(
                            fetchedAt = now,
                            branches = s.branches,
                            headsApproximate = s.headsApproximate,
                            error = null,
                            errorAt = null,
                            rateLimitedUntil = metaLimited,
                            branchesForbidden = s.branchesForbidden,
                            branchesForbiddenCheckedAt = when {
                                !s.branchesForbidden -> null
                                skipBranches -> it.branchesForbiddenCheckedAt
                                else -> now
                            },
                        )
                    }
                    RefreshOutcome.SUCCESS
                }
                is ApiResult.Err -> {
                    val e = result.error
                    if (e == ApiError.TokenInvalid) runCatching { tokens.markInvalid(tokenId) }
                    snapshots.update(repo) {
                        it.copy(
                            error = e,
                            errorAt = now,
                            rateLimitedUntil = (e as? ApiError.RateLimited)?.until ?: metaLimited,
                        )
                    }
                    if (e == ApiError.Offline || e == ApiError.Timeout || e is ApiError.Server) RefreshOutcome.RETRY else RefreshOutcome.SUCCESS
                }
            }
        } finally {
            withContext(NonCancellable) {
                snapshots.update(repo) { it.copy(refreshing = false) }
                onChanged(repo)
            }
        }
    }

    private companion object {
        val RECHECK_FORBIDDEN = 24.hours
    }
}

