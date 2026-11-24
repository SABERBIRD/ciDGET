package io.github.saberbird.cidget.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days

class BranchViewTest {
    private val now = t(100L * 24 * 3600)
    private fun names(v: BranchView) = v.rows.map { it.branch }
    private val opts = ViewOptions(hideIdleDays = null, maxRows = null)

    private val sample = listOf(
        branch("green-old", Status.GREEN, activity = t(1000)),
        branch("red-mid", Status.RED, activity = t(2000)),
        branch("yellow-new", Status.YELLOW, activity = t(3000)),
        branch("Grey", Status.GREY, GreyReason.INCOMPLETE, activity = t(2500)),
    )

    @Test fun a25_recent() {
        assertEquals(listOf("yellow-new", "Grey", "red-mid", "green-old"), names(selectBranches(sample, opts, now)))
    }

    @Test fun a25_status() {
        val v = selectBranches(sample, opts.copy(sortKey = SortKey.STATUS), now)
        assertEquals(listOf("red-mid", "yellow-new", "Grey", "green-old"), names(v))
    }

    @Test fun a25_name_caseInsensitiveNatural() {
        val list = listOf("b10", "B2", "a", "b1").map { branch(it) }
        val v = selectBranches(list, opts.copy(sortKey = SortKey.NAME), now)
        assertEquals(listOf("a", "b1", "B2", "b10"), names(v))
    }

    @Test fun a25_pinnedFirstInPinOrderUnderEveryKey() {
        for (k in SortKey.values()) {
            val v = selectBranches(sample, opts.copy(sortKey = k, pinned = listOf("green-old", "missing", "Grey")), now)
            assertEquals(listOf("green-old", "Grey"), names(v).take(2))
        }
    }

    @Test fun a25_tiesAreStable() {
        val list = listOf("x", "y", "z").map { branch(it, activity = t(5)) }
        assertEquals(listOf("x", "y", "z"), names(selectBranches(list, opts, now)))
    }

    @Test fun a26_idleFilterKeepsPinned() {
        val old = now - 40.days
        val list = listOf(branch("old", activity = old), branch("oldPinned", activity = old), branch("fresh", activity = now))
        val v = selectBranches(list, ViewOptions(pinned = listOf("oldPinned"), hideIdleDays = 30, maxRows = null), now)
        assertEquals(listOf("oldPinned", "fresh"), names(v))
    }

    @Test fun idleFilterDoesNotAffectNoRuns() {
        val old = now - 40.days
        val list = listOf(branch("none", Status.GREY, GreyReason.NO_RUNS, activity = old))
        assertEquals(1, selectBranches(list, ViewOptions(hideIdleDays = 30), now).rows.size)
    }

    @Test fun a27_maxRowsDoesNotChangeWorst() {
        val list = listOf(branch("a", Status.GREEN, activity = t(300)), branch("b", Status.GREEN, activity = t(200)), branch("c", Status.RED, activity = t(100)))
        val v = selectBranches(list, ViewOptions(hideIdleDays = null, maxRows = 1), now)
        assertEquals(listOf("a"), names(v))
        assertEquals(Status.RED, v.worst)
    }

    @Test fun worstOrderAndEmpty() {
        assertEquals(Status.GREY, selectBranches(emptyList(), opts, now).worst)
        val v = selectBranches(listOf(branch("g"), branch("y", Status.YELLOW), branch("n", Status.GREY, GreyReason.NO_RUNS)), opts, now)
        assertEquals(Status.YELLOW, v.worst)
        assertEquals(Status.GREY, selectBranches(listOf(branch("g"), branch("n", Status.GREY, GreyReason.NO_RUNS)), opts, now).worst)
    }

    @Test fun worstIgnoresFilteredBranches() {
        val list = listOf(branch("r", Status.RED, activity = now - 40.days), branch("g", activity = now))
        assertEquals(Status.GREEN, selectBranches(list, ViewOptions(hideIdleDays = 30), now).worst)
    }
}
