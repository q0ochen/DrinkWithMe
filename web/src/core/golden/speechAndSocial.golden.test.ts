// Golden-Tests: dieselben Eingaben und Erwartungen wie in den Kotlin-Tests GermanNumberWordsTest, SpeechTextsTest,
// GermanArticlesAndEventTextsTest und SocialTest.
import { describe, expect, it } from "vitest";
import { CATALOG_ITEMS } from "../bar/defaultBarCatalog";
import { inviteAppLink, inviteWebLink, parseInviteToken } from "../social/inviteLinks";
import type { FriendDrinkEvent } from "../social/ports";
import { EventTexts } from "../speech/eventTexts";
import { genderOf, withIndefiniteArticle } from "../speech/germanArticles";
import { cardinal, decimal, ordinalDative } from "../speech/germanNumberWords";
import { grams, quantity, quantityInt, speakable, SpeechTexts, SpeechUnit, volume } from "../speech/speechTexts";
import type { MonthComparison, TodaySummary, WeeklyAlcohol } from "../stats/statisticsCalculator";
import { instant } from "../testing/fixtures";
import { LocalDate } from "../time/localDate";

const today = (sugarGrams: number, alcoholGrams: number, sugaryDrinks: number, alcoholicDrinks: number, totalMilliliters: number, drinkCount: number): TodaySummary => ({
  sugarGrams,
  alcoholGrams,
  sugaryDrinks,
  alcoholicDrinks,
  totalMilliliters,
  drinkCount,
});
const week = (iso: string, grams: number, alcoholFreeDays: number): WeeklyAlcohol => ({
  weekStart: LocalDate.parse(iso),
  grams,
  alcoholFreeDays,
});
const comparison = (s: number | null, a: number | null, s1: number, s2: number, a1: number, a2: number): MonthComparison => ({
  sugarChangePercent: s,
  alcoholChangePercent: a,
  sugarGramsLast30Days: s1,
  sugarGramsPrevious30Days: s2,
  alcoholGramsLast30Days: a1,
  alcoholGramsPrevious30Days: a2,
});

