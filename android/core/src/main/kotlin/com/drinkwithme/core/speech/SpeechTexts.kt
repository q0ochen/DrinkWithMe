package com.drinkwithme.core.speech

import com.drinkwithme.core.health.HealthReferences
import com.drinkwithme.core.social.FriendDrinkEvent
import com.drinkwithme.core.stats.MonthComparison
import com.drinkwithme.core.stats.StatisticsCalculator
import com.drinkwithme.core.stats.TodaySummary
import com.drinkwithme.core.stats.WeeklyAlcohol
import java.math.BigDecimal
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

/** Einheiten, die beim Vorlesen ausgeschrieben werden. */
enum class SpeechUnit(val word: String) {
    MILLILITER("Milliliter"),
    LITER("Liter"),
    GRAMM("Gramm"),
    PROZENT("Prozent"),
}

/**
 * **Vorlese-Sätze** – getrennt von den Anzeige-Texten: ganze deutsche Sätze, Zahlen und Einheiten
 * ausgeschrieben („zweihundertfünfzig Milliliter“, „siebenundzwanzig Gramm“), keine Emojis oder Abkürzungen.
 * Keine Diagnose, keine Erlaubnis-Sprache, kein Lob für Alkohol.
 */
object SpeechTexts {
    private const val SOMEONE = "Jemand"
    private const val SOMETHING = "etwas"
    private const val A_DRINK = "Getränk"
    private val MONTHS = listOf(
        "Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember",
    )
    private val WHITESPACE = Regex("\\s+")

    // ---------- Ereignisse und Bestätigungen ----------

    /** „Anna trinkt eine Apfelschorle.“ – mit Menge: „Anna trinkt eine Apfelschorle, fünfhundert Milliliter.“ */
    fun drinkEvent(authorName: String, drinkName: String, volumeMilliliters: Int? = null): String {
        val author = speakable(authorName).ifEmpty { SOMEONE }
        val drink = GermanArticles.withIndefiniteArticle(speakable(drinkName).ifEmpty { SOMETHING })
        val amount = volumeMilliliters?.let { ", ${volume(it)}" } ?: ""
        return "$author trinkt $drink$amount."
    }

    /** Vorlese-Satz für ein Ereignis eines Freundes. */
    fun drinkEvent(event: FriendDrinkEvent, withVolume: Boolean = false): String =
        drinkEvent(event.author.displayName, event.name, event.volumeMilliliters.takeIf { withVolume })

    /** Kurze Ansage nach dem Eintragen: „Apfelschorle eingetragen.“ */
    fun logged(drinkName: String): String = "${speakable(drinkName).ifEmpty { A_DRINK }} eingetragen."

    /** Ansage nach „Rückgängig“: „Apfelschorle wieder entfernt.“ */
    fun undone(drinkName: String): String = "${speakable(drinkName).ifEmpty { A_DRINK }} wieder entfernt."

    /** Rückfrage bei mehrdeutiger Spracheingabe: „Meinst du Apfelschorle oder Apfelsaft?“ */
    fun whichDrink(first: String, second: String): String = "Meinst du ${speakable(first)} oder ${speakable(second)}?"

    /** Spracheingabe ohne Treffer. */
    fun notUnderstood(): String = "Das habe ich leider nicht verstanden. Sag bitte den Namen eines Getränks aus deiner Bar."

    // ---------- Statistik ----------

    /**
     * „Heute aus Getränken: fünfunddreißig Gramm Zucker.“ – bei Alkohol „… Zucker und zwölf Gramm Alkohol.“,
     * danach die Gesamtmenge („Insgesamt eins Komma fünf Liter.“).
     */
    fun today(summary: TodaySummary): String {
        if (summary.drinkCount == 0) return "Heute hast du noch nichts eingetragen."
        val sugar = if (isZeroGrams(summary.sugarGrams)) "kein Zucker" else "${grams(summary.sugarGrams)} Zucker"
        val alcohol = if (isZeroGrams(summary.alcoholGrams)) "" else " und ${grams(summary.alcoholGrams)} Alkohol"
        return "Heute aus Getränken: $sugar$alcohol. Insgesamt ${volume(summary.totalMilliliters)}."
    }

