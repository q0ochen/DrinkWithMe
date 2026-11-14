package com.drinkwithme.core.bar

/**
 * Ein Vorschlag aus dem Katalog.
 *
 * @property note Zusatz für die Anzeige, z. B. „Spirituose + Cola“.
 * @property isEstimate `true`, wenn die Werte nur grob geschätzt sind (Milch, Cocktail) und so markiert
 *   angezeigt werden müssen.
 */
data class CatalogEntry(
    val item: BarItem,
    val note: String? = null,
    val isEstimate: Boolean = false,
)

/**
 * Vorschlagsliste für die eigene Bar mit eigenen, frei verwendbaren Werten (CC0) –
 * Zucker in g/100 ml, Alkohol in Vol.-%, Standardportion in ml. Keine Fremddaten (z. B. Open Food Facts).
 *
 * Die IDs sind stabil („katalog-…“), damit ein Getränk nicht doppelt in der Bar landet.
 */
object DefaultBarCatalog {
    const val ID_PREFIX = "katalog-"

    const val WASSER = "${ID_PREFIX}wasser"
    const val MINERALWASSER = "${ID_PREFIX}mineralwasser"
    const val KAFFEE = "${ID_PREFIX}kaffee"
    const val TEE = "${ID_PREFIX}tee"
    const val COLA = "${ID_PREFIX}cola"
    const val COLA_ZERO = "${ID_PREFIX}cola-zero"
    const val LIMONADE = "${ID_PREFIX}limonade"
    const val EISTEE = "${ID_PREFIX}eistee"
    const val ENERGYDRINK = "${ID_PREFIX}energydrink"
    const val ORANGENSAFT = "${ID_PREFIX}orangensaft"
    const val APFELSAFT = "${ID_PREFIX}apfelsaft"
    const val APFELSCHORLE = "${ID_PREFIX}apfelschorle"
    const val MALZBIER = "${ID_PREFIX}malzbier"
    const val MILCH = "${ID_PREFIX}milch"
    const val PILS = "${ID_PREFIX}pils"
    const val WEIZENBIER = "${ID_PREFIX}weizenbier"
    const val ALKOHOLFREIES_BIER = "${ID_PREFIX}alkoholfreies-bier"
    const val RADLER = "${ID_PREFIX}radler"
    const val ROTWEIN = "${ID_PREFIX}rotwein-trocken"
    const val WEISSWEIN = "${ID_PREFIX}weisswein-trocken"
    const val SEKT = "${ID_PREFIX}sekt-trocken"
    const val SCHNAPS = "${ID_PREFIX}schnaps"
    const val LONGDRINK = "${ID_PREFIX}longdrink"
    const val COCKTAIL = "${ID_PREFIX}cocktail"

