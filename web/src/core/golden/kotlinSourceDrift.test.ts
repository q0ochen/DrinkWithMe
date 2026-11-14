// Drift-Erkennung: liest die Tabellen und Konstanten direkt aus dem Kotlin-Kern (android/core) und vergleicht
// sie mit dem TypeScript-Port. Ändert jemand z. B. einen Katalogwert, ein Füllwort oder einen Pflichtsatz nur in
// Kotlin, schlägt dieser Test fehl. Ohne android/core (z. B. bei einem reinen Web-Checkout) wird er übersprungen.
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import { CATALOG_ENTRIES, CatalogIds, DEFAULT_SELECTION_IDS } from "../bar/defaultBarCatalog";
import { categoryDisplayName, DRINK_CATEGORIES } from "../bar/drinkCategory";
import { DRINK_ICON_KEYS } from "../bar/drinkIcon";
import {
  ALCOHOLIC_MIN_PERCENT,
  ETHANOL_GRAMS_PER_MILLILITER,
  SUGARY_MIN_GRAMS_PER_100_ML,
  VERY_SUGARY_MIN_GRAMS_PER_100_ML,
} from "../health/healthMath";
import { ALCOHOL_RISK_BAND_LABELS, HealthReferences } from "../health/healthReferences";
import { UNDO_WINDOW_MILLIS } from "../log/logUseCases";
import { SharingTexts } from "../sharing/sharingPolicy";
import { ARTICLE_CONTAINERS, ARTICLE_DETERMINERS, FEMININE_ENDINGS, MASCULINE_ENDINGS, NEUTER_ENDINGS } from "../speech/germanArticles";
import { StatisticsDefaults } from "../stats/statisticsCalculator";
import { COMPOUND_PART_SCORE, MIN_COMPOUND_PART_LENGTH, MINIMUM_GAP, MINIMUM_SIMILARITY } from "../voice/voiceCommandMatcher";
import { FILLER_WORDS, NUMBER_WORDS } from "../voice/voiceVocabulary";

const here = path.dirname(fileURLToPath(import.meta.url));
const KOTLIN_ROOT = path.resolve(here, "../../../../android/core/src/main/kotlin/com/drinkwithme/core");
const available = existsSync(path.join(KOTLIN_ROOT, "bar/DrinkIcon.kt"));

function source(relative: string): string {
  return readFileSync(path.join(KOTLIN_ROOT, relative), "utf8");
}

/** Alle String-Literale eines Ausschnitts in Reihenfolge. */
function stringLiterals(text: string): string[] {
  return [...text.matchAll(/"((?:[^"\\]|\\.)*)"/g)].map((match) => match[1]);
}

/** Ausschnitt zwischen `start` und der zugehörigen schließenden Klammer. */
function block(text: string, start: string): string {
  const begin = text.indexOf(start);
  if (begin < 0) throw new Error(`Nicht gefunden: ${start}`);
  let depth = 0;
  for (let index = text.indexOf("(", begin); index < text.length; index++) {
    if (text[index] === "(") depth++;
    if (text[index] === ")") {
      depth--;
      if (depth === 0) return text.slice(begin, index + 1);
    }
  }
  throw new Error(`Keine schließende Klammer für ${start}`);
}

/** `const val NAME = …` als Zahl oder (ggf. mit `+` verketteter) Text. */
function constants(text: string): Map<string, string | number> {
  const result = new Map<string, string | number>();
  for (const match of text.matchAll(/const val (\w+)\s*=\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+|[\d.]+)/g)) {
    const value = match[2].trim();
    result.set(match[1], value.startsWith('"') ? stringLiterals(value).join("") : Number(value));
  }
  return result;
}

