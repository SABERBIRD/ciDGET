package io.github.saberbird.cidget.widget.config

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.saberbird.cidget.R
import io.github.saberbird.cidget.core.ColumnConfig
import io.github.saberbird.cidget.core.ColumnId
import io.github.saberbird.cidget.core.DisplaySettings
import io.github.saberbird.cidget.core.GlyphStyle
import io.github.saberbird.cidget.core.TinySource
import io.github.saberbird.cidget.core.WidgetConfig
import io.github.saberbird.cidget.di.AppGraph
import io.github.saberbird.cidget.settings.TokenSection
import io.github.saberbird.cidget.widget.Palette
import kotlinx.coroutines.launch

@StringRes
private fun columnName(id: ColumnId): Int = when (id) {
    ColumnId.STATUS -> R.string.col_status
    ColumnId.BRANCH -> R.string.col_branch
    ColumnId.AGE -> R.string.col_age
    ColumnId.RUN_NUMBER -> R.string.col_run_number
    ColumnId.COPY -> R.string.col_copy
    ColumnId.SHARE -> R.string.col_share
    ColumnId.WORKFLOW -> R.string.col_workflow
    ColumnId.SHA -> R.string.col_sha
    ColumnId.DURATION -> R.string.col_duration
    ColumnId.TITLE -> R.string.col_title
    ColumnId.ACTOR -> R.string.col_actor
    ColumnId.EVENT -> R.string.col_event
}

/** The settings a [target] edits right now (a widget following the global defaults shows those). */
private fun current(target: Target, config: WidgetConfig, global: DisplaySettings): DisplaySettings =
    if (target == Target.GLOBAL) global else config.effective(global)

/** Saves [next] for [target]. Editing a widget's own settings switches it off the global defaults. */
private suspend fun save(graph: AppGraph, appWidgetId: Int, target: Target, next: DisplaySettings) {
    when (target) {
        Target.GLOBAL -> graph.settings.updateGlobal { next }
        Target.WIDGET -> graph.settings.updateWidget(appWidgetId) { it.copy(useGlobalDefaults = false, display = next) }
    }
}

@Composable
internal fun MainScreen(
    graph: AppGraph,
    appWidgetId: Int,
    config: WidgetConfig,
    global: DisplaySettings,
    go: (Screen) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var otherWidgets by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { otherWidgets = graph.settings.allWidgets().count { it.key != appWidgetId && it.value.repo != null } }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TermTitle(stringResource(R.string.cfg_title))
        Text(stringResource(R.string.cfg_repo, config.repo ?: stringResource(R.string.cfg_none)))
        TermButton(stringResource(R.string.cfg_change_repo)) { go(Screen.Repo) }
        TermRule()
        TokenSection(graph, showContentsHint = false)
        TermRule()
        TermToggle(stringResource(R.string.cfg_use_defaults), config.useGlobalDefaults) { on ->
            scope.launch {
                graph.settings.updateWidget(appWidgetId) {
                    // Turning it off starts from what the widget shows now.
                    it.copy(useGlobalDefaults = on, display = if (on) it.display else global)
                }
            }
        }
        TermButton(stringResource(R.string.cfg_columns)) { go(Screen.Columns(Target.WIDGET)) }
        TermButton(stringResource(R.string.cfg_display)) { go(Screen.Display(Target.WIDGET)) }
        if (otherWidgets > 0) {
            TermButton(stringResource(R.string.cfg_copy_from)) { go(Screen.CopyFrom) }
        }
        TermRule()
        TermTitle(stringResource(R.string.cfg_global))
        TermButton(stringResource(R.string.cfg_columns)) { go(Screen.Columns(Target.GLOBAL)) }
        TermButton(stringResource(R.string.cfg_display)) { go(Screen.Display(Target.GLOBAL)) }
        TermRule()
        Text(stringResource(R.string.cfg_remove_hint), color = Palette.dim)
        TermButton(stringResource(R.string.cfg_uninstall)) { uninstall(context) }
    }
}

