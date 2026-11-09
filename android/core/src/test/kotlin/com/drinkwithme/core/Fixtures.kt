package com.drinkwithme.core

import com.drinkwithme.core.domain.Drink
import com.drinkwithme.core.domain.DrinkCategory
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

object Fixtures {
    /** 09.11.2026, 14:30 UTC */
    val afternoon: Instant = Instant.parse("2026-11-09T14:30:00Z")
    val clock: Clock = Clock.fixed(afternoon, ZoneOffset.UTC)

    fun drink(
        name: String = "Espresso",
        category: DrinkCategory = DrinkCategory.COFFEE,
        volume: Int = 30,
        note: String? = null,
        at: Instant = afternoon,
    ) = Drink(UUID.randomUUID().toString(), name, category, volume, note, at)
}
