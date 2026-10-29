package de.mbraisch.garminwidget.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import de.mbraisch.garminwidget.data.AppStore
import de.mbraisch.garminwidget.data.DisplayMode
import de.mbraisch.garminwidget.data.MetricValue
import de.mbraisch.garminwidget.data.Snapshot
import de.mbraisch.garminwidget.data.WidgetSettings
import de.mbraisch.garminwidget.sync.SyncScheduler
import de.mbraisch.garminwidget.ui.MainActivity
import kotlinx.coroutines.flow.combine

class GarminWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GarminWidget()
}

/** Alles, was das Widget zum Zeichnen braucht. */
data class WidgetState(
    val settings: WidgetSettings,
    val snapshot: Snapshot,
    val loggedIn: Boolean,
    val syncing: Boolean,
)

/**
 * Das Garmin-Widget. [SizeMode.Exact] liefert die tatsächliche Größe, sodass für jede
 * Rastergröße von 1×1 bis 4×4 ein eigenes Layout gewählt wird (siehe [WidgetLayout]).
 */
class GarminWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = AppStore(context)
        val initial = WidgetState(
            settings = store.currentSettings(),
            snapshot = store.currentSnapshot(),
            loggedIn = store.isLoggedIn(),
            syncing = false,
        )
        val states = combine(store.settings, store.snapshot, store.loggedIn, store.syncing) { s, snap, li, sy ->
            WidgetState(s, snap, li, sy)
        }
        provideContent {
            val state by states.collectAsState(initial = initial)
            GlanceTheme {
                WidgetRoot(state)
            }
        }
    }
}

/** Startet eine sofortige Aktualisierung (Button im Widget). */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        SyncScheduler.syncNow(context)
    }
}

// ---------------------------------------------------------------------------
// Aufbau
// ---------------------------------------------------------------------------

private val GAP = 6.dp

@Composable
private fun WidgetRoot(state: WidgetState) {
    val size = LocalSize.current
    val layout = WidgetLayout.of(size.width, size.height)

    when {
        !state.loggedIn -> MessageWidget(
            title = "Garmin Widget",
            text = if (layout.isTiny) "Anmelden" else "Tippen, um dich bei Garmin anzumelden",
        )
        state.settings.tiles.isEmpty() -> MessageWidget(
            title = "Keine Kacheln",
            text = "In der App Kacheln auswählen",
        )
        state.snapshot.fetchedAt == 0L -> MessageWidget(
            title = if (state.syncing) "Lädt…" else "Noch keine Daten",
            text = if (layout.isTiny) "" else "Tippen zum Öffnen",
        )
        state.settings.mode == DisplayMode.SWIPE -> SwipeWidget(state, layout)
        else -> GridWidget(state, layout)
    }
}

/** Hintergrund des gesamten Widgets mit dem System-Eckenradius (Pixel-Look). */
@Composable
private fun GlanceModifier.widgetBackgroundShape(): GlanceModifier {
    val base = this.appWidgetBackground().background(GlanceTheme.colors.widgetBackground)
    return if (Build.VERSION.SDK_INT >= 31) {
        base.cornerRadius(android.R.dimen.system_app_widget_background_radius)
    } else {
        base.cornerRadius(24.dp)
    }
}

