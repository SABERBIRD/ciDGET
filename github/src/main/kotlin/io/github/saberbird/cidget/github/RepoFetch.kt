package io.github.saberbird.cidget.github

import io.github.saberbird.cidget.core.AggregationInput
import io.github.saberbird.cidget.core.AggregationResult
import io.github.saberbird.cidget.core.BranchHead
import io.github.saberbird.cidget.core.Run
import io.github.saberbird.cidget.core.ViewOptions
import io.github.saberbird.cidget.core.aggregate
import io.github.saberbird.cidget.core.selectBranches
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days

/**
 * The refresh algorithm for one repository (GITHUB_API.md §3): branch heads, run pages with the stop
 * conditions of §3.2, then single-branch heads for huge repos (§3.1) and targeted `head_sha` fill-in
 * (§3.3). Failures of the optional enrichment steps are not errors: those branches stay approximate or
 * grey. Failures of the branch list (other than 403) and of the run list are returned as [ApiResult.Err].
 */
public suspend fun GitHubClient.fetchRepoState(
    owner: String,
    repo: String,
    settings: FetchSettings = FetchSettings(),
): ApiResult<RepoState> {
    val now = clock.now()
    var meta = ResponseMeta()

    // 3.1 branch heads
    var heads: List<BranchHead>? = null
    var headsComplete = true
    var forbidden = settings.branchesForbidden
    if (!forbidden) {
        when (val r = listBranches(owner, repo, settings.maxBranchPages)) {
            is ApiResult.Ok -> {
                meta = meta.merge(r.meta)
                heads = r.value.heads
                headsComplete = r.value.complete
            }
            is ApiResult.Err -> {
                meta = meta.merge(r.meta)
                if (r.error == ApiError.Forbidden) forbidden = true else return ApiResult.Err(r.error, meta)
            }
        }
    }

    // 3.2 runs
    val runs = mutableListOf<Run>()
    var next: String? = null
    var pages = 0
    val idleCutoff: Instant? = settings.hideIdleDays?.let { now - it.days }
    while (true) {
        when (val r = listRuns(owner, repo, next)) {
            is ApiResult.Err -> return ApiResult.Err(r.error, meta.merge(r.meta))
            is ApiResult.Ok -> {
                meta = meta.merge(r.meta)
                runs += r.value.runs
                next = r.value.next
                pages++
            }
        }
        if (next == null || pages >= settings.maxRunPages) break
        val oldest = runs.minOfOrNull { it.createdAt }
        if (idleCutoff != null && oldest != null && oldest <= idleCutoff) break
        if (heads != null && !someHeadWithoutRuns(heads, runs)) break
    }
    val coverageStart: Instant? = if (next != null) runs.minOfOrNull { it.createdAt } else null

    fun aggregateNow(h: List<BranchHead>?, resolved: Set<String>): AggregationResult =
        aggregate(AggregationInput(runs, h, headsComplete, coverageStart, now, resolved))

    val viewOptions = ViewOptions(
        pinned = settings.pinned,
        sortKey = settings.sortKey,
        hideIdleDays = settings.hideIdleDays,
        maxRows = settings.maxRows,
        hideBranchesWithoutRuns = settings.hideBranchesWithoutRuns,
    )
    fun displayed(result: AggregationResult): List<String> =
        selectBranches(result.branches, viewOptions, now).rows.map { it.branch }

    var result = aggregateNow(heads, emptySet())

    // 3.1 (3): partial branch list → exact heads for displayed and pinned branches
    if (heads != null && !headsComplete) {
        val known = heads.map { it.name }.toSet()
        val wanted = (displayed(result) + settings.pinned).distinct().filter { it !in known }
            .take(settings.maxSingleBranchRequests)
        val extra = mutableListOf<BranchHead>()
        for (name in wanted) {
            val r = getBranch(owner, repo, name)
            meta = meta.merge(r.meta)
            if (r is ApiResult.Ok) extra += r.value
        }
        if (extra.isNotEmpty()) {
            heads = heads + extra
            result = aggregateNow(heads, emptySet())
        }
    }

    // 3.3 targeted fill-in (accurate mode only)
    val resolved = mutableSetOf<String>()
    if (heads != null && coverageStart != null) {
        val shaOfOldest = runs.minWithOrNull(compareBy<Run> { it.createdAt }.thenBy { it.id })?.headSha
        val headSha = heads.associate { it.name to it.sha }
        val show = displayed(result).toSet()
        val wantedShas = (
            result.unknownCoverage.filter { it in show }.mapNotNull { headSha[it] } +
                listOfNotNull(shaOfOldest).filter { it in headSha.values && show.any { b -> headSha[b] == it } }
            ).distinct().take(settings.maxTargetedRequests)
        val known = runs.mapTo(mutableSetOf()) { it.id }
        for (sha in wantedShas) {
            val r = listRunsForSha(owner, repo, sha)
            meta = meta.merge(r.meta)
            if (r is ApiResult.Ok) {
                resolved += sha
                r.value.filter { known.add(it.id) }.forEach { runs += it }
            }
        }
        if (wantedShas.isNotEmpty()) result = aggregateNow(heads, resolved)
    }

    return ApiResult.Ok(
        RepoState(
            branches = result.branches,
            headsApproximate = result.branches.any { it.headApproximate },
            branchesForbidden = forbidden,
        ),
        meta,
    )
}

private fun someHeadWithoutRuns(heads: List<BranchHead>, runs: List<Run>): Boolean {
    val seen = runs.filter { !it.fromFork && it.headBranch != null }.map { it.headBranch to it.headSha }.toSet()
    return heads.any { (it.name to it.sha) !in seen }
}
