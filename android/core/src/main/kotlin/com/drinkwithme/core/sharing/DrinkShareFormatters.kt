package com.drinkwithme.core.sharing

import com.drinkwithme.core.domain.Drink
import java.time.LocalDate

/** Formatiert ein einzelnes Getränk als teilbaren Text. */
fun interface DrinkMessageFormatter {
    fun message(drink: Drink): String
}

/** Formatiert eine Tagesübersicht als teilbaren Text. */
fun interface DrinkSummaryFormatter {
    fun summary(drinks: List<Drink>, day: LocalDate): String
}
