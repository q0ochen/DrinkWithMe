package com.drinkwithme.app.misc

import com.drinkwithme.app.APFELSCHORLE
import com.drinkwithme.app.MutableClock
import com.drinkwithme.app.PILS
import com.drinkwithme.app.barItem
import com.drinkwithme.app.config.ServerConfig
import com.drinkwithme.app.entry
import com.drinkwithme.app.share.ShareImageFiles
import com.drinkwithme.app.share.StatisticsCardFactory
import com.drinkwithme.app.speech.AnnouncementPolicy
import com.drinkwithme.app.speech.GermanVoiceSelection
import com.drinkwithme.app.speech.SpeakerStatus
import com.drinkwithme.app.stats.ChartScale
import com.drinkwithme.app.stats.GermanFormat
import com.drinkwithme.app.stats.HistoryOverview
import com.drinkwithme.app.stats.HistoryTexts
import com.drinkwithme.app.voice.VoiceRequestFactory
import com.drinkwithme.core.health.HealthReferences
import com.drinkwithme.core.stats.MonthComparison
import com.drinkwithme.core.stats.MovingAverage
import com.drinkwithme.core.stats.StatisticsCalculator
import com.drinkwithme.core.stats.TodaySummary
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerConfigTest {
    private val vapid = "B" + "a".repeat(86)

    @Test
    fun missingValuesMeanNotConfigured() {
        assertNull(ServerConfig.parse("", "key", vapid, ""))
        assertNull(ServerConfig.parse("https://x.supabase.co", " ", vapid, ""))
        assertNull(ServerConfig.parse("http://x.supabase.co", "key", vapid, ""), "nur https")
    }

    @Test
    fun parsesAndNormalizes() {
        val config = ServerConfig.parse(" https://abc.supabase.co/ ", " key ", "$vapid=", "")!!
        assertEquals("https://abc.supabase.co", config.supabaseUrl)
        assertEquals("key", config.anonKey)
        assertEquals(vapid, config.vapidPublicKey)
        assertEquals(ServerConfig.DEFAULT_WEB_APP_URL, config.webAppUrl)
        assertNull(ServerConfig.parse("https://abc.supabase.co", "key", "zu-kurz", "https://web.example/")!!.vapidPublicKey)
        assertEquals("https://web.example", ServerConfig.parse("https://abc.supabase.co", "key", "", "https://web.example/")!!.webAppUrl)
    }
}

class VoiceRequestFactoryTest {
    @Test
    fun usesGermanFreeFormFiveResultsWithBiasingFromApi33() {
        val bar = listOf(APFELSCHORLE, PILS, barItem(id = "x", name = "pils"))
        val modern = VoiceRequestFactory.create(bar, sdkInt = 34)
        assertEquals("de-DE", modern.language)
        assertEquals(5, modern.maxResults)
        assertEquals(listOf("Apfelschorle", "Schorle", "Pils", "Bier"), modern.biasingStrings, "Namen und Aliase, ohne Doppelte")
        assertTrue(VoiceRequestFactory.create(bar, sdkInt = 32).biasingStrings.isEmpty(), "Biasing erst ab Android 13")
    }
}

class ShareImageFilesTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun namesFilesByDateAndKeepsOnlyTheNewest() {
        assertEquals("drink-with-me-statistik-2026-11-10.png", ShareImageFiles.fileName(LocalDate.of(2026, 11, 10)))
        val files = ShareImageFiles(folder.root)
        files.newImageFile(LocalDate.of(2026, 11, 9)).writeText("alt")
        val newest = files.newImageFile(LocalDate.of(2026, 11, 10))
        newest.writeText("neu")
        assertEquals(listOf(newest.name), files.directory.listFiles()!!.map { it.name })
        assertEquals("shared_images", newest.parentFile!!.name, "nur dieser Ordner ist per FileProvider freigegeben")
    }
}

class SpeechPolicyTest {
    @Test
    fun screenReaderSuppressesOnlyUnrequestedSpeech() {
        assertTrue(AnnouncementPolicy.shouldSpeak(screenReaderActive = false, explicitRequest = false))
        assertFalse(AnnouncementPolicy.shouldSpeak(screenReaderActive = true, explicitRequest = false))
        assertTrue(AnnouncementPolicy.shouldSpeak(screenReaderActive = true, explicitRequest = true))
    }

    @Test
    fun germanVoiceSelectionFallsBackAndReportsMissingData() {
        assertEquals(SpeakerStatus.READY, GermanVoiceSelection.status(1, null))
        assertEquals(SpeakerStatus.READY, GermanVoiceSelection.status(-2, 0), "Rückfall auf Locale.GERMAN")
        assertEquals(SpeakerStatus.MISSING_DATA, GermanVoiceSelection.status(-1, -2))
        assertEquals(SpeakerStatus.NO_GERMAN_VOICE, GermanVoiceSelection.status(-2, -2))
    }
}