describe("Kotlin-Kern: GermanNumberWordsTest", () => {
  it("knownCardinals", () => {
    const expected: Record<number, string> = {
      0: "null", 1: "eins", 2: "zwei", 6: "sechs", 7: "sieben", 10: "zehn", 11: "elf", 12: "zwölf",
      13: "dreizehn", 16: "sechzehn", 17: "siebzehn", 19: "neunzehn", 20: "zwanzig", 21: "einundzwanzig",
      27: "siebenundzwanzig", 30: "dreißig", 35: "fünfunddreißig", 42: "zweiundvierzig", 60: "sechzig",
      70: "siebzig", 99: "neunundneunzig", 100: "einhundert", 101: "einhunderteins", 111: "einhundertelf",
      121: "einhunderteinundzwanzig", 200: "zweihundert", 250: "zweihundertfünfzig",
      330: "dreihundertdreißig", 999: "neunhundertneunundneunzig", 1000: "eintausend", 1001: "eintausendeins",
      1100: "eintausendeinhundert", 1580: "eintausendfünfhundertachtzig", 2000: "zweitausend",
      2500: "zweitausendfünfhundert", 7777: "siebentausendsiebenhundertsiebenundsiebzig",
      9999: "neuntausendneunhundertneunundneunzig", 10000: "zehntausend", 21000: "einundzwanzigtausend",
      101000: "einhunderteintausend", 999999: "neunhundertneunundneunzigtausendneunhundertneunundneunzig",
    };
    for (const [number, words] of Object.entries(expected)) expect(cardinal(Number(number)), number).toBe(words);
  });

  it("largeAndNegativeNumbers", () => {
    expect(cardinal(1_000_000)).toBe("eine Million");
    expect(cardinal(1_000_001)).toBe("eine Million eins");
    expect(cardinal(2_300_000)).toBe("zwei Millionen dreihunderttausend");
    expect(cardinal(-5)).toBe("minus fünf");
    expect(cardinal(1_000_000_000)).toBe("1000000000");
  });

  it("allNumbersUpTo9999AreDistinctWordsWithoutDigits", () => {
    const words = Array.from({ length: 10_000 }, (_, number) => cardinal(number));
    expect(new Set(words).size).toBe(10_000);
    words.forEach((word, number) => {
      expect(/^[a-zäöüß]+$/.test(word), `${number}: ${word}`).toBe(true);
      expect(word.slice(0, word.endsWith("eins") ? -4 : undefined).includes("eins"), `${number}: ${word}`).toBe(false);
      expect(/sechszehn|siebenzehn|sechszig|siebenzig/.test(word), word).toBe(false);
    });
  });

  it("decimals", () => {
    expect(decimal(2.5)).toBe("zwei Komma fünf");
    expect(decimal(2.0)).toBe("zwei");
    expect(decimal(0.4)).toBe("null Komma vier");
    expect(decimal(1.5)).toBe("eins Komma fünf");
    expect(decimal(19.6)).toBe("neunzehn Komma sechs");
    expect(decimal(100.0)).toBe("einhundert");
    expect(decimal(2.25)).toBe("zwei Komma drei");
    expect(decimal(1.05)).toBe("eins Komma eins");
    expect(decimal(12.25, 2)).toBe("zwölf Komma zwei fünf");
    expect(decimal(12.05, 2)).toBe("zwölf Komma null fünf");
    expect(decimal(12.6, 0)).toBe("dreizehn");
    expect(decimal(-1.5)).toBe("minus eins Komma fünf");
    expect(decimal(-0.04)).toBe("null");
    expect(() => decimal(Number.NaN)).toThrow(RangeError);
  });

  it("ordinalsForDates", () => {
    const expected: Record<number, string> = {
      1: "ersten", 2: "zweiten", 3: "dritten", 4: "vierten", 7: "siebten", 8: "achten", 9: "neunten",
      11: "elften", 16: "sechzehnten", 19: "neunzehnten", 20: "zwanzigsten", 21: "einundzwanzigsten",
      30: "dreißigsten", 31: "einunddreißigsten",
    };
    for (const [number, words] of Object.entries(expected)) expect(ordinalDative(Number(number))).toBe(words);
    expect(() => ordinalDative(0)).toThrow(RangeError);
  });
});

