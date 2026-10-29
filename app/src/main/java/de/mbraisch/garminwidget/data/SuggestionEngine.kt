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
    /** Dein übliches Lauftempo in s/km (Median deiner letzten Läufe) – Basis für die Zielpace. */
    val usualPaceSecPerKm: Double? = null,
    /** VO2max als Ersatz, falls es keine Läufe gibt. */
    val vo2max: Double? = null,
    val date: LocalDate = LocalDate.now(),
)

/** Ein konkretes Lauftraining: Dauer bzw. Wiederholungen + Zielpace. */
private data class Workout(
    val title: String,
    val short: String,
    /** z. B. "40 min" oder "6 × 2 min" */
    val volume: String,
    /** Abstand der Zielpace zu deinem üblichen Tempo in s/km (positiv = langsamer); null = kein Lauf */
    val paceOffset: Int?,
    val what: String,
    val why: String,
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

        val w = when {
            knownInputs == 0 -> Workout("Grundlagenlauf", "Grundlage", "40 min", 20,
                GRUNDLAGE, "Noch zu wenig Daten für eine genauere Empfehlung")
            fatigue >= 4 -> Workout("Ruhetag", "Ruhetag", "Pause", null,
                "Heute kein Lauf – höchstens ein Spaziergang.", "Dein Körper braucht Erholung")
            fatigue == 3 -> Workout("Regeneration", "Regeneration", "25 min", 60,
                "Ganz lockerer Lauf (Zone 1), deutlich langsamer als sonst.", "Nur locker, um die Erholung zu fördern")
            fatigue == 2 -> Workout("Lockerer Lauf", "Locker", "35 min", 25,
                "Lockerer Dauerlauf (Zone 2), keine schnellen Abschnitte.", "Locker bleiben, keine Intensität")
            focus.contains("ANAEROBIC_SHORTAGE") -> Workout("Intervalle", "Intervalle", "6 × 2 min", -75,
                "Einlaufen, dann 6 × 2 min schnell mit je 2 min Trabpause, auslaufen.", "Dir fehlt anaerobe Belastung")
            focus.contains("AEROBIC_HIGH_SHORTAGE") -> Workout("Tempolauf", "Tempo", "3 × 8 min", -45,
                "Einlaufen, dann 3 × 8 min an der Schwelle mit je 3 min Trabpause, auslaufen.", "Dir fehlt hoch aerobe Belastung")
            focus.contains("AEROBIC_LOW_SHORTAGE") -> Workout("Grundlage lang", "Lang & locker", "70 min", 30,
                GRUNDLAGE, "Dir fehlt niedrig aerobe Belastung")
            weekend -> Workout("Langer Lauf", "Lang", "80 min", 30,
                GRUNDLAGE, "Wochenende – Zeit für die lange Einheit")
            fatigue == 1 -> Workout("Grundlagenlauf", "Grundlage", "40 min", 20,
                GRUNDLAGE, "Gute Basis, aber nicht ganz frisch")
            else -> Workout("Grundlage + Steigerungen", "Grundlage", "45 min", 20,
                "$GRUNDLAGE Am Ende 4 × 20 s zügig steigern.", "Du bist erholt")
        }

        val pace = w.paceOffset?.let { offset -> referencePace(input)?.let { formatPace(it + offset) } }
        val subtitle = when {
            w.paceOffset == null -> w.volume
            pace != null -> "${w.volume} · $pace"
            else -> w.volume
        }

        val detail = buildString {
            append(w.what)
            append(" ").append(w.why)
            if (reasons.isNotEmpty()) append(" (").append(reasons.joinToString(", ")).append(")")
            append(".")
            if (pace != null && input.usualPaceSecPerKm != null) {
                append(" Zielpace aus deinen letzten Läufen (üblich ${formatPace(input.usualPaceSecPerKm)}).")
            } else if (pace != null) {
                append(" Zielpace aus deiner VO2max geschätzt.")
            }
        }

        return MetricValue(
            type = type,
            available = true,
            value = w.title,
            short = w.short,
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

    private const val GRUNDLAGE =
        "Grundlage = ruhiger Dauerlauf in Zone 2: Du kannst dich dabei noch gut unterhalten."

    /**
     * Bezugstempo in s/km: dein übliches Tempo, sonst aus der VO2max geschätzt
     * (Formel nach Jack Daniels für ~68 % der VO2max ≈ übliches lockeres Tempo).
     */
    private fun referencePace(input: SuggestionInput): Double? {
        input.usualPaceSecPerKm?.let { return it }
        val vo2 = input.vo2max ?: return null
        // VO2 = -4.60 + 0.182258·v + 0.000104·v²  (v in m/min) → nach v auflösen
        val target = 0.68 * vo2 + 4.60
        val v = (-0.182258 + Math.sqrt(0.182258 * 0.182258 + 4 * 0.000104 * target)) / (2 * 0.000104)
        if (v <= 0) return null
        return 60_000.0 / v // s pro km
    }

    /** 380 → "6:20/km" (auf 5 s gerundet, wie auf der Uhr) */
    fun formatPace(secPerKm: Double): String {
        val total = (Math.round(secPerKm / 5.0) * 5).toInt().coerceIn(150, 900)
        return "%d:%02d/km".format(total / 60, total % 60)
    }

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
