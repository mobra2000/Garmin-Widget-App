package de.mbraisch.garminwidget.data

import android.content.Context
import de.mbraisch.garminwidget.auth.AuthExpiredException
import de.mbraisch.garminwidget.auth.GarminBlockedException
import de.mbraisch.garminwidget.auth.GarminClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Ergebnis eines einzelnen API-Aufrufs: Daten, "keine Daten" oder Fehler. */
private sealed interface Fetch<out T> {
    data class Ok<T>(val data: T?) : Fetch<T>
    data class Failed(val error: Throwable) : Fetch<Nothing>
}

private fun <T> Fetch<T>.data(): T? = (this as? Fetch.Ok<T>)?.data

class GarminRepository(context: Context) {

    private val appContext = context.applicationContext
    val client = GarminClient.get(appContext)
    val store = AppStore(appContext)

    /** Nach der Anmeldung im WebView: Ticket tauschen, Profil laden, erste Synchronisation. */
    suspend fun completeLogin(ticket: String) {
        client.exchangeTicket(ticket)
        val profile = client.getObject("/userprofile-service/socialProfile")
        val displayName = profile?.str("displayName")
            ?: throw IOException("Profil konnte nicht geladen werden")
        store.setLoggedIn(true, displayName)
        sync()
    }

    suspend fun logout() {
        client.logout()
        store.setLoggedIn(false)
    }

    /**
     * Lädt alle Werte und speichert den neuen Stand. Fehler einzelner Werte führen nicht
     * zum Abbruch – der jeweilige Wert wird dann als "nicht verfügbar" markiert.
     */
    suspend fun sync(): Snapshot {
        if (!client.isLoggedIn) throw AuthExpiredException("Nicht angemeldet")
        store.setSyncing(true)
        try {
            val snapshot = fetchSnapshot()
            store.saveSnapshot(snapshot)
            return snapshot
        } catch (e: AuthExpiredException) {
            client.logout()
            store.setLoggedIn(false, clearData = false)
            val old = store.currentSnapshot()
            store.saveSnapshot(old.copy(error = "Anmeldung abgelaufen – bitte in der App neu anmelden"))
            throw e
        } finally {
            store.setSyncing(false)
        }
    }

