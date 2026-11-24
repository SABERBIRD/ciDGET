package io.github.saberbird.cidget.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.saberbird.cidget.core.ColumnId
import io.github.saberbird.cidget.core.WidgetView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        r.updateWidget(2) { it.copy(display = it.display.withFontSize(16).withColumnMoved(ColumnId.AGE, -1)) }
        assertEquals("o/a", r.getWidgetRepo(1))
        assertEquals(setOf("o/a", "o/b"), r.reposInUse())
        assertEquals(13, r.getWidgetConfig(1).display.fontSizeSp)
        assertEquals(16, r.getWidgetConfig(2).display.fontSizeSp)
        assertEquals(ColumnId.BRANCH, r.getWidgetConfig(1).display.columns[1].id)
        assertEquals(ColumnId.AGE, r.getWidgetConfig(2).display.columns[1].id)
    }

    @Test fun pickingARepoReturnsToTheBranchList() = runBlocking<Unit> {
        val r = repo()
        r.updateWidget(1) { it.copy(view = WidgetView.REPOS) }
        assertEquals(WidgetView.REPOS, r.getWidgetConfig(1).view)
        r.setWidgetRepo(1, "o/a", "tok")
        val c = r.getWidgetConfig(1)
        assertEquals(WidgetView.BRANCHES, c.view)
        assertEquals("tok", c.tokenId)
        assertEquals("tok", r.tokenIdForRepo("o/a"))
    }

    @Test fun viewAndSelectedColumnArePersisted() = runBlocking<Unit> {
        val r = repo()
        r.updateWidget(3) { it.copy(view = WidgetView.ORDER, selected = ColumnId.AGE) }
        val c = r.getWidgetConfig(3)
        assertEquals(WidgetView.ORDER, c.view)
        assertEquals(ColumnId.AGE, c.selected)
    }

    @Test fun storedDisplayIsRepairedOnRead() = runBlocking<Unit> {
        val r = repo()
        r.updateWidget(1) { it.copy(display = it.display.copy(fontSizeSp = 99)) }
        assertEquals(16, r.getWidgetConfig(1).display.fontSizeSp)
        assertTrue(r.getWidgetConfig(1).display.columns.any { it.id == ColumnId.BRANCH && it.enabled })
        assertFalse(r.getWidgetConfig(1).display.columns.isEmpty())
    }

    @Test fun valuesArePersistedAndDeleteForgets() = runBlocking<Unit> {
        val r = repo()
        r.setWidgetRepo(7, "o/x")
        assertEquals("o/x", r.getWidgetRepo(7))
        assertTrue(file.exists())
        r.deleteWidget(7)
        assertNull(r.getWidgetRepo(7))
        assertTrue(r.allWidgets().isEmpty())
    }
}
