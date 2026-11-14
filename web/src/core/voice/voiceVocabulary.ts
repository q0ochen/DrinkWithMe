import { cardinal } from "../speech/germanNumberWords";
import { germanTokens, normalizeGerman } from "./germanTextNormalizer";

/** Füllwörter: „ich trinke/nehme/hatte“, Artikel, Gefäße, Mengenangaben, Höflichkeit (bereits normalisiert). */
export const FILLER_WORDS: ReadonlySet<string> = new Set([
  "ich", "trinke", "trink", "trinken", "nehme", "nehm", "nehmen", "hatte", "hab", "habe", "getrunken",
  "moechte", "will", "gerne", "gern", "bitte", "danke", "noch", "mal", "jetzt", "gerade", "heute",
  "ein", "eine", "einen", "einem", "einer", "eines", "der", "die", "das", "den", "dem", "des",
  "glas", "glaeser", "glaeschen", "flasche", "flaschen", "tasse", "tassen", "becher", "dose", "dosen",
  "kanne", "krug", "pott", "schluck",
  "liter", "milliliter", "zentiliter", "ml", "cl",
  "kleines", "kleine", "kleinen", "kleiner", "grosses", "grosse", "grossen", "grosser",
]);

/** Zahlwörter (null bis hundert, „zwo“, Brüche, Vielfache) – ohne Bedeutung für die Getränkeauswahl. */
export const NUMBER_WORDS: ReadonlySet<string> = (() => {
  const words = new Set<string>();
  for (let number = 0; number <= 100; number++) words.add(normalizeGerman(cardinal(number)));
  for (const word of [
    "zwo", "hundert", "tausend", "komma", "halb", "halbe", "halben", "halber", "halbes", "viertel",
    "einmal", "zweimal", "dreimal", "doppelt", "doppelte", "doppelten", "doppelter", "doppeltes",
  ]) {
    words.add(word);
  }
  return words;
})();

/** `true`, wenn das (normalisierte) Wort für den Abgleich keine Bedeutung hat. */
export function isIgnorable(token: string): boolean {
  return FILLER_WORDS.has(token) || NUMBER_WORDS.has(token) || /^[0-9]*$/.test(token);
}

/** Die bedeutungstragenden Wörter eines Textes. */
export function meaningfulTokens(text: string): string[] {
  return germanTokens(text).filter((token) => !isIgnorable(token));
}
