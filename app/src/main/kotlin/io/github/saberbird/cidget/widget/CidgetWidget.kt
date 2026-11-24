package io.github.saberbird.cidget.widget

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import io.github.saberbird.cidget.core.DisplaySettings
import androidx.glance.appwidget.provideContent
import io.github.saberbird.cidget.data.RepoList
import io.github.saberbird.cidget.data.TokenState
import io.github.saberbird.cidget.graph
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock

/**
 * The list widget. It never touches the network: it reads the widget's repo, the snapshot and the token
 * state from local stores, so it renders instantly from cache (also right after a reboot).
 */
class CidgetWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val graph = context.graph
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val initialConfig = graph.settings.getWidgetConfig(appWidgetId)
        val initialRepo = initialConfig.repo
        val initialSnapshot = initialRepo?.let { graph.snapshots.get(it) }
        provideContent {
            val config by graph.settings.widgetConfig(appWidgetId).collectAsState(initialConfig)
            val repo = config.repo
            val snapshots = remember(repo) { repo?.let { graph.snapshots.observe(it) } ?: flowOf(null) }
            val snapshot by snapshots.collectAsState(if (repo == initialRepo) initialSnapshot else null)
            val tokens by graph.tokenStore.summaries.collectAsState()
            val tokenState = remember(tokens, config.tokenId) { graph.tokenStore.stateOf(config.tokenId) }
            val display by graph.settings.effectiveDisplay(appWidgetId).collectAsState(DisplaySettings())
            val listOpen by graph.settings.repoListOpen(appWidgetId).collectAsState(false)
            val repoList by graph.repoListStore.observe().collectAsState(RepoList())
            WidgetContent(
                appWidgetId = appWidgetId,
                repo = repo,
                snapshot = snapshot,
                tokenPresent = tokenState != null,
                tokenInvalid = tokenState == TokenState.INVALID,
                now = Clock.System.now(),
                glyphs = Glyphs.system,
                display = display,
                repoListOpen = listOpen,
                repoList = repoList,
            )
        }
    }
}
