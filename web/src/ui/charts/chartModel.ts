import { ALCOHOL_RISK_BAND_LABELS, DAILY_SUGAR_LINES, HealthReferences, WEEKLY_ALCOHOL_BANDS, type AlcoholRiskBand, type ReferenceLineStyle } from "../../core/health/healthReferences";
import { weeklyBand, type DailySugar, type WeeklyAlcohol } from "../../core/stats/statisticsCalculator";
import type { LocalDate } from "../../core/time/localDate";
import { dayAndMonth, formatGrams, pluralize, shortDate, weekdayName, weekdayShort } from "../format";

/**
 * Gemeinsames Modell der Diagramme – genutzt vom SVG im Verlauf **und** vom Bild zum Teilen, damit beide
 * dieselben Bezugslinien und -bänder (aus `HealthReferences`) zeigen.
 */
export interface ChartBar {
  readonly key: string;
  /** Kurz unter dem Balken, z. B. „Di“ bzw. „9.11.“ */
  readonly shortLabel: string;
  /** Zweite Zeile, z. B. „10.11.“ */
  readonly secondLabel: string;
  /** Vollständig für Tabelle und Screenreader, z. B. „Dienstag, 10. November“ */
  readonly label: string;
  readonly value: number;
  readonly valueText: string;
  readonly isCurrent: boolean;
  /** Zusatz für die Tabelle, z. B. „4 alkoholfreie Tage · risikoarm“ */
  readonly detail: string | null;
}

export interface ChartLine {
  readonly value: number;
  readonly shortLabel: string;
  readonly label: string;
  readonly style: ReferenceLineStyle;
}

export interface ChartBand {
  readonly band: AlcoholRiskBand;
  readonly from: number;
  readonly to: number;
  readonly label: string;
}

export interface ChartModel {
  readonly kind: "sugar" | "alcohol";
  readonly title: string;
  readonly max: number;
  readonly ticks: readonly number[];
  readonly bars: readonly ChartBar[];
  readonly lines: readonly ChartLine[];
  readonly bands: readonly ChartBand[];
  /** Kurzfassung für Screenreader. */
  readonly summary: string;
  /** Pflicht- und Hinweistexte unter dem Diagramm. */
  readonly notes: readonly string[];
}

const STEPS = [5, 10, 20, 25, 50, 100, 200, 250, 500, 1000];

/** Achsenmaximum: nächster „runder“ Wert ≥ `value` mit höchstens 5 Teilstrichen. */
export function niceScale(value: number): { max: number; ticks: number[] } {
  const target = Math.max(value, 1);
  const step = STEPS.find((candidate) => Math.ceil(target / candidate) <= 5) ?? Math.ceil(target / 5 / 1000) * 1000;
  const max = Math.ceil(target / step) * step;
  const ticks: number[] = [];
  for (let tick = 0; tick <= max + 1e-9; tick += step) ticks.push(tick);
  return { max, ticks };
}

export function sugarChartModel(days: readonly DailySugar[], today: LocalDate): ChartModel {
  const highest = Math.max(0, ...days.map((day) => day.grams));
  const { max, ticks } = niceScale(Math.max(highest, HealthReferences.DAILY_SUGAR_LIMIT_GRAMS) * 1.1);
  const bars = days.map<ChartBar>((day) => ({
    key: day.date.toString(),
    shortLabel: weekdayShort(day.date),
    secondLabel: shortDate(day.date),
    label: `${weekdayName(day.date)}, ${dayAndMonth(day.date)}${day.date.equals(today) ? " (heute)" : ""}`,
    value: day.grams,
    valueText: formatGrams(day.grams),
    isCurrent: day.date.equals(today),
    detail: null,
  }));
  const todayValue = days.find((day) => day.date.equals(today))?.grams ?? 0;
  const daysOverLimit = days.filter((day) => day.grams > HealthReferences.DAILY_SUGAR_LIMIT_GRAMS).length;
  return {
    kind: "sugar",
    title: `Zucker aus Getränken pro Tag (letzte ${days.length} Tage)`,
    max,
    ticks,
    bars,
    lines: DAILY_SUGAR_LINES.map((line) => ({
      value: line.grams,
      shortLabel: formatGrams(line.grams),
      label: line.label,
      style: line.style,
    })),
    bands: [],
    summary:
      `Zucker aus Getränken in den letzten ${days.length} Tagen: heute ${formatGrams(todayValue)}, ` +
      `höchster Tag ${formatGrams(highest)}, ${pluralize(daysOverLimit, "Tag", "Tage")} über 50 g.`,
    notes: [HealthReferences.SUGAR_NOTE, HealthReferences.ADULTS_ONLY_NOTE],
  };
}

export function alcoholChartModel(weeks: readonly WeeklyAlcohol[], today: LocalDate, showBands: boolean): ChartModel {
  const highest = Math.max(0, ...weeks.map((week) => week.grams));
  const reference = showBands ? HealthReferences.WEEKLY_ALCOHOL_HIGH_ABOVE_GRAMS * 1.15 : 10;
  const { max, ticks } = niceScale(Math.max(highest * 1.1, reference));
  const currentWeek = today.mondayOfWeek();
  const bars = weeks.map<ChartBar>((week) => {
    const isCurrent = week.weekStart.equals(currentWeek);
    const band = ALCOHOL_RISK_BAND_LABELS[weeklyBand(week)];
    return {
      key: week.weekStart.toString(),
      shortLabel: isCurrent ? "jetzt" : shortDate(week.weekStart),
      secondLabel: isCurrent ? shortDate(week.weekStart) : "",
      label: `Woche ab Montag, ${dayAndMonth(week.weekStart)}${isCurrent ? " (laufende Woche)" : ""}`,
      value: week.grams,
      valueText: formatGrams(week.grams),
      isCurrent,
      detail: `${pluralize(week.alcoholFreeDays, "alkoholfreier Tag", "alkoholfreie Tage")}${week.grams > 0 ? ` · ${band}` : ""}`,
    };
  });
  const bands: ChartBand[] = showBands
    ? WEEKLY_ALCOHOL_BANDS.map((band) => ({
        band: band.band,
        from: band.fromGrams,
        to: band.toGrams ?? max,
        label: ALCOHOL_RISK_BAND_LABELS[band.band],
      }))
    : [];
  const thisWeek = weeks.find((week) => week.weekStart.equals(currentWeek));
  return {
    kind: "alcohol",
    title: `Alkohol pro Woche (letzte ${weeks.length} Wochen, Montag bis Sonntag)`,
    max,
    ticks,
    bars,
    lines: [],
    bands,
    summary:
      `Alkohol pro Woche: diese Woche ${formatGrams(thisWeek?.grams ?? 0)}` +
      `${thisWeek ? `, ${pluralize(thisWeek.alcoholFreeDays, "alkoholfreier Tag", "alkoholfreie Tage")}` : ""}; ` +
      `höchste Woche ${formatGrams(highest)}.`,
    notes: [
      HealthReferences.ALCOHOL_MANDATORY_NOTE,
      `Bezugswerte: ${HealthReferences.ALCOHOL_SOURCE} – unter 27 g pro Woche risikoarm, 27 bis 81 g moderates Risiko, über 81 g riskant.`,
      HealthReferences.ADULTS_ONLY_NOTE,
    ],
  };
}
