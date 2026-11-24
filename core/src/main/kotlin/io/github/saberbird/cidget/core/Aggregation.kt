package io.github.saberbird.cidget.core

/** Newest run first: greater `createdAt`, ties broken by greater `id`. */
private val newestFirst: Comparator<Run> =
    compareByDescending<Run> { it.createdAt }.thenByDescending { it.id }

private fun List<Run>.newest(): Run? = minWithOrNull(newestFirst)

/**
 * Turns fetched runs into one [BranchStatus] per branch, exactly as AGGREGATION.md §3–§5 describe.
 * Pure and total: unknown strings never throw.
 */
public fun aggregate(input: AggregationInput): AggregationResult {
    val runsByBranch: Map<String, List<Run>> = input.runs
        .filter { !it.fromFork }
        .mapNotNull { run -> run.headBranch?.let { it to run } }
        .groupBy({ it.first }, { it.second })
    val heads = input.heads
    val headShas: Map<String, String>? = heads?.associate { it.name to it.sha }

    val names: List<String> = if (heads == null || headShas == null) {
        runsByBranch.keys.sorted()
    } else {
        val extra = if (input.headsComplete) emptyList() else runsByBranch.keys.filter { it !in headShas }.sorted()
        heads.map { it.name }.distinct() + extra
    }

    val gaps = mutableListOf<String>()
    val result = names.map { name ->
        val branchRuns = runsByBranch[name].orEmpty()
        val lastActivity = branchRuns.maxOfOrNull { it.updatedAt }
        val exactSha = headShas?.get(name)
        val sha = exactSha ?: branchRuns.newest()?.headSha
        val approximate = exactSha == null
        val commitRuns = if (sha == null) emptyList() else branchRuns.filter { it.headSha == sha }
        when {
            commitRuns.isNotEmpty() -> statusFromRuns(name, sha, approximate, commitRuns, lastActivity)
            exactSha != null && input.runsCoverageStart != null && exactSha !in input.resolvedShas -> {
                gaps += name
                BranchStatus(name, Status.GREY, GreyReason.NOT_LOADED, sha, false, null, 0, lastActivity)
            }
            else -> BranchStatus(name, Status.GREY, GreyReason.NO_RUNS, sha, approximate, null, 0, lastActivity)
        }
    }
    return AggregationResult(result, gaps)
}

private fun statusFromRuns(
    name: String,
    sha: String?,
    approximate: Boolean,
    commitRuns: List<Run>,
    lastActivity: kotlinx.datetime.Instant?,
): BranchStatus {
    // One kept run per (workflow, event): the newest.
    val kept = commitRuns
        .groupBy { it.workflowId to it.event }
        .values
        .mapNotNull { it.newest() }
    fun newestIn(states: Set<RunState>): Run? = kept.filter { it.state in states }.newest()
    val failed = newestIn(setOf(RunState.FAILED))
    val running = newestIn(setOf(RunState.RUNNING))
    val incomplete = newestIn(setOf(RunState.INCOMPLETE, RunState.UNKNOWN))
    val success = newestIn(setOf(RunState.SUCCESS))
    val (status, reason, rep) = when {
        failed != null -> Triple(Status.RED, null, failed)
        running != null -> Triple(Status.YELLOW, null, running)
        incomplete != null -> Triple(Status.GREY, GreyReason.INCOMPLETE, incomplete)
        success != null -> Triple(Status.GREEN, null, success)
        else -> Triple(Status.GREY, GreyReason.NOTHING_RAN, kept.newest())
    }
    return BranchStatus(name, status, reason, sha, approximate, rep, kept.size, lastActivity)
}
