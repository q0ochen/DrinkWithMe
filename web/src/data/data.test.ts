import "fake-indexeddb/auto";
import { describe, expect, it } from "vitest";
import { CatalogIds, defaultSelection } from "../core/bar/defaultBarCatalog";
import { HISTORY_EARLIEST, HISTORY_LATEST } from "../core/log/drinkEntry";
import { catalogItem, entry, entryOf, HOUR } from "../core/testing/fixtures";
import { readRecipientId, writeRecipientId } from "../sw/recipientStore";
import { openDatabase } from "./database";
import { IndexedDbAuthStorage, IndexedDbBarRepository, IndexedDbHistoryRepository, parseBarItem, parseDrinkEntry } from "./repositories";
import { DEFAULT_SETTINGS, IndexedDbSettingsStore } from "./settingsStore";

let counter = 0;
const freshDb = () => openDatabase(`test-${Date.now()}-${++counter}`);

describe("IndexedDbBarRepository", () => {
  it("speichert, sortiert, ersetzt, löscht und ordnet um", async () => {
    const repository = new IndexedDbBarRepository(await freshDb());
    const [water, coffee, tea] = defaultSelection();
    await repository.save(tea);
    await repository.save(water);
    await repository.save(coffee);
    expect((await repository.items()).map((item) => item.name)).toEqual(["Wasser", "Kaffee", "Tee"]);

    await repository.save({ ...water, servingMilliliters: 500, valueSource: "USER" });
    expect((await repository.item(water.id))?.servingMilliliters).toBe(500);

    await repository.reorder([tea.id, "unbekannt"]);
    expect((await repository.items()).map((item) => [item.name, item.sortOrder])).toEqual([
      ["Tee", 0],
      ["Wasser", 1],
      ["Kaffee", 2],
    ]);

    await repository.delete(coffee.id);
    await repository.delete("unbekannt");
    expect((await repository.items()).map((item) => item.name)).toEqual(["Tee", "Wasser"]);
    expect(await repository.item(coffee.id)).toBeNull();
  });
});

describe("IndexedDbHistoryRepository", () => {
  it("liefert halboffene Zeiträume aufsteigend und ersetzt per ID", async () => {
    const repository = new IndexedDbHistoryRepository(await freshDb());
    const at = Date.parse("2026-11-10T08:00:00Z");
    const morning = entry(at);
    const noon = entry(at + 4 * HOUR);
    const evening = entry(at + 12 * HOUR);
    for (const item of [evening, morning, noon]) await repository.save(item);

    expect(await repository.entries(at, at + 24 * HOUR)).toEqual([morning, noon, evening]);
    expect(await repository.entries(morning.consumedAt, noon.consumedAt)).toEqual([morning]);
    expect(await repository.entries(at + 13 * HOUR, at + 12 * HOUR)).toEqual([]);

    await repository.save({ ...morning, volumeMilliliters: 330 });
    await repository.delete(noon.id);
    expect(await repository.entries(HISTORY_EARLIEST, HISTORY_LATEST)).toEqual([{ ...morning, volumeMilliliters: 330 }, evening]);
    expect(await repository.entry(noon.id)).toBeNull();
  });

  it("speichert nur die Felder des Datenmodells", async () => {
    const db = await freshDb();
    const repository = new IndexedDbHistoryRepository(db);
    const logged = { ...entryOf(catalogItem(CatalogIds.PILS), 1000, { id: "e-1" }), extra: "nicht speichern" };
    await repository.save(logged);
    expect(Object.keys((await db.get("history", "e-1")) as object).sort()).toEqual(
      [
        "alcoholPercent", "barItemId", "category", "consumedAt", "icon", "id", "name", "shareWithFriends",
        "sugarGramsPer100Ml", "volumeMilliliters",
      ].sort(),
    );
  });
});

describe("Defensives Lesen gespeicherter Daten", () => {
  it("unbekannte Icons und Kategorien werden zu other/OTHER, Unbrauchbares zu null", () => {
    expect(parseBarItem({ id: "x", name: "Kombucha", icon: "kombucha", category: "FERMENTED" })).toMatchObject({
      icon: "other",
      category: "OTHER",
      servingMilliliters: 250,
      aliases: [],
    });
    expect(parseBarItem({ name: "ohne ID" })).toBeNull();
    expect(parseDrinkEntry({ id: "e", consumedAt: 5, icon: "unicorn", category: "X", shareWithFriends: "ja" })).toMatchObject({
      icon: "other",
      category: "OTHER",
      shareWithFriends: false,
      name: "Getränk",
    });
    expect(parseDrinkEntry({ id: "e" })).toBeNull();
  });
});

describe("IndexedDbSettingsStore", () => {
  it("startet mit Standardwerten, speichert dauerhaft und meldet Änderungen", async () => {
    const db = await freshDb();
    const store = await IndexedDbSettingsStore.load(db);
    expect(store.current()).toEqual(DEFAULT_SETTINGS);
    const seen: string[] = [];
    store.subscribe((settings) => seen.push(settings.alcoholConsent));
    await store.update({ alcoholConsent: "ALLOWED", onboardingDone: true, feedLastSeenAt: 42 });
    expect(seen).toEqual(["ALLOWED"]);

    const reloaded = await IndexedDbSettingsStore.load(db);
    expect(reloaded.current()).toMatchObject({ alcoholConsent: "ALLOWED", onboardingDone: true, feedLastSeenAt: 42 });
  });

  it("verwirft ungültige Werte", async () => {
    const db = await freshDb();
    await db.put("settings", "VIELLEICHT", "alcoholConsent");
    await db.put("settings", "https://fremd.example/", "returnPath");
    await db.put("settings", { id: 5 }, "signedInUser");
    const store = await IndexedDbSettingsStore.load(db);
    expect(store.current().alcoholConsent).toBe("UNKNOWN");
    expect(store.current().returnPath).toBeNull();
    expect(store.current().signedInUser).toBeNull();
    await store.update({ returnPath: "//evil.example" });
    expect(store.current().returnPath).toBeNull();
    await store.update({ returnPath: "/einladung/abc" });
    expect(store.current().returnPath).toBe("/einladung/abc");
  });
});

describe("IndexedDbAuthStorage", () => {
  it("bietet die asynchrone Speicher-Schnittstelle für supabase-js", async () => {
    const storage = new IndexedDbAuthStorage(await freshDb());
    expect(await storage.getItem("k")).toBeNull();
    await storage.setItem("k", "v");
    expect(await storage.getItem("k")).toBe("v");
    await storage.removeItem("k");
    expect(await storage.getItem("k")).toBeNull();
  });
});

describe("Empfänger-Speicher für den Service Worker", () => {
  it("schreibt, liest und löscht die Nutzer-ID", async () => {
    expect(await readRecipientId()).toBeNull();
    await writeRecipientId("user-1");
    expect(await readRecipientId()).toBe("user-1");
    await writeRecipientId(null);
    expect(await readRecipientId()).toBeNull();
  });
});
