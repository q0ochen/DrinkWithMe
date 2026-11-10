package com.drinkwithme.core.bar

/**
 * Art eines Getränks. Die Namen der Konstanten sind Teil des Vertrags (z. B. Spalte `drink_events.category`)
 * und dürfen nicht umbenannt werden.
 */
enum class DrinkCategory { WATER, COFFEE, TEA, JUICE, SOFT_DRINK, ENERGY_DRINK, MILK, BEER, WINE, SPARKLING_WINE, SPIRIT, COCKTAIL, OTHER;

    companion object {
        /**
         * Liest einen gespeicherten oder vom Server empfangenen Namen. Unbekannte Werte (z. B. von einer
         * neueren App-Version) werden zu [OTHER], damit ältere Versionen nicht abstürzen.
         */
        fun fromName(name: String): DrinkCategory = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/** Deutscher Anzeigename der Kategorie (ohne Emoji – alle Grafiken sind eigene Icons). */
val DrinkCategory.displayName: String
    get() = when (this) {
        DrinkCategory.WATER -> "Wasser"
        DrinkCategory.COFFEE -> "Kaffee"
        DrinkCategory.TEA -> "Tee"
        DrinkCategory.JUICE -> "Saft"
        DrinkCategory.SOFT_DRINK -> "Erfrischungsgetränk"
        DrinkCategory.ENERGY_DRINK -> "Energydrink"
        DrinkCategory.MILK -> "Milch"
        DrinkCategory.BEER -> "Bier"
        DrinkCategory.WINE -> "Wein"
        DrinkCategory.SPARKLING_WINE -> "Sekt"
        DrinkCategory.SPIRIT -> "Spirituose"
        DrinkCategory.COCKTAIL -> "Cocktail"
        DrinkCategory.OTHER -> "Sonstiges"
    }
