package de.mbraisch.garminwidget.data

import androidx.annotation.DrawableRes
import de.mbraisch.garminwidget.R
import org.json.JSONArray
import org.json.JSONObject

/** Farbfamilie einer Kachel – wird im Widget und in der App auf die Material-You-Rollen abgebildet. */
enum class Accent { PRIMARY, SECONDARY, TERTIARY }

/** Bewertung eines Werts; steuert z. B. die Fehlerfarbe bei sehr niedriger Body Battery. */
enum class Tone { GOOD, NEUTRAL, WARN, BAD }

enum class MetricType(
    val id: String,
    val label: String,
    val shortLabel: String,
    @DrawableRes val icon: Int,
    val accent: Accent,
) {
    TRAINING_STATUS("training_status", "Trainingszustand", "Status", R.drawable.ic_training_status, Accent.PRIMARY),
    SUGGESTION("suggestion", "Trainingsvorschlag", "Vorschlag", R.drawable.ic_suggestion, Accent.PRIMARY),
    VO2MAX("vo2max", "VO2max", "VO2max", R.drawable.ic_vo2max, Accent.SECONDARY),
    SLEEP("sleep", "Schlaf", "Schlaf", R.drawable.ic_sleep, Accent.TERTIARY),
    RECOVERY("recovery", "Erholungsdauer", "Erholung", R.drawable.ic_recovery, Accent.SECONDARY),
    BODY_BATTERY("body_battery", "Body Battery", "Battery", R.drawable.ic_body_battery, Accent.PRIMARY),
    LOAD("load", "Belastung", "Belastung", R.drawable.ic_load, Accent.SECONDARY),
    HRV("hrv", "HFV", "HFV", R.drawable.ic_hrv, Accent.TERTIARY),
    CALORIES("calories", "Kalorien", "kcal", R.drawable.ic_calories, Accent.TERTIARY);

    companion object {
        fun fromId(id: String): MetricType? = entries.firstOrNull { it.id == id }

        /** Standardreihenfolge der Kacheln, wie sie nach der Installation aktiv ist. */
        val DEFAULT_ORDER = listOf(BODY_BATTERY, SLEEP, TRAINING_STATUS, SUGGESTION, HRV, RECOVERY, VO2MAX, LOAD, CALORIES)
    }
}

/**
 * Ein aufbereiteter Wert, so wie ihn Widget und App anzeigen.
 *
 * [value] ist der große Hauptwert (z. B. "72" oder "Produktiv"), [unit] die Einheit,
 * [subtitle] eine kurze zweite Zeile, [detail] ein längerer Text für die App und große Kacheln.
 * [progress] (0..1) füllt die Ringanzeige; null = keine Ringanzeige sinnvoll.
 */
data class MetricValue(
    val type: MetricType,
    val available: Boolean,
    val value: String,
    val unit: String = "",
    val subtitle: String = "",
    val detail: String = "",
    val progress: Float? = null,
    val tone: Tone = Tone.NEUTRAL,
    /** true, wenn der Hauptwert ein Wort/Text ist (Status, Vorschlag) und keine Zahl. */
    val isText: Boolean = false,
    /** Kurzform des Hauptwerts fürs Widget (z. B. "Tempo" statt "Tempolauf"). Leer = [value]. */
    val short: String = "",
) {
    /** Wert, wie ihn das Widget zeigt. */
    val compact: String get() = short.ifEmpty { value }

    fun toJson(): JSONObject = JSONObject()
        .put("type", type.id)
        .put("available", available)
        .put("value", value)
        .put("unit", unit)
        .put("subtitle", subtitle)
        .put("detail", detail)
        .put("progress", progress?.toDouble() ?: JSONObject.NULL)
        .put("tone", tone.name)
        .put("isText", isText)
        .put("short", short)

    companion object {
        fun unavailable(type: MetricType, reason: String) = MetricValue(
            type = type,
            available = false,
            value = "–",
            subtitle = reason,
        )

        fun fromJson(o: JSONObject): MetricValue? {
            val type = MetricType.fromId(o.optString("type")) ?: return null
            return MetricValue(
                type = type,
                available = o.optBoolean("available"),
                value = o.optString("value", "–"),
                unit = o.optString("unit"),
                subtitle = o.optString("subtitle"),
                detail = o.optString("detail"),
                progress = if (o.isNull("progress")) null else o.optDouble("progress").toFloat(),
                tone = runCatching { Tone.valueOf(o.optString("tone")) }.getOrDefault(Tone.NEUTRAL),
                isText = o.optBoolean("isText"),
                short = o.optString("short"),
            )
        }
    }
}

/** Stand aller Werte zum Zeitpunkt der letzten Synchronisation. */
data class Snapshot(
    val fetchedAt: Long,
    val metrics: Map<MetricType, MetricValue>,
    val error: String? = null,
) {
    operator fun get(type: MetricType): MetricValue =
        metrics[type] ?: MetricValue.unavailable(type, "Noch keine Daten")

    fun toJsonString(): String {
        val arr = JSONArray()
        metrics.values.forEach { arr.put(it.toJson()) }
        return JSONObject()
            .put("fetchedAt", fetchedAt)
            .put("error", error ?: JSONObject.NULL)
            .put("metrics", arr)
            .toString()
    }

    companion object {
        val EMPTY = Snapshot(0L, emptyMap())

        fun fromJsonString(s: String?): Snapshot {
            if (s.isNullOrBlank()) return EMPTY
            return runCatching {
                val o = JSONObject(s)
                val arr = o.optJSONArray("metrics") ?: JSONArray()
                val map = buildMap {
                    for (i in 0 until arr.length()) {
                        MetricValue.fromJson(arr.getJSONObject(i))?.let { put(it.type, it) }
                    }
                }
                Snapshot(
                    fetchedAt = o.optLong("fetchedAt"),
                    metrics = map,
                    error = if (o.isNull("error")) null else o.optString("error"),
                )
            }.getOrDefault(EMPTY)
        }
    }
}
