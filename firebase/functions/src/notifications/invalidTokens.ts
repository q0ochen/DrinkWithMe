import type { SendResult } from "./ports";

/** FCM-Fehler, bei denen ein Token immer dauerhaft ungültig ist und gelöscht wird (laut Vertrag). */
export const INVALID_TOKEN_ERROR_CODES: ReadonlySet<string> = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
]);

/**
 * FCM v1 meldet ein kaputtes Token als `messaging/invalid-argument` („The registration token is not
 * a valid FCM registration token“). Derselbe Code steht aber auch für Fehler in der Nachricht selbst –
 * dann wäre jedes Token betroffen. Deshalb zählt `invalid-argument` nur, wenn die Meldung das
 * Registrierungs-Token nennt.
 */
export const INVALID_ARGUMENT_ERROR_CODE = "messaging/invalid-argument";

const MENTIONS_REGISTRATION_TOKEN = /registration[ -]?token/iu;

/** `true`, wenn der Versand an diesem Token wegen eines dauerhaft ungültigen Tokens scheiterte. */
export function isInvalidTokenResult(result: SendResult): boolean {
  const { errorCode, errorMessage } = result;
  if (errorCode === undefined) return false;
  if (INVALID_TOKEN_ERROR_CODES.has(errorCode)) return true;
  return errorCode === INVALID_ARGUMENT_ERROR_CODE && MENTIONS_REGISTRATION_TOKEN.test(errorMessage ?? "");
}

/** Die Tokens aus `results`, die gelöscht werden sollen (ohne Duplikate). */
export function invalidTokens(results: readonly SendResult[]): string[] {
  return [...new Set(results.filter(isInvalidTokenResult).map((result) => result.token))];
}
