package io.github.saberbird.cidget.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.saberbird.cidget.core.ColumnId
import io.github.saberbird.cidget.core.DisplaySettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsRepositoryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val file = File(Files.createTempDirectory("ds").toFile(), "settings.preferences_pb")
    private fun repo() = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))

    @Test fun widgetsAreIndependent() = runBlocking<Unit> {
        val r = repo()
        r.setWidgetRepo(1, "o/a")
        r.setWidgetRepo(2, "o/b")
        r.updateWidget(2) { it.copy(useGlobalDefaults = false, display = it.display.withFontSize(16)) }
        assertEquals("o/a", r.getWidgetRepo(1))
        assertEquals(setOf("o/a", "o/b"), r.reposInUse())
        assertTrue(r.getWidgetConfig(1).useGlobalDefaults)
        assertEquals(13, r.effectiveDisplay(1).first().fontSizeSp)
        assertEquals(16, r.effectiveDisplay(2).first().fontSizeSp)
    }

    @Test fun globalDefaultsChangeInheritingWidgetsOnly() = runBlocking<Unit> {
        val r = repo()
        r.setWidgetRepo(1, "o/a")
        r.setWidgetRepo(2, "o/b")
        r.updateWidget(2) { it.copy(useGlobalDefaults = false, display = DisplaySettings().withFontSize(12)) }
        r.updateGlobal { it.withFontSize(15).withColumnEnabled(ColumnId.AGE, false) ?: it }
        assertEquals(15, r.effectiveDisplay(1).first().fontSizeSp)
        assertFalse(r.effectiveDisplay(1).first().columns.single { it.id == ColumnId.AGE }.enabled)
        assertEquals(12, r.effectiveDisplay(2).first().fontSizeSp)
        assertTrue(r.effectiveDisplay(2).first().columns.single { it.id == ColumnId.AGE }.enabled)
    }

    @Test fun copySettingsKeepsTargetRepo() = runBlocking<Unit> {
        val r = repo()
        r.setWidgetRepo(1, "o/a")
        r.setWidgetRepo(2, "o/b")
        r.updateWidget(1) { it.copy(useGlobalDefaults = false, display = it.display.withColumnMoved(ColumnId.AGE, -1).withFontSize(11)) }
        r.copySettings(1, 2)
        val c = r.getWidgetConfig(2)
        assertEquals("o/b", c.repo)
        assertFalse(c.useGlobalDefaults)
        assertEquals(11, c.display.fontSizeSp)
        assertEquals(r.getWidgetConfig(1).display, c.display)
    }

    @Test fun valuesArePersistedAndDeleteForgets() = runBlocking<Unit> {
        val r = repo()
        r.setWidgetRepo(7, "o/x")
        r.updateGlobal { it.withFontSize(14) }
        assertEquals("o/x", r.getWidgetRepo(7))
        assertEquals(14, r.globalDisplay().first().fontSizeSp)
        assertTrue(file.exists())
        r.deleteWidget(7)
        assertNull(r.getWidgetRepo(7))
        assertTrue(r.allWidgets().isEmpty())
    }
}
