/**
 * Art eines Getränks. Die Namen sind Teil des Vertrags (z. B. Spalte `drink_events.category`)
 * und dürfen nicht umbenannt werden – wie `enum class DrinkCategory` im Kotlin-Kern.
 */
export const DRINK_CATEGORIES = [
  "WATER",
  "COFFEE",
  "TEA",
  "JUICE",
  "SOFT_DRINK",
  "ENERGY_DRINK",
  "MILK",
  "BEER",
  "WINE",
  "SPARKLING_WINE",
  "SPIRIT",
  "COCKTAIL",
  "OTHER",
] as const;

export type DrinkCategory = (typeof DRINK_CATEGORIES)[number];

/** Liest einen gespeicherten oder empfangenen Namen; Unbekanntes wird zu „OTHER“. */
export function drinkCategoryFromName(name: string): DrinkCategory {
  return (DRINK_CATEGORIES as readonly string[]).includes(name) ? (name as DrinkCategory) : "OTHER";
}

const DISPLAY_NAMES: Record<DrinkCategory, string> = {
  WATER: "Wasser",
  COFFEE: "Kaffee",
  TEA: "Tee",
  JUICE: "Saft",
  SOFT_DRINK: "Erfrischungsgetränk",
  ENERGY_DRINK: "Energydrink",
  MILK: "Milch",
  BEER: "Bier",
  WINE: "Wein",
  SPARKLING_WINE: "Sekt",
  SPIRIT: "Spirituose",
  COCKTAIL: "Cocktail",
  OTHER: "Sonstiges",
};

/** Deutscher Anzeigename der Kategorie (ohne Emoji – alle Grafiken sind eigene Icons). */
export function categoryDisplayName(category: DrinkCategory): string {
  return DISPLAY_NAMES[category];
}
