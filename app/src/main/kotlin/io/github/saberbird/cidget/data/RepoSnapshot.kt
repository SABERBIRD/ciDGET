package io.github.saberbird.cidget.data

import io.github.saberbird.cidget.core.BranchStatus
import io.github.saberbird.cidget.github.ApiError
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/** Everything the widget needs to draw one repository, stored per repo (ARCHITECTURE.md §4). */
@Serializable
data class RepoSnapshot(
    /** `owner/repo`. */
    val repo: String,
    /** Last successful refresh. */
    val fetchedAt: Instant? = null,
    /** Aggregated, unsorted. */
    val branches: List<BranchStatus> = emptyList(),
    val headsApproximate: Boolean = false,
    /** Error of the last refresh; null if it succeeded. */
    val error: ApiError? = null,
    val errorAt: Instant? = null,
    val rateLimitedUntil: Instant? = null,
    val refreshing: Boolean = false,
    val branchesForbidden: Boolean = false,
    val branchesForbiddenCheckedAt: Instant? = null,
)
