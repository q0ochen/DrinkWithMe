import { INVITE_CODE_LENGTH } from "./inviteCode";

/** Fehlercodes, die als Callable-Fehler an die App gehen (Teilmenge der Firebase-Codes). */
export type SocialErrorCode =
  | "unauthenticated"
  | "invalid-argument"
  | "not-found"
  | "resource-exhausted"
  | "aborted";

/**
 * Fachlicher Fehler der Trinkrunden. Die Meldung ist deutsch und wird von der App
 * direkt angezeigt; die Übersetzung in einen `HttpsError` übernimmt die Callable-Schicht.
 */
export class SocialError extends Error {
  readonly code: SocialErrorCode;

  constructor(code: SocialErrorCode, message: string) {
    super(message);
    this.name = "SocialError";
    this.code = code;
  }
}

/** Alle fachlichen Fehler an einer Stelle – Texte wie im Kotlin-Kern (`SocialExceptions.kt`). */
export const SocialErrors = {
  unauthenticated: () => new SocialError("unauthenticated", "Bitte melde dich zuerst an."),
  emptyGroupName: () => new SocialError("invalid-argument", "Bitte gib der Runde einen Namen."),
  groupNameTooLong: (maximum: number) =>
    new SocialError("invalid-argument", `Der Rundenname darf höchstens ${maximum} Zeichen lang sein.`),
  invalidInviteCode: () =>
    new SocialError(
      "invalid-argument",
      `Der Code muss aus ${INVITE_CODE_LENGTH} Zeichen bestehen, z. B. K7MX2Q.`,
    ),
  invalidGroupId: () => new SocialError("invalid-argument", "Diese Runde gibt es nicht."),
  groupNotFound: () => new SocialError("not-found", "Zu diesem Code gibt es keine Runde."),
  groupFull: (maximum: number) =>
    new SocialError(
      "resource-exhausted",
      `Diese Runde ist voll – es können höchstens ${maximum} Personen mitmachen.`,
    ),
  inviteCodesExhausted: () =>
    new SocialError(
      "aborted",
      "Es konnte gerade kein freier Einladungscode erzeugt werden. Bitte versuche es noch einmal.",
    ),
} as const;
