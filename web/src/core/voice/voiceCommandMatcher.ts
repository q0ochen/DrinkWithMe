import type { BarItem } from "../bar/barItem";
import { germanTokens } from "./germanTextNormalizer";
import { PhoneticTextSimilarity, type TextSimilarity } from "./textSimilarity";
import { isIgnorable } from "./voiceVocabulary";

/** Sprachbefehle: „rückgängig“/„nein“, „vorlesen“, „wie viel heute“. */
export type VoiceCommand = "UNDO" | "READ_ALOUD" | "TODAY";

/** Ergebnis des Sprachabgleichs. */
export type VoiceMatch =
  | { readonly kind: "Drink"; readonly item: BarItem }
  | { readonly kind: "Ambiguous"; readonly first: BarItem; readonly second: BarItem }
  | { readonly kind: "Command"; readonly cmd: VoiceCommand }
  | { readonly kind: "NoMatch" };

export const NO_MATCH: VoiceMatch = { kind: "NoMatch" };

/** Ordnet Ergebnisse einer Spracherkennung einem Befehl oder einem Getränk der Bar zu. */
export interface VoiceMatcher {
  /** `candidates`: Erkennungsergebnisse, das wahrscheinlichste zuerst. */
  match(candidates: readonly string[], bar: readonly BarItem[]): VoiceMatch;
}

/** Mindestähnlichkeit für einen Treffer. */
export const MINIMUM_SIMILARITY = 0.88;
/** Mindestabstand des besten zum zweitbesten Getränk. */
export const MINIMUM_GAP = 0.05;
/** Wert für ein gesprochenes Wort, das ein Wortende eines Namens ist („Wein“ in „Rotwein“). */
export const COMPOUND_PART_SCORE = 0.94;
/** Wert einer genauen Übereinstimmung; genau beim Namen schlägt sie jeden nicht genauen Treffer. */
export const EXACT_SCORE = 1.0;
/** Kürzere gesprochene Wörter zählen nicht als Wortende. */
export const MIN_COMPOUND_PART_LENGTH = 3;
const EPSILON = 1e-9;
const UNDO_FIRST_WORDS = new Set(["nein", "nee"]);

interface Phrase {
  readonly tokens: readonly string[];
  readonly compact: string;
}

function phraseOf(tokens: readonly string[]): Phrase | null {
  return tokens.length === 0 ? null : { tokens, compact: tokens.join("") };
}

export interface VoiceMatcherOptions {
  readonly similarity?: TextSimilarity;
  readonly minimumSimilarity?: number;
  readonly minimumGap?: number;
}

/**
 * Sprachabgleich gegen Befehle und die Bar (Port von `VoiceCommandMatcher`).
 *
 * Je Kandidat (in der gelieferten Reihenfolge; das erste Ergebnis außer `NoMatch` gilt): normalisieren;
 * Befehle prüfen; Füll- und Zahlwörter entfernen und gegen Name **und** Aliase vergleichen
 * (Jaro-Winkler + Kölner Phonetik; ein einzelnes Wort als Wortende zählt beim Namen voll, beim Alias nur für eine
 * Rückfrage als Zweitbestes); eindeutig ab 0,88 mit Abstand ≥ 0,05, sonst `Ambiguous`. Eine genaue Übereinstimmung
 * mit dem **Namen** gilt immer, wenn beim Zweitbesten nichts genau passt; ein genauer Alias reicht dafür nicht.
 */
export class VoiceCommandMatcher implements VoiceMatcher {
  private readonly similarity: TextSimilarity;
  private readonly minimumSimilarity: number;
  private readonly minimumGap: number;

  constructor(options: VoiceMatcherOptions = {}) {
    this.similarity = options.similarity ?? new PhoneticTextSimilarity();
    this.minimumSimilarity = options.minimumSimilarity ?? MINIMUM_SIMILARITY;
    this.minimumGap = options.minimumGap ?? MINIMUM_GAP;
  }

  match(candidates: readonly string[], bar: readonly BarItem[]): VoiceMatch {
    const labelled = bar.map((item) => ({ item, name: phraseOfLabel(item.name), aliases: aliasPhrases(item) }));
    for (const candidate of candidates) {
      const result = this.matchCandidate(candidate, labelled);
      if (result.kind !== "NoMatch") return result;
    }
    return NO_MATCH;
  }

