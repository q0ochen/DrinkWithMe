package com.drinkwithme.core.log

import com.drinkwithme.core.bar.DrinkCategory
import com.drinkwithme.core.bar.DrinkIcon
import java.time.Instant

/** Ein getrunkenes Getränk. Werte werden beim Eintragen aus der Bar kopiert (Snapshot), damit spätere Änderungen
 *  an der Bar die Historie nicht rückwirkend verfälschen. */
data class DrinkEntry(
    val id: String,                    // UUID
    val barItemId: String?,
    val name: String,
    val icon: DrinkIcon,
    val category: DrinkCategory,
    val volumeMilliliters: Int,
    val alcoholPercent: Double,
    val sugarGramsPer100Ml: Double,
    val consumedAt: Instant,           // immer „jetzt“
    val shareWithFriends: Boolean,
)
