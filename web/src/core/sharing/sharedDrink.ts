import type { DrinkCategory } from "../bar/drinkCategory";
import type { DrinkIcon } from "../bar/drinkIcon";
import type { DrinkEntry } from "../log/drinkEntry";

/**
 * Alles, was von einem Eintrag den Server erreicht (Tabelle `drink_events`): Name, Icon, Kategorie, Menge in
 * ml und Zeitpunkt sowie die ID (= `DrinkEntry.id`). Bewusst **ohne** Vol.-%, Zucker oder Gramm.
 */
export interface SharedDrink {
  readonly id: string;
  readonly name: string;
  readonly icon: DrinkIcon;
  readonly category: DrinkCategory;
  readonly volumeMilliliters: number;
  readonly consumedAt: number;
}

/** Reduziert einen Eintrag auf die teilbaren Felder; Publisher senden nur diese. */
export function toSharedDrink(entry: DrinkEntry): SharedDrink {
  return {
    id: entry.id,
    name: entry.name,
    icon: entry.icon,
    category: entry.category,
    volumeMilliliters: entry.volumeMilliliters,
    consumedAt: entry.consumedAt,
  };
}

/** Wandelt Milliliter in gut lesbaren Text um („250 ml“, „1,5 l“) – wie `VolumeFormatter`. */
export function formatVolume(milliliters: number): string {
  if (milliliters < 1_000) return `${milliliters} ml`;
  const tenthsOfLiter = Math.trunc((milliliters + 50) / 100);
  const whole = Math.trunc(tenthsOfLiter / 10);
  const fraction = tenthsOfLiter % 10;
  return fraction === 0 ? `${whole} l` : `${whole},${fraction} l`;
}
