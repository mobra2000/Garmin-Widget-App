package de.mbraisch.garminwidget.widget

import android.content.Context
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.ColorRes
import de.mbraisch.garminwidget.R
import de.mbraisch.garminwidget.data.Accent
import de.mbraisch.garminwidget.data.MetricValue
import de.mbraisch.garminwidget.data.Tone

/**
 * Baut die Seiten des Wisch-Widgets (Kartenstapel) als RemoteViews.
 * Jede Seite zeigt 1 oder 2 Kacheln – nur Symbol und Wert, bei Platz als Ring.
 */
object SwipeViews {

    private class SlotIds(
        val tile: Int, val ring: Int, val track: Int, val prog: Int, val ringIcon: Int,
        val ringValue: Int, val ringUnit: Int, val icon: Int, val value: Int, val sub: Int,
    )

    private val SLOT_A = SlotIds(
        R.id.sw_tile_a, R.id.sw_ring_a, R.id.sw_track_a, R.id.sw_prog_a, R.id.sw_ricon_a,
        R.id.sw_rvalue_a, R.id.sw_runit_a, R.id.sw_icon_a, R.id.sw_value_a, R.id.sw_sub_a,
    )
    private val SLOT_B = SlotIds(
        R.id.sw_tile_b, R.id.sw_ring_b, R.id.sw_track_b, R.id.sw_prog_b, R.id.sw_ricon_b,
        R.id.sw_rvalue_b, R.id.sw_runit_b, R.id.sw_icon_b, R.id.sw_value_b, R.id.sw_sub_b,
    )

    private class Colors(@ColorRes val container: Int, @ColorRes val on: Int, @ColorRes val accent: Int)

    private fun colorsFor(v: MetricValue): Colors = when {
        !v.available -> Colors(R.color.sw_n_container, R.color.sw_n_on, R.color.sw_n_accent)
        v.tone == Tone.BAD -> Colors(R.color.sw_e_container, R.color.sw_e_on, R.color.sw_e_accent)
        v.type.accent == Accent.PRIMARY -> Colors(R.color.sw_p_container, R.color.sw_p_on, R.color.sw_p_accent)
        v.type.accent == Accent.SECONDARY -> Colors(R.color.sw_s_container, R.color.sw_s_on, R.color.sw_s_accent)
        else -> Colors(R.color.sw_t_container, R.color.sw_t_on, R.color.sw_t_accent)
    }

    /**
     * Eine Seite des Stapels.
     * @param pageW/pageH verfügbare Größe einer Seite in dp
     */
    fun page(context: Context, values: List<MetricValue>, pageW: Float, pageH: Float, detailed: Boolean): RemoteViews {
        val horizontal = pageW >= pageH
        return if (values.size >= 2) {
            // gemeinsame Karte mit 6 dp Innenrand, darin zwei Kacheln mit 6 dp Abstand
            val availW = pageW - 12f
            val availH = pageH - 12f
            val rv = RemoteViews(context.packageName, if (horizontal) R.layout.sw_page_2h else R.layout.sw_page_2v)
            val tileW = if (horizontal) (availW - 6f) / 2 else availW
            val tileH = if (horizontal) availH else (availH - 6f) / 2
            bindTile(context, rv, SLOT_A, values[0], tileW, tileH, detailed, tilePadding = 8f)
            bindTile(context, rv, SLOT_B, values[1], tileW, tileH, detailed, tilePadding = 8f)
            rv
        } else {
            val rv = RemoteViews(context.packageName, R.layout.sw_page_1)
            bindTile(context, rv, SLOT_A, values[0], pageW, pageH, detailed, tilePadding = 12f)
            rv
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
        val showSub = detailed && v.available && v.subtitle.isNotEmpty() && innerH >= 110f
        val ringSpace = minOf(innerW, innerH - if (showSub) 18f else 0f)
        val hasRing = v.available && v.progress != null && ringSpace >= 70f

        if (showSub) {
            rv.setViewVisibility(ids.sub, View.VISIBLE)
            rv.setTextViewText(ids.sub, v.subtitle)
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

            rv.setTextViewText(ids.ringValue, v.compact)
            rv.setTextViewTextSize(ids.ringValue, TypedValue.COMPLEX_UNIT_SP, (ring * 0.24f).coerceIn(14f, 34f))
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
            rv.sizeDp(ids.icon, (minDim * 0.30f).coerceIn(18f, 34f))

            val text = if (!v.isText && v.available && v.unit.isNotEmpty() && v.unit.length <= 4) "${v.compact} ${v.unit}" else v.compact
            val size = if (v.isText || !v.available) (innerW * 0.14f).coerceIn(12f, 20f) else (minDim * 0.30f).coerceIn(16f, 32f)
            rv.setTextViewText(ids.value, text)
            rv.setTextViewTextSize(ids.value, TypedValue.COMPLEX_UNIT_SP, size)
            rv.textColor(context, ids.value, c.on)
        }
    }
}
