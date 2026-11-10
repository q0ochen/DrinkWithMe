package com.drinkwithme.app.publish

import com.drinkwithme.app.MutableClock
import com.drinkwithme.app.data.InMemoryKeyValueStore
import com.drinkwithme.app.entry
import com.drinkwithme.app.supabase.SocialException
import com.drinkwithme.core.log.DrinkEntry
import com.drinkwithme.core.log.InMemoryHistoryRepository
import com.drinkwithme.core.sharing.AlcoholSharingConsent
import com.drinkwithme.core.sharing.SharingPolicy
import com.drinkwithme.core.sharing.SharingState
import com.drinkwithme.core.social.EventPublisher
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class ScriptedPublisher : EventPublisher {
    val calls = mutableListOf<String>()
    var failure: Exception? = null
    override suspend fun publish(entry: DrinkEntry) {
        failure?.let { throw it }
        calls += "publish:${entry.id}"
    }
    override suspend fun unpublish(entryId: String) {
        failure?.let { throw it }
        calls += "unpublish:$entryId"
    }
}

class PendingShareQueueTest {
    @Test
    fun keepsOrderDropsDuplicatesAndCancelsPublishOnUnpublish() {
        val queue = PendingShareQueue(InMemoryKeyValueStore())
        queue.add(PendingShare.Publish("a"))
        queue.add(PendingShare.Publish("b"))
        queue.add(PendingShare.Publish("a"))
        queue.add(PendingShare.Unpublish("b"))
        assertEquals(listOf(PendingShare.Publish("a"), PendingShare.Unpublish("b")), queue.snapshot())
        queue.remove(PendingShare.Publish("a"))
        assertEquals(listOf(PendingShare.Unpublish("b")), queue.snapshot())
        queue.clear()
        assertTrue(queue.snapshot().isEmpty())
    }

    @Test
    fun survivesRestartAndIgnoresGarbage() {
        val kv = InMemoryKeyValueStore()
        PendingShareQueue(kv).add(PendingShare.Unpublish("x"))
        assertEquals(listOf(PendingShare.Unpublish("x")), PendingShareQueue(kv).snapshot())
        kv.putString("pending_shares", "[{\"op\":\"zaubern\",\"id\":\"y\"},{\"op\":\"publish\"}]")
        assertTrue(PendingShareQueue(kv).snapshot().isEmpty())
    }
}

class RetryingEventPublisherTest {
    private val queue = PendingShareQueue(InMemoryKeyValueStore())
    private val delegate = ScriptedPublisher()
    private var scheduled = 0
    private val publisher = RetryingEventPublisher(delegate, queue) { scheduled++ }

    @Test
    fun networkFailuresAreQueuedForLater() = runTest {
        delegate.failure = SocialException.Network(IOException("offline"))
        publisher.publish(entry(id = "a"))
        publisher.unpublish("b")
        assertEquals(listOf(PendingShare.Publish("a"), PendingShare.Unpublish("b")), queue.snapshot())
        assertEquals(2, scheduled)
    }

    @Test
    fun otherFailuresAreNotRetried() = runTest {
        delegate.failure = SocialException.Server("42501", 403)
        val result = runCatching { publisher.publish(entry(id = "a")) }
        assertTrue(result.isFailure)
        assertTrue(queue.snapshot().isEmpty())
    }

    @Test
    fun successPassesThrough() = runTest {
        publisher.publish(entry(id = "a"))
        assertEquals(listOf("publish:a"), delegate.calls)
        assertEquals(0, scheduled)
    }
}

class ShareRetryProcessorTest {
    private val clock = MutableClock()
    private val history = InMemoryHistoryRepository()
    private val queue = PendingShareQueue(InMemoryKeyValueStore())
    private val publisher = ScriptedPublisher()
    private var state = SharingState(isSignedIn = true, alcoholConsent = AlcoholSharingConsent.UNKNOWN)
    private val processor = ShareRetryProcessor(queue, history, SharingPolicy { state }, publisher, clock)

    @Test
    fun publishesStillSharedFreshEntriesAndUnpublishes() = runTest {
        history.save(entry(id = "fresh", shared = true, at = clock.now.minusSeconds(60)))
        history.save(entry(id = "old", shared = true, at = clock.now.minus(Duration.ofHours(30))))
        history.save(entry(id = "private", shared = false, at = clock.now))
        listOf("fresh", "old", "private", "deleted").forEach { queue.add(PendingShare.Publish(it)) }
        queue.add(PendingShare.Unpublish("gone"))

        assertEquals(RetryOutcome.DONE, processor.run())
        assertEquals(listOf("publish:fresh", "unpublish:gone"), publisher.calls)
        assertTrue(queue.snapshot().isEmpty())
    }

    @Test
    fun stopsAtNetworkErrorAndKeepsTheRest() = runTest {
        history.save(entry(id = "a", shared = true, at = clock.now))
        queue.add(PendingShare.Publish("a"))
        queue.add(PendingShare.Unpublish("b"))
        publisher.failure = SocialException.Network(IOException())
        assertEquals(RetryOutcome.RETRY_LATER, processor.run())
        assertEquals(2, queue.snapshot().size)
    }

    @Test
    fun signedOutDropsPendingWork() = runTest {
        state = state.copy(isSignedIn = false)
        history.save(entry(id = "a", shared = true, at = clock.now))
        queue.add(PendingShare.Publish("a"))
        queue.add(PendingShare.Unpublish("b"))
        assertEquals(RetryOutcome.DONE, processor.run())
        assertTrue(publisher.calls.isEmpty())
        assertTrue(queue.snapshot().isEmpty())
    }
}
