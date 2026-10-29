package de.mbraisch.garminwidget.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Zahl oder null (fehlend, null oder kein Zahlenwert). */
fun JSONObject.num(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    val d = optDouble(key)
    return if (d.isNaN()) null else d
}

fun JSONObject.str(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).ifBlank { null }
}

fun JSONObject.obj(key: String): JSONObject? = if (isNull(key)) null else optJSONObject(key)
fun JSONObject.arr(key: String): JSONArray? = if (isNull(key)) null else optJSONArray(key)

fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

/**
 * Garmin liefert manche Werte als Map "Geräte-ID → Daten". Nimmt den Eintrag des
 * primären Trainingsgeräts, sonst den ersten.
 */
fun JSONObject.primaryDeviceEntry(): JSONObject? {
    val entries = keys().asSequence().mapNotNull { optJSONObject(it) }.toList()
    return entries.firstOrNull { it.optBoolean("primaryTrainingDevice") } ?: entries.firstOrNull()
}

private val GERMAN = Locale.GERMANY

fun formatInt(value: Double): String = String.format(GERMAN, "%,d", Math.round(value))

fun formatDecimal(value: Double, digits: Int = 1): String = String.format(GERMAN, "%.${digits}f", value)

/** 27240 s → "7 h 34 min" */
fun formatDurationLong(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "$h h $m min" else "$m min"
}

/** 27240 s → "7:34" */
fun formatDurationShort(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return String.format(GERMAN, "%d:%02d", h, m)
}
