package io.github.saberbird.cidget.core

import kotlinx.serialization.Serializable

/** Status glyph style (D10): Unicode `✓ ✗ ● ○` or ASCII brackets `[OK] [FAIL] [RUN] [--]`. */
@Serializable
public enum class GlyphStyle { UNICODE, BRACKET }

/**
 * What the 1×1 widget shows (D13, D28): the first pinned branch (the first row when none is pinned), the branch
 * with the newest run, or the worst status of the repo.
 */
@Serializable
public enum class TinySource { WORST, PINNED, LAST }

/** What a tap on the 1×1 widget does (D28). */
@Serializable
public enum class TinyTap { RUN, REFRESH, COPY }

/** UI language (D28). English is the default. */
@Serializable
public enum class Language(public val tag: String) { EN("en"), UK("uk") }

/** The screen a widget shows (D28): the branch list, the repo list or one of the settings screens. */
@Serializable
public enum class WidgetView { BRANCHES, REPOS, SETTINGS, SHOWN, ORDER, WIDTH, PRIORITY, DISPLAY, PINNED, TINY }

/** Everything about how a widget looks and what it lists. Each widget owns its copy (D28, WIDGET.md §5). */
@Serializable
public data class DisplaySettings(
    /** All 12 columns in display order. */
    val columns: List<ColumnConfig> = ColumnConfig.defaults(),
    /** 11–16 sp. */
    val fontSizeSp: Int = DEFAULT_FONT_SP,
    val glyphStyle: GlyphStyle = GlyphStyle.UNICODE,
    val tinySource: TinySource = TinySource.PINNED,
    val tinyTap: TinyTap = TinyTap.RUN,
    val language: Language = Language.EN,
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

    /**
     * Copy with column [id] moved one place among the enabled columns ([delta] -1 up, +1 down), jumping over
     * disabled ones. No-op at the ends or for a disabled column.
     */
    public fun withColumnMovedVisible(id: ColumnId, delta: Int): DisplaySettings {
        val i = columns.indexOfFirst { it.id == id }
        if (i < 0 || !columns[i].enabled || delta == 0) return this
        val step = if (delta < 0) -1 else 1
        var j = i + step
        while (j in columns.indices && !columns[j].enabled) j += step
        if (j !in columns.indices) return this
        val list = columns.toMutableList()
        list[i] = columns[j]
        list[j] = columns[i]
        return copy(columns = list)
    }

    /**
     * Copy with column [id] ranked one place higher ([delta] -1) or lower (+1) in the priority ranking of the
     * enabled columns (the rank decides which columns drop first when the line is too short). No-op at the
     * ends or for a disabled column.
     */
    public fun withPriorityMoved(id: ColumnId, delta: Int): DisplaySettings {
        val ranking = priorityRanking().map { it.id }.toMutableList()
        val i = ranking.indexOf(id)
        val j = i + (if (delta < 0) -1 else 1)
        if (i < 0 || delta == 0 || j !in ranking.indices) return this
        val values = priorityRanking().map { it.priority }
        ranking[i] = ranking[j].also { ranking[j] = ranking[i] }
        val byId = ranking.withIndex().associate { (rank, cid) -> cid to values[rank] }
        return copy(columns = columns.map { c -> byId[c.id]?.let { c.copy(priority = it) } ?: c })
    }

    /** Enabled columns from the most to the least important (the later one in the list loses ties). */
    public fun priorityRanking(): List<ColumnConfig> = columns.filter { it.enabled }.sortedByDescending { it.priority }

    /**
     * Copy with the width cap of [id] raised or lowered by [delta]. From automatic, a raise starts at the
     * column's default cap (10 when it has none); below 1 the cap becomes automatic again.
     */
    public fun withColumnMaxCharsStep(id: ColumnId, delta: Int): DisplaySettings {
        val current = columns.firstOrNull { it.id == id }?.maxChars
        val next = if (current == null) {
            if (delta > 0) Columns.spec(id).max ?: DEFAULT_CAP else null
        } else {
            (current + delta).takeIf { it >= ColumnConfig.MIN_CHARS }
        }
        return withColumnMaxChars(id, next)
    }

    /** Copy with [branch] pinned (appended to the pins) or unpinned. */
    public fun withPinToggled(branch: String): DisplaySettings =
        copy(pinned = if (branch in pinned) pinned - branch else pinned + branch)

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
        private const val DEFAULT_CAP: Int = 10
    }
}

/** Per-widget configuration (one widget = one repository, D6). */
@Serializable
public data class WidgetConfig(
    /** `owner/repo`, or null until chosen. */
    val repo: String? = null,
    /** Id of the saved token that has access to [repo] (D23); null = the first usable token. */
    val tokenId: String? = null,
    val display: DisplaySettings = DisplaySettings(),
    /** The screen the widget shows (D28). */
    val view: WidgetView = WidgetView.BRANCHES,
    /** The column selected on the order and priority screens. */
    val selected: ColumnId? = null,
)
