package de.mbraisch.garminwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import de.mbraisch.garminwidget.R
import de.mbraisch.garminwidget.data.AppStore
import de.mbraisch.garminwidget.data.DisplayMode
import de.mbraisch.garminwidget.data.Snapshot
import de.mbraisch.garminwidget.data.WidgetSettings
import de.mbraisch.garminwidget.sync.SyncScheduler
import de.mbraisch.garminwidget.ui.MainActivity
import de.mbraisch.garminwidget.ui.OpenFromWidgetActivity
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Das Garmin-Widget (ein Widget, zwei Darstellungen – Auswahl in der App):
 *
 * - **Mehrere Werte:** Raster mit 1–4 Kacheln je nach Größe.
 * - **Wischen:** Kartenstapel; jeder Wisch nach oben oder unten springt genau eine Karte weiter.
 *
 * Gebaut mit klassischen RemoteViews, weil nur so der Kartenstapel (StackView) zuverlässig läuft.
 */
class GarminWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val state = WidgetData.load(context)
        appWidgetIds.forEach { update(context, manager, it, state) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        // Größe geändert → passendes Layout neu aufbauen
        update(context, manager, appWidgetId, WidgetData.load(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            SyncScheduler.syncNow(context)
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        private const val ACTION_REFRESH = "de.mbraisch.garminwidget.action.REFRESH"

        fun updateAll(context: Context, state: WidgetData) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, GarminWidgetReceiver::class.java))
            ids.forEach { update(context, manager, it, state) }
        }

        private fun update(context: Context, manager: AppWidgetManager, appWidgetId: Int, state: WidgetData) {
            val options = manager.getAppWidgetOptions(appWidgetId)
            // Hochformat: minimale Breite, maximale Höhe
            val w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 150).toFloat()
            val h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 150).toFloat()
            val layout = WidgetLayout(w, h)

            val rv = when {
                !state.loggedIn -> message(context, if (layout.isTiny) "Anmelden" else "In der App anmelden")
                state.settings.tiles.isEmpty() -> message(context, "Kacheln wählen")
                state.snapshot.fetchedAt == 0L -> message(context, "Lädt…")
                state.settings.mode == DisplayMode.SWIPE -> stack(context, appWidgetId)
                else -> TileViews.grid(
                    context = context,
                    values = state.settings.tiles.take(layout.gridCount).map { state.snapshot[it] },
                    widthDp = w,
                    heightDp = h,
                    detailed = layout.detailed,
                    headerText = headerText(state.snapshot),
                    headerError = state.snapshot.error != null,
                    onTileClick = openIntent(context),
                    onRefresh = refreshIntent(context),
                )
            }
            manager.updateAppWidget(appWidgetId, rv)
            if (state.loggedIn && state.settings.mode == DisplayMode.SWIPE) {
                manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.sw_stack)
            }
        }

        /** Kartenstapel; die Karten liefert [SwipeWidgetService]. */
        private fun stack(context: Context, appWidgetId: Int): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_swipe)
            val serviceIntent = Intent(context, SwipeWidgetService::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            // Eindeutige Daten-URI, damit Android pro Widget eine eigene Kartenliste anlegt
            serviceIntent.data = Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME))
            @Suppress("DEPRECATION")
            rv.setRemoteAdapter(R.id.sw_stack, serviceIntent)
            rv.setEmptyView(R.id.sw_stack, R.id.sw_empty)
            rv.setTextViewText(R.id.sw_empty, "Lädt…")
            rv.setPendingIntentTemplate(
                R.id.sw_stack,
                PendingIntent.getActivity(
                    context, 0, Intent(context, OpenFromWidgetActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                ),
            )
            return rv
        }

        private fun message(context: Context, text: String): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_message)
            rv.setTextViewText(R.id.msg_text, text)
            rv.setOnClickPendingIntent(
                R.id.msg_root,
                PendingIntent.getActivity(
                    context, 1, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            return rv
        }

        private fun openIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 2, Intent(context, OpenFromWidgetActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun refreshIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 3,
            Intent(context, GarminWidgetReceiver::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun headerText(snapshot: Snapshot): String {
            if (snapshot.fetchedAt <= 0) return "–"
            val time = SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(snapshot.fetchedAt))
            return if (snapshot.error != null) "! $time" else time
        }
    }
}

/** Gespeicherter Stand, den das Widget anzeigt. */
data class WidgetData(
    val settings: WidgetSettings,
    val snapshot: Snapshot,
    val loggedIn: Boolean,
) {
    companion object {
        suspend fun read(context: Context): WidgetData {
            val store = AppStore(context)
            return WidgetData(store.currentSettings(), store.currentSnapshot(), store.isLoggedIn())
        }

        /** Für Broadcast-Empfänger (dort gibt es keine Coroutine); das Lesen dauert nur Millisekunden. */
        fun load(context: Context): WidgetData = runBlocking { read(context) }
    }
}

/**
 * Leitet aus der Widgetgröße (dp) die Rasterzellen ab und daraus,
 * wie viele Werte gleichzeitig Platz haben.
 */
data class WidgetLayout(val widthDp: Float, val heightDp: Float) {
    val cols: Int = when {
        widthDp < 130f -> 1
        widthDp < 230f -> 2
        widthDp < 320f -> 3
        else -> 4
    }
    val rows: Int = when {
        heightDp < 150f -> 1
        heightDp < 280f -> 2
        heightDp < 400f -> 3
        else -> 4
    }

    val isTiny: Boolean get() = cols == 1 && rows == 1

    /** Anzahl gleichzeitig sichtbarer Werte im Rastermodus. */
    val gridCount: Int = when {
        cols == 1 && rows == 1 -> 1
        cols == 1 || rows == 1 -> maxOf(cols, rows).coerceIn(2, 4)
        else -> 4
    }

    /** Große Widgets (ab 3×4 bzw. 4×3): kurze Zusatzinfos und Kopfzeile mit Aktualisieren. */
    val detailed: Boolean get() = cols * rows >= 12
}

/** Aktualisiert alle Garmin-Widgets auf dem Startbildschirm. */
object WidgetUpdater {
    suspend fun updateAll(context: Context) {
        val state = WidgetData.read(context)
        GarminWidgetReceiver.updateAll(context, state)
    }
}
