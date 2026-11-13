package com.drinkwithme.core.bar

/**
 * Ein Getränk in der eigenen Bar. Die Werte sind Vorschläge (siehe [DefaultBarCatalog]) oder eigene Angaben
 * und werden beim Eintragen in die Historie kopiert.
 *
 * Gültigkeit prüft der [BarItemValidator]; das Datenmodell selbst erzwingt nichts, damit Eingabemasken
 * auch Zwischenzustände halten können.
 */
data class BarItem(
    val id: String,
    val name: String,                 // 1–40 Zeichen
    val icon: DrinkIcon,
    val category: DrinkCategory,
    val servingMilliliters: Int,      // 1–2000
    val alcoholPercent: Double,       // Vol.-%, 0–80
    val sugarGramsPer100Ml: Double,   // 0–100
    val aliases: List<String> = emptyList(),   // für die Spracherkennung, z. B. „Schorle“
    val valueSource: ValueSource = ValueSource.DEFAULT,
    val sortOrder: Int = 0,
) {
    companion object {
        /** Höchstlänge des Namens in Zeichen (wie `drink_events.name` auf dem Server). */
        const val NAME_MAX_LENGTH = 40

        /** Höchstlänge eines Alternativnamens in Zeichen. */
        const val ALIAS_MAX_LENGTH = 40

        /** Erlaubte Portionsgröße bzw. Menge eines Eintrags in Millilitern (wie `drink_events.volume_ml`). */
        val SERVING_RANGE_MILLILITERS: IntRange = 1..2000

        /** Höchster Alkoholgehalt in Vol.-%. */
        const val ALCOHOL_PERCENT_MAX = 80.0

        /** Höchster Zuckergehalt in Gramm pro 100 ml. */
        const val SUGAR_GRAMS_PER_100_ML_MAX = 100.0
    }
}

/**
 * Bereinigte Kopie: Name und Alternativnamen ohne Leerraum am Rand, leere und doppelte Alternativnamen
 * (ohne Beachtung der Groß-/Kleinschreibung, gleich dem Namen) entfernt.
 */
fun BarItem.normalized(): BarItem {
    val trimmedName = name.trim()
    val seen = mutableSetOf(trimmedName.lowercase())
    val cleanAliases = aliases
        .map { it.trim() }
        .filter { it.isNotEmpty() && seen.add(it.lowercase()) }
    return copy(name = trimmedName, aliases = cleanAliases)
}
