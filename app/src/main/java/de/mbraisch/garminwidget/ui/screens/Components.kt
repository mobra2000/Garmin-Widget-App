package de.mbraisch.garminwidget.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.mbraisch.garminwidget.data.Accent
import de.mbraisch.garminwidget.data.MetricValue
import de.mbraisch.garminwidget.data.Tone

/** Farbrollen einer Kennzahl – identisch zur Logik im Widget. */
data class MetricColors(val container: Color, val onContainer: Color, val accent: Color, val onAccent: Color)

fun ColorScheme.colorsFor(v: MetricValue): MetricColors = when {
    !v.available -> MetricColors(surfaceContainerHigh, onSurfaceVariant, outline, surface)
    v.tone == Tone.BAD -> MetricColors(errorContainer, onErrorContainer, error, onError)
    v.type.accent == Accent.PRIMARY -> MetricColors(primaryContainer, onPrimaryContainer, primary, onPrimary)
    v.type.accent == Accent.SECONDARY -> MetricColors(secondaryContainer, onSecondaryContainer, secondary, onSecondary)
    else -> MetricColors(tertiaryContainer, onTertiaryContainer, tertiary, onTertiary)
}

@Composable
fun IconBubble(@DrawableRes icon: Int, background: Color, tint: Color, size: Dp = 36.dp) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.56f))
    }
}

/** Ringanzeige wie im Widget, mit Inhalt in der Mitte. */
@Composable
fun RingGauge(
    progress: Float,
    color: Color,
    trackColor: Color,
    size: Dp,
    strokeWidth: Dp = size * 0.11f,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            if (progress > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

/**
 * Karte für eine Kennzahl im Stil der Garmin-App ("Mein Tag"):
 * farbiges Symbol, Bezeichnung, großer Wert, Ring und Erläuterung.
 */
@Composable
fun MetricCard(v: MetricValue, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme.colorsFor(v)
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = c.container, contentColor = c.onContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(v.type.icon, c.accent, c.onAccent, size = 30.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    v.type.label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
            val progress = v.progress
            if (progress != null && v.available) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    RingGauge(progress, c.accent, c.onContainer.copy(alpha = 0.18f), size = 104.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(v.value, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            if (v.unit.isNotEmpty()) {
                                Text(v.unit, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        v.value,
                        fontSize = if (v.isText || !v.available) 22.sp else 34.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 26.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (v.unit.isNotEmpty() && v.available) {
                        Spacer(Modifier.width(4.dp))
                        Text(v.unit, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 5.dp))
                    }
                }
            }
            if (v.subtitle.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(v.subtitle, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (v.detail.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    v.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.onContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

/** Große, hervorgehobene Karte für den Trainingsvorschlag (wie "Vorgeschlagenes Training" bei Garmin). */
@Composable
fun SuggestionCard(v: MetricValue, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val c = scheme.colorsFor(v)
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = c.container, contentColor = c.onContainer),
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(v.type.icon, c.accent, c.onAccent, size = 56.dp)
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Trainingsvorschlag · Laufen", style = MaterialTheme.typography.labelLarge)
                Text(v.value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (v.subtitle.isNotEmpty()) {
                    Text(v.subtitle, style = MaterialTheme.typography.titleSmall)
                }
                if (v.detail.isNotEmpty()) {
                    Text(
                        v.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.onContainer.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}
