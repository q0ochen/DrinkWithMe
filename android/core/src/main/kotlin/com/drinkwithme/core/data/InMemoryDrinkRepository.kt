package com.drinkwithme.core.data

import com.drinkwithme.core.domain.Drink
import com.drinkwithme.core.domain.DrinkRepository
import java.util.concurrent.ConcurrentHashMap

/** Flüchtiges Repository für Tests und Compose-Previews. */
class InMemoryDrinkRepository(initial: List<Drink> = emptyList()) : DrinkRepository {
    private val drinks = ConcurrentHashMap(initial.associateBy { it.id })

    override fun allDrinks(): List<Drink> = drinks.values.toList()

    override fun save(drink: Drink) {
        drinks[drink.id] = drink
    }

    override fun delete(id: String) {
        drinks.remove(id)
    }
}
