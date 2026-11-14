// Vergleich mit Ausgaben, die der Kotlin-Kern selbst erzeugt hat (kotlin-golden.json, erzeugt mit
// web/tools/kotlin-golden/export.sh): viele feste, pseudozufällige Eingaben → exakt dieselben Ausgaben.
// Zahlen werden exakt verglichen (gleiche IEEE-754-Rechnung in gleicher Reihenfolge).
import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import { CATALOG_ITEMS, CatalogIds, defaultSelection } from "../bar/defaultBarCatalog";
import { drinkCategoryFromName } from "../bar/drinkCategory";
import { drinkIconFromKey } from "../bar/drinkIcon";
import type { DrinkEntry } from "../log/drinkEntry";
import { parseInviteToken } from "../social/inviteLinks";
import { genderOf, withIndefiniteArticle } from "../speech/germanArticles";
import { cardinal, decimal, ordinalDative } from "../speech/germanNumberWords";
import { grams, speakable, SpeechTexts, volume } from "../speech/speechTexts";
import { StatisticsCalculator, weeklyBand } from "../stats/statisticsCalculator";
import { BERLIN, MutableClock } from "../testing/fixtures";
import { LocalDate } from "../time/localDate";
import { normalizeGerman } from "../voice/germanTextNormalizer";
import { koelnerPhonetik } from "../voice/koelnerPhonetik";
import { jaroWinkler, PhoneticTextSimilarity } from "../voice/textSimilarity";
import { defaultVoiceMatcher, type VoiceMatch } from "../voice/voiceCommandMatcher";

type Num = number | null;

interface Golden {
  numbers: { cardinal: [number, string][]; decimal: [number, number, string][]; ordinal: [number, string][] };
  speech: {
    grams: [number, string][];
    volume: [number, string][];
    today: { input: number[]; text: string }[];
    weekly: { weekStart: string; grams: number; alcoholFreeDays: number; today: string; text: string }[];
    comparison: { sugar: Num; alcohol: Num; text: string }[];
    drinkEvent: { author: string; drink: string; volume: Num; text: string }[];
    speakable: [string, string][];
  };
  articles: [string, string, string][];
  phonetics: { normalize: [string, string][]; koelner: [string, string][]; similarity: [string, string, number, number][] };
  voice: { bar: string; candidate: string; result: string }[];
  statistics: {
    now: number;
    entries: { id: string; name: string; icon: string; category: string; volume: number; alcohol: number; sugar: number; at: number }[];
    dailySugar: [string, number][];
    weeklyAlcohol: [string, number, number, string][];
    today: number[];
    month: Num[];
    average: Num[];
    streak: number;
  }[];
  invites: [string, string | null][];
}

const here = path.dirname(fileURLToPath(import.meta.url));
const golden = JSON.parse(readFileSync(path.join(here, "kotlin-golden.json"), "utf8")) as Golden;

/** Erste Abweichung als lesbare Meldung statt tausender Einzel-Asserts. */
function mismatches<T>(cases: readonly T[], check: (item: T) => string | null): string[] {
  const failures: string[] = [];
  for (const item of cases) {
    const failure = check(item);
    if (failure) failures.push(failure);
    if (failures.length >= 5) break;
  }
  return failures;
}

const same = (label: string, actual: unknown, expected: unknown): string | null =>
  Object.is(actual, expected) || actual === expected ? null : `${label}: TS=${JSON.stringify(actual)} Kotlin=${JSON.stringify(expected)}`;