describe("Kotlin-Kern: SpeechTextsTest", () => {
  const anna = { id: "u-1", displayName: "Anna", avatarUrl: null };
  const event: FriendDrinkEvent = {
    id: "e-1",
    author: anna,
    name: "Apfelschorle",
    icon: "apple_spritzer",
    category: "JUICE",
    volumeMilliliters: 500,
    consumedAt: instant("2026-11-10T17:00:00Z"),
  };

  it("drinkEventSentences", () => {
    expect(SpeechTexts.friendEvent(event)).toBe("Anna trinkt eine Apfelschorle.");
    expect(SpeechTexts.friendEvent(event, true)).toBe("Anna trinkt eine Apfelschorle, fünfhundert Milliliter.");
    expect(SpeechTexts.drinkEvent("Anna", "Kaffee")).toBe("Anna trinkt einen Kaffee.");
    expect(SpeechTexts.drinkEvent("Anna", "Wasser")).toBe("Anna trinkt ein Wasser.");
    expect(SpeechTexts.drinkEvent("Anna", "Pils")).toBe("Anna trinkt ein Pils.");
    expect(SpeechTexts.drinkEvent("Ben", "Rotwein trocken")).toBe("Ben trinkt einen Rotwein trocken.");
    expect(SpeechTexts.drinkEvent("Ben", "Cola zero")).toBe("Ben trinkt eine Cola zero.");
    expect(SpeechTexts.drinkEvent("Ben", "Kombucha")).toBe("Ben trinkt Kombucha.");
    expect(SpeechTexts.drinkEvent("Anna", "Wasser", 1500)).toBe("Anna trinkt ein Wasser, eins Komma fünf Liter.");
  });

  it("drinkEventWithoutEmojisAndWithFallbacks", () => {
    expect(SpeechTexts.drinkEvent("Anna 🌹", "Bier 🍺️")).toBe("Anna trinkt ein Bier.");
    expect(SpeechTexts.drinkEvent("  ", "Apfelschorle")).toBe("Jemand trinkt eine Apfelschorle.");
    expect(SpeechTexts.drinkEvent("Anna", "🥤")).toBe("Anna trinkt etwas.");
  });

  it("confirmations", () => {
    expect(SpeechTexts.logged("Apfelschorle")).toBe("Apfelschorle eingetragen.");
    expect(SpeechTexts.undone("Apfelschorle")).toBe("Apfelschorle wieder entfernt.");
    expect(SpeechTexts.whichDrink("Apfelschorle", "Apfelsaft")).toBe("Meinst du Apfelschorle oder Apfelsaft?");
    expect(SpeechTexts.logged("🍺")).toBe("Getränk eingetragen.");
    expect(SpeechTexts.notUnderstood()).toBe("Das habe ich leider nicht verstanden. Sag bitte den Namen eines Getränks aus deiner Bar.");
  });

  it("todaySummary", () => {
    expect(SpeechTexts.today(today(35.2, 0.0, 1, 0, 1000, 3))).toBe("Heute aus Getränken: fünfunddreißig Gramm Zucker. Insgesamt ein Liter.");
    expect(SpeechTexts.today(today(48.98, 21.2, 1, 1, 1580, 4))).toBe(
      "Heute aus Getränken: neunundvierzig Gramm Zucker und einundzwanzig Gramm Alkohol. Insgesamt eins Komma sechs Liter.",
    );
    expect(SpeechTexts.today(today(2.5, 1.6, 0, 0, 500, 1))).toBe(
      "Heute aus Getränken: zwei Komma fünf Gramm Zucker und eins Komma sechs Gramm Alkohol. Insgesamt fünfhundert Milliliter.",
    );
    expect(SpeechTexts.today(today(0.0, 0.0, 0, 0, 250, 1))).toBe("Heute aus Getränken: kein Zucker. Insgesamt zweihundertfünfzig Milliliter.");
    expect(SpeechTexts.today(today(0.0, 0.0, 0, 0, 0, 0))).toBe("Heute hast du noch nichts eingetragen.");
  });

  it("weeklyAlcohol", () => {
    const day = LocalDate.of(2026, 11, 20);
    expect(SpeechTexts.weeklyAlcohol(week("2026-11-16", 42.0, 4), day)).toBe(
      "Diese Woche: zweiundvierzig Gramm Alkohol, vier alkoholfreie Tage. Das entspricht dem Bereich moderates " +
        "Risiko nach der Deutschen Gesellschaft für Ernährung. Es gibt keine unbedenkliche Menge Alkohol. Weniger ist besser.",
    );
    expect(SpeechTexts.weeklyAlcohol(week("2026-11-09", 0.0, 7), day)).toBe("Letzte Woche: kein Alkohol, sieben alkoholfreie Tage.");
    expect(SpeechTexts.weeklyAlcohol(week("2026-11-02", 19.6, 1), day)).toBe(
      "In der Woche ab dem zweiten November: zwanzig Gramm Alkohol, ein alkoholfreier Tag. Das entspricht " +
        "dem Bereich risikoarm nach der Deutschen Gesellschaft für Ernährung. Es gibt keine unbedenkliche Menge " +
        "Alkohol. Weniger ist besser.",
    );
    expect(SpeechTexts.weeklyAlcohol(week("2026-08-31", 100.0, 0), day)).toBe(
      "In der Woche ab dem einunddreißigsten August: einhundert Gramm Alkohol, kein alkoholfreier Tag. Das entspricht " +
        "dem Bereich riskant nach der Deutschen Gesellschaft für Ernährung. Es gibt keine unbedenkliche Menge " +
        "Alkohol. Weniger ist besser.",
    );
  });

  it("monthComparison", () => {
    expect(SpeechTexts.monthComparison(comparison(-20.0, 5.2, 80.0, 100.0, 21.0, 20.0))).toBe(
      "Vergleich der letzten dreißig Tage mit den dreißig Tagen davor: Zucker aus Getränken zwanzig Prozent weniger, " +
        "Alkohol fünf Prozent mehr.",
    );
    expect(SpeechTexts.monthComparison(comparison(null, 0.3, 0.0, 0.0, 20.0, 20.0))).toBe(
      "Vergleich der letzten dreißig Tage mit den dreißig Tagen davor: Zucker aus Getränken noch ohne Vergleichswert, " +
        "Alkohol etwa gleich viel.",
    );
    expect(SpeechTexts.monthComparison(comparison(1.0, -100.0, 0.0, 0.0, 0.0, 0.0))).toBe(
      "Vergleich der letzten dreißig Tage mit den dreißig Tagen davor: Zucker aus Getränken ein Prozent mehr, " +
        "Alkohol einhundert Prozent weniger.",
    );
  });

  it("quantitiesAndUnitsAreWrittenOut", () => {
    expect(quantityInt(250, SpeechUnit.MILLILITER)).toBe("zweihundertfünfzig Milliliter");
    expect(grams(27.0)).toBe("siebenundzwanzig Gramm");
    expect(grams(1.0)).toBe("ein Gramm");
    expect(grams(0.4)).toBe("null Komma vier Gramm");
    expect(grams(9.96)).toBe("zehn Gramm");
    expect(grams(19.6)).toBe("zwanzig Gramm");
    expect(quantity(2.5, SpeechUnit.LITER)).toBe("zwei Komma fünf Liter");
    expect(volume(1)).toBe("ein Milliliter");
    expect(volume(1000)).toBe("ein Liter");
    expect(volume(1250)).toBe("eins Komma drei Liter");
    expect(volume(2000)).toBe("zwei Liter");
    expect(quantityInt(5, SpeechUnit.PROZENT)).toBe("fünf Prozent");
  });

  it("spokenTextsContainNoDigitsAbbreviationsOrEmojis", () => {
    const texts = [
      SpeechTexts.friendEvent(event, true),
      SpeechTexts.today(today(48.98, 21.2, 1, 1, 1580, 4)),
      SpeechTexts.weeklyAlcohol(week("2026-11-02", 19.6, 1), LocalDate.of(2026, 11, 20)),
      SpeechTexts.monthComparison(comparison(-12.0, 3.0, 1.0, 1.0, 1.0, 1.0)),
    ];
    for (const text of texts) {
      expect(/\d/.test(text), text).toBe(false);
      for (const forbidden of [" ml", " g ", " l ", "DGE", "%", "Vol."]) expect(text.includes(forbidden), `${forbidden} in ${text}`).toBe(false);
      expect(/\p{So}/u.test(text)).toBe(false);
    }
  });

  it("speakableRemovesSymbols", () => {
    expect(speakable("🍺 Bier ‍")).toBe("Bier");
    expect(speakable("  Club-Mate  ")).toBe("Club-Mate");
    expect(speakable("Äpfel")).toBe("Äpfel");
  });
});

