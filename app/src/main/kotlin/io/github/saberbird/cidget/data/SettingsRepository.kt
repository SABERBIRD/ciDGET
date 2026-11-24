package io.github.saberbird.cidget.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private const val WIDGET_REPO_PREFIX = "widget_repo_"

/**
 * App settings in the single DataStore `settings`. For now: which repository each widget shows.
 * Later tasks add the rest of `WidgetConfig` and the global defaults.
 */
class SettingsRepository(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.settingsStore)

    private fun key(appWidgetId: Int) = stringPreferencesKey(WIDGET_REPO_PREFIX + appWidgetId)

    /** `owner/repo` shown by the widget, or null while none is chosen. */
    fun widgetRepo(appWidgetId: Int): Flow<String?> = store.data.map { it[key(appWidgetId)] }.distinctUntilChanged()

    suspend fun getWidgetRepo(appWidgetId: Int): String? = widgetRepo(appWidgetId).first()

    suspend fun setWidgetRepo(appWidgetId: Int, repo: String) {
        store.edit { it[key(appWidgetId)] = repo }
    }

    /** Forgets a widget's settings (called when the widget is removed). */
    suspend fun deleteWidget(appWidgetId: Int) {
        store.edit { it.remove(key(appWidgetId)) }
    }

    /** Distinct repos used by at least one widget. */
    suspend fun reposInUse(): Set<String> =
        store.data.first().asMap().filter { it.key.name.startsWith(WIDGET_REPO_PREFIX) }.values.map { it as String }.toSet()
}