    private suspend fun fetchSnapshot(): Snapshot = coroutineScope {
        val today = LocalDate.now()
        val todayStr = today.toString()
        val displayName = store.currentDisplayName() ?: run {
            val name = client.getObject("/userprofile-service/socialProfile")?.str("displayName")
                ?: throw IOException("Profil konnte nicht geladen werden")
            store.setDisplayName(name)
            name
        }

        val summaryJob = async { fetch { client.getObject("/usersummary-service/usersummary/daily/$displayName", mapOf("calendarDate" to todayStr)) } }
        val sleepJob = async { fetch { client.getObject("/wellness-service/wellness/dailySleepData/$displayName", mapOf("date" to todayStr, "nonSleepBufferMinutes" to "60")) } }
        val statusJob = async { fetch { client.getObject("/metrics-service/metrics/trainingstatus/aggregated/$todayStr") } }
        val readinessJob = async { fetch { client.get("/metrics-service/metrics/trainingreadiness/$todayStr") } }
        val hrvJob = async { fetch { client.getObject("/hrv-service/hrv/$todayStr") } }
        val calendarJob = async { fetch { client.getObject("/calendar-service/year/${today.year}/month/${today.monthValue - 1}") } }
        val runsJob = async {
            fetch {
                client.getArray(
                    "/activitylist-service/activities/search/activities",
                    mapOf("start" to "0", "limit" to "20", "activityType" to "running"),
                )
            }
        }

        val summary = summaryJob.await()
        val sleep = sleepJob.await()
        val status = statusJob.await()
        val readiness = readinessJob.await()
        val hrv = hrvJob.await()
        val calendar = calendarJob.await()
        val runs = runsJob.await()

        // Anmeldung abgelaufen? Dann sofort melden statt lauter leerer Werte.
        listOf(summary, sleep, status, readiness, hrv, calendar)
            .filterIsInstance<Fetch.Failed>()
            .firstOrNull { it.error is AuthExpiredException }
            ?.let { throw it.error }

        // Fallbacks, falls Hauptquellen nichts liefern
        val vo2Fallback = if (status.data()?.obj("mostRecentVO2Max") == null) {
            fetch { client.get("/metrics-service/metrics/maxmet/latest/$todayStr") }
        } else null
        val bodyBatteryFallback = if (summary.data()?.num("bodyBatteryMostRecentValue") == null) {
            fetch { client.getArray("/wellness-service/wellness/bodyBattery/reports/daily", mapOf("startDate" to todayStr, "endDate" to todayStr)) }
        } else null

        val parsed = ParsedData(
            summary = summary,
            sleep = sleep,
            status = status,
            readiness = readiness,
            hrv = hrv,
            calendar = calendar,
            runs = runs,
            vo2Fallback = vo2Fallback,
            bodyBatteryFallback = bodyBatteryFallback,
            today = today,
        )

        val metrics = buildMap {
            put(MetricType.TRAINING_STATUS, parsed.trainingStatus())
            put(MetricType.VO2MAX, parsed.vo2max())
            put(MetricType.SLEEP, parsed.sleep())
            put(MetricType.RECOVERY, parsed.recovery())
            put(MetricType.BODY_BATTERY, parsed.bodyBattery())
            put(MetricType.LOAD, parsed.load())
            put(MetricType.HRV, parsed.hrv())
            put(MetricType.CALORIES, parsed.calories())
            put(MetricType.SUGGESTION, parsed.suggestion())
        }

        val failures = listOf(summary, sleep, status, readiness, hrv, calendar).filterIsInstance<Fetch.Failed>()
        val firstError = failures.firstOrNull()?.error?.message ?: "unbekannter Fehler"
        val previous = store.currentSnapshot()

        // Alles fehlgeschlagen: alte Werte behalten, nur den Fehler vermerken
        if (failures.size == 6) {
            return@coroutineScope previous.copy(error = "Aktualisierung fehlgeschlagen: $firstError")
        }

        // Einzelne Werte fehlgeschlagen: dort den letzten bekannten Wert behalten
        val merged = metrics.mapValues { (type, value) ->
            val old = previous.metrics[type]
            if (!value.available && value.subtitle == LOAD_FAILED && old != null && old.available) old else value
        }
        val error = when {
            failures.isEmpty() -> null
            else -> "Einige Werte konnten nicht aktualisiert werden: $firstError"
        }
        Snapshot(fetchedAt = System.currentTimeMillis(), metrics = merged, error = error)
    }