class StatisticsPresentationTest {
    @Test
    fun germanNumbers() {
        assertEquals("35 g", GermanFormat.grams(35.4))
        assertEquals("2,5 g", GermanFormat.grams(2.5))
        assertEquals("0 g", GermanFormat.grams(0.04))
        assertEquals("1,5 l", GermanFormat.milliliters(1500))
        assertEquals("20 %", GermanFormat.percent(-19.6))
        assertEquals("Di. 10.11.", GermanFormat.shortDay(LocalDate.of(2026, 11, 10)))
        assertEquals("10. November 2026", GermanFormat.fullDate(LocalDate.of(2026, 11, 10)))
    }

    @Test
    fun todayAndTrendTexts() {
        assertEquals(listOf("Heute noch nichts eingetragen."), HistoryTexts.todayLines(TodaySummary(0.0, 0.0, 0, 0, 0, 0)))
        val lines = HistoryTexts.todayLines(TodaySummary(35.0, 19.6, 1, 1, 1000, 2))
        assertEquals("Zucker aus Getränken: 35 g", lines[0])
        assertEquals("2 Getränke, zusammen 1 l", lines[2])
        assertNull(HistoryTexts.streak(0), "keine Serie, kein Text")
        assertEquals("3 alkoholfreie Tage in Folge", HistoryTexts.streak(3))
        val comparison = HistoryTexts.comparisonLines(MonthComparison(-20.0, null, 1.0, 2.0, 0.0, 0.0))
        assertEquals("Zucker aus Getränken: 20 % weniger als in den 30 Tagen davor", comparison[0])
        assertEquals("Alkohol: noch ohne Vergleichswert", comparison[1])
        assertEquals("Ab 7 erfassten Tagen verfügbar (bisher 3).", HistoryTexts.averageLines(MovingAverage(null, null, 3)).single())
    }

    @Test
    fun chartsHaveTextAlternativesWithReferenceLines() {
        val clock = MutableClock(Instant.parse("2026-11-10T20:00:00Z"))
        val entries = listOf(entry(id = "a", sugar = 10.6, volume = 500, at = Instant.parse("2026-11-10T10:00:00Z")))
        val calculator = StatisticsCalculator(clock, ZoneOffset.UTC)
        val sugar = HistoryTexts.sugarChartDescription(calculator.dailySugar(entries))
        assertTrue(sugar.contains("Höchster Wert: 53 g (Di. 10.11.)"))
        assertTrue(sugar.contains("An 1 Tag über 50 g"))
        assertTrue(sugar.contains(HealthReferences.DAILY_SUGAR_WHO_LABEL))
        val alcohol = HistoryTexts.alcoholChartDescription(calculator.weeklyAlcohol(entries), showBands = true)
        assertTrue(alcohol.contains("risikoarm") && alcohol.endsWith(HealthReferences.ALCOHOL_MANDATORY_NOTE))
        assertFalse(HistoryTexts.alcoholChartDescription(calculator.weeklyAlcohol(entries), showBands = false).contains("riskant"))
    }

    @Test
    fun entryDescriptionsMentionSharing() {
        val text = HistoryTexts.entryDescription(entry(name = "Pils", alcohol = 4.9, sugar = 0.3, shared = true), ZoneOffset.UTC)
        assertEquals("Pils, 500 ml, um 11:00 Uhr, 1,5 g Zucker, 20 g Alkohol, mit Freunden geteilt", text)
        assertTrue(HistoryTexts.entryDescription(entry(), ZoneOffset.UTC).endsWith("privat"))
    }

    @Test
    fun chartScaleLeavesRoomForReferenceLines() {
        assertEquals(60.0, ChartScale.upperBound(listOf(10.0), listOf(50.0, 25.0)))
        assertEquals(10.0, ChartScale.upperBound(emptyList(), emptyList()))
        assertEquals(0.5f, ChartScale.fraction(30.0, 60.0))
        assertEquals(1f, ChartScale.fraction(100.0, 60.0))
        assertEquals(0f, ChartScale.fraction(Double.NaN, 60.0))
    }

    @Test
    fun shareCardContainsKeyFiguresNotesAndDescription() {
        val clock = MutableClock(Instant.parse("2026-11-10T20:00:00Z"))
        val entries = listOf(entry(at = Instant.parse("2026-11-10T10:00:00Z")))
        val overview = HistoryOverview.from(StatisticsCalculator(clock, ZoneOffset.UTC), entries, LocalDate.of(2026, 11, 10))
        val card = StatisticsCardFactory.create(overview, showAlcoholBands = true)
        assertEquals("Mein Getränke-Verlauf", card.title)
        assertEquals("Stand: 10. November 2026", card.dateLine)
        assertEquals("Heute: 28 g Zucker aus Getränken", card.keyFigures.first())
        assertTrue(card.notes.contains(HealthReferences.ALCOHOL_MANDATORY_NOTE))
        assertTrue(card.description.contains("Zucker aus Getränken pro Tag"))
        assertFalse(card.description.contains(".."), card.description)
        assertEquals(14, card.sugarDays.size)
        assertEquals(8, card.alcoholWeeks.size)
    }
}
