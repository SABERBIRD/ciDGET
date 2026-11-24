package io.github.saberbird.cidget.github

import io.github.saberbird.cidget.core.BranchHead
import io.github.saberbird.cidget.core.BranchStatus
import io.github.saberbird.cidget.core.Run
import io.github.saberbird.cidget.core.SortKey
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/** A repository as shown in the picker. */
@Serializable
public data class RepoSummary(
    val fullName: String,
    val isPrivate: Boolean,
    val archived: Boolean,
    val pushedAt: Instant?,
)

/** Branch heads. [complete] is false when the page cap was reached. */
public data class BranchList(val heads: List<BranchHead>, val complete: Boolean)

/** One page of runs; [next] is the URL of the next page (opaque), or null. */
public data class RunsPage(val runs: List<Run>, val next: String?)

/** Settings that steer one refresh (GITHUB_API.md §3). Defaults follow D13. */
public data class FetchSettings(
    val maxRunPages: Int = 3,
    val hideIdleDays: Int? = 30,
    val hideBranchesWithoutRuns: Boolean = false,
    val pinned: List<String> = emptyList(),
    val maxRows: Int? = 50,
    val sortKey: SortKey = SortKey.RECENT,
    /** Cached flag: skip the branch list (it answered 403 recently). */
    val branchesForbidden: Boolean = false,
    val maxBranchPages: Int = 5,
    val maxSingleBranchRequests: Int = 20,
    val maxTargetedRequests: Int = 10,
)

/** Outcome of [GitHubClient.fetchRepoState]. */
public data class RepoState(
    /** Aggregated, unsorted. */
    val branches: List<BranchStatus>,
    /** True when at least one branch head is a guess. */
    val headsApproximate: Boolean,
    /** The branch list answered 403 in this refresh (or was skipped because it did earlier). */
    val branchesForbidden: Boolean,
)
