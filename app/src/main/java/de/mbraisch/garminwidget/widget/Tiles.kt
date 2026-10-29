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
import de.mbraisch.garminwidget.data.MetricType
import de.mbraisch.garminwidget.data.MetricValue
import de.mbraisch.garminwidget.data.Tone
import de.mbraisch.garminwidget.ui.OpenFromWidgetActivity

/** Material-You-Farbrollen einer Kachel. */
private class TileColors(
    val container: ColorProvider,
    val onContainer: ColorProvider,
    val accent: ColorProvider,
)

@Composable
private fun colorsFor(value: MetricValue): TileColors {
    val c = GlanceTheme.colors
    if (!value.available) return TileColors(c.surfaceVariant, c.onSurfaceVariant, c.onSurfaceVariant)
    if (value.tone == Tone.BAD) return TileColors(c.errorContainer, c.onErrorContainer, c.error)
    return when (value.type.accent) {
        Accent.PRIMARY -> TileColors(c.primaryContainer, c.onPrimaryContainer, c.primary)
        Accent.SECONDARY -> TileColors(c.secondaryContainer, c.onSecondaryContainer, c.secondary)
        Accent.TERTIARY -> TileColors(c.tertiaryContainer, c.onTertiaryContainer, c.tertiary)
    }
}

/**
 * Eine Kachel: nur Symbol und Wert, bei ausreichend Platz als Ring.
 * [detailed] (große Widgets) ergänzt eine kurze Zusatzzeile.
 * [outer] = die Kachel füllt allein das ganze Widget.
 */
@Composable
fun TileView(
    value: MetricValue,
    width: Dp,
    height: Dp,
    modifier: GlanceModifier,
    outer: Boolean = false,
    detailed: Boolean = false,
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
    val pad = if (min(width, height) < 90.dp) 6.dp else 10.dp
    val innerW = width - pad * 2
    val innerH = height - pad * 2

    // Beim Trainingsvorschlag gehören Dauer und Pace zum Wert ("40 min · 6:20/km") und stehen immer da
    val isSuggestion = value.type == MetricType.SUGGESTION
    val showSub = (detailed || isSuggestion) && value.available && value.subtitle.isNotEmpty() &&
        innerH >= (if (isSuggestion) 56.dp else 110.dp)
    val ringH = innerH - if (showSub) 18.dp else 0.dp
    val hasRing = value.available && value.progress != null && min(innerW, ringH) >= 58.dp
    val wide = innerW >= innerH * 1.7f

    Box(
        modifier = shaped
            .padding(pad)
            .clickable(actionStartActivity<OpenFromWidgetActivity>()),
        contentAlignment = Alignment.Center,
    ) {
        when {
            wide -> WideTile(value, colors, innerW, innerH, showSub)
            hasRing -> RingTile(value, colors, innerW, ringH, showSub)
            else -> PlainTile(value, colors, innerW, innerH, showSub)
        }
    }
}

// ---------------------------------------------------------------------------
// Darstellungen
// ---------------------------------------------------------------------------

/** Ring mit Symbol und Wert in der Mitte. */
@Composable
private fun RingTile(v: MetricValue, colors: TileColors, w: Dp, h: Dp, showSub: Boolean) {
    val ringSize = min(w, h).coerceAtMost(150.dp)
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Ring(v, colors, ringSize)
        if (showSub) {
            Spacer(GlanceModifier.height(4.dp))
            WText(v.subtitle, if (v.type == MetricType.SUGGESTION) 13f else 11f, colors.onContainer, maxLines = 1, center = true)
        }
    }
}

