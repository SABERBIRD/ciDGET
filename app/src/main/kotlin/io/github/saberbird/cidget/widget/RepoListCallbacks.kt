package io.github.saberbird.cidget.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import io.github.saberbird.cidget.graph

/** Header tap: replaces the branch list with the repo list (`ls`) and refreshes that list in the worker. */
class OpenRepoListCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val graph = context.graph
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        graph.settings.setRepoListOpen(appWidgetId, true)
        CidgetWidget().update(context, glanceId)
        graph.requestRepoList()
    }
}

/** `[x]` in the repo list: back to the branch list without changing the repo. */
class CloseRepoListCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        context.graph.settings.setRepoListOpen(appWidgetId, false)
        CidgetWidget().update(context, glanceId)
    }
}

/** A repo row in the repo list: switch this widget to it and go back to the branch list. */
class PickRepoCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val repo = parameters[REPO_KEY] ?: return
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        context.graph.pickRepo(appWidgetId, repo, parameters[TOKEN_KEY])
    }

    companion object {
        val REPO_KEY = ActionParameters.Key<String>("repo")
        val TOKEN_KEY = ActionParameters.Key<String>("tokenId")
    }
}
