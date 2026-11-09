/** Anzeigename, wenn weder Profil noch Anmelde-Token einen Namen liefern. */
export const UNKNOWN_DISPLAY_NAME = "Unbekannt";

/** Höchstlänge eines Anzeigenamens – wie `users/{uid}.displayName` und `authorName`. */
export const MAX_DISPLAY_NAME_LENGTH = 50;

/**
 * Anzeigename eines Mitglieds laut Vertrag: `users/{uid}.displayName`,
 * sonst `request.auth.token.name`, sonst „Unbekannt“.
 * Leere Namen werden übersprungen, zu lange auf 50 Zeichen gekürzt.
 */
export function resolveDisplayName(profileName: unknown, tokenName: unknown): string {
  for (const candidate of [profileName, tokenName]) {
    if (typeof candidate !== "string") continue;
    const trimmed = candidate.trim();
    if (trimmed.length > 0) return truncate(trimmed, MAX_DISPLAY_NAME_LENGTH);
  }
  return UNKNOWN_DISPLAY_NAME;
}

/** Kürzt auf höchstens `maximum` UTF-16-Einheiten, ohne ein Emoji zu zerteilen. */
function truncate(text: string, maximum: number): string {
  if (text.length <= maximum) return text;
  let result = "";
  for (const character of text) {
    if (result.length + character.length > maximum) break;
    result += character;
  }
  return result.trimEnd();
}
