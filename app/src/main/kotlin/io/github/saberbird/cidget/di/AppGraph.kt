package io.github.saberbird.cidget.di

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log
import io.github.saberbird.cidget.data.KeystoreKeyProvider
import io.github.saberbird.cidget.data.TokenStore
import io.github.saberbird.cidget.github.GitHubClient
import io.github.saberbird.cidget.github.createHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File

/** Builds and holds the app-wide singletons. Created once in `Application.onCreate`. */
class AppGraph(val app: Application) {
    val tokenStore: TokenStore = TokenStore(File(app.filesDir, "token.bin"), KeystoreKeyProvider())

    val http: OkHttpClient = createHttpClient(
        cacheDir = File(app.cacheDir, "http"),
        userAgent = "ciDGET/${versionName()}",
        tokenProvider = tokenStore::usableToken,
    )

    private val debuggable = app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /** The API client. In debug builds it logs `GET path -> code` lines only (never headers or bodies). */
    val github: GitHubClient = GitHubClient(http, log = if (debuggable) { line -> Log.d("cidget", line) } else null)

    /** Drops cached API responses; they may hold private repository data. */
    suspend fun evictHttpCache() = withContext(Dispatchers.IO) { runCatching { http.cache?.evictAll() }; Unit }

    /** Called after the token was saved, replaced or deleted. Later tasks add snapshots and refreshes here. */
    suspend fun onTokenChanged() {
        evictHttpCache()
    }

    private fun versionName(): String =
        runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "0"
}
