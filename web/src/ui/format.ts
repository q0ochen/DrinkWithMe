import type { LocalDate, TimeZone } from "../core/time/localDate";

/**
 * Anzeige-Formate (deutsch, mit Ziffern) – getrennt von den Vorlese-Sätzen (`SpeechTexts`).
 */
const WEEKDAYS = ["Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag"];
const WEEKDAYS_SHORT = ["Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"];
const MONTHS = ["Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember"];

const oneDecimal = new Intl.NumberFormat("de-DE", { maximumFractionDigits: 1, minimumFractionDigits: 0 });
const noDecimal = new Intl.NumberFormat("de-DE", { maximumFractionDigits: 0 });

/** „1,6 g“ unter 10 g, sonst „35 g“ (wie beim Vorlesen gerundet). */
export function formatGrams(grams: number): string {
  const value = Math.abs(grams) < 10 ? oneDecimal.format(grams) : noDecimal.format(grams);
  return `${value === "-0" ? "0" : value} g`;
}

export function formatNumber(value: number, maxFractionDigits = 1): string {
  return new Intl.NumberFormat("de-DE", { maximumFractionDigits: maxFractionDigits }).format(value);
}

/** „−20 %“ bzw. „+5 %“ (echtes Minuszeichen). */
export function formatPercentChange(percent: number): string {
  const rounded = Math.round(percent);
  if (rounded === 0) return "±0 %";
  return `${rounded > 0 ? "+" : "−"}${noDecimal.format(Math.abs(rounded))} %`;
}

export function weekdayName(date: LocalDate): string {
  return WEEKDAYS[date.dayOfWeek - 1];
}

export function weekdayShort(date: LocalDate): string {
  return WEEKDAYS_SHORT[date.dayOfWeek - 1];
}

/** „9. November“ */
export function dayAndMonth(date: LocalDate): string {
  return `${date.dayOfMonth}. ${MONTHS[date.month - 1]}`;
}

/** „9.11.“ */
export function shortDate(date: LocalDate): string {
  return `${date.dayOfMonth}.${date.month}.`;
}

/** „Heute“, „Gestern“ oder „Montag, 9. November“ (mit Jahr, wenn nicht das laufende). */
export function dayHeading(date: LocalDate, today: LocalDate): string {
  if (date.equals(today)) return "Heute";
  if (date.equals(today.minusDays(1))) return "Gestern";
  const year = date.year === today.year ? "" : ` ${date.year}`;
  return `${weekdayName(date)}, ${dayAndMonth(date)}${year}`;
}

/** Uhrzeit „18:05“ in der Zeitzone des Geräts. */
export function formatTime(epochMillis: number): string {
  return new Intl.DateTimeFormat("de-DE", { hour: "2-digit", minute: "2-digit" }).format(epochMillis);
}

/** „gerade eben“, „vor 5 Min.“, „vor 2 Std.“, „gestern, 18:05“, „Montag, 9. November, 18:05“. */
export function formatRelative(epochMillis: number, now: number, zone: TimeZone): string {
  const minutes = Math.floor((now - epochMillis) / 60_000);
  if (minutes < 1) return "gerade eben";
  if (minutes < 60) return `vor ${minutes} Min.`;
  const date = zone.localDate(epochMillis);
  const today = zone.localDate(now);
  if (date.equals(today)) return `vor ${Math.floor(minutes / 60)} Std.`;
  const heading = dayHeading(date, today);
  return `${heading === "Gestern" ? "gestern" : heading}, ${formatTime(epochMillis)}`;
}

/** Anfangsbuchstaben für den Platzhalter statt eines Profilbilds (keine fremden Bilder laden). */
export function initials(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean);
  const letters = words.length >= 2 ? [words[0], words[words.length - 1]] : words.slice(0, 1);
  return letters.map((word) => [...word][0]?.toLocaleUpperCase("de-DE") ?? "").join("") || "?";
}

export function pluralize(count: number, one: string, many: string): string {
  return `${count} ${count === 1 ? one : many}`;
}