describe("Kotlin-Kern: GermanArticlesAndEventTextsTest", () => {
  it("articlesForTheWholeCatalog", () => {
    const expected: Array<[string, string]> = [
      ["Wasser", "ein Wasser"], ["Mineralwasser", "ein Mineralwasser"], ["Kaffee", "einen Kaffee"],
      ["Tee", "einen Tee"], ["Cola", "eine Cola"], ["Cola zero", "eine Cola zero"], ["Limonade", "eine Limonade"],
      ["Eistee", "einen Eistee"], ["Energydrink", "einen Energydrink"], ["Orangensaft", "einen Orangensaft"],
      ["Apfelsaft", "einen Apfelsaft"], ["Apfelschorle", "eine Apfelschorle"], ["Malzbier", "ein Malzbier"],
      ["Milch", "eine Milch"], ["Pils", "ein Pils"], ["Weizenbier", "ein Weizenbier"],
      ["Alkoholfreies Bier", "ein Alkoholfreies Bier"], ["Radler", "ein Radler"],
      ["Rotwein trocken", "einen Rotwein trocken"], ["Weißwein trocken", "einen Weißwein trocken"],
      ["Sekt trocken", "einen Sekt trocken"], ["Schnaps", "einen Schnaps"], ["Longdrink", "einen Longdrink"],
      ["Cocktail", "einen Cocktail"],
    ];
    expect(expected.map(([name]) => name)).toEqual(CATALOG_ITEMS.map((it) => it.name));
    for (const [name, withArticle] of expected) expect(withIndefiniteArticle(name)).toBe(withArticle);
  });

  it("containersAndExistingArticles", () => {
    expect(withIndefiniteArticle("Glas Wein")).toBe("ein Glas Wein");
    expect(withIndefiniteArticle("Tasse Kakao")).toBe("eine Tasse Kakao");
    expect(withIndefiniteArticle("Flasche Wasser")).toBe("eine Flasche Wasser");
    expect(withIndefiniteArticle("Ein Glas Milch")).toBe("Ein Glas Milch");
    expect(withIndefiniteArticle("2 Bier")).toBe("2 Bier");
    expect(withIndefiniteArticle("Club-Mate")).toBe("eine Club-Mate");
    expect(withIndefiniteArticle("Latte Macchiato")).toBe("einen Latte Macchiato");
    expect(withIndefiniteArticle(" Kombucha ")).toBe("Kombucha");
    expect(genderOf("Kombucha")).toBeNull();
    expect(genderOf("Kölsch")).toBe("NEUTER");
  });

  it("eventDisplayTexts", () => {
    const event: FriendDrinkEvent = {
      id: "e-1",
      author: { id: "u-1", displayName: "Anna", avatarUrl: "https://example.org/a.png" },
      name: "Apfelschorle",
      icon: "apple_spritzer",
      category: "JUICE",
      volumeMilliliters: 500,
      consumedAt: instant("2026-11-10T17:00:00Z"),
    };
    expect(EventTexts.eventTitle(event)).toBe("Anna trinkt gerade");
    expect(EventTexts.eventBody(event)).toBe("Apfelschorle (500 ml)");
    expect(EventTexts.headline(event)).toBe("Anna trinkt eine Apfelschorle.");
    expect(EventTexts.notificationTitle(" Ben ")).toBe("Ben trinkt gerade");
    expect(EventTexts.notificationBody("Radler", 1500)).toBe("Radler (1500 ml)");
  });
});

