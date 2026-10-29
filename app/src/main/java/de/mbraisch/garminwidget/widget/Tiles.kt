package de.mbraisch.garminwidget.widget

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import de.mbraisch.garminwidget.data.Accent
import de.mbraisch.garminwidget.data.MetricValue
import de.mbraisch.garminwidget.data.Tone
import de.mbraisch.garminwidget.ui.MainActivity

/** Material-You-Farbrollen einer Kachel. */
private class TileColors(
    val container: ColorProvider,
    val onContainer: ColorProvider,
    val accent: ColorProvider,
    val onAccent: ColorProvider,
)

@Composable
private fun colorsFor(value: MetricValue): TileColors {
    val c = GlanceTheme.colors
    if (!value.available) {
        return TileColors(c.surfaceVariant, c.onSurfaceVariant, c.outline, c.surface)
    }
    if (value.tone == Tone.BAD) {
        return TileColors(c.errorContainer, c.onErrorContainer, c.error, c.onError)
    }
    return when (value.type.accent) {
        Accent.PRIMARY -> TileColors(c.primaryContainer, c.onPrimaryContainer, c.primary, c.onPrimary)
        Accent.SECONDARY -> TileColors(c.secondaryContainer, c.onSecondaryContainer, c.secondary, c.onSecondary)
        Accent.TERTIARY -> TileColors(c.tertiaryContainer, c.onTertiaryContainer, c.tertiary, c.onTertiary)
    }
}

/** Welche Darstellung in eine Kachel dieser Größe passt. */
private enum class TileStyle {
    /** sehr klein (1×1, 4×1-Spalten): Symbol + Wert */
    TINY,
    /** flach & breit: Symbol links, Texte rechts */
    COMPACT_ROW,
    /** flach & schmal: Kopfzeile + Wert */
    COMPACT_COLUMN,
    /** groß & breit: Ring links, Texte rechts */
    WIDE,
    /** groß & hoch: Kopfzeile, Ring in der Mitte, Text unten */
    TALL,
}

private fun styleFor(w: Dp, h: Dp): TileStyle = when {
    w < 92.dp || h < 66.dp -> TileStyle.TINY
    h < 112.dp -> if (w >= 150.dp) TileStyle.COMPACT_ROW else TileStyle.COMPACT_COLUMN
    w >= h * 1.45f -> TileStyle.WIDE
    else -> TileStyle.TALL
}

/**
 * Eine Kachel mit einem Wert. [width]/[height] sind die tatsächlichen Maße in dp,
 * daraus ergibt sich die Darstellung. [outer] = die Kachel füllt allein das ganze Widget.
 */
@Composable
fun TileView(
    value: MetricValue,
    width: Dp,
    height: Dp,
    modifier: GlanceModifier,
    outer: Boolean = false,
) {
    val colors = colorsFor(value)
    var shaped = modifier.background(colors.container)
    shaped = if (outer) {
        val withBg = shaped.appWidgetBackground()
        if (Build.VERSION.SDK_INT >= 31) withBg.cornerRadius(android.R.dimen.system_app_widget_background_radius)
        else withBg.cornerRadius(24.dp)
    } else {
        if (Build.VERSION.SDK_INT >= 31) shaped.cornerRadius(android.R.dimen.system_app_widget_inner_radius)
        else shaped.cornerRadius(16.dp)
    }
    val pad = if (min(width, height) < 80.dp) 6.dp else 10.dp
    val innerW = width - pad * 2
    val innerH = height - pad * 2

    Box(
        modifier = shaped
            .padding(pad)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center,
    ) {
        when (styleFor(width, height)) {
            TileStyle.TINY -> TinyTile(value, colors, innerW, innerH)
            TileStyle.COMPACT_ROW -> CompactRowTile(value, colors, innerW, innerH)
            TileStyle.COMPACT_COLUMN -> CompactColumnTile(value, colors, innerH)
            TileStyle.WIDE -> WideTile(value, colors, innerW, innerH)
            TileStyle.TALL -> TallTile(value, colors, innerW, innerH)
        }
    }
}

// ---------------------------------------------------------------------------
// Darstellungen
// ---------------------------------------------------------------------------

