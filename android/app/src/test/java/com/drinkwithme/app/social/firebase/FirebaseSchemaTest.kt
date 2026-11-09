package com.drinkwithme.app.social.firebase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class FirebaseSchemaTest {

    @Test
    fun sharedDrinkDocumentIdStartsWithTheAuthor() {
        assertEquals("u1_d1", FirestoreSchema.GroupDrink.documentId(authorId = "u1", drinkId = "d1"))
        assertEquals(
            "AbC123_8f14e45f-ceea-467a-9575-2a6f1c6c5d1b",
            FirestoreSchema.GroupDrink.documentId("AbC123", "8f14e45f-ceea-467a-9575-2a6f1c6c5d1b"),
        )
    }

    @Test
    fun sameDrinkIdOfDifferentAuthorsGivesDifferentDocuments() {
        assertNotEquals(
            FirestoreSchema.GroupDrink.documentId("victim", "d1"),
            FirestoreSchema.GroupDrink.documentId("other", "d1"),
        )
    }

    @Test
    fun deleteAccountFunctionNameMatchesTheContract() {
        assertEquals("deleteAccount", CloudFunctions.DELETE_ACCOUNT)
        assertEquals("europe-west3", CloudFunctions.REGION)
    }
}
