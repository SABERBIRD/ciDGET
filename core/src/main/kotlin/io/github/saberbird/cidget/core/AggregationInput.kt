package io.github.saberbird.cidget.core

import kotlinx.datetime.Instant

/** Everything [aggregate] needs (AGGREGATION.md §1). */
public data class AggregationInput(
    /** All runs fetched in this refresh, in any order. */
    val runs: List<Run>,
    /** Null means the branch list is unavailable (fallback mode). */
    val heads: List<BranchHead>?,
    /** False when the branch list was capped. */
    val headsComplete: Boolean,
    /** `createdAt` of the oldest fetched run, or null when all runs were fetched. */
    val runsCoverageStart: Instant?,
    val now: Instant,
    /**
     * Head commits that a targeted `?head_sha=` request already covered. A branch whose head is in this
     * set and still has no runs is [GreyReason.NO_RUNS], not an unknown-coverage gap.
     */
    val resolvedShas: Set<String> = emptySet(),
)

/**
 * Output of [aggregate].
 *
 * @property branches one entry per branch, unsorted.
 * @property unknownCoverage branches whose head commit has no fetched runs although pagination stopped
 * early (`UNKNOWN_COVERAGE`). They appear in [branches] as grey [GreyReason.NOT_LOADED]; the data layer
 * may resolve them with a targeted request and aggregate again.
 */
public data class AggregationResult(
    val branches: List<BranchStatus>,
    val unknownCoverage: List<String>,
)
