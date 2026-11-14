package com.drinkwithme.core.stats

import com.drinkwithme.core.health.alcoholGrams
import com.drinkwithme.core.health.isAlcoholic
import com.drinkwithme.core.health.isSugary
import com.drinkwithme.core.health.sugarGrams
import com.drinkwithme.core.log.DrinkEntry
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Persönliche Statistik als reine Funktionen über die Einträge.
 *
 * Alle Zeiträume sind **Kalendertage in [zone]** (nicht 24-h-Blöcke), daher sind Sommer-/Winterzeit-Wechsel
 * (23- bzw. 25-Stunden-Tage) korrekt. Wochen beginnen immer am Montag.
 *
 * „Erfasst“ ist ein Tag ab dem Kalendertag des frühesten übergebenen Eintrags bis heute – die App übergibt
 * deshalb die gesamte (lokale) Historie. Tage vor dem ersten Eintrag zählen nie als alkoholfrei.
 */
class StatisticsCalculator(private val clock: Clock, private val zone: ZoneId) {

    /** Zucker pro Tag für die letzten [days] Tage bis heute (aufsteigend, Tage ohne Einträge mit 0 g). */
    fun dailySugar(entries: List<DrinkEntry>, days: Int = DEFAULT_SUGAR_DAYS): List<DailySugar> {
        require(days >= 1) { "days muss mindestens 1 sein." }
        val sugarByDate = entries.groupBy { it.date() }.mapValues { (_, list) -> list.sumOf { it.sugarGrams } }
        val start = currentDate().minusDays(days - 1L)
        return (0 until days).map { offset ->
            val date = start.plusDays(offset.toLong())
            DailySugar(date, sugarByDate[date] ?: 0.0)
        }
    }

    /** Alkohol pro Woche (Montag bis Sonntag) für die letzten [weeks] Wochen einschließlich der laufenden. */
    fun weeklyAlcohol(entries: List<DrinkEntry>, weeks: Int = DEFAULT_ALCOHOL_WEEKS): List<WeeklyAlcohol> {
        require(weeks >= 1) { "weeks muss mindestens 1 sein." }
        val today = currentDate()
        val byDate = entries.groupBy { it.date() }
        val trackingStart = trackingStart(entries)
        val currentWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (weeks - 1 downTo 0).map { weeksBack ->
            val weekStart = currentWeekStart.minusWeeks(weeksBack.toLong())
            val days = (0L until DAYS_PER_WEEK).map { weekStart.plusDays(it) }
            WeeklyAlcohol(
                weekStart = weekStart,
                grams = days.sumOf { day -> byDate[day].orEmpty().sumOf { it.alcoholGrams } },
                alcoholFreeDays = days.count { day ->
                    isTracked(day, trackingStart, today) && byDate[day].orEmpty().none { it.isAlcoholic }
                },
            )
        }
    }

    /** Kennzahlen von heute. */
    fun today(entries: List<DrinkEntry>): TodaySummary {
        val today = currentDate()
        val todays = entries.filter { it.date() == today }
        return TodaySummary(
            sugarGrams = todays.sumOf { it.sugarGrams },
            alcoholGrams = todays.sumOf { it.alcoholGrams },
            sugaryDrinks = todays.count { it.isSugary },
            alcoholicDrinks = todays.count { it.isAlcoholic },
            totalMilliliters = todays.sumOf { it.volumeMilliliters },
            drinkCount = todays.size,
        )
    }

    /** Zucker und Alkohol der letzten 30 Tage (bis heute) gegenüber den 30 Tagen davor. */
    fun monthComparison(entries: List<DrinkEntry>): MonthComparison {
        val today = currentDate()
        val currentStart = today.minusDays(COMPARISON_DAYS - 1L)
        val previousStart = currentStart.minusDays(COMPARISON_DAYS.toLong())
        val previousEnd = currentStart.minusDays(1)
        val current = entries.filter { it.date() in currentStart..today }
        val previous = entries.filter { it.date() in previousStart..previousEnd }
        val trackingStart = trackingStart(entries)
        val hasBasis = trackingStart != null && !trackingStart.isAfter(previousStart)

        val sugarNow = current.sumOf { it.sugarGrams }
        val sugarBefore = previous.sumOf { it.sugarGrams }
        val alcoholNow = current.sumOf { it.alcoholGrams }
        val alcoholBefore = previous.sumOf { it.alcoholGrams }
        return MonthComparison(
            sugarChangePercent = changePercent(sugarNow, sugarBefore, hasBasis),
            alcoholChangePercent = changePercent(alcoholNow, alcoholBefore, hasBasis),
            sugarGramsLast30Days = sugarNow,
            sugarGramsPrevious30Days = sugarBefore,
            alcoholGramsLast30Days = alcoholNow,
            alcoholGramsPrevious30Days = alcoholBefore,
        )
    }

    /** Alkohol in g/Woche und Zucker in g/Tag über die letzten 28 Tage, bezogen auf die erfassten Tage. */
    fun movingAverage4Weeks(entries: List<DrinkEntry>): MovingAverage {
        val today = currentDate()
        val windowStart = today.minusDays(MOVING_AVERAGE_DAYS - 1L)
        val trackingStart = trackingStart(entries) ?: return MovingAverage(null, null, 0)
        val firstCovered = maxOf(windowStart, trackingStart)
        val coveredDays = if (firstCovered.isAfter(today)) 0 else (today.toEpochDay() - firstCovered.toEpochDay() + 1).toInt()
        if (coveredDays < MOVING_AVERAGE_MIN_DAYS) return MovingAverage(null, null, coveredDays)

        val inWindow = entries.filter { it.date() in windowStart..today }
        return MovingAverage(
            alcoholGramsPerWeek = inWindow.sumOf { it.alcoholGrams } / coveredDays * DAYS_PER_WEEK,
            sugarGramsPerDay = inWindow.sumOf { it.sugarGrams } / coveredDays,
            coveredDays = coveredDays,
        )
    }

    /** Zusammenhängende alkoholfreie Tage bis einschließlich heute (0, wenn heute schon Alkohol dabei war). */
    fun alcoholFreeStreak(entries: List<DrinkEntry>): Int {
        val trackingStart = trackingStart(entries) ?: return 0
        val alcoholicDays = entries.filter { it.isAlcoholic }.map { it.date() }.toSet()
        var day = currentDate()
        var streak = 0
        while (!day.isBefore(trackingStart) && day !in alcoholicDays) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    private fun currentDate(): LocalDate = clock.instant().atZone(zone).toLocalDate()

    private fun DrinkEntry.date(): LocalDate = consumedAt.atZone(zone).toLocalDate()

    private fun trackingStart(entries: List<DrinkEntry>): LocalDate? = entries.minOfOrNull { it.date() }

    private fun isTracked(day: LocalDate, trackingStart: LocalDate?, today: LocalDate): Boolean =
        trackingStart != null && !day.isBefore(trackingStart) && !day.isAfter(today)

    private fun changePercent(now: Double, before: Double, hasBasis: Boolean): Double? =
        if (!hasBasis || before <= 0.0) null else (now - before) / before * 100.0

    companion object {
        const val DEFAULT_SUGAR_DAYS = 14
        const val DEFAULT_ALCOHOL_WEEKS = 8
        const val COMPARISON_DAYS = 30
        const val MOVING_AVERAGE_DAYS = 28
        const val MOVING_AVERAGE_MIN_DAYS = 7
        private const val DAYS_PER_WEEK = 7L
    }
}
