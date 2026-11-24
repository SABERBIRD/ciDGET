package io.github.saberbird.cidget.widget.ui

import io.github.saberbird.cidget.core.AppSettings
import io.github.saberbird.cidget.core.ColumnId
import io.github.saberbird.cidget.core.SortKey
import io.github.saberbird.cidget.core.Theme
import io.github.saberbird.cidget.core.Confirm
import io.github.saberbird.cidget.core.ConfirmKind
import io.github.saberbird.cidget.core.DisplaySettings
import io.github.saberbird.cidget.core.GlyphStyle
import io.github.saberbird.cidget.core.Language
import io.github.saberbird.cidget.core.TinySource
import io.github.saberbird.cidget.core.TinyTap
import io.github.saberbird.cidget.core.WidgetView
import io.github.saberbird.cidget.di.AppGraph
import io.github.saberbird.cidget.settings.TokenController

/**
 * Runs the [UiAction.Do] actions. Every change is one atomic settings write followed by one (merged) redraw of
 * the widget; the controller never draws anything itself.
 */
internal class UiController(private val graph: AppGraph) {
    suspend fun handle(appWidgetId: Int, action: UiAction.Do) {
        run(appWidgetId, action)
        graph.redraw(appWidgetId)
    }

    private suspend fun run(appWidgetId: Int, action: UiAction.Do) {
        val settings = graph.settings
        suspend fun edit(transform: (DisplaySettings) -> DisplaySettings) {
            settings.updateWidget(appWidgetId) { it.copy(display = transform(it.display).normalized()) }
        }
        when (action.cmd) {
            Cmd.VIEW -> {
                val view = enumOrNull<WidgetView>(action.arg) ?: return
                settings.updateWidget(appWidgetId) { it.copy(view = view, confirm = null) }
                if (view == WidgetView.REPOS) graph.requestRepoList()
            }
            Cmd.REFRESH -> {
                // One tap refreshes the repo list and every repo some widget shows, so switching is instant.
                graph.requestRepoList()
                settings.reposInUse().forEach { graph.requestRefresh(it) }
            }
            Cmd.PICK -> graph.pickRepo(appWidgetId, action.arg, action.arg2.ifEmpty { null })
            Cmd.ASK -> {
                val kind = enumOrNull<ConfirmKind>(action.arg) ?: return
                settings.updateWidget(appWidgetId) { it.copy(confirm = Confirm(kind, action.arg2)) }
            }
            Cmd.CANCEL -> settings.updateWidget(appWidgetId) { it.copy(confirm = null) }
            Cmd.CONFIRM -> {
                val pending = settings.getWidgetConfig(appWidgetId).confirm ?: return
                settings.updateWidget(appWidgetId) { it.copy(confirm = null) }
                when (pending.kind) {
                    ConfirmKind.DELETE_TOKEN -> TokenController(graph).delete(pending.target)
                    // Back to the factory look; the language is kept so the screen does not switch under the user.
                    ConfirmKind.RESET -> edit { DisplaySettings(language = it.language) }
                }
            }
            Cmd.COL_TOGGLE -> {
                val id = enumOrNull<ColumnId>(action.arg) ?: return
                settings.updateWidget(appWidgetId) { c ->
                    val on = c.display.columns.firstOrNull { it.id == id }?.enabled ?: return@updateWidget c
                    c.display.withColumnEnabled(id, !on)?.let { c.copy(display = it) } ?: c
                }
            }
            Cmd.COL_SELECT -> {
                val id = enumOrNull<ColumnId>(action.arg) ?: return
                settings.updateWidget(appWidgetId) { it.copy(selected = id) }
            }
            Cmd.COL_MOVE -> settings.updateWidget(appWidgetId) { c ->
                val sel = selectedColumn(c) ?: return@updateWidget c
                c.copy(display = c.display.withColumnMovedVisible(sel, action.arg.toIntOrNull() ?: 0))
            }
            Cmd.PRIO_MOVE -> settings.updateWidget(appWidgetId) { c ->
                val sel = selectedColumn(c) ?: return@updateWidget c
                c.copy(display = c.display.withPriorityMoved(sel, action.arg.toIntOrNull() ?: 0))
            }
            Cmd.COL_CAP -> {
                val id = enumOrNull<ColumnId>(action.arg) ?: return
                val step = action.arg2.toIntOrNull() ?: return
                edit { if (step == 0) it.withColumnMaxChars(id, null) else it.withColumnMaxCharsStep(id, step) }
            }
            Cmd.FONT -> edit { it.withFontSize(it.fontSizeSp + (action.arg.toIntOrNull() ?: 0)) }
            Cmd.GLYPH -> enumOrNull<GlyphStyle>(action.arg)?.let { s -> edit { it.copy(glyphStyle = s) } }
            Cmd.TINY_SOURCE -> enumOrNull<TinySource>(action.arg)?.let { s -> edit { it.copy(tinySource = s) } }
            Cmd.TINY_TAP -> enumOrNull<TinyTap>(action.arg)?.let { s -> edit { it.copy(tinyTap = s) } }
            Cmd.PIN -> edit { it.withPinToggled(action.arg) }
            Cmd.LANG -> edit { it.copy(language = if (it.language == Language.EN) Language.UK else Language.EN) }
            Cmd.THEME -> enumOrNull<Theme>(action.arg)?.let { t -> edit { it.copy(theme = t) } }
            Cmd.SORT -> enumOrNull<SortKey>(action.arg)?.let { k -> edit { it.copy(sortKey = k) } }
            Cmd.IDLE_DAYS -> edit { it.withIdleDaysStep(action.arg.toIntOrNull() ?: 0) }
            Cmd.MAX_ROWS -> edit { it.withMaxRowsStep(action.arg.toIntOrNull() ?: 0) }
            Cmd.HIDE_NO_RUNS -> edit { it.copy(hideBranchesWithoutRuns = !it.hideBranchesWithoutRuns) }
            Cmd.REFRESH_INTERVAL -> editApp { it.withRefreshStep(action.arg.toIntOrNull() ?: 0) }
            Cmd.REFRESH_UNMETERED -> editApp { it.copy(unmeteredOnly = !it.unmeteredOnly) }
            Cmd.REFRESH_BATTERY -> editApp { it.copy(batteryNotLow = !it.batteryNotLow) }
            Cmd.EXPIRY_WARN -> editApp { it.withExpiryWarnStep(action.arg.toIntOrNull() ?: 0) }
        }
    }

    /** App-wide settings change: every widget may show them (stale marker, expiry warning). */
    private suspend fun editApp(transform: (AppSettings) -> AppSettings) {
        graph.settings.updateAppSettings(transform)
        graph.schedulePeriodic()
        graph.redrawAll()
    }
}

/** The enum constant named [name], or null. */
internal inline fun <reified T : Enum<T>> enumOrNull(name: String): T? = enumValues<T>().firstOrNull { it.name == name }