@Composable
private fun MessageWidget(title: String, text: String) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .widgetBackgroundShape()
            .padding(10.dp)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
            WidgetIcon(de.mbraisch.garminwidget.R.drawable.ic_suggestion, 28.dp, GlanceTheme.colors.primary)
            Spacer(GlanceModifier.height(4.dp))
            WText(title, 14f, GlanceTheme.colors.onSurface, bold = true, maxLines = 1, center = true)
            if (text.isNotEmpty()) {
                WText(text, 12f, GlanceTheme.colors.onSurfaceVariant, maxLines = 2, center = true)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Rastermodus: 1–4 Werte gleichzeitig
// ---------------------------------------------------------------------------

@Composable
private fun GridWidget(state: WidgetState, layout: WidgetLayout) {
    val values = state.settings.tiles.take(layout.gridCount).map { state.snapshot[it] }

    // Einzelne Kachel: die Kachel *ist* das Widget (kein Rahmen drumherum)
    if (values.size == 1) {
        TileView(
            value = values.first(),
            width = layout.width,
            height = layout.height,
            outer = true,
            modifier = GlanceModifier.fillMaxSize(),
        )
        return
    }

    val pad = layout.padding
    val innerW = layout.width - pad * 2
    var innerH = layout.height - pad * 2
    val header = layout.showHeader
    if (header) innerH -= HEADER_HEIGHT + GAP

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .widgetBackgroundShape()
            .padding(pad),
    ) {
        if (header) {
            Header(state)
            Spacer(GlanceModifier.height(GAP))
        }
        TileGroup(values, innerW, innerH)
    }
}

/** Ordnet 2–4 Kacheln je nach Seitenverhältnis als Zeile, Spalte oder 2×2-Raster an. */
@Composable
private fun TileGroup(values: List<MetricValue>, w: Dp, h: Dp) {
    val n = values.size
    val aspect = w.value / h.value
    val arrangement = when {
        n == 1 -> Arrangement.SINGLE
        n == 4 && aspect > 0.55f && aspect < 2.6f -> Arrangement.GRID
        aspect >= 1f -> Arrangement.ROW
        else -> Arrangement.COLUMN
    }
    when (arrangement) {
        Arrangement.SINGLE -> TileView(values[0], w, h, modifier = GlanceModifier.fillMaxSize())

        Arrangement.ROW -> {
            val tileW = (w - GAP * (n - 1)) / n
            Row(modifier = GlanceModifier.fillMaxSize()) {
                values.forEachIndexed { i, v ->
                    if (i > 0) Spacer(GlanceModifier.width(GAP))
                    TileView(v, tileW, h, modifier = GlanceModifier.defaultWeight().fillMaxHeight())
                }
            }
        }

        Arrangement.COLUMN -> {
            val tileH = (h - GAP * (n - 1)) / n
            Column(modifier = GlanceModifier.fillMaxSize()) {
                values.forEachIndexed { i, v ->
                    if (i > 0) Spacer(GlanceModifier.height(GAP))
                    TileView(v, w, tileH, modifier = GlanceModifier.defaultWeight().fillMaxWidth())
                }
            }
        }

        Arrangement.GRID -> {
            val tileW = (w - GAP) / 2
            val tileH = (h - GAP) / 2
            Column(modifier = GlanceModifier.fillMaxSize()) {
                Row(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
                    TileView(values[0], tileW, tileH, modifier = GlanceModifier.defaultWeight().fillMaxHeight())
                    Spacer(GlanceModifier.width(GAP))
                    TileView(values[1], tileW, tileH, modifier = GlanceModifier.defaultWeight().fillMaxHeight())
                }
                Spacer(GlanceModifier.height(GAP))
                Row(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
                    TileView(values[2], tileW, tileH, modifier = GlanceModifier.defaultWeight().fillMaxHeight())
                    Spacer(GlanceModifier.width(GAP))
                    TileView(values[3], tileW, tileH, modifier = GlanceModifier.defaultWeight().fillMaxHeight())
                }
            }
        }
    }
}

private enum class Arrangement { SINGLE, ROW, COLUMN, GRID }

// ---------------------------------------------------------------------------
// Wischmodus: 1–2 Werte pro Seite, die übrigen per Wischen nach oben/unten
// ---------------------------------------------------------------------------

