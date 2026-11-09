package com.drinkwithme.app.social

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoredPendingUnpublishStoreTest {
    private val storage = InMemoryKeyValueStorage()
    private val store = StoredPendingUnpublishStore(storage)

    @Test
    fun remembersLastSignedInUserAcrossInstances() {
        assertNull(store.lastSignedInUserId())

        store.rememberSignedInUser("u1")

        assertEquals("u1", StoredPendingUnpublishStore(storage).lastSignedInUserId())
    }

    @Test
    fun signedOutDeletionIsRecordedForTheLastSignedInUser() {
        store.rememberSignedInUser("u1")

        assertTrue(store.addForLastSignedInUser("d1"))

        assertEquals(listOf("d1"), store.drinkIdsFor("u1"))
        assertTrue(store.drinkIdsFor("u2").isEmpty())
    }

    @Test
    fun withoutAnyPreviousSignInNothingIsRecorded() {
        assertFalse(store.addForLastSignedInUser("d1"))
        assertTrue(storage.values.isEmpty())
    }

    @Test
    fun entriesAreKeptPerUserAndPersisted() {
        store.add("u1", "d1")
        store.add("u2", "d2")
        store.add("u1", "d3")
        store.add("u1", "d1") // doppelt

        val reloaded = StoredPendingUnpublishStore(storage)
        assertEquals(listOf("d1", "d3"), reloaded.drinkIdsFor("u1"))
        assertEquals(listOf("d2"), reloaded.drinkIdsFor("u2"))
    }

    @Test
    fun removeDeletesOnlyThatEntry() {
        store.add("u1", "d1")
        store.add("u1", "d2")
        store.add("u2", "d1")

        store.remove("u1", "d1")

        assertEquals(listOf("d2"), store.drinkIdsFor("u1"))
        assertEquals(listOf("d1"), store.drinkIdsFor("u2"))
    }

    @Test
    fun signingInAsAnotherUserKeepsTheFirstUsersEntries() {
        store.rememberSignedInUser("u1")
        store.addForLastSignedInUser("d1")

        store.rememberSignedInUser("u2")

        assertEquals("u2", store.lastSignedInUserId())
        assertEquals(listOf("d1"), store.drinkIdsFor("u1"))
    }

    @Test
    fun eraseUserRemovesEntriesAndForgetsTheUser() {
        store.rememberSignedInUser("u1")
        store.add("u1", "d1")
        store.add("u2", "d2")

        store.eraseUser("u1")

        assertTrue(store.drinkIdsFor("u1").isEmpty())
        assertEquals(listOf("d2"), store.drinkIdsFor("u2"))
        assertNull(store.lastSignedInUserId())
        assertFalse(store.addForLastSignedInUser("d3"))
    }

    @Test
    fun oldestEntriesAreDroppedBeyondTheLimit() {
        val small = StoredPendingUnpublishStore(storage, maximumEntries = 2)
        small.add("u1", "d1")
        small.add("u1", "d2")
        small.add("u1", "d3")

        assertEquals(listOf("d2", "d3"), small.drinkIdsFor("u1"))
    }

    @Test
    fun valuesWithSeparatorsOrBlanksAreIgnored() {
        store.add("u1", "a\tb")
        store.add("u1", "a\nb")
        store.add(" ", "d1")
        store.rememberSignedInUser("")

        assertTrue(storage.values.isEmpty())
    }

    @Test
    fun corruptStoredLinesAreSkipped() {
        storage.putString("pendingUnpublishes", "u1\td1\nkaputt\n\nu1\td2\tzuviel")

        assertEquals(listOf("d1"), store.drinkIdsFor("u1"))
    }
}
