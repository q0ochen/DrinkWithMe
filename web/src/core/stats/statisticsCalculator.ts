import { entryAlcoholGrams, entrySugarGrams, isAlcoholic, isSugary } from "../health/healthMath";
import { weeklyAlcoholBand, type AlcoholRiskBand } from "../health/healthReferences";
import type { DrinkEntry } from "../log/drinkEntry";
import type { Clock } from "../time/clock";
import { LocalDate, type TimeZone } from "../time/localDate";

/** Zucker aus Getränken an einem Kalendertag. */
export interface DailySugar {
  readonly date: LocalDate;
  readonly grams: number;
}

/**
 * Alkohol einer Kalenderwoche (Montag bis Sonntag). `alcoholFreeDays` zählt nur Tage bis heute und ab dem
 * ersten Eintrag (davor gibt es keine Daten).
 */
export interface WeeklyAlcohol {
  readonly weekStart: LocalDate;
  readonly grams: number;
  readonly alcoholFreeDays: number;
}

/** Bereich nach {@link weeklyAlcoholBand}. */
export function weeklyBand(week: WeeklyAlcohol): AlcoholRiskBand {
  return weeklyAlcoholBand(week.grams);
}

/** Kennzahlen des heutigen Tages. */
export interface TodaySummary {
  readonly sugarGrams: number;
  readonly alcoholGrams: number;
  readonly sugaryDrinks: number;
  readonly alcoholicDrinks: number;
  readonly totalMilliliters: number;
  readonly drinkCount: number;
}

/**
 * Letzte 30 Tage im Vergleich zu den 30 Tagen davor. Prozent = (jetzt − vorher) / vorher × 100;
 * `null`, wenn es keine Vergleichsbasis gibt (vorher 0 g oder der Vorzeitraum ist nicht vollständig erfasst).
 */
export interface MonthComparison {
  readonly sugarChangePercent: number | null;
  readonly alcoholChangePercent: number | null;
  readonly sugarGramsLast30Days: number;
  readonly sugarGramsPrevious30Days: number;
  readonly alcoholGramsLast30Days: number;
  readonly alcoholGramsPrevious30Days: number;
}

/**
 * Gleitender Durchschnitt der letzten 4 Wochen (28 Tage bis heute), bezogen auf die erfassten Tage.
 * Die Werte sind `null`, solange weniger als 7 Tage erfasst sind.
 */
export interface MovingAverage {
  readonly alcoholGramsPerWeek: number | null;
  readonly sugarGramsPerDay: number | null;
  readonly coveredDays: number;
}

export const StatisticsDefaults = {
  DEFAULT_SUGAR_DAYS: 14,
  DEFAULT_ALCOHOL_WEEKS: 8,
  COMPARISON_DAYS: 30,
  MOVING_AVERAGE_DAYS: 28,
  MOVING_AVERAGE_MIN_DAYS: 7,
} as const;

const DAYS_PER_WEEK = 7;

const sum = (values: Iterable<number>): number => {
  let total = 0;
  for (const value of values) total += value;
  return total;
};

/**
 * Persönliche Statistik als reine Funktionen über die Einträge – Port von `StatisticsCalculator` aus dem
 * Kotlin-Kern mit denselben Regeln und derselben Summierreihenfolge.
 *
 * Alle Zeiträume sind **Kalendertage in `zone`** (nicht 24-h-Blöcke), daher sind Sommer-/Winterzeit-Wechsel
 * korrekt. Wochen beginnen immer am Montag. „Erfasst“ ist ein Tag ab dem Kalendertag des frühesten übergebenen
 * Eintrags bis heute – die App übergibt deshalb die gesamte lokale Historie.
 */
export class StatisticsCalculator {
  constructor(
    private readonly clock: Clock,
    private readonly zone: TimeZone,
  ) {}