  private matchCandidate(candidate: string, labelled: readonly Labelled[]): VoiceMatch {
    const tokens = germanTokens(candidate);
    const command = commandOf(tokens);
    if (command !== null) return { kind: "Command", cmd: command };
    const spoken = phraseOf(tokens.filter((token) => !isIgnorable(token)));
    if (spoken === null) return NO_MATCH;

    const ranked = labelled.map((entry) => this.scored(spoken, entry)).sort((a, b) => b.score - a.score);
    const best = ranked[0];
    if (best === undefined || best.score < this.minimumSimilarity - EPSILON) return NO_MATCH;
    // Zweitbestes nach `contender` (mit Alias-Wortenden); bei Gleichstand das zuerst gereihte.
    let runnerUp: Scored | undefined;
    for (const candidateScore of ranked.slice(1)) {
      if (runnerUp === undefined || candidateScore.contender > runnerUp.contender) runnerUp = candidateScore;
    }
    if (runnerUp === undefined) return { kind: "Drink", item: best.item };
    const onlyExact = isExact(best.nameScore) && !isExact(runnerUp.contender);
    if (!onlyExact && best.score - runnerUp.contender < this.minimumGap - EPSILON) {
      return { kind: "Ambiguous", first: best.item, second: runnerUp.item };
    }
    return { kind: "Drink", item: best.item };
  }

  private scored(spoken: Phrase, entry: Labelled): Scored {
    const name = entry.name;
    const nameScore =
      name === null ? 0.0 : Math.max(this.direct(spoken, name), isWordEnding(spoken, name) ? COMPOUND_PART_SCORE : 0.0);
    const aliasScore = entry.aliases.length === 0 ? 0.0 : Math.max(...entry.aliases.map((alias) => this.direct(spoken, alias)));
    const aliasPart = entry.aliases.some((alias) => isWordEnding(spoken, alias)) ? COMPOUND_PART_SCORE : 0.0;
    const score = Math.max(nameScore, aliasScore);
    return { item: entry.item, score, nameScore, contender: Math.max(score, aliasPart) };
  }

  private direct(spoken: Phrase, label: Phrase): number {
    return this.similarity.similarity(spoken.compact, label.compact);
  }
}

interface Labelled {
  readonly item: BarItem;
  readonly name: Phrase | null;
  readonly aliases: readonly Phrase[];
}

/**
 * `score`: bester Wert über Name und Aliase (damit gewinnt ein Getränk); `nameScore`: Wert des Namens allein;
 * `contender`: zusätzlich Alias-Wortenden – zählt nur als Zweitbestes, also nur für eine Rückfrage.
 */
interface Scored {
  readonly item: BarItem;
  readonly score: number;
  readonly nameScore: number;
  readonly contender: number;
}

function isExact(score: number): boolean {
  return score >= EXACT_SCORE - EPSILON;
}

/** „wein“ ist Wortende von „rotwein“; „cola“ ist ein Wort von „cola zero“. */
function isWordEnding(spoken: Phrase, label: Phrase): boolean {
  if (spoken.tokens.length !== 1) return false;
  const word = spoken.tokens[0];
  if (word.length < MIN_COMPOUND_PART_LENGTH) return false;
  return label.tokens.some((token) => token.endsWith(word) && (token.length > word.length || label.tokens.length > 1));
}

function phraseOfLabel(label: string): Phrase | null {
  const tokens = germanTokens(label);
  const meaningful = tokens.filter((token) => !isIgnorable(token));
  return phraseOf(meaningful.length > 0 ? meaningful : tokens);
}

function aliasPhrases(item: BarItem): Phrase[] {
  const phrases: Phrase[] = [];
  for (const alias of item.aliases) {
    const phrase = phraseOfLabel(alias);
    if (phrase !== null) phrases.push(phrase);
  }
  return phrases;
}

function commandOf(tokens: readonly string[]): VoiceCommand | null {
  if (tokens.length === 0) return null;
  if (tokens.includes("rueckgaengig") || UNDO_FIRST_WORDS.has(tokens[0])) return "UNDO";
  if (tokens.includes("vorlesen") || (tokens.includes("lies") && tokens.includes("vor"))) return "READ_ALOUD";
  if (tokens.includes("heute") && asksHowMuch(tokens)) return "TODAY";
  return null;
}

function asksHowMuch(tokens: readonly string[]): boolean {
  if (tokens.includes("wieviel") || tokens.includes("wieviele") || tokens[0] === "was") return true;
  for (let index = 0; index + 1 < tokens.length; index++) {
    if (tokens[index] === "wie" && (tokens[index + 1] === "viel" || tokens[index + 1] === "viele")) return true;
  }
  return false;
}

/** Standard-Abgleicher mit den Vertragswerten. */
export const defaultVoiceMatcher: VoiceMatcher = new VoiceCommandMatcher();
