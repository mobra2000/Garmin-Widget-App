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
import de.mbraisch.garminwidget.R
import de.mbraisch.garminwidget.data.AppStore
import de.mbraisch.garminwidget.data.DisplayMode
import de.mbraisch.garminwidget.data.MetricValue
import de.mbraisch.garminwidget.data.Snapshot
import de.mbraisch.garminwidget.data.WidgetSettings
import de.mbraisch.garminwidget.sync.SyncScheduler
import de.mbraisch.garminwidget.ui.MainActivity
import de.mbraisch.garminwidget.ui.OpenFromWidgetActivity
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
 * Das Raster-Widget (1–4 Werte gleichzeitig). Der Wischmodus ist ein eigenes Widget,
 * siehe [SwipeWidgetProvider]. [SizeMode.Exact] liefert die tatsächliche Größe, sodass für jede
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

/** Startet eine sofortige Aktualisierung (Button im großen Widget). */
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
    val layout = WidgetLayout(size.width, size.height)

    when {
        !state.loggedIn -> MessageWidget(if (layout.isTiny) "Anmelden" else "In der App anmelden", openApp = true)
        state.settings.tiles.isEmpty() -> MessageWidget("Kacheln wählen", openApp = true)
        state.snapshot.fetchedAt == 0L -> MessageWidget(if (state.syncing) "Lädt…" else "Keine Daten", openApp = true)
        state.settings.mode == DisplayMode.SWIPE -> ScrollWidget(state, layout)
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

/** Hinweis statt Werten (nicht angemeldet, keine Kacheln, noch keine Daten). */
@Composable
private fun MessageWidget(text: String, openApp: Boolean) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .widgetBackgroundShape()
            .padding(8.dp)
            .clickable(
                if (openApp) actionStartActivity<MainActivity>() else actionStartActivity<OpenFromWidgetActivity>()
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
            WidgetIcon(R.drawable.ic_suggestion, 26.dp, GlanceTheme.colors.primary)
            Spacer(GlanceModifier.height(4.dp))
            WText(text, 12f, GlanceTheme.colors.onSurfaceVariant, maxLines = 2, center = true)
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
            detailed = layout.detailed,
            modifier = GlanceModifier.fillMaxSize(),
        )
        return
    }

    val pad = layout.padding
    val innerW = layout.width - pad * 2
    var innerH = layout.height - pad * 2
    if (layout.detailed) innerH -= HEADER_HEIGHT

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .widgetBackgroundShape()
            .padding(pad),
    ) {
        if (layout.detailed) Header(state)
        TileGroup(values, innerW, innerH, layout.detailed)
    }
}

/** Ordnet 2–4 Kacheln je nach Seitenverhältnis als Zeile, Spalte oder 2×2-Raster an. */
@Composable
private fun TileGroup(values: List<MetricValue>, w: Dp, h: Dp, detailed: Boolean) {
    val n = values.size
    val aspect = w.value / h.value
    val arrangement = when {
        n == 1 -> Arrangement.SINGLE
        n == 4 && aspect > 0.55f && aspect < 2.6f -> Arrangement.GRID
        aspect >= 1f -> Arrangement.ROW
        else -> Arrangement.COLUMN
    }
    when (arrangement) {
        Arrangement.SINGLE -> TileView(values[0], w, h, GlanceModifier.fillMaxSize(), detailed = detailed)

        Arrangement.ROW -> {
            val tileW = (w - GAP * (n - 1)) / n
            Row(modifier = GlanceModifier.fillMaxSize()) {
                values.forEachIndexed { i, v ->
                    if (i > 0) Spacer(GlanceModifier.width(GAP))
                    TileView(v, tileW, h, GlanceModifier.defaultWeight().fillMaxHeight(), detailed = detailed)
                }
            }
        }

        Arrangement.COLUMN -> {
            val tileH = (h - GAP * (n - 1)) / n
            Column(modifier = GlanceModifier.fillMaxSize()) {
                values.forEachIndexed { i, v ->
                    if (i > 0) Spacer(GlanceModifier.height(GAP))
                    TileView(v, w, tileH, GlanceModifier.defaultWeight().fillMaxWidth(), detailed = detailed)
                }
            }
        }

        Arrangement.GRID -> {
            val tileW = (w - GAP) / 2
            val tileH = (h - GAP) / 2
            Column(modifier = GlanceModifier.fillMaxSize()) {
                Row(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
                    TileView(values[0], tileW, tileH, GlanceModifier.defaultWeight().fillMaxHeight(), detailed = detailed)
                    Spacer(GlanceModifier.width(GAP))
                    TileView(values[1], tileW, tileH, GlanceModifier.defaultWeight().fillMaxHeight(), detailed = detailed)
                }
                Spacer(GlanceModifier.height(GAP))
                Row(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
                    TileView(values[2], tileW, tileH, GlanceModifier.defaultWeight().fillMaxHeight(), detailed = detailed)
                    Spacer(GlanceModifier.width(GAP))
                    TileView(values[3], tileW, tileH, GlanceModifier.defaultWeight().fillMaxHeight(), detailed = detailed)
                }
            }
        }
    }
}

private enum class Arrangement { SINGLE, ROW, COLUMN, GRID }

// ---------------------------------------------------------------------------
// Durchscrollen: 1–2 Werte pro Seite, jede Seite so hoch wie das Widget
// ---------------------------------------------------------------------------

@Composable
private fun ScrollWidget(state: WidgetState, layout: WidgetLayout) {
    val perPage = if (layout.isTiny) 1 else state.settings.perPage
    val pages = state.settings.tiles.map { state.snapshot[it] }.chunked(perPage)
    val pad = layout.padding
    val innerW = layout.width - pad * 2
    val innerH = layout.height - pad * 2
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .widgetBackgroundShape()
            .padding(pad),
    ) {
        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            items(pages.indices.toList(), itemId = { it.toLong() }) { index ->
                Box(modifier = GlanceModifier.fillMaxWidth().height(innerH)) {
                    TileGroup(pages[index], innerW, innerH, layout.detailed)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Kopfzeile (nur große Widgets): Uhrzeit + Aktualisieren
// ---------------------------------------------------------------------------

private val HEADER_HEIGHT = 24.dp

@Composable
private fun Header(state: WidgetState) {
    val time = if (state.snapshot.fetchedAt > 0) {
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.GERMANY).format(java.util.Date(state.snapshot.fetchedAt))
    } else "–"
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(HEADER_HEIGHT).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.End,
    ) {
        WText(
            text = when {
                state.syncing -> "…"
                state.snapshot.error != null -> "! $time"
                else -> time
            },
            sizeSp = 11f,
            color = if (state.snapshot.error != null && !state.syncing) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(GlanceModifier.width(2.dp))
        Box(
            modifier = GlanceModifier
                .size(HEADER_HEIGHT)
                .cornerRadius(12.dp)
                .clickable(actionRunCallback<RefreshAction>()),
            contentAlignment = Alignment.Center,
        ) {
            WidgetIcon(R.drawable.ic_refresh, 16.dp, GlanceTheme.colors.onSurfaceVariant)
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

    /** Große Widgets (ab 3×4 bzw. 4×3): kurze Zusatzinfos und Kopfzeile mit Aktualisieren. */
    val detailed: Boolean get() = cols * rows >= 12

    val padding: Dp get() = if (min(width, height) < 110.dp) 6.dp else 8.dp
}
