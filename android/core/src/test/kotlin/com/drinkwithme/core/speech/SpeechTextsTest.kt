package com.drinkwithme.core.speech

import com.drinkwithme.core.bar.DrinkCategory
import com.drinkwithme.core.bar.DrinkIcon
import com.drinkwithme.core.social.FriendDrinkEvent
import com.drinkwithme.core.social.UserProfile
import com.drinkwithme.core.stats.MonthComparison
import com.drinkwithme.core.stats.TodaySummary
import com.drinkwithme.core.stats.WeeklyAlcohol
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SpeechTextsTest {
    private val anna = UserProfile("u-1", "Anna", null)
    private val event = FriendDrinkEvent(
        "e-1", anna, "Apfelschorle", DrinkIcon.APPLE_SPRITZER, DrinkCategory.JUICE, 500, Instant.parse("2026-11-10T17:00:00Z"),
    )

    @Test
    fun drinkEventSentences() {
        assertEquals("Anna trinkt eine Apfelschorle.", SpeechTexts.drinkEvent(event))
        assertEquals("Anna trinkt eine Apfelschorle, fünfhundert Milliliter.", SpeechTexts.drinkEvent(event, withVolume = true))
        assertEquals("Anna trinkt einen Kaffee.", SpeechTexts.drinkEvent("Anna", "Kaffee"))
        assertEquals("Anna trinkt ein Wasser.", SpeechTexts.drinkEvent("Anna", "Wasser"))
        assertEquals("Anna trinkt ein Pils.", SpeechTexts.drinkEvent("Anna", "Pils"))
        assertEquals("Ben trinkt einen Rotwein trocken.", SpeechTexts.drinkEvent("Ben", "Rotwein trocken"))
        assertEquals("Ben trinkt eine Cola zero.", SpeechTexts.drinkEvent("Ben", "Cola zero"))
        assertEquals("Ben trinkt Kombucha.", SpeechTexts.drinkEvent("Ben", "Kombucha"))
        assertEquals(
            "Anna trinkt ein Wasser, eins Komma fünf Liter.",
            SpeechTexts.drinkEvent("Anna", "Wasser", volumeMilliliters = 1500),
        )
    }

    @Test
    fun drinkEventWithoutEmojisAndWithFallbacks() {
        assertEquals("Anna trinkt ein Bier.", SpeechTexts.drinkEvent("Anna 🌹", "Bier 🍺️"))
        assertEquals("Jemand trinkt eine Apfelschorle.", SpeechTexts.drinkEvent("  ", "Apfelschorle"))
        assertEquals("Anna trinkt etwas.", SpeechTexts.drinkEvent("Anna", "🥤"))
    }

    @Test
    fun confirmations() {
        assertEquals("Apfelschorle eingetragen.", SpeechTexts.logged("Apfelschorle"))
        assertEquals("Apfelschorle wieder entfernt.", SpeechTexts.undone("Apfelschorle"))
        assertEquals("Meinst du Apfelschorle oder Apfelsaft?", SpeechTexts.whichDrink("Apfelschorle", "Apfelsaft"))
        assertEquals("Getränk eingetragen.", SpeechTexts.logged("🍺"))
        assertEquals("Das habe ich leider nicht verstanden. Sag bitte den Namen eines Getränks aus deiner Bar.", SpeechTexts.notUnderstood())
    }

    @Test
    fun todaySummary() {
        assertEquals(
            "Heute aus Getränken: fünfunddreißig Gramm Zucker. Insgesamt ein Liter.",
            SpeechTexts.today(TodaySummary(35.2, 0.0, 1, 0, 1000, 3)),
        )
        assertEquals(
            "Heute aus Getränken: neunundvierzig Gramm Zucker und einundzwanzig Gramm Alkohol. Insgesamt eins Komma sechs Liter.",
            SpeechTexts.today(TodaySummary(48.98, 21.2, 1, 1, 1580, 4)),
        )
        assertEquals(
            "Heute aus Getränken: zwei Komma fünf Gramm Zucker und eins Komma sechs Gramm Alkohol. Insgesamt fünfhundert Milliliter.",
            SpeechTexts.today(TodaySummary(2.5, 1.6, 0, 0, 500, 1)),
        )
        assertEquals(
            "Heute aus Getränken: kein Zucker. Insgesamt zweihundertfünfzig Milliliter.",
            SpeechTexts.today(TodaySummary(0.0, 0.0, 0, 0, 250, 1)),
        )
        assertEquals("Heute hast du noch nichts eingetragen.", SpeechTexts.today(TodaySummary(0.0, 0.0, 0, 0, 0, 0)))
    }

    @Test
    fun weeklyAlcohol() {
        val today = LocalDate.of(2026, 11, 20) // Freitag, Woche ab 16.11.
        assertEquals(
            "Diese Woche: zweiundvierzig Gramm Alkohol, vier alkoholfreie Tage. Das entspricht dem Bereich moderates " +
                "Risiko nach der Deutschen Gesellschaft für Ernährung. Es gibt keine unbedenkliche Menge Alkohol. Weniger ist besser.",
            SpeechTexts.weeklyAlcohol(WeeklyAlcohol(LocalDate.of(2026, 11, 16), 42.0, 4), today),
        )
        assertEquals(
            "Letzte Woche: kein Alkohol, sieben alkoholfreie Tage.",
            SpeechTexts.weeklyAlcohol(WeeklyAlcohol(LocalDate.of(2026, 11, 9), 0.0, 7), today),
        )
        assertEquals(
            "In der Woche ab dem zweiten November: zwanzig Gramm Alkohol, ein alkoholfreier Tag. Das entspricht " +
                "dem Bereich risikoarm nach der Deutschen Gesellschaft für Ernährung. Es gibt keine unbedenkliche Menge " +
                "Alkohol. Weniger ist besser.",
            SpeechTexts.weeklyAlcohol(WeeklyAlcohol(LocalDate.of(2026, 11, 2), 19.6, 1), today),
        )
        assertEquals(
            "In der Woche ab dem einunddreißigsten August: einhundert Gramm Alkohol, kein alkoholfreier Tag. Das entspricht " +
                "dem Bereich riskant nach der Deutschen Gesellschaft für Ernährung. Es gibt keine unbedenkliche Menge " +
                "Alkohol. Weniger ist besser.",
            SpeechTexts.weeklyAlcohol(WeeklyAlcohol(LocalDate.of(2026, 8, 31), 100.0, 0), today),
        )
    }

    @Test
    fun monthComparison() {
        assertEquals(
            "Vergleich der letzten dreißig Tage mit den dreißig Tagen davor: Zucker aus Getränken zwanzig Prozent weniger, " +
                "Alkohol fünf Prozent mehr.",
            SpeechTexts.monthComparison(MonthComparison(-20.0, 5.2, 80.0, 100.0, 21.0, 20.0)),
        )
        assertEquals(
            "Vergleich der letzten dreißig Tage mit den dreißig Tagen davor: Zucker aus Getränken noch ohne Vergleichswert, " +
                "Alkohol etwa gleich viel.",
            SpeechTexts.monthComparison(MonthComparison(null, 0.3, 0.0, 0.0, 20.0, 20.0)),
        )
        assertEquals(
            "Vergleich der letzten dreißig Tage mit den dreißig Tagen davor: Zucker aus Getränken ein Prozent mehr, " +
                "Alkohol einhundert Prozent weniger.",
            SpeechTexts.monthComparison(MonthComparison(1.0, -100.0, 0.0, 0.0, 0.0, 0.0)),
        )
    }

    @Test
    fun quantitiesAndUnitsAreWrittenOut() {
        assertEquals("zweihundertfünfzig Milliliter", SpeechTexts.quantity(250, SpeechUnit.MILLILITER))
        assertEquals("siebenundzwanzig Gramm", SpeechTexts.grams(27.0))
        assertEquals("ein Gramm", SpeechTexts.grams(1.0))
        assertEquals("null Komma vier Gramm", SpeechTexts.grams(0.4))
        assertEquals("zehn Gramm", SpeechTexts.grams(9.96))
        assertEquals("zwanzig Gramm", SpeechTexts.grams(19.6))
        assertEquals("zwei Komma fünf Liter", SpeechTexts.quantity(2.5, SpeechUnit.LITER))
        assertEquals("ein Milliliter", SpeechTexts.volume(1))
        assertEquals("ein Liter", SpeechTexts.volume(1000))
        assertEquals("eins Komma drei Liter", SpeechTexts.volume(1250))
        assertEquals("zwei Liter", SpeechTexts.volume(2000))
        assertEquals("fünf Prozent", SpeechTexts.quantity(5, SpeechUnit.PROZENT))
    }

    @Test
    fun spokenTextsContainNoDigitsAbbreviationsOrEmojis() {
        val texts = listOf(
            SpeechTexts.drinkEvent(event, withVolume = true),
            SpeechTexts.today(TodaySummary(48.98, 21.2, 1, 1, 1580, 4)),
            SpeechTexts.weeklyAlcohol(WeeklyAlcohol(LocalDate.of(2026, 11, 2), 19.6, 1), LocalDate.of(2026, 11, 20)),
            SpeechTexts.monthComparison(MonthComparison(-12.0, 3.0, 1.0, 1.0, 1.0, 1.0)),
        )
        texts.forEach { text ->
            assertFalse(text.any { it.isDigit() }, text)
            listOf(" ml", " g ", " l ", "DGE", "%", "Vol.").forEach { assertFalse(it in text, "$it in $text") }
            assertNull(text.codePoints().toArray().firstOrNull { Character.getType(it) == Character.OTHER_SYMBOL.toInt() })
        }
    }

    @Test
    fun speakableRemovesSymbols() {
        assertEquals("Bier", SpeechTexts.speakable("🍺 Bier ‍"))
        assertEquals("Club-Mate", SpeechTexts.speakable("  Club-Mate  "))
        assertEquals("Äpfel", SpeechTexts.speakable("Äpfel"))
    }
}
