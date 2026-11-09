package com.drinkwithme.app.social

import com.drinkwithme.core.social.CurrentUserProvider
import com.drinkwithme.core.social.UserProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PendingUnpublishSyncTest {
    private val user = MutableStateFlow<UserProfile?>(null)
    private val users = object : CurrentUserProvider {
        override val currentUser = user
    }
    private val store = StoredPendingUnpublishStore(InMemoryKeyValueStorage())
    private val retried = mutableListOf<String>()
    private val errors = mutableListOf<Throwable>()
    private var retry: suspend (String) -> Unit = { userId ->
        retried += userId
        store.drinkIdsFor(userId).forEach { store.remove(userId, it) }
    }
    private val sync = PendingUnpublishSync(users, store, { userId -> retry(userId) }, onError = { errors += it })

    @Test
    fun remembersEverySignedInUser() = runTest(UnconfinedTestDispatcher()) {
        sync.start(backgroundScope)

        user.value = UserProfile("u1", "Anna")
        user.value = null

        assertEquals("u1", store.lastSignedInUserId())
    }

    @Test
    fun pendingDeletionsAreRetriedWhenTheSameUserSignsInAgain() = runTest(UnconfinedTestDispatcher()) {
        sync.start(backgroundScope)
        user.value = UserProfile("u1", "Anna")
        user.value = null
        store.addForLastSignedInUser("d1") // abgemeldet gelöscht

        user.value = UserProfile("u2", "Ben")
        assertTrue(retried.isEmpty(), "Ein anderer Nutzer darf die Löschungen von u1 nicht auslösen.")
        assertEquals(listOf("d1"), store.drinkIdsFor("u1"))

        user.value = UserProfile("u1", "Anna")
        assertEquals(listOf("u1"), retried)
        assertTrue(store.drinkIdsFor("u1").isEmpty())
    }

    @Test
    fun pendingDeletionsAreRetriedOnAppStartWhenAlreadySignedIn() = runTest(UnconfinedTestDispatcher()) {
        store.add("u1", "d1")
        user.value = UserProfile("u1", "Anna")

        sync.start(backgroundScope)

        assertEquals(listOf("u1"), retried)
    }

    @Test
    fun failuresAreReportedAndEntriesKept() = runTest(UnconfinedTestDispatcher()) {
        store.add("u1", "d1")
        retry = { throw IllegalStateException("offline") }

        sync.start(backgroundScope)
        user.value = UserProfile("u1", "Anna")

        assertEquals(1, errors.size)
        assertEquals(listOf("d1"), store.drinkIdsFor("u1"))
    }

    @Test
    fun signingOutCancelsTheRetry() = runTest(UnconfinedTestDispatcher()) {
        store.add("u1", "d1")
        val never = CompletableDeferred<Unit>()
        var cancelled = false
        retry = {
            try {
                never.await()
            } finally {
                cancelled = true
            }
        }
        sync.start(backgroundScope)
        user.value = UserProfile("u1", "Anna")

        user.value = null

        assertTrue(cancelled)
        assertTrue(errors.isEmpty())
        assertEquals(listOf("d1"), store.drinkIdsFor("u1"))
    }
}