describe("Ausgaben des Kotlin-Kerns (kotlin-golden.json)", () => {
  it("Zahlwörter", () => {
    expect(mismatches(golden.numbers.cardinal, ([n, words]) => same(`cardinal(${n})`, cardinal(n), words))).toEqual([]);
    expect(mismatches(golden.numbers.decimal, ([value, digits, words]) => same(`decimal(${value}, ${digits})`, decimal(value, digits), words))).toEqual([]);
    expect(mismatches(golden.numbers.ordinal, ([n, words]) => same(`ordinal(${n})`, ordinalDative(n), words))).toEqual([]);
    expect(golden.numbers.decimal.length).toBeGreaterThan(1000);
  });

  it("Vorlese-Sätze", () => {
    const speech = golden.speech;
    expect(mismatches(speech.grams, ([value, text]) => same(`grams(${value})`, grams(value), text))).toEqual([]);
    expect(mismatches(speech.volume, ([ml, text]) => same(`volume(${ml})`, volume(ml), text))).toEqual([]);
    expect(
      mismatches(speech.today, ({ input, text }) => {
        const [sugarGrams, alcoholGrams, sugaryDrinks, alcoholicDrinks, totalMilliliters, drinkCount] = input;
        return same(`today(${input})`, SpeechTexts.today({ sugarGrams, alcoholGrams, sugaryDrinks, alcoholicDrinks, totalMilliliters, drinkCount }), text);
      }),
    ).toEqual([]);
    expect(
      mismatches(speech.weekly, (week) =>
        same(
          `weekly(${week.weekStart}, ${week.grams})`,
          SpeechTexts.weeklyAlcohol({ weekStart: LocalDate.parse(week.weekStart), grams: week.grams, alcoholFreeDays: week.alcoholFreeDays }, LocalDate.parse(week.today)),
          week.text,
        ),
      ),
    ).toEqual([]);
    expect(
      mismatches(speech.comparison, (comparison) =>
        same(
          `month(${comparison.sugar}, ${comparison.alcohol})`,
          SpeechTexts.monthComparison({
            sugarChangePercent: comparison.sugar,
            alcoholChangePercent: comparison.alcohol,
            sugarGramsLast30Days: 0,
            sugarGramsPrevious30Days: 0,
            alcoholGramsLast30Days: 0,
            alcoholGramsPrevious30Days: 0,
          }),
          comparison.text,
        ),
      ),
    ).toEqual([]);
    expect(
      mismatches(speech.drinkEvent, (event) =>
        same(`drinkEvent(${event.author}, ${event.drink})`, SpeechTexts.drinkEvent(event.author, event.drink, event.volume), event.text),
      ),
    ).toEqual([]);
    expect(mismatches(speech.speakable, ([input, output]) => same(`speakable(${input})`, speakable(input), output))).toEqual([]);
  });

  it("Artikel", () => {
    expect(
      mismatches(golden.articles, ([name, withArticle, gender]) =>
        same(`article(${name})`, withIndefiniteArticle(name), withArticle) ?? same(`gender(${name})`, genderOf(name) ?? "null", gender),
      ),
    ).toEqual([]);
  });

  it("Normalisierung, Kölner Phonetik und Ähnlichkeit (exakte Zahlen)", () => {
    const similarity = new PhoneticTextSimilarity();
    expect(mismatches(golden.phonetics.normalize, ([input, output]) => same(`normalize(${input})`, normalizeGerman(input), output))).toEqual([]);
    expect(mismatches(golden.phonetics.koelner, ([input, output]) => same(`koelner(${input})`, koelnerPhonetik.encode(input), output))).toEqual([]);
    expect(
      mismatches(
        golden.phonetics.similarity,
        ([a, b, jw, phonetic]) =>
          same(`jaroWinkler(${a}, ${b})`, jaroWinkler.similarity(a, b), jw) ?? same(`phonetic(${a}, ${b})`, similarity.similarity(a, b), phonetic),
      ),
    ).toEqual([]);
  });

  it("Sprachabgleich gegen drei Bars", () => {
    const bars: Record<string, typeof CATALOG_ITEMS> = {
      catalog: CATALOG_ITEMS,
      default: defaultSelection(),
      small: CATALOG_ITEMS.filter((item) => [CatalogIds.WEIZENBIER, CatalogIds.WASSER, CatalogIds.ROTWEIN].includes(item.id as never)),
    };
    const describe = (match: VoiceMatch): string => {
      switch (match.kind) {
        case "Drink":
          return `Drink:${match.item.id}`;
        case "Ambiguous":
          return `Ambiguous:${match.first.id}|${match.second.id}`;
        case "Command":
          return `Command:${match.cmd}`;
        case "NoMatch":
          return "NoMatch";
      }
    };
    expect(golden.voice.length).toBeGreaterThan(1000);
    expect(
      mismatches(golden.voice, ({ bar, candidate, result }) =>
        same(`${bar}: „${candidate}“`, describe(defaultVoiceMatcher.match([candidate], bars[bar])), result),
      ),
    ).toEqual([]);
  });

  it("Statistik über zufällige Verläufe (Berlin, auch über Zeitumstellungen)", () => {
    for (const [index, scenario] of golden.statistics.entries()) {
      const entries: DrinkEntry[] = scenario.entries.map((entry) => ({
        id: entry.id,
        barItemId: null,
        name: entry.name,
        icon: drinkIconFromKey(entry.icon),
        category: drinkCategoryFromName(entry.category),
        volumeMilliliters: entry.volume,
        alcoholPercent: entry.alcohol,
        sugarGramsPer100Ml: entry.sugar,
        consumedAt: entry.at,
        shareWithFriends: false,
      }));
      const stats = new StatisticsCalculator(new MutableClock(scenario.now), BERLIN);
      const label = `Szenario ${index}`;
      expect(stats.dailySugar(entries).map((day) => [day.date.toString(), day.grams]), label).toEqual(scenario.dailySugar);
      expect(
        stats.weeklyAlcohol(entries).map((week) => [week.weekStart.toString(), week.grams, week.alcoholFreeDays, weeklyBand(week)]),
        label,
      ).toEqual(scenario.weeklyAlcohol);
      const today = stats.today(entries);
      expect(
        [today.sugarGrams, today.alcoholGrams, today.sugaryDrinks, today.alcoholicDrinks, today.totalMilliliters, today.drinkCount],
        label,
      ).toEqual(scenario.today);
      const month = stats.monthComparison(entries);
      expect(
        [
          month.sugarChangePercent,
          month.alcoholChangePercent,
          month.sugarGramsLast30Days,
          month.sugarGramsPrevious30Days,
          month.alcoholGramsLast30Days,
          month.alcoholGramsPrevious30Days,
        ],
        label,
      ).toEqual(scenario.month);
      const average = stats.movingAverage4Weeks(entries);
      expect([average.alcoholGramsPerWeek, average.sugarGramsPerDay, average.coveredDays], label).toEqual(scenario.average);
      expect(stats.alcoholFreeStreak(entries), label).toBe(scenario.streak);
    }
    expect(golden.statistics).toHaveLength(40);
  });

  it("Einladungs-Links", () => {
    expect(mismatches(golden.invites, ([input, token]) => same(`parseToken(${JSON.stringify(input)})`, parseInviteToken(input), token))).toEqual([]);
  });
});
