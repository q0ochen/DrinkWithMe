import type { BarItem } from "../core/bar/barItem";
import { reorderItems, type BarRepository } from "../core/bar/barRepository";
import { drinkCategoryFromName } from "../core/bar/drinkCategory";
import { drinkIconFromKey } from "../core/bar/drinkIcon";
import type { DrinkEntry, HistoryRepository } from "../core/log/drinkEntry";
import type { Database } from "./database";

/** Gespeicherte Werte defensiv lesen: unbekannte Icons/Kategorien (neuere Version) werden zu „other“/„OTHER“. */
export function parseBarItem(value: unknown): BarItem | null {
  if (!isRecord(value) || typeof value.id !== "string" || typeof value.name !== "string") return null;
  return {
    id: value.id,
    name: value.name,
    icon: drinkIconFromKey(String(value.icon ?? "")),
    category: drinkCategoryFromName(String(value.category ?? "")),
    servingMilliliters: numberOr(value.servingMilliliters, 250),
    alcoholPercent: numberOr(value.alcoholPercent, 0),
    sugarGramsPer100Ml: numberOr(value.sugarGramsPer100Ml, 0),
    aliases: Array.isArray(value.aliases) ? value.aliases.filter((alias): alias is string => typeof alias === "string") : [],
    valueSource: value.valueSource === "USER" ? "USER" : "DEFAULT",
    sortOrder: numberOr(value.sortOrder, 0),
  };
}

export function parseDrinkEntry(value: unknown): DrinkEntry | null {
  if (!isRecord(value) || typeof value.id !== "string" || typeof value.consumedAt !== "number") return null;
  return {
    id: value.id,
    barItemId: typeof value.barItemId === "string" ? value.barItemId : null,
    name: typeof value.name === "string" ? value.name : "Getränk",
    icon: drinkIconFromKey(String(value.icon ?? "")),
    category: drinkCategoryFromName(String(value.category ?? "")),
    volumeMilliliters: numberOr(value.volumeMilliliters, 0),
    alcoholPercent: numberOr(value.alcoholPercent, 0),
    sugarGramsPer100Ml: numberOr(value.sugarGramsPer100Ml, 0),
    consumedAt: value.consumedAt,
    shareWithFriends: value.shareWithFriends === true,
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function numberOr(value: unknown, fallback: number): number {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}

/** Nur die Felder des Datenmodells speichern (keine UI-Zusätze). */
function storableBarItem(item: BarItem): BarItem {
  return {
    id: item.id,
    name: item.name,
    icon: item.icon,
    category: item.category,
    servingMilliliters: item.servingMilliliters,
    alcoholPercent: item.alcoholPercent,
    sugarGramsPer100Ml: item.sugarGramsPer100Ml,
    aliases: [...item.aliases],
    valueSource: item.valueSource,
    sortOrder: item.sortOrder,
  };
}

function storableEntry(entry: DrinkEntry): DrinkEntry {
  return {
    id: entry.id,
    barItemId: entry.barItemId,
    name: entry.name,
    icon: entry.icon,
    category: entry.category,
    volumeMilliliters: entry.volumeMilliliters,
    alcoholPercent: entry.alcoholPercent,
    sugarGramsPer100Ml: entry.sugarGramsPer100Ml,
    consumedAt: entry.consumedAt,
    shareWithFriends: entry.shareWithFriends,
  };
}

/** Die eigene Bar in IndexedDB. */
export class IndexedDbBarRepository implements BarRepository {
  constructor(private readonly db: Database) {}

  async items(): Promise<BarItem[]> {
    const all = await this.db.getAll("bar");
    return all
      .map(parseBarItem)
      .filter((item): item is BarItem => item !== null)
      .sort((a, b) => a.sortOrder - b.sortOrder);
  }

  async item(id: string): Promise<BarItem | null> {
    return parseBarItem(await this.db.get("bar", id));
  }

  async save(item: BarItem): Promise<void> {
    await this.db.put("bar", storableBarItem(item));
  }

  async delete(id: string): Promise<void> {
    await this.db.delete("bar", id);
  }

  async reorder(ids: readonly string[]): Promise<void> {
    const tx = this.db.transaction("bar", "readwrite");
    const current = (await tx.store.getAll()).map(parseBarItem).filter((item): item is BarItem => item !== null);
    for (const item of reorderItems(current, ids)) await tx.store.put(storableBarItem(item));
    await tx.done;
  }
}

/** Die vollständige Trinkhistorie in IndexedDB (Index über `consumedAt`). */
export class IndexedDbHistoryRepository implements HistoryRepository {
  constructor(private readonly db: Database) {}

  async entries(from: number, to: number): Promise<DrinkEntry[]> {
    if (!(to > from)) return [];
    const range = IDBKeyRange.bound(from, to, false, true);
    const all = await this.db.getAllFromIndex("history", "consumedAt", range);
    return all.map(parseDrinkEntry).filter((entry): entry is DrinkEntry => entry !== null);
  }

  async entry(id: string): Promise<DrinkEntry | null> {
    return parseDrinkEntry(await this.db.get("history", id));
  }

  async save(entry: DrinkEntry): Promise<void> {
    await this.db.put("history", storableEntry(entry));
  }

  async delete(id: string): Promise<void> {
    await this.db.delete("history", id);
  }
}

/** Schlüssel der Supabase-Sitzung im Store `auth` (`storageKey` von supabase-js). */
export const AUTH_STORAGE_KEY = "drink-with-me-auth";

/**
 * Asynchroner Schlüssel/Wert-Speicher für die Supabase-Sitzung (Schnittstelle von `auth.storage`).
 * So liegt auch die Anmeldung in IndexedDB statt im localStorage.
 */
export class IndexedDbAuthStorage {
  constructor(private readonly db: Database) {}

  async getItem(key: string): Promise<string | null> {
    return (await this.db.get("auth", key)) ?? null;
  }

  async setItem(key: string, value: string): Promise<void> {
    await this.db.put("auth", value, key);
  }

  async removeItem(key: string): Promise<void> {
    await this.db.delete("auth", key);
  }

  async clear(): Promise<void> {
    await this.db.clear("auth");
  }

  /** Liegt eine Anmeldesitzung vor? (Ohne supabase-js zu laden – z. B. für den Start.) */
  async hasSession(): Promise<boolean> {
    return (await this.getItem(AUTH_STORAGE_KEY)) !== null;
  }
}
