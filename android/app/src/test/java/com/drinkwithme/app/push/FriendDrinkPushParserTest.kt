package com.drinkwithme.app.push

import com.drinkwithme.app.social.SignedInUserIdProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FriendDrinkPushParserTest {
    private val message = mapOf(
        "type" to "drink",
        "groupId" to "g1",
        "drinkId" to "d1",
        "docId" to "u2_d1",
        "recipientId" to "u1",
        "title" to "Ben trinkt gerade",
        "body" to "🍺 Pils (500 ml) · Stammtisch",
    )

    @Test
    fun messageForTheSignedInUserIsShown() {
        val notification = FriendDrinkPushParser.parse(message, signedInUserId = "u1")

        assertEquals(
            FriendDrinkNotification(
                title = "Ben trinkt gerade",
                body = "🍺 Pils (500 ml) · Stammtisch",
                tag = "u2_d1",
                groupId = "g1",
            ),
            notification,
        )
    }

    @Test
    fun messageForAnotherUserIsDropped() {
        assertNull(FriendDrinkPushParser.parse(message, signedInUserId = "u3"))
    }

    @Test
    fun messageWhileSignedOutIsDropped() {
        assertNull(FriendDrinkPushParser.parse(message, signedInUserId = null))
    }

    @Test
    fun messageWithoutRecipientIsDropped() {
        assertNull(FriendDrinkPushParser.parse(message - "recipientId", signedInUserId = "u1"))
        assertNull(FriendDrinkPushParser.parse(message + ("recipientId" to " "), signedInUserId = " "))
    }

    @Test
    fun otherTypesAndIncompleteMessagesAreDropped() {
        assertNull(FriendDrinkPushParser.parse(message + ("type" to "other"), "u1"))
        assertNull(FriendDrinkPushParser.parse(message - "type", "u1"))
        assertNull(FriendDrinkPushParser.parse(message - "title", "u1"))
    }

    @Test
    fun tagFallsBackToDrinkIdAndGroupIsOptional() {
        val notification = FriendDrinkPushParser.parse(message - "docId" - "groupId" - "body", "u1")!!
        assertEquals("d1", notification.tag)
        assertNull(notification.groupId)
        assertEquals("", notification.body)
    }

    @Test
    fun handlerShowsOnlyMessagesForTheSignedInUser() {
        val shown = mutableListOf<FriendDrinkNotification>()
        var signedIn: String? = "u1"
        val handler = DefaultPushMessageHandler(
            registrar = NoOpRegistrar,
            notifier = { shown += it },
            signedInUser = SignedInUserIdProvider { signedIn },
            scope = CoroutineScope(Dispatchers.Unconfined),
        )

        handler.onDataMessage(message)
        handler.onDataMessage(message + ("recipientId" to "someone-else"))
        signedIn = null
        handler.onDataMessage(message)

        assertEquals(1, shown.size)
        assertTrue(shown.single().title.startsWith("Ben"))
    }

    private object NoOpRegistrar : PushTokenRegistrar {
        override suspend fun registerCurrentToken() = Unit
        override suspend fun registerToken(token: String) = Unit
        override suspend fun removeCurrentTokenDocument() = Unit
        override suspend fun invalidateToken() = Unit
    }
}
