package io.github.saberbird.cidget.widget.ui

import io.github.saberbird.cidget.core.ColumnId
import io.github.saberbird.cidget.core.DisplaySettings
import io.github.saberbird.cidget.core.GlyphStyle
import io.github.saberbird.cidget.core.Language
import io.github.saberbird.cidget.core.TinySource
import io.github.saberbird.cidget.core.TinyTap
import io.github.saberbird.cidget.core.WidgetView
import io.github.saberbird.cidget.di.AppGraph
import io.github.saberbird.cidget.settings.TokenController

/**
 * Runs the [UiAction.Do] actions. Every change is one atomic settings write; the widgets redraw from the
 * settings observer in [AppGraph], so the controller never draws anything itself.
 */
internal class UiController(private val graph: AppGraph) {
    suspend fun handle(appWidgetId: Int, action: UiAction.Do) {
        val settings = graph.settings
        suspend fun edit(transform: (DisplaySettings) -> DisplaySettings) {
            settings.updateWidget(appWidgetId) { it.copy(display = transform(it.display).normalized()) }
        }
        when (action.kind) {
            "view" -> {
                val view = runCatching { WidgetView.valueOf(action.arg) }.getOrNull() ?: return
                settings.updateWidget(appWidgetId) { it.copy(view = view) }
                if (view == WidgetView.REPOS) graph.requestRepoList()
            }
            "refresh" -> {
                // One tap refreshes the repo list and every repo some widget shows, so switching is instant.
                graph.requestRepoList()
                settings.reposInUse().forEach { graph.requestRefresh(it) }
            }
            "pick" -> graph.pickRepo(appWidgetId, action.arg, action.arg2.ifEmpty { null })
            "deltoken" -> TokenController(graph).delete(action.arg)
            "coltoggle" -> {
                val id = column(action.arg) ?: return
                settings.updateWidget(appWidgetId) { c ->
                    val on = c.display.columns.firstOrNull { it.id == id }?.enabled ?: return@updateWidget c
                    c.display.withColumnEnabled(id, !on)?.let { c.copy(display = it) } ?: c
                }
            }
            "colsel" -> {
                val id = column(action.arg) ?: return
                settings.updateWidget(appWidgetId) { it.copy(selected = id) }
            }
            "colmove" -> settings.updateWidget(appWidgetId) { c ->
                val sel = selectedColumn(c) ?: return@updateWidget c
                c.copy(display = c.display.withColumnMovedVisible(sel, action.arg.toIntOrNull() ?: 0))
            }
            "priomove" -> settings.updateWidget(appWidgetId) { c ->
                val sel = selectedColumn(c) ?: return@updateWidget c
                c.copy(display = c.display.withPriorityMoved(sel, action.arg.toIntOrNull() ?: 0))
            }
            "colcap" -> {
                val id = column(action.arg) ?: return
                val step = action.arg2.toIntOrNull() ?: return
                edit { if (step == 0) it.withColumnMaxChars(id, null) else it.withColumnMaxCharsStep(id, step) }
            }
            "font" -> edit { it.withFontSize(it.fontSizeSp + (action.arg.toIntOrNull() ?: 0)) }
            "glyph" -> runCatching { GlyphStyle.valueOf(action.arg) }.getOrNull()?.let { s -> edit { it.copy(glyphStyle = s) } }
            "tinysrc" -> runCatching { TinySource.valueOf(action.arg) }.getOrNull()?.let { s -> edit { it.copy(tinySource = s) } }
            "tinytap" -> runCatching { TinyTap.valueOf(action.arg) }.getOrNull()?.let { s -> edit { it.copy(tinyTap = s) } }
            "pin" -> edit { it.withPinToggled(action.arg) }
            "lang" -> edit { it.copy(language = if (it.language == Language.EN) Language.UK else Language.EN) }
            // Back to the factory look; the language is kept so the screen does not switch under the user.
            "reset" -> edit { DisplaySettings(language = it.language) }
        }
    }

    private fun column(name: String): ColumnId? = runCatching { ColumnId.valueOf(name) }.getOrNull()
}
