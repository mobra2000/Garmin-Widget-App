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
import android.widget.RemoteViewsService
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import de.mbraisch.garminwidget.R
import de.mbraisch.garminwidget.data.AppStore
import de.mbraisch.garminwidget.data.MetricValue
import de.mbraisch.garminwidget.ui.MainActivity
import de.mbraisch.garminwidget.ui.OpenFromWidgetActivity
import kotlinx.coroutines.runBlocking

/**
 * Wisch-Widget: Android-Kartenstapel (StackView). Ein Wisch nach oben oder unten
 * blättert genau eine Seite weiter und rastet ein. Jede Seite zeigt 1 oder 2 Werte.
 */
class SwipeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { update(context, manager, it) }
        manager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.sw_stack)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        // Größe geändert → Seiten neu aufbauen
        update(context, manager, appWidgetId)
        manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.sw_stack)
    }

    companion object {
        fun update(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val rv = RemoteViews(context.packageName, R.layout.widget_swipe)

            val serviceIntent = Intent(context, SwipeWidgetService::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            // Eindeutige Daten-URI, damit Android pro Widget eine eigene Liste anlegt
            serviceIntent.data = Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME))
            @Suppress("DEPRECATION")
            rv.setRemoteAdapter(R.id.sw_stack, serviceIntent)
            rv.setEmptyView(R.id.sw_stack, R.id.sw_empty)

            // Text, wenn (noch) keine Seiten da sind
            val emptyText = runCatching {
                runBlocking {
                    val store = AppStore(context)
                    when {
                        !store.isLoggedIn() -> "In der App anmelden"
                        store.currentSettings().tiles.isEmpty() -> "Kacheln wählen"
                        else -> "Lädt…"
                    }
                }
            }.getOrDefault("–")
            rv.setTextViewText(R.id.sw_empty, emptyText)
            rv.setOnClickPendingIntent(
                R.id.sw_empty,
                PendingIntent.getActivity(
                    context, 1, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )

            // Tippen auf eine Seite → Garmin Connect (bzw. diese App, je nach Einstellung)
            rv.setPendingIntentTemplate(
                R.id.sw_stack,
                PendingIntent.getActivity(
                    context, 0, Intent(context, OpenFromWidgetActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                ),
            )
            manager.updateAppWidget(appWidgetId, rv)
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SwipeWidgetProvider::class.java))
            if (ids.isEmpty()) return
            ids.forEach { update(context, manager, it) }
            manager.notifyAppWidgetViewDataChanged(ids, R.id.sw_stack)
        }
    }
}

/** Liefert die Seiten des Kartenstapels. */
class SwipeWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        SwipeFactory(applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID))
}

private class SwipeFactory(private val context: Context, private val appWidgetId: Int) :
    RemoteViewsService.RemoteViewsFactory {

    private var pages: List<List<MetricValue>> = emptyList()
    private var pageW = 150f
    private var pageH = 150f
    private var detailed = false

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val store = AppStore(context)
        val (settings, snapshot, loggedIn) = runBlocking {
            Triple(store.currentSettings(), store.currentSnapshot(), store.isLoggedIn())
        }

        // Größe des Widgets (Hochformat: minimale Breite, maximale Höhe)
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
        val w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 150).toFloat()
        val h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 150).toFloat()
        val layout = WidgetLayout(w.dp, h.dp)
        detailed = layout.detailed
        // Der Kartenstapel macht jede Karte 90 % so groß wie das Widget (der Rest zeigt
        // die Karten dahinter) und lässt rundherum 4 dp Rand.
        pageW = w * 0.9f - 8f
        pageH = h * 0.9f - 8f

        pages = if (!loggedIn || snapshot.fetchedAt == 0L) {
            emptyList()
        } else {
            val perPage = if (layout.isTiny) 1 else settings.perPage
            settings.tiles.map { snapshot[it] }.chunked(perPage)
        }
    }

    override fun onDestroy() {}

    override fun getCount(): Int = pages.size

    override fun getViewAt(position: Int): RemoteViews {
        val page = pages.getOrNull(position) ?: return RemoteViews(context.packageName, R.layout.sw_page_1)
        val rv = SwipeViews.page(context, page, pageW, pageH, detailed)
        rv.setOnClickFillInIntent(R.id.sw_page, Intent())
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 3

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}

/** Aktualisiert beide Widget-Arten (Raster und Wischen). */
object WidgetUpdater {
    suspend fun updateAll(context: Context) {
        GarminWidget().updateAll(context)
        SwipeWidgetProvider.updateAll(context)
    }
}
