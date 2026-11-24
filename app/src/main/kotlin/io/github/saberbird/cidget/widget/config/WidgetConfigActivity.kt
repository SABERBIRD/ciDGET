package io.github.saberbird.cidget.widget.config

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.glance.appwidget.GlanceAppWidgetManager
import io.github.saberbird.cidget.core.WidgetView
import io.github.saberbird.cidget.graph
import io.github.saberbird.cidget.widget.CidgetWidget
import io.github.saberbird.cidget.widget.ui.WideTerminal
import kotlinx.coroutines.launch

/**
 * The widget's configuration activity (D28). The settings live inside the widget, so for a widget big enough
 * to show them this activity has no window: it switches the widget to its settings (or to the repo list when
 * a new widget has no repo yet), redraws the widget (the system may hold back the first update of a widget
 * that has a configuration activity) and finishes. For a widget too small, and for the narrow widget's `~$`
 * icon, it shows the same screens full screen ([WideTerminal]). Backing out keeps the widget
 * (`configuration_optional`).
 */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        val graph = applicationContext.graph
        val wide = intent.getBooleanExtra(EXTRA_WIDE, false) || isSmall(appWidgetId)
        graph.background.launch {
            val config = graph.settings.getWidgetConfig(appWidgetId)
            val requested = intent.getStringExtra(EXTRA_VIEW)?.let { runCatching { WidgetView.valueOf(it) }.getOrNull() }
            // A new widget without a repo starts at the repo list when a token exists, else at the settings.
            val view = requested ?: if (config.repo == null && graph.tokenStore.hasToken()) WidgetView.REPOS else WidgetView.SETTINGS
            graph.settings.updateWidget(appWidgetId) { it.copy(view = view) }
            if (view == WidgetView.REPOS) graph.requestRepoList()
            runCatching {
                val glanceId = GlanceAppWidgetManager(applicationContext).getGlanceIdBy(appWidgetId)
                CidgetWidget().update(applicationContext, glanceId)
            }
            runOnUiThread {
                if (wide) {
                    setContent { WideTerminal(graph, appWidgetId, onClose = { finish() }) }
                } else {
                    finish()
                }
            }
        }
    }

    /** True when the launcher reports a widget smaller than one terminal screen needs (one cell wide or short). */
    private fun isSmall(appWidgetId: Int): Boolean {
        val options = AppWidgetManager.getInstance(this).getAppWidgetOptions(appWidgetId)
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        return (width in 1 until SMALL_DP) || (height in 1 until SMALL_DP)
    }

    companion object {
        private const val EXTRA_WIDE = "wide"
        private const val EXTRA_VIEW = "view"
        private const val SMALL_DP = 110

        /** Opens the wide terminal at the repo list (the narrow widget's `~$` icon and a tap on an empty 1×1). */
        fun wideIntent(context: Context, appWidgetId: Int): Intent =
            Intent(context, WidgetConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .putExtra(EXTRA_WIDE, true)
                .putExtra(EXTRA_VIEW, WidgetView.REPOS.name)
                .setData(Uri.parse("cidget://wide/$appWidgetId"))
    }
}
