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

    @Test fun effectiveFollowsFlag() {
        val global = DisplaySettings(fontSizeSp = 15)
        val own = DisplaySettings(fontSizeSp = 12)
        assertEquals(15, WidgetConfig(display = own).effective(global).fontSizeSp)
        assertEquals(12, WidgetConfig(useGlobalDefaults = false, display = own).effective(global).fontSizeSp)
    }

    @Test fun jsonRoundTripAndUnknownKeys() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val c = WidgetConfig("o/r", "tok1", false, DisplaySettings(fontSizeSp = 14, glyphStyle = GlyphStyle.BRACKET, pinned = listOf("main")))
        assertEquals(c, json.decodeFromString(WidgetConfig.serializer(), json.encodeToString(WidgetConfig.serializer(), c)))
        assertEquals(WidgetConfig("x/y"), json.decodeFromString(WidgetConfig.serializer(), """{"repo":"x/y","future":1}"""))
    }
}
