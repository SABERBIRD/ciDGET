package io.github.saberbird.cidget.di

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import io.github.saberbird.cidget.core.WidgetConfig
import io.github.saberbird.cidget.core.WidgetView
import io.github.saberbird.cidget.data.KeystoreKeyProvider
import io.github.saberbird.cidget.data.RefreshRepository
import io.github.saberbird.cidget.data.RepoFetcher
import io.github.saberbird.cidget.data.RepoListRepository
import io.github.saberbird.cidget.data.RepoListStore
import io.github.saberbird.cidget.data.SettingsRepository
import io.github.saberbird.cidget.data.SnapshotStore
import io.github.saberbird.cidget.data.TokenStore
import io.github.saberbird.cidget.github.GitHubClient
import io.github.saberbird.cidget.github.createHttpClient
import io.github.saberbird.cidget.github.fetchRepoState
import io.github.saberbird.cidget.github.withTokenProvider
import io.github.saberbird.cidget.widget.CidgetWidget
import io.github.saberbird.cidget.work.RefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import okhttp3.OkHttpClient
import java.io.File

/** Builds and holds the app-wide singletons. Created once in `Application.onCreate`. */
class AppGraph(val app: Application) {
    val tokenStore: TokenStore = TokenStore(File(app.filesDir, "token.bin"), KeystoreKeyProvider())

    /**
     * Shared OkHttp client with the ETag cache. The cache directory is `http2`: entries written before the
     * `Vary: Authorization` fix (see `createHttpClient`) could hold a token, so the old `http` directory is deleted.
     */
    val http: OkHttpClient = createHttpClient(
        cacheDir = File(app.cacheDir, "http2"),
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

    // Redraw requests are queued and merged: a tap or a refresh asks for a redraw from several places, and every
    // Glance update re-renders the whole widget. One update per widget per burst keeps taps fast.
    private val redraws = Channel<Int>(Channel.UNLIMITED)

    /** Redraws widget [appWidgetId] soon (merged with other requests). */
    fun redraw(appWidgetId: Int) {
        redraws.trySend(appWidgetId)
    }

    /** Redraws every widget soon. */
    fun redrawAll() {
        redraws.trySend(ALL)
    }

    /** Redraws the widgets that show [repo]. */
    suspend fun redrawRepo(repo: String) {
        settings.allWidgets().filterValues { it.repo == repo }.keys.forEach(::redraw)
    }

    val refresh: RefreshRepository = RefreshRepository(
        tokens = tokenStore,
        snapshots = snapshots,
        fetcher = RepoFetcher { tokenId, owner, repo, s -> githubFor(tokenId).fetchRepoState(owner, repo, s) },
        tokenIdFor = settings::tokenIdForRepo,
        clock = Clock.System,
        onChanged = ::redrawRepo,
    )

    val repoListStore: RepoListStore = RepoListStore(File(app.filesDir, "repos.json"))

    val repoLists: RepoListRepository = RepoListRepository(
        tokens = tokenStore,
        store = repoListStore,
        listFor = { tokenId -> githubFor(tokenId).listUserRepos() },
        clock = Clock.System,
        onChanged = { redrawAll() },
    )

    /** Shows "loading" in the widget's repo list at once, then queues the worker that refreshes it. */
    fun requestRepoList() {
        background.launch {
            repoLists.prepare()
            RefreshScheduler.refreshRepoList(app)
        }
    }

    /**
     * Opens the in-widget screen [requested] (or, by default, the repo list for a new widget without a repo when a
     * token exists, else the settings) and redraws the widget. Returns the stored config. Used by the system's
     * "widget settings" entry.
     */
    suspend fun openInWidget(appWidgetId: Int, requested: WidgetView?): WidgetConfig {
        val current = settings.getWidgetConfig(appWidgetId)
        val view = requested ?: if (current.repo == null && tokenStore.hasToken()) WidgetView.REPOS else WidgetView.SETTINGS
        settings.updateWidget(appWidgetId) { it.copy(view = view, confirm = null) }
        if (view == WidgetView.REPOS) requestRepoList()
        redraw(appWidgetId)
        // The first update of a widget that has a configuration activity can be dropped while the launcher is
        // still placing it; draw it again once it is surely on the home screen.
        background.launch {
            delay(FIRST_DRAW_RETRY_MS)
            redraw(appWidgetId)
        }
        return settings.getWidgetConfig(appWidgetId)
    }

    /**
     * Picks [repo] (seen by [tokenId]) for widget [appWidgetId], returns it to the branch list, prunes snapshots and
     * queues a refresh of the repo.
     */
    suspend fun pickRepo(appWidgetId: Int, repo: String, tokenId: String?) {
        val before = settings.getWidgetRepo(appWidgetId)
        // One atomic write (also back to the branch list); the widget observes it and redraws, so the switch is
        // instant (D26).
        settings.setWidgetRepo(appWidgetId, repo, tokenId)
        redraw(appWidgetId)
        // Everything slower runs in the background, so the next tap is never waiting for this repo.
        background.launch {
            val inUse = settings.reposInUse()
            if (before != null && before != repo && before !in inUse) RefreshScheduler.cancel(app, before)
            snapshots.deleteExcept(inUse)
            requestRefresh(repo)
            schedulePeriodic()
        }
    }

    /** Manual refresh: shows `[...]` immediately, then queues the work. */
    suspend fun requestRefresh(repo: String) {
        if (refresh.prepare(repo)) refreshNow(repo)
    }

    /** Queues a refresh for [repo] (`owner/repo`). */
    fun refreshNow(repo: String) = RefreshScheduler.refreshNow(app, repo)

    /** Matches the periodic background refresh to the settings and the repos shown (T11). */
    suspend fun schedulePeriodic() {
        runCatching { RefreshScheduler.schedulePeriodic(app, this) }
    }

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
        redrawAll()
        if (tokenStore.hasToken()) {
            settings.reposInUse().forEach(::refreshNow)
            RefreshScheduler.refreshRepoList(app)
        }
    }

    init {
        background.launch {
            for (first in redraws) {
                delay(REDRAW_MERGE_MS)
                val ids = mutableSetOf(first)
                while (true) ids += redraws.tryReceive().getOrNull() ?: break
                if (ALL in ids) {
                    runCatching { CidgetWidget().updateAll(app) }
                } else {
                    val manager = GlanceAppWidgetManager(app)
                    ids.forEach { id -> runCatching { CidgetWidget().update(app, manager.getGlanceIdBy(id)) } }
                }
            }
        }
        background.launch(Dispatchers.IO) {
            // One-time cleanup: the cache before the Vary fix may hold tokens in its entry metadata.
            runCatching { File(app.cacheDir, "http").takeIf { it.exists() }?.deleteRecursively() }
        }
        background.launch {
            runCatching { settings.dropLegacyKeys() }
            schedulePeriodic()
        }
    }

    private fun versionName(): String =
        runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "0"

    private companion object {
        const val ALL = Int.MIN_VALUE
        const val REDRAW_MERGE_MS = 30L
        const val FIRST_DRAW_RETRY_MS = 1500L
    }
}
