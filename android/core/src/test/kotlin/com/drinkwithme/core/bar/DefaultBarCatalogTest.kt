package com.drinkwithme.core.bar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultBarCatalogTest {
    /** Name · Zucker g/100 ml · Vol.-% · Portion ml · Icon · Kategorie – exakt laut Vertrag. */
    private data class Row(
        val name: String,
        val sugar: Double,
        val alcohol: Double,
        val serving: Int,
        val icon: DrinkIcon,
        val category: DrinkCategory,
    )

    private val expected = listOf(
        Row("Wasser", 0.0, 0.0, 250, DrinkIcon.WATER, DrinkCategory.WATER),
        Row("Mineralwasser", 0.0, 0.0, 250, DrinkIcon.SPARKLING_WATER, DrinkCategory.WATER),
        Row("Kaffee", 0.0, 0.0, 200, DrinkIcon.COFFEE, DrinkCategory.COFFEE),
        Row("Tee", 0.0, 0.0, 250, DrinkIcon.TEA, DrinkCategory.TEA),
        Row("Cola", 10.6, 0.0, 330, DrinkIcon.COLA, DrinkCategory.SOFT_DRINK),
        Row("Cola zero", 0.0, 0.0, 330, DrinkIcon.COLA, DrinkCategory.SOFT_DRINK),
        Row("Limonade", 8.0, 0.0, 330, DrinkIcon.LEMONADE, DrinkCategory.SOFT_DRINK),
        Row("Eistee", 7.5, 0.0, 500, DrinkIcon.ICED_TEA, DrinkCategory.SOFT_DRINK),
        Row("Energydrink", 11.0, 0.0, 250, DrinkIcon.ENERGY_DRINK, DrinkCategory.ENERGY_DRINK),
        Row("Orangensaft", 9.0, 0.0, 200, DrinkIcon.JUICE, DrinkCategory.JUICE),
        Row("Apfelsaft", 10.0, 0.0, 200, DrinkIcon.JUICE, DrinkCategory.JUICE),
        Row("Apfelschorle", 5.5, 0.0, 500, DrinkIcon.APPLE_SPRITZER, DrinkCategory.JUICE),
        Row("Malzbier", 9.0, 0.0, 330, DrinkIcon.MALT_BEER, DrinkCategory.SOFT_DRINK),
        Row("Milch", 4.8, 0.0, 200, DrinkIcon.MILK, DrinkCategory.MILK),
        Row("Pils", 0.3, 4.9, 500, DrinkIcon.BEER, DrinkCategory.BEER),
        Row("Weizenbier", 0.3, 5.4, 500, DrinkIcon.WHEAT_BEER, DrinkCategory.BEER),
        Row("Alkoholfreies Bier", 2.5, 0.4, 500, DrinkIcon.BEER, DrinkCategory.BEER),
        Row("Radler", 5.0, 2.5, 500, DrinkIcon.RADLER, DrinkCategory.BEER),
        Row("Rotwein trocken", 0.3, 12.0, 125, DrinkIcon.RED_WINE, DrinkCategory.WINE),
        Row("Weißwein trocken", 0.3, 11.5, 125, DrinkIcon.WHITE_WINE, DrinkCategory.WINE),
        Row("Sekt trocken", 2.5, 11.0, 100, DrinkIcon.SPARKLING_WINE, DrinkCategory.SPARKLING_WINE),
        Row("Schnaps", 0.0, 40.0, 20, DrinkIcon.SHOT, DrinkCategory.SPIRIT),
        Row("Longdrink", 8.7, 5.3, 240, DrinkIcon.LONGDRINK, DrinkCategory.SPIRIT),
        Row("Cocktail", 8.0, 10.0, 200, DrinkIcon.COCKTAIL, DrinkCategory.COCKTAIL),
    )

    @Test
    fun containsExactlyTheContractValues() {
        val actual = DefaultBarCatalog.items.map {
            Row(it.name, it.sugarGramsPer100Ml, it.alcoholPercent, it.servingMilliliters, it.icon, it.category)
        }
        assertEquals(expected, actual)
    }

    @Test
    fun onlyMilkAndCocktailAreMarkedAsEstimates() {
        val estimates = DefaultBarCatalog.entries.filter { it.isEstimate }
        assertEquals(listOf("Milch", "Cocktail"), estimates.map { it.item.name })
        assertEquals("Zucker = Milchzucker (Schätzwert)", DefaultBarCatalog.entry(DefaultBarCatalog.MILCH)?.note)
        assertEquals("Schätzwert", DefaultBarCatalog.entry(DefaultBarCatalog.COCKTAIL)?.note)
        assertEquals("Spirituose + Cola", DefaultBarCatalog.entry(DefaultBarCatalog.LONGDRINK)?.note)
    }

    @Test
    fun idsAreStableUniqueAndPrefixed() {
        val ids = DefaultBarCatalog.items.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.startsWith(DefaultBarCatalog.ID_PREFIX) })
        assertEquals("katalog-apfelschorle", DefaultBarCatalog.APFELSCHORLE)
    }

    @Test
    fun everyItemIsValidAndMarkedAsDefault() {
        val validator = DefaultBarItemValidator()
        DefaultBarCatalog.items.forEach { item ->
            assertTrue(validator.validate(item).isEmpty(), item.name)
            assertEquals(ValueSource.DEFAULT, item.valueSource)
        }
    }

    @Test
    fun sortOrderFollowsTheList() {
        assertEquals(DefaultBarCatalog.items.indices.toList(), DefaultBarCatalog.items.map { it.sortOrder })
    }

    @Test
    fun defaultSelectionIsWaterCoffeeTeaAppleSpritzerColaPilsRedWine() {
        val selection = DefaultBarCatalog.defaultSelection()
        assertEquals(
            listOf("Wasser", "Kaffee", "Tee", "Apfelschorle", "Cola", "Pils", "Rotwein trocken"),
            selection.map { it.name },
        )
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), selection.map { it.sortOrder })
        assertEquals(DefaultBarCatalog.defaultSelectionIds, selection.map { it.id })
    }

    @Test
    fun aliasesHelpVoiceInput() {
        assertTrue("Schorle" in DefaultBarCatalog.item(DefaultBarCatalog.APFELSCHORLE)!!.aliases)
        assertTrue("Apfelsaftschorle" in DefaultBarCatalog.item(DefaultBarCatalog.APFELSCHORLE)!!.aliases)
        assertTrue("Bier" in DefaultBarCatalog.item(DefaultBarCatalog.PILS)!!.aliases)
    }

    @Test
    fun unknownIdsReturnNull() {
        assertNull(DefaultBarCatalog.item("katalog-unbekannt"))
        assertNull(DefaultBarCatalog.entry("wasser"))
    }
}
