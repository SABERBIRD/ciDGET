package io.github.saberbird.cidget.widget.config

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.saberbird.cidget.R
import io.github.saberbird.cidget.core.ColumnConfig
import io.github.saberbird.cidget.core.ColumnId
import io.github.saberbird.cidget.core.DisplaySettings
import io.github.saberbird.cidget.core.GlyphStyle
import io.github.saberbird.cidget.core.TinySource
import io.github.saberbird.cidget.core.WidgetConfig
import io.github.saberbird.cidget.di.AppGraph
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
    tokenCount: Int,
    go: (Screen) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var otherWidgets by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { otherWidgets = graph.settings.allWidgets().count { it.key != appWidgetId && it.value.repo != null } }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.cfg_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.cfg_repo, config.repo ?: stringResource(R.string.cfg_none)))
        FilledTonalButton(onClick = { go(Screen.Repo) }) { Text(stringResource(R.string.cfg_change_repo)) }
        Text(if (tokenCount == 0) stringResource(R.string.token_none) else pluralStringResource(R.plurals.token_count, tokenCount, tokenCount))
        OutlinedButton(onClick = { go(Screen.Token) }) { Text(stringResource(R.string.token_title)) }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.cfg_use_defaults), Modifier.weight(1f))
            Switch(
                checked = config.useGlobalDefaults,
                onCheckedChange = { on ->
                    scope.launch {
                        graph.settings.updateWidget(appWidgetId) {
                            // Turning it off starts from what the widget shows now.
                            it.copy(useGlobalDefaults = on, display = if (on) it.display else global)
                        }
                    }
                },
            )
        }
        OutlinedButton(onClick = { go(Screen.Columns(Target.WIDGET)) }) { Text(stringResource(R.string.cfg_columns)) }
        OutlinedButton(onClick = { go(Screen.Display(Target.WIDGET)) }) { Text(stringResource(R.string.cfg_display)) }
        if (otherWidgets > 0) {
            OutlinedButton(onClick = { go(Screen.CopyFrom) }) { Text(stringResource(R.string.cfg_copy_from)) }
        }
        HorizontalDivider()
        Text(stringResource(R.string.cfg_global), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { go(Screen.Columns(Target.GLOBAL)) }) { Text(stringResource(R.string.cfg_columns)) }
        OutlinedButton(onClick = { go(Screen.Display(Target.GLOBAL)) }) { Text(stringResource(R.string.cfg_display)) }
    }
}

@Composable
internal fun ColumnsScreen(graph: AppGraph, appWidgetId: Int, config: WidgetConfig, global: DisplaySettings, target: Target, back: () -> Unit) {
    val scope = rememberCoroutineScope()
    val display = current(target, config, global)
    var warn by remember { mutableStateOf(false) }
    fun edit(next: DisplaySettings) = scope.launch { save(graph, appWidgetId, target, next) }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(
            stringResource(if (target == Target.GLOBAL) R.string.cfg_global else R.string.cfg_columns),
            style = MaterialTheme.typography.titleLarge,
        )
        if (target == Target.WIDGET && config.useGlobalDefaults) {
            Text(stringResource(R.string.cfg_editing_leaves_defaults), style = MaterialTheme.typography.bodySmall)
        }
        if (warn) Text(stringResource(R.string.cfg_status_or_branch), color = MaterialTheme.colorScheme.error)
        display.columns.forEachIndexed { index, col ->
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = col.enabled,
                    onCheckedChange = { on ->
                        val next = display.withColumnEnabled(col.id, on)
                        if (next == null) warn = true else { warn = false; edit(next) }
                    },
                )
                Text(stringResource(columnName(col.id)), Modifier.weight(1f).padding(start = 12.dp))
                TextButton(enabled = index > 0, onClick = { edit(display.withColumnMoved(col.id, -1)) }) { Text("▲") }
                TextButton(enabled = index < display.columns.size - 1, onClick = { edit(display.withColumnMoved(col.id, 1)) }) { Text("▼") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PriorityChoice(R.string.prio_high, col, ColumnConfig.HIGH) { edit(display.withColumnPriority(col.id, ColumnConfig.HIGH)) }
                PriorityChoice(R.string.prio_medium, col, ColumnConfig.MEDIUM) { edit(display.withColumnPriority(col.id, ColumnConfig.MEDIUM)) }
                PriorityChoice(R.string.prio_low, col, ColumnConfig.LOW) { edit(display.withColumnPriority(col.id, ColumnConfig.LOW)) }
            }
        }
        BackButton(back)
    }
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
    if (bucket(col.priority) == value) {
        Button(onClick = onClick) { Text(stringResource(label)) }
    } else {
        OutlinedButton(onClick = onClick) { Text(stringResource(label)) }
    }
}

@Composable
internal fun DisplayScreen(graph: AppGraph, appWidgetId: Int, config: WidgetConfig, global: DisplaySettings, target: Target, back: () -> Unit) {
    val scope = rememberCoroutineScope()
    val display = current(target, config, global)
    fun edit(next: DisplaySettings) = scope.launch { save(graph, appWidgetId, target, next) }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(if (target == Target.GLOBAL) R.string.cfg_global else R.string.cfg_display),
            style = MaterialTheme.typography.titleLarge,
        )
        if (target == Target.WIDGET && config.useGlobalDefaults) {
            Text(stringResource(R.string.cfg_editing_leaves_defaults), style = MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(R.string.disp_font, display.fontSizeSp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = display.fontSizeSp > DisplaySettings.MIN_FONT_SP, onClick = { edit(display.withFontSize(display.fontSizeSp - 1)) }) { Text("−") }
            OutlinedButton(enabled = display.fontSizeSp < DisplaySettings.MAX_FONT_SP, onClick = { edit(display.withFontSize(display.fontSizeSp + 1)) }) { Text("+") }
        }
        HorizontalDivider()
        Text(stringResource(R.string.disp_glyph), style = MaterialTheme.typography.titleMedium)
        Choice(R.string.disp_glyph_unicode, display.glyphStyle == GlyphStyle.UNICODE) { edit(display.copy(glyphStyle = GlyphStyle.UNICODE)) }
        Choice(R.string.disp_glyph_bracket, display.glyphStyle == GlyphStyle.BRACKET) { edit(display.copy(glyphStyle = GlyphStyle.BRACKET)) }
        HorizontalDivider()
        Text(stringResource(R.string.disp_tiny), style = MaterialTheme.typography.titleMedium)
        Choice(R.string.disp_tiny_worst, display.tinySource == TinySource.WORST) { edit(display.copy(tinySource = TinySource.WORST)) }
        Choice(R.string.disp_tiny_pinned, display.tinySource == TinySource.PINNED) { edit(display.copy(tinySource = TinySource.PINNED)) }
        BackButton(back)
    }
}

@Composable
private fun Choice(@StringRes label: Int, selected: Boolean, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onSelect), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(stringResource(label))
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
        Text(stringResource(R.string.cfg_copy_from), style = MaterialTheme.typography.titleLarge)
        val list = others
        if (list != null && list.isEmpty()) Text(stringResource(R.string.copy_none))
        list?.forEach { (id, repo) ->
            Text(
                repo,
                Modifier.fillMaxWidth().clickable { scope.launch { graph.settings.copySettings(id, appWidgetId); back() } }.padding(vertical = 14.dp),
            )
        }
        BackButton(back)
    }
}
