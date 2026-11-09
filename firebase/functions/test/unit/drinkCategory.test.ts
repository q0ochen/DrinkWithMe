import { describe, expect, it } from "vitest";
import { DRINK_CATEGORIES, emojiFor, isDrinkCategory } from "../../src/domain/drinkCategory";

describe("drinkCategory", () => {
  it("kennt genau die Kategorien aus dem Vertrag in derselben Reihenfolge", () => {
    expect(DRINK_CATEGORIES).toEqual([
      "WATER", "COFFEE", "TEA", "JUICE", "SOFT_DRINK", "BEER", "WINE", "COCKTAIL", "OTHER",
    ]);
  });

  it("liefert die Emojis wie DrinkCategory im Kotlin-Kern", () => {
    expect(DRINK_CATEGORIES.map(emojiFor)).toEqual(["💧", "☕️", "🍵", "🧃", "🥤", "🍺", "🍷", "🍹", "🥛"]);
  });

  it("stellt Unbekanntes wie OTHER dar", () => {
    expect(emojiFor("MILKSHAKE")).toBe("🥛");
    expect(emojiFor("toString")).toBe("🥛");
    expect(isDrinkCategory("toString")).toBe(false);
    expect(isDrinkCategory("BEER")).toBe(true);
  });
});
