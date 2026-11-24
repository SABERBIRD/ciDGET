package io.github.saberbird.cidget.widget

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import io.github.saberbird.cidget.data.RepoList
import io.github.saberbird.cidget.graph
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock

/**
 * The widget. It never touches the network: it reads the widget's config, the snapshot, the repo list and the
 * token summaries from local stores, so it renders instantly from cache (also right after a reboot).
 */
class CidgetWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val graph = context.graph
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val initialConfig = graph.settings.getWidgetConfig(appWidgetId)
        val initialRepo = initialConfig.repo
        val initialSnapshot = initialRepo?.let { graph.snapshots.get(it) }
        val initialApp = graph.settings.getAppSettings()
        provideContent {
            // Every flow is created once: a new flow on each recomposition would restart its collection each time.
            val configFlow = remember { graph.settings.widgetConfig(appWidgetId) }
            val config by configFlow.collectAsState(initialConfig)
            val repo = config.repo
            val snapshots = remember(repo) { repo?.let { graph.snapshots.observe(it) } ?: flowOf(null) }
            val snapshot by snapshots.collectAsState(if (repo == initialRepo) initialSnapshot else null)
            val tokens by graph.tokenStore.summaries.collectAsState()
            val tokenState = remember(tokens, config.tokenId) { graph.tokenStore.stateOf(config.tokenId) }
            val repoListFlow = remember { graph.repoListStore.observe() }
            val repoList by repoListFlow.collectAsState(RepoList())
            val appFlow = remember { graph.settings.appSettings }
            val app by appFlow.collectAsState(initialApp)
            WidgetContent(
                appWidgetId = appWidgetId,
                config = config,
                tokens = tokens,
                tokenState = tokenState,
                repoList = repoList,
                snapshot = snapshot,
                now = Clock.System.now(),
                glyphs = Glyphs.system,
                app = app,
            )
        }
    }
}
