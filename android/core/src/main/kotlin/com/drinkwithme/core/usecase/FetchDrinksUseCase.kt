package com.drinkwithme.core.usecase

import com.drinkwithme.core.domain.Drink
import com.drinkwithme.core.domain.DrinkReader

/** Liefert alle Getränke, neueste zuerst. */
fun interface FetchDrinksUseCase {
    operator fun invoke(): List<Drink>
}

class DefaultFetchDrinksUseCase(private val reader: DrinkReader) : FetchDrinksUseCase {
    override fun invoke(): List<Drink> = reader.allDrinks().sortedByDescending { it.consumedAt }
}
