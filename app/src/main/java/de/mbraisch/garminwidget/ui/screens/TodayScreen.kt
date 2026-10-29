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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mbraisch.garminwidget.R
import de.mbraisch.garminwidget.data.MetricType
import de.mbraisch.garminwidget.ui.MainViewModel
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private val HEALTH = listOf(MetricType.BODY_BATTERY, MetricType.SLEEP, MetricType.HRV, MetricType.CALORIES)
private val TRAINING = listOf(MetricType.TRAINING_STATUS, MetricType.LOAD, MetricType.RECOVERY, MetricType.VO2MAX)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(vm: MainViewModel, onLogin: () -> Unit, modifier: Modifier = Modifier) {
    val loggedIn by vm.loggedIn.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()

    PullToRefreshBox(
        isRefreshing = syncing,
        onRefresh = { if (loggedIn) vm.refresh() },
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Header(
                    updatedAt = snapshot.fetchedAt,
                    syncing = syncing,
                    loggedIn = loggedIn,
                    onRefresh = vm::refresh,
                )
            }

            if (!loggedIn) {
                item { LoginPromptCard(onLogin, expired = snapshot.fetchedAt > 0) }
            }

            snapshot.error?.let { error ->
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Text(error, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (snapshot.fetchedAt > 0) {
                item { SuggestionCard(snapshot[MetricType.SUGGESTION], Modifier.fillMaxWidth()) }
                item { SectionTitle("Gesundheit") }
                HEALTH.chunked(2).forEach { row ->
                    item { MetricRow(row.map { snapshot[it] }) }
                }
                item { SectionTitle("Training") }
                TRAINING.chunked(2).forEach { row ->
                    item { MetricRow(row.map { snapshot[it] }) }
                }
                item {
                    Text(
                        "Werte aus Garmin Connect – so aktuell wie die letzte Synchronisation deiner Uhr.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricRow(values: List<de.mbraisch.garminwidget.data.MetricValue>) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        values.forEach { MetricCard(it, Modifier.weight(1f)) }
        if (values.size == 1) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun Header(updatedAt: Long, syncing: Boolean, loggedIn: Boolean, onRefresh: () -> Unit) {
    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMANY))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Mein Tag", style = MaterialTheme.typography.headlineLarge)
            Text(
                text = buildString {
                    append(date)
                    when {
                        syncing -> append(" · aktualisiere…")
                        updatedAt > 0 -> append(" · Stand ").append(
                            SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(updatedAt))
                        )
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (loggedIn) {
            IconButton(onClick = onRefresh, enabled = !syncing) {
                Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Aktualisieren")
            }
        }
    }
}

@Composable
fun LoginPromptCard(onLogin: () -> Unit, expired: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(R.drawable.ic_suggestion, scheme.primary, scheme.onPrimary, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    if (expired) "Erneut anmelden" else "Mit Garmin Connect verbinden",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (expired) "Deine Anmeldung ist abgelaufen. Melde dich erneut an, damit App und Widget wieder aktuelle Werte zeigen."
                else "Melde dich mit deinem Garmin-Konto an. Die Anmeldung läuft direkt über die Garmin-Seite – dein Passwort wird von dieser App weder ausgelesen noch gespeichert.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onLogin) { Text("Bei Garmin anmelden") }
        }
    }
}
