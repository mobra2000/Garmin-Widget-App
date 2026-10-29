package de.mbraisch.garminwidget.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.ColorRes
import androidx.annotation.RequiresApi
import de.mbraisch.garminwidget.R
import de.mbraisch.garminwidget.data.Accent
import de.mbraisch.garminwidget.data.MetricValue
import de.mbraisch.garminwidget.data.Tone
import de.mbraisch.garminwidget.ui.OpenFromWidgetActivity

/**
 * Wischmodus als Android-Kartenstapel (StackView): Ein Wisch nach oben oder unten
 * blättert genau eine Seite weiter und rastet ein. Glance kennt dieses Element nicht,
 * deshalb wird es hier direkt als RemoteViews gebaut (ab Android 12).
 */
@RequiresApi(31)
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
     * @param widthDp/heightDp Größe des Widgets
     * @param detailed große Widgets: kurze Zusatzzeile anzeigen
     */
    fun build(
        context: Context,
        pages: List<List<MetricValue>>,
        widthDp: Float,
        heightDp: Float,
        detailed: Boolean,
    ): RemoteViews {
        val root = RemoteViews(context.packageName, R.layout.widget_swipe)

        // Innenmaße einer Seite (Rand 6 dp; der Stapel braucht oben etwas Platz für die Karten dahinter)
        val pageW = widthDp - 12f
        val pageH = (heightDp - 12f) * 0.88f
        val horizontal = pageW >= pageH

        val items = RemoteViews.RemoteCollectionItems.Builder()
            .setHasStableIds(true)
            .setViewTypeCount(3)
        pages.forEachIndexed { index, page ->
            val item: RemoteViews
            if (page.size >= 2) {
                item = RemoteViews(context.packageName, if (horizontal) R.layout.sw_page_2h else R.layout.sw_page_2v)
                val tileW = if (horizontal) (pageW - 6f) / 2 else pageW
                val tileH = if (horizontal) pageH else (pageH - 6f) / 2
                bindTile(item, SLOT_A, page[0], tileW, tileH, detailed)
                bindTile(item, SLOT_B, page[1], tileW, tileH, detailed)
            } else {
                item = RemoteViews(context.packageName, R.layout.sw_page_1)
                bindTile(item, SLOT_A, page[0], pageW, pageH, detailed)
            }
            item.setOnClickFillInIntent(R.id.sw_page, Intent())
            items.addItem(index.toLong(), item)
        }
        root.setRemoteAdapter(R.id.sw_stack, items.build())
        root.setEmptyView(R.id.sw_stack, R.id.sw_empty)

        val template = PendingIntent.getActivity(
            context,
            0,
            Intent(context, OpenFromWidgetActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        root.setPendingIntentTemplate(R.id.sw_stack, template)
        return root
    }

    private fun bindTile(rv: RemoteViews, ids: SlotIds, v: MetricValue, tileW: Float, tileH: Float, detailed: Boolean) {
        val c = colorsFor(v)
        rv.setColorStateList(ids.tile, "setBackgroundTintList", c.container)

        val innerW = tileW - 16f
        val innerH = tileH - 16f
        val showSub = detailed && v.available && v.subtitle.isNotEmpty() && innerH >= 110f
        val ringSpace = minOf(innerW, innerH - if (showSub) 18f else 0f)
        val hasRing = v.available && v.progress != null && ringSpace >= 70f

        // Untertitel
        if (showSub) {
            rv.setViewVisibility(ids.sub, View.VISIBLE)
            rv.setTextViewText(ids.sub, v.subtitle)
            rv.setColorStateList(ids.sub, "setTextColor", c.on)
        } else {
            rv.setViewVisibility(ids.sub, View.GONE)
        }

        if (hasRing) {
            val ring = ringSpace.coerceAtMost(150f)
            rv.setViewVisibility(ids.ring, View.VISIBLE)
            rv.setViewVisibility(ids.icon, View.GONE)
            rv.setViewVisibility(ids.value, View.GONE)
            rv.setViewLayoutWidth(ids.ring, ring, TypedValue.COMPLEX_UNIT_DIP)
            rv.setViewLayoutHeight(ids.ring, ring, TypedValue.COMPLEX_UNIT_DIP)

            rv.setColorStateList(ids.track, "setImageTintList", c.on)
            val progressRes = RingDrawables.forProgress(v.progress ?: 0f)
            if (progressRes != null) {
                rv.setViewVisibility(ids.prog, View.VISIBLE)
                rv.setImageViewResource(ids.prog, progressRes)
                rv.setColorStateList(ids.prog, "setImageTintList", c.accent)
            } else {
                rv.setViewVisibility(ids.prog, View.INVISIBLE)
            }

            val iconSize = (ring * 0.18f).coerceIn(12f, 24f)
            rv.setImageViewResource(ids.ringIcon, v.type.icon)
            rv.setColorStateList(ids.ringIcon, "setImageTintList", c.accent)
            rv.setViewLayoutWidth(ids.ringIcon, iconSize, TypedValue.COMPLEX_UNIT_DIP)
            rv.setViewLayoutHeight(ids.ringIcon, iconSize, TypedValue.COMPLEX_UNIT_DIP)

            rv.setTextViewText(ids.ringValue, v.compact)
            rv.setTextViewTextSize(ids.ringValue, TypedValue.COMPLEX_UNIT_SP, (ring * 0.24f).coerceIn(14f, 34f))
            rv.setColorStateList(ids.ringValue, "setTextColor", c.on)

            if (v.unit.isNotEmpty() && ring >= 100f) {
                rv.setViewVisibility(ids.ringUnit, View.VISIBLE)
                rv.setTextViewText(ids.ringUnit, v.unit)
                rv.setColorStateList(ids.ringUnit, "setTextColor", c.on)
            } else {
                rv.setViewVisibility(ids.ringUnit, View.GONE)
            }
        } else {
            val minDim = minOf(innerW, innerH)
            rv.setViewVisibility(ids.ring, View.GONE)
            rv.setViewVisibility(ids.icon, View.VISIBLE)
            rv.setViewVisibility(ids.value, View.VISIBLE)

            val iconSize = (minDim * 0.30f).coerceIn(18f, 34f)
            rv.setImageViewResource(ids.icon, v.type.icon)
            rv.setColorStateList(ids.icon, "setImageTintList", c.accent)
            rv.setViewLayoutWidth(ids.icon, iconSize, TypedValue.COMPLEX_UNIT_DIP)
            rv.setViewLayoutHeight(ids.icon, iconSize, TypedValue.COMPLEX_UNIT_DIP)

            val text = if (!v.isText && v.available && v.unit.isNotEmpty() && v.unit.length <= 4) "${v.compact} ${v.unit}" else v.compact
            val size = if (v.isText || !v.available) (innerW * 0.14f).coerceIn(12f, 20f) else (minDim * 0.30f).coerceIn(16f, 32f)
            rv.setTextViewText(ids.value, text)
            rv.setTextViewTextSize(ids.value, TypedValue.COMPLEX_UNIT_SP, size)
            rv.setColorStateList(ids.value, "setTextColor", c.on)
        }
    }
}
