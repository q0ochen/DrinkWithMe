import { ALCOHOL_RISK_BAND_LABELS, HealthReferences } from "../health/healthReferences";
import type { FriendDrinkEvent } from "../social/ports";
import {
  StatisticsDefaults,
  weeklyBand,
  type MonthComparison,
  type TodaySummary,
  type WeeklyAlcohol,
} from "../stats/statisticsCalculator";
import type { LocalDate } from "../time/localDate";
import { withIndefiniteArticle } from "./germanArticles";
import { cardinal, decimal, ordinalDative, roundedSignum, roundsToOne } from "./germanNumberWords";

/** Einheiten, die beim Vorlesen ausgeschrieben werden. */
export const SpeechUnit = {
  MILLILITER: "Milliliter",
  LITER: "Liter",
  GRAMM: "Gramm",
  PROZENT: "Prozent",
} as const;
export type SpeechUnit = (typeof SpeechUnit)[keyof typeof SpeechUnit];

const SOMEONE = "Jemand";
const SOMETHING = "etwas";
const A_DRINK = "Getränk";
const MONTHS = [
  "Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember",
];
/** Wie Javas `\s` (nur ASCII-Leerraum). */
const WHITESPACE = /[ \t\n\x0B\f\r]+/g;
/** Zeichenklassen, die nicht vorgelesen werden (wie im Kotlin-Kern: So, Sk, Cf, Co, Cs, Cn, Cc, Mn, Me). */
const UNSPEAKABLE = /[\p{So}\p{Sk}\p{Cf}\p{Co}\p{Cs}\p{Cn}\p{Cc}\p{Mn}\p{Me}]/gu;

/** Entfernt Emojis, Symbole und Steuerzeichen aus Namen, damit die Sprachausgabe sie nicht vorliest. */
export function speakable(text: string): string {
  return text.normalize("NFC").replace(UNSPEAKABLE, " ").replace(WHITESPACE, " ").trim();
}

/** Zahl mit Einheit: „zweihundertfünfzig Milliliter“, „ein Gramm“, „eins Komma fünf Liter“. */
export function quantity(value: number, unit: SpeechUnit, maxFractionDigits = 1): string {
  const number = roundsToOne(value, maxFractionDigits) ? "ein" : decimal(value, maxFractionDigits);
  return `${number} ${unit}`;
}

/** Ganze Zahl mit Einheit (ohne Nachkommastellen). */
export function quantityInt(value: number, unit: SpeechUnit): string {
  return quantity(value, unit, 0);
}

/** Menge: unter 1000 ml in Millilitern, sonst in Litern auf eine Nachkommastelle („eins Komma fünf Liter“). */
export function volume(milliliters: number): string {
  if (milliliters < 1000) return quantityInt(milliliters, SpeechUnit.MILLILITER);
  return quantity(Math.trunc((milliliters + 50) / 100) / 10.0, SpeechUnit.LITER);
}

function gramDigits(value: number): number {
  return Math.abs(value) >= 10.0 ? 0 : 1;
}

/** Gramm: ab 10 g ganzzahlig („siebenundzwanzig Gramm“), darunter mit einer Nachkommastelle. */
export function grams(value: number): string {
  return quantity(value, SpeechUnit.GRAMM, gramDigits(value));
}

function isZeroGrams(value: number): boolean {
  return roundedSignum(value, gramDigits(value)) === 0;
}

function change(percent: number | null): string {
  if (percent === null) return "noch ohne Vergleichswert";
  const signum = roundedSignum(percent, 0);
  if (signum === 0) return "etwa gleich viel";
  const amount = quantity(Math.abs(percent), SpeechUnit.PROZENT, 0);
  return signum < 0 ? `${amount} weniger` : `${amount} mehr`;
}

function spokenDate(day: LocalDate): string {
  return `${ordinalDative(day.dayOfMonth)} ${MONTHS[day.month - 1]}`;
}

/**
 * **Vorlese-Sätze** (Port von `SpeechTexts`) – getrennt von den Anzeige-Texten: ganze deutsche Sätze, Zahlen und
 * Einheiten ausgeschrieben, keine Emojis oder Abkürzungen. Keine Diagnose, keine Erlaubnis-Sprache.
 */
