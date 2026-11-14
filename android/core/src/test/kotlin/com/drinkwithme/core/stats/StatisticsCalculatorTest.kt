package com.drinkwithme.core.stats

import com.drinkwithme.core.BERLIN
import com.drinkwithme.core.MutableClock
import com.drinkwithme.core.bar.DefaultBarCatalog
import com.drinkwithme.core.berlin
import com.drinkwithme.core.catalogItem
import com.drinkwithme.core.entry
import com.drinkwithme.core.entryOf
import com.drinkwithme.core.health.AlcoholRiskBand
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatisticsCalculatorTest {
    private val delta = 1e-9
    private val pils = catalogItem(DefaultBarCatalog.PILS) // 500 ml → 19,6 g Alkohol, 1,5 g Zucker
    private val alcoholFreeBeer = catalogItem(DefaultBarCatalog.ALKOHOLFREIES_BIER) // 0,4 % → 1,6 g, 12,5 g Zucker
    private val cola = catalogItem(DefaultBarCatalog.COLA) // 330 ml → 34,98 g Zucker
    private val water = catalogItem(DefaultBarCatalog.WASSER)
    private val wine = catalogItem(DefaultBarCatalog.ROTWEIN) // 125 ml → 12 g Alkohol

    private fun calculator(now: Instant) = StatisticsCalculator(MutableClock(now), BERLIN)

    private fun sugar(grams: Double, at: Instant) = entry(at, volume = 100, sugarPer100Ml = grams)

    /** 19,6 g Alkohol ohne Zucker. */
    private fun alcohol(at: Instant) = entry(at, volume = 500, alcoholPercent = 4.9)

    // ---------- dailySugar ----------

    @Test
    fun dailySugarCoversFourteenDaysUpToToday() {
        val stats = calculator(berlin("2026-11-10T12:00"))
        val result = stats.dailySugar(
            listOf(
                entryOf(cola, berlin("2026-11-10T09:00")),
                entryOf(cola, berlin("2026-11-10T11:00")),
                sugar(27.5, berlin("2026-11-09T20:00")),
                sugar(99.0, berlin("2026-10-27T12:00")), // 14 Tage vor heute → außerhalb
            ),
        )
        assertEquals(14, result.size)
        assertEquals(LocalDate.of(2026, 10, 28), result.first().date)
        assertEquals(LocalDate.of(2026, 11, 10), result.last().date)
        assertEquals(69.96, result.last().grams, delta)
        assertEquals(27.5, result[12].grams, delta)
        assertTrue(result.dropLast(2).all { it.grams == 0.0 })
    }

    @Test
    fun dailySugarUsesLocalCalendarDaysAcrossTheAutumnClockChange() {
        // 25.10.2026: Umstellung von 03:00 Sommerzeit auf 02:00 Winterzeit – der Tag hat 25 Stunden.
        val stats = calculator(berlin("2026-10-26T12:00"))
        val result = stats.dailySugar(
            listOf(
                sugar(1.0, Instant.parse("2026-10-24T22:30:00Z")), // 25.10. 00:30 Sommerzeit
                sugar(2.0, Instant.parse("2026-10-25T00:30:00Z")), // 25.10. 02:30 Sommerzeit
                sugar(4.0, Instant.parse("2026-10-25T01:30:00Z")), // 25.10. 02:30 Winterzeit
                sugar(8.0, Instant.parse("2026-10-25T22:30:00Z")), // 25.10. 23:30 Winterzeit
                sugar(16.0, Instant.parse("2026-10-25T23:15:00Z")), // 26.10. 00:15 Winterzeit
            ),
            days = 2,
        )
        assertEquals(listOf(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 26)), result.map { it.date })
        assertEquals(listOf(15.0, 16.0), result.map { it.grams })
    }

    @Test
    fun dailySugarNeedsAtLeastOneDay() {
        assertFailsWith<IllegalArgumentException> { calculator(berlin("2026-11-10T12:00")).dailySugar(emptyList(), days = 0) }
    }

    // ---------- weeklyAlcohol ----------

    @Test
    fun weeksStartOnMondayAndEndWithTheCurrentWeek() {
        val result = calculator(berlin("2026-11-10T12:00")).weeklyAlcohol(emptyList())
        assertEquals(8, result.size)
        assertTrue(result.all { it.weekStart.dayOfWeek == DayOfWeek.MONDAY })
        assertEquals(LocalDate.of(2026, 11, 9), result.last().weekStart)
        assertEquals(LocalDate.of(2026, 9, 21), result.first().weekStart)
    }

    @Test
    fun mondayItselfStartsTheCurrentWeek() {
        val result = calculator(berlin("2026-11-09T00:05")).weeklyAlcohol(emptyList(), weeks = 1)
        assertEquals(LocalDate.of(2026, 11, 9), result.single().weekStart)
    }

    @Test
    fun weekBoundaryAfterTheAutumnClockChange() {
        // Sonntag 25.10. 23:30 Winterzeit gehört noch zur Woche ab 19.10., Montag 00:30 zur Woche ab 26.10.
        val stats = calculator(berlin("2026-10-28T12:00"))
        val result = stats.weeklyAlcohol(
            listOf(
                entryOf(pils, Instant.parse("2026-10-25T22:30:00Z")),
                entryOf(pils, Instant.parse("2026-10-25T23:30:00Z")),
                entryOf(pils, Instant.parse("2026-10-26T10:00:00Z")),
            ),
            weeks = 2,
        )
        assertEquals(listOf(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 26)), result.map { it.weekStart })
        assertEquals(19.6, result[0].grams, delta)
        assertEquals(39.2, result[1].grams, delta)
    }

    @Test
    fun weekBoundaryAfterTheSpringClockChange() {
        // 29.03.2026: Umstellung auf Sommerzeit. Sonntag 23:30 Sommerzeit = 21:30 UTC, Montag 00:30 = 22:30 UTC.
        val stats = calculator(berlin("2026-04-01T12:00"))
        val result = stats.weeklyAlcohol(
            listOf(
                entryOf(pils, Instant.parse("2026-03-29T21:30:00Z")),
                entryOf(pils, Instant.parse("2026-03-29T22:30:00Z")),
            ),
            weeks = 2,
        )
        assertEquals(listOf(LocalDate.of(2026, 3, 23), LocalDate.of(2026, 3, 30)), result.map { it.weekStart })
        assertEquals(listOf(19.6, 19.6), result.map { it.grams })
    }

    @Test
    fun alcoholFreeDaysIgnoreTheGramsOfAlcoholFreeBeer() {
        // Mo Pils, Di alkoholfreies Bier (0,4 %), Mi nur Wasser; heute ist Mittwoch.
        val stats = calculator(berlin("2026-11-11T20:00"))
        val result = stats.weeklyAlcohol(
            listOf(
                entryOf(pils, berlin("2026-11-09T19:00")),
                entryOf(alcoholFreeBeer, berlin("2026-11-10T19:00")),
                entryOf(water, berlin("2026-11-11T08:00")),
            ),
            weeks = 2,
        )
        val thisWeek = result.last()
        assertEquals(21.2, thisWeek.grams, delta) // 19,6 g + 1,6 g – vollständig summiert
        assertEquals(2, thisWeek.alcoholFreeDays) // Di + Mi; Do–So liegen in der Zukunft
        assertEquals(AlcoholRiskBand.LOW_RISK, thisWeek.band)

        val weekBefore = result.first()
        assertEquals(0.0, weekBefore.grams)
        assertEquals(0, weekBefore.alcoholFreeDays) // vor dem ersten Eintrag gibt es keine Daten
    }

    @Test
    fun fullyTrackedWeekWithoutAlcoholHasSevenAlcoholFreeDays() {
        val stats = calculator(berlin("2026-11-10T12:00"))
        val result = stats.weeklyAlcohol(
            listOf(entryOf(water, berlin("2026-10-01T12:00")), entryOf(alcoholFreeBeer, berlin("2026-11-05T20:00"))),
            weeks = 2,
        )
        assertEquals(7, result.first().alcoholFreeDays)
        assertEquals(1.6, result.first().grams, delta)
        assertEquals(2, result.last().alcoholFreeDays) // Mo + Di (heute)
    }

    @Test
    fun weeklyBandUsesTheReferenceValues() {
        val stats = calculator(berlin("2026-11-13T12:00"))
        val result = stats.weeklyAlcohol(List(5) { entryOf(pils, berlin("2026-11-09T18:00").plusSeconds(3_600L * it)) }, weeks = 1)
        assertEquals(98.0, result.single().grams, delta)
        assertEquals(AlcoholRiskBand.HIGH_RISK, result.single().band)
    }

    // ---------- today ----------

    @Test
    fun todaySummarizesOnlyTodaysEntries() {
        val stats = calculator(berlin("2026-11-10T18:00"))
        val summary = stats.today(
            listOf(
                entryOf(cola, berlin("2026-11-10T10:00")),
                entryOf(pils, berlin("2026-11-10T17:00")),
                entryOf(alcoholFreeBeer, berlin("2026-11-10T17:30")),
                entryOf(water, berlin("2026-11-10T00:30")),
                entryOf(wine, berlin("2026-11-09T23:59")),
            ),
        )
        assertEquals(48.98, summary.sugarGrams, delta)
        assertEquals(21.2, summary.alcoholGrams, delta)
        assertEquals(1, summary.sugaryDrinks)
        assertEquals(1, summary.alcoholicDrinks)
        assertEquals(1580, summary.totalMilliliters)
        assertEquals(4, summary.drinkCount)
    }

    @Test
    fun todayIsEmptyWithoutEntries() {
        assertEquals(TodaySummary(0.0, 0.0, 0, 0, 0, 0), calculator(berlin("2026-11-10T18:00")).today(emptyList()))
    }

    // ---------- monthComparison ----------

    private val comparisonNow = berlin("2026-11-10T12:00") // letzte 30 Tage: 12.10.–10.11., davor: 12.09.–11.10.

    @Test
    fun monthComparisonInPercent() {
        val result = calculator(comparisonNow).monthComparison(
            listOf(
                entryOf(water, berlin("2026-09-12T08:00")), // Beginn der Aufzeichnung = Beginn des Vorzeitraums
                sugar(60.0, berlin("2026-09-20T12:00")),
                sugar(40.0, berlin("2026-10-11T23:00")), // letzter Tag des Vorzeitraums
                alcohol(berlin("2026-10-01T20:00")),
                sugar(50.0, berlin("2026-10-12T00:30")), // erster Tag des aktuellen Zeitraums
                alcohol(berlin("2026-11-01T20:00")),
                alcohol(berlin("2026-11-10T11:00")),
            ),
        )
        assertEquals(-50.0, assertNotNull(result.sugarChangePercent), delta)
        assertEquals(100.0, assertNotNull(result.alcoholChangePercent), delta)
        assertEquals(50.0, result.sugarGramsLast30Days, delta)
        assertEquals(100.0, result.sugarGramsPrevious30Days, delta)
        assertEquals(39.2, result.alcoholGramsLast30Days, delta)
        assertEquals(19.6, result.alcoholGramsPrevious30Days, delta)
    }

    @Test
    fun monthComparisonNeedsAFullyTrackedPreviousPeriod() {
        val result = calculator(comparisonNow).monthComparison(
            listOf(sugar(60.0, berlin("2026-09-13T12:00")), sugar(30.0, berlin("2026-11-01T12:00"))),
        )
        assertNull(result.sugarChangePercent)
        assertNull(result.alcoholChangePercent)
        assertEquals(30.0, result.sugarGramsLast30Days, delta)
    }

    @Test
    fun monthComparisonWithoutPreviousAlcoholHasNoAlcoholPercent() {
        val result = calculator(comparisonNow).monthComparison(
            listOf(sugar(10.0, berlin("2026-09-01T12:00")), sugar(5.0, berlin("2026-09-30T12:00")), alcohol(berlin("2026-11-01T20:00"))),
        )
        assertNull(result.alcoholChangePercent)
        assertEquals(-100.0, assertNotNull(result.sugarChangePercent), delta)
        assertEquals(19.6, result.alcoholGramsLast30Days, delta)
    }

    @Test
    fun monthComparisonWithoutEntries() {
        val result = calculator(comparisonNow).monthComparison(emptyList())
        assertEquals(MonthComparison(null, null, 0.0, 0.0, 0.0, 0.0), result)
    }

    @Test
    fun monthComparisonUsesCalendarDaysAcrossClockChanges() {
        // Heute 25.10. (25-Stunden-Tag): Vorzeitraum 27.08.–25.09., aktueller Zeitraum 26.09.–25.10.
        val result = calculator(berlin("2026-10-25T12:00")).monthComparison(
            listOf(
                sugar(10.0, berlin("2026-08-27T00:10")),
                sugar(5.0, berlin("2026-09-25T23:50")),
                sugar(30.0, berlin("2026-09-26T00:10")),
            ),
        )
        assertEquals(100.0, assertNotNull(result.sugarChangePercent), delta)
    }

    // ---------- movingAverage4Weeks ----------

    @Test
    fun movingAverageOverTheLast28Days() {
        val entries = listOf(entryOf(water, berlin("2026-10-01T12:00"))) +
            List(4) { entryOf(pils, berlin("2026-10-14T20:00").plusSeconds(86_400L * 7 * it)) } +
            sugar(274.0, berlin("2026-11-10T09:00")) +
            sugar(99.0, berlin("2026-10-13T20:00")) // einen Tag vor dem Fenster
        val result = calculator(berlin("2026-11-10T12:00")).movingAverage4Weeks(entries)
        assertEquals(28, result.coveredDays)
        assertEquals(19.6, assertNotNull(result.alcoholGramsPerWeek), delta) // 78,4 g / 4 Wochen
        assertEquals(10.0, assertNotNull(result.sugarGramsPerDay), delta) // (274 g + 4 × 1,5 g) / 28 Tage
    }

    @Test
    fun movingAverageNeedsSevenTrackedDays() {
        val stats = calculator(berlin("2026-11-10T12:00"))
        assertEquals(MovingAverage(null, null, 6), stats.movingAverage4Weeks(listOf(entryOf(pils, berlin("2026-11-05T20:00")))))
        val sevenDays = stats.movingAverage4Weeks(listOf(entryOf(pils, berlin("2026-11-04T20:00"))))
        assertEquals(7, sevenDays.coveredDays)
        assertEquals(19.6, assertNotNull(sevenDays.alcoholGramsPerWeek), delta)
        assertEquals(1.5 / 7, assertNotNull(sevenDays.sugarGramsPerDay), delta)
        assertEquals(MovingAverage(null, null, 0), stats.movingAverage4Weeks(emptyList()))
    }

    // ---------- alcoholFreeStreak ----------

    @Test
    fun alcoholFreeStreakCountsDaysUpToToday() {
        val stats = calculator(berlin("2026-11-10T20:00"))
        val entries = listOf(
            entryOf(pils, berlin("2026-11-01T20:00")),
            entryOf(pils, berlin("2026-11-06T20:00")),
            entryOf(alcoholFreeBeer, berlin("2026-11-08T20:00")),
            entryOf(water, berlin("2026-11-10T08:00")),
        )
        assertEquals(4, stats.alcoholFreeStreak(entries)) // 07.–10.11.
    }

    @Test
    fun alcoholFreeStreakIsZeroAfterAlcoholToday() {
        val stats = calculator(berlin("2026-11-10T20:00"))
        assertEquals(0, stats.alcoholFreeStreak(listOf(entryOf(water, berlin("2026-11-01T08:00")), entryOf(wine, berlin("2026-11-10T19:00")))))
        assertEquals(0, stats.alcoholFreeStreak(emptyList()))
    }

    @Test
    fun alcoholFreeStreakStartsWithTheFirstEntry() {
        val stats = calculator(berlin("2026-11-10T20:00"))
        assertEquals(3, stats.alcoholFreeStreak(listOf(entryOf(alcoholFreeBeer, berlin("2026-11-08T20:00")))))
    }
}
