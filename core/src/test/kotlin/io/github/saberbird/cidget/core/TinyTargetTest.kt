package io.github.saberbird.cidget.core

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TinyTargetTest {
    private fun b(name: String, status: Status, at: String?) = BranchStatus(
        name, status, if (status == Status.GREY) GreyReason.NO_RUNS else null, null, false, null, 1, at?.let(Instant::parse),
    )

    private val rows = listOf(
        b("main", Status.GREEN, "2026-01-03T00:00:00Z"),
        b("feature", Status.RED, "2026-01-02T00:00:00Z"),
        b("dev", Status.YELLOW, "2026-01-04T00:00:00Z"),
        b("idle", Status.GREY, null),
    )
    private val view = BranchView(rows, Status.RED)

    @Test fun pinnedUsesTheFirstPinnedBranchThatExists() {
        assertEquals("dev", tinyTarget(view, TinySource.PINNED, listOf("gone", "dev", "main"))?.branch)
    }

    @Test fun pinnedFallsBackToTheFirstRowSoItMatchesTheList() {
        assertEquals("main", tinyTarget(view, TinySource.PINNED, emptyList())?.branch)
        assertEquals("main", tinyTarget(view, TinySource.PINNED, listOf("gone"))?.branch)
    }

    @Test fun lastIsTheNewestActivity() {
        assertEquals("dev", tinyTarget(view, TinySource.LAST, emptyList())?.branch)
    }

    @Test fun worstIsTheFirstRowWithTheWorstStatus() {
        assertEquals("feature", tinyTarget(view, TinySource.WORST, emptyList())?.branch)
    }

    @Test fun noRowsGiveNull() {
        assertNull(tinyTarget(BranchView(emptyList(), Status.GREY), TinySource.PINNED, listOf("main")))
        assertEquals("idle", tinyTarget(BranchView(rows.takeLast(1), Status.GREY), TinySource.LAST, emptyList())?.branch)
    }
}