export const SpeechTexts = {
  /** „Anna trinkt eine Apfelschorle.“ – mit Menge: „Anna trinkt eine Apfelschorle, fünfhundert Milliliter.“ */
  drinkEvent(authorName: string, drinkName: string, volumeMilliliters: number | null = null): string {
    const author = speakable(authorName) || SOMEONE;
    const drink = withIndefiniteArticle(speakable(drinkName) || SOMETHING);
    const amount = volumeMilliliters === null ? "" : `, ${volume(volumeMilliliters)}`;
    return `${author} trinkt ${drink}${amount}.`;
  },

  /** Vorlese-Satz für ein Ereignis eines Freundes. */
  friendEvent(event: FriendDrinkEvent, withVolume = false): string {
    return SpeechTexts.drinkEvent(event.author.displayName, event.name, withVolume ? event.volumeMilliliters : null);
  },

  /** Kurze Ansage nach dem Eintragen: „Apfelschorle eingetragen.“ */
  logged(drinkName: string): string {
    return `${speakable(drinkName) || A_DRINK} eingetragen.`;
  },

  /** Ansage nach „Rückgängig“: „Apfelschorle wieder entfernt.“ */
  undone(drinkName: string): string {
    return `${speakable(drinkName) || A_DRINK} wieder entfernt.`;
  },

  /** Rückfrage bei mehrdeutiger Spracheingabe: „Meinst du Apfelschorle oder Apfelsaft?“ */
  whichDrink(first: string, second: string): string {
    return `Meinst du ${speakable(first)} oder ${speakable(second)}?`;
  },

  /** Spracheingabe ohne Treffer. */
  notUnderstood(): string {
    return "Das habe ich leider nicht verstanden. Sag bitte den Namen eines Getränks aus deiner Bar.";
  },

  /** „Heute aus Getränken: fünfunddreißig Gramm Zucker.“ … „Insgesamt eins Komma fünf Liter.“ */
  today(summary: TodaySummary): string {
    if (summary.drinkCount === 0) return "Heute hast du noch nichts eingetragen.";
    const sugar = isZeroGrams(summary.sugarGrams) ? "kein Zucker" : `${grams(summary.sugarGrams)} Zucker`;
    const alcohol = isZeroGrams(summary.alcoholGrams) ? "" : ` und ${grams(summary.alcoholGrams)} Alkohol`;
    return `Heute aus Getränken: ${sugar}${alcohol}. Insgesamt ${volume(summary.totalMilliliters)}.`;
  },

  /** „Diese Woche: zweiundvierzig Gramm Alkohol, vier alkoholfreie Tage.“ (+ Bereich und Pflichtsatz). */
  weeklyAlcohol(week: WeeklyAlcohol, today: LocalDate): string {
    const currentWeekStart = today.mondayOfWeek();
    let period: string;
    if (week.weekStart.equals(currentWeekStart)) period = "Diese Woche";
    else if (week.weekStart.equals(currentWeekStart.minusWeeks(1))) period = "Letzte Woche";
    else period = `In der Woche ab dem ${spokenDate(week.weekStart)}`;
    const noAlcohol = isZeroGrams(week.grams);
    const alcohol = noAlcohol ? "kein Alkohol" : `${grams(week.grams)} Alkohol`;
    let freeDays: string;
    if (week.alcoholFreeDays === 0) freeDays = "kein alkoholfreier Tag";
    else if (week.alcoholFreeDays === 1) freeDays = "ein alkoholfreier Tag";
    else freeDays = `${cardinal(week.alcoholFreeDays)} alkoholfreie Tage`;
    const summary = `${period}: ${alcohol}, ${freeDays}.`;
    if (noAlcohol) return summary;
    return (
      `${summary} Das entspricht dem Bereich ${ALCOHOL_RISK_BAND_LABELS[weeklyBand(week)]} nach der Deutschen ` +
      `Gesellschaft für Ernährung. ${HealthReferences.ALCOHOL_MANDATORY_NOTE}`
    );
  },

  /** „Vergleich der letzten dreißig Tage mit den dreißig Tagen davor: Zucker aus Getränken …, Alkohol ….“ */
  monthComparison(comparison: MonthComparison): string {
    const days = cardinal(StatisticsDefaults.COMPARISON_DAYS);
    return (
      `Vergleich der letzten ${days} Tage mit den ${days} Tagen davor: ` +
      `Zucker aus Getränken ${change(comparison.sugarChangePercent)}, ` +
      `Alkohol ${change(comparison.alcoholChangePercent)}.`
    );
  },
} as const;
