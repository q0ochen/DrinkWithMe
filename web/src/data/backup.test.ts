// Sicherung von Bar und Verlauf als JSON-Datei (Review 3, Befund 11): z. B. vom Safari-Tab in die App auf dem
// Home-Bildschirm (eigener Speicher) oder auf ein neues Gerät. Geprüft: Format, Prüfung jeder Zeile, Zusammenführen.
import { describe, expect, it } from "vitest";
import type { BarItem } from "../core/bar/barItem";
import { InMemoryBarRepository } from "../core/bar/barRepository";
import { CatalogIds, catalogItem as lookup } from "../core/bar/defaultBarCatalog";
import { HISTORY_EARLIEST, HISTORY_LATEST, InMemoryHistoryRepository } from "../core/log/drinkEntry";
import { catalogItem, entryOf, MutableClock } from "../core/testing/fixtures";
import { BACKUP_FORMAT, BackupService, MAX_BACKUP_CHARACTERS, parseBackup, serializeBackup } from "./backup";

const NOW = Date.parse("2026-11-10T18:00:00Z");
const HOUR = 60 * 60 * 1000;
const water = catalogItem(CatalogIds.WASSER);
const pils = catalogItem(CatalogIds.PILS);

function item(id: string, name: string, sortOrder: number, overrides: Partial<BarItem> = {}): BarItem {
  return { ...water, id, name, sortOrder, aliases: [], ...overrides };
}

describe("Sicherungsdatei", () => {
  it("enthält Format, Version, Zeitpunkt, Bar und Verlauf – und lässt sich wieder einlesen", () => {
    const entries = [entryOf(water, NOW - HOUR, { id: "e-1" }), entryOf(pils, NOW, { id: "e-2", share: true })];
    const text = serializeBackup([item("a", "Wasser", 0)], entries, NOW);
    const json = JSON.parse(text) as Record<string, unknown>;
    expect(json).toMatchObject({ format: BACKUP_FORMAT, version: 1, exportedAt: "2026-11-10T18:00:00.000Z" });
    const parsed = parseBackup(text, NOW);
    expect(parsed).toEqual({ ok: true, bar: [item("a", "Wasser", 0)], history: entries, invalid: 0 });
  });

  it("weist fremde, kaputte, zu große und zu neue Dateien mit deutscher Meldung ab", () => {
    expect(parseBackup("kein json", NOW)).toEqual({ ok: false, error: "Die Datei ist keine Sicherung von Drink With Me." });
    expect(parseBackup(JSON.stringify({ format: "andere-app", version: 1, bar: [], history: [] }), NOW)).toMatchObject({ ok: false });
    expect(parseBackup(JSON.stringify([1, 2, 3]), NOW)).toMatchObject({ ok: false });
    expect(parseBackup(JSON.stringify({ format: BACKUP_FORMAT, version: 2, bar: [], history: [] }), NOW)).toEqual({
      ok: false,
      error: "Diese Sicherung stammt aus einer neueren Version von Drink With Me. Bitte aktualisiere die App und versuche es erneut.",
    });
    expect(parseBackup(" ".repeat(MAX_BACKUP_CHARACTERS + 1), NOW)).toEqual({
      ok: false,
      error: "Die Datei ist zu groß für eine Sicherung von Drink With Me.",
    });
    expect(parseBackup(JSON.stringify({ format: BACKUP_FORMAT, version: 1, bar: "x", history: [] }), NOW)).toMatchObject({ ok: false });
  });

  it("prüft jede Zeile wie beim Eintragen: Ungültiges wird gezählt und übersprungen, Unbekanntes wird „Sonstiges“", () => {
    const good = entryOf(water, NOW, { id: "e-ok" });
    const text = JSON.stringify({
      format: BACKUP_FORMAT,
      version: 1,
      bar: [
        item("a", "Wasser", 0),
        item("b", "", 1), // leerer Name
        item("c", "Zu viel", 2, { servingMilliliters: 5000 }),
        { ...item("d", "Kombucha", 3), icon: "kombucha", category: "FERMENTED" }, // unbekannt → other/OTHER
        { name: "ohne ID" },
        item("a", "Doppelt", 4), // doppelte ID: die erste gilt
      ],
      history: [
        good,
        { ...good, id: "e-zukunft", consumedAt: NOW + 3 * 24 * HOUR },
        { ...good, id: "e-menge", volumeMilliliters: 0 },
        { ...good, id: "e-alkohol", alcoholPercent: 96 },
        { ...good, id: "e-name", name: "x".repeat(41) },
        { ...good, id: "", name: "ohne ID" },
        { ...good, id: "e-zeit", consumedAt: "gestern" },
        { ...good, id: "e-ok" }, // doppelt
        "Unsinn",
      ],
    });
    const parsed = parseBackup(text, NOW);
    if (!parsed.ok) throw new Error(parsed.error);
    expect(parsed.bar.map((barItem) => [barItem.id, barItem.name, barItem.icon, barItem.category])).toEqual([
      ["a", "Wasser", "water", "WATER"],
      ["d", "Kombucha", "other", "OTHER"],
    ]);
    expect(parsed.history.map((entry) => entry.id)).toEqual(["e-ok"]);
    expect(parsed.invalid).toBe(4 + 8);
  });
});

