import { koelnerPhonetik, type PhoneticEncoder } from "./koelnerPhonetik";

/** Ähnlichkeit zweier Texte zwischen 0 (nichts gemeinsam) und 1 (gleich). */
export interface TextSimilarity {
  similarity(a: string, b: string): number;
}

export const JARO_WINKLER_PREFIX_SCALE = 0.1;
export const JARO_WINKLER_MAX_PREFIX_LENGTH = 4;
export const JARO_WINKLER_BOOST_THRESHOLD = 0.7;

/** Reine Jaro-Ähnlichkeit. Zwei leere Texte gelten als gleich, ein leerer und ein nicht leerer als verschieden. */
export function jaro(a: string, b: string): number {
  if (a === b) return 1.0;
  if (a.length === 0 || b.length === 0) return 0.0;
  const window = Math.max(0, Math.trunc(Math.max(a.length, b.length) / 2) - 1);
  const matchedA = new Array<boolean>(a.length).fill(false);
  const matchedB = new Array<boolean>(b.length).fill(false);
  let matches = 0;
  for (let i = 0; i < a.length; i++) {
    const from = Math.max(0, i - window);
    const to = Math.min(b.length - 1, i + window);
    for (let j = from; j <= to; j++) {
      if (!matchedB[j] && a[i] === b[j]) {
        matchedA[i] = true;
        matchedB[j] = true;
        matches++;
        break;
      }
    }
  }
  if (matches === 0) return 0.0;
  // UTF-16-Einheiten wie Kotlin-`Char`s (nach der Normalisierung ohnehin nur a–z und 0–9).
  const orderA = a.split("").filter((_, index) => matchedA[index]);
  const orderB = b.split("").filter((_, index) => matchedB[index]);
  let mismatched = 0;
  for (let index = 0; index < orderA.length; index++) if (orderA[index] !== orderB[index]) mismatched++;
  const transpositions = Math.trunc(mismatched / 2);
  const m = matches;
  return (m / a.length + m / b.length + (m - transpositions) / m) / 3.0;
}

/**
 * Jaro-Winkler (Port von `JaroWinkler`): Jaro-Wert plus Bonus für einen gemeinsamen Anfang (bis 4 Zeichen,
 * Faktor 0,1), der Bonus gilt ab einem Jaro-Wert über 0,7.
 */
export const jaroWinkler: TextSimilarity = {
  similarity(a: string, b: string): number {
    const value = jaro(a, b);
    if (value <= JARO_WINKLER_BOOST_THRESHOLD) return value;
    let prefix = 0;
    const limit = Math.min(a.length, b.length, JARO_WINKLER_MAX_PREFIX_LENGTH);
    while (prefix < limit && a[prefix] === b[prefix]) prefix++;
    return value + prefix * JARO_WINKLER_PREFIX_SCALE * (1.0 - value);
  },
};

/**
 * Jaro-Winkler auf dem Text, ergänzt um die Kölner Phonetik (Port von `PhoneticTextSimilarity`): Klingen zwei
 * Wörter ähnlich („Bils“/„Pils“), hebt der Mittelwert aus Text- und Lautcode-Ähnlichkeit den Wert an.
 */
export class PhoneticTextSimilarity implements TextSimilarity {
  constructor(
    private readonly encoder: PhoneticEncoder = koelnerPhonetik,
    private readonly base: TextSimilarity = jaroWinkler,
  ) {}

  similarity(a: string, b: string): number {
    const text = this.base.similarity(a, b);
    const codeA = this.encoder.encode(a);
    const codeB = this.encoder.encode(b);
    if (codeA.length === 0 || codeB.length === 0) return text;
    const sound = this.base.similarity(codeA, codeB);
    return Math.max(text, (text + sound) / 2.0);
  }
}
