package io.github.saberbird.cidget.github

import io.github.saberbird.cidget.core.BranchHead
import io.github.saberbird.cidget.core.Run
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

/** One decoded page plus the URL of the next one. */
internal data class Page<T>(val value: T, val next: String?)

/**
 * GitHub REST client (GITHUB_API.md). Every method returns an [ApiResult]; no exception escapes.
 *
 * @param http client from [createHttpClient] (tests pass their own).
 * @param log optional debug sink for `METHOD url -> code` lines. It never receives headers or bodies,
 * so the token cannot reach it.
 */
public class GitHubClient(
    private val http: OkHttpClient,
    baseUrl: String = "https://api.github.com",
    internal val clock: Clock = Clock.System,
    private val log: ((String) -> Unit)? = null,
) {
    private val base: HttpUrl = baseUrl.toHttpUrl()

    private fun url(vararg segments: String, build: HttpUrl.Builder.() -> Unit = {}): HttpUrl =
        base.newBuilder().apply { segments.forEach { addPathSegments(it) } }.apply(build).build()

    private fun repoUrl(owner: String, repo: String, vararg tail: String, build: HttpUrl.Builder.() -> Unit = {}): HttpUrl =
        base.newBuilder().addPathSegment("repos").addPathSegment(owner).addPathSegment(repo)
            .apply { tail.forEach { addPathSegments(it) } }.apply(build).build()

    @OptIn(ExperimentalSerializationApi::class)
    internal suspend fun <T> getPage(url: HttpUrl, serializer: KSerializer<T>): ApiResult<Page<T>> {
        var meta = ResponseMeta()
        return try {
            val request = Request.Builder().url(url).get().build()
            http.newCall(request).await().use { response ->
                meta = parseMeta(response.headers, response.networkResponse?.code == 304)
                log?.invoke("GET ${url.encodedPath} -> ${response.code}")
                if (!response.isSuccessful) {
                    ApiResult.Err(mapHttpError(response.code, response.headers, clock.now()), meta)
                } else {
                    val body = response.body
                    val value = json.decodeFromStream(serializer, body.byteStream())
                    ApiResult.Ok(Page(value, parseLinkNext(response.headers)), meta)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            ApiResult.Err(mapException(e), meta)
        }
    }

    private suspend fun <T, R> getAll(
        first: HttpUrl,
        serializer: KSerializer<List<T>>,
        maxPages: Int,
        map: (T) -> R,
    ): ApiResult<Pair<List<R>, Boolean>> {
        val out = mutableListOf<R>()
        var meta = ResponseMeta()
        var next: String? = first.toString()
        var pages = 0
        while (next != null && pages < maxPages) {
            when (val r = getPage(next.toHttpUrl(), serializer)) {
                is ApiResult.Err -> return ApiResult.Err(r.error, meta.merge(r.meta))
                is ApiResult.Ok -> {
                    meta = meta.merge(r.meta)
                    out += r.value.value.map(map)
                    next = r.value.next
                    pages++
                }
            }
        }
        return ApiResult.Ok(out to (next == null), meta)
    }

    /** Repos the token can see (`/user/repos`, newest push first), at most [maxPages] pages. Also the token check. */
    public suspend fun listUserRepos(maxPages: Int = 10, perPage: Int = 100): ApiResult<List<RepoSummary>> =
        when (val r = getAll(
            url("user/repos") { addQueryParameter("per_page", "$perPage"); addQueryParameter("sort", "pushed") },
            ListSerializer(UserRepoDto.serializer()),
            maxPages,
        ) { it.toSummary() }) {
            is ApiResult.Ok -> ApiResult.Ok(r.value.first, r.meta)
            is ApiResult.Err -> r
        }

    /** Checks one repo typed by hand. */
    public suspend fun getRepo(owner: String, repo: String): ApiResult<RepoSummary> =
        when (val r = getPage(repoUrl(owner, repo), UserRepoDto.serializer())) {
            is ApiResult.Ok -> ApiResult.Ok(r.value.value.toSummary(), r.meta)
            is ApiResult.Err -> r
        }

    /** Branch heads, at most [maxPages] pages; [BranchList.complete] is false when more pages exist. */
    public suspend fun listBranches(owner: String, repo: String, maxPages: Int = 5): ApiResult<BranchList> =
        when (val r = getAll(
            repoUrl(owner, repo, "branches") { addQueryParameter("per_page", "100") },
            ListSerializer(BranchDto.serializer()),
            maxPages,
        ) { it.toHead() }) {
            is ApiResult.Ok -> ApiResult.Ok(BranchList(r.value.first, r.value.second), r.meta)
            is ApiResult.Err -> r
        }

    /** Head of one branch. The name is URL-encoded. */
    public suspend fun getBranch(owner: String, repo: String, branch: String): ApiResult<BranchHead> {
        val u = base.newBuilder().addPathSegment("repos").addPathSegment(owner).addPathSegment(repo)
            .addPathSegment("branches").addPathSegment(branch).build()
        return when (val r = getPage(u, BranchDto.serializer())) {
            is ApiResult.Ok -> ApiResult.Ok(r.value.value.toHead(), r.meta)
            is ApiResult.Err -> r
        }
    }

    /** One page of workflow runs. [next] null = first page; otherwise the `next` of the previous page. */
    public suspend fun listRuns(owner: String, repo: String, next: String? = null): ApiResult<RunsPage> {
        val u = next?.toHttpUrl() ?: repoUrl(owner, repo, "actions/runs") {
            addQueryParameter("per_page", "100")
            addQueryParameter("exclude_pull_requests", "true")
        }
        return when (val r = getPage(u, RunsDto.serializer())) {
            is ApiResult.Ok -> ApiResult.Ok(RunsPage(r.value.value.workflowRuns.map { it.toRun() }, r.value.next), r.meta)
            is ApiResult.Err -> r
        }
    }

    /** Runs of one commit (first page of 100 is enough in practice). */
    public suspend fun listRunsForSha(owner: String, repo: String, sha: String): ApiResult<List<Run>> {
        val u = repoUrl(owner, repo, "actions/runs") {
            addQueryParameter("head_sha", sha)
            addQueryParameter("per_page", "100")
            addQueryParameter("exclude_pull_requests", "true")
        }
        return when (val r = getPage(u, RunsDto.serializer())) {
            is ApiResult.Ok -> ApiResult.Ok(r.value.value.workflowRuns.map { it.toRun() }, r.meta)
            is ApiResult.Err -> r
        }
    }
}
