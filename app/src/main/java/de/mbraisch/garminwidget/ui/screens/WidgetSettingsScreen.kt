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
                "Gilt für alle Garmin-Widgets auf deinem Startbildschirm.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            val manager = AppWidgetManager.getInstance(context)
            if (manager.isRequestPinAppWidgetSupported) {
                FilledTonalButton(
                    onClick = {
                        manager.requestPinAppWidget(ComponentName(context, GarminWidgetReceiver::class.java), null, null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(painterResource(R.drawable.ic_widgets), contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Widget zum Startbildschirm hinzufügen")
                }
            }
        }

        // ---------------- Darstellung ----------------
        item {
            SettingsCard {
                Text("Darstellung", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val modes = listOf(DisplayMode.GRID to "Mehrere Werte", DisplayMode.SWIPE to "Wischen")
                    modes.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = settings.mode == mode,
                            onClick = { vm.updateSettings { it.copy(mode = mode) } },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                        ) { Text(label) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (settings.mode == DisplayMode.GRID)
                        "Je nach Größe zeigt das Widget die ersten aktiven Kacheln gleichzeitig: 1×1 → 1 Wert, 2×1 und 1×2 → 2 Werte, 3×1 → 3 Werte, ab 2×2 und 4×1 → 4 Werte. Größere Widgets zeigen mehr Details."
                    else
                        "Das Widget zeigt 1 oder 2 Werte; die übrigen aktiven Kacheln erreichst du durch Wischen nach oben und unten im Widget.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (settings.mode == DisplayMode.SWIPE) {
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
                }
            }
        }

        // ---------------- Kacheln ----------------
        item {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text("Kacheln", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Aktivieren und mit den Pfeilen sortieren. Oben stehende Kacheln erscheinen im Widget zuerst.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(settings.allInOrder, key = { it.id }) { type ->
            TileRow(type, settings, vm)
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
                    "So oft holt die App im Hintergrund neue Werte. Über den Pfeil im Widget (ab 3×3) oder in der App geht es jederzeit sofort.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TileRow(type: MetricType, settings: WidgetSettings, vm: MainViewModel) {
    val active = type in settings.tiles
    val index = settings.tiles.indexOf(type)
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active) scheme.surfaceContainerHigh else scheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
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
                        "Position ${index + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            if (active) {
                IconButton(
                    onClick = { vm.updateSettings { it.copy(tiles = it.tiles.move(type, -1)) } },
                    enabled = index > 0,
                ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Nach oben") }
                IconButton(
                    onClick = { vm.updateSettings { it.copy(tiles = it.tiles.move(type, +1)) } },
                    enabled = index < settings.tiles.size - 1,
                ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Nach unten") }
            }
            Switch(
                checked = active,
                onCheckedChange = { on ->
                    vm.updateSettings {
                        it.copy(tiles = if (on) (it.tiles - type) + type else it.tiles - type)
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
