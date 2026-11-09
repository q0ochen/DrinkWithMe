package com.drinkwithme.core.domain

/** Art eines Getränks. Neue Kategorien erfordern keine Änderung der Use Cases (Open/Closed). */
enum class DrinkCategory(val displayName: String, val emoji: String) {
    WATER("Wasser", "💧"),
    COFFEE("Kaffee", "☕️"),
    TEA("Tee", "🍵"),
    JUICE("Saft", "🧃"),
    SOFT_DRINK("Softdrink", "🥤"),
    BEER("Bier", "🍺"),
    WINE("Wein", "🍷"),
    COCKTAIL("Cocktail", "🍹"),
    OTHER("Sonstiges", "🥛"),
}
