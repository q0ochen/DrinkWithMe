package com.drinkwithme.app.social

import com.drinkwithme.core.domain.Drink
import com.drinkwithme.core.domain.DrinkCategory
import com.drinkwithme.core.domain.DrinkVisibility
import com.drinkwithme.core.social.CurrentUserProvider
import com.drinkwithme.core.social.UserProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthorAwareDrinkPublisherTest {
    private val anna = UserProfile("A", "Anna")
    private val ben = UserProfile("B", "Ben")

    private val user = MutableStateFlow<UserProfile?>(null)
    private val users = object : CurrentUserProvider {
        override val currentUser = user
    }
    private val storage = InMemoryKeyValueStorage()
    private val pending = StoredPendingUnpublishStore(storage)
    private val authors = StoredPublishedDrinkAuthors(storage)
    private val server = FakeServer()
    private val publisher = AuthorAwareDrinkPublisher(users, server, authors, pending)

    @Test
    fun publishingRemembersTheAuthorBeforeTheServerConfirms() = runTest(UnconfinedTestDispatcher()) {
        user.value = anna
        val offline = CompletableDeferred<Unit>()
        server.shareResult = { offline.await() }

        val publishing = launch { publisher.publish(drink("d1")) }

        // Firestore hat den Batch eingereiht, der Server hat noch nicht bestätigt – der Autor ist trotzdem bekannt.
        assertTrue(publishing.isActive)
        assertEquals("A", authors.authorOf("d1"))
        offline.complete(Unit)
        assertEquals(listOf("A_d1@gA"), server.documents.toList())
    }

    @Test
    fun privateDrinksOrDrinksWithoutGroupsAreNeitherSharedNorRemembered() = runTest {
        user.value = anna
        publisher.publish(drink("d1", DrinkVisibility.PRIVATE))
        server.groups.remove("A")
        publisher.publish(drink("d2"))

        assertTrue(server.documents.isEmpty())
        assertNull(authors.authorOf("d1"))
        assertNull(authors.authorOf("d2"))
    }

    @Test
    fun deletingWhileTheAuthorIsSignedInRemovesItNowAndForgetsTheAuthor() = runTest {
        user.value = anna
        publisher.publish(drink("d1"))

        publisher.unpublish("d1")

        assertTrue(server.documents.isEmpty())
        assertNull(authors.authorOf("d1"))
        assertTrue(pending.drinkIdsFor("A").isEmpty())
    }

    @Test
    fun deletingWhileAnotherAccountIsSignedInWaitsForTheAuthor() = runTest {
        user.value = anna
        publisher.publish(drink("d1"))
        user.value = ben
        pending.rememberSignedInUser("B")

        publisher.unpublish("d1")

        assertEquals(listOf("A_d1@gA"), server.documents.toList(), "Ben darf Annas Kopie nicht löschen.")
        assertEquals(listOf("d1"), pending.drinkIdsFor("A"))
        assertTrue(pending.drinkIdsFor("B").isEmpty())

        user.value = anna
        publisher.retryPendingUnpublishes("A")

        assertTrue(server.documents.isEmpty())
        assertTrue(pending.drinkIdsFor("A").isEmpty())
        assertNull(authors.authorOf("d1"))
    }

    @Test
    fun deletingWhileSignedOutWaitsForTheAuthorNotForTheLastSignedInUser() = runTest {
        user.value = anna
        publisher.publish(drink("d1"))
        user.value = ben
        pending.rememberSignedInUser("B")
        user.value = null

        publisher.unpublish("d1")

        assertEquals(listOf("d1"), pending.drinkIdsFor("A"))
        assertTrue(pending.drinkIdsFor("B").isEmpty())
        assertTrue(server.removals.isEmpty())
    }

    @Test
    fun accountSwitchOnOneDeviceEndToEnd() = runTest(UnconfinedTestDispatcher()) {
        // Anna teilt, meldet sich ab; Ben meldet sich an und wieder ab; dann wird das Getränk gelöscht.
        PendingUnpublishSync(users, pending, publisher).start(backgroundScope)
        user.value = anna
        publisher.publish(drink("d1"))
        user.value = null
        user.value = ben
        user.value = null
        publisher.unpublish("d1")

        user.value = ben
        assertEquals(listOf("A_d1@gA"), server.documents.toList(), "Bens Anmeldung löst Annas Löschung nicht aus.")

        user.value = anna
        assertTrue(server.documents.isEmpty())
        assertTrue(pending.drinkIdsFor("A").isEmpty())
        assertNull(authors.authorOf("d1"))
    }

    @Test
    fun unknownAuthorFallsBackToTheSignedInUser() = runTest {
        server.documents += "B_d1@gB" // z. B. vor diesem Update geteilt
        user.value = ben

        publisher.unpublish("d1")

        assertTrue(server.documents.isEmpty())
    }

    @Test
    fun unknownAuthorWhileSignedOutFallsBackToTheLastSignedInUser() = runTest {
        pending.rememberSignedInUser("B")

        publisher.unpublish("d1")

        assertEquals(listOf("d1"), pending.drinkIdsFor("B"))
    }

    @Test
    fun withoutAnySignInNothingIsRecorded() = runTest {
        publisher.unpublish("d1")

        assertTrue(storage.values.isEmpty())
    }

    @Test
    fun failedRemovalIsQueuedForTheAuthorAndKeepsTheAuthor() = runTest {
        user.value = anna
        publisher.publish(drink("d1"))
        server.removeResult = { throw IllegalStateException("offline") }

        assertFailsWith<IllegalStateException> { publisher.unpublish("d1") }

        assertEquals(listOf("d1"), pending.drinkIdsFor("A"))
        assertEquals("A", authors.authorOf("d1"))
    }

    @Test
    fun erasingTheAccountForgetsItsAuthorship() = runTest {
        user.value = anna
        publisher.publish(drink("d1"))
        CompositeLocalUserDataEraser(listOf(pending, authors)).eraseUser("A")
        user.value = null

        publisher.unpublish("d1")

        // Kein Eintrag mit der Nutzer-ID des gelöschten Kontos entsteht neu.
        assertTrue(storage.values.isEmpty())
    }

    private fun drink(id: String, visibility: DrinkVisibility = DrinkVisibility.FRIENDS) =
        Drink(id, "Helles", DrinkCategory.BEER, 500, null, Instant.parse("2026-11-09T20:00:00Z"), visibility)

    /**
     * Server mit den Sicherheitsregeln im Kleinen: Jedes Konto ist in einer eigenen Runde („g“ + Nutzer-ID);
     * schreiben und löschen darf nur das angemeldete Konto seine eigenen Kopien.
     */
    private inner class FakeServer : GroupDrinkGateway {
        val groups = mutableMapOf("A" to listOf("gA"), "B" to listOf("gB"))
        val documents = linkedSetOf<String>()
        val removals = mutableListOf<String>()
        var shareResult: suspend () -> Unit = {}
        var removeResult: suspend () -> Unit = {}

        override suspend fun groupIdsOf(userId: String): List<String> = groups[userId].orEmpty()

        override suspend fun share(drink: Drink, author: UserProfile, groupIds: List<String>) {
            check(user.value?.id == author.id) { "permission-denied" }
            shareResult()
            groupIds.forEach { documents += "${author.id}_${drink.id}@$it" }
        }

        override suspend fun remove(authorId: String, drinkId: String, groupIds: List<String>) {
            check(user.value?.id == authorId) { "permission-denied" }
            removeResult()
            removals += "${authorId}_$drinkId"
            groupIds.forEach { documents -= "${authorId}_$drinkId@$it" }
        }
    }
}
