import type { DrinkCategory } from "./drinkCategory";

/**
 * Schlüssel der selbst gezeichneten Icons; identisch in Android (`R.drawable.drink_<key>`) und Web
 * (`icons/<key>.svg`). Reihenfolge und Werte wie `enum class DrinkIcon` im Kotlin-Kern.
 */
export const DRINK_ICON_KEYS = [
  "water",
  "sparkling_water",
  "coffee",
  "tea",
  "juice",
  "apple_spritzer",
  "cola",
  "lemonade",
  "iced_tea",
  "energy_drink",
  "milk",
  "malt_beer",
  "beer",
  "wheat_beer",
  "radler",
  "red_wine",
  "white_wine",
  "sparkling_wine",
  "shot",
  "longdrink",
  "cocktail",
  "other",
] as const;

export type DrinkIcon = (typeof DRINK_ICON_KEYS)[number];

/** Format, das auch der Server für `drink_events.icon` prüft. */
export const DRINK_ICON_KEY_PATTERN = /^[a-z_]{1,30}$/;

/** Icon zu einem Schlüssel oder `null`, wenn der Schlüssel unbekannt ist. */
export function drinkIconFromKeyOrNull(key: string): DrinkIcon | null {
  return (DRINK_ICON_KEYS as readonly string[]).includes(key) ? (key as DrinkIcon) : null;
}

/** Icon zu einem Schlüssel; unbekannte Schlüssel (z. B. von neueren Versionen) werden zu „other“. */
export function drinkIconFromKey(key: string): DrinkIcon {
  return drinkIconFromKeyOrNull(key) ?? "other";
}

const DEFAULT_ICON: Record<DrinkCategory, DrinkIcon> = {
  WATER: "water",
  COFFEE: "coffee",
  TEA: "tea",
  JUICE: "juice",
  SOFT_DRINK: "lemonade",
  ENERGY_DRINK: "energy_drink",
  MILK: "milk",
  BEER: "beer",
  WINE: "red_wine",
  SPARKLING_WINE: "sparkling_wine",
  SPIRIT: "shot",
  COCKTAIL: "cocktail",
  OTHER: "other",
};

/** Passendes Standard-Icon für ein eigenes Getränk der Kategorie. */
export function defaultIconFor(category: DrinkCategory): DrinkIcon {
  return DEFAULT_ICON[category];
}

const ICON_LABELS: Record<DrinkIcon, string> = {
  water: "Wasser",
  sparkling_water: "Mineralwasser",
  coffee: "Kaffee",
  tea: "Tee",
  juice: "Saft",
  apple_spritzer: "Apfelschorle",
  cola: "Cola",
  lemonade: "Limonade",
  iced_tea: "Eistee",
  energy_drink: "Energydrink",
  milk: "Milch",
  malt_beer: "Malzbier",
  beer: "Bier",
  wheat_beer: "Weizenbier",
  radler: "Radler",
  red_wine: "Rotwein",
  white_wine: "Weißwein",
  sparkling_wine: "Sekt",
  shot: "Schnaps",
  longdrink: "Longdrink",
  cocktail: "Cocktail",
  other: "Sonstiges",
};

/** Deutscher Name eines Icons für die Auswahl im Bearbeiten-Dialog (Screenreader und Beschriftung). */
export function drinkIconLabel(icon: DrinkIcon): string {
  return ICON_LABELS[icon];
}
