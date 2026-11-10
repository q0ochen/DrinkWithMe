package com.drinkwithme.app.polling

import com.drinkwithme.app.FakeFeed
import com.drinkwithme.app.FixedUser
import com.drinkwithme.app.MutableClock
import com.drinkwithme.app.RecordingNotifier
import com.drinkwithme.app.data.InMemoryKeyValueStore
import com.drinkwithme.app.data.NotifiedEvents
import com.drinkwithme.app.friendEvent
import com.drinkwithme.app.profile
import com.drinkwithme.app.push.PushMessageHandler
import kotlinx.coroutines.test.runTest
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class MemoryState(override var lastSeenEventAt: Instant? = null) : PollingState

class FeedPollerTest {
    private val clock = MutableClock(Instant.parse("2026-11-10T12:00:00Z"))
    private val feed = FakeFeed()
    private val user = FixedUser("me-id")
    private val state = MemoryState()
    private val notified = NotifiedEvents(InMemoryKeyValueStore())
    private val notifier = RecordingNotifier()
    private val poller = FeedPoller(feed, user, state, notified, notifier, clock, maxNotificationsPerPoll = 3)

    @Test
    fun firstPollOnlyRemembersExistingEvents() = runTest {
        feed.events = listOf(friendEvent("old", at = Instant.parse("2026-11-10T11:00:00Z")))
        assertEquals(PollResult.Baseline(1), poller.poll())
        assertTrue(notifier.shown.isEmpty())
        assertEquals(clock.now, state.lastSeenEventAt)
        assertEquals(Instant.parse("2026-11-09T12:00:00Z"), feed.requests.single().first, "24 h Rückblick")
    }

    @Test
    fun laterPollsNotifyNewEventsOnceUsingSharedTexts() = runTest {
        poller.poll()
        clock.advance(Duration.ofMinutes(15))
        feed.events = listOf(friendEvent("new", profile(name = "Anna"), "Apfelschorle", 500, clock.now.minusSeconds(60)))

        assertEquals(PollResult.Checked(1), poller.poll())
        assertEquals("Anna trinkt gerade", notifier.shown.single().title)
        assertEquals("Apfelschorle (500 ml)", notifier.shown.single().body)

        clock.advance(Duration.ofMinutes(15))
        assertEquals(PollResult.Checked(0), poller.poll(), "keine Doppelten")
        assertEquals(1, notifier.shown.size)
    }

    @Test
    fun lateSharedEventsWithinOneDayAreStillFound() = runTest {
        poller.poll()
        clock.advance(Duration.ofHours(2))
        // Offline um 09:00 eingetragen, erst jetzt geteilt: liegt vor dem letzten Abruf.
        feed.events = listOf(friendEvent("late", at = Instant.parse("2026-11-10T09:00:00Z")))
        assertEquals(PollResult.Checked(1), poller.poll())
    }

    @Test
    fun eventsAlreadyShownByPushAreSkipped() = runTest {
        poller.poll()
        val push = PushMessageHandler(user, notified, RecordingNotifier())
        push.handle(
            """{"type":"drink","eventId":"pushed","authorId":"anna-id","recipientId":"me-id","title":"T","body":"B"}""".toByteArray(),
            decrypted = true,
        )
        feed.events = listOf(friendEvent("pushed", at = clock.now.minusSeconds(30)))
        assertEquals(PollResult.Checked(0), poller.poll())
    }

    @Test
    fun limitsNotificationsAndIgnoresOwnEvents() = runTest {
        poller.poll()
        feed.events = (1..6).map { friendEvent("e$it", at = clock.now.minusSeconds(60L * it)) } +
            friendEvent("mine", profile("me-id", "Ich"), at = clock.now.minusSeconds(5))
        assertEquals(PollResult.Checked(3), poller.poll())
        assertEquals(listOf("e3", "e2", "e1"), notifier.shown.map { it.eventId }, "die neuesten, in zeitlicher Reihenfolge")
    }

    @Test
    fun signedOutDoesNothing() = runTest {
        user.id = null
        assertIs<PollResult.NotSignedIn>(poller.poll())
        assertTrue(feed.requests.isEmpty())
    }
}

class PollingPolicyTest {
    @Test
    fun pollsOnlyWithoutPush() {
        assertTrue(PollingPolicy.shouldPoll(serverConfigured = true, signedIn = true, pushActive = false))
        assertFalse(PollingPolicy.shouldPoll(serverConfigured = true, signedIn = true, pushActive = true))
        assertFalse(PollingPolicy.shouldPoll(serverConfigured = true, signedIn = false, pushActive = false))
        assertFalse(PollingPolicy.shouldPoll(serverConfigured = false, signedIn = true, pushActive = false))
    }

    @Test
    fun socialBackgroundSchedulesAndSyncs() {
        val calls = mutableListOf<String>()
        val scheduler = object : PollingScheduler {
            override fun schedule() { calls += "schedule" }
            override fun cancel() { calls += "cancel" }
        }
        var signedIn = true
        var push = false
        val background = SocialBackground(true, { signedIn }, { push }, scheduler) { calls += "sync" }
        background.refresh()
        push = true
        background.refresh()
        signedIn = false
        background.refresh()
        assertEquals(listOf("schedule", "sync", "cancel", "sync", "cancel"), calls)
    }
}
