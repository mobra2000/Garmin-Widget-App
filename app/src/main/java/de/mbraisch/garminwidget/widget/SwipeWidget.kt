package de.mbraisch.garminwidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import de.mbraisch.garminwidget.R
import de.mbraisch.garminwidget.data.MetricValue

/** Liefert die Karten für den Wischmodus (Kartenstapel) des Garmin-Widgets. */
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
        val state = WidgetData.load(context)

        // Größe des Widgets (Hochformat: minimale Breite, maximale Höhe)
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
        val w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 150).toFloat()
        val h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 150).toFloat()
        val layout = WidgetLayout(w, h)
        detailed = layout.detailed
        // Ab Android 12 ist der Stapel so vergrößert, dass die vorderste Karte das ganze
        // Widget füllt (siehe GarminWidgetReceiver.stack); rundherum bleiben 4 dp Rand.
        // Davor: Karte = 90 % der Fläche, der Rest zeigt die Karten dahinter.
        if (Build.VERSION.SDK_INT >= 31) {
            pageW = w - 8f
            pageH = h - 8f
        } else {
            pageW = w * 0.9f - 8f
            pageH = h * 0.9f - 8f
        }

        pages = if (!state.loggedIn || state.snapshot.fetchedAt == 0L) {
            emptyList()
        } else {
            val perPage = if (layout.isTiny) 1 else state.settings.perPage
            state.settings.tiles.map { state.snapshot[it] }.chunked(perPage)
        }
    }

    override fun onDestroy() {}

    override fun getCount(): Int = pages.size

    override fun getViewAt(position: Int): RemoteViews {
        val page = pages.getOrNull(position) ?: return RemoteViews(context.packageName, R.layout.sw_page_1)
        val rv = TileViews.page(context, page, pageW, pageH, detailed)
        rv.setOnClickFillInIntent(R.id.sw_page, Intent())
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 3

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}
