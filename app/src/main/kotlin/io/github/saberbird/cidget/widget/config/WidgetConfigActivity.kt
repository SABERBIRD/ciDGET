package io.github.saberbird.cidget.widget.config

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.saberbird.cidget.R
import io.github.saberbird.cidget.core.DisplaySettings
import io.github.saberbird.cidget.core.WidgetConfig
import io.github.saberbird.cidget.di.AppGraph
import io.github.saberbird.cidget.graph
import io.github.saberbird.cidget.settings.TokenScreen

/**
 * The app's only settings UI (D22: no launcher icon). It is the widget's configuration activity, opened
 * by the system when the widget is added and from the widget's "reconfigure" menu entry, and by a tap on
 * the widget header as a fallback for launchers without that entry. Backing out keeps the widget
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
        setContent {
            TerminalTheme {
                ConfigRoot(graph, appWidgetId, onFinish = { finish() })
            }
        }
    }
}

/** Where the user is inside the configuration activity. */
internal sealed interface Screen {
    data object Main : Screen
    data object Token : Screen
    data object Repo : Screen
    data class Columns(val target: Target) : Screen
    data class Display(val target: Target) : Screen
    data object CopyFrom : Screen
}

/** Whose display settings a screen edits. */
internal enum class Target { WIDGET, GLOBAL }

@Composable
private fun ConfigRoot(graph: AppGraph, appWidgetId: Int, onFinish: () -> Unit) {
    val tokens by graph.tokenStore.summaries.collectAsState()
    val config by graph.settings.widgetConfig(appWidgetId).collectAsState(WidgetConfig())
    val global by graph.settings.globalDisplay().collectAsState(DisplaySettings())
    var screen by remember { mutableStateOf<Screen>(if (tokens.isEmpty()) Screen.Token else if (config.repo == null) Screen.Repo else Screen.Main) }
    BackHandler(enabled = screen != Screen.Main) { screen = Screen.Main }

    when (val s = screen) {
        Screen.Main -> MainScreen(graph, appWidgetId, config, global) { screen = it }
        Screen.Token -> Column {
            TokenScreen(graph, showContentsHint = false)
            BackButton { screen = Screen.Main }
        }
        Screen.Repo -> if (tokens.isEmpty()) {
            Column {
                Text(stringResource(R.string.picker_need_token), modifier = Modifier.padding(16.dp))
                Column(Modifier.padding(horizontal = 16.dp)) {
                    TermButton(stringResource(R.string.token_title)) { screen = Screen.Token }
                }
            }
        } else {
            RepoPickerScreen(graph, onPicked = { repo, tokenId ->
                graph.pickRepo(appWidgetId, repo, tokenId)
                onFinish()
            })
        }
        is Screen.Columns -> ColumnsScreen(graph, appWidgetId, config, global, s.target) { screen = Screen.Main }
        is Screen.Display -> DisplayScreen(graph, appWidgetId, config, global, s.target) { screen = Screen.Main }
        Screen.CopyFrom -> CopyFromScreen(graph, appWidgetId) { screen = Screen.Main }
    }
}

@Composable
internal fun BackButton(onClick: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) { TermButton(stringResource(R.string.config_back), onClick = onClick) }
}
