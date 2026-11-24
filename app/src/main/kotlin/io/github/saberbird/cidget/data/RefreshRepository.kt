package io.github.saberbird.cidget.data

import io.github.saberbird.cidget.github.ApiError
import io.github.saberbird.cidget.github.ApiResult
import io.github.saberbird.cidget.github.FetchSettings
import io.github.saberbird.cidget.github.RepoState
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.hours

/** Fetches the state of one repo; the production implementation calls `GitHubClient.fetchRepoState`. */
fun interface RepoFetcher {
    suspend fun fetch(owner: String, repo: String, settings: FetchSettings): ApiResult<RepoState>
}

/** What the worker should do after a refresh. */
enum class RefreshOutcome { SUCCESS, RETRY }

/**
 * One refresh of one repo (ARCHITECTURE.md §2–§3): skips while rate limited or while the token is invalid,
 * flags `refreshing` around the call, keeps the old data on errors and records the error.
 *
 * @param onChanged called after every snapshot change so widgets redraw.
 */
class RefreshRepository(
    private val tokens: TokenStore,
    private val snapshots: SnapshotStore,
    private val fetcher: RepoFetcher,
    private val clock: Clock,
    private val onChanged: suspend () -> Unit,
) {
    suspend fun refresh(repo: String): RefreshOutcome {
        val summary = tokens.summary.value
        if (summary == null || summary.state == TokenState.INVALID) return RefreshOutcome.SUCCESS
        val owner = repo.substringBefore('/', "")
        val name = repo.substringAfter('/', "")
        if (owner.isEmpty() || name.isEmpty()) return RefreshOutcome.SUCCESS

        val before = snapshots.get(repo)
        val start = clock.now()
        before?.rateLimitedUntil?.let { if (it > start) return RefreshOutcome.SUCCESS }

        snapshots.update(repo) { it.copy(refreshing = true) }
        onChanged()
        try {
            val skipBranches = before != null && before.branchesForbidden &&
                before.branchesForbiddenCheckedAt?.let { start - it < RECHECK_FORBIDDEN } == true
            val result = fetcher.fetch(owner, name, FetchSettings(branchesForbidden = skipBranches))
            val now = clock.now()
            result.meta.tokenExpiresAt?.let { tokens.updateExpiry(it) }
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
                    if (e == ApiError.TokenInvalid) tokens.markInvalid()
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
                onChanged()
            }
        }
    }

    private companion object {
        val RECHECK_FORBIDDEN = 24.hours
    }
}

