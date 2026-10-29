package de.mbraisch.garminwidget.widget

import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.ColorRes
import de.mbraisch.garminwidget.R
import de.mbraisch.garminwidget.data.Accent
import de.mbraisch.garminwidget.data.MetricType
import de.mbraisch.garminwidget.data.MetricValue
import de.mbraisch.garminwidget.data.Tone

/**
 * Baut die Kacheln des Widgets als RemoteViews – für den Rastermodus (1–4 Kacheln)
 * und für die Karten des Wischmodus (Kartenstapel). Jede Kachel zeigt nur Symbol und
 * Wert, bei genug Platz als Ring; Schriftgrößen werden an die Kachelbreite angepasst,
 * damit nichts abgeschnitten wird.
 */
object TileViews {

    private class SlotIds(
        val tile: Int, val ring: Int, val track: Int, val prog: Int, val ringIcon: Int,
        val ringValue: Int, val ringUnit: Int, val icon: Int, val value: Int, val sub: Int,
    )

    private val SLOTS = listOf(
        SlotIds(R.id.sw_tile_a, R.id.sw_ring_a, R.id.sw_track_a, R.id.sw_prog_a, R.id.sw_ricon_a,
            R.id.sw_rvalue_a, R.id.sw_runit_a, R.id.sw_icon_a, R.id.sw_value_a, R.id.sw_sub_a),
        SlotIds(R.id.sw_tile_b, R.id.sw_ring_b, R.id.sw_track_b, R.id.sw_prog_b, R.id.sw_ricon_b,
            R.id.sw_rvalue_b, R.id.sw_runit_b, R.id.sw_icon_b, R.id.sw_value_b, R.id.sw_sub_b),
        SlotIds(R.id.sw_tile_c, R.id.sw_ring_c, R.id.sw_track_c, R.id.sw_prog_c, R.id.sw_ricon_c,
            R.id.sw_rvalue_c, R.id.sw_runit_c, R.id.sw_icon_c, R.id.sw_value_c, R.id.sw_sub_c),
        SlotIds(R.id.sw_tile_d, R.id.sw_ring_d, R.id.sw_track_d, R.id.sw_prog_d, R.id.sw_ricon_d,
            R.id.sw_rvalue_d, R.id.sw_runit_d, R.id.sw_icon_d, R.id.sw_value_d, R.id.sw_sub_d),
    )

    private class Colors(@ColorRes val container: Int, @ColorRes val on: Int, @ColorRes val accent: Int)

    private fun colorsFor(v: MetricValue): Colors = when {
        !v.available -> Colors(R.color.sw_n_container, R.color.sw_n_on, R.color.sw_n_accent)
        v.tone == Tone.BAD -> Colors(R.color.sw_e_container, R.color.sw_e_on, R.color.sw_e_accent)
        v.type.accent == Accent.PRIMARY -> Colors(R.color.sw_p_container, R.color.sw_p_on, R.color.sw_p_accent)
        v.type.accent == Accent.SECONDARY -> Colors(R.color.sw_s_container, R.color.sw_s_on, R.color.sw_s_accent)
        else -> Colors(R.color.sw_t_container, R.color.sw_t_on, R.color.sw_t_accent)
    }

    // ------------------------------------------------------------------
    // Rastermodus
    // ------------------------------------------------------------------

    private const val GAP = 6f
    private const val HEADER = 24f

