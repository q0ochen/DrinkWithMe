package com.drinkwithme.app.stats

import kotlin.math.ceil

/** Gemeinsame Achsen-Rechnung für die Diagramme in der App und im geteilten Bild. */
object ChartScale {
    /**
     * Obergrenze der y-Achse: Platz für den größten Wert und alle Bezugslinien (+15 %), aufgerundet auf
     * [step]. Nie 0, damit leere Diagramme nicht durch 0 teilen.
     */
    fun upperBound(values: List<Double>, references: List<Double>, step: Double = 10.0): Double {
        val highest = (values + references).filter { it.isFinite() }.maxOrNull() ?: 0.0
        val padded = highest * 1.15
        return maxOf(step, ceil(padded / step) * step)
    }

    /** Anteil 0..1 eines Werts an der Achse (für die Balkenhöhe). */
    fun fraction(value: Double, upper: Double): Float =
        if (upper <= 0.0 || !value.isFinite()) 0f else (value / upper).coerceIn(0.0, 1.0).toFloat()
}
