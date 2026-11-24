package io.github.saberbird.cidget.core

import kotlinx.serialization.Serializable

/** The 12 widget columns (WIDGET.md §3). */
@Serializable
public enum class ColumnId { STATUS, BRANCH, AGE, RUN_NUMBER, COPY, SHARE, WORKFLOW, SHA, DURATION, TITLE, ACTOR, EVENT }

/** Text alignment inside a column. */
public enum class Align { LEFT, RIGHT }

/**
 * Static facts about a column. [flexible] columns shrink to [min] and grow up to [max]; fixed ones take
 * their content width (capped at [max] when set). [defaultPriority] and [defaultOn] are the initial settings.
 */
public data class ColumnSpec(
    val id: ColumnId,
    val flexible: Boolean,
    val min: Int,
    val max: Int?,
    val defaultOn: Boolean,
    val defaultPriority: Int,
    val align: Align,
)

/** The column table of WIDGET.md §3, in default order. */
public object Columns {
    /** All specs in default display order. */
    public val specs: List<ColumnSpec> = listOf(
        ColumnSpec(ColumnId.STATUS, false, 0, null, true, 100, Align.LEFT),
        ColumnSpec(ColumnId.BRANCH, true, 6, 40, true, 90, Align.LEFT),
        ColumnSpec(ColumnId.AGE, false, 0, 3, true, 70, Align.RIGHT),
        ColumnSpec(ColumnId.RUN_NUMBER, false, 0, null, true, 60, Align.RIGHT),
        ColumnSpec(ColumnId.COPY, false, 0, null, true, 50, Align.LEFT),
        ColumnSpec(ColumnId.SHARE, false, 0, null, true, 45, Align.LEFT),
        ColumnSpec(ColumnId.WORKFLOW, true, 3, 14, true, 40, Align.LEFT),
        ColumnSpec(ColumnId.SHA, false, 0, 7, false, 30, Align.LEFT),
        ColumnSpec(ColumnId.DURATION, false, 0, 6, false, 25, Align.RIGHT),
        ColumnSpec(ColumnId.TITLE, true, 8, 60, false, 20, Align.LEFT),
        ColumnSpec(ColumnId.ACTOR, true, 3, 12, false, 15, Align.LEFT),
        ColumnSpec(ColumnId.EVENT, false, 0, 4, false, 10, Align.LEFT),
    )

    private val byId = specs.associateBy { it.id }

    /** Spec of [id]. */
    public fun spec(id: ColumnId): ColumnSpec = byId.getValue(id)
}

/**
 * One user-configured column: whether it is shown, how important it is and an optional width cap. The list
 * order is the display order. [maxChars] (null = the column's default cap) limits the column to that many
 * characters, so one long branch name cannot push the other columns out.
 */
@Serializable
public data class ColumnConfig(val id: ColumnId, val enabled: Boolean, val priority: Int, val maxChars: Int? = null) {
    public companion object {
        /** Allowed range of a user-set [maxChars]. */
        public const val MIN_CHARS: Int = 1
        public const val MAX_CHARS: Int = 80

        /** Priority values stored for the UI's high / medium / low. */
        public const val HIGH: Int = 80
        public const val MEDIUM: Int = 50
        public const val LOW: Int = 20

        /** The default list: every column in default order with its default on/priority. */
        public fun defaults(): List<ColumnConfig> =
            Columns.specs.map { ColumnConfig(it.id, it.defaultOn, it.defaultPriority) }
    }
}
