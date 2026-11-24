package io.github.saberbird.cidget.github

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Only the fields listed in GITHUB_API.md §2. Everything the API may omit is nullable.

@Serializable
internal data class RunsDto(@SerialName("workflow_runs") val workflowRuns: List<RunDto> = emptyList())

@Serializable
internal data class RunDto(
    val id: Long,
    val name: String? = null,
    @SerialName("workflow_id") val workflowId: Long,
    @SerialName("run_number") val runNumber: Int,
    @SerialName("run_attempt") val runAttempt: Int = 1,
    val event: String = "",
    val status: String? = null,
    val conclusion: String? = null,
    @SerialName("head_branch") val headBranch: String? = null,
    @SerialName("head_sha") val headSha: String,
    @SerialName("display_title") val displayTitle: String? = null,
    @SerialName("head_commit") val headCommit: HeadCommitDto? = null,
    val actor: ActorDto? = null,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("updated_at") val updatedAt: Instant,
    @SerialName("run_started_at") val runStartedAt: Instant? = null,
    @SerialName("head_repository") val headRepository: RepoNameDto? = null,
    val repository: RepoNameDto? = null,
)

@Serializable internal data class HeadCommitDto(val message: String? = null)

@Serializable internal data class ActorDto(val login: String? = null)

@Serializable internal data class RepoNameDto(@SerialName("full_name") val fullName: String? = null)

@Serializable
internal data class BranchDto(val name: String, val commit: CommitDto)

@Serializable internal data class CommitDto(val sha: String)

@Serializable
internal data class UserRepoDto(
    @SerialName("full_name") val fullName: String,
    @SerialName("private") val isPrivate: Boolean = false,
    val archived: Boolean = false,
    @SerialName("pushed_at") val pushedAt: Instant? = null,
)
