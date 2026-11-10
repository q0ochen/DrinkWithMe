package com.drinkwithme.app.stats

import com.drinkwithme.core.health.AlcoholRiskBand
import com.drinkwithme.core.health.HealthReferences
import com.drinkwithme.core.health.alcoholGrams
import com.drinkwithme.core.health.isAlcoholic
import com.drinkwithme.core.health.sugarGrams
import com.drinkwithme.core.log.DrinkEntry
import com.drinkwithme.core.sharing.VolumeFormatter
import com.drinkwithme.core.stats.DailySugar
import com.drinkwithme.core.stats.MonthComparison
import com.drinkwithme.core.stats.MovingAverage
import com.drinkwithme.core.stats.StatisticsCalculator
import com.drinkwithme.core.stats.TodaySummary
import com.drinkwithme.core.stats.WeeklyAlcohol
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Alles, was der Verlauf zeigt – einmal aus der Historie berechnet. */
data class HistoryOverview(
    val today: TodaySummary,
    val dailySugar: List<DailySugar>,
    val weeklyAlcohol: List<WeeklyAlcohol>,
    val comparison: MonthComparison,
    val average: MovingAverage,
    val alcoholFreeStreak: Int,
    val todayDate: LocalDate,
) {
    /** Die laufende Woche (Montag bis heute). */
    val currentWeek: WeeklyAlcohol? get() = weeklyAlcohol.lastOrNull()

    companion object {
        fun from(calculator: StatisticsCalculator, entries: List<DrinkEntry>, todayDate: LocalDate) = HistoryOverview(
            today = calculator.today(entries),
            dailySugar = calculator.dailySugar(entries),
            weeklyAlcohol = calculator.weeklyAlcohol(entries),
            comparison = calculator.monthComparison(entries),
            average = calculator.movingAverage4Weeks(entries),
            alcoholFreeStreak = calculator.alcoholFreeStreak(entries),
            todayDate = todayDate,
        )
    }
}

/** Deutsche Anzeige-Formate (mit Ziffern und Einheitenkürzeln; Vorlese-Sätze kommen aus `SpeechTexts`). */
object GermanFormat {
    private val locale: Locale = Locale.GERMANY
    private val dayMonth = DateTimeFormatter.ofPattern("d.M.", locale)
    private val fullDate = DateTimeFormatter.ofPattern("d. MMMM yyyy", locale)
    private val time = DateTimeFormatter.ofPattern("HH:mm", locale)

    /** „12 g“ ab 10 g, darunter „2,5 g“; „0 g“ statt „0,0 g“. */
    fun grams(value: Double): String {
        val rounded = if (abs(value) >= 10.0) value.roundToInt().toString() else oneDecimal(value)
        return "$rounded g"
    }

    fun milliliters(value: Int): String = VolumeFormatter.format(value)

    fun percent(value: Double): String = "${abs(value).roundToInt()} %"

    /** „Mo. 3.11.“ */
    fun shortDay(date: LocalDate): String =
        "${date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).trimEnd('.')}. ${date.format(dayMonth)}"

    /** „3.11.“ */
    fun dayMonth(date: LocalDate): String = date.format(dayMonth)

    /** „10. November 2026“ */
    fun fullDate(date: LocalDate): String = date.format(fullDate)

    fun time(instant: Instant, zone: ZoneId): String = instant.atZone(zone).format(time)

    private fun oneDecimal(value: Double): String {
        val tenths = (value * 10).roundToInt()
        if (tenths % 10 == 0) return (tenths / 10).toString()
        val sign = if (tenths < 0) "-" else ""
        return "$sign${abs(tenths) / 10},${abs(tenths) % 10}"
    }
}

/**
 * Texte des Verlaufs – auch als Textfassung der Diagramme für Screenreader und TV. Keine Diagnose, keine
 * Erlaubnis-Sprache, Lob nur für alkoholfreie Tage.
 */
object HistoryTexts {
    const val TITLE = "Verlauf"
    const val SUGAR_CHART_TITLE = "Zucker aus Getränken pro Tag"
    const val ALCOHOL_CHART_TITLE = "Alkohol pro Woche (Montag bis Sonntag)"

    fun todayLines(today: TodaySummary): List<String> {
        if (today.drinkCount == 0) return listOf("Heute noch nichts eingetragen.")
        return listOf(
            "Zucker aus Getränken: ${GermanFormat.grams(today.sugarGrams)}",
            "Alkohol: ${GermanFormat.grams(today.alcoholGrams)}",
            "${drinks(today.drinkCount)}, zusammen ${GermanFormat.milliliters(today.totalMilliliters)}",
            "davon zuckerhaltig: ${today.sugaryDrinks}, alkoholisch: ${today.alcoholicDrinks}",
        )
    }

    /** Nur positive Serie wird erwähnt. */
    fun streak(days: Int): String? = when {
        days <= 0 -> null
        days == 1 -> "1 alkoholfreier Tag in Folge"
        else -> "$days alkoholfreie Tage in Folge"
    }

    fun alcoholFreeDaysThisWeek(week: WeeklyAlcohol?): String {
        val count = week?.alcoholFreeDays ?: 0
        return if (count == 1) "Alkoholfreie Tage diese Woche: 1" else "Alkoholfreie Tage diese Woche: $count"
    }

    fun comparisonLines(comparison: MonthComparison): List<String> = listOf(
        "Zucker aus Getränken: ${change(comparison.sugarChangePercent)}",
        "Alkohol: ${change(comparison.alcoholChangePercent)}",
    )

