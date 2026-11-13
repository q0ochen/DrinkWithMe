package com.drinkwithme.core.speech

import com.drinkwithme.core.bar.DefaultBarCatalog
import com.drinkwithme.core.bar.DrinkCategory
import com.drinkwithme.core.bar.DrinkIcon
import com.drinkwithme.core.social.FriendDrinkEvent
import com.drinkwithme.core.social.UserProfile
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GermanArticlesAndEventTextsTest {
    @Test
    fun articlesForTheWholeCatalog() {
        val expected = mapOf(
            "Wasser" to "ein Wasser", "Mineralwasser" to "ein Mineralwasser", "Kaffee" to "einen Kaffee",
            "Tee" to "einen Tee", "Cola" to "eine Cola", "Cola zero" to "eine Cola zero", "Limonade" to "eine Limonade",
            "Eistee" to "einen Eistee", "Energydrink" to "einen Energydrink", "Orangensaft" to "einen Orangensaft",
            "Apfelsaft" to "einen Apfelsaft", "Apfelschorle" to "eine Apfelschorle", "Malzbier" to "ein Malzbier",
            "Milch" to "eine Milch", "Pils" to "ein Pils", "Weizenbier" to "ein Weizenbier",
            "Alkoholfreies Bier" to "ein Alkoholfreies Bier", "Radler" to "ein Radler",
            "Rotwein trocken" to "einen Rotwein trocken", "Weißwein trocken" to "einen Weißwein trocken",
            "Sekt trocken" to "einen Sekt trocken", "Schnaps" to "einen Schnaps", "Longdrink" to "einen Longdrink",
            "Cocktail" to "einen Cocktail",
        )
        assertEquals(expected.keys.toList(), DefaultBarCatalog.items.map { it.name })
        expected.forEach { (name, withArticle) -> assertEquals(withArticle, GermanArticles.withIndefiniteArticle(name)) }
    }

    @Test
    fun containersAndExistingArticles() {
        assertEquals("ein Glas Wein", GermanArticles.withIndefiniteArticle("Glas Wein"))
        assertEquals("eine Tasse Kakao", GermanArticles.withIndefiniteArticle("Tasse Kakao"))
        assertEquals("eine Flasche Wasser", GermanArticles.withIndefiniteArticle("Flasche Wasser"))
        assertEquals("Ein Glas Milch", GermanArticles.withIndefiniteArticle("Ein Glas Milch"))
        assertEquals("2 Bier", GermanArticles.withIndefiniteArticle("2 Bier"))
        assertEquals("eine Club-Mate", GermanArticles.withIndefiniteArticle("Club-Mate"))
        assertEquals("einen Latte Macchiato", GermanArticles.withIndefiniteArticle("Latte Macchiato"))
        assertEquals("Kombucha", GermanArticles.withIndefiniteArticle(" Kombucha "))
        assertNull(GermanArticles.genderOf("Kombucha"))
        assertEquals(GrammaticalGender.NEUTER, GermanArticles.genderOf("Kölsch"))
    }

    @Test
    fun eventDisplayTexts() {
        val event = FriendDrinkEvent(
            "e-1", UserProfile("u-1", "Anna", "https://example.org/a.png"), "Apfelschorle", DrinkIcon.APPLE_SPRITZER,
            DrinkCategory.JUICE, 500, Instant.parse("2026-11-10T17:00:00Z"),
        )
        assertEquals("Anna trinkt gerade", EventTexts.notificationTitle(event))
        assertEquals("Apfelschorle (500 ml)", EventTexts.notificationBody(event))
        assertEquals("Anna trinkt eine Apfelschorle.", EventTexts.headline(event))
        assertEquals("Ben trinkt gerade", EventTexts.notificationTitle(" Ben "))
        assertEquals("Radler (1500 ml)", EventTexts.notificationBody("Radler", 1500))
    }
}
