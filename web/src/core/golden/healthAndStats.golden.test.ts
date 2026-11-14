// Golden-Tests: dieselben Eingaben und Erwartungen wie in den Kotlin-Tests HealthTest und StatisticsCalculatorTest.
import { describe, expect, it } from "vitest";
import { CatalogIds } from "../bar/defaultBarCatalog";
import {
  alcoholGrams,
  entryAlcoholGrams,
  entrySugarGrams,
  isAlcoholic,
  isSugary,
  isVerySugary,
  servingAlcoholGrams,
  servingSugarGrams,
  sugarGrams,
  sugarLevel,
} from "../health/healthMath";
import {
  ALCOHOL_RISK_BAND_LABELS,
  DAILY_SUGAR_LINES,
  HealthReferences,
  WEEKLY_ALCOHOL_BANDS,
  weeklyAlcoholBand,
} from "../health/healthReferences";
import { StatisticsCalculator, weeklyBand } from "../stats/statisticsCalculator";
import { BERLIN, berlin, catalogItem, DAY, entry, entryOf, HOUR, instant, MutableClock } from "../testing/fixtures";
import { LocalDate } from "../time/localDate";

const DELTA = 1e-9;
function near(actual: number | null | undefined, expected: number, delta = DELTA): void {
  expect(actual).not.toBeNull();
  expect(actual).not.toBeUndefined();
  expect(Math.abs((actual as number) - expected), `${actual} ≈ ${expected}`).toBeLessThanOrEqual(delta);
}