@Composable
private fun TinyTile(v: MetricValue, colors: TileColors, w: Dp, h: Dp) {
    val showLabel = h >= 62.dp
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        WidgetIcon(v.type.icon, if (h >= 56.dp) 20.dp else 16.dp, colors.accent)
        Spacer(GlanceModifier.height(2.dp))
        if (v.isText || !v.available) {
            WText(v.value, if (w < 70.dp) 11f else 12f, colors.onContainer, bold = true, maxLines = 2, center = true)
        } else {
            WText(v.value, valueSize(h, max = 24f), colors.onContainer, bold = true, maxLines = 1, center = true)
        }
        if (showLabel) {
            WText(v.type.shortLabel, 10f, colors.onContainer, maxLines = 1, center = true)
        }
    }
}

@Composable
private fun CompactRowTile(v: MetricValue, colors: TileColors, w: Dp, h: Dp) {
    val ring = v.progress != null && v.available && w >= 210.dp
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        IconBubble(v.type.icon, if (h >= 70.dp) 34.dp else 28.dp, colors)
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            WText(v.type.label, 12f, colors.onContainer, maxLines = 1)
            ValueLine(v, colors, if (v.isText) 15f else valueSize(h, max = 24f))
            if (h >= 72.dp && v.subtitle.isNotEmpty()) {
                WText(v.subtitle, 11f, colors.onContainer, maxLines = 1)
            }
        }
        if (ring) {
            Spacer(GlanceModifier.width(8.dp))
            Ring(v, colors, min(h, 56.dp), showValue = false)
        }
    }
}

@Composable
private fun CompactColumnTile(v: MetricValue, colors: TileColors, h: Dp) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            WidgetIcon(v.type.icon, 16.dp, colors.accent)
            Spacer(GlanceModifier.width(4.dp))
            WText(v.type.shortLabel, 11f, colors.onContainer, maxLines = 1)
        }
        Spacer(GlanceModifier.height(2.dp))
        ValueLine(v, colors, if (v.isText) 14f else valueSize(h, max = 26f))
        if (h >= 80.dp && v.subtitle.isNotEmpty()) {
            WText(v.subtitle, 10f, colors.onContainer, maxLines = 1)
        }
    }
}

@Composable
private fun WideTile(v: MetricValue, colors: TileColors, w: Dp, h: Dp) {
    val ringSize = min(h, 104.dp)
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        if (v.progress != null && v.available) {
            Ring(v, colors, ringSize, showValue = true)
        } else {
            IconBubble(v.type.icon, min(ringSize, 48.dp), colors)
        }
        Spacer(GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                if (v.progress != null && v.available) {
                    WidgetIcon(v.type.icon, 14.dp, colors.accent)
                    Spacer(GlanceModifier.width(4.dp))
                }
                WText(v.type.label, 13f, colors.onContainer, bold = true, maxLines = 1)
            }
            if (v.progress == null || !v.available) {
                ValueLine(v, colors, if (v.isText) 18f else 26f)
            }
            if (v.subtitle.isNotEmpty()) {
                WText(v.subtitle, 12f, colors.onContainer, maxLines = 2)
            }
            if (h >= 120.dp && w >= 230.dp && v.detail.isNotEmpty()) {
                Spacer(GlanceModifier.height(2.dp))
                WText(v.detail, 11f, colors.onContainer, maxLines = if (h >= 150.dp) 3 else 2)
            }
        }
    }
}

@Composable
private fun TallTile(v: MetricValue, colors: TileColors, w: Dp, h: Dp) {
    val hasRing = v.progress != null && v.available
    val showDetail = h >= 200.dp && w >= 140.dp && v.detail.isNotEmpty()
    // Platz für Kopfzeile (~20 dp) und Untertitel (~18 dp) abziehen
    val reserved = 44.dp + if (showDetail) 34.dp else 0.dp
    val ringSize = min(w, h - reserved).coerceAtLeast(48.dp).coerceAtMost(132.dp)

    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            WidgetIcon(v.type.icon, 16.dp, colors.accent)
            Spacer(GlanceModifier.width(4.dp))
            WText(v.type.label, 12f, colors.onContainer, bold = true, maxLines = 1)
        }
        Box(
            modifier = GlanceModifier.defaultWeight(),
            contentAlignment = Alignment.Center,
        ) {
            if (hasRing) {
                Ring(v, colors, ringSize, showValue = true)
            } else {
                Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                    IconBubble(v.type.icon, min(ringSize * 0.42f, 52.dp), colors)
                    Spacer(GlanceModifier.height(6.dp))
                    WText(
                        v.value,
                        if (v.isText) textValueSize(w) else valueSize(h, max = 32f),
                        colors.onContainer,
                        bold = true,
                        maxLines = 2,
                        center = true,
                    )
                }
            }
        }
        if (v.subtitle.isNotEmpty()) {
            WText(v.subtitle, 12f, colors.onContainer, maxLines = 1, center = true)
        }
        if (showDetail) {
            WText(v.detail, 10f, colors.onContainer, maxLines = 2, center = true)
        }
    }
}

