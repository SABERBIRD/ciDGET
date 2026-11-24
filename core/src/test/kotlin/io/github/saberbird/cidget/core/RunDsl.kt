package io.github.saberbird.cidget.core

import kotlinx.datetime.Instant

internal val T0: Instant = Instant.parse("2026-01-01T00:00:00Z")

/** Seconds after [T0]. */
internal fun t(seconds: Long): Instant = Instant.fromEpochSeconds(T0.epochSeconds + seconds)

private var nextId = 1L

/** Test DSL: builds a run with sensible defaults. */
internal fun run(
    wf: Long = 1,
    branch: String? = "main",
    sha: String = "sha1",
    status: String = "completed",
    conclusion: String? = "success",
    event: String = "push",
    at: Long = 100,
    id: Long = nextId++,
    attempt: Int = 1,
    fork: Boolean = false,
    started: Instant? = t(at - 10),
    updated: Instant = t(at + 20),
): Run = Run(
    id = id,
    workflowId = wf,
    workflowName = "wf$wf",
    runNumber = id.toInt(),
    runAttempt = attempt,
    event = event,
    status = status,
    conclusion = conclusion,
    headBranch = branch,
    headSha = sha,
    fromFork = fork,
    displayTitle = "title $id",
    commitMessage = null,
    actor = null,
    htmlUrl = "https://github.com/o/r/actions/runs/$id",
    createdAt = t(at),
    updatedAt = updated,
    runStartedAt = started,
)

internal fun input(
    runs: List<Run>,
    heads: List<BranchHead>? = listOf(BranchHead("main", "sha1")),
    complete: Boolean = true,
    coverageStart: Instant? = null,
): AggregationInput = AggregationInput(runs, heads, complete, coverageStart, t(10_000))

internal fun AggregationResult.of(branch: String): BranchStatus = branches.single { it.branch == branch }

internal fun branch(
    name: String,
    status: Status = Status.GREEN,
    reason: GreyReason? = null,
    activity: Instant? = t(100),
): BranchStatus = BranchStatus(name, status, reason, "sha", false, null, 1, activity)
