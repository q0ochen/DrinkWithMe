/** Grammatisches Geschlecht eines Getränkenamens. */
export type GrammaticalGender = "MASCULINE" | "FEMININE" | "NEUTER";

/** Gefäße am Anfang („Glas Wein“) bestimmen das Geschlecht. */
export const ARTICLE_CONTAINERS: Readonly<Record<string, GrammaticalGender>> = {
  glas: "NEUTER",
  gläschen: "NEUTER",
  tasse: "FEMININE",
  flasche: "FEMININE",
  dose: "FEMININE",
  kanne: "FEMININE",
  becher: "MASCULINE",
  krug: "MASCULINE",
  pott: "MASCULINE",
  schluck: "MASCULINE",
};

export const FEMININE_ENDINGS = [
  "schorle", "cola", "limonade", "limo", "milch", "brause", "mate", "schokolade", "bowle", "brühe", "maß", "halbe",
] as const;
export const MASCULINE_ENDINGS = [
  "wein", "kaffee", "tee", "saft", "sekt", "schnaps", "likör", "drink", "cocktail", "espresso", "cappuccino",
  "macchiato", "mokka", "kakao", "whisky", "whiskey", "wodka", "vodka", "rum", "gin", "tequila", "korn",
  "smoothie", "shake", "prosecco", "champagner", "punsch", "grog", "kefir", "lassi", "sirup", "trunk", "most",
  "spritz", "sprudel", "kurzer", "klarer", "shot", "cidre",
] as const;
export const NEUTER_ENDINGS = [
  "wasser", "bier", "pils", "pilsener", "pilsner", "weizen", "kölsch", "helles", "dunkles", "radler",
  "getränk", "tonic", "export",
] as const;

/** Wortenden, längste zuerst (stabil sortiert wie `sortedByDescending` in Kotlin). */
const NOUN_ENDINGS: ReadonlyArray<readonly [string, GrammaticalGender]> = [
  ...FEMININE_ENDINGS.map((ending) => [ending, "FEMININE"] as const),
  ...MASCULINE_ENDINGS.map((ending) => [ending, "MASCULINE"] as const),
  ...NEUTER_ENDINGS.map((ending) => [ending, "NEUTER"] as const),
].sort((a, b) => b[0].length - a[0].length);

export const ARTICLE_DETERMINERS: ReadonlySet<string> = new Set([
  "ein", "eine", "einen", "der", "die", "das", "den", "mein", "meine", "meinen", "kein", "keine", "keinen",
  "etwas", "noch",
]);

function words(text: string): string[] {
  return text
    .toLocaleLowerCase("de-DE")
    .split(/[^\p{L}]+/u)
    .filter((word) => word.length > 0);
}

/** Geschlecht des Getränkenamens oder `null`, wenn es nicht sicher bestimmbar ist. */
export function genderOf(drinkName: string): GrammaticalGender | null {
  const list = words(drinkName);
  const first = list[0];
  if (first !== undefined && Object.hasOwn(ARTICLE_CONTAINERS, first)) return ARTICLE_CONTAINERS[first];
  for (const word of [...list].reverse()) {
    const match = NOUN_ENDINGS.find(([ending]) => word.endsWith(ending));
    if (match) return match[1];
  }
  return null;
}

/**
 * Name mit unbestimmtem Artikel im Akkusativ (Port von `GermanArticles`): „einen Kaffee“, „eine Apfelschorle“,
 * „ein Wasser“. Beginnt der Name schon mit Artikel oder Ziffer oder ist das Geschlecht unbekannt, bleibt er
 * unverändert („Anna trinkt Kombucha“ ist ebenfalls korrekt).
 */
export function withIndefiniteArticle(drinkName: string): string {
  const name = drinkName.trim();
  const first = words(name)[0];
  if (name.length === 0 || /^\p{Nd}/u.test(name) || (first !== undefined && ARTICLE_DETERMINERS.has(first))) return name;
  switch (genderOf(name)) {
    case "MASCULINE":
      return `einen ${name}`;
    case "FEMININE":
      return `eine ${name}`;
    case "NEUTER":
      return `ein ${name}`;
    default:
      return name;
  }
}
