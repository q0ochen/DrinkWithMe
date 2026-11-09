package com.drinkwithme.core

import com.drinkwithme.core.domain.DrinkCategory
import com.drinkwithme.core.sharing.GermanShareFormatter
import com.drinkwithme.core.sharing.VolumeFormatter
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class GermanShareFormatterTest {
    private val formatter = GermanShareFormatter(ZoneOffset.UTC)
    private val day = LocalDate.of(2026, 11, 9)

    @Test
    fun messageForSingleDrink() {
        val drink = Fixtures.drink("Rotwein", DrinkCategory.WINE, 150, note = "Mit Freunden")

        assertEquals(
            "🍷 Ich trinke gerade Rotwein (150 ml) um 14:30 Uhr. „Mit Freunden“ Trink mit mir! #DrinkWithMe",
            formatter.message(drink),
        )
    }

    @Test
    fun summaryListsDrinksAndTotal() {
        val drinks = listOf(
            Fixtures.drink("Wasser", DrinkCategory.WATER, 1_000),
            Fixtures.drink("Espresso", DrinkCategory.COFFEE, 500),
        )

        assertEquals(
            """
            Mein Getränke-Tag am 09.11.2026:
            💧 14:30 Wasser – 1 l
            ☕️ 14:30 Espresso – 500 ml
            Gesamt: 1,5 l
            #DrinkWithMe
            """.trimIndent(),
            formatter.summary(drinks, day),
        )
    }

    @Test
    fun summaryWithoutDrinks() {
        assertEquals(
            "Mein Getränke-Tag am 09.11.2026:\nNoch nichts getrunken – wer trinkt mit mir? #DrinkWithMe",
            formatter.summary(emptyList(), day),
        )
    }

    @Test
    fun volumeFormatting() {
        assertEquals("250 ml", VolumeFormatter.format(250))
        assertEquals("1 l", VolumeFormatter.format(1_000))
        assertEquals("1,3 l", VolumeFormatter.format(1_250))
        assertEquals("2,5 l", VolumeFormatter.format(2_480))
    }
}