// ---------------------------------------------------------------------------
// Bausteine
// ---------------------------------------------------------------------------

/** Ringanzeige: Hintergrundring + Fortschritt, optional mit Wert in der Mitte. */
@Composable
private fun Ring(v: MetricValue, colors: TileColors, size: Dp, showValue: Boolean) {
    val progressRes = RingDrawables.forProgress(v.progress ?: 0f)
    Box(modifier = GlanceModifier.size(size), contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(de.mbraisch.garminwidget.R.drawable.ring_track),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(colors.onContainer),
        )
        if (progressRes != null) {
            Image(
                provider = ImageProvider(progressRes),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(colors.accent),
            )
        }
        if (showValue) {
            Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                WText(
                    v.value,
                    (size.value * 0.30f).coerceIn(14f, 34f),
                    colors.onContainer,
                    bold = true,
                    maxLines = 1,
                    center = true,
                )
                if (v.unit.isNotEmpty() && size >= 64.dp) {
                    WText(v.unit, 10f, colors.onContainer, maxLines = 1, center = true)
                }
            }
        } else {
            WidgetIcon(v.type.icon, size * 0.36f, colors.accent)
        }
    }
}

/** Großer Wert mit kleiner Einheit dahinter. */
@Composable
private fun ValueLine(v: MetricValue, colors: TileColors, sizeSp: Float) {
    if (v.isText || !v.available || v.unit.isEmpty()) {
        WText(v.value, sizeSp, colors.onContainer, bold = true, maxLines = if (v.isText) 2 else 1)
        return
    }
    Row(verticalAlignment = Alignment.Vertical.Bottom) {
        WText(v.value, sizeSp, colors.onContainer, bold = true, maxLines = 1)
        Spacer(GlanceModifier.width(3.dp))
        WText(v.unit, 11f, colors.onContainer, maxLines = 1, modifier = GlanceModifier.padding(bottom = 3.dp))
    }
}

/** Symbol in einem farbigen Kreis (wie die Kennzahlen in der Garmin-App). */
@Composable
private fun IconBubble(@DrawableRes icon: Int, size: Dp, colors: TileColors) {
    Box(
        modifier = GlanceModifier
            .size(size)
            .cornerRadius(size / 2)
            .background(colors.accent),
        contentAlignment = Alignment.Center,
    ) {
        WidgetIcon(icon, size * 0.56f, colors.onAccent)
    }
}

@Composable
fun WidgetIcon(@DrawableRes icon: Int, size: Dp, tint: ColorProvider) {
    Image(
        provider = ImageProvider(icon),
        contentDescription = null,
        modifier = GlanceModifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

@Composable
fun WText(
    text: String,
    sizeSp: Float,
    color: ColorProvider,
    bold: Boolean = false,
    maxLines: Int = 1,
    center: Boolean = false,
    modifier: GlanceModifier = GlanceModifier,
) {
    Text(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        style = TextStyle(
            color = color,
            fontSize = sizeSp.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = if (center) TextAlign.Center else TextAlign.Start,
        ),
    )
}

/** Schriftgröße für Zahlenwerte abhängig von der verfügbaren Höhe. */
private fun valueSize(h: Dp, max: Float): Float = (h.value * 0.30f).coerceIn(16f, max)

/** Schriftgröße für Text-Werte (z. B. "Produktiv") abhängig von der Breite. */
private fun textValueSize(w: Dp): Float = (w.value * 0.11f).coerceIn(13f, 20f)