    /**
     * Diagnose: holt die Rohantworten der wichtigsten Endpunkte (gekürzt) als Text,
     * damit fehlende Werte gezielt gefunden werden können. Ganz oben steht eine
     * Liste aller Felder, deren Name "recover" oder "hrv" enthält.
     */
    suspend fun diagnostics(): String {
        val today = LocalDate.now().toString()
        val displayName = store.currentDisplayName().orEmpty()
        val sources = linkedMapOf<String, suspend () -> Any?>(
            "trainingreadiness" to suspend { client.get("/metrics-service/metrics/trainingreadiness/$today") },
            "trainingstatus" to suspend { client.get("/metrics-service/metrics/trainingstatus/aggregated/$today") },
            "hrv" to suspend { client.get("/hrv-service/hrv/$today") },
            "sleep" to suspend { client.get("/wellness-service/wellness/dailySleepData/$displayName", mapOf("date" to today, "nonSleepBufferMinutes" to "60")) },
            "usersummary" to suspend { client.get("/usersummary-service/usersummary/daily/$displayName", mapOf("calendarDate" to today)) },
            "activities" to suspend { client.get("/activitylist-service/activities/search/activities", mapOf("start" to "0", "limit" to "2")) },
        )
        val results = linkedMapOf<String, Any?>()
        for ((name, call) in sources) {
            results[name] = try {
                call() ?: "(keine Daten)"
            } catch (e: Exception) {
                "(Fehler: ${e.message})"
            }
        }
        // Details der letzten Aktivität (dort steht bei manchen Uhren die Erholungszeit)
        val lastActivityId = (results["activities"] as? JSONArray)?.optJSONObject(0)?.num("activityId")?.toLong()
        if (lastActivityId != null) {
            results["activity_detail"] = try {
                client.get("/activity-service/activity/$lastActivityId") ?: "(keine Daten)"
            } catch (e: Exception) {
                "(Fehler: ${e.message})"
            }
        }

        val hits = mutableListOf<String>()
        results.forEach { (name, data) -> collectHits(name, data, hits) }

        return buildString {
            append("Garmin-Widget Diagnose ").append(today).append('\n')
            append("== Gefundene Felder (recover/hrv) ==\n")
            if (hits.isEmpty()) append("(keine)\n") else hits.take(80).forEach { append(it).append('\n') }
            results.forEach { (name, data) ->
                append("\n== ").append(name).append(" ==\n")
                append(trim(data, 0).toString().take(6000)).append('\n')
            }
        }
    }

    private fun collectHits(path: String, data: Any?, out: MutableList<String>) {
        when (data) {
            is JSONObject -> data.keys().forEach { k ->
                val v = data.opt(k)
                val lower = k.lowercase()
                if ((lower.contains("recover") || lower.contains("hrv")) && v !is JSONObject && v !is JSONArray) {
                    out += "$path.$k = $v"
                }
                collectHits("$path.$k", v, out)
            }
            is JSONArray -> for (i in 0 until minOf(data.length(), 5)) collectHits("$path[$i]", data.opt(i), out)
        }
    }

    /** Kürzt lange Listen, damit der Text überschaubar bleibt. */
    private fun trim(data: Any?, depth: Int): Any? = when {
        depth > 6 -> "…"
        data is JSONObject -> JSONObject().also { o ->
            data.keys().forEach { k -> o.put(k, trim(data.opt(k), depth + 1)) }
        }
        data is JSONArray -> JSONArray().also { a ->
            for (i in 0 until minOf(data.length(), 3)) a.put(trim(data.opt(i), depth + 1))
            if (data.length() > 3) a.put("… (${data.length()} Einträge)")
        }
        else -> data
    }

    private suspend fun <T> fetch(block: suspend () -> T?): Fetch<T> = try {
        Fetch.Ok(block())
    } catch (e: IOException) {
        Fetch.Failed(e)
    } catch (e: org.json.JSONException) {
        Fetch.Failed(e)
    }
}

private const val NOT_SUPPORTED = "Für dein Gerät nicht verfügbar"
private const val LOAD_FAILED = "Konnte nicht geladen werden"
private const val NO_DATA_YET = "Noch keine Daten"

