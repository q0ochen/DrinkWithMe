package com.drinkwithme.app.data

import com.drinkwithme.app.barItem
import com.drinkwithme.app.entry
import com.drinkwithme.core.bar.DrinkCategory
import com.drinkwithme.core.bar.DrinkIcon
import com.drinkwithme.core.bar.ValueSource
import com.drinkwithme.core.log.HistoryReader
import com.drinkwithme.core.sharing.AlcoholSharingConsent
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalJsonTest {
    @Test
    fun barItemsSurviveRoundTrip() {
        val items = listOf(
            barItem(aliases = listOf("Leitungswasser")),
            barItem(id = "pils", name = "Pils", icon = DrinkIcon.BEER, category = DrinkCategory.BEER, serving = 500, alcohol = 4.9, sugar = 0.3)
                .copy(valueSource = ValueSource.USER, sortOrder = 3),
        )
        val decoded = BarJson.decode(BarJson.encode(items))
        assertIs<DecodeResult.Decoded<*>>(decoded)
        assertEquals(items, decoded.items)
        assertEquals(0, decoded.skipped)
    }

    @Test
    fun historySurvivesRoundTripWithMillisecondPrecision() {
        val entries = listOf(entry(at = Instant.parse("2026-11-10T11:00:00.123Z"), shared = true), entry(id = "e2"))
        val decoded = HistoryJson.decode(HistoryJson.encode(entries)) as DecodeResult.Decoded
        assertEquals(entries, decoded.items)
    }

    @Test
    fun readingIsTolerant() {
        val text = """
            {"version": 99, "entries": [
              {"id":"a","name":"Mate","icon":"mate_new","category":"FUTURE","volumeMilliliters":330,"alcoholPercent":0,
               "sugarGramsPer100Ml":5.5,"consumedAt":"2026-11-10T10:00:00Z","extra":true},
              {"id":"","name":"Leer","volumeMilliliters":1,"consumedAt":"2026-11-10T10:00:00Z"},
              {"id":"b","name":"Ohne Zeit","volumeMilliliters":200},
              "kein Objekt",
              {"id":"c","name":"Tee","icon":"tea","category":"TEA","volumeMilliliters":250,"consumedAt":"2026-11-10T09:00:00+01:00","barItemId":null}
            ]}
        """.trimIndent()
        val decoded = HistoryJson.decode(text) as DecodeResult.Decoded
        assertEquals(listOf("a", "c"), decoded.items.map { it.id })
        assertEquals(3, decoded.skipped)
        val mate = decoded.items[0]
        assertEquals(DrinkIcon.OTHER, mate.icon)
        assertEquals(DrinkCategory.OTHER, mate.category)
        assertEquals(5.5, mate.sugarGramsPer100Ml)
        assertFalse(mate.shareWithFriends, "ohne Angabe bleibt ein Eintrag privat")
        assertEquals(Instant.parse("2026-11-10T08:00:00Z"), decoded.items[1].consumedAt)
        assertNull(decoded.items[1].barItemId)
    }

    @Test
    fun brokenFilesAreReportedAsCorrupt() {
        assertEquals(DecodeResult.Corrupt, HistoryJson.decode("{ kaputt"))
        assertEquals(DecodeResult.Corrupt, BarJson.decode("""{"version":1}"""))
        assertEquals(DecodeResult.Corrupt, BarJson.decode("[]"))
    }
}

class JsonFileRepositoriesTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun barIsStoredAcrossInstancesAndReorderable() {
        val file = File(folder.root, "bar.json")
        val first = JsonFileBarRepository(file)
        first.save(barItem(id = "a", name = "A", sortOrder = 0))
        first.save(barItem(id = "b", name = "B", sortOrder = 1))
        first.save(barItem(id = "c", name = "C", sortOrder = 2))
        first.reorder(listOf("c", "a"))

