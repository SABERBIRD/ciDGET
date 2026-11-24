package io.github.saberbird.cidget.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ShareTest {
    private fun status(branch: String, s: Status, rep: Run?, reason: GreyReason? = null) =
        BranchStatus(branch, s, reason, "sha", false, rep, 1, null)

    @Test fun redRunFormat() {
        val r = run(id = 412, wf = 7, branch = "main", conclusion = "failure").copy(runNumber = 412, workflowName = "CI")
        assertEquals(
            "CI ✗ testING/main #412 CI: https://github.com/o/r/actions/runs/412",
            shareText("SABERBIRD/testING", status("main", Status.RED, r)),
        )
    }

    @Test fun otherStatusGlyphsAreUnicode() {
        val r = run(id = 5).copy(runNumber = 5, workflowName = "Lint")
        assertEquals("✓", Status.GREEN.unicodeGlyph())
        assertEquals("●", Status.YELLOW.unicodeGlyph())
        assertEquals("○", Status.GREY.unicodeGlyph())
        assertEquals("CI ✓ r/x #5 Lint: https://github.com/o/r/actions/runs/5", shareText("o/r", status("x", Status.GREEN, r)))
    }

    @Test fun blankWorkflowNameIsOmitted() {
        val r = run(id = 9).copy(runNumber = 9, workflowName = " ")
        assertEquals("CI ✓ r/x #9: https://github.com/o/r/actions/runs/9", shareText("o/r", status("x", Status.GREEN, r)))
    }

    @Test fun branchWithoutRunsUsesBranchPage() {
        val s = status("feature/a b", Status.GREY, null, GreyReason.NO_RUNS)
        assertEquals("CI ○ r/feature/a b: https://github.com/o/r/tree/feature/a%20b", shareText("o/r", s))
        assertEquals("https://github.com/o/r/tree/feature/a%20b", rowUrl("o/r", s))
    }

    @Test fun branchUrlEncodesUnicodeAndSpecials() {
        assertEquals("https://github.com/o/r/tree/%D1%84%D1%96%D1%87%D0%B0%23%3F", branchPageUrl("o/r", "фіча#?"))
    }
}
