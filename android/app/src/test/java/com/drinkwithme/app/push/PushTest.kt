package com.drinkwithme.app.push

import com.drinkwithme.app.FixedUser
import com.drinkwithme.app.RecordingNotifier
import com.drinkwithme.app.data.InMemoryKeyValueStore
import com.drinkwithme.app.data.NotifiedEvents
import com.drinkwithme.app.supabase.PushSubscription
import com.drinkwithme.app.supabase.PushSubscriptionRemote
import com.drinkwithme.app.supabase.SocialException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PushMessageHandlerTest {
    private val user = FixedUser("me-id")
    private val notifier = RecordingNotifier()
    private val handler = PushMessageHandler(user, NotifiedEvents(InMemoryKeyValueStore()), notifier)

    private fun payload(recipient: String = "me-id", author: String = "anna-id", eventId: String = "ev-1", type: String = "drink") =
        """{"type":"$type","eventId":"$eventId","authorId":"$author","recipientId":"$recipient",
            "title":"Anna trinkt gerade","body":"Apfelschorle (500 ml)"}""".toByteArray()

    @Test
    fun showsDecryptedMessagesForTheSignedInUser() {
        assertEquals(PushOutcome.SHOWN, handler.handle(payload(), decrypted = true))
        assertEquals(DrinkNotificationContent("ev-1", "Anna trinkt gerade", "Apfelschorle (500 ml)"), notifier.shown.single())
    }

    @Test
    fun dropsMessagesTheConnectorCouldNotDecrypt() {
        assertEquals(PushOutcome.NOT_DECRYPTED, handler.handle(payload(), decrypted = false))
        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun dropsMessagesForAnotherAccount() {
        assertEquals(PushOutcome.OTHER_RECIPIENT, handler.handle(payload(recipient = "frueheres-konto"), decrypted = true))
        user.id = null
        assertEquals(PushOutcome.SIGNED_OUT, handler.handle(payload(), decrypted = true))
        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun dropsOwnEventsInvalidPayloadsAndDuplicates() {
        assertEquals(PushOutcome.OWN_EVENT, handler.handle(payload(author = "me-id"), decrypted = true))
        assertEquals(PushOutcome.INVALID, handler.handle("kein json".toByteArray(), decrypted = true))
        assertEquals(PushOutcome.INVALID, handler.handle(payload(type = "chat"), decrypted = true))
        assertEquals(PushOutcome.INVALID, handler.handle("""{"type":"drink","eventId":"x"}""".toByteArray(), decrypted = true))
        assertEquals(PushOutcome.SHOWN, handler.handle(payload(), decrypted = true))
        assertEquals(PushOutcome.DUPLICATE, handler.handle(payload(), decrypted = true))
        assertEquals(1, notifier.shown.size)
    }

    @Test
    fun parserTrimsAndLimitsTexts() {
        val long = "x".repeat(500)
        val message = DrinkPushParser.parse(
            """{"type":"drink","eventId":" ev ","authorId":"a","recipientId":"r","title":"$long","body":" Tee (250 ml) "}""".toByteArray(),
        )!!
        assertEquals("ev", message.eventId)
        assertEquals(200, message.title.length)
        assertEquals("Tee (250 ml)", message.body)
    }
}

class NotificationRequestCodesTest {
    @Test
    fun eachEventGetsItsOwnStableNonNegativeCodes() {
        val ids = (1..2000).map { "event-$it" } + "e" + "11111111-2222-3333-4444-555555555555"
        val open = ids.map(NotificationRequestCodes::open)
        val read = ids.map(NotificationRequestCodes::readAloud)
        assertTrue(open.all { it >= 0 && it % 2 == 0 })
        assertTrue(read.all { it > 0 && it % 2 == 1 })
        assertTrue(open.intersect(read.toSet()).isEmpty(), "Öffnen und Vorlesen nie gleich")
        assertEquals(ids.size, open.toSet().size, "keine Kollision bei diesen IDs")
        assertEquals(NotificationRequestCodes.open("x"), NotificationRequestCodes.open("x"))
        assertNotEquals(NotificationRequestCodes.open("a"), NotificationRequestCodes.open("b"))
    }
}

private class FakeRemote : PushSubscriptionRemote {
    val calls = mutableListOf<String>()
    var failure: SocialException? = null
    override suspend fun register(subscription: PushSubscription) {
        failure?.let { throw it }
        calls += "register:${subscription.endpoint}"
    }
    override suspend fun unregister(endpoint: String) {
        failure?.let { throw it }
        calls += "unregister:$endpoint"
    }
}

class PushSubscriptionSyncTest {
    private val store = PushEndpointStore(InMemoryKeyValueStore())
    private val remote = FakeRemote()
    private val user = FixedUser("me-id")
    private val sync = PushSubscriptionSync(store, remote, user)
    private val first = PushSubscription("https://ntfy.sh/up1", "p".repeat(87), "a".repeat(22))
    private val second = PushSubscription("https://ntfy.sh/up2", "q".repeat(87), "b".repeat(22))

    @Test
    fun uploadsNewEndpointOnceForTheSignedInUser() = runTest {
        store.setCurrent(first)
        assertEquals(SyncOutcome.DONE, sync.sync())
        assertEquals(SyncOutcome.DONE, sync.sync())
        assertEquals(listOf("register:https://ntfy.sh/up1"), remote.calls)
        assertEquals(UploadedSubscription("https://ntfy.sh/up1", "me-id"), store.uploaded)
    }

    @Test
    fun replacesChangedEndpoint() = runTest {
        store.setCurrent(first)
        sync.sync()
        store.setCurrent(second)
        sync.sync()
        assertEquals(listOf("register:https://ntfy.sh/up1", "unregister:https://ntfy.sh/up1", "register:https://ntfy.sh/up2"), remote.calls)
    }

    @Test
    fun removesSubscriptionWhenDistributorIsGone() = runTest {
        store.setCurrent(first)
        sync.sync()
        store.setCurrent(null)
        sync.sync()
        assertEquals("unregister:https://ntfy.sh/up1", remote.calls.last())
        assertNull(store.uploaded)
    }

    @Test
    fun forgetsForeignAccountsSubscriptionWithoutCallingTheServer() = runTest {
        store.setCurrent(first)
        sync.sync()
        user.id = "other-id"
        sync.sync()
        assertEquals(listOf("register:https://ntfy.sh/up1", "register:https://ntfy.sh/up1"), remote.calls)
        assertEquals("other-id", store.uploaded?.userId)
    }

    @Test
    fun networkErrorsAskForRetryAndServerErrorsAreShown() = runTest {
        store.setCurrent(first)
        remote.failure = SocialException.Network(java.io.IOException())
        assertEquals(SyncOutcome.RETRY_LATER, sync.sync())
        remote.failure = SocialException.Server("too_many_subscriptions", 400)
        assertEquals(SyncOutcome.DONE, sync.sync())
        assertTrue(store.lastError.value!!.contains("fünf Geräten"))
        remote.failure = null
        sync.sync()
        assertNull(store.lastError.value)
    }

    @Test
    fun signOutRemovesThisDevicesSubscription() = runTest {
        store.setCurrent(first)
        sync.sync()
        sync.removeForSignOut()
        assertEquals("unregister:https://ntfy.sh/up1", remote.calls.last())
        assertNull(store.uploaded)
    }

    @Test
    fun storeSurvivesRestart() {
        val kv = InMemoryKeyValueStore()
        PushEndpointStore(kv).apply {
            setCurrent(first)
            uploaded = UploadedSubscription(first.endpoint, "me-id")
        }
        val restored = PushEndpointStore(kv)
        assertEquals(first, restored.current.value)
        assertEquals("me-id", restored.uploaded?.userId)
    }
}
