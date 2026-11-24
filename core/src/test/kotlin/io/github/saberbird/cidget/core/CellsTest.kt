package io.github.saberbird.cidget.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class CellsTest {
    private val glyphs = CellGlyphs({ when (it) { Status.RED -> "✗"; Status.YELLOW -> "●"; Status.GREEN -> "✓"; Status.GREY -> "○" } }, "⧉", "↗")

    @Test fun durationFormats() {
        assertEquals("0s", formatDuration(0.seconds))
        assertEquals("45s", formatDuration(45.seconds))
        assertEquals("1m00s", formatDuration(60.seconds))
        assertEquals("3m12s", formatDuration(3.minutes + 12.seconds))
        assertEquals("59m", formatDuration(59.minutes + 59.seconds))
        assertEquals("5h02", formatDuration(5.hours + 2.minutes))
        assertEquals("123h", formatDuration(123.hours))
        listOf(0, 59, 61, 599, 600, 3599, 3600, 90000, 400000).forEach {
            assert(formatDuration(it.seconds).length <= 6) { "too long for $it" }
        }
    }

    @Test fun eventNames() {
        assertEquals(listOf("push", "pr", "cron", "man", "mq", "rel", "chec"),
            listOf("push", "pull_request", "schedule", "workflow_dispatch", "merge_group", "release", "check_suite").map(::formatEvent))
    }

    @Test fun cellsOfRunRow() {
        val r = run(id = 412, wf = 1, branch = "main", at = 0, started = t(-60), updated = t(192)).copy(workflowName = "CI", actor = "rex", event = "pull_request", displayTitle = "Fix snaps\nbody")
        val b = BranchStatus("main", Status.RED, null, "abcdef0123456", true, r, 1, t(192))
        val c = cellsFor(b, t(192 + 300), glyphs)
        assertEquals("✗~", c[ColumnId.STATUS])
        assertEquals("5m", c[ColumnId.AGE])
        assertEquals("#412", c[ColumnId.RUN_NUMBER])
        assertEquals("CI", c[ColumnId.WORKFLOW])
        assertEquals("abcdef0", c[ColumnId.SHA])
        assertEquals("4m12s", c[ColumnId.DURATION])
        assertEquals("Fix snaps", c[ColumnId.TITLE])
        assertEquals("rex", c[ColumnId.ACTOR])
        assertEquals("pr", c[ColumnId.EVENT])
        assertEquals("[⧉]", c[ColumnId.COPY])
        assertEquals("[↗]", c[ColumnId.SHARE])
    }

    @Test fun cellsOfNoRunsRowAreBlank() {
        val b = BranchStatus("dev", Status.GREY, GreyReason.NO_RUNS, "sha", false, null, 0, null)
        val c = cellsFor(b, t(0), glyphs)
        assertEquals("○", c[ColumnId.STATUS])
        listOf(ColumnId.AGE, ColumnId.RUN_NUMBER, ColumnId.WORKFLOW, ColumnId.DURATION, ColumnId.TITLE, ColumnId.ACTOR, ColumnId.EVENT)
            .forEach { assertEquals("", c[it], it.name) }
        assertEquals("dev", c[ColumnId.BRANCH])
    }

    @Test fun segmentsMergeAdjacentTextAndKeepActionsAlone() {
        val ids = listOf(ColumnId.STATUS, ColumnId.BRANCH, ColumnId.AGE, ColumnId.RUN_NUMBER, ColumnId.COPY, ColumnId.SHARE, ColumnId.WORKFLOW)
        val fitted = ids.map { FittedColumn(it, 3, Align.LEFT) }
        val seg = rowSegments(fitted, ids.map { it.name.take(3) })
        assertEquals(
            listOf(SegmentRole.STATUS, SegmentRole.FG, SegmentRole.DIM, SegmentRole.COPY, SegmentRole.DIM, SegmentRole.SHARE, SegmentRole.DIM),
            seg.map { it.role },
        )
        // joined text equals the plain row
        assertEquals(ids.joinToString(" ") { it.name.take(3) }, seg.joinToString("") { it.text })
        // the status segment stays alone, the separator leads the branch segment
        assertEquals("STA", seg[0].text)
        assertEquals(" BRA ", seg[1].text)
    }

    @Test fun manyTextSegmentsCollapseToFg() {
        val ids = ColumnId.values().toList()
        val fitted = ids.map { FittedColumn(it, 2, Align.LEFT) }
        val seg = rowSegments(fitted, ids.map { "x" })
        assert(seg.size <= 10) { "Glance rows allow at most 10 children, got ${seg.size}" }
    }
}
