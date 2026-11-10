import type { DrinkCategory } from "./drinkCategory";
import type { DrinkIcon } from "./drinkIcon";

/** Herkunft der Werte (Portion, Vol.-%, Zucker) eines Bar-Getränks. */
export type ValueSource = "DEFAULT" | "USER";

/**
 * Ein Getränk in der eigenen Bar (wie `BarItem` im Kotlin-Kern). Die Werte werden beim Eintragen in die
 * Historie kopiert. Gültigkeit prüft der {@link BarItemValidator}.
 */
export interface BarItem {
  readonly id: string;
  /** 1–40 Zeichen */
  readonly name: string;
  readonly icon: DrinkIcon;
  readonly category: DrinkCategory;
  /** 1–2000 */
  readonly servingMilliliters: number;
  /** Vol.-%, 0–80 */
  readonly alcoholPercent: number;
  /** 0–100 */
  readonly sugarGramsPer100Ml: number;
  /** Für die Spracherkennung, z. B. „Schorle“. */
  readonly aliases: readonly string[];
  readonly valueSource: ValueSource;
  readonly sortOrder: number;
}

export const BAR_ITEM_LIMITS = {
  /** Höchstlänge des Namens in Zeichen (wie `drink_events.name`). */
  NAME_MAX_LENGTH: 40,
  ALIAS_MAX_LENGTH: 40,
  SERVING_MIN_MILLILITERS: 1,
  SERVING_MAX_MILLILITERS: 2000,
  ALCOHOL_PERCENT_MAX: 80,
  SUGAR_GRAMS_PER_100_ML_MAX: 100,
} as const;

/**
 * Bereinigte Kopie: Name und Alternativnamen ohne Rand-Leerraum; leere und doppelte Alternativnamen
 * (ohne Beachtung der Groß-/Kleinschreibung, gleich dem Namen) entfernt.
 */
export function normalizedBarItem(item: BarItem): BarItem {
  const trimmedName = item.name.trim();
  const seen = new Set([trimmedName.toLowerCase()]);
  const aliases: string[] = [];
  for (const alias of item.aliases) {
    const trimmed = alias.trim();
    const key = trimmed.toLowerCase();
    if (trimmed.length > 0 && !seen.has(key)) {
      seen.add(key);
      aliases.push(trimmed);
    }
  }
  return { ...item, name: trimmedName, aliases };
}
