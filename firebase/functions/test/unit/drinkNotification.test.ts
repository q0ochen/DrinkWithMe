import { describe, expect, it } from "vitest";
import {
  buildDrinkNotification,
  parseSharedDrink,
  selectRecipients,
} from "../../src/domain/drinkNotification";
import type { SharedDrink } from "../../src/domain/drinkNotification";

const drink: SharedDrink = {
  authorId: "anna",
  authorName: "Anna",
  name: "Helles",
  category: "BEER",
  volumeInMilliliters: 500,
};

describe("buildDrinkNotification", () => {
  it("baut Titel, Text, Android-Kanal/Tag und Daten laut Vertrag", () => {
    expect(buildDrinkNotification(drink, { groupId: "g1", drinkId: "d1" }, "Stammtisch")).toEqual({
      notification: {
        title: "Anna trinkt gerade",
        body: "🍺 Helles (500 ml) · Stammtisch",
      },
      android: { notification: { channelId: "drinks", tag: "d1" } },
      data: { type: "drink", groupId: "g1", drinkId: "d1" },
    });
  });

  it("formatiert Liter wie der VolumeFormatter", () => {
    const water = { ...drink, name: "Wasser", category: "WATER", volumeInMilliliters: 1_500 };

    expect(buildDrinkNotification(water, { groupId: "g", drinkId: "d" }, "Büro").notification.body).toBe(
      "💧 Wasser (1,5 l) · Büro",
    );
  });

  it("nutzt die Kategorie-Emojis des Kotlin-Kerns", () => {
    const coffee = { ...drink, name: "Espresso", category: "COFFEE", volumeInMilliliters: 30 };

    expect(buildDrinkNotification(coffee, { groupId: "g", drinkId: "d" }, "R").notification.body).toBe(
      "☕️ Espresso (30 ml) · R",
    );
  });
});

describe("parseSharedDrink", () => {
  const data = {
    drinkId: "d1",
    authorId: "anna",
    authorName: "Anna",
    name: "Helles",
    category: "BEER",
    volumeInMilliliters: 500,
    note: null,
  };

  it("liest die benötigten Felder", () => {
    expect(parseSharedDrink(data)).toEqual(drink);
  });

  it.each([
    ["undefined", undefined],
    ["ohne Autor", { ...data, authorId: undefined }],
    ["Menge als Text", { ...data, volumeInMilliliters: "500" }],
    ["Menge mit Nachkommastellen", { ...data, volumeInMilliliters: 0.5 }],
  ])("liefert null: %s", (_, value) => {
    expect(parseSharedDrink(value)).toBeNull();
  });
});

describe("selectRecipients", () => {
  it("benachrichtigt alle außer dem Autor", () => {
    expect(selectRecipients(["anna", "bob", "cem"], "anna")).toEqual(["bob", "cem"]);
  });

  it("entfernt doppelte Einträge", () => {
    expect(selectRecipients(["bob", "bob", "anna"], "anna")).toEqual(["bob"]);
  });

  it("liefert nichts, wenn der Autor allein ist", () => {
    expect(selectRecipients(["anna"], "anna")).toEqual([]);
  });
});
