package io.github.saberbird.cidget.github

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import java.nio.file.Files

internal const val FAKE_TOKEN = "github_pat_TEST"
internal val NOW: Instant = Instant.parse("2026-11-24T12:00:00Z")

internal fun fixture(name: String): String =
    object {}.javaClass.getResource("/fixtures/$name")!!.readText()

internal class Harness(val log: MutableList<String> = mutableListOf()) {
    val server = MockWebServer().also { it.start() }
    val http = createHttpClient(Files.createTempDirectory("httpcache").toFile(), "ciDGET/test", { FAKE_TOKEN })
    val client = GitHubClient(http, server.url("/").toString(), FixedClock, { log += it })
    fun close() = server.close()
}

internal object FixedClock : Clock {
    override fun now(): Instant = NOW
}

internal fun json(body: String, vararg headers: Pair<String, String>, code: Int = 200): MockResponse =
    MockResponse.Builder().code(code).apply { headers.forEach { (k, v) -> addHeader(k, v) } }
        .addHeader("Content-Type", "application/json").body(body).build()

internal fun runJson(id: Long, branch: String, sha: String, created: String, concl: String? = "success", status: String = "completed"): String =
    """{"id":$id,"name":"CI","workflow_id":1,"run_number":$id,"event":"push","status":"$status",
        "conclusion":${concl?.let { "\"$it\"" }},"head_branch":"$branch","head_sha":"$sha","display_title":"t",
        "html_url":"https://github.com/o/r/actions/runs/$id","created_at":"$created","updated_at":"$created",
        "head_repository":{"full_name":"o/r"},"repository":{"full_name":"o/r"}}"""

internal fun runs(vararg r: String): String = """{"total_count":${r.size},"workflow_runs":[${r.joinToString(",")}]}"""
