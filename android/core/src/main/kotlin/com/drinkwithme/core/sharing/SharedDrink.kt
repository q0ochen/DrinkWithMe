package com.drinkwithme.core.sharing

import com.drinkwithme.core.bar.DrinkCategory
import com.drinkwithme.core.bar.DrinkIcon
import com.drinkwithme.core.log.DrinkEntry
import java.time.Instant

/**
 * Alles, was von einem Eintrag den Server erreicht (Tabelle `drink_events`): Name, Icon, Kategorie,
 * Menge in ml und Zeitpunkt sowie die ID (= `DrinkEntry.id`) zum Zurückziehen. Bewusst **ohne** Vol.-%,
 * Zucker oder Gramm.
 */
data class SharedDrink(
    val id: String,
    val name: String,
    val icon: DrinkIcon,
    val category: DrinkCategory,
    val volumeMilliliters: Int,
    val consumedAt: Instant,
)

/** Reduziert einen Eintrag auf die teilbaren Felder; `EventPublisher`-Implementierungen senden nur diese. */
fun DrinkEntry.toSharedDrink(): SharedDrink = SharedDrink(
    id = id,
    name = name,
    icon = icon,
    category = category,
    volumeMilliliters = volumeMilliliters,
    consumedAt = consumedAt,
)
