package com.drinkwithme.core.log

import com.drinkwithme.core.MutableClock
import com.drinkwithme.core.SequentialIds
import com.drinkwithme.core.bar.BarItem
import com.drinkwithme.core.bar.DefaultBarCatalog
import com.drinkwithme.core.bar.DrinkCategory
import com.drinkwithme.core.bar.DrinkIcon
import com.drinkwithme.core.bar.InvalidBarItemException
import com.drinkwithme.core.catalogItem
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogDrinkFromBarUseCaseTest {
    private val now = Instant.parse("2026-11-10T17:30:00Z")
    private val clock = MutableClock(now)
    private val history = InMemoryHistoryRepository()
    private val decisions = mutableListOf<Pair<BarItem, Boolean?>>()
    private val shareAll = ShareDecider { item, override -> decisions += item to override; override != false }
    private val logDrink = LogDrinkFromBarUseCase(history, shareAll, clock, SequentialIds())
    private val schorle = catalogItem(DefaultBarCatalog.APFELSCHORLE)

    @Test
    fun createsASnapshotWithTheCurrentTime() {
        val entry = logDrink(schorle)

        assertEquals(
            DrinkEntry(
                id = "id-1",
                barItemId = DefaultBarCatalog.APFELSCHORLE,
                name = "Apfelschorle",
                icon = DrinkIcon.APPLE_SPRITZER,
                category = DrinkCategory.JUICE,
                volumeMilliliters = 500,
                alcoholPercent = 0.0,
                sugarGramsPer100Ml = 5.5,
                consumedAt = now,
                shareWithFriends = true,
            ),
            entry,
        )
        assertEquals(listOf(entry), history.entries(HistoryReader.EARLIEST, HistoryReader.LATEST))
    }

    @Test
    fun timeIsAlwaysNow() {
        val first = logDrink(schorle)
        clock.advance(Duration.ofMinutes(45))
        val second = logDrink(schorle)

        assertEquals(now, first.consumedAt)
        assertEquals(now.plus(Duration.ofMinutes(45)), second.consumedAt)
    }

    @Test
    fun timeIsTruncatedToMilliseconds() {
        clock.now = Instant.parse("2026-11-10T17:30:00.123456789Z")
        assertEquals(Instant.parse("2026-11-10T17:30:00.123Z"), logDrink(schorle).consumedAt)
    }

    @Test
    fun laterChangesToTheBarDoNotChangeTheHistory() {
        val entry = logDrink(schorle)
        val changed = schorle.copy(name = "Schorle light", sugarGramsPer100Ml = 2.0, servingMilliliters = 300)
        logDrink(changed)

        val stored = history.entry(entry.id)!!
        assertEquals("Apfelschorle", stored.name)
        assertEquals(5.5, stored.sugarGramsPer100Ml)
        assertEquals(500, stored.volumeMilliliters)
    }

    @Test
    fun volumeOverrideReplacesTheServing() {
        assertEquals(330, logDrink(schorle, volumeOverride = 330).volumeMilliliters)
        assertEquals(1, logDrink(schorle, volumeOverride = 1).volumeMilliliters)
        assertEquals(2000, logDrink(schorle, volumeOverride = 2000).volumeMilliliters)
    }

    @Test
    fun invalidVolumeIsRejectedAndNothingIsSaved() {
        listOf(0, -5, 2001).forEach { volume ->
            val error = assertFailsWith<IllegalArgumentException> { logDrink(schorle, volumeOverride = volume) }
            assertEquals("Die Menge muss zwischen 1 und 2000 ml liegen.", error.message)
        }
        assertTrue(history.entries(HistoryReader.EARLIEST, HistoryReader.LATEST).isEmpty())
    }

    @Test
    fun invalidBarItemIsRejected() {
        assertFailsWith<InvalidBarItemException> { logDrink(schorle.copy(name = " ")) }
        assertTrue(history.entries(HistoryReader.EARLIEST, HistoryReader.LATEST).isEmpty())
    }

    @Test
    fun sharingIsDecidedByThePolicyWithTheOverride() {
        assertTrue(logDrink(schorle).shareWithFriends)
        assertFalse(logDrink(schorle, shareOverride = false).shareWithFriends)
        assertTrue(logDrink(schorle, shareOverride = true).shareWithFriends)
        assertEquals(listOf<Boolean?>(null, false, true), decisions.map { it.second })
        assertTrue(decisions.all { it.first == schorle })
    }

    @Test
    fun nameIsTrimmed() {
        assertEquals("Apfelschorle", logDrink(schorle.copy(name = "  Apfelschorle ")).name)
    }
}
