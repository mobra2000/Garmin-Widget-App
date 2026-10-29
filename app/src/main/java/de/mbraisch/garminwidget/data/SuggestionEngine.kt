package de.mbraisch.garminwidget.data

import java.time.DayOfWeek
import java.time.LocalDate

/** Eingangswerte für den Laufvorschlag. Alles optional – fehlende Werte werden ignoriert. */
data class SuggestionInput(
    val plannedWorkoutTitle: String? = null,
    val plannedIsRun: Boolean = false,
    val recoveryHours: Double? = null,
    val bodyBattery: Int? = null,
    val sleepScore: Int? = null,
    val trainingStatus: String? = null,      // z. B. "PRODUCTIVE", "OVERREACHING"
    val acwrStatus: String? = null,          // z. B. "OPTIMAL", "HIGH", "VERY_HIGH"
    val loadFocus: String? = null,           // z. B. "AEROBIC_HIGH_SHORTAGE"
    val hrvStatus: String? = null,           // z. B. "BALANCED", "LOW"
    val date: LocalDate = LocalDate.now(),
)

/**
 * Trainingsvorschlag fürs Laufen.
 *
 * Garmin stellt den "täglichen Trainingsvorschlag" der Uhr nicht über Connect bereit.
 * Deshalb gilt:
 * 1. Steht für heute ein Lauf im Garmin-Kalender (z. B. Garmin Coach oder ein selbst
 *    geplantes Workout), wird dieser angezeigt.
 * 2. Sonst leitet die App aus Erholung, Body Battery, Schlaf, HFV, Belastung und
 *    Belastungsfokus selbst einen Vorschlag ab – nach den gleichen Prinzipien wie Garmin
 *    (erst Erholung prüfen, dann den Belastungsfokus ausgleichen).
 */
object SuggestionEngine {

