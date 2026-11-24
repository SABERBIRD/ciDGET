package io.github.saberbird.cidget.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import io.github.saberbird.cidget.graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Receives the AppWidget broadcasts, refreshes widgets that have a repo but no data, and cleans up on delete. */
class CidgetWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CidgetWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        background {
            val graph = context.graph
            appWidgetIds.forEach { id ->
                val repo = graph.settings.getWidgetRepo(id) ?: return@forEach
                if (graph.snapshots.get(repo) == null) graph.requestRefresh(repo)
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        background {
            val graph = context.graph
            appWidgetIds.forEach { graph.settings.deleteWidget(it) }
            graph.snapshots.deleteExcept(graph.settings.reposInUse())
        }
    }

    private fun BroadcastReceiver.background(block: suspend () -> Unit) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                block()
            } finally {
                pending.finish()
            }
        }
    }
}
