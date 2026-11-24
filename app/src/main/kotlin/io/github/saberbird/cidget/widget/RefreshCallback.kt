package io.github.saberbird.cidget.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import io.github.saberbird.cidget.graph

/** `[⟳]`: refresh the repo of this widget. `[...]` shows at once, the work runs expedited. */
class RefreshCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val graph = context.graph
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        val repo = graph.settings.getWidgetRepo(appWidgetId) ?: return
        graph.requestRefresh(repo)
    }
}