describe("Kotlin-Kern: HealthTest", () => {
  it("alcoholGramsFormula", () => {
    near(alcoholGrams(500, 4.9), 19.6);
    near(alcoholGrams(500, 0.4), 1.6);
    near(alcoholGrams(125, 12.0), 12.0);
    near(alcoholGrams(20, 40.0), 6.4);
    near(alcoholGrams(250, 0.0), 0.0);
  });

  it("sugarGramsFormula", () => {
    near(sugarGrams(330, 10.6), 34.98);
    near(sugarGrams(500, 5.5), 27.5);
    near(sugarGrams(250, 0.0), 0.0);
  });

  it("alcoholicFrom1Point2Percent", () => {
    expect(isAlcoholic(1.2)).toBe(true);
    expect(isAlcoholic(4.9)).toBe(true);
    expect(isAlcoholic(1.19)).toBe(false);
    expect(isAlcoholic(0.4)).toBe(false);
    expect(isAlcoholic(0.0)).toBe(false);
  });

  it("sugarLevels", () => {
    expect(isSugary(4.99)).toBe(false);
    expect(isSugary(5.0)).toBe(true);
    expect(isVerySugary(7.99)).toBe(false);
    expect(isVerySugary(8.0)).toBe(true);
    expect(sugarLevel(4.8)).toBe("LOW");
    expect(sugarLevel(5.5)).toBe("SUGARY");
    expect(sugarLevel(10.6)).toBe("VERY_SUGARY");
  });

  it("catalogClassification", () => {
    const pils = catalogItem(CatalogIds.PILS);
    const alcoholFree = catalogItem(CatalogIds.ALKOHOLFREIES_BIER);
    const milk = catalogItem(CatalogIds.MILCH);
    const cola = catalogItem(CatalogIds.COLA);
    const schorle = catalogItem(CatalogIds.APFELSCHORLE);
    expect(isAlcoholic(pils.alcoholPercent)).toBe(true);
    expect(isAlcoholic(alcoholFree.alcoholPercent)).toBe(false);
    near(servingAlcoholGrams(alcoholFree), 1.6);
    expect(isSugary(milk.sugarGramsPer100Ml)).toBe(false);
    expect(isVerySugary(cola.sugarGramsPer100Ml)).toBe(true);
    expect(sugarLevel(schorle.sugarGramsPer100Ml)).toBe("SUGARY");
    expect(isSugary(schorle.sugarGramsPer100Ml)).toBe(true);
    expect(isVerySugary(schorle.sugarGramsPer100Ml)).toBe(false);
    near(servingSugarGrams(cola), 34.98);
  });

  it("entryExtensionsUseTheLoggedVolume", () => {
    const logged = entryOf(catalogItem(CatalogIds.PILS), 0, { volume: 330 });
    near(entryAlcoholGrams(logged), 12.936);
    near(entrySugarGrams(logged), 0.99);
    expect(isAlcoholic(logged.alcoholPercent)).toBe(true);
    expect(isSugary(logged.sugarGramsPer100Ml)).toBe(false);
    expect(sugarLevel(logged.sugarGramsPer100Ml)).toBe("LOW");
  });

  it("weeklyAlcoholBandsFollowDge2024", () => {
    expect(weeklyAlcoholBand(0.0)).toBe("LOW_RISK");
    expect(weeklyAlcoholBand(26.99)).toBe("LOW_RISK");
    expect(weeklyAlcoholBand(27.0)).toBe("MODERATE_RISK");
    expect(weeklyAlcoholBand(81.0)).toBe("MODERATE_RISK");
    expect(weeklyAlcoholBand(81.01)).toBe("HIGH_RISK");
    expect(Object.values(ALCOHOL_RISK_BAND_LABELS)).toEqual(["risikoarm", "moderates Risiko", "riskant"]);
    expect(WEEKLY_ALCOHOL_BANDS).toEqual([
      { band: "LOW_RISK", fromGrams: 0.0, toGrams: 27.0 },
      { band: "MODERATE_RISK", fromGrams: 27.0, toGrams: 81.0 },
      { band: "HIGH_RISK", fromGrams: 81.0, toGrams: null },
    ]);
  });

  it("referenceTextsAreExact", () => {
    expect(HealthReferences.ALCOHOL_MANDATORY_NOTE).toBe("Es gibt keine unbedenkliche Menge Alkohol. Weniger ist besser.");
    expect(HealthReferences.SUGAR_NOTE).toBe("Getränke sind nur ein Teil davon.");
    expect(HealthReferences.INFO_TEXT).toBe(
      "Diese App ist ein persönliches Getränketagebuch. Sie stellt keine Diagnose und ersetzt keine ärztliche " +
        "Beratung. Kinder, Jugendliche, Schwangere und Stillende sollten keinen Alkohol trinken.",
    );
    expect(DAILY_SUGAR_LINES).toEqual([
      { grams: 50.0, label: "Obergrenze für freien Zucker in der gesamten Ernährung (bei 2000 kcal)", style: "SOLID" },
      { grams: 25.0, label: "WHO: unter 25 g bringt zusätzlichen Nutzen", style: "DASHED" },
    ]);
    expect(HealthReferences.ALCOHOL_SOURCE).toBe("DGE 2024");
  });

  it("noPermissionLanguageInReferenceTexts", () => {
    const texts = [
      HealthReferences.ALCOHOL_MANDATORY_NOTE,
      HealthReferences.SUGAR_NOTE,
      HealthReferences.INFO_TEXT,
      HealthReferences.DAILY_SUGAR_LIMIT_LABEL,
      HealthReferences.DAILY_SUGAR_WHO_LABEL,
      HealthReferences.ADULTS_ONLY_NOTE,
      ...Object.values(ALCOHOL_RISK_BAND_LABELS),
    ];
    for (const text of texts) {
      expect(text.includes("übrig"), text).toBe(false);
      expect(text.includes("gesund ") && text.includes("Alkohol"), text).toBe(false);
    }
  });
});