    /** Alle Vorschläge in Anzeigereihenfolge ([BarItem.sortOrder] = Position). */
    val entries: List<CatalogEntry> = listOf(
        entry(WASSER, "Wasser", DrinkIcon.WATER, DrinkCategory.WATER, sugar = 0.0, alcohol = 0.0, serving = 250,
            aliases = listOf("Leitungswasser", "Stilles Wasser")),
        entry(MINERALWASSER, "Mineralwasser", DrinkIcon.SPARKLING_WATER, DrinkCategory.WATER, sugar = 0.0, alcohol = 0.0,
            serving = 250, aliases = listOf("Sprudel", "Sprudelwasser")),
        entry(KAFFEE, "Kaffee", DrinkIcon.COFFEE, DrinkCategory.COFFEE, sugar = 0.0, alcohol = 0.0, serving = 200,
            aliases = listOf("Filterkaffee")),
        entry(TEE, "Tee", DrinkIcon.TEA, DrinkCategory.TEA, sugar = 0.0, alcohol = 0.0, serving = 250,
            aliases = listOf("Kräutertee", "Früchtetee")),
        entry(COLA, "Cola", DrinkIcon.COLA, DrinkCategory.SOFT_DRINK, sugar = 10.6, alcohol = 0.0, serving = 330),
        entry(COLA_ZERO, "Cola zero", DrinkIcon.COLA, DrinkCategory.SOFT_DRINK, sugar = 0.0, alcohol = 0.0, serving = 330,
            aliases = listOf("Cola light", "Zuckerfreie Cola")),
        entry(LIMONADE, "Limonade", DrinkIcon.LEMONADE, DrinkCategory.SOFT_DRINK, sugar = 8.0, alcohol = 0.0, serving = 330,
            aliases = listOf("Limo", "Brause")),
        entry(EISTEE, "Eistee", DrinkIcon.ICED_TEA, DrinkCategory.SOFT_DRINK, sugar = 7.5, alcohol = 0.0, serving = 500,
            aliases = listOf("Ice Tea")),
        entry(ENERGYDRINK, "Energydrink", DrinkIcon.ENERGY_DRINK, DrinkCategory.ENERGY_DRINK, sugar = 11.0, alcohol = 0.0,
            serving = 250, aliases = listOf("Energy Drink", "Energy")),
        entry(ORANGENSAFT, "Orangensaft", DrinkIcon.JUICE, DrinkCategory.JUICE, sugar = 9.0, alcohol = 0.0, serving = 200,
            aliases = listOf("O-Saft")),
        entry(APFELSAFT, "Apfelsaft", DrinkIcon.JUICE, DrinkCategory.JUICE, sugar = 10.0, alcohol = 0.0, serving = 200),
        entry(APFELSCHORLE, "Apfelschorle", DrinkIcon.APPLE_SPRITZER, DrinkCategory.JUICE, sugar = 5.5, alcohol = 0.0,
            serving = 500, aliases = listOf("Apfelsaftschorle", "Schorle")),
        entry(MALZBIER, "Malzbier", DrinkIcon.MALT_BEER, DrinkCategory.SOFT_DRINK, sugar = 9.0, alcohol = 0.0, serving = 330,
            aliases = listOf("Malztrunk")),
        entry(MILCH, "Milch", DrinkIcon.MILK, DrinkCategory.MILK, sugar = 4.8, alcohol = 0.0, serving = 200,
            aliases = listOf("Vollmilch"), note = "Zucker = Milchzucker (Schätzwert)", isEstimate = true),
        entry(PILS, "Pils", DrinkIcon.BEER, DrinkCategory.BEER, sugar = 0.3, alcohol = 4.9, serving = 500,
            aliases = listOf("Bier", "Pilsener", "Pilsner")),
        entry(WEIZENBIER, "Weizenbier", DrinkIcon.WHEAT_BEER, DrinkCategory.BEER, sugar = 0.3, alcohol = 5.4, serving = 500,
            aliases = listOf("Weizen", "Weißbier", "Hefeweizen")),
        entry(ALKOHOLFREIES_BIER, "Alkoholfreies Bier", DrinkIcon.BEER, DrinkCategory.BEER, sugar = 2.5, alcohol = 0.4,
            serving = 500, aliases = listOf("Alkoholfrei")),
        entry(RADLER, "Radler", DrinkIcon.RADLER, DrinkCategory.BEER, sugar = 5.0, alcohol = 2.5, serving = 500,
            aliases = listOf("Alster")),
        entry(ROTWEIN, "Rotwein trocken", DrinkIcon.RED_WINE, DrinkCategory.WINE, sugar = 0.3, alcohol = 12.0, serving = 125,
            aliases = listOf("Rotwein")),
        entry(WEISSWEIN, "Weißwein trocken", DrinkIcon.WHITE_WINE, DrinkCategory.WINE, sugar = 0.3, alcohol = 11.5,
            serving = 125, aliases = listOf("Weißwein")),
        entry(SEKT, "Sekt trocken", DrinkIcon.SPARKLING_WINE, DrinkCategory.SPARKLING_WINE, sugar = 2.5, alcohol = 11.0,
            serving = 100, aliases = listOf("Sekt", "Prosecco")),
        entry(SCHNAPS, "Schnaps", DrinkIcon.SHOT, DrinkCategory.SPIRIT, sugar = 0.0, alcohol = 40.0, serving = 20,
            aliases = listOf("Kurzer", "Klarer", "Shot")),
        entry(LONGDRINK, "Longdrink", DrinkIcon.LONGDRINK, DrinkCategory.SPIRIT, sugar = 8.7, alcohol = 5.3, serving = 240,
            aliases = listOf("Rum Cola", "Whisky Cola"), note = "Spirituose + Cola"),
        entry(COCKTAIL, "Cocktail", DrinkIcon.COCKTAIL, DrinkCategory.COCKTAIL, sugar = 8.0, alcohol = 10.0, serving = 200,
            note = "Schätzwert", isEstimate = true),
    ).mapIndexed { index, entry -> entry.copy(item = entry.item.copy(sortOrder = index)) }

    /** Alle Vorschläge als Bar-Getränke. */
    val items: List<BarItem> = entries.map { it.item }

    /** Vorauswahl beim ersten Start: Wasser, Kaffee, Tee, Apfelschorle, Cola, Pils, Rotwein. */
    val defaultSelectionIds: List<String> = listOf(WASSER, KAFFEE, TEE, APFELSCHORLE, COLA, PILS, ROTWEIN)

    fun entry(id: String): CatalogEntry? = entries.firstOrNull { it.item.id == id }

    fun item(id: String): BarItem? = entry(id)?.item

    /** Die Vorauswahl in dieser Reihenfolge, mit [BarItem.sortOrder] 0, 1, 2 … */
    fun defaultSelection(): List<BarItem> =
        defaultSelectionIds.mapNotNull { item(it) }.mapIndexed { index, item -> item.copy(sortOrder = index) }

    private fun entry(
        id: String,
        name: String,
        icon: DrinkIcon,
        category: DrinkCategory,
        sugar: Double,
        alcohol: Double,
        serving: Int,
        aliases: List<String> = emptyList(),
        note: String? = null,
        isEstimate: Boolean = false,
    ) = CatalogEntry(
        item = BarItem(
            id = id,
            name = name,
            icon = icon,
            category = category,
            servingMilliliters = serving,
            alcoholPercent = alcohol,
            sugarGramsPer100Ml = sugar,
            aliases = aliases,
            valueSource = ValueSource.DEFAULT,
        ),
        note = note,
        isEstimate = isEstimate,
    )
}