@Composable
private fun SwipeWidget(state: WidgetState, layout: WidgetLayout) {
    val perPage = if (layout.isTiny) 1 else state.settings.perPage
    val pages = state.settings.tiles.map { state.snapshot[it] }.chunked(perPage)

    val pad = layout.padding
    val innerW = layout.width - pad * 2
    var innerH = layout.height - pad * 2
    val header = layout.showHeader
    if (header) innerH -= HEADER_HEIGHT + GAP

    val showDots = pages.size > 1 && innerW >= 90.dp
    val dotsW = if (showDots) 12.dp else 0.dp

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .widgetBackgroundShape()
            .padding(pad),
    ) {
        if (header) {
            Header(state)
            Spacer(GlanceModifier.height(GAP))
        }
        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            items(pages.indices.toList(), itemId = { it.toLong() }) { index ->
                // Jede Seite ist genau so hoch wie der sichtbare Bereich → eine Seite pro Wischbewegung
                Row(
                    modifier = GlanceModifier.fillMaxWidth().height(innerH),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                ) {
                    Box(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                        TileGroup(pages[index], innerW - dotsW, innerH)
                    }
                    if (showDots) {
                        PageDots(count = pages.size, current = index, width = dotsW)
                    }
                }
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int, width: Dp) {
    val shown = count.coerceAtMost(9)
    Column(
        modifier = GlanceModifier.width(width).fillMaxHeight(),
        horizontalAlignment = Alignment.Horizontal.End,
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        for (i in 0 until shown) {
            if (i > 0) Spacer(GlanceModifier.height(4.dp))
            val active = i == current
            Box(
                modifier = GlanceModifier
                    .size(if (active) 6.dp else 5.dp)
                    .cornerRadius(3.dp)
                    .background(if (active) GlanceTheme.colors.primary else GlanceTheme.colors.outline),
            ) {}
        }
    }
}

// ---------------------------------------------------------------------------
// Kopfzeile (ab ca. 3×3): Stand + Aktualisieren
// ---------------------------------------------------------------------------

private val HEADER_HEIGHT = 26.dp

@Composable
private fun Header(state: WidgetState) {
    val time = if (state.snapshot.fetchedAt > 0) {
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.GERMANY).format(java.util.Date(state.snapshot.fetchedAt))
    } else "–"
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(HEADER_HEIGHT).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        WidgetIcon(de.mbraisch.garminwidget.R.drawable.ic_suggestion, 16.dp, GlanceTheme.colors.primary)
        Spacer(GlanceModifier.width(6.dp))
        WText(
            text = when {
                state.syncing -> "Aktualisiere…"
                state.snapshot.error != null -> "Stand $time · Fehler"
                else -> "Stand $time"
            },
            sizeSp = 12f,
            color = GlanceTheme.colors.onSurfaceVariant,
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        Box(
            modifier = GlanceModifier
                .size(HEADER_HEIGHT)
                .cornerRadius(13.dp)
                .clickable(actionRunCallback<RefreshAction>()),
            contentAlignment = Alignment.Center,
        ) {
            WidgetIcon(de.mbraisch.garminwidget.R.drawable.ic_refresh, 18.dp, GlanceTheme.colors.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------------------
// Größenlogik
// ---------------------------------------------------------------------------

/**
 * Leitet aus der Widgetgröße (dp) die Rasterzellen ab und daraus,
 * wie viele Werte gleichzeitig Platz haben.
 */
data class WidgetLayout(val width: Dp, val height: Dp) {
    val cols: Int = when {
        width < 130.dp -> 1
        width < 230.dp -> 2
        width < 320.dp -> 3
        else -> 4
    }
    val rows: Int = when {
        height < 150.dp -> 1
        height < 280.dp -> 2
        height < 400.dp -> 3
        else -> 4
    }

    val isTiny: Boolean get() = cols == 1 && rows == 1

    /** Anzahl gleichzeitig sichtbarer Werte im Rastermodus. */
    val gridCount: Int = when {
        cols == 1 && rows == 1 -> 1
        cols == 1 || rows == 1 -> maxOf(cols, rows).coerceIn(2, 4)
        else -> 4
    }

    /** Kopfzeile mit Uhrzeit und Aktualisieren-Button erst bei großen Widgets (ca. 3×3+). */
    val showHeader: Boolean get() = cols >= 3 && rows >= 3

    val padding: Dp get() = if (min(width, height) < 110.dp) 6.dp else 8.dp

    companion object {
        fun of(width: Dp, height: Dp) = WidgetLayout(width, height)
    }
}
