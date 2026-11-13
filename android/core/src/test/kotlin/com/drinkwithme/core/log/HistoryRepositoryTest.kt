package com.drinkwithme.core.log

import com.drinkwithme.core.berlin
import com.drinkwithme.core.entry
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HistoryRepositoryTest {
    private val morning = entry(berlin("2026-11-10T08:00"))
    private val noon = entry(berlin("2026-11-10T12:00"))
    private val evening = entry(berlin("2026-11-10T20:00"))

    @Test
    fun entriesUsesAHalfOpenRangeAndSortsAscending() {
        val repository = InMemoryHistoryRepository(listOf(evening, morning, noon))
        assertEquals(listOf(morning, noon, evening), repository.entries(berlin("2026-11-10T00:00"), berlin("2026-11-11T00:00")))
        assertEquals(listOf(morning), repository.entries(morning.consumedAt, noon.consumedAt))
        assertTrue(repository.entries(berlin("2026-11-11T00:00"), berlin("2026-11-12T00:00")).isEmpty())
    }

    @Test
    fun saveAddsOrReplacesAndDeleteRemoves() {
        val repository = InMemoryHistoryRepository()
        repository.save(morning)
        repository.save(morning.copy(volumeMilliliters = 330))
        repository.save(noon)
        repository.delete(noon.id)
        repository.delete("unbekannt")
        assertEquals(listOf(morning.copy(volumeMilliliters = 330)), repository.entries(HistoryReader.EARLIEST, HistoryReader.LATEST))
        assertEquals(morning.copy(volumeMilliliters = 330), repository.entry(morning.id))
        assertNull(repository.entry(noon.id))
    }

    @Test
    fun defaultEntryLookupSearchesTheWholeHistory() {
        val reader = object : HistoryReader {
            override fun entries(from: Instant, to: Instant) =
                listOf(morning, noon).filter { !it.consumedAt.isBefore(from) && it.consumedAt.isBefore(to) }
        }
        assertEquals(noon, reader.entry(noon.id))
        assertNull(reader.entry("unbekannt"))
    }

    @Test
    fun latestFitsIntoEpochMilliseconds() {
        assertTrue(HistoryReader.LATEST.toEpochMilli() > Instant.parse("2999-12-31T00:00:00Z").toEpochMilli())
    }
}