  /** Zucker pro Tag für die letzten `days` Tage bis heute (aufsteigend, Tage ohne Einträge mit 0 g). */
  dailySugar(entries: readonly DrinkEntry[], days: number = StatisticsDefaults.DEFAULT_SUGAR_DAYS): DailySugar[] {
    if (days < 1) throw new RangeError("days muss mindestens 1 sein.");
    const sugarByDate = new Map<number, number>();
    for (const [day, list] of this.groupByDate(entries)) sugarByDate.set(day, sum(list.map(entrySugarGrams)));
    const start = this.currentDate().minusDays(days - 1);
    return Array.from({ length: days }, (_, offset) => {
      const date = start.plusDays(offset);
      return { date, grams: sugarByDate.get(date.epochDay) ?? 0.0 };
    });
  }

  /** Alkohol pro Woche (Montag bis Sonntag) für die letzten `weeks` Wochen einschließlich der laufenden. */
  weeklyAlcohol(entries: readonly DrinkEntry[], weeks: number = StatisticsDefaults.DEFAULT_ALCOHOL_WEEKS): WeeklyAlcohol[] {
    if (weeks < 1) throw new RangeError("weeks muss mindestens 1 sein.");
    const today = this.currentDate();
    const byDate = this.groupByDate(entries);
    const trackingStart = this.trackingStart(entries);
    const currentWeekStart = today.mondayOfWeek();
    const result: WeeklyAlcohol[] = [];
    for (let weeksBack = weeks - 1; weeksBack >= 0; weeksBack--) {
      const weekStart = currentWeekStart.minusWeeks(weeksBack);
      const days = Array.from({ length: DAYS_PER_WEEK }, (_, index) => weekStart.plusDays(index));
      result.push({
        weekStart,
        grams: sum(days.map((day) => sum((byDate.get(day.epochDay) ?? []).map(entryAlcoholGrams)))),
        alcoholFreeDays: days.filter(
          (day) =>
            this.isTracked(day, trackingStart, today) &&
            !(byDate.get(day.epochDay) ?? []).some((entry) => isAlcoholic(entry.alcoholPercent)),
        ).length,
      });
    }
    return result;
  }

  /** Kennzahlen von heute. */
  today(entries: readonly DrinkEntry[]): TodaySummary {
    const today = this.currentDate();
    const todays = entries.filter((entry) => this.dateOf(entry).equals(today));
    return {
      sugarGrams: sum(todays.map(entrySugarGrams)),
      alcoholGrams: sum(todays.map(entryAlcoholGrams)),
      sugaryDrinks: todays.filter((entry) => isSugary(entry.sugarGramsPer100Ml)).length,
      alcoholicDrinks: todays.filter((entry) => isAlcoholic(entry.alcoholPercent)).length,
      totalMilliliters: sum(todays.map((entry) => entry.volumeMilliliters)),
      drinkCount: todays.length,
    };
  }

  /** Zucker und Alkohol der letzten 30 Tage (bis heute) gegenüber den 30 Tagen davor. */
  monthComparison(entries: readonly DrinkEntry[]): MonthComparison {
    const days = StatisticsDefaults.COMPARISON_DAYS;
    const today = this.currentDate();
    const currentStart = today.minusDays(days - 1);
    const previousStart = currentStart.minusDays(days);
    const previousEnd = currentStart.minusDays(1);
    const inRange = (entry: DrinkEntry, from: LocalDate, to: LocalDate): boolean => {
      const date = this.dateOf(entry).epochDay;
      return date >= from.epochDay && date <= to.epochDay;
    };
    const current = entries.filter((entry) => inRange(entry, currentStart, today));
    const previous = entries.filter((entry) => inRange(entry, previousStart, previousEnd));
    const trackingStart = this.trackingStart(entries);
    const hasBasis = trackingStart !== null && !trackingStart.isAfter(previousStart);

    const sugarNow = sum(current.map(entrySugarGrams));
    const sugarBefore = sum(previous.map(entrySugarGrams));
    const alcoholNow = sum(current.map(entryAlcoholGrams));
    const alcoholBefore = sum(previous.map(entryAlcoholGrams));
    return {
      sugarChangePercent: changePercent(sugarNow, sugarBefore, hasBasis),
      alcoholChangePercent: changePercent(alcoholNow, alcoholBefore, hasBasis),
      sugarGramsLast30Days: sugarNow,
      sugarGramsPrevious30Days: sugarBefore,
      alcoholGramsLast30Days: alcoholNow,
      alcoholGramsPrevious30Days: alcoholBefore,
    };
  }

