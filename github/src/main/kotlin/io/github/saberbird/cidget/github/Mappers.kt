package io.github.saberbird.cidget.github

import io.github.saberbird.cidget.core.BranchHead
import io.github.saberbird.cidget.core.Run

/** DTO → :core model. `:core` never sees DTOs. */
internal fun RunDto.toRun(): Run {
    val headName = headRepository?.fullName
    val repoName = repository?.fullName
    return Run(
        id = id,
        workflowId = workflowId,
        workflowName = name.orEmpty(),
        runNumber = runNumber,
        runAttempt = runAttempt,
        event = event,
        status = status.orEmpty(),
        conclusion = conclusion,
        headBranch = headBranch,
        headSha = headSha,
        fromFork = headName != repoName,
        displayTitle = displayTitle ?: name.orEmpty(),
        commitMessage = headCommit?.message?.lineSequence()?.firstOrNull(),
        actor = actor?.login,
        htmlUrl = htmlUrl,
        createdAt = createdAt,
        updatedAt = updatedAt,
        runStartedAt = runStartedAt,
    )
}

internal fun BranchDto.toHead(): BranchHead = BranchHead(name, commit.sha)

internal fun UserRepoDto.toSummary(): RepoSummary = RepoSummary(fullName, isPrivate, archived, pushedAt)
