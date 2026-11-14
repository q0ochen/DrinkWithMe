package com.drinkwithme.core.sharing

import com.drinkwithme.core.MutableClock
import com.drinkwithme.core.SequentialIds
import com.drinkwithme.core.bar.DefaultBarCatalog
import com.drinkwithme.core.catalogItem
import com.drinkwithme.core.entryOf
import com.drinkwithme.core.log.DrinkEntry
import com.drinkwithme.core.log.HistoryReader
import com.drinkwithme.core.log.InMemoryHistoryRepository
import com.drinkwithme.core.log.LogDrinkFromBarUseCase
import com.drinkwithme.core.log.UndoLastEntryUseCase
import com.drinkwithme.core.log.UndoResult
import com.drinkwithme.core.social.EventPublisher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Zeichnet alle Netzwerkaufträge auf; optional mit Fehlern oder Wartepunkt. */
private class RecordingPublisher : EventPublisher {
    val calls = mutableListOf<String>()
    val published = mutableListOf<DrinkEntry>()
    var failNextWith: Exception? = null
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun publish(entry: DrinkEntry) {
        gate?.await()
        failNextWith?.let { failNextWith = null; throw it }
        calls += "publish:${entry.id}"
        published += entry
    }

    override suspend fun unpublish(entryId: String) {
        gate?.await()
        failNextWith?.let { failNextWith = null; throw it }
        calls += "unpublish:$entryId"
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SharingHistoryRepositoryTest {
    private val water = catalogItem(DefaultBarCatalog.WASSER)
    private val pils = catalogItem(DefaultBarCatalog.PILS)
    private val local = InMemoryHistoryRepository()
    private val publisher = RecordingPublisher()
    private val errors = mutableListOf<Throwable>()
    private var state = SharingState(isSignedIn = true, alcoholConsent = AlcoholSharingConsent.ALLOWED)
    private val policy = SharingPolicy { state }
    private val at = Instant.parse("2026-11-10T18:00:00Z")

    private fun TestScope.repository() = SharingHistoryRepository(local, publisher, policy, this) { errors += it }

    private fun allLocal() = local.entries(HistoryReader.EARLIEST, HistoryReader.LATEST)

    @Test
    fun savesLocallyAtOnceAndPublishesInTheBackground() = runTest {
        val repository = repository()
        val entry = entryOf(water, at, share = true)

        repository.save(entry)
        assertEquals(listOf(entry), allLocal()) // sofort lokal
        assertTrue(publisher.calls.isEmpty()) // Netz erst im Hintergrund

        advanceUntilIdle()
        assertEquals(listOf(entry), publisher.published)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun localSaveNeverWaitsForTheNetwork() = runTest {
        val repository = repository()
        val gate = CompletableDeferred<Unit>().also { publisher.gate = it }
        val first = entryOf(water, at, share = true)
        val second = entryOf(water, at.plusSeconds(60), share = true)

        repository.save(first)
        runCurrent() // Veröffentlichen hängt am Netz …
        repository.save(second) // … lokales Speichern trotzdem sofort
        assertEquals(listOf(first, second), repository.entries(HistoryReader.EARLIEST, HistoryReader.LATEST))
        assertTrue(publisher.calls.isEmpty())

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("publish:${first.id}", "publish:${second.id}"), publisher.calls)
    }

    @Test
    fun privateEntriesCauseNoNetworkCalls() = runTest {
        val repository = repository()
        val entry = entryOf(water, at, share = false)

        repository.save(entry)
        repository.delete(entry.id)
        advanceUntilIdle()

        assertTrue(publisher.calls.isEmpty())
        assertTrue(allLocal().isEmpty())
    }

    @Test
    fun rechecksThePolicyBeforePublishing() = runTest {
        val repository = repository()
        state = SharingState(isSignedIn = true, alcoholConsent = AlcoholSharingConsent.DENIED)
        repository.save(entryOf(pils, at, share = true)) // Zustimmung inzwischen widerrufen
        state = SharingState(isSignedIn = false, alcoholConsent = AlcoholSharingConsent.ALLOWED)
        repository.save(entryOf(water, at, share = true)) // inzwischen abgemeldet
        advanceUntilIdle()

        assertTrue(publisher.calls.isEmpty())
        assertEquals(2, allLocal().size)
    }

    @Test
    fun deleteUnpublishesSharedEntries() = runTest {
        val repository = repository()
        val entry = entryOf(water, at, share = true)
        repository.save(entry)
        repository.delete(entry.id)

        assertNull(local.entry(entry.id)) // sofort lokal gelöscht
        advanceUntilIdle()
        assertEquals(listOf("publish:${entry.id}", "unpublish:${entry.id}"), publisher.calls) // Reihenfolge bleibt
    }

    @Test
    fun deleteWhileSignedOutStaysLocal() = runTest {
        val repository = repository()
        val entry = entryOf(water, at, share = true)
        local.save(entry)
        state = state.copy(isSignedIn = false)

        repository.delete(entry.id)
        advanceUntilIdle()
        assertTrue(publisher.calls.isEmpty())
        assertNull(local.entry(entry.id))
    }

    @Test
    fun resavingASharedEntryAsPrivateUnpublishesIt() = runTest {
        val repository = repository()
        val entry = entryOf(water, at, share = true)
        repository.save(entry)
        repository.save(entry.copy(shareWithFriends = false))
        advanceUntilIdle()

        assertEquals(listOf("publish:${entry.id}", "unpublish:${entry.id}"), publisher.calls)
        assertEquals(listOf(entry.copy(shareWithFriends = false)), allLocal())
    }

    @Test
    fun failuresAreReportedAndBlockNothing() = runTest {
        val repository = repository()
        val failure = IllegalStateException("offline")
        publisher.failNextWith = failure
        val first = entryOf(water, at, share = true)
        val second = entryOf(water, at.plusSeconds(1), share = true)

        repository.save(first)
        repository.save(second)
        advanceUntilIdle()

        assertEquals(listOf(first, second), allLocal())
        assertEquals(listOf<Throwable>(failure), errors)
        assertEquals(listOf(second), publisher.published)
    }

    @Test
    fun aThrowingErrorCallbackDoesNotStopLaterOperations() = runTest {
        val repository = SharingHistoryRepository(local, publisher, policy, this) { throw IllegalArgumentException("Callback kaputt") }
        publisher.failNextWith = IllegalStateException("offline")
        val first = entryOf(water, at, share = true)
        val second = entryOf(water, at.plusSeconds(1), share = true)

        repository.save(first)
        repository.save(second)
        advanceUntilIdle()

        assertEquals(listOf(second), publisher.published)
    }

    @Test
    fun laterOperationsAfterTheQueueRanEmptyStillRun() = runTest {
        val repository = repository()
        val first = entryOf(water, at, share = true)
        repository.save(first)
        advanceUntilIdle()
        val second = entryOf(water, at.plusSeconds(5), share = true)
        repository.save(second)
        advanceUntilIdle()

        assertEquals(listOf(first, second), publisher.published)
    }

    @Test
    fun readsComeFromTheLocalHistory() = runTest {
        val entry = entryOf(water, at, share = false)
        local.save(entry)
        val repository = repository()
        assertEquals(listOf(entry), repository.entries(at, at.plusSeconds(1)))
        assertEquals(entry, repository.entry(entry.id))
    }

    @Test
    fun logAndUndoThroughTheDecorator() = runTest {
        val clock = MutableClock(at)
        val repository = repository()
        val logDrink = LogDrinkFromBarUseCase(repository, policy, clock, SequentialIds())
        val undo = UndoLastEntryUseCase(repository, clock)

        val entry = logDrink(pils)
        assertTrue(entry.shareWithFriends)
        clock.advance(Duration.ofSeconds(3))
        assertIs<UndoResult.Undone>(undo())
        advanceUntilIdle()

        assertTrue(allLocal().isEmpty())
        assertEquals(listOf("publish:id-1", "unpublish:id-1"), publisher.calls)
    }
}
