import { normalizedBarItem, type BarItem } from "../core/bar/barItem";
import { characterCount, DefaultBarItemValidator, type BarItemValidator } from "../core/bar/barItemValidation";
import type { BarRepository } from "../core/bar/barRepository";
import { drinkCategoryFromName } from "../core/bar/drinkCategory";
import { drinkIconFromKey } from "../core/bar/drinkIcon";
import { BAR_ITEM_LIMITS } from "../core/bar/barItem";
import { HISTORY_EARLIEST, HISTORY_LATEST, type DrinkEntry, type HistoryRepository } from "../core/log/drinkEntry";
import type { Clock } from "../core/time/clock";

/**
 * Sicherung von Bar und Verlauf als JSON-Datei – z. B. vom Safari-Tab in die App auf dem Home-Bildschirm (die hat
 * auf dem iPhone einen eigenen Speicher) oder auf ein anderes Gerät. Die Datei bleibt beim Nutzer; nichts geht an
 * einen Server.
 *
 * Format (Version 1):
 * `{ "format": "drink-with-me-backup", "version": 1, "exportedAt": "<ISO>", "bar": [BarItem…], "history": [DrinkEntry…] }`
 * mit genau den Feldern des Datenmodells (wie in IndexedDB).
 */
export const BACKUP_FORMAT = "drink-with-me-backup";
export const BACKUP_VERSION = 1;
/** Obergrenzen gegen versehentlich gewählte, riesige Dateien. */
export const MAX_BACKUP_CHARACTERS = 30_000_000;
const MAX_BAR_ITEMS = 1_000;
const MAX_HISTORY_ENTRIES = 200_000;
const MAX_ID_LENGTH = 100;
/** Zeitpunkte bis einen Tag in der Zukunft (Uhren gehen ungenau), nichts vor 2000. */
const FUTURE_TOLERANCE_MILLIS = 24 * 60 * 60 * 1000;
const EARLIEST_PLAUSIBLE = Date.UTC(2000, 0, 1);

export const BackupTexts = {
  NOT_A_BACKUP: "Die Datei ist keine Sicherung von Drink With Me.",
  TOO_NEW: "Diese Sicherung stammt aus einer neueren Version von Drink With Me. Bitte aktualisiere die App und versuche es erneut.",
  TOO_LARGE: "Die Datei ist zu groß für eine Sicherung von Drink With Me.",
} as const;

export type BackupParseResult =
  | { readonly ok: true; readonly bar: BarItem[]; readonly history: DrinkEntry[]; readonly invalid: number }
  | { readonly ok: false; readonly error: string };

/** Ergebnis des Einlesens (für die Meldung in den Einstellungen). */
export interface ImportSummary {
  readonly addedEntries: number;
  /** Schon vorhandene Einträge (gleiche ID) – bleiben unverändert. */
  readonly existingEntries: number;
  readonly addedBarItems: number;
  /** Getränke mit gleicher ID – übernehmen die Werte und die Reihenfolge aus der Datei. */
  readonly updatedBarItems: number;
  /** Unbrauchbare oder doppelte Zeilen der Datei (übersprungen). */
  readonly invalid: number;
}

/** Ungültige Sicherungsdatei; `message` ist ein deutscher Text für die Anzeige. */
export class BackupError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "BackupError";
  }
}

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

