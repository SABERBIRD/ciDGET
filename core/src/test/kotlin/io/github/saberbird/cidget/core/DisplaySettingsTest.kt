package io.github.saberbird.cidget.core

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DisplaySettingsTest {
    @Test fun defaultsMatchSpec() {
        val d = DisplaySettings()
        assertEquals(12, d.columns.size)
        assertEquals(listOf(ColumnId.STATUS, ColumnId.BRANCH, ColumnId.AGE, ColumnId.RUN_NUMBER, ColumnId.COPY, ColumnId.SHARE, ColumnId.WORKFLOW),
            d.columns.filter { it.enabled }.map { it.id })
        assertEquals(13, d.fontSizeSp)
        assertEquals(ViewOptions(), d.viewOptions())
    }

    @Test fun statusOrBranchMustStayEnabled() {
        var d = DisplaySettings()
        d = assertNotNull(d.withColumnEnabled(ColumnId.STATUS, false))
        assertNull(d.withColumnEnabled(ColumnId.BRANCH, false))
        d = assertNotNull(d.withColumnEnabled(ColumnId.STATUS, true).also { assertNotNull(it) }?.withColumnEnabled(ColumnId.BRANCH, false))
        assertNull(d.withColumnEnabled(ColumnId.STATUS, false))
        assertNotNull(d.withColumnEnabled(ColumnId.AGE, false))
    }

    @Test fun moveAndPriority() {
        val d = DisplaySettings().withColumnMoved(ColumnId.AGE, -1)
        assertEquals(listOf(ColumnId.STATUS, ColumnId.AGE, ColumnId.BRANCH), d.columns.take(3).map { it.id })
        assertEquals(d, d.withColumnMoved(ColumnId.STATUS, -1))
        assertEquals(d.columns.last().id, d.withColumnMoved(d.columns.last().id, 1).columns.last().id)
        assertEquals(ColumnConfig.LOW, d.withColumnPriority(ColumnId.AGE, ColumnConfig.LOW).columns.single { it.id == ColumnId.AGE }.priority)
    }

    @Test fun fontSizeClamped() {
        assertEquals(11, DisplaySettings().withFontSize(3).fontSizeSp)
        assertEquals(16, DisplaySettings().withFontSize(40).fontSizeSp)
    }

    @Test fun normalizedRepairsStoredData() {
        val broken = DisplaySettings(columns = listOf(ColumnConfig(ColumnId.AGE, true, 50), ColumnConfig(ColumnId.AGE, false, 10), ColumnConfig(ColumnId.STATUS, false, 100)), fontSizeSp = 99)
        val n = broken.normalized()
        assertEquals(12, n.columns.size)
        assertEquals(12, n.columns.map { it.id }.toSet().size)
        assertEquals(ColumnId.AGE, n.columns.first().id)
        assertTrue(n.columns.single { it.id == ColumnId.BRANCH }.enabled)
        assertEquals(16, n.fontSizeSp)
    }

    @Test fun movedVisibleJumpsOverDisabledColumns() {
        // default order: STATUS BRANCH AGE RUN_NUMBER COPY SHARE WORKFLOW SHA(off) ...
        val d = DisplaySettings().withColumnMovedVisible(ColumnId.WORKFLOW, 1)
        assertEquals(DisplaySettings().columns.map { it.id }.let { l -> l.take(6) }, d.columns.take(6).map { it.id })
        val up = DisplaySettings().withColumnEnabled(ColumnId.AGE, false)!!.withColumnMovedVisible(ColumnId.RUN_NUMBER, -1)
        assertEquals(listOf(ColumnId.STATUS, ColumnId.RUN_NUMBER, ColumnId.BRANCH), up.columns.filter { it.enabled }.take(3).map { it.id })
        val disabled = DisplaySettings().withColumnMovedVisible(ColumnId.SHA, -1)
        assertEquals(DisplaySettings(), disabled)
        assertEquals(DisplaySettings(), DisplaySettings().withColumnMovedVisible(ColumnId.STATUS, -1))
    }

    @Test fun priorityMovedSwapsRanksOfEnabledColumns() {
        val d = DisplaySettings().withPriorityMoved(ColumnId.AGE, -1)
        // AGE (70) was below BRANCH (90): now ranked directly under STATUS, above BRANCH.
        assertEquals(listOf(ColumnId.STATUS, ColumnId.AGE, ColumnId.BRANCH), d.priorityRanking().take(3).map { it.id })
        assertEquals(DisplaySettings(), DisplaySettings().withPriorityMoved(ColumnId.STATUS, -1))
        val last = DisplaySettings().priorityRanking().last().id
        assertEquals(DisplaySettings(), DisplaySettings().withPriorityMoved(last, 1))
        assertEquals(DisplaySettings(), DisplaySettings().withPriorityMoved(ColumnId.SHA, -1))
    }

    @Test fun maxCharsStepStartsAtTheDefaultCapAndReturnsToAuto() {
        var d = DisplaySettings().withColumnMaxCharsStep(ColumnId.BRANCH, 1)
        assertEquals(40, d.columns.single { it.id == ColumnId.BRANCH }.maxChars)
        d = d.withColumnMaxCharsStep(ColumnId.BRANCH, -1)
        assertEquals(39, d.columns.single { it.id == ColumnId.BRANCH }.maxChars)
        assertEquals(10, DisplaySettings().withColumnMaxCharsStep(ColumnId.COPY, 1).columns.single { it.id == ColumnId.COPY }.maxChars)
        val one = DisplaySettings().withColumnMaxChars(ColumnId.BRANCH, 1).withColumnMaxCharsStep(ColumnId.BRANCH, -1)
        assertNull(one.columns.single { it.id == ColumnId.BRANCH }.maxChars)
        assertNull(DisplaySettings().withColumnMaxCharsStep(ColumnId.BRANCH, -1).columns.single { it.id == ColumnId.BRANCH }.maxChars)
    }

    @Test fun pinToggleKeepsPinOrder() {
        val d = DisplaySettings().withPinToggled("dev").withPinToggled("main")
        assertEquals(listOf("dev", "main"), d.pinned)
        assertEquals(listOf("main"), d.withPinToggled("dev").pinned)
    }

    @Test fun newDefaults() {
        val d = DisplaySettings()
        assertEquals(TinySource.PINNED, d.tinySource)
        assertEquals(TinyTap.RUN, d.tinyTap)
        assertEquals(Language.EN, d.language)
        assertEquals(WidgetView.BRANCHES, WidgetConfig().view)
    }

    @Test fun jsonRoundTripAndUnknownKeys() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val c = WidgetConfig("o/r", "tok1", DisplaySettings(fontSizeSp = 14, glyphStyle = GlyphStyle.BRACKET, pinned = listOf("main"), language = Language.UK), WidgetView.ORDER, ColumnId.AGE)
        assertEquals(c, json.decodeFromString(WidgetConfig.serializer(), json.encodeToString(WidgetConfig.serializer(), c)))
        assertEquals(WidgetConfig("x/y"), json.decodeFromString(WidgetConfig.serializer(), """{"repo":"x/y","future":1,"useGlobalDefaults":true}"""))
    }

    @Test fun idleAndRowStepsWalkTheChoicesAndEndInOff() {
        val d = DisplaySettings()
        assertEquals(60, d.withIdleDaysStep(1).hideIdleDays)
        assertEquals(14, d.withIdleDaysStep(-1).hideIdleDays)
        assertNull(DisplaySettings(hideIdleDays = 365).withIdleDaysStep(1).hideIdleDays)
        assertNull(DisplaySettings(hideIdleDays = null).withIdleDaysStep(1).hideIdleDays)
        assertEquals(365, DisplaySettings(hideIdleDays = null).withIdleDaysStep(-1).hideIdleDays)
        assertEquals(1, DisplaySettings(hideIdleDays = 1).withIdleDaysStep(-1).hideIdleDays)
        // A value that is not a step (older settings) restarts from the default.
        assertEquals(60, DisplaySettings(hideIdleDays = 45).withIdleDaysStep(1).hideIdleDays)
        assertEquals(100, d.withMaxRowsStep(1).maxRows)
        assertNull(d.withMaxRowsStep(2).maxRows)
        assertEquals(5, DisplaySettings(maxRows = 5).withMaxRowsStep(-1).maxRows)
    }

    @Test fun confirmAndThemeSurviveSerialization() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val c = WidgetConfig("o/r", display = DisplaySettings(theme = Theme.SYSTEM), confirm = Confirm(ConfirmKind.DELETE_TOKEN, "id1"))
        assertEquals(c, json.decodeFromString(WidgetConfig.serializer(), json.encodeToString(WidgetConfig.serializer(), c)))
        assertEquals(Theme.DARK, DisplaySettings().theme)
    }
}
