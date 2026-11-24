package io.github.saberbird.cidget.settings

import io.github.saberbird.cidget.data.TokenStoreUnavailableException
import io.github.saberbird.cidget.di.AppGraph
import io.github.saberbird.cidget.github.ApiError
import io.github.saberbird.cidget.github.ApiResult
import io.github.saberbird.cidget.github.GitHubClient
import io.github.saberbird.cidget.github.withToken

/** Outcome of [TokenController.validateAndSave]. */
sealed interface SaveResult {
    data object Saved : SaveResult
    data object Empty : SaveResult
    data class Failed(val error: ApiError) : SaveResult
}

/** Validates, saves, replaces and deletes tokens (SECURITY.md rules 4 and 5). */
class TokenController(private val graph: AppGraph) {
    /** Checks [raw] with `GET /user/repos?per_page=1`; only a valid token is saved. */
    suspend fun validateAndSave(raw: String, label: String, replaceId: String?): SaveResult {
        val token = raw.trim()
        if (token.isEmpty()) return SaveResult.Empty
        val probe = GitHubClient(graph.http.withToken(token))
        return when (val r = probe.listUserRepos(maxPages = 1, perPage = 1)) {
            is ApiResult.Ok -> {
                try {
                    graph.tokenStore.add(token, label, r.meta.tokenExpiresAt, replaceId)
                } catch (_: TokenStoreUnavailableException) {
                    return SaveResult.Failed(ApiError.Unexpected)
                }
                graph.onTokensChanged(removed = false)
                SaveResult.Saved
            }
            is ApiResult.Err -> SaveResult.Failed(r.error)
        }
    }

    /** Removes token [id], the HTTP cache and the snapshots, then refreshes widgets. */
    suspend fun delete(id: String) {
        try {
            graph.tokenStore.remove(id)
        } catch (_: TokenStoreUnavailableException) {
            return
        }
        graph.onTokensChanged(removed = true)
    }
}
