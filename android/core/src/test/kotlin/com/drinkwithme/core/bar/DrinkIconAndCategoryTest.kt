package com.drinkwithme.core.bar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DrinkIconAndCategoryTest {
    @Test
    fun iconKeysMatchTheContract() {
        val expected = mapOf(
            DrinkIcon.WATER to "water", DrinkIcon.SPARKLING_WATER to "sparkling_water", DrinkIcon.COFFEE to "coffee",
            DrinkIcon.TEA to "tea", DrinkIcon.JUICE to "juice", DrinkIcon.APPLE_SPRITZER to "apple_spritzer",
            DrinkIcon.COLA to "cola", DrinkIcon.LEMONADE to "lemonade", DrinkIcon.ICED_TEA to "iced_tea",
            DrinkIcon.ENERGY_DRINK to "energy_drink", DrinkIcon.MILK to "milk", DrinkIcon.MALT_BEER to "malt_beer",
            DrinkIcon.BEER to "beer", DrinkIcon.WHEAT_BEER to "wheat_beer", DrinkIcon.RADLER to "radler",
            DrinkIcon.RED_WINE to "red_wine", DrinkIcon.WHITE_WINE to "white_wine",
            DrinkIcon.SPARKLING_WINE to "sparkling_wine", DrinkIcon.SHOT to "shot", DrinkIcon.LONGDRINK to "longdrink",
            DrinkIcon.COCKTAIL to "cocktail", DrinkIcon.OTHER to "other",
        )
        assertEquals(expected, DrinkIcon.entries.associateWith { it.key })
    }

    @Test
    fun iconKeysAreUniqueAndAcceptedByTheServer() {
        assertEquals(DrinkIcon.entries.size, DrinkIcon.entries.map { it.key }.toSet().size)
        DrinkIcon.entries.forEach { assertTrue(DrinkIcon.KEY_PATTERN.matches(it.key), it.key) }
    }

    @Test
    fun iconLookupByKey() {
        assertEquals(DrinkIcon.APPLE_SPRITZER, DrinkIcon.fromKey("apple_spritzer"))
        assertEquals(DrinkIcon.OTHER, DrinkIcon.fromKey("unicorn_milk"))
        assertNull(DrinkIcon.fromKeyOrNull("unicorn_milk"))
        DrinkIcon.entries.forEach { assertEquals(it, DrinkIcon.fromKeyOrNull(it.key)) }
    }

    @Test
    fun defaultIconPerCategory() {
        assertEquals(DrinkIcon.RED_WINE, DrinkIcon.defaultFor(DrinkCategory.WINE))
        assertEquals(DrinkIcon.SHOT, DrinkIcon.defaultFor(DrinkCategory.SPIRIT))
        assertEquals(DrinkIcon.LEMONADE, DrinkIcon.defaultFor(DrinkCategory.SOFT_DRINK))
        assertEquals(DrinkIcon.OTHER, DrinkIcon.defaultFor(DrinkCategory.OTHER))
    }

    @Test
    fun categoriesMatchTheContract() {
        assertEquals(
            listOf(
                "WATER", "COFFEE", "TEA", "JUICE", "SOFT_DRINK", "ENERGY_DRINK", "MILK", "BEER", "WINE",
                "SPARKLING_WINE", "SPIRIT", "COCKTAIL", "OTHER",
            ),
            DrinkCategory.entries.map { it.name },
        )
    }

    @Test
    fun categoryFromNameFallsBackToOther() {
        assertEquals(DrinkCategory.SPARKLING_WINE, DrinkCategory.fromName("SPARKLING_WINE"))
        assertEquals(DrinkCategory.OTHER, DrinkCategory.fromName("KOMBUCHA"))
    }

    @Test
    fun categoriesHaveGermanNamesWithoutEmojis() {
        assertEquals("Wasser", DrinkCategory.WATER.displayName)
        assertEquals("Sekt", DrinkCategory.SPARKLING_WINE.displayName)
        assertEquals("Spirituose", DrinkCategory.SPIRIT.displayName)
        DrinkCategory.entries.forEach { category ->
            assertTrue(category.displayName.all { it.isLetter() || it == ' ' }, category.name)
        }
    }
}
