/** Bereich der wöchentlichen Alkoholmenge nach DGE 2024. Die Bezeichnungen werden genau so angezeigt. */
export type AlcoholRiskBand = "LOW_RISK" | "MODERATE_RISK" | "HIGH_RISK";

export const ALCOHOL_RISK_BAND_LABELS: Readonly<Record<AlcoholRiskBand, string>> = {
  LOW_RISK: "risikoarm",
  MODERATE_RISK: "moderates Risiko",
  HIGH_RISK: "riskant",
};

/** Ein Band im Wochen-Diagramm; `toGrams` = `null` für „offen“. */
export interface AlcoholBand {
  readonly band: AlcoholRiskBand;
  readonly fromGrams: number;
  readonly toGrams: number | null;
}

/** Strichart einer Bezugslinie im Zucker-Diagramm. */
export type ReferenceLineStyle = "SOLID" | "DASHED";

/** Bezugslinie im Zucker-Diagramm (Gramm pro Tag). */
export interface SugarReferenceLine {
  readonly grams: number;
  readonly label: string;
  readonly style: ReferenceLineStyle;
}

/**
 * Bezugswerte – nur für gesunde Erwachsene (wie `HealthReferences` im Kotlin-Kern). Bänder sind ausblendbar
 * und werden **nicht grün** dargestellt. Formulierungen: keine Diagnose, keine Erlaubnis-Sprache.
 */
export const HealthReferences = {
  ALCOHOL_SOURCE: "DGE 2024",
  /** Unter diesem Wert (g/Woche): „risikoarm“. */
  WEEKLY_ALCOHOL_MODERATE_FROM_GRAMS: 27.0,
  /** Über diesem Wert (g/Woche): „riskant“. */
  WEEKLY_ALCOHOL_HIGH_ABOVE_GRAMS: 81.0,
  /** Pflichtsatz bei jeder Darstellung der Alkohol-Bezugswerte. */
  ALCOHOL_MANDATORY_NOTE: "Es gibt keine unbedenkliche Menge Alkohol. Weniger ist besser.",
  DAILY_SUGAR_LIMIT_GRAMS: 50.0,
  DAILY_SUGAR_LIMIT_LABEL: "Obergrenze für freien Zucker in der gesamten Ernährung (bei 2000 kcal)",
  DAILY_SUGAR_WHO_GRAMS: 25.0,
  DAILY_SUGAR_WHO_LABEL: "WHO: unter 25 g bringt zusätzlichen Nutzen",
  /** Hinweis beim Zucker-Diagramm. */
  SUGAR_NOTE: "Getränke sind nur ein Teil davon.",
  /** Hinweis zum Geltungsbereich aller Bezugswerte. */
  ADULTS_ONLY_NOTE: "Die Bezugswerte gelten nur für gesunde Erwachsene.",
  /** Info-Text (Einstellungen, erster Start, Verlauf). */
  INFO_TEXT:
    "Diese App ist ein persönliches Getränketagebuch. Sie stellt keine Diagnose und ersetzt " +
    "keine ärztliche Beratung. Kinder, Jugendliche, Schwangere und Stillende sollten keinen Alkohol trinken.",
} as const;

/** Bänder für das Wochen-Diagramm, aufsteigend. */
export const WEEKLY_ALCOHOL_BANDS: readonly AlcoholBand[] = [
  { band: "LOW_RISK", fromGrams: 0.0, toGrams: HealthReferences.WEEKLY_ALCOHOL_MODERATE_FROM_GRAMS },
  {
    band: "MODERATE_RISK",
    fromGrams: HealthReferences.WEEKLY_ALCOHOL_MODERATE_FROM_GRAMS,
    toGrams: HealthReferences.WEEKLY_ALCOHOL_HIGH_ABOVE_GRAMS,
  },
  { band: "HIGH_RISK", fromGrams: HealthReferences.WEEKLY_ALCOHOL_HIGH_ABOVE_GRAMS, toGrams: null },
];

/** Bezugslinien für das Zucker-Diagramm: 50 g durchgezogen, 25 g gestrichelt. */
export const DAILY_SUGAR_LINES: readonly SugarReferenceLine[] = [
  { grams: HealthReferences.DAILY_SUGAR_LIMIT_GRAMS, label: HealthReferences.DAILY_SUGAR_LIMIT_LABEL, style: "SOLID" },
  { grams: HealthReferences.DAILY_SUGAR_WHO_GRAMS, label: HealthReferences.DAILY_SUGAR_WHO_LABEL, style: "DASHED" },
];

/** Unter 27 g „risikoarm“, 27–81 g „moderates Risiko“, über 81 g „riskant“ (Gramm Alkohol pro Woche Mo–So). */
export function weeklyAlcoholBand(grams: number): AlcoholRiskBand {
  if (grams < HealthReferences.WEEKLY_ALCOHOL_MODERATE_FROM_GRAMS) return "LOW_RISK";
  if (grams <= HealthReferences.WEEKLY_ALCOHOL_HIGH_ABOVE_GRAMS) return "MODERATE_RISK";
  return "HIGH_RISK";
}