    fun averageLines(average: MovingAverage): List<String> {
        val alcohol = average.alcoholGramsPerWeek
        val sugar = average.sugarGramsPerDay
        if (alcohol == null || sugar == null) {
            return listOf("Ab ${StatisticsCalculator.MOVING_AVERAGE_MIN_DAYS} erfassten Tagen verfügbar (bisher ${average.coveredDays}).")
        }
        return listOf(
            "Alkohol: ${GermanFormat.grams(alcohol)} pro Woche",
            "Zucker aus Getränken: ${GermanFormat.grams(sugar)} pro Tag",
        )
    }

    fun sugarDayRow(day: DailySugar): String = "${GermanFormat.shortDay(day.date)}: ${GermanFormat.grams(day.grams)} Zucker"

    fun sugarChartDescription(days: List<DailySugar>): String {
        if (days.isEmpty()) return "$SUGAR_CHART_TITLE: keine Daten."
        val max = days.maxBy { it.grams }
        val over = days.count { it.grams > HealthReferences.DAILY_SUGAR_LIMIT_GRAMS }
        return "$SUGAR_CHART_TITLE, letzte ${days.size} Tage. Höchster Wert: ${GermanFormat.grams(max.grams)} " +
            "(${GermanFormat.shortDay(max.date)}). ${daysOver(over)} über ${GermanFormat.grams(HealthReferences.DAILY_SUGAR_LIMIT_GRAMS)}. " +
            "Durchgezogene Linie bei ${GermanFormat.grams(HealthReferences.DAILY_SUGAR_LIMIT_GRAMS)}: " +
            "${HealthReferences.DAILY_SUGAR_LIMIT_LABEL}. Gestrichelte Linie bei " +
            "${GermanFormat.grams(HealthReferences.DAILY_SUGAR_WHO_GRAMS)}: ${HealthReferences.DAILY_SUGAR_WHO_LABEL}."
    }

    fun weekLabel(week: WeeklyAlcohol): String = "ab ${GermanFormat.dayMonth(week.weekStart)}"

    fun alcoholWeekRow(week: WeeklyAlcohol, showBands: Boolean): String {
        val free = if (week.alcoholFreeDays == 1) "1 alkoholfreier Tag" else "${week.alcoholFreeDays} alkoholfreie Tage"
        val band = if (showBands && week.grams > 0.0) " – ${week.band.label}" else ""
        return "Woche ${weekLabel(week)}: ${GermanFormat.grams(week.grams)} Alkohol, $free$band"
    }

    fun alcoholChartDescription(weeks: List<WeeklyAlcohol>, showBands: Boolean): String {
        if (weeks.isEmpty()) return "$ALCOHOL_CHART_TITLE: keine Daten."
        val bands = if (showBands) {
            val low = GermanFormat.grams(HealthReferences.WEEKLY_ALCOHOL_MODERATE_FROM_GRAMS)
            val high = GermanFormat.grams(HealthReferences.WEEKLY_ALCOHOL_HIGH_ABOVE_GRAMS)
            " Bereiche nach ${HealthReferences.ALCOHOL_SOURCE}: unter $low ${AlcoholRiskBand.LOW_RISK.label}, " +
                "$low bis $high ${AlcoholRiskBand.MODERATE_RISK.label}, über $high ${AlcoholRiskBand.HIGH_RISK.label}."
        } else {
            ""
        }
        return "$ALCOHOL_CHART_TITLE, letzte ${weeks.size} Wochen. Diese Woche ${GermanFormat.grams(weeks.last().grams)}.$bands " +
            HealthReferences.ALCOHOL_MANDATORY_NOTE
    }

    /** Zeile in der Eintragsliste, auch als Beschreibung für Screenreader. */
    fun entryDescription(entry: DrinkEntry, zone: ZoneId): String {
        val parts = mutableListOf(
            "${entry.name}, ${GermanFormat.milliliters(entry.volumeMilliliters)}",
            "um ${GermanFormat.time(entry.consumedAt, zone)} Uhr",
            "${GermanFormat.grams(entry.sugarGrams)} Zucker",
        )
        if (entry.isAlcoholic || entry.alcoholGrams > 0.0) parts += "${GermanFormat.grams(entry.alcoholGrams)} Alkohol"
        parts += if (entry.shareWithFriends) "mit Freunden geteilt" else "privat"
        return parts.joinToString(", ")
    }

    /** Überschrift einer Tagesgruppe in der Liste: „Heute“, „Gestern“, sonst „Mo. 3.11.“ */
    fun dayHeading(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Heute"
        today.minusDays(1) -> "Gestern"
        else -> GermanFormat.shortDay(date)
    }

    fun isCurrentWeek(week: WeeklyAlcohol, today: LocalDate): Boolean =
        week.weekStart == today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun daysOver(count: Int): String = when (count) {
        0 -> "An keinem Tag"
        1 -> "An 1 Tag"
        else -> "An $count Tagen"
    }

    private fun drinks(count: Int): String = if (count == 1) "1 Getränk" else "$count Getränke"

    private fun change(percent: Double?): String {
        if (percent == null) return "noch ohne Vergleichswert"
        val rounded = percent.roundToInt()
        return when {
            rounded == 0 -> "etwa gleich viel wie in den 30 Tagen davor"
            rounded < 0 -> "${GermanFormat.percent(percent)} weniger als in den 30 Tagen davor"
            else -> "${GermanFormat.percent(percent)} mehr als in den 30 Tagen davor"
        }
    }
}
