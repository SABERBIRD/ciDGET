package io.github.saberbird.cidget.github

import io.github.saberbird.cidget.core.Status
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitHubClientTest {
    private val h = Harness()

    @AfterTest fun tearDown() = h.close()

    private fun <T> ApiResult<T>.ok(): T = (this as ApiResult.Ok).value
    private fun ApiResult<*>.err(): ApiError = (this as ApiResult.Err).error

    @Test fun parsesRunFixture() = runBlocking<Unit> {
        h.server.enqueue(json(fixture("runs.json")))
        val page = h.client.listRuns("o", "r").ok()
        assertEquals(4, page.runs.size)
        val main = page.runs[0]
        assertEquals("Fix things", main.commitMessage)
        assertEquals(2, main.runAttempt)
        assertFalse(main.fromFork)
        assertEquals("octo", main.actor)
        assertNull(page.runs[1].conclusion)
        assertNull(page.runs[1].runStartedAt)
        assertNull(page.runs[2].headBranch)
        assertTrue(page.runs[3].fromFork)
        val req = h.server.takeRequest()
        assertEquals("/repos/o/r/actions/runs?per_page=100&exclude_pull_requests=true", req.url.encodedPath + "?" + req.url.encodedQuery)
    }

    @Test fun sendsRequiredHeaders() = runBlocking<Unit> {
        h.server.enqueue(json("[]"))
        h.client.listUserRepos()
        val req = h.server.takeRequest()
        assertEquals("Bearer $FAKE_TOKEN", req.headers["Authorization"])
        assertEquals("application/vnd.github+json", req.headers["Accept"])
        assertEquals(API_VERSION, req.headers["X-GitHub-Api-Version"])
        assertEquals("ciDGET/test", req.headers["User-Agent"])
        assertEquals("max-age=0", req.headers["Cache-Control"])
    }

    @Test fun followsLinkHeaderForRepos() = runBlocking<Unit> {
        val next = h.server.url("/user/repos?page=2").toString()
        h.server.enqueue(json("""[{"full_name":"o/a","private":true}]""", "Link" to "<$next>; rel=\"next\", <$next>; rel=\"last\""))
        h.server.enqueue(json("""[{"full_name":"o/b","archived":true,"pushed_at":"2026-01-01T00:00:00Z"}]"""))
        val repos = h.client.listUserRepos().ok()
        assertEquals(listOf("o/a", "o/b"), repos.map { it.fullName })
        assertTrue(repos[0].isPrivate)
        assertTrue(repos[1].archived)
        assertEquals(2, h.server.requestCount)
    }

    @Test fun branchPageCapGivesIncompleteList() = runBlocking<Unit> {
        val next = h.server.url("/next").toString()
        repeat(2) { h.server.enqueue(json("""[{"name":"b$it","commit":{"sha":"s$it"}}]""", "Link" to "<$next>; rel=\"next\"")) }
        val list = h.client.listBranches("o", "r", maxPages = 2).ok()
        assertEquals(2, list.heads.size)
        assertFalse(list.complete)
    }

    @Test fun branchNameIsUrlEncoded() = runBlocking<Unit> {
        h.server.enqueue(json("""{"name":"feature/a b","commit":{"sha":"s"}}"""))
        assertEquals("s", h.client.getBranch("o", "r", "feature/a b").ok().sha)
        assertEquals("/repos/o/r/branches/feature%2Fa%20b", h.server.takeRequest().url.encodedPath)
    }

    @Test fun etagSecondRequestGets304FromCache() = runBlocking<Unit> {
        val body = json("""[{"full_name":"o/a"}]""", "ETag" to "\"abc\"", "Cache-Control" to "private, max-age=60")
        h.server.enqueue(body)
        h.server.enqueue(MockResponse.Builder().code(304).addHeader("ETag", "\"abc\"").build())
        val first = h.client.listUserRepos()
        assertFalse(first.meta.notModified)
        val second = h.client.listUserRepos()
        assertEquals("o/a", second.ok().single().fullName)
        assertTrue(second.meta.notModified)
        h.server.takeRequest()
        val req2 = h.server.takeRequest()
        assertEquals("\"abc\"", req2.headers["If-None-Match"])
        assertEquals("max-age=0", req2.headers["Cache-Control"])
    }

    private fun errorFor(code: Int, vararg headers: Pair<String, String>): ApiError = runBlocking {
        h.server.enqueue(MockResponse.Builder().code(code).apply { headers.forEach { addHeader(it.first, it.second) } }.body("{}").build())
        h.client.getRepo("o", "r").err()
    }

    @Test fun errorTable() {
        assertEquals(ApiError.TokenInvalid, errorFor(401))
        assertEquals(ApiError.Forbidden, errorFor(403))
        assertEquals(ApiError.NotFound, errorFor(404))
        assertEquals(ApiError.Server(502), errorFor(502))
        assertEquals(ApiError.Http(422), errorFor(422))
    }

    @Test fun rateLimitFromRemainingZero() {
        val reset = NOW.epochSeconds + 300
        val e = errorFor(403, "x-ratelimit-remaining" to "0", "x-ratelimit-reset" to "$reset")
        assertEquals(ApiError.RateLimited(Instant.fromEpochSeconds(reset)), e)
    }

    @Test fun plain403WithRemainingLeftIsForbidden() {
        assertEquals(ApiError.Forbidden, errorFor(403, "x-ratelimit-remaining" to "4000", "x-ratelimit-reset" to "${NOW.epochSeconds + 5}"))
    }

    @Test fun retryAfterParsed() {
        assertEquals(ApiError.RateLimited(Instant.fromEpochSeconds(NOW.epochSeconds + 42)), errorFor(403, "retry-after" to "42"))
        assertEquals(ApiError.RateLimited(Instant.fromEpochSeconds(NOW.epochSeconds + 7)), errorFor(429, "retry-after" to "7"))
    }

    @Test fun plain429WaitsSixtySeconds() {
        assertEquals(ApiError.RateLimited(Instant.fromEpochSeconds(NOW.epochSeconds + 60)), errorFor(429))
    }

    @Test fun badJsonIsParseError() = runBlocking<Unit> {
        h.server.enqueue(json("not json"))
        assertEquals(ApiError.Parse, h.client.getRepo("o", "r").err())
    }

    @Test fun offlineAndTimeout() = runBlocking<Unit> {
        h.server.close()
        assertEquals(ApiError.Offline, h.client.getRepo("o", "r").err())
        val slow = Harness()
        val c = GitHubClient(
            slow.http.newBuilder().addInterceptor { throw java.net.SocketTimeoutException("t") }.build(),
            slow.server.url("/").toString(), FixedClock,
        )
        assertEquals(ApiError.Timeout, c.getRepo("o", "r").err())
        slow.close()
    }

    @Test fun noTokenSendsNothing() = runBlocking<Unit> {
        val http = createHttpClient(java.nio.file.Files.createTempDirectory("c").toFile(), "ciDGET/test", { null })
        val c = GitHubClient(http, h.server.url("/").toString(), FixedClock)
        assertEquals(ApiError.NoToken, c.getRepo("o", "r").err())
        assertEquals(0, h.server.requestCount)
    }

    @Test fun throwingInterceptorNeverEscapes() = runBlocking<Unit> {
        val bad = OkHttpClient.Builder().addInterceptor(Interceptor { throw IllegalStateException("boom") }).build()
        val c = GitHubClient(bad, h.server.url("/").toString(), FixedClock)
        assertIs<ApiResult.Err>(c.getRepo("o", "r"))
        assertIs<ApiResult.Err>(c.listRuns("o", "r"))
        assertIs<ApiResult.Err>(c.fetchRepoState("o", "r"))
    }

    @Test fun logNeverContainsTokenOrAuthorization() = runBlocking<Unit> {
        h.server.enqueue(json("[]", "x-accepted-github-permissions" to "contents=read"))
        h.server.enqueue(MockResponse.Builder().code(401).body("{}").build())
        h.client.listUserRepos()
        h.client.getRepo("o", "r")
        assertTrue(h.log.isNotEmpty())
        h.log.forEach {
            assertFalse(it.contains(FAKE_TOKEN))
            assertFalse(it.contains("Authorization", ignoreCase = true))
        }
    }

    @Test fun tokenExpiryFormats() {
        val want = Instant.parse("2026-12-24T15:32:55Z")
        assertEquals(want, parseTokenExpiry("2026-12-24 15:32:55 UTC"))
        assertEquals(want, parseTokenExpiry("2026-12-24 15:32:55 +0000"))
        assertEquals(want, parseTokenExpiry("2026-12-24 15:32:55 Z"))
        assertEquals(Instant.parse("2026-12-24T13:32:55Z"), parseTokenExpiry("2026-12-24 15:32:55 +02:00"))
        assertNull(parseTokenExpiry(null))
        assertNull(parseTokenExpiry("garbage"))
    }

    @Test fun tokenExpiryAndRateLimitInMeta() = runBlocking<Unit> {
        h.server.enqueue(json("[]", "github-authentication-token-expiration" to "2026-12-24 15:32:55 UTC",
            "x-ratelimit-limit" to "5000", "x-ratelimit-remaining" to "4999", "x-ratelimit-reset" to "1800000000", "x-ratelimit-resource" to "core"))
        val meta = h.client.listUserRepos().meta
        assertEquals(Instant.parse("2026-12-24T15:32:55Z"), meta.tokenExpiresAt)
        assertEquals(4999, meta.rateLimit?.remaining)
        assertEquals("core", meta.rateLimit?.resource)
    }

    @Test fun missingExpiryHeaderIsNull() = runBlocking<Unit> {
        h.server.enqueue(json("[]"))
        assertNull(h.client.listUserRepos().meta.tokenExpiresAt)
    }

    // ---- fetchRepoState ----

    private fun dispatch(routes: (RecordedRequest) -> MockResponse) {
        h.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = routes(request)
        }
    }

    private val branches = """[{"name":"main","commit":{"sha":"s1"}},{"name":"idle","commit":{"sha":"s9"}}]"""
    private val created = "2026-11-24T11:00:00Z"

    @Test fun fetchRepoStateAccurate() = runBlocking<Unit> {
        dispatch { r ->
            when {
                r.url.encodedPath.endsWith("/branches") -> json(branches)
                else -> json(runs(runJson(1, "main", "s1", created), runJson(2, "main", "old", "2026-11-24T09:00:00Z", "failure")))
            }
        }
        val s = h.client.fetchRepoState("o", "r").ok()
        assertEquals(Status.GREEN, s.branches.single { it.branch == "main" }.status)
        assertEquals("idle", s.branches.single { it.status == Status.GREY }.branch)
        assertFalse(s.headsApproximate)
        assertFalse(s.branchesForbidden)
    }

    @Test fun branches403FallsBackToApproximate() = runBlocking<Unit> {
        dispatch { r ->
            if (r.url.encodedPath.endsWith("/branches")) MockResponse.Builder().code(403).body("{}").build()
            else json(runs(runJson(1, "main", "s1", created)))
        }
        val s = h.client.fetchRepoState("o", "r").ok()
        assertTrue(s.branchesForbidden)
        assertTrue(s.headsApproximate)
        assertEquals(Status.GREEN, s.branches.single().status)
    }

    @Test fun forbiddenFlagSkipsBranchRequest() = runBlocking<Unit> {
        dispatch { json(runs(runJson(1, "main", "s1", created))) }
        h.client.fetchRepoState("o", "r", FetchSettings(branchesForbidden = true)).ok()
        assertEquals(1, h.server.requestCount)
    }

    @Test fun runsErrorIsReturnedNotThrown() = runBlocking<Unit> {
        dispatch { r ->
            if (r.url.encodedPath.endsWith("/branches")) json(branches) else MockResponse.Builder().code(401).body("{}").build()
        }
        assertEquals(ApiError.TokenInvalid, h.client.fetchRepoState("o", "r").err())
    }

    private fun pagedRuns(requests: MutableList<String>, headsJson: String = branches): (RecordedRequest) -> MockResponse = { r ->
        requests += r.url.encodedPath + "?" + r.url.encodedQuery
        when {
            r.url.encodedPath.endsWith("/branches") -> json(headsJson)
            r.url.queryParameter("head_sha") != null -> json(runs())
            else -> {
                val page = r.url.queryParameter("page")?.toInt() ?: 1
                val next = h.server.url("/repos/o/r/actions/runs?page=${page + 1}").toString()
                json(runs(runJson(page.toLong(), "other", "x$page", "2026-11-2${5 - page}T00:00:00Z")), "Link" to "<$next>; rel=\"next\"")
            }
        }
    }

    private fun runPageRequests(r: MutableList<String>) = r.count { it.contains("actions/runs") && !it.contains("head_sha") }

    @Test fun stopsAtMaxRunPages() = runBlocking<Unit> {
        val seen = mutableListOf<String>()
        dispatch(pagedRuns(seen))
        h.client.fetchRepoState("o", "r", FetchSettings(maxRunPages = 2, hideIdleDays = null)).ok()
        assertEquals(2, runPageRequests(seen))
    }

    @Test fun stopsWhenOldestRunIsOlderThanIdleWindow() = runBlocking<Unit> {
        val seen = mutableListOf<String>()
        dispatch(pagedRuns(seen))
        // page 1 run is 2026-11-24 00:00, now is 12:00; a window of 0 days puts the cutoff at now
        h.client.fetchRepoState("o", "r", FetchSettings(maxRunPages = 3, hideIdleDays = 0)).ok()
        assertEquals(1, runPageRequests(seen))
    }

    @Test fun stopsWhenEveryHeadHasRuns() = runBlocking<Unit> {
        val seen = mutableListOf<String>()
        dispatch(pagedRuns(seen, """[{"name":"other","commit":{"sha":"x1"}}]"""))
        h.client.fetchRepoState("o", "r", FetchSettings(maxRunPages = 3, hideIdleDays = null)).ok()
        assertEquals(1, runPageRequests(seen))
    }

    @Test fun fallbackModeKeepsPagingUntilCap() = runBlocking<Unit> {
        val seen = mutableListOf<String>()
        dispatch(pagedRuns(seen))
        h.client.fetchRepoState("o", "r", FetchSettings(maxRunPages = 3, hideIdleDays = null, branchesForbidden = true)).ok()
        assertEquals(3, runPageRequests(seen))
    }

    @Test fun targetedFillInResolvesCoverageGapToNoRuns() = runBlocking<Unit> {
        val seen = mutableListOf<String>()
        dispatch(pagedRuns(seen))
        val s = h.client.fetchRepoState("o", "r", FetchSettings(maxRunPages = 1, hideIdleDays = null)).ok()
        assertTrue(seen.any { it.contains("head_sha=s1") })
        assertEquals(io.github.saberbird.cidget.core.GreyReason.NO_RUNS, s.branches.single { it.branch == "main" }.greyReason)
    }

    @Test fun targetedFillInFindsRuns() = runBlocking<Unit> {
        dispatch { r ->
            when {
                r.url.encodedPath.endsWith("/branches") -> json(branches)
                r.url.queryParameter("head_sha") == "s1" -> json(runs(runJson(50, "main", "s1", created)))
                else -> json(runs(runJson(1, "other", "x", "2026-11-20T00:00:00Z")), "Link" to "<${h.server.url("/z")}>; rel=\"next\"")
            }
        }
        val s = h.client.fetchRepoState("o", "r", FetchSettings(maxRunPages = 1, hideIdleDays = null)).ok()
        assertEquals(Status.GREEN, s.branches.single { it.branch == "main" }.status)
    }

    @Test fun hugeRepoSingleBranchLookup() = runBlocking<Unit> {
        val next = h.server.url("/more").toString()
        dispatch { r ->
            when {
                r.url.encodedPath.endsWith("/branches/late") -> json("""{"name":"late","commit":{"sha":"l1"}}""")
                r.url.encodedPath.endsWith("/branches") -> json(branches, "Link" to "<$next>; rel=\"next\"")
                else -> json(runs(runJson(1, "late", "l1", created)))
            }
        }
        val s = h.client.fetchRepoState("o", "r", FetchSettings(maxBranchPages = 1)).ok()
        val late = s.branches.single { it.branch == "late" }
        assertFalse(late.headApproximate)
        assertEquals(Status.GREEN, late.status)
    }
}