describe.skipIf(!available)("Kotlin-Quelltext und TypeScript-Port sind gleich", () => {
  it("DrinkIcon: Schlüssel und Reihenfolge", () => {
    const keys = [...source("bar/DrinkIcon.kt").matchAll(/\b[A-Z_]+\("([a-z_]+)"\)/g)].map((match) => match[1]);
    expect(keys).toEqual([...DRINK_ICON_KEYS]);
  });

  it("DrinkCategory: Namen und deutsche Anzeigenamen", () => {
    const text = source("bar/DrinkCategory.kt");
    const names = /enum class DrinkCategory \{([^;]+);/.exec(text)?.[1].split(",").map((name) => name.trim());
    expect(names).toEqual([...DRINK_CATEGORIES]);
    const displayNames = Object.fromEntries([...text.matchAll(/DrinkCategory\.([A-Z_]+) -> "([^"]+)"/g)].map((m) => [m[1], m[2]]));
    expect(displayNames).toEqual(Object.fromEntries(DRINK_CATEGORIES.map((category) => [category, categoryDisplayName(category)])));
  });

  it("DefaultBarCatalog: IDs, Werte, Aliase, Hinweise und Vorauswahl", () => {
    const text = source("bar/DefaultBarCatalog.kt");
    const iconKeys = Object.fromEntries(
      [...source("bar/DrinkIcon.kt").matchAll(/\b([A-Z_]+)\("([a-z_]+)"\)/g)].map((match) => [match[1], match[2]]),
    );
    const ids = Object.fromEntries([...text.matchAll(/const val (\w+) = "\$\{ID_PREFIX\}([^"]+)"/g)].map((m) => [m[1], `katalog-${m[2]}`]));
    expect(ids).toEqual(CatalogIds);

    const entries = [...text.matchAll(/entry\((\w+), "([^"]+)", DrinkIcon\.(\w+), DrinkCategory\.(\w+),([^]*?)\),\n/g)].map((match) => {
      const rest = match[5];
      const number = (name: string) => Number(new RegExp(`${name} = ([\\d.]+)`).exec(rest)?.[1]);
      const aliases = /aliases = listOf\(([^)]*)\)/.exec(rest);
      return {
        id: ids[match[1]],
        name: match[2],
        icon: iconKeys[match[3]],
        category: match[4],
        sugar: number("sugar"),
        alcohol: number("alcohol"),
        serving: number("serving"),
        aliases: aliases ? stringLiterals(aliases[1]) : [],
        note: /note = "([^"]*)"/.exec(rest)?.[1] ?? null,
        isEstimate: /isEstimate = true/.test(rest),
      };
    });
    expect(entries).toEqual(
      CATALOG_ENTRIES.map((entry) => ({
        id: entry.item.id,
        name: entry.item.name,
        icon: entry.item.icon,
        category: entry.item.category,
        sugar: entry.item.sugarGramsPer100Ml,
        alcohol: entry.item.alcoholPercent,
        serving: entry.item.servingMilliliters,
        aliases: [...entry.item.aliases],
        note: entry.note,
        isEstimate: entry.isEstimate,
      })),
    );
    const selection = /defaultSelectionIds: List<String> = listOf\(([^)]*)\)/.exec(text)?.[1].split(",").map((name) => ids[name.trim()]);
    expect(selection).toEqual(DEFAULT_SELECTION_IDS);
  });

  it("HealthMath und HealthReferences: Schwellen, Bezugswerte und Pflichttexte", () => {
    const math = constants(source("health/HealthMath.kt"));
    expect(math.get("ETHANOL_GRAMS_PER_MILLILITER")).toBe(ETHANOL_GRAMS_PER_MILLILITER);
    expect(math.get("ALCOHOLIC_MIN_PERCENT")).toBe(ALCOHOLIC_MIN_PERCENT);
    expect(math.get("SUGARY_MIN_GRAMS_PER_100_ML")).toBe(SUGARY_MIN_GRAMS_PER_100_ML);
    expect(math.get("VERY_SUGARY_MIN_GRAMS_PER_100_ML")).toBe(VERY_SUGARY_MIN_GRAMS_PER_100_ML);

    const text = source("health/HealthReferences.kt");
    const references = constants(text);
    for (const [name, value] of Object.entries(HealthReferences)) expect(references.get(name), name).toBe(value);
    expect(references.size).toBe(Object.keys(HealthReferences).length);
    const labels = Object.fromEntries([...text.matchAll(/\b([A-Z_]+)\("([^"]+)"\)/g)].map((m) => [m[1], m[2]]));
    expect(labels).toEqual(ALCOHOL_RISK_BAND_LABELS);
  });

  it("SharingPolicy: Texte der einmaligen Frage", () => {
    const texts = constants(source("sharing/SharingPolicy.kt"));
    for (const [name, value] of Object.entries(SharingTexts)) expect(texts.get(name), name).toBe(value);
  });

  it("StatisticsCalculator und Rückgängig: Zeiträume", () => {
    const stats = constants(source("stats/StatisticsCalculator.kt"));
    for (const [name, value] of Object.entries(StatisticsDefaults)) expect(stats.get(name), name).toBe(value);
    const window = /DEFAULT_WINDOW: Duration = Duration\.ofSeconds\((\d+)\)/.exec(source("log/UndoLastEntryUseCase.kt"))?.[1];
    expect(Number(window) * 1000).toBe(UNDO_WINDOW_MILLIS);
  });

  it("VoiceCommandMatcher und VoiceVocabulary: Schwellen, Füll- und Zahlwörter", () => {
    const matcher = constants(source("voice/VoiceCommandMatcher.kt"));
    expect(matcher.get("MINIMUM_SIMILARITY")).toBe(MINIMUM_SIMILARITY);
    expect(matcher.get("MINIMUM_GAP")).toBe(MINIMUM_GAP);
    expect(matcher.get("COMPOUND_PART_SCORE")).toBe(COMPOUND_PART_SCORE);
    expect(matcher.get("MIN_COMPOUND_PART_LENGTH")).toBe(MIN_COMPOUND_PART_LENGTH);

    const vocabulary = source("voice/VoiceVocabulary.kt");
    expect(new Set(stringLiterals(block(vocabulary, "FILLER_WORDS: Set<String> = setOf(")))).toEqual(FILLER_WORDS);
    const extras = stringLiterals(block(vocabulary, "addAll(\n            listOf("));
    for (const word of extras) expect(NUMBER_WORDS.has(word), word).toBe(true);
    expect(NUMBER_WORDS.size).toBe(new Set([...extras, ...[...NUMBER_WORDS].filter((word) => !extras.includes(word))]).size);
  });

  it("GermanArticles: Gefäße, Wortenden und Artikelwörter", () => {
    const text = source("speech/GermanArticles.kt");
    const containers = Object.fromEntries([...text.matchAll(/"([^"]+)" to GrammaticalGender\.(\w+)/g)].map((m) => [m[1], m[2]]));
    expect(containers).toEqual(ARTICLE_CONTAINERS);
    const endings = (gender: string) => stringLiterals(block(text, `endings(\n            GrammaticalGender.${gender},`));
    expect(endings("FEMININE")).toEqual([...FEMININE_ENDINGS]);
    expect(endings("MASCULINE")).toEqual([...MASCULINE_ENDINGS]);
    expect(endings("NEUTER")).toEqual([...NEUTER_ENDINGS]);
    expect(new Set(stringLiterals(block(text, "DETERMINERS = setOf(")))).toEqual(ARTICLE_DETERMINERS);
  });
});
