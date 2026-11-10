package com.drinkwithme.app.share

import com.drinkwithme.app.stats.GermanFormat
import com.drinkwithme.app.stats.HistoryOverview
import com.drinkwithme.app.stats.HistoryTexts
import com.drinkwithme.core.health.HealthReferences
import com.drinkwithme.core.stats.DailySugar
import com.drinkwithme.core.stats.WeeklyAlcohol

/** Inhalt des Bildes „Als Bild teilen“ – unabhängig vom Zeichnen, damit er testbar ist. */
data class StatisticsCard(
    val title: String,
    val dateLine: String,
    val keyFigures: List<String>,
    val sugarDays: List<DailySugar>,
    val alcoholWeeks: List<WeeklyAlcohol>,
    val showAlcoholBands: Boolean,
    val notes: List<String>,
    val footer: String,
    /** Textfassung des Bildes (Vorschau für Screenreader). */
    val description: String,
)

object StatisticsCardFactory {
    const val TITLE = "Mein Getränke-Verlauf"
    const val FOOTER = "Erstellt mit Drink With Me – persönliches Getränketagebuch"

    fun create(overview: HistoryOverview, showAlcoholBands: Boolean): StatisticsCard {
        val keyFigures = buildList {
            add("Heute: ${GermanFormat.grams(overview.today.sugarGrams)} Zucker aus Getränken")
            overview.currentWeek?.let { add("Diese Woche: ${GermanFormat.grams(it.grams)} Alkohol") }
            add(HistoryTexts.alcoholFreeDaysThisWeek(overview.currentWeek))
            HistoryTexts.streak(overview.alcoholFreeStreak)?.let(::add)
        }
        val notes = listOf(
            HealthReferences.ALCOHOL_MANDATORY_NOTE,
            "Zucker-Linien: ${GermanFormat.grams(HealthReferences.DAILY_SUGAR_LIMIT_GRAMS)} durchgezogen, " +
                "${GermanFormat.grams(HealthReferences.DAILY_SUGAR_WHO_GRAMS)} gestrichelt. ${HealthReferences.SUGAR_NOTE}",
            HealthReferences.ADULTS_ONLY_NOTE,
        )
        val dateLine = "Stand: ${GermanFormat.fullDate(overview.todayDate)}"
        val description = (
            listOf(TITLE, dateLine) + keyFigures +
                HistoryTexts.sugarChartDescription(overview.dailySugar) +
                HistoryTexts.alcoholChartDescription(overview.weeklyAlcohol, showAlcoholBands)
            ).joinToString(" ") { sentence -> sentence.trim().let { if (it.endsWith(".")) it else "$it." } }
        return StatisticsCard(
            title = TITLE,
            dateLine = dateLine,
            keyFigures = keyFigures,
            sugarDays = overview.dailySugar,
            alcoholWeeks = overview.weeklyAlcohol,
            showAlcoholBands = showAlcoholBands,
            notes = notes,
            footer = FOOTER,
            description = description,
        )
    }
}
