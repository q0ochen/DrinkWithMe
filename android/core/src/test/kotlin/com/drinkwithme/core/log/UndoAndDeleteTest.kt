package com.drinkwithme.core.log

import com.drinkwithme.core.MutableClock
import com.drinkwithme.core.entry
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UndoAndDeleteTest {
    private val loggedAt = Instant.parse("2026-11-10T17:30:00Z")
    private val clock = MutableClock(loggedAt)
    private val latest = entry(loggedAt)
    private val earlier = entry(loggedAt.minus(Duration.ofMinutes(5)))
    private val history = InMemoryHistoryRepository(listOf(earlier, latest))
    private val undo = UndoLastEntryUseCase(history, clock)

    @Test
    fun undoesImmediatelyWithoutQuestion() {
        assertEquals(UndoResult.Undone(latest), undo())
        assertNull(history.entry(latest.id))
        assertEquals(earlier, history.entry(earlier.id))
    }

    @Test
    fun undoesUpToExactlyTenSeconds() {
        clock.advance(Duration.ofSeconds(10))
        assertEquals(UndoResult.Undone(latest), undo())
    }

    @Test
    fun asksForConfirmationAfterTenSeconds() {
        clock.advance(Duration.ofSeconds(10).plusMillis(1))
        assertEquals(UndoResult.ConfirmationRequired(latest), undo())
        assertEquals(latest, history.entry(latest.id))

        assertEquals(UndoResult.Undone(latest), undo.confirm(latest))
        assertNull(history.entry(latest.id))
    }

    @Test
    fun repeatedUndoRemovesTheNextNewestEntry() {
        undo()
        clock.advance(Duration.ofSeconds(1))
        assertEquals(UndoResult.ConfirmationRequired(earlier), undo())
    }

    @Test
    fun nothingToUndoWithoutRecentEntries() {
        assertEquals(UndoResult.NothingToUndo, UndoLastEntryUseCase(InMemoryHistoryRepository(), clock)())
        clock.advance(Duration.ofHours(25))
        assertEquals(UndoResult.NothingToUndo, undo())
    }

    @Test
    fun windowIsConfigurable() {
        clock.advance(Duration.ofSeconds(20))
        assertEquals(UndoResult.Undone(latest), UndoLastEntryUseCase(history, clock, window = Duration.ofSeconds(30))())
        assertEquals(Duration.ofSeconds(10), UndoLastEntryUseCase.DEFAULT_WINDOW)
    }

    @Test
    fun deleteEntryRemovesIt() {
        DeleteEntryUseCase(history)(earlier.id)
        assertEquals(listOf(latest), history.entries(HistoryReader.EARLIEST, HistoryReader.LATEST))
        DeleteEntryUseCase(history)("unbekannt")
        assertTrue(history.entry(latest.id) != null)
    }
}
