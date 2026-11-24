package io.github.saberbird.cidget.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.saberbird.cidget.core.DisplaySettings
import io.github.saberbird.cidget.core.WidgetConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private const val WIDGET_PREFIX = "widget_config_"
private const val LIST_OPEN_PREFIX = "repo_list_open_"
private val GLOBAL_KEY = stringPreferencesKey("global_display")

/**
 * App settings in the single DataStore `settings`: one [WidgetConfig] JSON per `appWidgetId` and the
 * global [DisplaySettings] defaults. Unreadable values fall back to defaults.
 */
class SettingsRepository(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.settingsStore)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Emits whenever any stored setting changes (widget configs, global defaults, repo-list flags). The app
     * uses it to redraw widgets, because a Glance session that has gone idle does not follow flows by itself.
     */
    fun changes(): Flow<Map<String, Any>> =
        store.data.map { p -> p.asMap().mapKeys { it.key.name } }.distinctUntilChanged()

    private fun key(appWidgetId: Int) = stringPreferencesKey(WIDGET_PREFIX + appWidgetId)

    private fun decodeConfig(s: String?): WidgetConfig =
        s?.let { runCatching { json.decodeFromString(WidgetConfig.serializer(), it) }.getOrNull() } ?: WidgetConfig()

    private fun decodeGlobal(s: String?): DisplaySettings =
        (s?.let { runCatching { json.decodeFromString(DisplaySettings.serializer(), it) }.getOrNull() } ?: DisplaySettings()).normalized()

    /** Configuration of one widget (defaults while none is stored). */
    fun widgetConfig(appWidgetId: Int): Flow<WidgetConfig> =
        store.data.map { decodeConfig(it[key(appWidgetId)]) }.distinctUntilChanged()

    suspend fun getWidgetConfig(appWidgetId: Int): WidgetConfig = widgetConfig(appWidgetId).first()

    /** Reads, transforms and stores a widget's configuration atomically. */
    suspend fun updateWidget(appWidgetId: Int, transform: (WidgetConfig) -> WidgetConfig) {
        store.edit { prefs ->
            val next = transform(decodeConfig(prefs[key(appWidgetId)]))
            prefs[key(appWidgetId)] = json.encodeToString(WidgetConfig.serializer(), next)
        }
    }

    /** `owner/repo` shown by the widget, or null while none is chosen. */
    fun widgetRepo(appWidgetId: Int): Flow<String?> = widgetConfig(appWidgetId).map { it.repo }.distinctUntilChanged()

    suspend fun getWidgetRepo(appWidgetId: Int): String? = getWidgetConfig(appWidgetId).repo

    suspend fun setWidgetRepo(appWidgetId: Int, repo: String, tokenId: String? = null) =
        updateWidget(appWidgetId) { it.copy(repo = repo, tokenId = tokenId) }

    /** Token id for [repo] taken from the first widget that shows it, or null (use the default token). */
    suspend fun tokenIdForRepo(repo: String): String? = allWidgets().values.firstOrNull { it.repo == repo }?.tokenId

    private fun listKey(appWidgetId: Int) = booleanPreferencesKey(LIST_OPEN_PREFIX + appWidgetId)

    /** True while the widget shows its repo list (`ls` view) instead of the branch list (D24). */
    fun repoListOpen(appWidgetId: Int): Flow<Boolean> =
        store.data.map { it[listKey(appWidgetId)] ?: false }.distinctUntilChanged()

    suspend fun setRepoListOpen(appWidgetId: Int, open: Boolean) {
        store.edit { if (open) it[listKey(appWidgetId)] = true else it.remove(listKey(appWidgetId)) }
    }

    /** Global defaults for widgets that do not override them. */
    fun globalDisplay(): Flow<DisplaySettings> = store.data.map { decodeGlobal(it[GLOBAL_KEY]) }.distinctUntilChanged()

    suspend fun updateGlobal(transform: (DisplaySettings) -> DisplaySettings) {
        store.edit { prefs ->
            val next = transform(decodeGlobal(prefs[GLOBAL_KEY])).normalized()
            prefs[GLOBAL_KEY] = json.encodeToString(DisplaySettings.serializer(), next)
        }
    }

    /** The settings a widget renders with: its own or the global defaults, following its flag. */
    fun effectiveDisplay(appWidgetId: Int): Flow<DisplaySettings> =
        combine(widgetConfig(appWidgetId), globalDisplay()) { c, g -> c.effective(g) }.distinctUntilChanged()

    /** Forgets a widget's settings (called when the widget is removed). */
    suspend fun deleteWidget(appWidgetId: Int) {
        store.edit { it.remove(key(appWidgetId)); it.remove(listKey(appWidgetId)) }
    }

    /** All stored widget configurations by `appWidgetId`. */
    suspend fun allWidgets(): Map<Int, WidgetConfig> =
        store.data.first().asMap().mapNotNull { (k, v) ->
            val id = k.name.removePrefix(WIDGET_PREFIX).takeIf { k.name.startsWith(WIDGET_PREFIX) }?.toIntOrNull()
            id?.let { it to decodeConfig(v as? String) }
        }.toMap()

    /** Distinct repos used by at least one widget. */
    suspend fun reposInUse(): Set<String> = allWidgets().values.mapNotNull { it.repo }.toSet()

    /** Copies the display settings (and the use-global flag) of [from] to [to]; the repo stays. */
    suspend fun copySettings(from: Int, to: Int) {
        val source = getWidgetConfig(from)
        updateWidget(to) { it.copy(useGlobalDefaults = source.useGlobalDefaults, display = source.display) }
    }
}