describe("Kotlin-Kern: StatisticsCalculatorTest", () => {
  const pils = catalogItem(CatalogIds.PILS);
  const alcoholFreeBeer = catalogItem(CatalogIds.ALKOHOLFREIES_BIER);
  const cola = catalogItem(CatalogIds.COLA);
  const water = catalogItem(CatalogIds.WASSER);
  const wine = catalogItem(CatalogIds.ROTWEIN);
  const calculator = (now: number) => new StatisticsCalculator(new MutableClock(now), BERLIN);
  const sugar = (grams: number, at: number) => entry(at, { volume: 100, sugarPer100Ml: grams });
  const alcohol = (at: number) => entry(at, { volume: 500, alcoholPercent: 4.9 });
  const date = (iso: string) => LocalDate.parse(iso);

  it("dailySugarCoversFourteenDaysUpToToday", () => {
    const result = calculator(berlin("2026-11-10T12:00")).dailySugar([
      entryOf(cola, berlin("2026-11-10T09:00")),
      entryOf(cola, berlin("2026-11-10T11:00")),
      sugar(27.5, berlin("2026-11-09T20:00")),
      sugar(99.0, berlin("2026-10-27T12:00")),
    ]);
    expect(result).toHaveLength(14);
    expect(result[0].date.toString()).toBe("2026-10-28");
    expect(result[13].date.toString()).toBe("2026-11-10");
    near(result[13].grams, 69.96);
    near(result[12].grams, 27.5);
    expect(result.slice(0, -2).every((it) => it.grams === 0.0)).toBe(true);
  });

  it("dailySugarUsesLocalCalendarDaysAcrossTheAutumnClockChange", () => {
    const result = calculator(berlin("2026-10-26T12:00")).dailySugar(
      [
        sugar(1.0, instant("2026-10-24T22:30:00Z")),
        sugar(2.0, instant("2026-10-25T00:30:00Z")),
        sugar(4.0, instant("2026-10-25T01:30:00Z")),
        sugar(8.0, instant("2026-10-25T22:30:00Z")),
        sugar(16.0, instant("2026-10-25T23:15:00Z")),
      ],
      2,
    );
    expect(result.map((it) => it.date.toString())).toEqual(["2026-10-25", "2026-10-26"]);
    expect(result.map((it) => it.grams)).toEqual([15.0, 16.0]);
  });

  it("dailySugarNeedsAtLeastOneDay", () => {
    expect(() => calculator(berlin("2026-11-10T12:00")).dailySugar([], 0)).toThrow(RangeError);
  });

  it("weeksStartOnMondayAndEndWithTheCurrentWeek", () => {
    const result = calculator(berlin("2026-11-10T12:00")).weeklyAlcohol([]);
    expect(result).toHaveLength(8);
    expect(result.every((it) => it.weekStart.dayOfWeek === 1)).toBe(true);
    expect(result[7].weekStart.toString()).toBe("2026-11-09");
    expect(result[0].weekStart.toString()).toBe("2026-09-21");
  });

  it("mondayItselfStartsTheCurrentWeek", () => {
    const result = calculator(berlin("2026-11-09T00:05")).weeklyAlcohol([], 1);
    expect(result.map((it) => it.weekStart.toString())).toEqual(["2026-11-09"]);
  });

  it("weekBoundaryAfterTheAutumnClockChange", () => {
    const result = calculator(berlin("2026-10-28T12:00")).weeklyAlcohol(
      [
        entryOf(pils, instant("2026-10-25T22:30:00Z")),
        entryOf(pils, instant("2026-10-25T23:30:00Z")),
        entryOf(pils, instant("2026-10-26T10:00:00Z")),
      ],
      2,
    );
    expect(result.map((it) => it.weekStart.toString())).toEqual(["2026-10-19", "2026-10-26"]);
    near(result[0].grams, 19.6);
    near(result[1].grams, 39.2);
  });

  it("weekBoundaryAfterTheSpringClockChange", () => {
    const result = calculator(berlin("2026-04-01T12:00")).weeklyAlcohol(
      [entryOf(pils, instant("2026-03-29T21:30:00Z")), entryOf(pils, instant("2026-03-29T22:30:00Z"))],
      2,
    );
    expect(result.map((it) => it.weekStart.toString())).toEqual(["2026-03-23", "2026-03-30"]);
    expect(result.map((it) => it.grams)).toEqual([19.6, 19.6]);
  });

  it("alcoholFreeDaysIgnoreTheGramsOfAlcoholFreeBeer", () => {
    const result = calculator(berlin("2026-11-11T20:00")).weeklyAlcohol(
      [
        entryOf(pils, berlin("2026-11-09T19:00")),
        entryOf(alcoholFreeBeer, berlin("2026-11-10T19:00")),
        entryOf(water, berlin("2026-11-11T08:00")),
      ],
      2,
    );
    const thisWeek = result[1];
    near(thisWeek.grams, 21.2);
    expect(thisWeek.alcoholFreeDays).toBe(2);
    expect(weeklyBand(thisWeek)).toBe("LOW_RISK");
    expect(result[0].grams).toBe(0.0);
    expect(result[0].alcoholFreeDays).toBe(0);
  });

  it("fullyTrackedWeekWithoutAlcoholHasSevenAlcoholFreeDays", () => {
    const result = calculator(berlin("2026-11-10T12:00")).weeklyAlcohol(
      [entryOf(water, berlin("2026-10-01T12:00")), entryOf(alcoholFreeBeer, berlin("2026-11-05T20:00"))],
      2,
    );
    expect(result[0].alcoholFreeDays).toBe(7);
    near(result[0].grams, 1.6);
    expect(result[1].alcoholFreeDays).toBe(2);
  });

  it("weeklyBandUsesTheReferenceValues", () => {
    const result = calculator(berlin("2026-11-13T12:00")).weeklyAlcohol(
      Array.from({ length: 5 }, (_, index) => entryOf(pils, berlin("2026-11-09T18:00") + index * HOUR)),
      1,
    );
    near(result[0].grams, 98.0);
    expect(weeklyBand(result[0])).toBe("HIGH_RISK");
  });

  it("todaySummarizesOnlyTodaysEntries", () => {
    const summary = calculator(berlin("2026-11-10T18:00")).today([
      entryOf(cola, berlin("2026-11-10T10:00")),
      entryOf(pils, berlin("2026-11-10T17:00")),
      entryOf(alcoholFreeBeer, berlin("2026-11-10T17:30")),
      entryOf(water, berlin("2026-11-10T00:30")),
      entryOf(wine, berlin("2026-11-09T23:59")),
    ]);
    near(summary.sugarGrams, 48.98);
    near(summary.alcoholGrams, 21.2);
    expect(summary.sugaryDrinks).toBe(1);
    expect(summary.alcoholicDrinks).toBe(1);
    expect(summary.totalMilliliters).toBe(1580);
    expect(summary.drinkCount).toBe(4);
  });

  it("todayIsEmptyWithoutEntries", () => {
    expect(calculator(berlin("2026-11-10T18:00")).today([])).toEqual({
      sugarGrams: 0.0,
      alcoholGrams: 0.0,
      sugaryDrinks: 0,
      alcoholicDrinks: 0,
      totalMilliliters: 0,
      drinkCount: 0,
    });
  });

  const comparisonNow = berlin("2026-11-10T12:00");

  it("monthComparisonInPercent", () => {
    const result = calculator(comparisonNow).monthComparison([
      entryOf(water, berlin("2026-09-12T08:00")),
      sugar(60.0, berlin("2026-09-20T12:00")),
      sugar(40.0, berlin("2026-10-11T23:00")),
      alcohol(berlin("2026-10-01T20:00")),
      sugar(50.0, berlin("2026-10-12T00:30")),
      alcohol(berlin("2026-11-01T20:00")),
      alcohol(berlin("2026-11-10T11:00")),
    ]);
    near(result.sugarChangePercent, -50.0);
    near(result.alcoholChangePercent, 100.0);
    near(result.sugarGramsLast30Days, 50.0);
    near(result.sugarGramsPrevious30Days, 100.0);
    near(result.alcoholGramsLast30Days, 39.2);
    near(result.alcoholGramsPrevious30Days, 19.6);
  });

  it("monthComparisonNeedsAFullyTrackedPreviousPeriod", () => {
    const result = calculator(comparisonNow).monthComparison([
      sugar(60.0, berlin("2026-09-13T12:00")),
      sugar(30.0, berlin("2026-11-01T12:00")),
    ]);
    expect(result.sugarChangePercent).toBeNull();
    expect(result.alcoholChangePercent).toBeNull();
    near(result.sugarGramsLast30Days, 30.0);
  });

  it("monthComparisonWithoutPreviousAlcoholHasNoAlcoholPercent", () => {
    const result = calculator(comparisonNow).monthComparison([
      sugar(10.0, berlin("2026-09-01T12:00")),
      sugar(5.0, berlin("2026-09-30T12:00")),
      alcohol(berlin("2026-11-01T20:00")),
    ]);
    expect(result.alcoholChangePercent).toBeNull();
    near(result.sugarChangePercent, -100.0);
    near(result.alcoholGramsLast30Days, 19.6);
  });

  it("monthComparisonWithoutEntries", () => {
    expect(calculator(comparisonNow).monthComparison([])).toEqual({
      sugarChangePercent: null,
      alcoholChangePercent: null,
      sugarGramsLast30Days: 0.0,
      sugarGramsPrevious30Days: 0.0,
      alcoholGramsLast30Days: 0.0,
      alcoholGramsPrevious30Days: 0.0,
    });
  });

  it("monthComparisonUsesCalendarDaysAcrossClockChanges", () => {
    const result = calculator(berlin("2026-10-25T12:00")).monthComparison([
      sugar(10.0, berlin("2026-08-27T00:10")),
      sugar(5.0, berlin("2026-09-25T23:50")),
      sugar(30.0, berlin("2026-09-26T00:10")),
    ]);
    near(result.sugarChangePercent, 100.0);
  });

  it("movingAverageOverTheLast28Days", () => {
    const entries = [
      entryOf(water, berlin("2026-10-01T12:00")),
      ...Array.from({ length: 4 }, (_, index) => entryOf(pils, berlin("2026-10-14T20:00") + index * 7 * DAY)),
      sugar(274.0, berlin("2026-11-10T09:00")),
      sugar(99.0, berlin("2026-10-13T20:00")),
    ];
    const result = calculator(berlin("2026-11-10T12:00")).movingAverage4Weeks(entries);
    expect(result.coveredDays).toBe(28);
    near(result.alcoholGramsPerWeek, 19.6);
    near(result.sugarGramsPerDay, 10.0);
  });

  it("movingAverageNeedsSevenTrackedDays", () => {
    const stats = calculator(berlin("2026-11-10T12:00"));
    expect(stats.movingAverage4Weeks([entryOf(pils, berlin("2026-11-05T20:00"))])).toEqual({
      alcoholGramsPerWeek: null,
      sugarGramsPerDay: null,
      coveredDays: 6,
    });
    const sevenDays = stats.movingAverage4Weeks([entryOf(pils, berlin("2026-11-04T20:00"))]);
    expect(sevenDays.coveredDays).toBe(7);
    near(sevenDays.alcoholGramsPerWeek, 19.6);
    near(sevenDays.sugarGramsPerDay, 1.5 / 7);
    expect(stats.movingAverage4Weeks([])).toEqual({ alcoholGramsPerWeek: null, sugarGramsPerDay: null, coveredDays: 0 });
  });

  it("alcoholFreeStreakCountsDaysUpToToday", () => {
    const stats = calculator(berlin("2026-11-10T20:00"));
    expect(
      stats.alcoholFreeStreak([
        entryOf(pils, berlin("2026-11-01T20:00")),
        entryOf(pils, berlin("2026-11-06T20:00")),
        entryOf(alcoholFreeBeer, berlin("2026-11-08T20:00")),
        entryOf(water, berlin("2026-11-10T08:00")),
      ]),
    ).toBe(4);
  });

  it("alcoholFreeStreakIsZeroAfterAlcoholToday", () => {
    const stats = calculator(berlin("2026-11-10T20:00"));
    expect(stats.alcoholFreeStreak([entryOf(water, berlin("2026-11-01T08:00")), entryOf(wine, berlin("2026-11-10T19:00"))])).toBe(0);
    expect(stats.alcoholFreeStreak([])).toBe(0);
  });

  it("alcoholFreeStreakStartsWithTheFirstEntry", () => {
    expect(calculator(berlin("2026-11-10T20:00")).alcoholFreeStreak([entryOf(alcoholFreeBeer, berlin("2026-11-08T20:00"))])).toBe(3);
  });

  it("dates in the weekly result are LocalDates", () => {
    expect(calculator(berlin("2026-11-10T12:00")).weeklyAlcohol([], 1)[0].weekStart.equals(date("2026-11-09"))).toBe(true);
  });
});
