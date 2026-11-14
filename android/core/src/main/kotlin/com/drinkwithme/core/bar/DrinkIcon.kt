package com.drinkwithme.core.bar

/** Schlüssel eines selbst gezeichneten Icons; identisch in Android (R.drawable.drink_<key>) und Web (icons/<key>.svg). */
enum class DrinkIcon(val key: String) {
    WATER("water"), SPARKLING_WATER("sparkling_water"), COFFEE("coffee"), TEA("tea"),
    JUICE("juice"), APPLE_SPRITZER("apple_spritzer"), COLA("cola"), LEMONADE("lemonade"),
    ICED_TEA("iced_tea"), ENERGY_DRINK("energy_drink"), MILK("milk"), MALT_BEER("malt_beer"),
    BEER("beer"), WHEAT_BEER("wheat_beer"), RADLER("radler"), RED_WINE("red_wine"), WHITE_WINE("white_wine"),
    SPARKLING_WINE("sparkling_wine"), SHOT("shot"), LONGDRINK("longdrink"), COCKTAIL("cocktail"), OTHER("other"),
    ;

    companion object {
        /** Format, das auch der Server für `drink_events.icon` prüft. */
        val KEY_PATTERN: Regex = Regex("^[a-z_]{1,30}$")

        /** Icon zu einem Schlüssel oder `null`, wenn der Schlüssel unbekannt ist. */
        fun fromKeyOrNull(key: String): DrinkIcon? = entries.firstOrNull { it.key == key }

        /** Icon zu einem Schlüssel; unbekannte Schlüssel (z. B. von neueren Versionen) werden zu [OTHER]. */
        fun fromKey(key: String): DrinkIcon = fromKeyOrNull(key) ?: OTHER

        /** Passendes Standard-Icon für ein eigenes Getränk der Kategorie. */
        fun defaultFor(category: DrinkCategory): DrinkIcon = when (category) {
            DrinkCategory.WATER -> WATER
            DrinkCategory.COFFEE -> COFFEE
            DrinkCategory.TEA -> TEA
            DrinkCategory.JUICE -> JUICE
            DrinkCategory.SOFT_DRINK -> LEMONADE
            DrinkCategory.ENERGY_DRINK -> ENERGY_DRINK
            DrinkCategory.MILK -> MILK
            DrinkCategory.BEER -> BEER
            DrinkCategory.WINE -> RED_WINE
            DrinkCategory.SPARKLING_WINE -> SPARKLING_WINE
            DrinkCategory.SPIRIT -> SHOT
            DrinkCategory.COCKTAIL -> COCKTAIL
            DrinkCategory.OTHER -> OTHER
        }
    }
}
