package com.drinkwithme.core.bar

import com.drinkwithme.core.barItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BarRepositoryAndUseCaseTest {
    private val water = barItem("Wasser", sortOrder = 0)
    private val tea = barItem("Tee", sortOrder = 1)
    private val cola = barItem("Cola", sortOrder = 2)

    @Test
    fun itemsAreSortedBySortOrder() {
        val repository = InMemoryBarRepository(listOf(cola, water, tea))
        assertEquals(listOf(water, tea, cola), repository.items())
    }

    @Test
    fun saveAddsOrReplaces() {
        val repository = InMemoryBarRepository(listOf(water))
        repository.save(tea)
        repository.save(water.copy(servingMilliliters = 500))
        assertEquals(listOf(water.copy(servingMilliliters = 500), tea), repository.items())
        assertEquals(tea, repository.item(tea.id))
    }

    @Test
    fun deleteRemovesAndIgnoresUnknownIds() {
        val repository = InMemoryBarRepository(listOf(water, tea))
        repository.delete(water.id)
        repository.delete("unbekannt")
        assertEquals(listOf(tea), repository.items())
        assertNull(repository.item(water.id))
    }

    @Test
    fun reorderPutsNamedItemsFirstAndKeepsTheRest() {
        val repository = InMemoryBarRepository(listOf(water, tea, cola))
        repository.reorder(listOf(cola.id, "unbekannt", cola.id))
        assertEquals(listOf(cola.id, water.id, tea.id), repository.items().map { it.id })
        assertEquals(listOf(0, 1, 2), repository.items().map { it.sortOrder })

        repository.reorder(listOf(tea.id, water.id, cola.id))
        assertEquals(listOf(tea.id, water.id, cola.id), repository.items().map { it.id })
    }

    @Test
    fun defaultItemLookupUsesItems() {
        val reader = object : BarReader {
            override fun items() = listOf(water, tea)
        }
        assertEquals(tea, reader.item(tea.id))
        assertNull(reader.item("unbekannt"))
    }

    @Test
    fun saveBarItemNormalizesValidatesAndSaves() {
        val repository = InMemoryBarRepository()
        val saved = SaveBarItemUseCase(repository)(water.copy(name = " Wasser ", aliases = listOf(" Leitungswasser ", "")))
        assertEquals(water.copy(aliases = listOf("Leitungswasser")), saved)
        assertEquals(listOf(saved), repository.items())
    }

    @Test
    fun saveBarItemRejectsInvalidItemsWithoutSaving() {
        val repository = InMemoryBarRepository()
        assertFailsWith<InvalidBarItemException> { SaveBarItemUseCase(repository)(water.copy(servingMilliliters = 0)) }
        assertTrue(repository.items().isEmpty())
    }

    @Test
    fun setUpBarSavesTheDefaultSelectionInOrder() {
        val repository = InMemoryBarRepository()
        val saved = SetUpBarFromCatalogUseCase(repository)()
        assertEquals(DefaultBarCatalog.defaultSelection(), saved)
        assertEquals(saved, repository.items())
    }

    @Test
    fun setUpBarUsesTheChosenOrderAndIgnoresUnknownIds() {
        val repository = InMemoryBarRepository()
        val saved = SetUpBarFromCatalogUseCase(repository)(
            listOf(DefaultBarCatalog.SEKT, "katalog-unbekannt", DefaultBarCatalog.WASSER, DefaultBarCatalog.SEKT),
        )
        assertEquals(listOf("Sekt trocken", "Wasser"), saved.map { it.name })
        assertEquals(listOf(0, 1), repository.items().map { it.sortOrder })
    }
}