    fun build(input: SuggestionInput): MetricValue {
        val type = MetricType.SUGGESTION

        input.plannedWorkoutTitle?.let { title ->
            return MetricValue(
                type = type,
                available = true,
                value = title,
                short = if (input.plannedIsRun) "Geplant" else title,
                subtitle = if (input.plannedIsRun) "Geplant im Garmin-Kalender" else "Geplant (kein Lauf)",
                detail = "Dieses Workout steht heute in deinem Garmin-Connect-Kalender.",
                tone = Tone.NEUTRAL,
                isText = true,
            )
        }

        val reasons = mutableListOf<String>()
        var fatigue = 0  // 0 = frisch … 3+ = deutlich ermüdet

        input.recoveryHours?.let {
            when {
                it >= 48 -> { fatigue += 3; reasons += "noch ${it.toInt()} h Erholung" }
                it >= 24 -> { fatigue += 2; reasons += "noch ${it.toInt()} h Erholung" }
                it >= 12 -> { fatigue += 1; reasons += "noch ${it.toInt()} h Erholung" }
            }
        }
        input.bodyBattery?.let {
            when {
                it < 25 -> { fatigue += 2; reasons += "Body Battery nur $it" }
                it < 45 -> { fatigue += 1; reasons += "Body Battery $it" }
            }
        }
        input.sleepScore?.let {
            when {
                it < 45 -> { fatigue += 2; reasons += "Schlafscore $it" }
                it < 60 -> { fatigue += 1; reasons += "Schlafscore $it" }
            }
        }
        when (input.hrvStatus) {
            "LOW", "POOR" -> { fatigue += 2; reasons += "HFV niedrig" }
            "UNBALANCED" -> { fatigue += 1; reasons += "HFV unausgeglichen" }
        }
        when (input.acwrStatus) {
            "VERY_HIGH" -> { fatigue += 2; reasons += "akute Belastung sehr hoch" }
            "HIGH" -> { fatigue += 1; reasons += "akute Belastung hoch" }
        }
        when (input.trainingStatus) {
            "OVERREACHING" -> { fatigue += 2; reasons += "Status: Überlastung" }
            "STRAINED", "UNPRODUCTIVE" -> { fatigue += 1; reasons += "Status: ${statusLabel(input.trainingStatus)}" }
        }

        val weekend = input.date.dayOfWeek == DayOfWeek.SATURDAY || input.date.dayOfWeek == DayOfWeek.SUNDAY
        val focus = input.loadFocus.orEmpty()

        val knownInputs = listOf(
            input.recoveryHours, input.bodyBattery, input.sleepScore,
            input.hrvStatus?.takeIf { it != "NONE" }, input.acwrStatus, input.trainingStatus,
        ).count { it != null }

        val (title, subtitle, why) = when {
            knownInputs == 0 -> Triple("Grundlagenlauf", "35–45 min · Zone 2", "Noch zu wenig Daten für eine genauere Empfehlung")
            fatigue >= 4 -> Triple("Ruhetag", "Pause oder lockeres Gehen", "Dein Körper braucht Erholung")
            fatigue == 3 -> Triple("Regeneration", "20–30 min · Zone 1", "Nur ganz locker, um die Erholung zu fördern")
            fatigue == 2 -> Triple("Lockerer Lauf", "30–40 min · Zone 2", "Locker bleiben, keine Intensität")
            focus.contains("ANAEROBIC_SHORTAGE") ->
                Triple("Intervalle", "6 × 2 min schnell · 2 min Trab", "Dir fehlt anaerobe Belastung")
            focus.contains("AEROBIC_HIGH_SHORTAGE") ->
                Triple("Tempolauf", "3 × 8 min Schwelle · 3 min Trab", "Dir fehlt hoch aerobe Belastung")
            focus.contains("AEROBIC_LOW_SHORTAGE") ->
                Triple("Grundlage lang", "60–75 min · Zone 2", "Dir fehlt niedrig aerobe Belastung")
            weekend -> Triple("Langer Lauf", "70–90 min · Zone 2", "Wochenende – Zeit für die lange Einheit")
            fatigue == 1 -> Triple("Grundlagenlauf", "35–45 min · Zone 2", "Gute Basis, aber nicht ganz frisch")
            else -> Triple("Grundlage + Steigerungen", "40–50 min · Zone 2 · 4 × 20 s", "Du bist erholt")
        }

        val detail = buildString {
            append(why)
            if (reasons.isNotEmpty()) append(" (").append(reasons.joinToString(", ")).append(")")
            append(". Vorschlag der App auf Basis deiner Garmin-Werte.")
        }

        return MetricValue(
            type = type,
            available = true,
            value = title,
            short = SHORT_TITLES[title] ?: title,
            subtitle = subtitle,
            detail = detail,
            tone = when {
                fatigue >= 4 -> Tone.BAD
                fatigue >= 2 -> Tone.WARN
                else -> Tone.GOOD
            },
            isText = true,
        )
    }

    /** Kurzformen fürs Widget */
    private val SHORT_TITLES = mapOf(
        "Grundlage + Steigerungen" to "Grundlage",
        "Grundlagenlauf" to "Grundlage",
        "Grundlage lang" to "Lang & locker",
        "Lockerer Lauf" to "Locker",
        "Langer Lauf" to "Lang",
        "Tempolauf" to "Tempo",
    )

    fun statusLabel(status: String?): String = when (status) {
        "PRODUCTIVE" -> "Produktiv"
        "MAINTAINING" -> "Erhaltend"
        "RECOVERY" -> "Erholung"
        "PEAKING" -> "Leistungshoch"
        "UNPRODUCTIVE" -> "Unproduktiv"
        "OVERREACHING" -> "Überlastung"
        "STRAINED" -> "Angestrengt"
        "DETRAINING" -> "Leistungsverlust"
        "PAUSED", "TRAINING_PAUSED" -> "Pausiert"
        "NO_STATUS", null -> "Kein Status"
        else -> status.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}