  /** Alkohol in g/Woche und Zucker in g/Tag über die letzten 28 Tage, bezogen auf die erfassten Tage. */
  movingAverage4Weeks(entries: readonly DrinkEntry[]): MovingAverage {
    const today = this.currentDate();
    const windowStart = today.minusDays(StatisticsDefaults.MOVING_AVERAGE_DAYS - 1);
    const trackingStart = this.trackingStart(entries);
    if (trackingStart === null) return { alcoholGramsPerWeek: null, sugarGramsPerDay: null, coveredDays: 0 };
    const firstCovered = windowStart.epochDay > trackingStart.epochDay ? windowStart : trackingStart;
    const coveredDays = firstCovered.isAfter(today) ? 0 : today.epochDay - firstCovered.epochDay + 1;
    if (coveredDays < StatisticsDefaults.MOVING_AVERAGE_MIN_DAYS) {
      return { alcoholGramsPerWeek: null, sugarGramsPerDay: null, coveredDays };
    }
    const inWindow = entries.filter((entry) => {
      const date = this.dateOf(entry).epochDay;
      return date >= windowStart.epochDay && date <= today.epochDay;
    });
    return {
      alcoholGramsPerWeek: (sum(inWindow.map(entryAlcoholGrams)) / coveredDays) * DAYS_PER_WEEK,
      sugarGramsPerDay: sum(inWindow.map(entrySugarGrams)) / coveredDays,
      coveredDays,
    };
  }

  /** Zusammenhängende alkoholfreie Tage bis einschließlich heute (0, wenn heute schon Alkohol dabei war). */
  alcoholFreeStreak(entries: readonly DrinkEntry[]): number {
    const trackingStart = this.trackingStart(entries);
    if (trackingStart === null) return 0;
    const alcoholicDays = new Set(
      entries.filter((entry) => isAlcoholic(entry.alcoholPercent)).map((entry) => this.dateOf(entry).epochDay),
    );
    let day = this.currentDate();
    let streak = 0;
    while (!day.isBefore(trackingStart) && !alcoholicDays.has(day.epochDay)) {
      streak++;
      day = day.minusDays(1);
    }
    return streak;
  }

  /** Heutiges Datum in der Zeitzone des Rechners. */
  currentDate(): LocalDate {
    return this.zone.localDate(this.clock.now());
  }

  /** Kalendertag eines Eintrags in der Zeitzone des Rechners. */
  dateOf(entry: DrinkEntry): LocalDate {
    return this.zone.localDate(entry.consumedAt);
  }

  private groupByDate(entries: readonly DrinkEntry[]): Map<number, DrinkEntry[]> {
    const groups = new Map<number, DrinkEntry[]>();
    for (const entry of entries) {
      const key = this.dateOf(entry).epochDay;
      const list = groups.get(key);
      if (list) list.push(entry);
      else groups.set(key, [entry]);
    }
    return groups;
  }

  private trackingStart(entries: readonly DrinkEntry[]): LocalDate | null {
    let earliest: LocalDate | null = null;
    for (const entry of entries) {
      const date = this.dateOf(entry);
      if (earliest === null || date.isBefore(earliest)) earliest = date;
    }
    return earliest;
  }

  private isTracked(day: LocalDate, trackingStart: LocalDate | null, today: LocalDate): boolean {
    return trackingStart !== null && !day.isBefore(trackingStart) && !day.isAfter(today);
  }
}

function changePercent(now: number, before: number, hasBasis: boolean): number | null {
  return !hasBasis || before <= 0.0 ? null : ((now - before) / before) * 100.0;
}
