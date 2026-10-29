package de.mbraisch.garminwidget.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mbraisch.garminwidget.R
import de.mbraisch.garminwidget.ui.MainViewModel

@Composable
fun AccountScreen(vm: MainViewModel, onLogin: () -> Unit, modifier: Modifier = Modifier) {
    val loggedIn by vm.loggedIn.collectAsStateWithLifecycle()
    val displayName by vm.displayName.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Konto", style = MaterialTheme.typography.headlineLarge) }

        item {
            if (loggedIn) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBubble(R.drawable.ic_person, scheme.primary, scheme.onPrimary, size = 48.dp)
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    displayName ?: "Garmin-Konto",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "Mit Garmin Connect verbunden",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = vm::refresh, enabled = !syncing) {
                                Icon(painterResource(R.drawable.ic_sync), contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (syncing) "Lädt…" else "Synchronisieren")
                            }
                            OutlinedButton(onClick = { confirmLogout = true }) {
                                Icon(painterResource(R.drawable.ic_logout), contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Abmelden")
                            }
                        }
                    }
                }
            } else {
                LoginPromptCard(onLogin, expired = snapshot.fetchedAt > 0)
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Systemfarben (Material You)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (settings.dynamicColor) "App-Farben passen sich deinem Hintergrundbild an"
                            else "Garmin-Blau, unabhängig vom Hintergrundbild",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.dynamicColor,
                        onCheckedChange = { on -> vm.updateSettings { it.copy(dynamicColor = on) } },
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("So funktioniert's", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Die App meldet sich wie die Garmin-Connect-App bei Garmin an und liest deine Werte direkt vom Handy aus – ohne eigenen Server. Gespeichert wird nur ein verschlüsseltes Zugriffstoken, nicht dein Passwort.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        "Garmin bietet für diese Werte keine offizielle Schnittstelle für Privatpersonen an. Ändert Garmin etwas, können einzelne Werte vorübergehend fehlen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        "Der Trainingsvorschlag kommt aus deinem Garmin-Kalender, falls dort für heute ein Lauf geplant ist. Sonst berechnet ihn die App aus Erholung, Body Battery, Schlaf, HFV und Belastung.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (loggedIn) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Fehlersuche", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Kopiert die Rohdaten von Garmin (gekürzt) in die Zwischenablage, z. B. um herauszufinden, warum ein Wert fehlt. Enthält deine Gesundheits- und Trainingsdaten.",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = vm::copyDiagnostics) { Text("Rohdaten kopieren") }
                    }
                }
            }
        }

        item {
            Text(
                "Garmin Widget 1.8 · Symbole: Material Symbols (Apache 2.0)",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Abmelden?") },
            text = { Text("Das Widget zeigt danach keine Werte mehr, bis du dich erneut anmeldest.") },
            confirmButton = {
                TextButton(onClick = { confirmLogout = false; vm.logout() }) { Text("Abmelden") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("Abbrechen") }
            },
        )
    }
}
