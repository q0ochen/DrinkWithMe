package com.drinkwithme.app.social

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoredPublishedDrinkAuthorsTest {
    private val storage = InMemoryKeyValueStorage()
    private val authors = StoredPublishedDrinkAuthors(storage)

    @Test
    fun remembersTheAuthorAcrossInstances() {
        assertNull(authors.authorOf("d1"))

        authors.remember("d1", "u1")

        assertEquals("u1", StoredPublishedDrinkAuthors(storage).authorOf("d1"))
    }

    @Test
    fun aDrinkHasExactlyOneAuthor() {
        authors.remember("d1", "u1")
        authors.remember("d1", "u2")

        assertEquals("u2", authors.authorOf("d1"))
        authors.forget("d1", "u2")
        assertNull(authors.authorOf("d1"))
    }

    @Test
    fun forgetOnlyRemovesTheEntryOfThatAuthor() {
        authors.remember("d1", "u1")
        authors.remember("d2", "u1")

        authors.forget("d1", "u2")
        assertEquals("u1", authors.authorOf("d1"))

        authors.forget("d1", "u1")
        assertNull(authors.authorOf("d1"))
        assertEquals("u1", authors.authorOf("d2"))
    }

    @Test
    fun eraseUserRemovesAllDrinksOfThatUserOnly() {
        authors.remember("d1", "u1")
        authors.remember("d2", "u2")
        authors.remember("d3", "u1")

        authors.eraseUser("u1")

        assertNull(authors.authorOf("d1"))
        assertNull(authors.authorOf("d3"))
        assertEquals("u2", authors.authorOf("d2"))
    }

    @Test
    fun oldestEntriesAreDroppedBeyondTheLimit() {
        val small = StoredPublishedDrinkAuthors(storage, maximumEntries = 2)
        small.remember("d1", "u1")
        small.remember("d2", "u1")
        small.remember("d3", "u1")

        assertNull(small.authorOf("d1"))
        assertEquals("u1", small.authorOf("d2"))
        assertEquals("u1", small.authorOf("d3"))
    }

    @Test
    fun valuesWithSeparatorsOrBlanksAreIgnored() {
        authors.remember("a\tb", "u1")
        authors.remember("d1", "u\n1")
        authors.remember(" ", "u1")

        assertTrue(storage.values.isEmpty())
    }

    @Test
    fun sharesTheStorageWithThePendingStoreWithoutInterfering() {
        val pending = StoredPendingUnpublishStore(storage)
        authors.remember("d1", "u1")
        pending.add("u1", "d2")
        pending.rememberSignedInUser("u1")

        pending.eraseUser("u1")

        assertEquals("u1", authors.authorOf("d1"))
        authors.eraseUser("u1")
        assertTrue(storage.values.isEmpty())
    }
}
