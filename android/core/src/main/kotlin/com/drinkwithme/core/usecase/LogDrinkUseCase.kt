package com.drinkwithme.core.usecase

import com.drinkwithme.core.domain.Drink
import com.drinkwithme.core.domain.DrinkDraft
import com.drinkwithme.core.domain.DrinkValidator
import com.drinkwithme.core.domain.DrinkWriter
import com.drinkwithme.core.domain.IdGenerator
import com.drinkwithme.core.domain.RandomIdGenerator
import java.time.Clock

/** Erfasst ein neues Getränk – immer mit dem aktuellen Zeitpunkt. */
fun interface LogDrinkUseCase {
    operator fun invoke(draft: DrinkDraft): Drink
}

class DefaultLogDrinkUseCase(
    private val validator: DrinkValidator,
    private val writer: DrinkWriter,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val idGenerator: IdGenerator = RandomIdGenerator,
) : LogDrinkUseCase {
    override fun invoke(draft: DrinkDraft): Drink {
        validator.validate(draft)
        val drink = Drink(
            id = idGenerator.newId(),
            name = draft.name.trim(),
            category = draft.category,
            volumeInMilliliters = draft.volumeInMilliliters,
            note = draft.note?.trim()?.takeIf { it.isNotEmpty() },
            consumedAt = clock.instant(),
            visibility = draft.visibility,
        )
        writer.save(drink)
        return drink
    }
}
