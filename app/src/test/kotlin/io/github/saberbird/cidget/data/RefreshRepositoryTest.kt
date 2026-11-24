package io.github.saberbird.cidget.data

import io.github.saberbird.cidget.core.BranchStatus
import io.github.saberbird.cidget.core.Status
import io.github.saberbird.cidget.github.ApiError
import io.github.saberbird.cidget.github.ApiResult
import io.github.saberbird.cidget.github.RateLimit
import io.github.saberbird.cidget.github.RepoState
import io.github.saberbird.cidget.github.ResponseMeta
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.nio.file.Files
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RefreshRepositoryTest {
    private val now = Instant.parse("2026-11-24T12:00:00Z")
    private val clock = object : Clock { override fun now() = now }
    private val dir = Files.createTempDirectory("rr").toFile()
    private val keys = object : TokenKeyProvider {
        var key: SecretKey? = null
        override fun getOrCreate(): SecretKey = key ?: KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { key = it }
        override fun delete() { key = null }
    }
    private val tokens = TokenStore(java.io.File(dir, "token.bin"), keys).also { it.add("github_pat_TEST_1234", "", null) }
    private val snapshots = SnapshotStore(java.io.File(dir, "snapshots"))
    private var calls = 0
    private var changed = 0
    private var next: ApiResult<RepoState> = ok()

    private fun branch(name: String) = BranchStatus(name, Status.GREEN, null, "s", false, null, 1, now)
    private fun ok(meta: ResponseMeta = ResponseMeta()) = ApiResult.Ok(RepoState(listOf(branch("main")), false, false), meta)
    private fun err(e: ApiError, meta: ResponseMeta = ResponseMeta()) = ApiResult.Err(e, meta)

    private val repo = RefreshRepository(tokens, snapshots, { _, _, _, _ -> calls++; next }, { null }, clock) { changed++ }

    @Test fun successStoresSnapshotAndClearsRefreshing() = runBlocking<Unit> {
        assertEquals(RefreshOutcome.SUCCESS, repo.refresh("o/r"))
        val s = assertNotNull(snapshots.get("o/r"))
        assertEquals(now, s.fetchedAt)
        assertEquals(listOf("main"), s.branches.map { it.branch })
        assertNull(s.error)
        assertFalse(s.refreshing)
        assertTrue(changed >= 2)
    }

    @Test fun errorKeepsOldSnapshotAndRecordsError() = runBlocking<Unit> {
        repo.refresh("o/r")
        next = err(ApiError.Offline)
        assertEquals(RefreshOutcome.RETRY, repo.refresh("o/r"))
        val s = assertNotNull(snapshots.get("o/r"))
        assertEquals(listOf("main"), s.branches.map { it.branch })
        assertEquals(ApiError.Offline, s.error)
        assertEquals(now, s.errorAt)
        assertFalse(s.refreshing)
    }

    @Test fun transientErrorsRetryOthersDoNot() = runBlocking<Unit> {
        for (e in listOf(ApiError.Timeout, ApiError.Server(503))) {
            next = err(e); assertEquals(RefreshOutcome.RETRY, repo.refresh("o/r"))
        }
        for (e in listOf(ApiError.NotFound, ApiError.Http(422), ApiError.Parse)) {
            next = err(e); assertEquals(RefreshOutcome.SUCCESS, repo.refresh("o/r"))
        }
    }

    @Test fun unauthorizedMarksTokenInvalidAndStopsFurtherCalls() = runBlocking<Unit> {
        next = err(ApiError.TokenInvalid)
        repo.refresh("o/r")
        assertEquals(TokenState.INVALID, tokens.summaries.value.single().state)
        assertEquals(1, calls)
        repo.refresh("o/r")
        repo.refresh("o/other")
        assertEquals(1, calls)
    }

    @Test fun rateLimitedRepoIsSkippedUntilReset() = runBlocking<Unit> {
        next = err(ApiError.RateLimited(now + kotlin.time.Duration.parse("5m")))
        repo.refresh("o/r")
        assertEquals(1, calls)
        repo.refresh("o/r")
        assertEquals(1, calls)
        assertEquals(now + kotlin.time.Duration.parse("5m"), snapshots.get("o/r")?.rateLimitedUntil)
    }

    @Test fun rateLimitHeadersWithZeroRemainingAlsoSkip() = runBlocking<Unit> {
        val reset = now + kotlin.time.Duration.parse("10m")
        next = ok(ResponseMeta(rateLimit = RateLimit(5000, 0, reset, "core")))
        repo.refresh("o/r")
        repo.refresh("o/r")
        assertEquals(1, calls)
    }

    @Test fun noTokenDoesNothing() = runBlocking<Unit> {
        tokens.delete()
        repo.refresh("o/r")
        assertEquals(0, calls)
        assertNull(snapshots.get("o/r"))
    }

    @Test fun expiryFromHeaderIsSaved() = runBlocking<Unit> {
        val exp = Instant.parse("2026-12-24T15:32:55Z")
        next = ok(ResponseMeta(tokenExpiresAt = exp))
        repo.refresh("o/r")
        assertEquals(exp, tokens.summaries.value.single().expiresAt)
    }

    @Test fun refreshingFlagClearedWhenFetcherThrows() = runBlocking<Unit> {
        val boom = RefreshRepository(tokens, snapshots, { _, _, _, _ -> error("boom") }, { null }, clock) { }
        runCatching { boom.refresh("o/r") }
        assertFalse(snapshots.get("o/r")?.refreshing ?: false)
    }

    @Test fun forbiddenBranchListIsRememberedAndSkippedFor24h() = runBlocking<Unit> {
        var seen: Boolean? = null
        val r = RefreshRepository(tokens, snapshots, { _, _, _, s -> seen = s.branchesForbidden; ApiResult.Ok(RepoState(emptyList(), true, true), ResponseMeta()) }, { null }, clock) { }
        r.refresh("o/r")
        assertEquals(false, seen)
        r.refresh("o/r")
        assertEquals(true, seen)
        assertEquals(now, snapshots.get("o/r")?.branchesForbiddenCheckedAt)
    }

    @Test fun snapshotsSurviveReopen() = runBlocking<Unit> {
        repo.refresh("o/r")
        val reopened = SnapshotStore(java.io.File(dir, "snapshots"))
        assertEquals(now, reopened.get("o/r")?.fetchedAt)
        reopened.deleteAll()
        assertNull(SnapshotStore(java.io.File(dir, "snapshots")).get("o/r"))
    }

    @Test fun eachRepoUsesItsOwnToken() = runBlocking<Unit> {
        val second = tokens.add("github_pat_TEST_5678", "work", null)
        val used = mutableListOf<String?>()
        val r = RefreshRepository(
            tokens, snapshots, { id, _, _, _ -> used += id; ok() },
            { if (it == "o/work") second else null }, clock,
        ) { }
        val first = tokens.summaries.value.first().id
        r.refresh("o/work")
        r.refresh("o/private")
        assertEquals(listOf<String?>(second, first), used)
    }

    @Test fun invalidTokenOnlyBlocksItsOwnRepos() = runBlocking<Unit> {
        val second = tokens.add("github_pat_TEST_5678", "work", null)
        val bad = tokens.summaries.value.first().id
        tokens.markInvalid(bad)
        val r = RefreshRepository(tokens, snapshots, { _, _, _, _ -> calls++; ok() }, { if (it == "o/work") second else bad }, clock) { }
        r.refresh("o/old")
        assertEquals(0, calls)
        r.refresh("o/work")
        assertEquals(1, calls)
    }

    @Test fun preparedRefreshThatCannotRunClearsTheIndicator() = runBlocking<Unit> {
        assertTrue(repo.prepare("o/r"))
        assertTrue(assertNotNull(snapshots.get("o/r")).refreshing)
        // The token turns invalid (another repo got a 401) before the queued work runs.
        tokens.markInvalid(null)
        assertEquals(RefreshOutcome.SUCCESS, repo.refresh("o/r"))
        assertFalse(assertNotNull(snapshots.get("o/r")).refreshing)
        assertEquals(0, calls)
    }

    @Test fun storedRefreshingFlagIsDroppedOnLoad() = runBlocking<Unit> {
        repo.prepare("o/r")
        val reopened = SnapshotStore(java.io.File(dir, "snapshots"))
        assertFalse(assertNotNull(reopened.get("o/r")).refreshing)
    }
}
