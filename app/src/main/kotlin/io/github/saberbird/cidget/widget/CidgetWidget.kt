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
        val initialRepo = graph.settings.getWidgetRepo(appWidgetId)
        val initialSnapshot = initialRepo?.let { graph.snapshots.get(it) }
        provideContent {
            val repo by graph.settings.widgetRepo(appWidgetId).collectAsState(initialRepo)
            val snapshots = remember(repo) { repo?.let { graph.snapshots.observe(it) } ?: flowOf(null) }
            val snapshot by snapshots.collectAsState(if (repo == initialRepo) initialSnapshot else null)
            val token by graph.tokenStore.summary.collectAsState()
            val display by graph.settings.effectiveDisplay(appWidgetId).collectAsState(DisplaySettings())
            WidgetContent(
                appWidgetId = appWidgetId,
                repo = repo,
                snapshot = snapshot,
                tokenPresent = token != null,
                tokenInvalid = token?.state == io.github.saberbird.cidget.data.TokenState.INVALID,
                now = Clock.System.now(),
                glyphs = Glyphs.system,
                display = display,
            )
        }
    }
}
