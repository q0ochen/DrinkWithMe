package com.drinkwithme.core.social

import com.drinkwithme.core.Fixtures
import com.drinkwithme.core.data.InMemoryDrinkRepository
import com.drinkwithme.core.domain.DrinkVisibility
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PublishingDrinkRepositoryTest {
    private val local = InMemoryDrinkRepository()

    @Test
    fun savesLocallyAndPublishesFriendsDrinks() = runTest {
        val publisher = RecordingPublisher()
        val repository = PublishingDrinkRepository(local, publisher, this)
        val drink = Fixtures.drink()

        repository.save(drink)
        advanceUntilIdle()

        assertEquals(listOf(drink), local.allDrinks())
        assertEquals(listOf(drink), publisher.published)
    }

    @Test
    fun privateDrinksStayLocal() = runTest {
        val publisher = RecordingPublisher()
        val repository = PublishingDrinkRepository(local, publisher, this)
        val drink = Fixtures.drink().copy(visibility = DrinkVisibility.PRIVATE)

        repository.save(drink)
        advanceUntilIdle()

        assertEquals(listOf(drink), local.allDrinks())
        assertTrue(publisher.published.isEmpty())
    }

    @Test
    fun deleteRemovesLocallyAndUnpublishes() = runTest {
        val publisher = RecordingPublisher()
        val repository = PublishingDrinkRepository(local, publisher, this)
        val drink = Fixtures.drink()
        local.save(drink)

        repository.delete(drink.id)
        advanceUntilIdle()

        assertTrue(local.allDrinks().isEmpty())
        assertEquals(listOf(drink.id), publisher.unpublished)
    }

    @Test
    fun publishFailureDoesNotBreakLocalSaveAndIsReported() = runTest {
        val errors = mutableListOf<Throwable>()
        val failure = IllegalStateException("offline")
        val repository = PublishingDrinkRepository(local, RecordingPublisher(failWith = failure), this) { errors += it }
        val drink = Fixtures.drink()

        repository.save(drink)
        advanceUntilIdle()

        assertEquals(listOf(drink), local.allDrinks())
        assertEquals(listOf<Throwable>(failure), errors)
    }
}

