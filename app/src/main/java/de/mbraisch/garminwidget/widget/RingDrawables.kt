package de.mbraisch.garminwidget.widget

import de.mbraisch.garminwidget.R

/** Vorgefertigte Ring-Segmente in 5-%-Schritten (Vektorgrafiken, scharf in jeder Größe). */
object RingDrawables {
    private val steps = listOf(
        R.drawable.ring_005,
        R.drawable.ring_010,
        R.drawable.ring_015,
        R.drawable.ring_020,
        R.drawable.ring_025,
        R.drawable.ring_030,
        R.drawable.ring_035,
        R.drawable.ring_040,
        R.drawable.ring_045,
        R.drawable.ring_050,
        R.drawable.ring_055,
        R.drawable.ring_060,
        R.drawable.ring_065,
        R.drawable.ring_070,
        R.drawable.ring_075,
        R.drawable.ring_080,
        R.drawable.ring_085,
        R.drawable.ring_090,
        R.drawable.ring_095,
        R.drawable.ring_100,
    )

    /** Drawable für den Fortschritt 0..1 oder null, wenn nichts gefüllt ist. */
    fun forProgress(progress: Float): Int? {
        val index = Math.round(progress.coerceIn(0f, 1f) * 20f) - 1
        return if (index < 0) null else steps[index]
    }
}
