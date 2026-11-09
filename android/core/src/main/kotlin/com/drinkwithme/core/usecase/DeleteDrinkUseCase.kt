package com.drinkwithme.core.usecase

import com.drinkwithme.core.domain.DrinkWriter

/** Entfernt ein Getränk. */
fun interface DeleteDrinkUseCase {
    operator fun invoke(id: String)
}

class DefaultDeleteDrinkUseCase(private val writer: DrinkWriter) : DeleteDrinkUseCase {
    override fun invoke(id: String) = writer.delete(id)
}
