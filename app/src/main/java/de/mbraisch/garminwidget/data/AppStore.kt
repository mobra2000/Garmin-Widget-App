package de.mbraisch.garminwidget.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.store: DataStore<Preferences> by preferencesDataStore(name = "garmin_widget")

enum class DisplayMode { GRID, SWIPE }

/** Alle Einstellungen, die die App und das Widget betreffen. */
data class WidgetSettings(
    /** Aktive Kacheln in der gewählten Reihenfolge. */
    val tiles: List<MetricType> = MetricType.DEFAULT_ORDER,
    val mode: DisplayMode = DisplayMode.GRID,
    /** Werte pro Seite im Wischmodus (1 oder 2). */
    val perPage: Int = 1,
    /** Aktualisierungsintervall in Minuten. */
    val intervalMinutes: Int = 60,
    /** App: true = Systemfarben (Material You), false = feste Garmin-Farben. */
    val dynamicColor: Boolean = true,
) {
    /** Alle Kacheln: erst die aktiven in ihrer Reihenfolge, dann die inaktiven. */
    val allInOrder: List<MetricType>
        get() = tiles + MetricType.entries.filter { it !in tiles }
}

/** Zentraler Speicher für Einstellungen, letzte Daten und Anmeldestatus. */
class AppStore(private val context: Context) {

    private object Keys {
        val TILES = stringPreferencesKey("tiles")
        val MODE = stringPreferencesKey("mode")
        val PER_PAGE = intPreferencesKey("per_page")
        val INTERVAL = intPreferencesKey("interval")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val SNAPSHOT = stringPreferencesKey("snapshot")
        val LOGGED_IN = booleanPreferencesKey("logged_in")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val SYNCING = booleanPreferencesKey("syncing")
    }

    val settings: Flow<WidgetSettings> = context.store.data.map { parseSettings(it) }.distinctUntilChanged()

    private fun parseSettings(p: Preferences): WidgetSettings {
        val tiles = p[Keys.TILES]
            ?.split(',')
            ?.mapNotNull { MetricType.fromId(it) }
            ?: MetricType.DEFAULT_ORDER
        return WidgetSettings(
            tiles = tiles,
            mode = runCatching { DisplayMode.valueOf(p[Keys.MODE] ?: "") }.getOrDefault(DisplayMode.GRID),
            perPage = (p[Keys.PER_PAGE] ?: 1).coerceIn(1, 2),
            intervalMinutes = p[Keys.INTERVAL] ?: 60,
            dynamicColor = p[Keys.DYNAMIC] ?: true,
        )
    }

    val snapshot: Flow<Snapshot> = context.store.data
        .map { it[Keys.SNAPSHOT] }
        .distinctUntilChanged()
        .map { Snapshot.fromJsonString(it) }

    val loggedIn: Flow<Boolean> = context.store.data.map { it[Keys.LOGGED_IN] ?: false }.distinctUntilChanged()
    val syncing: Flow<Boolean> = context.store.data.map { it[Keys.SYNCING] ?: false }.distinctUntilChanged()
    val displayName: Flow<String?> = context.store.data.map { it[Keys.DISPLAY_NAME] }.distinctUntilChanged()

    suspend fun currentSettings(): WidgetSettings = settings.first()
    suspend fun currentSnapshot(): Snapshot = snapshot.first()
    suspend fun isLoggedIn(): Boolean = loggedIn.first()
    suspend fun currentDisplayName(): String? = displayName.first()

    /**
     * Ändert die Einstellungen atomar (auch bei schnellen, aufeinanderfolgenden Klicks
     * geht keine Änderung verloren). Liefert alten und neuen Stand.
     */
    suspend fun updateSettings(transform: (WidgetSettings) -> WidgetSettings): Pair<WidgetSettings, WidgetSettings> {
        var result: Pair<WidgetSettings, WidgetSettings>? = null
        context.store.edit { p ->
            val old = parseSettings(p)
            val s = transform(old)
            p[Keys.TILES] = s.tiles.joinToString(",") { it.id }
            p[Keys.MODE] = s.mode.name
            p[Keys.PER_PAGE] = s.perPage
            p[Keys.INTERVAL] = s.intervalMinutes
            p[Keys.DYNAMIC] = s.dynamicColor
            result = old to s
        }
        return result!!
    }

    suspend fun saveSnapshot(s: Snapshot) {
        context.store.edit { it[Keys.SNAPSHOT] = s.toJsonString() }
    }

    /** [clearData] = false behält die letzten Werte (z. B. wenn nur die Anmeldung abgelaufen ist). */
    suspend fun setLoggedIn(value: Boolean, displayName: String? = null, clearData: Boolean = true) {
        context.store.edit { p ->
            p[Keys.LOGGED_IN] = value
            if (displayName != null) p[Keys.DISPLAY_NAME] = displayName
            if (!value && clearData) {
                p.remove(Keys.DISPLAY_NAME)
                p.remove(Keys.SNAPSHOT)
            }
        }
    }

    suspend fun setDisplayName(name: String) {
        context.store.edit { it[Keys.DISPLAY_NAME] = name }
    }

    suspend fun setSyncing(value: Boolean) {
        context.store.edit { it[Keys.SYNCING] = value }
    }
}