export function serializeBackup(bar: readonly BarItem[], history: readonly DrinkEntry[], exportedAt: number): string {
  return JSON.stringify({
    format: BACKUP_FORMAT,
    version: BACKUP_VERSION,
    exportedAt: new Date(exportedAt).toISOString(),
    bar: bar.map(storableBarItem),
    history: history.map(storableEntry),
  });
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

const finite = (value: unknown): value is number => typeof value === "number" && Number.isFinite(value);

function validId(value: unknown): value is string {
  // eslint-disable-next-line no-control-regex
  return typeof value === "string" && value.length > 0 && value.length <= MAX_ID_LENGTH && !/[\u0000-\u001f\u007f]/.test(value);
}

function barItemFrom(raw: unknown, index: number, validator: BarItemValidator): BarItem | null {
  if (!isRecord(raw) || !validId(raw.id) || typeof raw.name !== "string") return null;
  const { servingMilliliters, alcoholPercent, sugarGramsPer100Ml } = raw;
  if (!finite(servingMilliliters) || !finite(alcoholPercent) || !finite(sugarGramsPer100Ml)) return null;
  const item = normalizedBarItem({
    id: raw.id,
    name: raw.name,
    icon: drinkIconFromKey(String(raw.icon ?? "")),
    category: drinkCategoryFromName(String(raw.category ?? "")),
    servingMilliliters,
    alcoholPercent,
    sugarGramsPer100Ml,
    aliases: Array.isArray(raw.aliases) ? raw.aliases.filter((alias): alias is string => typeof alias === "string") : [],
    valueSource: raw.valueSource === "USER" ? "USER" : "DEFAULT",
    sortOrder: finite(raw.sortOrder) ? raw.sortOrder : index,
  });
  return validator.validate(item).length === 0 ? item : null;
}

/** Ein Eintrag mit denselben Grenzen wie beim Eintragen (Name 1–40 Zeichen, 1–2000 ml, 0–80 Vol.-%, 0–100 g). */
function entryFrom(raw: unknown, now: number): DrinkEntry | null {
  if (!isRecord(raw) || !validId(raw.id) || typeof raw.name !== "string") return null;
  const name = raw.name.trim();
  const { volumeMilliliters, alcoholPercent, sugarGramsPer100Ml, consumedAt } = raw;
  const ok =
    name.length > 0 &&
    characterCount(name) <= BAR_ITEM_LIMITS.NAME_MAX_LENGTH &&
    finite(volumeMilliliters) &&
    Number.isInteger(volumeMilliliters) &&
    volumeMilliliters >= BAR_ITEM_LIMITS.SERVING_MIN_MILLILITERS &&
    volumeMilliliters <= BAR_ITEM_LIMITS.SERVING_MAX_MILLILITERS &&
    finite(alcoholPercent) &&
    alcoholPercent >= 0 &&
    alcoholPercent <= BAR_ITEM_LIMITS.ALCOHOL_PERCENT_MAX &&
    finite(sugarGramsPer100Ml) &&
    sugarGramsPer100Ml >= 0 &&
    sugarGramsPer100Ml <= BAR_ITEM_LIMITS.SUGAR_GRAMS_PER_100_ML_MAX &&
    finite(consumedAt) &&
    consumedAt >= EARLIEST_PLAUSIBLE &&
    consumedAt <= now + FUTURE_TOLERANCE_MILLIS;
  if (!ok) return null;
  return {
    id: raw.id,
    barItemId: validId(raw.barItemId) ? raw.barItemId : null,
    name,
    icon: drinkIconFromKey(String(raw.icon ?? "")),
    category: drinkCategoryFromName(String(raw.category ?? "")),
    volumeMilliliters: volumeMilliliters as number,
    alcoholPercent: alcoholPercent as number,
    sugarGramsPer100Ml: sugarGramsPer100Ml as number,
    consumedAt: Math.trunc(consumedAt as number),
    shareWithFriends: raw.shareWithFriends === true,
  };
}

/** Liest und prüft eine Sicherung. Unbrauchbare und doppelte Zeilen werden gezählt und übersprungen. */
export function parseBackup(text: string, now: number, validator: BarItemValidator = new DefaultBarItemValidator()): BackupParseResult {
  if (text.length > MAX_BACKUP_CHARACTERS) return { ok: false, error: BackupTexts.TOO_LARGE };
  let value: unknown;
  try {
    value = JSON.parse(text);
  } catch {
    return { ok: false, error: BackupTexts.NOT_A_BACKUP };
  }
  if (!isRecord(value) || value.format !== BACKUP_FORMAT || !finite(value.version)) return { ok: false, error: BackupTexts.NOT_A_BACKUP };
  if (value.version > BACKUP_VERSION) return { ok: false, error: BackupTexts.TOO_NEW };
  const rawBar = value.bar ?? [];
  const rawHistory = value.history ?? [];
  if (!Array.isArray(rawBar) || !Array.isArray(rawHistory)) return { ok: false, error: BackupTexts.NOT_A_BACKUP };
  if (rawBar.length > MAX_BAR_ITEMS || rawHistory.length > MAX_HISTORY_ENTRIES) return { ok: false, error: BackupTexts.TOO_LARGE };

  let invalid = 0;
  const bar: BarItem[] = [];
  const barIds = new Set<string>();
  rawBar.forEach((raw, index) => {
    const item = barItemFrom(raw, index, validator);
    if (!item || barIds.has(item.id)) {
      invalid++;
      return;
    }
    barIds.add(item.id);
    bar.push(item);
  });
  const history: DrinkEntry[] = [];
  const entryIds = new Set<string>();
  for (const raw of rawHistory) {
    const entry = entryFrom(raw, now);
    if (!entry || entryIds.has(entry.id)) {
      invalid++;
      continue;
    }
    entryIds.add(entry.id);
    history.push(entry);
  }
  // Stabil nach sortOrder (bei Gleichstand die Reihenfolge der Datei).
  const ordered = bar.map((item, index) => ({ item, index })).sort((a, b) => a.item.sortOrder - b.item.sortOrder || a.index - b.index);
  return { ok: true, bar: ordered.map(({ item }) => item), history, invalid };
}

/**
 * Export und Import von Bar und Verlauf. Arbeitet direkt auf den **lokalen** Repositories: Eingelesene Einträge
 * werden nie (erneut) mit Freunden geteilt.
 *
 * Zusammenführen – es wird nie etwas gelöscht:
 * - Verlauf: Einträge mit neuer ID kommen dazu; vorhandene (gleiche ID) bleiben unverändert.
 * - Bar: Getränke aus der Datei in deren Reihenfolge (gleiche ID → Werte aus der Datei), danach die Getränke, die
 *   nur auf diesem Gerät sind.
 */
export class BackupService {
  constructor(
    private readonly bar: BarRepository,
    private readonly history: HistoryRepository,
    private readonly clock: Clock,
    private readonly onChanged: () => void = () => {},
  ) {}

  async exportJson(): Promise<string> {
    const [bar, history] = await Promise.all([this.bar.items(), this.history.entries(HISTORY_EARLIEST, HISTORY_LATEST)]);
    return serializeBackup(bar, history, this.clock.now());
  }

  /** @throws BackupError bei einer ungültigen Datei – dann bleibt alles unverändert. */
  async importJson(text: string): Promise<ImportSummary> {
    const parsed = parseBackup(text, this.clock.now());
    if (!parsed.ok) throw new BackupError(parsed.error);

    const current = await this.bar.items();
    const currentIds = new Set(current.map((item) => item.id));
    const fileIds = new Set(parsed.bar.map((item) => item.id));
    const ordered = [...parsed.bar, ...current.filter((item) => !fileIds.has(item.id))];
    for (const [index, item] of ordered.entries()) await this.bar.save({ ...item, sortOrder: index });

    const existing = new Set((await this.history.entries(HISTORY_EARLIEST, HISTORY_LATEST)).map((entry) => entry.id));
    let addedEntries = 0;
    for (const entry of parsed.history) {
      if (existing.has(entry.id)) continue;
      await this.history.save(entry);
      addedEntries++;
    }
    this.onChanged();
    const updatedBarItems = parsed.bar.filter((item) => currentIds.has(item.id)).length;
    return {
      addedEntries,
      existingEntries: parsed.history.length - addedEntries,
      addedBarItems: parsed.bar.length - updatedBarItems,
      updatedBarItems,
      invalid: parsed.invalid,
    };
  }
}