describe("BackupService: Zusammenführen, nichts geht verloren", () => {
  function setUp(bar: BarItem[] = [], entries = [entryOf(water, NOW - HOUR, { id: "e-local", share: true })]) {
    const barRepository = new InMemoryBarRepository(bar);
    const history = new InMemoryHistoryRepository(entries);
    let notified = 0;
    const service = new BackupService(barRepository, history, new MutableClock(NOW), () => notified++);
    return { service, barRepository, history, notified: () => notified };
  }

  it("Verlauf: neue Einträge kommen dazu, vorhandene (gleiche ID) bleiben unverändert", async () => {
    const { service, history, notified } = setUp();
    const backup = serializeBackup(
      [],
      [entryOf(pils, NOW - 2 * HOUR, { id: "e-local", share: false }), entryOf(pils, NOW - 3 * HOUR, { id: "e-neu", share: true })],
      NOW,
    );
    const summary = await service.importJson(backup);
    expect(summary).toEqual({ addedEntries: 1, existingEntries: 1, addedBarItems: 0, updatedBarItems: 0, invalid: 0 });
    const all = await history.entries(HISTORY_EARLIEST, HISTORY_LATEST);
    expect(all.map((entry) => [entry.id, entry.name, entry.shareWithFriends])).toEqual([
      ["e-neu", "Pils", true],
      ["e-local", "Wasser", true],
    ]);
    expect(notified()).toBe(1);
    // Erneutes Einlesen derselben Datei ändert nichts.
    expect(await service.importJson(backup)).toMatchObject({ addedEntries: 0, existingEntries: 2 });
  });

  it("Bar: Reihenfolge und Werte aus der Datei; Getränke, die nur hier sind, bleiben (hinten)", async () => {
    const { service, barRepository } = setUp([item("wasser", "Wasser", 0), item("nur-hier", "Eistee", 1), item("tee", "Tee", 2)]);
    const backup = serializeBackup(
      [item("tee", "Tee", 0, { servingMilliliters: 400, valueSource: "USER" }), item("neu", "Kombucha", 1), item("wasser", "Wasser", 2)],
      [],
      NOW,
    );
    expect(await service.importJson(backup)).toMatchObject({ addedBarItems: 1, updatedBarItems: 2 });
    const bar = await barRepository.items();
    expect(bar.map((barItem) => [barItem.id, barItem.sortOrder])).toEqual([
      ["tee", 0],
      ["neu", 1],
      ["wasser", 2],
      ["nur-hier", 3],
    ]);
    expect(bar[0]).toMatchObject({ servingMilliliters: 400, valueSource: "USER" });
  });

  it("eine ungültige Datei ändert nichts", async () => {
    const { service, barRepository, history } = setUp([item("wasser", "Wasser", 0)]);
    await expect(service.importJson("{}")).rejects.toThrow("Die Datei ist keine Sicherung von Drink With Me.");
    expect(await barRepository.items()).toHaveLength(1);
    expect(await history.entries(HISTORY_EARLIEST, HISTORY_LATEST)).toHaveLength(1);
  });

  it("Export enthält die ganze Bar und den ganzen Verlauf", async () => {
    const { service } = setUp([item("wasser", "Wasser", 0)]);
    const parsed = parseBackup(await service.exportJson(), NOW);
    expect(parsed).toMatchObject({ ok: true, invalid: 0 });
    if (parsed.ok) {
      expect(parsed.bar.map((barItem) => barItem.id)).toEqual(["wasser"]);
      expect(parsed.history.map((entry) => entry.id)).toEqual(["e-local"]);
    }
  });

  it("Katalogwerte bleiben exakt erhalten (Golden: gleiche Zahlen wie im Kotlin-Kern)", async () => {
    const original = lookup(CatalogIds.PILS);
    if (!original) throw new Error("Pils fehlt im Katalog");
    const parsed = parseBackup(serializeBackup([original], [], NOW), NOW);
    expect(parsed).toMatchObject({ ok: true, bar: [original] });
  });
});