/** Wandelt die Garmin-Antworten in anzeigefertige Werte um. */
private class ParsedData(
    val summary: Fetch<JSONObject>,
    val sleep: Fetch<JSONObject>,
    val status: Fetch<JSONObject>,
    val readiness: Fetch<Any>,
    val hrv: Fetch<JSONObject>,
    val calendar: Fetch<JSONObject>,
    val runs: Fetch<JSONArray>,
    val vo2Fallback: Fetch<Any>?,
    val bodyBatteryFallback: Fetch<JSONArray>?,
    val today: LocalDate,
) {
    private fun reason(f: Fetch<*>?, missing: String = NOT_SUPPORTED) =
        if (f is Fetch.Failed) LOAD_FAILED else missing

    // ---------- Trainingsstatus & Belastung (gemeinsame Quelle) ----------

    private val statusEntry: JSONObject? = status.data()
        ?.obj("mostRecentTrainingStatus")
        ?.obj("latestTrainingStatusData")
        ?.primaryDeviceEntry()

    private val loadBalanceEntry: JSONObject? = status.data()
        ?.obj("mostRecentTrainingLoadBalance")
        ?.obj("metricsTrainingLoadBalanceDTOMap")
        ?.primaryDeviceEntry()

    /** z. B. "PRODUCTIVE" aus "PRODUCTIVE_3" */
    val statusKey: String? = statusEntry?.str("trainingStatusFeedbackPhrase")
        ?.replace(Regex("_\\d+$"), "")
        ?.let { if (statusEntry?.optBoolean("trainingPaused") == true) "PAUSED" else it }

    val loadFocusKey: String? = loadBalanceEntry?.str("trainingBalanceFeedbackPhrase")

    private val acute: JSONObject? = statusEntry?.obj("acuteTrainingLoadDTO")
    val acwrStatus: String? = acute?.str("acwrStatus")

    fun trainingStatus(): MetricValue {
        val type = MetricType.TRAINING_STATUS
        val key = statusKey ?: return MetricValue.unavailable(type, reason(status))
        val tone = when (key) {
            "PRODUCTIVE", "PEAKING" -> Tone.GOOD
            "OVERREACHING" -> Tone.BAD
            "UNPRODUCTIVE", "STRAINED", "DETRAINING" -> Tone.WARN
            else -> Tone.NEUTRAL
        }
        val focus = loadFocusLabel(loadFocusKey)
        return MetricValue(
            type = type,
            available = true,
            value = SuggestionEngine.statusLabel(key),
            subtitle = focus ?: statusEntry?.str("sinceDate")?.let { "seit ${germanDate(it)}" }.orEmpty(),
            detail = buildString {
                append(statusDescription(key))
                if (focus != null) append(" Belastungsfokus: $focus.")
            },
            tone = tone,
            isText = true,
        )
    }

    fun load(): MetricValue {
        val type = MetricType.LOAD
        val entry = statusEntry ?: return MetricValue.unavailable(type, reason(status))

        val acuteLoad = acute?.num("dailyTrainingLoadAcute")
        if (acuteLoad != null) {
            val max = acute?.num("maxTrainingLoadChronic")
            val min = acute?.num("minTrainingLoadChronic")
            val (label, tone) = when (acwrStatus) {
                "OPTIMAL" -> "Optimal" to Tone.GOOD
                "LOW" -> "Niedrig" to Tone.NEUTRAL
                "HIGH" -> "Hoch" to Tone.WARN
                "VERY_HIGH" -> "Sehr hoch" to Tone.BAD
                else -> "" to Tone.NEUTRAL
            }
            return MetricValue(
                type = type,
                available = true,
                value = formatInt(acuteLoad),
                subtitle = listOf("Akut", label).filter { it.isNotBlank() }.joinToString(" · "),
                detail = if (min != null && max != null)
                    "Akute Belastung ${formatInt(acuteLoad)}, optimaler Bereich ${formatInt(min)}–${formatInt(max)}."
                else "Akute Belastung der letzten 7 Tage.",
                progress = max?.takeIf { it > 0 }?.let { (acuteLoad / (it * 1.33)).toFloat().coerceIn(0f, 1f) },
                tone = tone,
            )
        }

        // Ältere Geräte (z. B. Fenix 6): 7-Tage-Belastung mit Zielkorridor
        val weekly = entry.num("weeklyTrainingLoad") ?: return MetricValue.unavailable(type, NO_DATA_YET)
        val min = entry.num("loadTunnelMin")
        val max = entry.num("loadTunnelMax")
        val (label, tone) = when {
            min == null || max == null -> "" to Tone.NEUTRAL
            weekly < min -> "Niedrig" to Tone.NEUTRAL
            weekly > max -> "Hoch" to Tone.WARN
            else -> "Optimal" to Tone.GOOD
        }
        return MetricValue(
            type = type,
            available = true,
            value = formatInt(weekly),
            subtitle = listOf("7 Tage", label).filter { it.isNotBlank() }.joinToString(" · "),
            detail = if (min != null && max != null)
                "7-Tage-Belastung ${formatInt(weekly)}, Zielbereich ${formatInt(min)}–${formatInt(max)}."
            else "Trainingsbelastung der letzten 7 Tage.",
            progress = max?.takeIf { it > 0 }?.let { (weekly / (it * 1.33)).toFloat().coerceIn(0f, 1f) },
            tone = tone,
        )
    }

    // ---------- VO2max ----------

    private val vo2Source: JSONObject? = run {
        val fromStatus = status.data()?.obj("mostRecentVO2Max")?.obj("generic")
        val fromFallback = when (val d = vo2Fallback?.data()) {
            is JSONObject -> d.obj("generic") ?: d
            is JSONArray -> d.objects().lastOrNull()?.let { it.obj("generic") ?: it }
            else -> null
        }
        fromStatus ?: fromFallback
    }
    val vo2Value: Double? = vo2Source?.num("vo2MaxPreciseValue") ?: vo2Source?.num("vo2MaxValue")

    /**
     * Dein übliches Lauftempo in s/km: Median der Läufe ab 2,5 km aus den letzten 60 Tagen.
     */
    val usualPaceSecPerKm: Double? = run {
        val cutoff = today.minusDays(60).toString()
        val paces = runs.data()?.objects().orEmpty()
            .filter { (it.str("startTimeLocal") ?: "") >= cutoff }
            .filter { (it.num("distance") ?: 0.0) >= 2500.0 }
            .mapNotNull { it.num("averageSpeed")?.takeIf { v -> v in 1.5..7.0 } }
            .map { 1000.0 / it }
            .sorted()
        if (paces.size < 2) null else paces[paces.size / 2]
    }

    fun vo2max(): MetricValue {
        val type = MetricType.VO2MAX
        val src = vo2Source
        val value = vo2Value
            ?: return MetricValue.unavailable(type, reason(status))
        val date = src?.str("calendarDate")
        val fitnessAge = src?.num("fitnessAge")
        return MetricValue(
            type = type,
            available = true,
            value = value.roundToInt().toString(),
            unit = "ml/kg/min",
            subtitle = if (fitnessAge != null) "Fitnessalter ${fitnessAge.roundToInt()}" else "Laufen",
            detail = buildString {
                append("Genauer Wert: ${formatDecimal(value)} ml/kg/min")
                if (date != null) append(", gemessen am ${germanDate(date)}")
                append(".")
            },
            progress = ((value - 30) / 40).toFloat().coerceIn(0f, 1f),
            tone = Tone.NEUTRAL,
        )
    }

    // ---------- Schlaf ----------

    private val sleepDto: JSONObject? = sleep.data()?.obj("dailySleepDTO")
    val sleepScore: Int? = sleepDto?.obj("sleepScores")?.obj("overall")?.num("value")?.roundToInt()

    fun sleep(): MetricValue {
        val type = MetricType.SLEEP
        val dto = sleepDto ?: return MetricValue.unavailable(type, reason(sleep, NO_DATA_YET))
        val seconds = dto.num("sleepTimeSeconds")?.toLong()?.takeIf { it > 0 }
            ?: return MetricValue.unavailable(type, "Keine Schlafdaten für letzte Nacht")
        val qualifier = dto.obj("sleepScores")?.obj("overall")?.str("qualifierKey")
        val quality = when (qualifier) {
            "EXCELLENT" -> "Ausgezeichnet"
            "GOOD" -> "Gut"
            "FAIR" -> "Mittelmäßig"
            "POOR" -> "Schlecht"
            else -> null
        }
        val deep = dto.num("deepSleepSeconds")?.toLong()
        val rem = dto.num("remSleepSeconds")?.toLong()
        val detail = buildString {
            append("Geschlafen: ${formatDurationLong(seconds)}")
            if (deep != null) append(" · Tiefschlaf ${formatDurationLong(deep)}")
            if (rem != null) append(" · REM ${formatDurationLong(rem)}")
            append(".")
        }
        val score = sleepScore
        return if (score != null) {
            MetricValue(
                type = type,
                available = true,
                value = score.toString(),
                subtitle = listOfNotNull("${formatDurationShort(seconds)} h", quality).joinToString(" · "),
                detail = detail,
                progress = score / 100f,
                tone = when {
                    score >= 80 -> Tone.GOOD
                    score >= 60 -> Tone.NEUTRAL
                    score >= 40 -> Tone.WARN
                    else -> Tone.BAD
                },
            )
        } else {
            // Ohne Schlafscore (ältere Geräte): Schlafdauer als Hauptwert
            MetricValue(
                type = type,
                available = true,
                value = formatDurationShort(seconds),
                unit = "h",
                subtitle = "Schlafdauer",
                detail = detail,
                progress = (seconds / (8.0 * 3600)).toFloat().coerceIn(0f, 1f),
            )
        }
    }

    // ---------- Erholungsdauer ----------

    /** Verbleibende Erholung in Stunden (aus der Trainingsbereitschaft, falls vom Gerät unterstützt). */
    val recoveryHours: Double? = run {
        val entries = when (val d = readiness.data()) {
            is JSONArray -> d.objects()
            is JSONObject -> listOf(d)
            else -> emptyList()
        }.filter { it.num("recoveryTime") != null }
        val latest = entries.maxByOrNull { it.str("timestampLocal") ?: it.str("timestamp") ?: "" } ?: return@run null
        if (latest.str("recoveryTimeChangePhrase") == "REACHED_ZERO") return@run 0.0
        val minutesAtSnapshot = latest.num("recoveryTime") ?: return@run null
        // Der Wert gilt zum Messzeitpunkt – seitdem ist Zeit vergangen.
        val elapsedMin = latest.str("timestampLocal")?.let { ts ->
            runCatching {
                Duration.between(LocalDateTime.parse(ts.take(19)), LocalDateTime.now()).toMinutes().toDouble()
            }.getOrNull()
        } ?: 0.0
        ((minutesAtSnapshot - elapsedMin.coerceAtLeast(0.0)) / 60.0).coerceAtLeast(0.0)
    }

    fun recovery(): MetricValue {
        val type = MetricType.RECOVERY
        val hours = recoveryHours ?: return MetricValue.unavailable(type, reason(readiness))
        val rounded = ceil(hours).toInt()
        return MetricValue(
            type = type,
            available = true,
            value = rounded.toString(),
            unit = "h",
            subtitle = if (rounded == 0) "Vollständig erholt" else "bis vollständig erholt",
            detail = if (rounded == 0) "Du bist vollständig erholt."
            else "Noch etwa $rounded Stunden bis zur vollständigen Erholung.",
            progress = (1.0 - hours / 72.0).toFloat().coerceIn(0f, 1f),
            tone = when {
                rounded == 0 -> Tone.GOOD
                rounded > 36 -> Tone.WARN
                else -> Tone.NEUTRAL
            },
        )
    }

    // ---------- Body Battery ----------

    val bodyBatteryNow: Int? = summary.data()?.num("bodyBatteryMostRecentValue")?.roundToInt()
        ?: bodyBatteryFallback?.data()?.objects()?.firstOrNull()?.let { latestBodyBattery(it) }

    fun bodyBattery(): MetricValue {
        val type = MetricType.BODY_BATTERY
        val value = bodyBatteryNow ?: return MetricValue.unavailable(type, reason(summary, NO_DATA_YET))
        val s = summary.data()
        val high = s?.num("bodyBatteryHighestValue")?.roundToInt()
        val low = s?.num("bodyBatteryLowestValue")?.roundToInt()
        val charged = s?.num("bodyBatteryChargedValue")?.roundToInt()
        val drained = s?.num("bodyBatteryDrainedValue")?.roundToInt()
        return MetricValue(
            type = type,
            available = true,
            value = value.toString(),
            subtitle = if (high != null && low != null) "Max $high · Min $low" else "",
            detail = buildString {
                append("Aktuell $value von 100.")
                if (charged != null) append(" Heute +$charged aufgeladen")
                if (drained != null) append(", −$drained verbraucht")
                if (charged != null || drained != null) append(".")
            },
            progress = value / 100f,
            tone = when {
                value < 25 -> Tone.BAD
                value < 50 -> Tone.WARN
                else -> Tone.GOOD
            },
        )
    }

    private fun latestBodyBattery(report: JSONObject): Int? {
        val values = report.arr("bodyBatteryValuesArray") ?: return null
        val descriptors = report.arr("bodyBatteryValueDescriptorDTOList")?.objects().orEmpty()
        val levelIndex = descriptors.firstOrNull {
            it.str("bodyBatteryValueDescriptorKey") == "bodyBatteryLevel"
        }?.optInt("bodyBatteryValueDescriptorIndex", -1) ?: -1
        for (i in values.length() - 1 downTo 0) {
            val row = values.optJSONArray(i) ?: continue
            val idx = if (levelIndex >= 0) levelIndex else row.length() - 1
            val v = row.optDouble(idx)
            if (!v.isNaN()) return v.roundToInt()
        }
        return null
    }

    // ---------- HFV ----------

    private val hrvSummary: JSONObject? = hrv.data()?.obj("hrvSummary")
    val hrvStatusKey: String? = hrvSummary?.str("status")

    fun hrv(): MetricValue {
        val type = MetricType.HRV
        // Nächtliche HFV: zuerst der HFV-Status (neuere Uhren), sonst die Schlafdaten –
        // dort steht sie auch bei älteren Uhren wie der Fenix 6.
        val fromStatus = hrvSummary?.num("lastNightAvg")
        val lastNight = fromStatus
            ?: sleepDto?.num("avgOvernightHrv")
            ?: sleep.data()?.num("avgOvernightHrv")
            ?: sleepDto?.num("avgSleepHRV")
            ?: return MetricValue.unavailable(type, reason(hrv))
        val weekly = hrvSummary?.num("weeklyAvg")
        val baseline = hrvSummary?.obj("baseline")
        val low = baseline?.num("balancedLow")
        val high = baseline?.num("balancedUpper")
        val (label, tone) = when (hrvStatusKey) {
            "BALANCED" -> "Ausgeglichen" to Tone.GOOD
            "UNBALANCED" -> "Unausgeglichen" to Tone.WARN
            "LOW" -> "Niedrig" to Tone.BAD
            "POOR" -> "Schlecht" to Tone.BAD
            "NONE", null -> (if (fromStatus == null) "Letzte Nacht" else "Wird ermittelt") to Tone.NEUTRAL
            else -> (hrvStatusKey ?: "") to Tone.NEUTRAL
        }
        return MetricValue(
            type = type,
            available = true,
            value = lastNight.roundToInt().toString(),
            unit = "ms",
            subtitle = label,
            detail = buildString {
                append("Letzte Nacht Ø ${lastNight.roundToInt()} ms")
                if (weekly != null) append(", 7-Tage-Ø ${weekly.roundToInt()} ms")
                if (low != null && high != null) append(", ausgeglichener Bereich ${low.roundToInt()}–${high.roundToInt()} ms")
                append(".")
            },
            progress = if (low != null && high != null && high > 0) {
                (lastNight / (high * 1.25)).toFloat().coerceIn(0f, 1f)
            } else null,
            tone = tone,
        )
    }

    // ---------- Kalorien ----------

    fun calories(): MetricValue {
        val type = MetricType.CALORIES
        val s = summary.data()
        val total = s?.num("totalKilocalories")?.takeIf { it > 0 }
            ?: return MetricValue.unavailable(type, reason(summary, NO_DATA_YET))
        val active = s?.num("activeKilocalories")
        val bmr = s?.num("bmrKilocalories")
        return MetricValue(
            type = type,
            available = true,
            value = formatInt(total),
            unit = "kcal",
            subtitle = active?.let { "aktiv ${formatInt(it)}" }.orEmpty(),
            detail = buildString {
                append("Heute bisher ${formatInt(total)} kcal verbrannt")
                if (active != null) append(", davon ${formatInt(active)} kcal aktiv")
                if (bmr != null) append(" (Grundumsatz ${formatInt(bmr)} kcal)")
                append(".")
            },
        )
    }

    // ---------- Trainingsvorschlag ----------

    fun suggestion(): MetricValue {
        val items = calendar.data()?.arr("calendarItems")?.objects().orEmpty()
            .filter { it.str("itemType") == "workout" && it.str("date") == today.toString() }
        val planned = items.firstOrNull { it.str("sportTypeKey")?.contains("running") == true } ?: items.firstOrNull()
        return SuggestionEngine.build(
            SuggestionInput(
                plannedWorkoutTitle = planned?.str("title"),
                plannedIsRun = planned?.str("sportTypeKey")?.contains("running") == true,
                recoveryHours = recoveryHours,
                bodyBattery = bodyBatteryNow,
                sleepScore = sleepScore,
                trainingStatus = statusKey,
                acwrStatus = acwrStatus,
                loadFocus = loadFocusKey,
                hrvStatus = hrvStatusKey,
                usualPaceSecPerKm = usualPaceSecPerKm,
                vo2max = vo2Value,
                date = today,
            )
        )
    }

    // ---------- Texte ----------

    private fun loadFocusLabel(key: String?): String? = when {
        key == null -> null
        key.contains("ANAEROBIC_SHORTAGE") -> "Mangel anaerob"
        key.contains("AEROBIC_HIGH_SHORTAGE") -> "Mangel hoch aerob"
        key.contains("AEROBIC_LOW_SHORTAGE") -> "Mangel niedrig aerob"
        key.contains("ANAEROBIC_FOCUS") -> "Fokus anaerob"
        key.contains("AEROBIC_HIGH_FOCUS") -> "Fokus hoch aerob"
        key.contains("AEROBIC_LOW_FOCUS") -> "Fokus niedrig aerob"
        key.contains("BALANCED") -> "Ausgewogen"
        key.contains("ABOVE") -> "Über dem Zielbereich"
        key.contains("BELOW") -> "Unter dem Zielbereich"
        else -> null
    }

    private fun statusDescription(key: String): String = when (key) {
        "PRODUCTIVE" -> "Deine Fitness verbessert sich – die Belastung passt."
        "MAINTAINING" -> "Deine Fitness bleibt stabil."
        "RECOVERY" -> "Leichtere Belastung erlaubt dem Körper, sich zu erholen."
        "PEAKING" -> "Du bist in Topform für einen Wettkampf."
        "UNPRODUCTIVE" -> "Die Belastung ist gut, aber die Fitness sinkt – evtl. fehlt Erholung."
        "OVERREACHING" -> "Die Belastung ist sehr hoch und kontraproduktiv – mehr Erholung einplanen."
        "STRAINED" -> "Erholung oder HFV sind beeinträchtigt – es fällt schwer, sich zu erholen."
        "DETRAINING" -> "Die Belastung ist seit über einer Woche deutlich niedriger als üblich."
        "PAUSED" -> "Der Trainingsstatus ist pausiert."
        else -> "Noch nicht genug Daten für einen Trainingsstatus."
    }

    private fun germanDate(iso: String): String = runCatching {
        val d = LocalDate.parse(iso.take(10))
        "%02d.%02d.".format(d.dayOfMonth, d.monthValue)
    }.getOrDefault(iso)
}
