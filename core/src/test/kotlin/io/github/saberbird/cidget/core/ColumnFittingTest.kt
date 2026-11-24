package io.github.saberbird.cidget.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColumnFittingTest {
    private fun cfg(vararg on: ColumnId, prio: Map<ColumnId, Int> = emptyMap()): List<ColumnConfig> =
        ColumnConfig.defaults().map { it.copy(enabled = it.id in on, priority = prio[it.id] ?: it.priority) }

    private fun row(vararg kv: Pair<ColumnId, String>) = mapOf(*kv)
    private fun ids(f: List<FittedColumn>) = f.map { it.id }

    private val full = row(
        ColumnId.STATUS to "✗", ColumnId.BRANCH to "feature/snaps", ColumnId.AGE to "5m",
        ColumnId.RUN_NUMBER to "#412", ColumnId.COPY to "[⧉]", ColumnId.SHARE to "[↗]", ColumnId.WORKFLOW to "CI",
    )
    private val six = arrayOf(ColumnId.STATUS, ColumnId.BRANCH, ColumnId.AGE, ColumnId.RUN_NUMBER, ColumnId.COPY, ColumnId.SHARE)

    @Test fun l1_everythingFits() {
        val f = fitColumns(cfg(*six, ColumnId.WORKFLOW), listOf(full), 80)
        assertEquals(listOf(ColumnId.STATUS, ColumnId.BRANCH, ColumnId.AGE, ColumnId.RUN_NUMBER, ColumnId.COPY, ColumnId.SHARE, ColumnId.WORKFLOW), ids(f))
        assertEquals(13, f.single { it.id == ColumnId.BRANCH }.width)
        assertEquals(2, f.single { it.id == ColumnId.AGE }.width)
    }

    @Test fun l2_lowestPriorityDroppedFirst() {
        // minimum needs: 1+6+2+4+3+3+2 = 21 + 6 separators = 27
        val f = fitColumns(cfg(*six, ColumnId.WORKFLOW), listOf(full), 26)
        assertTrue(ColumnId.WORKFLOW !in ids(f))
        assertEquals(6, f.size)
        val f2 = fitColumns(cfg(*six, ColumnId.WORKFLOW), listOf(full), 22)
        assertTrue(ColumnId.SHARE !in ids(f2) && ColumnId.WORKFLOW !in ids(f2))
        assertTrue(ColumnId.COPY in ids(f2))
    }

    @Test fun l3_tieBrokenByOrder() {
        val c = cfg(ColumnId.STATUS, ColumnId.BRANCH, ColumnId.AGE, ColumnId.RUN_NUMBER, prio = mapOf(ColumnId.AGE to 50, ColumnId.RUN_NUMBER to 50))
        // needs: 1+6+2+4 + 3 sep = 16; at 15 one of the tied columns must go: the later one (RUN_NUMBER).
        val f = fitColumns(c, listOf(full), 15)
        assertTrue(ColumnId.AGE in ids(f))
        assertTrue(ColumnId.RUN_NUMBER !in ids(f))
    }

    @Test fun l4_statusAndBranchSurvive() {
        val f = fitColumns(cfg(*six), listOf(full), 8)
        assertEquals(listOf(ColumnId.STATUS, ColumnId.BRANCH), ids(f))
        assertEquals(6, f.single { it.id == ColumnId.BRANCH }.width)
        val tight = fitColumns(cfg(*six), listOf(full), 6)
        assertEquals(listOf(ColumnId.STATUS, ColumnId.BRANCH), ids(tight))
        assertEquals(4, tight.single { it.id == ColumnId.BRANCH }.width)
    }

    @Test fun l5_veryNarrowGivesStatusOnly() {
        assertEquals(listOf(ColumnId.STATUS), ids(fitColumns(cfg(*six), listOf(full), 4)))
        assertEquals(listOf(ColumnId.STATUS), ids(fitColumns(cfg(*six), listOf(full), 1)))
    }

    @Test fun l6_spareGoesToHigherPriorityFirst() {
        val rows = listOf(row(ColumnId.STATUS to "✓", ColumnId.BRANCH to "a-long-branch-name", ColumnId.WORKFLOW to "Build and test"))
        val c = cfg(ColumnId.STATUS, ColumnId.BRANCH, ColumnId.WORKFLOW)
        // needs: 1 + 6 + 3 + 2 sep = 12; contents: 1, 18, 14.
        val f = fitColumns(c, rows, 24)
        // spare 12 → BRANCH (prio 90) takes 12 (to 18), WORKFLOW stays 3.
        assertEquals(18, f.single { it.id == ColumnId.BRANCH }.width)
        assertEquals(3, f.single { it.id == ColumnId.WORKFLOW }.width)
        val f2 = fitColumns(c.map { if (it.id == ColumnId.WORKFLOW) it.copy(priority = 95) else it }, rows, 24)
        assertEquals(14, f2.single { it.id == ColumnId.WORKFLOW }.width)
        assertEquals(6 + (24 - 12 - 11), f2.single { it.id == ColumnId.BRANCH }.width)
    }

    @Test fun l7_truncationWithEllipsis() {
        assertEquals("feature/s…", fitCell("feature/snaps-long", 10, Align.LEFT))
        assertEquals("ab ", fitCell("ab", 3, Align.LEFT))
        assertEquals(" ab", fitCell("ab", 3, Align.RIGHT))
        assertEquals("…", truncate("abc", 1))
        assertEquals("", truncate("abc", 0))
        val f = fitColumns(cfg(ColumnId.STATUS, ColumnId.BRANCH), listOf(row(ColumnId.STATUS to "✓", ColumnId.BRANCH to "a-very-long-branch")), 8)
        assertEquals(listOf("✓", "a-ver…"), renderRow(f, row(ColumnId.STATUS to "✓", ColumnId.BRANCH to "a-very-long-branch")).map { it.trimEnd() })
    }

    @Test fun l8_emptyColumnDropped() {
        val rows = listOf(row(ColumnId.STATUS to "✓", ColumnId.BRANCH to "main", ColumnId.WORKFLOW to ""))
        val f = fitColumns(cfg(ColumnId.STATUS, ColumnId.BRANCH, ColumnId.WORKFLOW), rows, 40)
        assertEquals(listOf(ColumnId.STATUS, ColumnId.BRANCH), ids(f))
        assertTrue(fitColumns(cfg(ColumnId.WORKFLOW), rows, 40).isEmpty())
    }

    @Test fun l9_wideCharactersCountTwo() {
        assertEquals(4, displayWidth("日本"))
        assertEquals(2, displayWidth("😀"))
        assertEquals(3, displayWidth("a😀"))
        assertEquals(5, displayWidth("a😀日")) // code points, not UTF-16 units
        assertEquals("日…", truncate("日本語", 4))
        val rows = listOf(row(ColumnId.STATUS to "✓", ColumnId.BRANCH to "日本語ブランチ"))
        val f = fitColumns(cfg(ColumnId.STATUS, ColumnId.BRANCH), rows, 40)
        assertEquals(14, f.single { it.id == ColumnId.BRANCH }.width)
    }

    @Test fun l10_charsPerLineWithFontScale() {
        // (320 - 16) / (13 * 1.0 * 0.6) = 38.97 → 38
        assertEquals(38, charsPerLine(320f, 16f, 13f, 1.0f, 0.6f))
        // (320 - 16) / (13 * 1.3 * 0.6) = 29.98 → 29
        assertEquals(29, charsPerLine(320f, 16f, 13f, 1.3f, 0.6f))
        assertEquals(0, charsPerLine(10f, 16f, 13f, 1f, 0.6f))
    }

    @Test fun branchColumnCapAndFixedCaps() {
        val rows = listOf(row(ColumnId.STATUS to "✓", ColumnId.BRANCH to "x".repeat(60), ColumnId.AGE to "12345", ColumnId.SHA to "abcdef0123"))
        val f = fitColumns(cfg(ColumnId.STATUS, ColumnId.BRANCH, ColumnId.AGE, ColumnId.SHA), rows, 200)
        assertEquals(40, f.single { it.id == ColumnId.BRANCH }.width)
        assertEquals(3, f.single { it.id == ColumnId.AGE }.width)
        assertEquals(7, f.single { it.id == ColumnId.SHA }.width)
    }

    @Test fun l11_userMaxCharsCapsALongBranchSoOtherColumnsStay() {
        val long = row(
            ColumnId.STATUS to "✗", ColumnId.BRANCH to "feature/a-really-long-branch-name-here", ColumnId.AGE to "5m",
            ColumnId.RUN_NUMBER to "#412", ColumnId.COPY to "[⧉]", ColumnId.SHARE to "[↗]",
        )
        val capped = cfg(*six).map { if (it.id == ColumnId.BRANCH) it.copy(maxChars = 12) else it }
        val f = fitColumns(capped, listOf(long), 40)
        assertEquals(12, f.single { it.id == ColumnId.BRANCH }.width)
        assertEquals(six.toList(), ids(f))
    }

    @Test fun l12_maxCharsOverridesTheDefaultCapAndIsClamped() {
        val s = DisplaySettings().withColumnMaxChars(ColumnId.BRANCH, 500)
        assertEquals(ColumnConfig.MAX_CHARS, s.columns.single { it.id == ColumnId.BRANCH }.maxChars)
        assertEquals(null, s.withColumnMaxChars(ColumnId.BRANCH, null).columns.single { it.id == ColumnId.BRANCH }.maxChars)
        val wide = row(ColumnId.STATUS to "✗", ColumnId.BRANCH to "x".repeat(50))
        val f = fitColumns(cfg(ColumnId.STATUS, ColumnId.BRANCH).map { if (it.id == ColumnId.BRANCH) it.copy(maxChars = 50) else it }, listOf(wide), 80)
        assertEquals(50, f.single { it.id == ColumnId.BRANCH }.width)
    }
}
