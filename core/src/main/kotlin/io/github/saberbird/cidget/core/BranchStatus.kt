package io.github.saberbird.cidget.core

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/** Aggregated state of one branch (AGGREGATION.md §6). */
@Serializable
public data class BranchStatus(
    val branch: String,
    val status: Status,
    val greyReason: GreyReason?,
    val headSha: String?,
    /** True when the head commit was guessed from the newest run (D8 fallback). */
    val headApproximate: Boolean,
    val representative: Run?,
    /** Number of kept workflow groups. */
    val workflowCount: Int,
    /** Newest `updatedAt` of the branch's runs on any commit. */
    val lastActivity: Instant?,
)