describe("Kotlin-Kern: SocialTest (InviteLinks)", () => {
  const token = "AbCdEfGhIjKlMnOpQrStUv";

  it("parsesInviteTokensFromLinksAndRawTokens", () => {
    expect(parseInviteToken(token)).toBe(token);
    expect(parseInviteToken(` drinkwithme://einladung/${token} `)).toBe(token);
    expect(parseInviteToken(`https://q0ochen.github.io/DrinkWithMe/einladung/${token}`)).toBe(token);
    expect(parseInviteToken(`https://q0ochen.github.io/DrinkWithMe/einladung/${token}/`)).toBe(token);
    expect(parseInviteToken(`https://example.org/einladung/${token}?quelle=messenger#oben`)).toBe(token);
    expect(parseInviteToken(`drinkwithme://einladung/${"a-b_c".repeat(10)}`)).toBe("a-b_c".repeat(10));
  });

  it("rejectsInvalidInvites", () => {
    for (const input of [
      "",
      "kurz",
      `http://q0ochen.github.io/DrinkWithMe/einladung/${token}`,
      `drinkwithme://freunde/${token}`,
      `drinkwithme://einladung/${token}/mehr`,
      `https://example.org/einladung/${token}/mehr`,
      `https://example.org/${token}`,
      `https://example.org/einladung/${token.slice(0, -1)}`,
      `https://example.org/einladung/${token.slice(0, -1)}!`,
      "nicht einmal ein Link mit Leerzeichen",
      "x".repeat(129),
    ]) {
      expect(parseInviteToken(input), input).toBeNull();
    }
  });

  it("buildsInviteLinks", () => {
    expect(inviteAppLink(token)).toBe(`drinkwithme://einladung/${token}`);
    expect(inviteWebLink("https://q0ochen.github.io/DrinkWithMe/", token)).toBe(`https://q0ochen.github.io/DrinkWithMe/einladung/${token}`);
    expect(() => inviteAppLink("kurz")).toThrow(RangeError);
    expect(parseInviteToken(inviteWebLink("https://q0ochen.github.io/DrinkWithMe", token))).toBe(token);
  });
});
