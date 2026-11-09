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
    val visibility: DrinkVisibility = DrinkVisibility.FRIENDS,
)

/** Wer ein Getränk sehen darf. */
enum class DrinkVisibility {
    /** Wird automatisch in allen Trinkrunden des Nutzers geteilt. */
    FRIENDS,

    /** Bleibt nur auf dem eigenen Gerät. */
    PRIVATE,
}

/**
 * Benutzereingabe, bevor daraus ein gültiges [Drink] wird.
 *
 * Enthält bewusst keinen Zeitpunkt: Ein Getränk wird immer mit dem
 * aktuellen Zeitpunkt („jetzt“) erfasst, den der Use Case setzt.
 */
data class DrinkDraft(
    val name: String,
    val category: DrinkCategory,
    val volumeInMilliliters: Int,
    val note: String? = null,
    val visibility: DrinkVisibility = DrinkVisibility.FRIENDS,
)