    /**
     * Raster mit 1–4 Kacheln für ein Widget der Größe [widthDp] × [heightDp].
     * Die Anordnung (Zeile, Spalte, 2×2) richtet sich nach dem Seitenverhältnis.
     */
    fun grid(
        context: Context,
        values: List<MetricValue>,
        widthDp: Float,
        heightDp: Float,
        detailed: Boolean,
        headerText: String?,
        headerError: Boolean,
        onTileClick: PendingIntent,
        onRefresh: PendingIntent,
    ): RemoteViews {
        if (values.size == 1) {
            val rv = RemoteViews(context.packageName, R.layout.gr_1)
            bindTile(context, rv, SLOTS[0], values[0], widthDp, heightDp, detailed, tilePadding = 12f)
            rv.setOnClickPendingIntent(R.id.sw_tile_a, onTileClick)
            return rv
        }

        val showHeader = detailed && headerText != null
        val innerW = widthDp - 2 * GAP
        val innerH = heightDp - 2 * GAP - if (showHeader) HEADER else 0f
        val n = values.size
        val aspect = innerW / innerH

        val (layout, tileW, tileH) = when {
            n == 4 && aspect > 0.55f && aspect < 2.6f ->
                Triple(R.layout.gr_4g, (innerW - GAP) / 2, (innerH - GAP) / 2)
            aspect >= 1f -> Triple(
                when (n) { 2 -> R.layout.gr_2h; 3 -> R.layout.gr_3h; else -> R.layout.gr_4h },
                (innerW - GAP * (n - 1)) / n, innerH,
            )
            else -> Triple(
                when (n) { 2 -> R.layout.gr_2v; 3 -> R.layout.gr_3v; else -> R.layout.gr_4v },
                innerW, (innerH - GAP * (n - 1)) / n,
            )
        }

        val rv = RemoteViews(context.packageName, layout)
        values.forEachIndexed { i, v ->
            bindTile(context, rv, SLOTS[i], v, tileW, tileH, detailed, tilePadding = 8f)
            rv.setOnClickPendingIntent(SLOTS[i].tile, onTileClick)
        }
        if (showHeader) {
            rv.setViewVisibility(R.id.gr_header, View.VISIBLE)
            rv.setTextViewText(R.id.gr_time, headerText)
            rv.textColor(context, R.id.gr_time, if (headerError) R.color.sw_e_accent else R.color.sw_n_on)
            rv.setOnClickPendingIntent(R.id.gr_refresh, onRefresh)
        }
        return rv
    }

    // ------------------------------------------------------------------
    // Wischmodus: eine Karte des Stapels
    // ------------------------------------------------------------------

    /** Eine Karte mit 1 oder 2 Werten; [pageW]/[pageH] = Größe der Karte in dp. */
    fun page(context: Context, values: List<MetricValue>, pageW: Float, pageH: Float, detailed: Boolean): RemoteViews {
        val horizontal = pageW >= pageH
        return if (values.size >= 2) {
            // gemeinsame Karte mit 6 dp Innenrand, darin zwei Kacheln mit 6 dp Abstand
            val availW = pageW - 12f
            val availH = pageH - 12f
            val rv = RemoteViews(context.packageName, if (horizontal) R.layout.sw_page_2h else R.layout.sw_page_2v)
            val tileW = if (horizontal) (availW - GAP) / 2 else availW
            val tileH = if (horizontal) availH else (availH - GAP) / 2
            bindTile(context, rv, SLOTS[0], values[0], tileW, tileH, detailed, tilePadding = 8f)
            bindTile(context, rv, SLOTS[1], values[1], tileW, tileH, detailed, tilePadding = 8f)
            rv
        } else {
            val rv = RemoteViews(context.packageName, R.layout.sw_page_1)
            bindTile(context, rv, SLOTS[0], values[0], pageW, pageH, detailed, tilePadding = 12f)
            rv
        }
    }

    // ------------------------------------------------------------------
    // Eine Kachel
    // ------------------------------------------------------------------

    /** Schriftgröße (dp), bei der [text] in [widthDp] passt – höchstens [max]. */
    private fun fit(text: String, widthDp: Float, max: Float, min: Float = 10f, bold: Boolean = true): Float {
        val perChar = if (bold) 0.60f else 0.54f
        val byWidth = widthDp / (text.length.coerceAtLeast(1) * perChar)
        return minOf(max, byWidth).coerceAtLeast(min)
    }

