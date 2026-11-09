/**
 * Getränkekategorien mit Emoji – identisch zu `DrinkCategory` im Kotlin-Kern
 * (`android/core/.../domain/DrinkCategory.kt`). Gespeichert wird der Name, z. B. `"BEER"`.
 */
export const DRINK_CATEGORY_EMOJIS = {
  WATER: "\u{1F4A7}", // 💧
  COFFEE: "☕️", // ☕️
  TEA: "\u{1F375}", // 🍵
  JUICE: "\u{1F9C3}", // 🧃
  SOFT_DRINK: "\u{1F964}", // 🥤
  BEER: "\u{1F37A}", // 🍺
  WINE: "\u{1F377}", // 🍷
  COCKTAIL: "\u{1F379}", // 🍹
  OTHER: "\u{1F95B}", // 🥛
} as const;

export type DrinkCategory = keyof typeof DRINK_CATEGORY_EMOJIS;

export const DRINK_CATEGORIES = Object.keys(DRINK_CATEGORY_EMOJIS) as DrinkCategory[];

export function isDrinkCategory(value: unknown): value is DrinkCategory {
  return typeof value === "string" && Object.hasOwn(DRINK_CATEGORY_EMOJIS, value);
}

/** Emoji zur Kategorie; Unbekanntes wird wie `OTHER` dargestellt. */
export function emojiFor(category: string): string {
  return isDrinkCategory(category) ? DRINK_CATEGORY_EMOJIS[category] : DRINK_CATEGORY_EMOJIS.OTHER;
}
