package com.drinkwithme.app.social.firebase

import com.drinkwithme.core.domain.Drink
import com.drinkwithme.core.domain.DrinkCategory
import com.drinkwithme.core.social.GroupMember
import com.drinkwithme.core.social.UserProfile
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupDrinkMapperTest {
    private val consumedAt = Instant.parse("2026-11-09T14:30:00Z")
    private val drink = Drink("d1", "Pils", DrinkCategory.BEER, 500, null, consumedAt)
    private val author = UserProfile("u1", "Anna")

    @Test
    fun documentHasExactlyTheContractFields() {
        val consumed = Any()
        val created = Any()

        val document = GroupDrinkMapper.toDocument(drink, author, consumed, created)

        assertEquals(
            setOf(
                "drinkId", "authorId", "authorName", "name", "category",
                "volumeInMilliliters", "note", "consumedAt", "createdAt",
            ),
            document.keys,
        )
        assertEquals("d1", document["drinkId"])
        assertEquals("u1", document["authorId"])
        assertEquals("Anna", document["authorName"])
        assertEquals("Pils", document["name"])
        assertEquals("BEER", document["category"])
        assertEquals(500, document["volumeInMilliliters"])
        assertTrue(document.containsKey("note"))
        assertNull(document["note"])
        assertTrue(document["consumedAt"] === consumed)
        assertTrue(document["createdAt"] === created)
    }

    @Test
    fun blankAuthorNameBecomesUnknown() {
        val document = GroupDrinkMapper.toDocument(drink, UserProfile("u1", "  "), Any(), Any())
        assertEquals("Unbekannt", document["authorName"])
    }

    @Test
    fun mapsFeedDocument() {
        val entry = GroupDrinkMapper.fromDocument(
            groupId = "g1",
            documentId = "d1",
            data = mapOf(
                "drinkId" to "d1",
                "authorId" to "u2",
                "authorName" to "Ben",
                "name" to "Rotwein",
                "category" to "WINE",
                "volumeInMilliliters" to 150L, // Firestore liefert Ganzzahlen als Long
                "note" to "Mit Freunden",
            ),
            consumedAt = consumedAt,
        )!!

        assertEquals("d1", entry.drinkId)
        assertEquals("g1", entry.groupId)
        assertEquals(GroupMember("u2", "Ben"), entry.author)
        assertEquals(DrinkCategory.WINE, entry.category)
        assertEquals(150, entry.volumeInMilliliters)
        assertEquals("Mit Freunden", entry.note)
        assertEquals(consumedAt, entry.consumedAt)
    }

    @Test
    fun unknownCategoryBecomesOther() {
        val entry = GroupDrinkMapper.fromDocument(
            "g1",
            "d1",
            mapOf("authorId" to "u2", "name" to "Kombucha", "category" to "KOMBUCHA", "volumeInMilliliters" to 330L),
            consumedAt,
        )!!
        assertEquals(DrinkCategory.OTHER, entry.category)
        assertEquals("d1", entry.drinkId)
    }

    @Test
    fun incompleteDocumentsAreSkipped() {
        val complete = mapOf("authorId" to "u2", "name" to "Wasser", "category" to "WATER", "volumeInMilliliters" to 250L)
        assertNull(GroupDrinkMapper.fromDocument("g1", "d1", complete, consumedAt = null))
        assertNull(GroupDrinkMapper.fromDocument("g1", "d1", complete - "authorId", consumedAt))
        assertNull(GroupDrinkMapper.fromDocument("g1", "d1", complete - "volumeInMilliliters", consumedAt))
    }
}
