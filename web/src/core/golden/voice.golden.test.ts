// Golden-Tests: dieselben Eingaben und Erwartungen wie in den Kotlin-Tests PhoneticAndSimilarityTest und
// VoiceCommandMatcherTest.
import { describe, expect, it } from "vitest";
import { CATALOG_ITEMS, CatalogIds } from "../bar/defaultBarCatalog";
import { barItem, catalogItem } from "../testing/fixtures";
import { germanTokens, normalizeGerman } from "../voice/germanTextNormalizer";
import { koelnerPhonetik } from "../voice/koelnerPhonetik";
import { jaro, jaroWinkler, PhoneticTextSimilarity } from "../voice/textSimilarity";
import {
  defaultVoiceMatcher,
  MINIMUM_GAP,
  MINIMUM_SIMILARITY,
  VoiceCommandMatcher,
  type VoiceMatch,
} from "../voice/voiceCommandMatcher";
import { meaningfulTokens, NUMBER_WORDS } from "../voice/voiceVocabulary";

const near3 = (actual: number, expected: number) => expect(Math.abs(actual - expected)).toBeLessThanOrEqual(1e-3);

describe("Kotlin-Kern: PhoneticAndSimilarityTest", () => {
  const encode = (text: string) => koelnerPhonetik.encode(text);

  it("koelnerPhonetikKnownExamples", () => {
    expect(encode("Müller-Lüdenscheidt")).toBe("65752682");
    expect(encode("Wikipedia")).toBe("3412");
    expect(encode("Breschnew")).toBe("17863");
    expect(encode("Heinz")).toBe("068");
  });

  it("koelnerPhonetikContextRules", () => {
    expect(encode("Cäsar")).toBe("487");
    expect(encode("Celle")).toBe("85");
    expect(encode("Fuchs")).toBe("348");
    expect(encode("Xaver")).toBe("4837");
    expect(encode("Hexe")).toBe("048");
    expect(encode("Matsch")).toBe("68");
    expect(encode("Stadt")).toBe("822");
    expect(encode("Phi")).toBe("3");
    expect(encode("h-!")).toBe("");
  });

  it("drinksThatSoundAlikeShareACode", () => {
    expect(encode("Pils")).toBe("158");
    expect(encode("Pilz")).toBe("158");
    expect(encode("Bils")).toBe("158");
    expect(encode("Cola")).toBe(encode("Kola"));
    expect(encode("Weißbier")).toBe(encode("weissbier"));
    expect(encode("Apfelschorle")).not.toBe(encode("Apfelsaftschorle"));
  });

  it("jaroWinklerKnownValues", () => {
    near3(jaro("MARTHA", "MARHTA"), 0.944);
    near3(jaroWinkler.similarity("MARTHA", "MARHTA"), 0.961);
    near3(jaro("DWAYNE", "DUANE"), 0.822);
    near3(jaroWinkler.similarity("DWAYNE", "DUANE"), 0.84);
    near3(jaro("DIXON", "DICKSONX"), 0.767);
    near3(jaroWinkler.similarity("DIXON", "DICKSONX"), 0.813);
  });

  it("jaroWinklerEdgeCases", () => {
    expect(jaroWinkler.similarity("", "")).toBe(1.0);
    expect(jaroWinkler.similarity("", "bier")).toBe(0.0);
    expect(jaroWinkler.similarity("abc", "xyz")).toBe(0.0);
    expect(jaroWinkler.similarity("pils", "pils")).toBe(1.0);
    expect(Math.abs(jaroWinkler.similarity("apfelschorle", "apfelsaft") - jaroWinkler.similarity("apfelsaft", "apfelschorle"))).toBeLessThanOrEqual(1e-12);
  });

  it("drinkNameSimilarities", () => {
    near3(jaroWinkler.similarity("apfelsaftschorle", "apfelschorle"), 0.95);
    near3(jaroWinkler.similarity("apfelsaftschorle", "apfelsaft"), 0.913);
    expect(jaroWinkler.similarity("apfelschorle", "apfelsaft")).toBeLessThan(0.88);
  });

  it("phoneticSimilarityRaisesSoundAlikesButNeverLowers", () => {
    const similarity = new PhoneticTextSimilarity();
    near3(jaroWinkler.similarity("bils", "pils"), 0.833);
    near3(similarity.similarity("bils", "pils"), 0.917);
    for (const [a, b] of [
      ["wasser", "mineralwasser"],
      ["tee", "eistee"],
      ["cola", "colazero"],
    ]) {
      expect(similarity.similarity(a, b)).toBeGreaterThanOrEqual(jaroWinkler.similarity(a, b));
    }
    expect(similarity.similarity("123", "124")).toBe(jaroWinkler.similarity("123", "124"));
  });

  it("normalizerHandlesUmlautsSharpSAccentsAndPunctuation", () => {
    expect(normalizeGerman("Weißbier")).toBe("weissbier");
    expect(normalizeGerman("Äpfel Öl Über")).toBe("aepfel oel ueber");
    expect(normalizeGerman("Äpfel")).toBe("aepfel");
    expect(normalizeGerman("Café Crème")).toBe("cafe creme");
    expect(normalizeGerman("  Coca-Cola, bitte! ")).toBe("coca cola bitte");
    expect(germanTokens("O-Saft 0,5")).toEqual(["o", "saft", "0", "5"]);
    expect(germanTokens(" ... ")).toEqual([]);
  });

  it("vocabularyRemovesFillerAndNumberWords", () => {
    expect(meaningfulTokens("Ich trinke ein Bier, bitte")).toEqual(["bier"]);
    expect(meaningfulTokens("zwei Gläser Wasser")).toEqual(["wasser"]);
    expect(meaningfulTokens("ein großes Glas Milch")).toEqual(["milch"]);
    expect(meaningfulTokens("Ich hatte eine Tasse Kaffee")).toEqual(["kaffee"]);
    expect(meaningfulTokens("0,5 Liter Cola")).toEqual(["cola"]);
    expect(meaningfulTokens("einen doppelten Espresso")).toEqual(["espresso"]);
    expect(NUMBER_WORDS.has("siebenundzwanzig")).toBe(true);
    expect(NUMBER_WORDS.has("fuenf")).toBe(true);
    expect(NUMBER_WORDS.has("dreissig")).toBe(true);
  });
});

