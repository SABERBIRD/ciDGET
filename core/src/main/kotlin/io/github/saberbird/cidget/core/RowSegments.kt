package io.github.saberbird.cidget.core

/** Colour role of a row segment; the widget maps roles to palette colours. */
public enum class SegmentRole { STATUS, FG, DIM, COPY, SHARE }

/** A run of text drawn with one colour; [COPY]/[SHARE] segments are the clickable cells. */
public data class Segment(val role: SegmentRole, val text: String)

private const val MAX_TEXT_SEGMENTS = 8

/**
 * Turns a rendered row into few segments (WIDGET.md §3.2): `STATUS` alone, `BRANCH` in `fg`, other text
 * columns in `dim`, `COPY`/`SHARE` alone; adjacent text of one role is merged, separators included. If more
 * than 8 text segments would result, all non-status text uses `fg` and merges.
 */
public fun rowSegments(fitted: List<FittedColumn>, rendered: List<String>): List<Segment> {
    fun build(roleOf: (ColumnId) -> SegmentRole): List<Segment> {
        val out = mutableListOf<Segment>()
        fun isAction(r: SegmentRole) = r == SegmentRole.COPY || r == SegmentRole.SHARE
        fun add(role: SegmentRole, text: String) {
            val last = out.lastOrNull()
            if (last != null && last.role == role && !isAction(role) && role != SegmentRole.STATUS) {
                out[out.size - 1] = last.copy(text = last.text + text)
            } else {
                out += Segment(role, text)
            }
        }
        fitted.forEachIndexed { i, col ->
            val role = roleOf(col.id)
            val prev = out.lastOrNull()
            when {
                i == 0 -> add(role, rendered[i])
                // The separator joins the previous text segment...
                prev != null && !isAction(prev.role) && prev.role != SegmentRole.STATUS -> {
                    add(prev.role, " ")
                    add(role, rendered[i])
                }
                // ...or leads the next text segment...
                !isAction(role) -> add(role, " " + rendered[i])
                // ...or stands alone between two action/status cells.
                else -> {
                    add(SegmentRole.DIM, " ")
                    add(role, rendered[i])
                }
            }
        }
        return out
    }

    val normal = build {
        when (it) {
            ColumnId.STATUS -> SegmentRole.STATUS
            ColumnId.BRANCH -> SegmentRole.FG
            ColumnId.COPY -> SegmentRole.COPY
            ColumnId.SHARE -> SegmentRole.SHARE
            else -> SegmentRole.DIM
        }
    }
    if (normal.count { it.role == SegmentRole.FG || it.role == SegmentRole.DIM } <= MAX_TEXT_SEGMENTS) return normal
    return build {
        when (it) {
            ColumnId.STATUS -> SegmentRole.STATUS
            ColumnId.COPY -> SegmentRole.COPY
            ColumnId.SHARE -> SegmentRole.SHARE
            else -> SegmentRole.FG
        }
    }
}
