package com.drinkwithme.core

import com.drinkwithme.core.data.InMemoryDrinkRepository
import com.drinkwithme.core.domain.DefaultDrinkValidator
import com.drinkwithme.core.domain.Drink
import com.drinkwithme.core.domain.DrinkCategory
import com.drinkwithme.core.domain.DrinkDraft
import com.drinkwithme.core.domain.DrinkValidationException
import com.drinkwithme.core.sharing.GermanShareFormatter
import com.drinkwithme.core.usecase.DefaultDeleteDrinkUseCase
import com.drinkwithme.core.usecase.DefaultFetchDrinksUseCase
import com.drinkwithme.core.usecase.DefaultLogDrinkUseCase
import com.drinkwithme.core.usecase.DefaultShareDrinksUseCase
import java.time.Duration
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UseCaseTest {
    @Test
    fun logDrinkTrimsInputAndStoresDrink() {
        val repository = InMemoryDrinkRepository()
        val logDrink = DefaultLogDrinkUseCase(DefaultDrinkValidator(), repository, Fixtures.clock) { "id-1" }

        val drink = logDrink(DrinkDraft("  Cappuccino ", DrinkCategory.COFFEE, 200, note = "  "))

        assertEquals(Drink("id-1", "Cappuccino", DrinkCategory.COFFEE, 200, null, Fixtures.afternoon), drink)
        assertEquals(listOf(drink), repository.allDrinks())
    }

    @Test
    fun logDrinkDoesNotStoreInvalidDraft() {
        val repository = InMemoryDrinkRepository()
        val logDrink = DefaultLogDrinkUseCase(DefaultDrinkValidator(), repository)

        assertFailsWith<DrinkValidationException> { logDrink(DrinkDraft("", DrinkCategory.WATER, 200)) }
        assertTrue(repository.allDrinks().isEmpty())
    }

    @Test
    fun fetchDrinksSortsNewestFirst() {
        val older = Fixtures.drink("Tee", at = Fixtures.afternoon.minus(Duration.ofHours(1)))
        val newer = Fixtures.drink("Kaffee")

        assertEquals(listOf(newer, older), DefaultFetchDrinksUseCase(InMemoryDrinkRepository(listOf(older, newer)))())
    }

    @Test
    fun deleteDrinkRemovesIt() {
        val drink = Fixtures.drink()
        val repository = InMemoryDrinkRepository(listOf(drink))

        DefaultDeleteDrinkUseCase(repository)(drink.id)

        assertTrue(repository.allDrinks().isEmpty())
    }

    @Test
    fun todaySummaryOnlyContainsTodaysDrinks() {
        val formatter = GermanShareFormatter(ZoneOffset.UTC)
        val share = DefaultShareDrinksUseCase(formatter, formatter, Fixtures.clock)
        val today = Fixtures.drink("Wasser", DrinkCategory.WATER, 500)
        val yesterday = Fixtures.drink("Bier", DrinkCategory.BEER, 330, at = Fixtures.afternoon.minus(Duration.ofDays(1)))

        val summary = share.todaySummary(listOf(today, yesterday))

        assertTrue("Wasser" in summary)
        assertFalse("Bier" in summary)
    }
}
