package io.github.saberbird.cidget.core

import kotlinx.serialization.Serializable

/** Status glyph style (D10): Unicode `✓ ✗ ● ○` or ASCII brackets `[OK] [FAIL] [RUN] [--]`. */
@Serializable
public enum class GlyphStyle { UNICODE, BRACKET }

/** What the one-glyph TINY widget shows (D13): the worst status of the repo, or of the first pinned branch. */
@Serializable
public enum class TinySource { WORST, PINNED }

/**
 * Everything about how a widget looks and what it lists. A widget either follows the global defaults or
 * owns a copy of this (WIDGET.md §5).
 */
@Serializable
public data class DisplaySettings(
    /** All 12 columns in display order. */
    val columns: List<ColumnConfig> = ColumnConfig.defaults(),
    /** 11–16 sp. */
    val fontSizeSp: Int = DEFAULT_FONT_SP,
    val glyphStyle: GlyphStyle = GlyphStyle.UNICODE,
    val tinySource: TinySource = TinySource.WORST,
    val sortKey: SortKey = SortKey.RECENT,
    val pinned: List<String> = emptyList(),
    val hideIdleDays: Int? = 30,
    val maxRows: Int? = 50,
    val hideBranchesWithoutRuns: Boolean = false,
) {
    /** The filter/sort options for [selectBranches]. */
    public fun viewOptions(): ViewOptions = ViewOptions(pinned, sortKey, hideIdleDays, maxRows, hideBranchesWithoutRuns)

    /**
     * Returns a copy with the column [id] enabled or disabled, or null if that would leave neither `STATUS`
     * nor `BRANCH` enabled.
     */
    public fun withColumnEnabled(id: ColumnId, enabled: Boolean): DisplaySettings? {
        val cols = columns.map { if (it.id == id) it.copy(enabled = enabled) else it }
        val ok = cols.any { it.enabled && (it.id == ColumnId.STATUS || it.id == ColumnId.BRANCH) }
        return if (ok) copy(columns = cols) else null
    }

    /** Copy with the priority of column [id] set (use [ColumnConfig.HIGH]/[ColumnConfig.MEDIUM]/[ColumnConfig.LOW]). */
    public fun withColumnPriority(id: ColumnId, priority: Int): DisplaySettings =
        copy(columns = columns.map { if (it.id == id) it.copy(priority = priority) else it })

    /** Copy with the width cap of column [id] set to [maxChars] (clamped to 1–80), or cleared with null. */
    public fun withColumnMaxChars(id: ColumnId, maxChars: Int?): DisplaySettings {
        val v = maxChars?.coerceIn(ColumnConfig.MIN_CHARS, ColumnConfig.MAX_CHARS)
        return copy(columns = columns.map { if (it.id == id) it.copy(maxChars = v) else it })
    }

    /** Copy with column [id] moved one place up ([delta] = -1) or down (+1). No-op at the ends. */
    public fun withColumnMoved(id: ColumnId, delta: Int): DisplaySettings {
        val i = columns.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in columns.indices) return this
        val list = columns.toMutableList()
        list[i] = columns[j]
        list[j] = columns[i]
        return copy(columns = list)
    }

    /** Copy with a font size clamped to 11–16 sp. */
    public fun withFontSize(sp: Int): DisplaySettings = copy(fontSizeSp = sp.coerceIn(MIN_FONT_SP, MAX_FONT_SP))

    /**
     * Repairs settings read from storage: all 12 columns present once (missing ones appended with defaults),
     * font size in range, and at least `STATUS` or `BRANCH` enabled.
     */
    public fun normalized(): DisplaySettings {
        val known = columns.distinctBy { it.id }
        val missing = ColumnConfig.defaults().filter { d -> known.none { it.id == d.id } }
        var cols = known + missing
        if (cols.none { it.enabled && (it.id == ColumnId.STATUS || it.id == ColumnId.BRANCH) }) {
            cols = cols.map { if (it.id == ColumnId.BRANCH) it.copy(enabled = true) else it }
        }
        cols = cols.map { c -> c.maxChars?.let { c.copy(maxChars = it.coerceIn(ColumnConfig.MIN_CHARS, ColumnConfig.MAX_CHARS)) } ?: c }
        return copy(columns = cols, fontSizeSp = fontSizeSp.coerceIn(MIN_FONT_SP, MAX_FONT_SP))
    }

    public companion object {
        public const val MIN_FONT_SP: Int = 11
        public const val MAX_FONT_SP: Int = 16
        public const val DEFAULT_FONT_SP: Int = 13
    }
}

/** Per-widget configuration (one widget = one repository, D6). */
@Serializable
public data class WidgetConfig(
    /** `owner/repo`, or null until chosen. */
    val repo: String? = null,
    /** Id of the saved token that has access to [repo] (D23); null = the first usable token. */
    val tokenId: String? = null,
    /** True (default): [display] is ignored and the global defaults apply. */
    val useGlobalDefaults: Boolean = true,
    val display: DisplaySettings = DisplaySettings(),
) {
    /** The settings in force: the global defaults or this widget's own. */
    public fun effective(global: DisplaySettings): DisplaySettings = (if (useGlobalDefaults) global else display).normalized()
}
