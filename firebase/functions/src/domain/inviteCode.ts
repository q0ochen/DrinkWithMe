/** Länge eines Einladungscodes – wie `InviteCode.LENGTH` im Kotlin-Kern. */
export const INVITE_CODE_LENGTH = 6;

/** Alphabet ohne leicht verwechselbare Zeichen (kein I, L, O, 0, 1) – wie `InviteCode.ALPHABET`. */
export const INVITE_CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

/**
 * Normalisiert eine Eingabe wie `InviteCode.parse` im Kotlin-Kern
 * (Großschreibung, ohne Leerzeichen/Bindestriche) und prüft das Format.
 *
 * @returns den normalisierten Code oder `null`, wenn das Format nicht stimmt.
 */
export function parseInviteCode(raw: unknown): string | null {
  if (typeof raw !== "string") return null;
  const normalized = raw.toUpperCase().replace(/[\s-]/gu, "");
  if (normalized.length !== INVITE_CODE_LENGTH) return null;
  return [...normalized].every((character) => INVITE_CODE_ALPHABET.includes(character))
    ? normalized
    : null;
}

/** Liefert eine gleichverteilte ganze Zahl im Bereich `[0, bound)`. */
export type RandomIndex = (bound: number) => number;

/** Erzeugt Einladungscodes. Die Eindeutigkeit stellt der Aufrufer sicher (Transaktion). */
export interface InviteCodeGenerator {
  next(): string;
}

/** Erzeugt zufällige Codes; die Zufallsquelle wird injiziert (in Produktion `crypto.randomInt`). */
export class RandomInviteCodeGenerator implements InviteCodeGenerator {
  constructor(private readonly randomIndex: RandomIndex) {}

  next(): string {
    let code = "";
    for (let position = 0; position < INVITE_CODE_LENGTH; position++) {
      code += INVITE_CODE_ALPHABET.charAt(this.randomIndex(INVITE_CODE_ALPHABET.length));
    }
    return code;
  }
}
