package com.drinkwithme.core.stats

import com.drinkwithme.core.health.AlcoholRiskBand
import com.drinkwithme.core.health.HealthReferences
import java.time.LocalDate

/** Zucker aus Getränken an einem Kalendertag. */
data class DailySugar(val date: LocalDate, val grams: Double)

/**
 * Alkohol einer Kalenderwoche (Montag bis Sonntag).
 *
 * @property alcoholFreeDays Tage ohne alkoholisches Getränk – gezählt werden nur Tage bis heute und ab dem
 *   ersten Eintrag (davor gibt es keine Daten).
 */
data class WeeklyAlcohol(val weekStart: LocalDate, val grams: Double, val alcoholFreeDays: Int) {
    /** Bereich nach [HealthReferences.weeklyAlcoholBand]. */
    val band: AlcoholRiskBand get() = HealthReferences.weeklyAlcoholBand(grams)
}

/** Kennzahlen des heutigen Tages. */
data class TodaySummary(
    val sugarGrams: Double,
    val alcoholGrams: Double,
    val sugaryDrinks: Int,
    val alcoholicDrinks: Int,
    val totalMilliliters: Int,
    val drinkCount: Int,
)

/**
 * Letzte 30 Tage im Vergleich zu den 30 Tagen davor. Prozent = (jetzt − vorher) / vorher × 100;
 * `null`, wenn es keine Vergleichsbasis gibt (vorher 0 g oder der Vorzeitraum ist nicht vollständig erfasst).
 */
data class MonthComparison(
    val sugarChangePercent: Double?,
    val alcoholChangePercent: Double?,
    val sugarGramsLast30Days: Double,
    val sugarGramsPrevious30Days: Double,
    val alcoholGramsLast30Days: Double,
    val alcoholGramsPrevious30Days: Double,
)

/**
 * Gleitender Durchschnitt der letzten 4 Wochen (28 Tage bis heute), bezogen auf die erfassten Tage.
 * Die Werte sind `null`, solange weniger als [StatisticsCalculator.MOVING_AVERAGE_MIN_DAYS] Tage erfasst sind.
 */
data class MovingAverage(
    val alcoholGramsPerWeek: Double?,
    val sugarGramsPerDay: Double?,
    val coveredDays: Int,
)
