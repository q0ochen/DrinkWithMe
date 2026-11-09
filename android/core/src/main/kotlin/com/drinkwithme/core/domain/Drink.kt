package com.drinkwithme.core.domain

import java.time.Instant

/** Ein getrunkenes Getränk – reines Datenmodell (Single Responsibility). */
data class Drink(
    val id: String,
    val name: String,
    val category: DrinkCategory,
    val volumeInMilliliters: Int,
    val note: String?,
    val consumedAt: Instant,
)

/** Benutzereingabe, bevor daraus ein gültiges [Drink] wird. */
data class DrinkDraft(
    val name: String,
    val category: DrinkCategory,
    val volumeInMilliliters: Int,
    val note: String? = null,
    val consumedAt: Instant? = null,
)
