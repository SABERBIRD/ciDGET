package io.github.saberbird.cidget.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class AggregationTest {
    private fun main(vararg runs: Run) = aggregate(input(runs.toList())).of("main")

    @Test fun a1_twoWorkflowsSuccess() {
        val older = run(wf = 1, at = 100)
        val newer = run(wf = 2, at = 200)
        val b = main(older, newer)
        assertEquals(Status.GREEN, b.status)
        assertEquals(newer.id, b.representative?.id)
        assertEquals(2, b.workflowCount)
    }

    @Test fun a2_failureBeatsSuccess() {
        val lint = run(wf = 2, conclusion = "failure")
        val b = main(run(wf = 1), lint)
        assertEquals(Status.RED, b.status)
        assertEquals(lint.id, b.representative?.id)
    }

    @Test fun a3_failureBeatsRunning() {
        val b = main(run(wf = 1, conclusion = "failure"), run(wf = 2, status = "in_progress", conclusion = null))
        assertEquals(Status.RED, b.status)
    }

    @Test fun a4_runningBeatsSuccess() {
        val lint = run(wf = 2, status = "queued", conclusion = null)
        val b = main(run(wf = 1), lint)
        assertEquals(Status.YELLOW, b.status)
        assertEquals(lint.id, b.representative?.id)
    }

    @Test fun a5_rerunSuccessReplacesOldFailure() {
        assertEquals(Status.GREEN, main(run(conclusion = "failure", at = 100), run(at = 200)).status)
    }

    @Test fun a6_newFailureReplacesOldSuccess() {
        assertEquals(Status.RED, main(run(at = 100), run(conclusion = "failure", at = 200)).status)
    }

    @Test fun a7_attemptTwoIsNotSpecial() {
        assertEquals(Status.GREEN, main(run(attempt = 2)).status)
    }

    @Test fun a8_differentEventsAreSeparateWorkflows() {
        val b = main(run(event = "push", conclusion = "failure", at = 100), run(event = "pull_request", at = 200))
        assertEquals(Status.RED, b.status)
        assertEquals(2, b.workflowCount)
    }

    @Test fun a9_allCancelled() {
        val b = main(run(conclusion = "cancelled"))
        assertEquals(Status.GREY, b.status)
        assertEquals(GreyReason.INCOMPLETE, b.greyReason)
    }

    @Test fun a10_successPlusCancelled() {
        val b = main(run(wf = 1), run(wf = 2, conclusion = "cancelled"))
        assertEquals(Status.GREY, b.status)
        assertEquals(GreyReason.INCOMPLETE, b.greyReason)
    }

    @Test fun a11_successPlusSkipped() {
        val ok = run(wf = 1)
        val b = main(ok, run(wf = 2, conclusion = "skipped", at = 300))
        assertEquals(Status.GREEN, b.status)
        assertEquals(ok.id, b.representative?.id)
    }

    @Test fun a12_allSkipped() {
        val b = main(run(conclusion = "skipped"))
        assertEquals(Status.GREY, b.status)
        assertEquals(GreyReason.NOTHING_RAN, b.greyReason)
        assertTrue(b.representative != null)
    }

    @Test fun a13_successPlusNeutral() {
        assertEquals(Status.GREEN, main(run(wf = 1), run(wf = 2, conclusion = "neutral")).status)
    }

    @Test fun a14_specialConclusions() {
        assertEquals(Status.GREY, main(run(conclusion = "stale")).status)
        assertEquals(Status.RED, main(run(conclusion = "action_required")).status)
        assertEquals(Status.RED, main(run(conclusion = "startup_failure")).status)
        assertEquals(Status.RED, main(run(conclusion = "timed_out")).status)
    }

    @Test fun a15_unknownValuesDoNotCrash() {
        val a = main(run(conclusion = "foo"))
        assertEquals(Status.GREY, a.status)
        assertEquals(GreyReason.INCOMPLETE, a.greyReason)
        assertEquals(Status.GREY, main(run(status = "bar", conclusion = null)).status)
        assertEquals(RunState.UNKNOWN, RunState.of("completed", null))
        assertEquals(RunState.UNKNOWN, RunState.of("bar", "failure"))
    }

    @Test fun a16_noRunsShownAndSortedLast() {
        val r = aggregate(input(listOf(run()), heads = listOf(BranchHead("main", "sha1"), BranchHead("idle", "x"))))
        val idle = r.of("idle")
        assertEquals(Status.GREY, idle.status)
        assertEquals(GreyReason.NO_RUNS, idle.greyReason)
        assertNull(idle.representative)
        val shown = selectBranches(r.branches.reversed(), ViewOptions(), t(10_000)).rows.map { it.branch }
        assertEquals(listOf("main", "idle"), shown)
        val hidden = selectBranches(r.branches, ViewOptions(hideBranchesWithoutRuns = true), t(10_000))
        assertEquals(listOf("main"), hidden.rows.map { it.branch })
    }

    @Test fun a17_oldGreenDoesNotLeak() {
        val b = main(run(sha = "old"))
        assertEquals(GreyReason.NO_RUNS, b.greyReason)
        assertEquals("sha1", b.headSha)
        assertFalse(b.headApproximate)
    }

    @Test fun a18_fallbackUsesNewestRun() {
        val r = aggregate(input(listOf(run(sha = "a", at = 100), run(sha = "b", at = 200)), heads = null))
        val b = r.of("main")
        assertEquals("b", b.headSha)
        assertTrue(b.headApproximate)
    }

    @Test fun a18_fallbackTieGoesToGreaterId() {
        val r = aggregate(input(listOf(run(sha = "a", at = 100, id = 1), run(sha = "b", at = 100, id = 2)), heads = null))
        assertEquals("b", r.of("main").headSha)
    }

    @Test fun a19_newRedOnTopOfGreen() {
        val b = main(run(sha = "old"), run(sha = "sha1", conclusion = "failure", at = 200))
        assertEquals(Status.RED, b.status)
    }

    @Test fun a20_newRunningOnTopOfRed() {
        val b = main(run(sha = "old", conclusion = "failure"), run(sha = "sha1", status = "in_progress", conclusion = null, at = 200))
        assertEquals(Status.YELLOW, b.status)
    }

    @Test fun a21_forkRunIgnored() {
        val r = aggregate(input(listOf(run(fork = true)), heads = null))
        assertTrue(r.branches.isEmpty())
        assertEquals(GreyReason.NO_RUNS, main(run(fork = true)).greyReason)
    }

    @Test fun a22_sameShaOnOtherBranchIsNotBorrowed() {
        val r = aggregate(input(listOf(run(branch = "a", sha = "s")), heads = listOf(BranchHead("a", "s"), BranchHead("b", "s"))))
        assertEquals(Status.GREEN, r.of("a").status)
        assertEquals(GreyReason.NO_RUNS, r.of("b").greyReason)
    }

    @Test fun a23_nullHeadBranchIgnored() {
        val r = aggregate(input(listOf(run(branch = null)), heads = null))
        assertTrue(r.branches.isEmpty())
    }

    @Test fun a24_coverageGapSignalled() {
        val r = aggregate(input(listOf(run(sha = "other")), coverageStart = t(50)))
        assertEquals(listOf("main"), r.unknownCoverage)
        val b = r.of("main")
        assertEquals(Status.GREY, b.status)
        assertEquals(GreyReason.NOT_LOADED, b.greyReason)
    }

    @Test fun a24_noGapWhenAllRunsFetched() {
        assertTrue(aggregate(input(listOf(run(sha = "other")))).unknownCoverage.isEmpty())
    }

    @Test fun a28_duration() {
        val done = run(at = 100, started = t(100), updated = t(160))
        assertEquals(60.seconds, runDuration(done, t(1000)))
        val running = run(at = 100, status = "in_progress", conclusion = null, started = t(100))
        assertEquals(900.seconds, runDuration(running, t(1000)))
        assertNull(runDuration(run(started = null), t(1000)))
        assertEquals(0.seconds, runDuration(run(started = t(500), updated = t(100)), t(1000)))
    }

    @Test fun a29_incompleteHeadsGiveApproximateBranch() {
        val runs = listOf(run(branch = "main", sha = "sha1"), run(branch = "other", sha = "z", at = 300))
        val r = aggregate(input(runs, complete = false))
        assertFalse(r.of("main").headApproximate)
        assertTrue(r.of("other").headApproximate)
        assertEquals("z", r.of("other").headSha)
        assertEquals(Status.GREEN, r.of("other").status)
    }

    @Test fun a30_completeHeadsDropDeletedBranch() {
        val runs = listOf(run(branch = "main"), run(branch = "gone"))
        assertEquals(listOf("main"), aggregate(input(runs)).branches.map { it.branch })
    }

    @Test fun lastActivityCoversAllCommits() {
        val b = main(run(sha = "old", at = 100, updated = t(900)), run(at = 200, updated = t(300)))
        assertEquals(t(900), b.lastActivity)
    }
}
