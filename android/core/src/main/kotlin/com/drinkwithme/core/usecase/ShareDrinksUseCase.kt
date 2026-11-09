package com.drinkwithme.core.usecase

import com.drinkwithme.core.domain.Drink
import com.drinkwithme.core.sharing.DrinkMessageFormatter
import com.drinkwithme.core.sharing.DrinkSummaryFormatter
import java.time.Clock
import java.time.LocalDate

/** Erzeugt teilbare Texte für einzelne Getränke oder den heutigen Tag. */
interface ShareDrinksUseCase {
    fun message(drink: Drink): String
    fun todaySummary(drinks: List<Drink>): String
}

class DefaultShareDrinksUseCase(
    private val messageFormatter: DrinkMessageFormatter,
    private val summaryFormatter: DrinkSummaryFormatter,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ShareDrinksUseCase {
    override fun message(drink: Drink): String = messageFormatter.message(drink)

    override fun todaySummary(drinks: List<Drink>): String {
        val today = LocalDate.now(clock)
        val todaysDrinks = drinks
            .filter { it.consumedAt.atZone(clock.zone).toLocalDate() == today }
            .sortedBy { it.consumedAt }
        return summaryFormatter.summary(todaysDrinks, today)
    }
}
