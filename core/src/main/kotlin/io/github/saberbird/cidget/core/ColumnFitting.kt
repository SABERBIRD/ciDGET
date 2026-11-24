package io.github.saberbird.cidget.core

import kotlin.math.floor

/** A column that survived fitting, with its final [width] in cells. */
public data class FittedColumn(val id: ColumnId, val width: Int, val align: Align)

/**
 * `charsPerLine = floor((widgetWidthDp - horizontalPaddingDp) / (fontSizeSp * fontScale * advanceRatio))`
 * (WIDGET.md §3.1). Never negative.
 */
public fun charsPerLine(
    widgetWidthDp: Float,
    horizontalPaddingDp: Float,
    fontSizeSp: Float,
    fontScale: Float,
    advanceRatio: Float,
): Int = floor((widgetWidthDp - horizontalPaddingDp) / (fontSizeSp * fontScale * advanceRatio)).toInt().coerceAtLeast(0)

private const val SEP = 1
private const val MIN_BRANCH = 3

/**
 * Chooses columns and widths for [charsPerLine] (WIDGET.md §3.1). [rows] hold the rendered text of each
 * cell. Pure and deterministic.
 */
public fun fitColumns(columns: List<ColumnConfig>, rows: List<Map<ColumnId, String>>, charsPerLine: Int): List<FittedColumn> {
    class Col(val cfg: ColumnConfig, val spec: ColumnSpec, val order: Int, val content: Int) {
        val need: Int get() = if (spec.flexible) minOf(spec.min, content) else content
    }

    // 1. content width per enabled column; empty columns are dropped.
    val cols = columns.withIndex().filter { it.value.enabled }.map { (i, cfg) ->
        val spec = Columns.spec(cfg.id)
        val longest = rows.maxOfOrNull { displayWidth(it[cfg.id].orEmpty()) } ?: 0
        Col(cfg, spec, i, (cfg.maxChars ?: spec.max)?.let { minOf(longest, it) } ?: longest)
    }.filter { it.content > 0 }.toMutableList()
    if (cols.isEmpty()) return emptyList()

    fun used(): Int = cols.sumOf { it.need } + SEP * (cols.size - 1)

    // 3. drop the least important column until it fits.
    while (used() > charsPerLine) {
        val status = cols.firstOrNull { it.cfg.id == ColumnId.STATUS }
        val canDropBranch = (status?.need ?: 0).let { s -> (if (status != null) s + SEP else 0) + MIN_BRANCH } > charsPerLine
        val candidates = cols.filter {
            when (it.cfg.id) {
                ColumnId.STATUS -> cols.size == 1
                ColumnId.BRANCH -> canDropBranch
                else -> true
            }
        }
        val victim = candidates.minWithOrNull(compareBy<Col> { it.cfg.priority }.thenByDescending { it.order }) ?: break
        cols.remove(victim)
        if (cols.isEmpty()) return emptyList()
    }

    // 2./4. start at the needs, then hand out spare width to flexible columns by priority.
    val widths = cols.associate { it.cfg.id to it.need }.toMutableMap()
    var spare = charsPerLine - used()
    if (spare < 0) {
        // Only protected columns are left and BRANCH still does not fit: shrink it (at least MIN_BRANCH).
        val branch = cols.firstOrNull { it.cfg.id == ColumnId.BRANCH }
        if (branch != null) widths[branch.cfg.id] = (widths.getValue(branch.cfg.id) + spare).coerceAtLeast(MIN_BRANCH)
        spare = 0
    }
    for (c in cols.filter { it.spec.flexible }.sortedWith(compareByDescending<Col> { it.cfg.priority }.thenBy { it.order })) {
        if (spare <= 0) break
        val give = minOf(spare, c.content - widths.getValue(c.cfg.id)).coerceAtLeast(0)
        widths[c.cfg.id] = widths.getValue(c.cfg.id) + give
        spare -= give
    }
    return cols.map { FittedColumn(it.cfg.id, widths.getValue(it.cfg.id), it.spec.align) }
}

/** Renders one row's cells to their fitted widths (padded, aligned, truncated with `…`). */
public fun renderRow(fitted: List<FittedColumn>, cells: Map<ColumnId, String>): List<String> =
    fitted.map { fitCell(cells[it.id].orEmpty(), it.width, it.align) }
