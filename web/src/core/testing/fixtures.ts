// Gegenstück zu android/core/src/test/kotlin/com/drinkwithme/core/TestFixtures.kt – gleiche Hilfen, damit die
// Golden-Tests die Kotlin-Testfälle 1:1 übernehmen können.
import type { BarItem } from "../bar/barItem";
import { catalogItem as lookupCatalogItem } from "../bar/defaultBarCatalog";
import type { DrinkEntry } from "../log/drinkEntry";
import type { Clock, IdGenerator } from "../time/clock";
import { ianaTimeZone, type TimeZone } from "../time/localDate";

/** Uhr, deren Zeitpunkt im Test verstellt werden kann. */
export class MutableClock implements Clock {
  constructor(public nowMillis: number) {}

  now(): number {
    return this.nowMillis;
  }

  advance(millis: number): void {
    this.nowMillis += millis;
  }
}

/** Fortlaufende IDs „id-1“, „id-2“ … */
export class SequentialIds implements IdGenerator {
  private counter = 0;

  newId(): string {
    this.counter += 1;
    return `id-${this.counter}`;
  }
}

export const BERLIN: TimeZone = ianaTimeZone("Europe/Berlin");

const berlinParts = new Intl.DateTimeFormat("en-US", {
  timeZone: "Europe/Berlin",
  hourCycle: "h23",
  year: "numeric",
  month: "numeric",
  day: "numeric",
  hour: "numeric",
  minute: "numeric",
  second: "numeric",
});

function berlinOffset(epochMillis: number): number {
  const parts = Object.fromEntries(berlinParts.formatToParts(epochMillis).map((part) => [part.type, part.value]));
  const asUtc = Date.UTC(
    Number(parts.year),
    Number(parts.month) - 1,
    Number(parts.day),
    Number(parts.hour),
    Number(parts.minute),
    Number(parts.second),
  );
  return asUtc - Math.floor(epochMillis / 1000) * 1000;
}

/** Lokale Berliner Zeit („2026-11-10T18:00“) als Zeitpunkt (wie `berlin()` in den Kotlin-Tests). */
export function berlin(localDateTime: string): number {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/.exec(localDateTime);
  if (!match) throw new Error(`Ungültige Zeit: ${localDateTime}`);
  const [, y, mo, d, h, mi, s] = match;
  const asUtc = Date.UTC(Number(y), Number(mo) - 1, Number(d), Number(h), Number(mi), Number(s ?? "0"));
  let guess = asUtc;
  for (let i = 0; i < 3; i++) guess = asUtc - berlinOffset(guess);
  return guess;
}

/** `Instant.parse("2026-10-24T22:30:00Z")` */
export function instant(iso: string): number {
  const value = Date.parse(iso);
  if (Number.isNaN(value)) throw new Error(`Ungültiger Zeitpunkt: ${iso}`);
  return value;
}

export const SECOND = 1000;
export const MINUTE = 60 * SECOND;
export const HOUR = 60 * MINUTE;
export const DAY = 24 * HOUR;

export function catalogItem(id: string): BarItem {
  const item = lookupCatalogItem(id);
  if (!item) throw new Error(`Unbekannte Katalog-ID ${id}`);
  return item;
}

let entryIds = 0;
const nextEntryId = (): string => `entry-${++entryIds}`;

/** Ein Eintrag aus einem Katalog-Getränk zu einem festen Zeitpunkt. */
export function entryOf(
  item: BarItem,
  at: number,
  options: { volume?: number; share?: boolean; id?: string } = {},
): DrinkEntry {
  return {
    id: options.id ?? nextEntryId(),
    barItemId: item.id,
    name: item.name,
    icon: item.icon,
    category: item.category,
    volumeMilliliters: options.volume ?? item.servingMilliliters,
    alcoholPercent: item.alcoholPercent,
    sugarGramsPer100Ml: item.sugarGramsPer100Ml,
    consumedAt: at,
    shareWithFriends: options.share ?? false,
  };
}

/** Ein frei definierter Eintrag. */
export function entry(
  at: number,
  options: { volume?: number; alcoholPercent?: number; sugarPer100Ml?: number; share?: boolean; name?: string; id?: string } = {},
): DrinkEntry {
  return {
    id: options.id ?? nextEntryId(),
    barItemId: null,
    name: options.name ?? "Testgetränk",
    icon: "other",
    category: "OTHER",
    volumeMilliliters: options.volume ?? 500,
    alcoholPercent: options.alcoholPercent ?? 0.0,
    sugarGramsPer100Ml: options.sugarPer100Ml ?? 0.0,
    consumedAt: at,
    shareWithFriends: options.share ?? false,
  };
}

/** Einfaches Bar-Getränk für Tests. */
export function barItem(
  name: string,
  options: {
    id?: string;
    aliases?: string[];
    alcoholPercent?: number;
    sugarPer100Ml?: number;
    serving?: number;
    sortOrder?: number;
  } = {},
): BarItem {
  return {
    id: options.id ?? `bar-${name.toLowerCase()}`,
    name,
    icon: "other",
    category: "OTHER",
    servingMilliliters: options.serving ?? 250,
    alcoholPercent: options.alcoholPercent ?? 0.0,
    sugarGramsPer100Ml: options.sugarPer100Ml ?? 0.0,
    aliases: options.aliases ?? [],
    valueSource: "DEFAULT",
    sortOrder: options.sortOrder ?? 0,
  };
}
