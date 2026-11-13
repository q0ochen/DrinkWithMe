import type { BarItem } from "../bar/barItem";
import type { DrinkCategory } from "../bar/drinkCategory";
import type { DrinkIcon } from "../bar/drinkIcon";

/**
 * Ein getrunkenes Getränk (wie `DrinkEntry` im Kotlin-Kern). Werte werden beim Eintragen aus der Bar
 * kopiert (Snapshot), damit spätere Änderungen an der Bar die Historie nicht rückwirkend verfälschen.
 */
export interface DrinkEntry {
  /** UUID */
  readonly id: string;
  readonly barItemId: string | null;
  readonly name: string;
  readonly icon: DrinkIcon;
  readonly category: DrinkCategory;
  readonly volumeMilliliters: number;
  readonly alcoholPercent: number;
  readonly sugarGramsPer100Ml: number;
  /** Millisekunden seit 1970 (UTC) – immer „jetzt“. */
  readonly consumedAt: number;
  readonly shareWithFriends: boolean;
}

/**
 * Entscheidet beim Eintragen, ob ein Eintrag mit Freunden geteilt wird (umgesetzt von der `SharingPolicy`).
 * `shareOverride`: `false` = privat; `null` = Regel anwenden; `true` hebt nur eine Privat-Markierung auf.
 */
export interface ShareDecider {
  shareWithFriends(item: BarItem, shareOverride: boolean | null): boolean;
}

/** Lesender Zugriff auf die Trinkhistorie. */
export interface HistoryReader {
  /** Alle Einträge mit `from ≤ consumedAt < to`, aufsteigend nach `consumedAt`. */
  entries(from: number, to: number): Promise<DrinkEntry[]>;
  entry(id: string): Promise<DrinkEntry | null>;
}

/** Schreibender Zugriff auf die Trinkhistorie. Wartet nie auf das Netz. */
export interface HistoryWriter {
  /** Legt an oder ersetzt den Eintrag mit derselben ID. */
  save(entry: DrinkEntry): Promise<void>;
  /** Entfernt den Eintrag; unbekannte IDs werden ignoriert. */
  delete(id: string): Promise<void>;
}

/** Die vollständige Trinkhistorie – liegt nur auf dem Gerät. */
export interface HistoryRepository extends HistoryReader, HistoryWriter {}

/** Frühester und spätester Zeitpunkt für „alle Einträge“ (wie `HistoryReader.EARLIEST/LATEST`). */
export const HISTORY_EARLIEST = 0;
export const HISTORY_LATEST = Date.UTC(3000, 0, 1);

/** Flüchtige Historie für Tests und Vorschauen. */
export class InMemoryHistoryRepository implements HistoryRepository {
  private readonly byId = new Map<string, DrinkEntry>();

  constructor(initial: readonly DrinkEntry[] = []) {
    for (const entry of initial) this.byId.set(entry.id, entry);
  }

  async entries(from: number, to: number): Promise<DrinkEntry[]> {
    return [...this.byId.values()]
      .filter((entry) => entry.consumedAt >= from && entry.consumedAt < to)
      .sort((a, b) => a.consumedAt - b.consumedAt);
  }

  async entry(id: string): Promise<DrinkEntry | null> {
    return this.byId.get(id) ?? null;
  }

  async save(entry: DrinkEntry): Promise<void> {
    this.byId.set(entry.id, entry);
  }

  async delete(id: string): Promise<void> {
    this.byId.delete(id);
  }
}
