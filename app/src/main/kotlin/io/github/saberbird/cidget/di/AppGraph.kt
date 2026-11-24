package io.github.saberbird.cidget.di

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log
import io.github.saberbird.cidget.data.KeystoreKeyProvider
import io.github.saberbird.cidget.data.RefreshRepository
import io.github.saberbird.cidget.data.RepoFetcher
import io.github.saberbird.cidget.data.RepoListRepository
import io.github.saberbird.cidget.data.RepoListStore
import io.github.saberbird.cidget.data.SettingsRepository
import io.github.saberbird.cidget.data.SnapshotStore
import io.github.saberbird.cidget.widget.CidgetWidget
import androidx.glance.appwidget.updateAll
import kotlinx.datetime.Clock
import io.github.saberbird.cidget.github.fetchRepoState
import io.github.saberbird.cidget.github.withTokenProvider
import io.github.saberbird.cidget.work.RefreshScheduler
import io.github.saberbird.cidget.data.TokenStore
import io.github.saberbird.cidget.github.GitHubClient
import io.github.saberbird.cidget.github.createHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File

/** Builds and holds the app-wide singletons. Created once in `Application.onCreate`. */
class AppGraph(val app: Application) {
    val tokenStore: TokenStore = TokenStore(File(app.filesDir, "token.bin"), KeystoreKeyProvider())

    val http: OkHttpClient = createHttpClient(
        cacheDir = File(app.cacheDir, "http"),
        userAgent = "ciDGET/${versionName()}",
        tokenProvider = { tokenStore.usableToken(null) },
    )

    private val debuggable = app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private val log: ((String) -> Unit)? = if (debuggable) { line -> Log.d("cidget", line) } else null

    /**
     * API client for token [tokenId] (null = the default token; D23). In debug builds it logs
     * `GET path -> code` lines only (never headers or bodies). The token is read per request.
     */
    fun githubFor(tokenId: String?): GitHubClient =
        GitHubClient(http.withTokenProvider { tokenStore.usableToken(tokenId) }, log = log)

    /** For work that must outlive a widget callback or a screen. */
    val background: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val snapshots: SnapshotStore = SnapshotStore(File(app.filesDir, "snapshots"))

    val settings: SettingsRepository = SettingsRepository(app)

    /** Redraws every widget from the local stores. */
    suspend fun updateWidgets() = CidgetWidget().updateAll(app)

    val refresh: RefreshRepository = RefreshRepository(
        tokens = tokenStore,
        snapshots = snapshots,
        fetcher = RepoFetcher { tokenId, owner, repo, s -> githubFor(tokenId).fetchRepoState(owner, repo, s) },
        tokenIdFor = settings::tokenIdForRepo,
        clock = Clock.System,
        onChanged = ::updateWidgets,
    )

    val repoListStore: RepoListStore = RepoListStore(File(app.filesDir, "repos.json"))

    val repoLists: RepoListRepository = RepoListRepository(
        tokens = tokenStore,
        store = repoListStore,
        listFor = { tokenId -> githubFor(tokenId).listUserRepos() },
        clock = Clock.System,
        onChanged = ::updateWidgets,
    )

    /** Shows "loading" in the widget's repo list at once, then queues the worker that refreshes it. */
    fun requestRepoList() {
        background.launch {
            repoLists.prepare()
            RefreshScheduler.refreshRepoList(app)
        }
    }

    /**
     * Picks [repo] (seen by [tokenId]) for widget [appWidgetId], closes its repo list, prunes snapshots and
     * queues a refresh of the repo.
     */
    suspend fun pickRepo(appWidgetId: Int, repo: String, tokenId: String?) {
        val before = settings.getWidgetRepo(appWidgetId)
        // One atomic write; the widget observes it and redraws, so the switch is instant (D26).
        settings.setWidgetRepo(appWidgetId, repo, tokenId)
        settings.setRepoListOpen(appWidgetId, false)
        // Everything slower runs in the background, so the next tap is never waiting for this repo.
        background.launch {
            val inUse = settings.reposInUse()
            if (before != null && before != repo && before !in inUse) RefreshScheduler.cancel(app, before)
            snapshots.deleteExcept(inUse)
            requestRefresh(repo)
        }
    }

    /** Manual refresh: shows `[...]` immediately, then queues the work. */
    suspend fun requestRefresh(repo: String) {
        if (refresh.prepare(repo)) refreshNow(repo)
    }

    /** Queues a refresh for [repo] (`owner/repo`). */
    fun refreshNow(repo: String) = RefreshScheduler.refreshNow(app, repo)

    /** Drops cached API responses; they may hold private repository data. */
    suspend fun evictHttpCache() = withContext(Dispatchers.IO) { runCatching { http.cache?.evictAll() }; Unit }

    /**
     * Called after a token was added, replaced or removed. Clears cached private data, redraws widgets and
     * refreshes. When a token was [removed] all snapshots go too, because they may hold its private data.
     */
    suspend fun onTokensChanged(removed: Boolean) {
        evictHttpCache()
        if (removed) {
            snapshots.deleteAll()
            repoListStore.clear()
        }
        updateWidgets()
        if (tokenStore.hasToken()) {
            settings.reposInUse().forEach(::refreshNow)
            RefreshScheduler.refreshRepoList(app)
        }
    }

    init {
        // Redraw widgets after every settings change, from the widget callbacks and from the settings screens
        // alike (an idle Glance session would otherwise keep showing the old state).
        background.launch {
            settings.changes().drop(1).conflate().collect { runCatching { updateWidgets() } }
        }
    }

    private fun versionName(): String =
        runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "0"
}