describe("Kotlin-Kern: VoiceCommandMatcherTest", () => {
  const catalog = CATALOG_ITEMS;
  const matcher = new VoiceCommandMatcher();
  const match = (...candidates: string[]) => matcher.match(candidates, catalog);
  const drink = (id: string): VoiceMatch => ({ kind: "Drink", item: catalogItem(id) });
  const ambiguous = (first: string, second: string): VoiceMatch => ({
    kind: "Ambiguous",
    first: catalogItem(first),
    second: catalogItem(second),
  });
  const command = (cmd: "UNDO" | "READ_ALOUD" | "TODAY"): VoiceMatch => ({ kind: "Command", cmd });

  it("beerPleaseMatchesPilsViaItsAlias", () => {
    expect(match("ein Bier bitte")).toEqual(drink(CatalogIds.PILS));
    expect(match("Ich trinke heute ein Bier")).toEqual(drink(CatalogIds.PILS));
    expect(match("zwei Bier")).toEqual(drink(CatalogIds.PILS));
  });

  it("pilsAndSoundAlikes", () => {
    expect(match("Pils")).toEqual(drink(CatalogIds.PILS));
    expect(match("Pilz")).toEqual(drink(CatalogIds.PILS));
    expect(match("Bils")).toEqual(drink(CatalogIds.PILS));
  });

  it("appleSpritzerVariants", () => {
    expect(match("Apfelschorle")).toEqual(drink(CatalogIds.APFELSCHORLE));
    expect(match("eine Apfelsaftschorle")).toEqual(drink(CatalogIds.APFELSCHORLE));
    expect(match("Apfelsaft Schorle")).toEqual(drink(CatalogIds.APFELSCHORLE));
    expect(match("Schorle")).toEqual(drink(CatalogIds.APFELSCHORLE));
    expect(match("Apfelsaft")).toEqual(drink(CatalogIds.APFELSAFT));
  });

  it("withoutAliasesApfelsaftschorleIsAmbiguous", () => {
    const schorle = barItem("Apfelschorle");
    const juice = barItem("Apfelsaft");
    const bar = [juice, schorle];
    expect(matcher.match(["Apfelsaftschorle"], bar)).toEqual({ kind: "Ambiguous", first: schorle, second: juice });
    expect(matcher.match(["Apfelschorle"], bar)).toEqual({ kind: "Drink", item: schorle });
    expect(matcher.match(["Apfelsaft"], bar)).toEqual({ kind: "Drink", item: juice });
  });

  it("ambiguousPairs", () => {
    expect(match("ein Glas Wein")).toEqual(ambiguous(CatalogIds.ROTWEIN, CatalogIds.WEISSWEIN));
    expect(match("Saft")).toEqual(ambiguous(CatalogIds.ORANGENSAFT, CatalogIds.APFELSAFT));
    expect(match("Apfel")).toEqual(ambiguous(CatalogIds.APFELSAFT, CatalogIds.APFELSCHORLE));
    const small = barItem("Wasser", { id: "klein", serving: 250 });
    const large = barItem("Wasser", { id: "gross", serving: 500 });
    expect(matcher.match(["Wasser"], [small, large])).toEqual({ kind: "Ambiguous", first: small, second: large });
  });

  it("closeNamesAreDistinguished", () => {
    expect(match("ich trinke einen Rotwein")).toEqual(drink(CatalogIds.ROTWEIN));
    expect(match("WEISSWEIN")).toEqual(drink(CatalogIds.WEISSWEIN));
    expect(match("Cola")).toEqual(drink(CatalogIds.COLA));
    expect(match("Cola Zero")).toEqual(drink(CatalogIds.COLA_ZERO));
    expect(match("eine Cola light")).toEqual(drink(CatalogIds.COLA_ZERO));
    expect(match("Wasser")).toEqual(drink(CatalogIds.WASSER));
    expect(match("Sprudel")).toEqual(drink(CatalogIds.MINERALWASSER));
    expect(match("eine Tasse Tee")).toEqual(drink(CatalogIds.TEE));
    expect(match("Kräutertee")).toEqual(drink(CatalogIds.TEE));
    expect(match("Eistee")).toEqual(drink(CatalogIds.EISTEE));
    expect(match("ein Weißbier")).toEqual(drink(CatalogIds.WEIZENBIER));
    expect(match("ein alkoholfreies Bier")).toEqual(drink(CatalogIds.ALKOHOLFREIES_BIER));
    expect(match("Malzbier")).toEqual(drink(CatalogIds.MALZBIER));
    expect(match("Ich hatte eine Tasse Kaffee")).toEqual(drink(CatalogIds.KAFFEE));
  });

  it("wordEndingMatchesAnUnambiguousCompound", () => {
    const bar = [barItem("Weizenbier"), barItem("Wasser")];
    expect(matcher.match(["ein Bier"], bar)).toEqual({ kind: "Drink", item: bar[0] });
    const wine = barItem("Rotwein trocken");
    expect(matcher.match(["Wein"], [wine, barItem("Kaffee")])).toEqual({ kind: "Drink", item: wine });
  });

  it("commands", () => {
    for (const text of ["Rückgängig", "rückgängig machen", "bitte rückgängig", "Nein", "nein danke", "nee"]) {
      expect(match(text), text).toEqual(command("UNDO"));
    }
    for (const text of ["Vorlesen", "bitte vorlesen", "lies das bitte vor"]) {
      expect(match(text), text).toEqual(command("READ_ALOUD"));
    }
    for (const text of ["Wie viel heute?", "wieviel habe ich heute getrunken", "Was habe ich heute getrunken"]) {
      expect(match(text), text).toEqual(command("TODAY"));
    }
  });

  it("commandsWorkWithAnEmptyBar", () => {
    expect(matcher.match(["rückgängig"], [])).toEqual(command("UNDO"));
    expect(matcher.match(["Pils"], [])).toEqual({ kind: "NoMatch" });
  });

  it("noMatch", () => {
    for (const text of ["Fahrrad", "", "bitte", "ich trinke ein Glas"]) expect(match(text), text).toEqual({ kind: "NoMatch" });
    expect(matcher.match([], catalog)).toEqual({ kind: "NoMatch" });
  });

  it("candidatesAreCheckedInOrderAndTheFirstResultWins", () => {
    expect(match("Fahrrad fahren", "ein Pils", "Wasser")).toEqual(drink(CatalogIds.PILS));
    expect(match("nein", "Wein")).toEqual(command("UNDO"));
    expect(match("Wein", "nein")).toEqual(ambiguous(CatalogIds.ROTWEIN, CatalogIds.WEISSWEIN));
  });

  it("thresholdAndGapFollowTheContract", () => {
    const scores: Record<string, number> = { aaa: 0.88, bbb: 0.83, ccc: 0.84, ddd: 0.879 };
    const stub = new VoiceCommandMatcher({ similarity: { similarity: (_spoken, label) => scores[label] ?? 0.0 } });
    const [a, b, c, d] = ["aaa", "bbb", "ccc", "ddd"].map((name) => barItem(name));
    expect(stub.match(["xyz"], [b, a])).toEqual({ kind: "Drink", item: a });
    expect(stub.match(["xyz"], [a, c])).toEqual({ kind: "Ambiguous", first: a, second: c });
    expect(stub.match(["xyz"], [d])).toEqual({ kind: "NoMatch" });
    expect(MINIMUM_SIMILARITY).toBe(0.88);
    expect(MINIMUM_GAP).toBe(0.05);
  });

  it("thresholdsAreConfigurable", () => {
    expect(new VoiceCommandMatcher({ minimumSimilarity: 0.99 }).match(["Pilz"], catalog)).toEqual({ kind: "NoMatch" });
  });

  it("companionUsesTheDefaultMatcher", () => {
    expect(defaultVoiceMatcher.match(["ein Bier bitte"], catalog)).toEqual(drink(CatalogIds.PILS));
    expect(defaultVoiceMatcher.match(["wie viel heute"], catalog)).toEqual(command("TODAY"));
  });
});
