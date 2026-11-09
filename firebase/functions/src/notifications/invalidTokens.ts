import type { DeviceToken, SendResult } from "./ports";

/** FCM-Fehler, bei denen ein Token dauerhaft ungültig ist und gelöscht wird (laut Vertrag). */
export const INVALID_TOKEN_ERROR_CODES: ReadonlySet<string> = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
]);

/** Alle gespeicherten Tokens, deren Versand mit einem der obigen Fehler scheiterte. */
export function invalidTokens(
  tokens: readonly DeviceToken[],
  results: readonly SendResult[],
): DeviceToken[] {
  const invalid = new Set(
    results
      .filter((result) => result.errorCode !== undefined && INVALID_TOKEN_ERROR_CODES.has(result.errorCode))
      .map((result) => result.token),
  );
  return tokens.filter((deviceToken) => invalid.has(deviceToken.token));
}