        val second = JsonFileBarRepository(file)
        assertEquals(listOf("c", "a", "b"), second.items().map { it.id })
        assertEquals(listOf(0, 1, 2), second.items().map { it.sortOrder })
        second.delete("a")
        assertEquals(listOf("c", "b"), JsonFileBarRepository(file).items().map { it.id })
        assertFalse(File(folder.root, "bar.json.tmp").exists(), "keine Zwischendatei übrig")
    }

    @Test
    fun historyFiltersHalfOpenAndSorts() {
        val repository = JsonFileHistoryRepository(File(folder.root, "history.json"))
        repository.save(entry(id = "late", at = Instant.parse("2026-11-10T12:00:00Z")))
        repository.save(entry(id = "early", at = Instant.parse("2026-11-10T08:00:00Z")))
        repository.save(entry(id = "late", at = Instant.parse("2026-11-10T12:00:00Z"), shared = true))

        val all = repository.entries(HistoryReader.EARLIEST, HistoryReader.LATEST)
        assertEquals(listOf("early", "late"), all.map { it.id })
        assertTrue(repository.entry("late")!!.shareWithFriends)
        val window = repository.entries(Instant.parse("2026-11-10T08:00:00Z"), Instant.parse("2026-11-10T12:00:00Z"))
        assertEquals(listOf("early"), window.map { it.id })
    }

    @Test
    fun revisionGrowsWithEveryWrite() {
        val repository = JsonFileHistoryRepository(File(folder.root, "history.json"))
        val before = repository.revision.value
        repository.save(entry())
        repository.delete("e1")
        assertEquals(before + 2, repository.revision.value)
    }

    @Test
    fun corruptFileIsSetAsideInsteadOfOverwritten() {
        val file = File(folder.root, "history.json")
        file.writeText("{ das ist kein JSON")
        val repository = JsonFileHistoryRepository(file)
        assertTrue(repository.entries(HistoryReader.EARLIEST, HistoryReader.LATEST).isEmpty())
        repository.save(entry())

        val quarantined = folder.root.listFiles()!!.filter { it.name.startsWith("history.json.defekt-") }
        assertEquals(1, quarantined.size)
        assertEquals("{ das ist kein JSON", quarantined.single().readText())
        assertEquals(listOf("e1"), JsonFileHistoryRepository(file).entries(HistoryReader.EARLIEST, HistoryReader.LATEST).map { it.id })
    }
}

class SettingsStoresTest {
    @Test
    fun settingsPersistAndExposeFlows() {
        val store = InMemoryKeyValueStore()
        val settings = AppSettings(store)
        assertEquals(AlcoholSharingConsent.UNKNOWN, settings.alcoholConsent.value)
        assertFalse(settings.onboardingDone)
        assertTrue(settings.showAlcoholBands.value.value)

        settings.setAlcoholConsent(AlcoholSharingConsent.ALLOWED)
        settings.onboardingDone = true
        settings.showAlcoholBands.set(false)
        settings.lastSeenEventAt = Instant.parse("2026-11-10T10:00:00Z")
        settings.pendingInviteToken = "token"

        val reloaded = AppSettings(store)
        assertEquals(AlcoholSharingConsent.ALLOWED, reloaded.alcoholConsent.value)
        assertTrue(reloaded.onboardingDone)
        assertFalse(reloaded.showAlcoholBands.value.value)
        assertEquals(Instant.parse("2026-11-10T10:00:00Z"), reloaded.lastSeenEventAt)

        reloaded.clearAccountData()
        assertNull(reloaded.lastSeenEventAt)
        assertNull(reloaded.pendingInviteToken)
        assertEquals(AlcoholSharingConsent.ALLOWED, reloaded.alcoholConsent.value, "Zustimmung ist eine Geräte-Einstellung")
    }

    @Test
    fun notifiedEventsDeduplicateAndKeepOnlyTheNewest() {
        val store = InMemoryKeyValueStore()
        val notified = NotifiedEvents(store, capacity = 3)
        assertTrue(notified.markIfNew("a"))
        assertFalse(notified.markIfNew("a"))
        listOf("b", "c", "d").forEach { assertTrue(notified.markIfNew(it)) }
        assertFalse(NotifiedEvents(store, capacity = 3).contains("a"), "älteste ID fällt heraus")
        assertTrue(NotifiedEvents(store, capacity = 3).contains("d"))
        notified.clear()
        assertFalse(notified.contains("d"))
    }

    @Test
    fun brokenStoredValuesFallBackToDefaults() {
        val store = InMemoryKeyValueStore(
            mapOf("alcohol_sharing_consent" to "VIELLEICHT", "notified_event_ids" to "kaputt", "last_seen_event_at" to "gestern"),
        )
        assertEquals(AlcoholSharingConsent.UNKNOWN, AppSettings(store).alcoholConsent.value)
        assertNull(AppSettings(store).lastSeenEventAt)
        assertTrue(NotifiedEvents(store).markIfNew("x"))
    }
}
