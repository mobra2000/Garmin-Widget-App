package de.mbraisch.garminwidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.TypedValue
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import de.mbraisch.garminwidget.R
import de.mbraisch.garminwidget.data.MetricValue
import kotlin.math.ceil
import kotlin.math.roundToInt

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
    private var bleed = 0f
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
        // Ab Android 12 liegen alle Karten deckungsgleich genau auf dem Widget
        // (siehe SwipeGeometry). Davor: Karte = 90 % der Fläche, der Rest zeigt die Karten dahinter.
        if (Build.VERSION.SDK_INT >= 31) {
            pageW = w
            pageH = h
            bleed = SwipeGeometry.BLEED_DP
        } else {
            pageW = w * 0.9f - 8f
            pageH = h * 0.9f - 8f
            bleed = 0f
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
        val rv = TileViews.page(context, page, pageW, pageH, detailed, bleed)
        rv.setOnClickFillInIntent(R.id.sw_page, Intent())
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 3

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}

/**
 * Geometrie des Kartenstapels ab Android 12.
 *
 * Androids Kartenstapel (StackView) legt die Karten normalerweise versetzt hintereinander
 * (jede weitere 10 % nach oben rechts). Beim Weiterwischen rutscht die nächste Karte dann
 * schräg nach unten links an ihren Platz. Der Versatz berechnet sich aus
 * „Stapelbreite − Kartenbreite“, sobald die Karten schmaler sind als der Platz, den der Stapel
 * ihnen anbietet. Deshalb:
 *
 * - Der Stapel ist rundherum um den inneren Rahmen der Karten (4 dp) plus [BLEED_DP] größer
 *   als das Widget (negative Ränder).
 * - Rechts und unten bekommt er einen negativen Innenabstand – dadurch bietet er den Karten
 *   mehr Platz an, als sie brauchen.
 * - Jede Karte ist genau so groß wie der Stapel (Widget + 2 × [BLEED_DP] + Rahmen).
 *
 * → Versatz ≈ 0: alle Karten liegen deckungsgleich, beim Wischen bewegt sich nur die
 * oberste Karte – und zwar senkrecht. Die Karten ragen [BLEED_DP] über den Widgetrand hinaus;
 * dort schneidet das Widget sie mit seinen eigenen, überall gleich runden Ecken ab.
 */
object SwipeGeometry {
    /** So weit ragen die Karten über den Widgetrand hinaus (dp). */
    const val BLEED_DP = 4f

    /** Innerer Rahmen, den Android um jede Karte im Stapel legt (in StackView fest 4 dp, aufgerundet auf ganze Pixel). */
    fun framePaddingDp(context: Context): Float {
        val density = context.resources.displayMetrics.density
        return ceil(density * 4f) / density
    }

    fun apply(context: Context, rv: RemoteViews, stackId: Int, widthDp: Float, heightDp: Float) {
        val overhang = framePaddingDp(context) + BLEED_DP
        listOf(RemoteViews.MARGIN_LEFT, RemoteViews.MARGIN_TOP, RemoteViews.MARGIN_RIGHT, RemoteViews.MARGIN_BOTTOM)
            .forEach { rv.setViewLayoutMargin(stackId, it, -overhang, TypedValue.COMPLEX_UNIT_DIP) }
        // Negativer Innenabstand rechts/unten: der Stapel bietet den Karten mehr Platz an, als sie
        // brauchen – nur dann richtet er den Versatz nach der tatsächlichen Kartengröße.
        val density = context.resources.displayMetrics.density
        val stackW = (widthDp + 2 * overhang) * density
        val stackH = (heightDp + 2 * overhang) * density
        rv.setViewPadding(stackId, 0, 0, -stackW.roundToInt(), -stackH.roundToInt())
    }
}
