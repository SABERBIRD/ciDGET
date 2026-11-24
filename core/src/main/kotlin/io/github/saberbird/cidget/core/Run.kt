package io.github.saberbird.cidget.core

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/** One GitHub Actions workflow run, already mapped from the API (AGGREGATION.md §1). */
@Serializable
public data class Run(
    val id: Long,
    val workflowId: Long,
    /** `run.name`, the workflow name. */
    val workflowName: String,
    val runNumber: Int,
    val runAttempt: Int,
    /** `push`, `pull_request`, `schedule`, `workflow_dispatch`, ... */
    val event: String,
    /** Raw API `status`. */
    val status: String,
    /** Raw API `conclusion`; null while the run is not completed. */
    val conclusion: String?,
    /** Null for some events (for example tag pushes); such runs are ignored. */
    val headBranch: String?,
    val headSha: String,
    /** True when `head_repository.full_name != repository.full_name`. */
    val fromFork: Boolean,
    val displayTitle: String,
    /** First line of `head_commit.message`. */
    val commitMessage: String?,
    val actor: String?,
    val htmlUrl: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val runStartedAt: Instant?,
)
