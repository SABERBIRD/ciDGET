package io.github.saberbird.cidget.data

import io.github.saberbird.cidget.github.ApiError
import io.github.saberbird.cidget.github.ApiResult
import io.github.saberbird.cidget.github.RepoSummary
import io.github.saberbird.cidget.github.ResponseMeta
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.io.File
import java.nio.file.Files
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepoListTest {
    private val now = Instant.parse("2026-11-24T12:00:00Z")
    private val clock = object : Clock { override fun now() = now }
    private val dir = Files.createTempDirectory("rl").toFile()
    private val keys = object : TokenKeyProvider {
        var key: SecretKey? = null
        override fun getOrCreate(): SecretKey = key ?: KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { key = it }
        override fun delete() { key = null }
    }
    private val tokens = TokenStore(File(dir, "token.bin"), keys)
    private val store = RepoListStore(File(dir, "repos.json"))
    private val a = tokens.add("github_pat_TEST_aaaa", "a", null)
    private val b = tokens.add("github_pat_TEST_bbbb", "b", null)
    private val results = mutableMapOf<String, ApiResult<List<RepoSummary>>>()

    private fun repo(name: String, priv: Boolean = false) = RepoSummary(name, priv, false, null)
    private val repos = RepoListRepository(tokens, store, { results.getValue(it) }, clock)

    @Test fun mergesReposOfAllTokensAndDeduplicates() = runBlocking<Unit> {
        results[a] = ApiResult.Ok(listOf(repo("me/x"), repo("org/shared")), ResponseMeta())
        results[b] = ApiResult.Ok(listOf(repo("org/shared"), repo("org/y", true)), ResponseMeta())
        assertEquals(RefreshOutcome.SUCCESS, repos.refresh())
        val list = store.get()
        assertEquals(listOf("me/x", "org/shared", "org/y"), list.repos.map { it.fullName })
        assertEquals(a, list.repos.first { it.fullName == "org/shared" }.tokenId)
        assertEquals(b, list.repos.first { it.fullName == "org/y" }.tokenId)
        assertEquals(now, list.fetchedAt)
        assertFalse(list.refreshing)
    }

    @Test fun failingTokenKeepsItsOldEntriesAndReportsTheError() = runBlocking<Unit> {
        results[a] = ApiResult.Ok(listOf(repo("me/x")), ResponseMeta())
        results[b] = ApiResult.Ok(listOf(repo("org/y")), ResponseMeta())
        repos.refresh()
        results[b] = ApiResult.Err(ApiError.Offline, ResponseMeta())
        results[a] = ApiResult.Ok(listOf(repo("me/z")), ResponseMeta())
        assertEquals(RefreshOutcome.RETRY, repos.refresh())
        val list = store.get()
        assertEquals(setOf("me/z", "org/y"), list.repos.map { it.fullName }.toSet())
        assertEquals(ApiError.Offline, list.error)
    }

    @Test fun persistsAcrossReopenAndClears() = runBlocking<Unit> {
        results[a] = ApiResult.Ok(listOf(repo("me/x")), ResponseMeta())
        results[b] = ApiResult.Ok(emptyList(), ResponseMeta())
        repos.refresh()
        val reopened = RepoListStore(File(dir, "repos.json"))
        assertEquals(listOf("me/x"), reopened.get().repos.map { it.fullName })
        reopened.clear()
        assertTrue(RepoListStore(File(dir, "repos.json")).get().repos.isEmpty())
    }

    @Test fun prepareShowsRefreshing() = runBlocking<Unit> {
        repos.prepare()
        assertTrue(store.get().refreshing)
    }
}