    private fun bindTile(
        context: Context,
        rv: RemoteViews,
        ids: SlotIds,
        v: MetricValue,
        tileW: Float,
        tileH: Float,
        detailed: Boolean,
        tilePadding: Float,
    ) {
        val c = colorsFor(v)
        rv.tintBackground(context, ids.tile, c.container)

        val innerW = tileW - 2 * tilePadding
        val innerH = tileH - 2 * tilePadding
        // Beim Trainingsvorschlag gehören Dauer und Pace zum Wert und stehen immer da
        val isSuggestion = v.type == MetricType.SUGGESTION
        val showSub = (detailed || isSuggestion) && v.available && v.subtitle.isNotEmpty() &&
            innerH >= (if (isSuggestion) 56f else 110f)
        val subSize = if (showSub) fit(v.subtitle, innerW, if (isSuggestion) 14f else 11f, min = 9f, bold = false) else 0f
        val ringSpace = minOf(innerW, innerH - if (showSub) subSize + 8f else 0f)
        val hasRing = v.available && v.progress != null && ringSpace >= 64f

        if (showSub) {
            rv.setViewVisibility(ids.sub, View.VISIBLE)
            rv.setTextViewText(ids.sub, v.subtitle)
            rv.setTextViewTextSize(ids.sub, TypedValue.COMPLEX_UNIT_DIP, subSize)
            rv.textColor(context, ids.sub, c.on)
        } else {
            rv.setViewVisibility(ids.sub, View.GONE)
        }

        if (hasRing) {
            val ring = ringSpace.coerceAtMost(180f)
            rv.setViewVisibility(ids.ring, View.VISIBLE)
            rv.setViewVisibility(ids.icon, View.GONE)
            rv.setViewVisibility(ids.value, View.GONE)
            rv.sizeDp(ids.ring, ring)

            rv.tintImage(context, ids.track, c.on)
            val progressRes = RingDrawables.forProgress(v.progress ?: 0f)
            if (progressRes != null) {
                rv.setViewVisibility(ids.prog, View.VISIBLE)
                rv.setImageViewResource(ids.prog, progressRes)
                rv.tintImage(context, ids.prog, c.accent)
            } else {
                rv.setViewVisibility(ids.prog, View.INVISIBLE)
            }

            rv.setImageViewResource(ids.ringIcon, v.type.icon)
            rv.tintImage(context, ids.ringIcon, c.accent)
            rv.sizeDp(ids.ringIcon, (ring * 0.18f).coerceIn(12f, 24f))

            // Innerer Durchmesser des Rings ≈ 70 % – der Wert muss da hineinpassen
            rv.setTextViewText(ids.ringValue, v.compact)
            rv.setTextViewTextSize(ids.ringValue, TypedValue.COMPLEX_UNIT_DIP, fit(v.compact, ring * 0.62f, (ring * 0.26f).coerceIn(14f, 40f)))
            rv.textColor(context, ids.ringValue, c.on)

            if (v.unit.isNotEmpty() && ring >= 100f) {
                rv.setViewVisibility(ids.ringUnit, View.VISIBLE)
                rv.setTextViewText(ids.ringUnit, v.unit)
                rv.textColor(context, ids.ringUnit, c.on)
            } else {
                rv.setViewVisibility(ids.ringUnit, View.GONE)
            }
        } else {
            val minDim = minOf(innerW, innerH)
            rv.setViewVisibility(ids.ring, View.GONE)
            rv.setViewVisibility(ids.icon, View.VISIBLE)
            rv.setViewVisibility(ids.value, View.VISIBLE)

            rv.setImageViewResource(ids.icon, v.type.icon)
            rv.tintImage(context, ids.icon, c.accent)
            rv.sizeDp(ids.icon, (minDim * 0.28f).coerceIn(18f, 34f))

            val withUnit = !v.isText && v.available && v.unit.isNotEmpty() && v.unit.length <= 4
            val text = if (withUnit) "${v.compact} ${v.unit}" else v.compact
            val maxSize = if (v.isText || !v.available) 22f else (minDim * 0.30f).coerceIn(16f, 34f)
            rv.setTextViewText(ids.value, text)
            rv.setTextViewTextSize(ids.value, TypedValue.COMPLEX_UNIT_DIP, fit(text, innerW, maxSize, min = 11f))
            rv.textColor(context, ids.value, c.on)
        }
    }

    // --- Farben: ab Android 12 als Ressource (passt sich Hell/Dunkel und Material You an) ---

    private fun RemoteViews.tintBackground(context: Context, id: Int, @ColorRes res: Int) {
        if (Build.VERSION.SDK_INT >= 31) setColorStateList(id, "setBackgroundTintList", res)
        else setInt(id, "setBackgroundColor", context.getColor(res))
    }

    private fun RemoteViews.tintImage(context: Context, id: Int, @ColorRes res: Int) {
        if (Build.VERSION.SDK_INT >= 31) setColorStateList(id, "setImageTintList", res)
        else setInt(id, "setColorFilter", context.getColor(res))
    }

    private fun RemoteViews.textColor(context: Context, id: Int, @ColorRes res: Int) {
        if (Build.VERSION.SDK_INT >= 31) setColorStateList(id, "setTextColor", res)
        else setTextColor(id, context.getColor(res))
    }

    private fun RemoteViews.sizeDp(id: Int, size: Float) {
        if (Build.VERSION.SDK_INT >= 31) {
            setViewLayoutWidth(id, size, TypedValue.COMPLEX_UNIT_DIP)
            setViewLayoutHeight(id, size, TypedValue.COMPLEX_UNIT_DIP)
        }
    }
}
