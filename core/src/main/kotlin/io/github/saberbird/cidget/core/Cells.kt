package io.github.saberbird.cidget.core

import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** `DURATION` column: `45s`, `3m12s`, `59m`, `5h02`; at most 6 characters. */
public fun formatDuration(d: Duration): String {
    val total = d.inWholeSeconds.coerceAtLeast(0)
    return when {
        d < 1.minutes -> "${total}s"
        d < 10.minutes -> "${total / 60}m${(total % 60).toString().padStart(2, '0')}s"
        d < 1.hours -> "${total / 60}m"
        d >= 100.hours -> "${total / 3600}h"
        else -> "${total / 3600}h${((total % 3600) / 60).toString().padStart(2, '0')}"
    }
}

/** `EVENT` column: `push`, `pr`, `cron`, `man`, `mq`, `rel`, anything else the first 4 characters. */
public fun formatEvent(event: String): String = when (event) {
    "push" -> "push"
    "pull_request" -> "pr"
    "schedule" -> "cron"
    "workflow_dispatch" -> "man"
    "merge_group" -> "mq"
    "release" -> "rel"
    else -> event.take(4)
}

/** Glyph strings the cells use (already resolved against the font by the caller). */
public data class CellGlyphs(val status: (Status) -> String, val copy: String, val share: String)

/**
 * Text of every column for one branch row. Columns that come from a run are blank for a branch without
 * runs. `STATUS` gets a `~` suffix when the head is approximate. `COPY`/`SHARE` are bracketed glyphs.
 */
public fun cellsFor(b: BranchStatus, now: Instant, glyphs: CellGlyphs): Map<ColumnId, String> {
    val r = b.representative
    return mapOf(
        ColumnId.STATUS to glyphs.status(b.status) + if (b.headApproximate) "~" else "",
        ColumnId.BRANCH to b.branch,
        ColumnId.AGE to (r?.let { formatAge(it.updatedAt, now) } ?: ""),
        ColumnId.RUN_NUMBER to (r?.let { "#${it.runNumber}" } ?: ""),
        ColumnId.COPY to "[${glyphs.copy}]",
        ColumnId.SHARE to "[${glyphs.share}]",
        ColumnId.WORKFLOW to (r?.workflowName ?: ""),
        ColumnId.SHA to (b.headSha?.take(7) ?: ""),
        ColumnId.DURATION to (r?.let { runDuration(it, now)?.let(::formatDuration) } ?: ""),
        ColumnId.TITLE to (r?.displayTitle?.lineSequence()?.firstOrNull() ?: ""),
        ColumnId.ACTOR to (r?.actor ?: ""),
        ColumnId.EVENT to (r?.let { formatEvent(it.event) } ?: ""),
    )
}
