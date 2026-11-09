import { describe, expect, it } from "vitest";
import {
  buildDrinkMessage,
  drinkBody,
  drinkTitle,
  parseSharedDrink,
  selectRecipients,
} from "../../src/domain/drinkNotification";
import type { SharedDrink } from "../../src/domain/drinkNotification";

const drink: SharedDrink = {
  drinkId: "d1",
  authorId: "anna",
  authorName: "Anna",
  name: "Helles",
  category: "BEER",
  volumeInMilliliters: 500,
};

describe("buildDrinkMessage", () => {
  it("baut eine reine Daten-Nachricht laut Vertrag – mit recipientId, ohne notification-Block", () => {
    const message = buildDrinkMessage(drink, { groupId: "g1", docId: "anna_d1" }, "Stammtisch", "bob");

    expect(message).toEqual({
      android: { priority: "high" },
      data: {
        type: "drink",
        groupId: "g1",
        drinkId: "d1",
        docId: "anna_d1",
        recipientId: "bob",
        title: "Anna trinkt gerade",
        body: "🍺 Helles (500 ml) · Stammtisch",
      },
    });
    expect(Object.keys(message)).toEqual(["android", "data"]);
  });

  it("enthält in data nur Zeichenketten (Vorgabe von FCM)", () => {
    const message = buildDrinkMessage(drink, { groupId: "g1", docId: "anna_d1" }, "Stammtisch", "bob");

    for (const value of Object.values(message.data)) expect(typeof value).toBe("string");
  });

  it("formatiert Liter wie der VolumeFormatter", () => {
    const water = { ...drink, name: "Wasser", category: "WATER", volumeInMilliliters: 1_500 };

    expect(drinkBody(water, "Büro")).toBe("💧 Wasser (1,5 l) · Büro");
  });

  it("nutzt die Kategorie-Emojis des Kotlin-Kerns", () => {
    const coffee = { ...drink, name: "Espresso", category: "COFFEE", volumeInMilliliters: 30 };

    expect(drinkBody(coffee, "R")).toBe("☕️ Espresso (30 ml) · R");
    expect(drinkTitle(coffee)).toBe("Anna trinkt gerade");
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
    ["ohne drinkId", { ...data, drinkId: undefined }],
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
