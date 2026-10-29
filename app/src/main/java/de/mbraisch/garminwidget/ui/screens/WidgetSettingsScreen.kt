package de.mbraisch.garminwidget.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mbraisch.garminwidget.R
import de.mbraisch.garminwidget.data.DisplayMode
import de.mbraisch.garminwidget.data.MetricType
import de.mbraisch.garminwidget.data.WidgetSettings
import de.mbraisch.garminwidget.ui.MainViewModel
import de.mbraisch.garminwidget.widget.GarminWidgetReceiver
import de.mbraisch.garminwidget.widget.SwipeWidgetProvider

private val INTERVALS = listOf(30 to "30 min", 60 to "1 h", 120 to "2 h", 240 to "4 h")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Widget", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Es gibt zwei Widgets. Die Einstellungen gelten für alle Garmin-Widgets auf deinem Startbildschirm.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---------------- Die zwei Widgets ----------------
        item {
            val manager = AppWidgetManager.getInstance(context)
            SettingsCard {
                Text("Garmin Raster", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val modes = listOf(DisplayMode.GRID to "Mehrere Werte", DisplayMode.SWIPE to "Durchscrollen")
                    modes.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = settings.mode == mode,
                            onClick = { vm.updateSettings { it.copy(mode = mode) } },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                        ) { Text(label) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (settings.mode == DisplayMode.GRID)
                        "1×1 → 1 Wert, 2×1 und 1×2 → 2, 3×1 → 3, ab 2×2 → 4 Werte gleichzeitig. Große Widgets (ab 3×4) zeigen kleine Zusatzinfos."
                    else
                        "1 oder 2 Werte pro Seite (siehe unten); die übrigen erreichst du durch Scrollen im Widget.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (manager.isRequestPinAppWidgetSupported) {
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = {
                            manager.requestPinAppWidget(ComponentName(context, GarminWidgetReceiver::class.java), null, null)
                        },
                    ) {
                        Icon(painterResource(R.drawable.ic_widgets), contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Hinzufügen")
                    }
                }
            }
        }

        item {
            val manager = AppWidgetManager.getInstance(context)
            SettingsCard {
                Text("Garmin Wischen (Test)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Eigenes Widget als Kartenstapel: Jeder Wisch nach oben oder unten blättert genau eine Seite weiter. Die Einstellung „Werte pro Seite“ gilt auch für das Durchscrollen im Raster-Widget.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text("Werte pro Seite", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf(1, 2).forEachIndexed { index, n ->
                        SegmentedButton(
                            selected = settings.perPage == n,
                            onClick = { vm.updateSettings { it.copy(perPage = n) } },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                        ) { Text(if (n == 1) "1 Wert" else "2 Werte") }
                    }
                }
                if (manager.isRequestPinAppWidgetSupported) {
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = {
                            manager.requestPinAppWidget(ComponentName(context, SwipeWidgetProvider::class.java), null, null)
                        },
                    ) {
                        Icon(painterResource(R.drawable.ic_widgets), contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Hinzufügen")
                    }
                }
            }
        }

        // ---------------- Tippen ----------------
        item {
            SettingsCard {
                Text("Tippen auf das Widget öffnet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf(true to "Garmin Connect", false to "Diese App").forEachIndexed { index, (garmin, label) ->
                        SegmentedButton(
                            selected = settings.tapOpensGarmin == garmin,
                            onClick = { vm.updateSettings { it.copy(tapOpensGarmin = garmin) } },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                        ) { Text(label) }
                    }
                }
            }
        }

        // ---------------- Kacheln ----------------
        item {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text("Kacheln", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Einschalten und mit den Pfeilen sortieren. Oben stehende Kacheln erscheinen im Widget zuerst.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(settings.order, key = { it.id }) { type ->
            TileRow(type, settings, vm, Modifier.animateItem())
        }

        // ---------------- Aktualisierung ----------------
        item {
            SettingsCard {
                Text("Aktualisierung", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    INTERVALS.forEach { (minutes, label) ->
                        FilterChip(
                            selected = settings.intervalMinutes == minutes,
                            onClick = { vm.updateSettings { it.copy(intervalMinutes = minutes) } },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "So oft holt die App im Hintergrund neue Werte. Sofort aktualisieren: in der App nach unten ziehen oder beim großen Widget (4×4) auf den Pfeil tippen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TileRow(type: MetricType, settings: WidgetSettings, vm: MainViewModel, modifier: Modifier = Modifier) {
    val active = type in settings.enabled
    val index = settings.order.indexOf(type)
    val position = settings.tiles.indexOf(type)
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active) scheme.surfaceContainerHigh else scheme.surfaceContainerLow,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        ) {
            IconBubble(
                type.icon,
                if (active) scheme.primary else scheme.surfaceVariant,
                if (active) scheme.onPrimary else scheme.onSurfaceVariant,
                size = 34.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(type.label, style = MaterialTheme.typography.titleSmall)
                if (active) {
                    Text(
                        "Position ${position + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(
                onClick = { vm.updateSettings { it.copy(order = it.order.move(type, -1)) } },
                enabled = index > 0,
            ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Nach oben") }
            IconButton(
                onClick = { vm.updateSettings { it.copy(order = it.order.move(type, +1)) } },
                enabled = index < settings.order.size - 1,
            ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Nach unten") }
            Switch(
                checked = active,
                onCheckedChange = { on ->
                    vm.updateSettings {
                        it.copy(enabled = if (on) it.enabled + type else it.enabled - type)
                    }
                },
            )
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

private fun List<MetricType>.move(type: MetricType, delta: Int): List<MetricType> {
    val from = indexOf(type)
    val to = from + delta
    if (from < 0 || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