/** Opens the system's uninstall dialog for this app; falls back to the app's settings page. */
private fun uninstall(context: Context) {
    val uri = Uri.parse("package:${context.packageName}")
    try {
        context.startActivity(Intent(Intent.ACTION_DELETE, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
internal fun ColumnsScreen(graph: AppGraph, appWidgetId: Int, config: WidgetConfig, global: DisplaySettings, target: Target, back: () -> Unit) {
    val scope = rememberCoroutineScope()
    val display = current(target, config, global)
    var warn by remember { mutableStateOf(false) }
    fun edit(next: DisplaySettings) = scope.launch { save(graph, appWidgetId, target, next) }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
        TermTitle(stringResource(if (target == Target.GLOBAL) R.string.cfg_global else R.string.cfg_columns))
        if (target == Target.WIDGET && config.useGlobalDefaults) {
            Text(stringResource(R.string.cfg_editing_leaves_defaults), style = MaterialTheme.typography.bodySmall, color = Palette.dim)
        }
        if (warn) Text(stringResource(R.string.cfg_status_or_branch), color = MaterialTheme.colorScheme.error)
        display.columns.forEachIndexed { index, col ->
            TermRule()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    TermToggle(stringResource(columnName(col.id)), col.enabled) { on ->
                        val next = display.withColumnEnabled(col.id, on)
                        if (next == null) warn = true else { warn = false; edit(next) }
                    }
                }
                TermButton("▲", enabled = index > 0) { edit(display.withColumnMoved(col.id, -1)) }
                TermButton("▼", enabled = index < display.columns.size - 1) { edit(display.withColumnMoved(col.id, 1)) }
            }
            MaxCharsField(col) { edit(display.withColumnMaxChars(col.id, it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PriorityChoice(R.string.prio_high, col, ColumnConfig.HIGH) { edit(display.withColumnPriority(col.id, ColumnConfig.HIGH)) }
                PriorityChoice(R.string.prio_medium, col, ColumnConfig.MEDIUM) { edit(display.withColumnPriority(col.id, ColumnConfig.MEDIUM)) }
                PriorityChoice(R.string.prio_low, col, ColumnConfig.LOW) { edit(display.withColumnPriority(col.id, ColumnConfig.LOW)) }
            }
        }
        BackButton(back)
    }
}

/** Optional width cap in characters for one column; empty means the column's default (D26). */
@Composable
private fun MaxCharsField(col: ColumnConfig, onChange: (Int?) -> Unit) {
    var text by remember(col.id, col.maxChars) { mutableStateOf(col.maxChars?.toString().orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            val digits = v.filter { it.isDigit() }.take(2)
            text = digits
            onChange(digits.toIntOrNull())
        },
        label = { Text(stringResource(R.string.col_max)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A column's stored priority counts as the nearest of high / medium / low. */
private fun bucket(priority: Int): Int = when {
    priority >= 65 -> ColumnConfig.HIGH
    priority >= 35 -> ColumnConfig.MEDIUM
    else -> ColumnConfig.LOW
}

@Composable
private fun PriorityChoice(@StringRes label: Int, col: ColumnConfig, value: Int, onClick: () -> Unit) {
    // Defaults such as 90 or 70 show as "high"; picking a button stores 80, 50 or 20.
    TermButton(stringResource(label), selected = bucket(col.priority) == value, onClick = onClick)
}

@Composable
internal fun DisplayScreen(graph: AppGraph, appWidgetId: Int, config: WidgetConfig, global: DisplaySettings, target: Target, back: () -> Unit) {
    val scope = rememberCoroutineScope()
    val display = current(target, config, global)
    fun edit(next: DisplaySettings) = scope.launch { save(graph, appWidgetId, target, next) }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TermTitle(stringResource(if (target == Target.GLOBAL) R.string.cfg_global else R.string.cfg_display))
        if (target == Target.WIDGET && config.useGlobalDefaults) {
            Text(stringResource(R.string.cfg_editing_leaves_defaults), style = MaterialTheme.typography.bodySmall, color = Palette.dim)
        }
        Text(stringResource(R.string.disp_font, display.fontSizeSp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TermButton("−", enabled = display.fontSizeSp > DisplaySettings.MIN_FONT_SP) { edit(display.withFontSize(display.fontSizeSp - 1)) }
            TermButton("+", enabled = display.fontSizeSp < DisplaySettings.MAX_FONT_SP) { edit(display.withFontSize(display.fontSizeSp + 1)) }
        }
        TermRule()
        Text(stringResource(R.string.disp_glyph), color = Palette.dim)
        TermChoice(stringResource(R.string.disp_glyph_unicode), display.glyphStyle == GlyphStyle.UNICODE) { edit(display.copy(glyphStyle = GlyphStyle.UNICODE)) }
        TermChoice(stringResource(R.string.disp_glyph_bracket), display.glyphStyle == GlyphStyle.BRACKET) { edit(display.copy(glyphStyle = GlyphStyle.BRACKET)) }
        TermRule()
        Text(stringResource(R.string.disp_tiny), color = Palette.dim)
        TermChoice(stringResource(R.string.disp_tiny_worst), display.tinySource == TinySource.WORST) { edit(display.copy(tinySource = TinySource.WORST)) }
        TermChoice(stringResource(R.string.disp_tiny_pinned), display.tinySource == TinySource.PINNED) { edit(display.copy(tinySource = TinySource.PINNED)) }
        BackButton(back)
    }
}

@Composable
internal fun CopyFromScreen(graph: AppGraph, appWidgetId: Int, back: () -> Unit) {
    val scope = rememberCoroutineScope()
    var others by remember { mutableStateOf<List<Pair<Int, String>>?>(null) }
    LaunchedEffect(Unit) {
        others = graph.settings.allWidgets().filter { it.key != appWidgetId && it.value.repo != null }.map { it.key to it.value.repo.orEmpty() }
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
        TermTitle(stringResource(R.string.cfg_copy_from))
        val list = others
        if (list != null && list.isEmpty()) Text(stringResource(R.string.copy_none))
        list?.forEach { (id, repo) ->
            Text(
                "> $repo",
                Modifier.fillMaxWidth().clickable { scope.launch { graph.settings.copySettings(id, appWidgetId); back() } }.padding(vertical = 14.dp),
            )
        }
        BackButton(back)
    }
}