/** Symbol über dem Wert (für Kacheln ohne Ring oder mit wenig Platz). */
@Composable
private fun PlainTile(v: MetricValue, colors: TileColors, w: Dp, h: Dp, showSub: Boolean) {
    val minDim = min(w, h)
    val iconSize = (minDim * 0.30f).coerceIn(18.dp, 34.dp)
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        WidgetIcon(v.type.icon, iconSize, colors.accent)
        Spacer(GlanceModifier.height(if (minDim < 70.dp) 2.dp else 6.dp))
        ValueLine(v, colors, w, minDim, center = true)
        if (showSub) {
            Spacer(GlanceModifier.height(2.dp))
            WText(v.subtitle, if (v.type == MetricType.SUGGESTION) 13f else 11f, colors.onContainer, maxLines = 1, center = true)
        }
    }
}

/** Flache, breite Kachel: Symbol links, Wert rechts. */
@Composable
private fun WideTile(v: MetricValue, colors: TileColors, w: Dp, h: Dp, showSub: Boolean) {
    val iconSize = (h * 0.42f).coerceIn(18.dp, 32.dp)
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        if (v.available && v.progress != null && h >= 48.dp) {
            MiniRing(v, colors, min(h, 56.dp))
        } else {
            WidgetIcon(v.type.icon, iconSize, colors.accent)
        }
        Spacer(GlanceModifier.width(10.dp))
        Column(verticalAlignment = Alignment.Vertical.CenterVertically) {
            ValueLine(v, colors, w * 0.6f, h, center = false)
            if (showSub) WText(v.subtitle, if (v.type == MetricType.SUGGESTION) 13f else 11f, colors.onContainer, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------------------
// Bausteine
// ---------------------------------------------------------------------------

@Composable
private fun RingImages(v: MetricValue, colors: TileColors) {
    Image(
        provider = ImageProvider(de.mbraisch.garminwidget.R.drawable.ring_track),
        contentDescription = null,
        modifier = GlanceModifier.fillMaxSize(),
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(colors.onContainer),
    )
    RingDrawables.forProgress(v.progress ?: 0f)?.let { res ->
        Image(
            provider = ImageProvider(res),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(colors.accent),
        )
    }
}

/** Ring mit kleinem Symbol und Wert in der Mitte. */
@Composable
private fun Ring(v: MetricValue, colors: TileColors, size: Dp) {
    Box(modifier = GlanceModifier.size(size), contentAlignment = Alignment.Center) {
        RingImages(v, colors)
        Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
            WidgetIcon(v.type.icon, (size * 0.18f).coerceIn(12.dp, 24.dp), colors.accent)
            WText(
                v.compact,
                (size.value * 0.26f).coerceIn(14f, 36f),
                colors.onContainer,
                bold = true,
                maxLines = 1,
                center = true,
            )
            if (v.unit.isNotEmpty() && size >= 100.dp) {
                WText(v.unit, 10f, colors.onContainer, maxLines = 1, center = true)
            }
        }
    }
}

/** Kleiner Ring nur mit Symbol (für flache Kacheln). */
@Composable
private fun MiniRing(v: MetricValue, colors: TileColors, size: Dp) {
    Box(modifier = GlanceModifier.size(size), contentAlignment = Alignment.Center) {
        RingImages(v, colors)
        WidgetIcon(v.type.icon, size * 0.4f, colors.accent)
    }
}

/** Wert mit kleiner Einheit dahinter. */
@Composable
private fun ValueLine(v: MetricValue, colors: TileColors, w: Dp, h: Dp, center: Boolean) {
    if (v.isText || !v.available) {
        val size = (w.value * 0.15f).coerceIn(12f, 20f)
        WText(v.compact, size, colors.onContainer, bold = true, maxLines = 1, center = center)
        return
    }
    val size = (h.value * 0.32f).coerceIn(16f, 34f)
    Row(verticalAlignment = Alignment.Vertical.Bottom) {
        WText(v.compact, size, colors.onContainer, bold = true, maxLines = 1)
        if (v.unit.isNotEmpty() && v.unit.length <= 4 && w >= 70.dp) {
            Spacer(GlanceModifier.width(2.dp))
            WText(v.unit, 10f, colors.onContainer, maxLines = 1, modifier = GlanceModifier.padding(bottom = 3.dp))
        }
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
