package com.drinkwithme.core.health

/** Bereich der wöchentlichen Alkoholmenge nach DGE 2024. Die Bezeichnungen werden genau so angezeigt. */
enum class AlcoholRiskBand(val label: String) {
    /** Unter 27 g pro Woche. */
    LOW_RISK("risikoarm"),

    /** 27 bis 81 g pro Woche. */
    MODERATE_RISK("moderates Risiko"),

    /** Über 81 g pro Woche. */
    HIGH_RISK("riskant"),
}

/**
 * Ein Band im Wochen-Diagramm.
 *
 * @property fromGrams untere Grenze (inklusive bei [AlcoholRiskBand.MODERATE_RISK], sonst ab 0).
 * @property toGrams obere Grenze oder `null` für „offen“.
 */
data class AlcoholBand(val band: AlcoholRiskBand, val fromGrams: Double, val toGrams: Double?)

/** Strichart einer Bezugslinie im Zucker-Diagramm. */
enum class ReferenceLineStyle { SOLID, DASHED }

/** Bezugslinie im Zucker-Diagramm (Gramm pro Tag). */
data class SugarReferenceLine(val grams: Double, val label: String, val style: ReferenceLineStyle)

/**
 * Bezugswerte – nur für gesunde Erwachsene. Bänder sind in der App ausblendbar und werden **nicht grün**
 * dargestellt (keine „Erlaubnis“-Farbe). Formulierungen: keine Diagnose, keine Erlaubnis-Sprache.
 */
object HealthReferences {
    /** Quelle der Alkohol-Bezugswerte. */
    const val ALCOHOL_SOURCE = "DGE 2024"

    /** Unter diesem Wert (g/Woche): „risikoarm“. */
    const val WEEKLY_ALCOHOL_MODERATE_FROM_GRAMS = 27.0

    /** Über diesem Wert (g/Woche): „riskant“. */
    const val WEEKLY_ALCOHOL_HIGH_ABOVE_GRAMS = 81.0

    /** Pflichtsatz bei jeder Darstellung der Alkohol-Bezugswerte. */
    const val ALCOHOL_MANDATORY_NOTE = "Es gibt keine unbedenkliche Menge Alkohol. Weniger ist besser."

    /** Obergrenze für freien Zucker pro Tag in Gramm (durchgezogene Linie). */
    const val DAILY_SUGAR_LIMIT_GRAMS = 50.0
    const val DAILY_SUGAR_LIMIT_LABEL = "Obergrenze für freien Zucker in der gesamten Ernährung (bei 2000 kcal)"

    /** WHO-Wert mit zusätzlichem Nutzen in Gramm pro Tag (gestrichelte Linie). */
    const val DAILY_SUGAR_WHO_GRAMS = 25.0
    const val DAILY_SUGAR_WHO_LABEL = "WHO: unter 25 g bringt zusätzlichen Nutzen"

    /** Hinweis beim Zucker-Diagramm. */
    const val SUGAR_NOTE = "Getränke sind nur ein Teil davon."

    /** Hinweis zum Geltungsbereich aller Bezugswerte. */
    const val ADULTS_ONLY_NOTE = "Die Bezugswerte gelten nur für gesunde Erwachsene."

    /** Info-Text (z. B. Einstellungen, erster Start). */
    const val INFO_TEXT = "Diese App ist ein persönliches Getränketagebuch. Sie stellt keine Diagnose und ersetzt " +
        "keine ärztliche Beratung. Kinder, Jugendliche, Schwangere und Stillende sollten keinen Alkohol trinken."

    /** Bänder für das Wochen-Diagramm, aufsteigend. */
    val weeklyAlcoholBands: List<AlcoholBand> = listOf(
        AlcoholBand(AlcoholRiskBand.LOW_RISK, 0.0, WEEKLY_ALCOHOL_MODERATE_FROM_GRAMS),
        AlcoholBand(AlcoholRiskBand.MODERATE_RISK, WEEKLY_ALCOHOL_MODERATE_FROM_GRAMS, WEEKLY_ALCOHOL_HIGH_ABOVE_GRAMS),
        AlcoholBand(AlcoholRiskBand.HIGH_RISK, WEEKLY_ALCOHOL_HIGH_ABOVE_GRAMS, null),
    )

    /** Bezugslinien für das Zucker-Diagramm: 50 g durchgezogen, 25 g gestrichelt. */
    val dailySugarLines: List<SugarReferenceLine> = listOf(
        SugarReferenceLine(DAILY_SUGAR_LIMIT_GRAMS, DAILY_SUGAR_LIMIT_LABEL, ReferenceLineStyle.SOLID),
        SugarReferenceLine(DAILY_SUGAR_WHO_GRAMS, DAILY_SUGAR_WHO_LABEL, ReferenceLineStyle.DASHED),
    )

    /** Unter 27 g „risikoarm“, 27–81 g „moderates Risiko“, über 81 g „riskant“ (Gramm Alkohol pro Woche Mo–So). */
    fun weeklyAlcoholBand(grams: Double): AlcoholRiskBand = when {
        grams < WEEKLY_ALCOHOL_MODERATE_FROM_GRAMS -> AlcoholRiskBand.LOW_RISK
        grams <= WEEKLY_ALCOHOL_HIGH_ABOVE_GRAMS -> AlcoholRiskBand.MODERATE_RISK
        else -> AlcoholRiskBand.HIGH_RISK
    }
}