    /**
     * „Diese Woche: zweiundvierzig Gramm Alkohol, vier alkoholfreie Tage.“ – mit Alkohol folgen der Bereich nach
     * der DGE (ausgeschrieben) und der Pflichtsatz.
     *
     * @param today heutiges Datum, um „Diese Woche“ / „Letzte Woche“ / „In der Woche ab dem …“ zu wählen.
     */
    fun weeklyAlcohol(week: WeeklyAlcohol, today: LocalDate): String {
        val currentWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val period = when (week.weekStart) {
            currentWeekStart -> "Diese Woche"
            currentWeekStart.minusWeeks(1) -> "Letzte Woche"
            else -> "In der Woche ab dem ${date(week.weekStart)}"
        }
        val noAlcohol = isZeroGrams(week.grams)
        val alcohol = if (noAlcohol) "kein Alkohol" else "${grams(week.grams)} Alkohol"
        val freeDays = when (week.alcoholFreeDays) {
            0 -> "kein alkoholfreier Tag"
            1 -> "ein alkoholfreier Tag"
            else -> "${GermanNumberWords.cardinal(week.alcoholFreeDays)} alkoholfreie Tage"
        }
        val summary = "$period: $alcohol, $freeDays."
        if (noAlcohol) return summary
        return "$summary Das entspricht dem Bereich ${week.band.label} nach der Deutschen Gesellschaft für Ernährung. " +
            HealthReferences.ALCOHOL_MANDATORY_NOTE
    }

    /**
     * „Vergleich der letzten dreißig Tage mit den dreißig Tagen davor: Zucker aus Getränken zwanzig Prozent weniger,
     * Alkohol fünf Prozent mehr.“ Ohne Vergleichsbasis: „noch ohne Vergleichswert“.
     */
    fun monthComparison(comparison: MonthComparison): String {
        val days = GermanNumberWords.cardinal(StatisticsCalculator.COMPARISON_DAYS)
        return "Vergleich der letzten $days Tage mit den $days Tagen davor: " +
            "Zucker aus Getränken ${change(comparison.sugarChangePercent)}, " +
            "Alkohol ${change(comparison.alcoholChangePercent)}."
    }

    // ---------- Bausteine ----------

    /** Zahl mit Einheit: „zweihundertfünfzig Milliliter“, „ein Gramm“, „eins Komma fünf Liter“. */
    fun quantity(value: Double, unit: SpeechUnit, maxFractionDigits: Int = 1): String {
        val isOne = GermanNumberWords.round(value, maxFractionDigits).compareTo(BigDecimal.ONE) == 0
        val number = if (isOne) "ein" else GermanNumberWords.decimal(value, maxFractionDigits)
        return "$number ${unit.word}"
    }

    fun quantity(value: Int, unit: SpeechUnit): String = quantity(value.toDouble(), unit, maxFractionDigits = 0)

    /** Menge: unter 1000 ml in Millilitern, sonst in Litern auf eine Nachkommastelle („eins Komma fünf Liter“). */
    fun volume(milliliters: Int): String =
        if (milliliters < 1000) {
            quantity(milliliters, SpeechUnit.MILLILITER)
        } else {
            quantity(((milliliters + 50) / 100) / 10.0, SpeechUnit.LITER)
        }

    /** Gramm: ab 10 g ganzzahlig („siebenundzwanzig Gramm“), darunter mit einer Nachkommastelle. */
    fun grams(value: Double): String = quantity(value, SpeechUnit.GRAMM, gramDigits(value))

    /** Entfernt Emojis, Symbole und Steuerzeichen aus Namen, damit die Sprachausgabe sie nicht vorliest. */
    fun speakable(text: String): String {
        val composed = Normalizer.normalize(text, Normalizer.Form.NFC)
        val cleaned = buildString(composed.length) {
            var index = 0
            while (index < composed.length) {
                val codePoint = composed.codePointAt(index)
                if (isUnspeakable(codePoint)) append(' ') else appendCodePoint(codePoint)
                index += Character.charCount(codePoint)
            }
        }
        return cleaned.replace(WHITESPACE, " ").trim()
    }

    private fun isUnspeakable(codePoint: Int): Boolean = when (Character.getType(codePoint).toByte()) {
        Character.OTHER_SYMBOL, Character.MODIFIER_SYMBOL, Character.FORMAT, Character.PRIVATE_USE,
        Character.SURROGATE, Character.UNASSIGNED, Character.CONTROL, Character.NON_SPACING_MARK,
        Character.ENCLOSING_MARK,
        -> true
        else -> false
    }

    private fun change(percent: Double?): String {
        if (percent == null) return "noch ohne Vergleichswert"
        val rounded = GermanNumberWords.round(percent, 0)
        if (rounded.signum() == 0) return "etwa gleich viel"
        val amount = quantity(abs(percent), SpeechUnit.PROZENT, maxFractionDigits = 0)
        return if (rounded.signum() < 0) "$amount weniger" else "$amount mehr"
    }

    private fun date(day: LocalDate): String = "${GermanNumberWords.ordinalDative(day.dayOfMonth)} ${MONTHS[day.monthValue - 1]}"

    private fun gramDigits(value: Double): Int = if (abs(value) >= 10.0) 0 else 1

    private fun isZeroGrams(value: Double): Boolean = GermanNumberWords.round(value, gramDigits(value)).signum() == 0
}
