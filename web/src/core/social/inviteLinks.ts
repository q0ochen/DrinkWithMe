/**
 * Einladungs-Links (Port von `InviteLinks`): `drinkwithme://einladung/<token>` (App) und
 * `<WEB_APP_URL>/einladung/<token>` (Web). Das Token ist base64url mit mindestens 128 Bit (≥ 22 Zeichen).
 */
export const INVITE_APP_SCHEME = "drinkwithme";
export const INVITE_PATH_SEGMENT = "einladung";

const TOKEN_PATTERN = /^[A-Za-z0-9_-]{22,128}$/;
/** Grobe URI-Zerlegung wie `java.net.URI` für hierarchische URIs: Schema, Authority, Pfad, Query, Fragment. */
const URI_PATTERN = /^([A-Za-z][A-Za-z0-9+.-]*):\/\/([^/?#]*)([^?#]*)(\?[^#]*)?(#.*)?$/;

export function isValidInviteToken(token: string): boolean {
  return TOKEN_PATTERN.test(token);
}

function requireToken(token: string): string {
  if (!isValidInviteToken(token)) throw new RangeError("Ungültiges Einladungs-Token.");
  return token;
}

/** Link, der die Android-App direkt öffnet. */
export function inviteAppLink(token: string): string {
  return `${INVITE_APP_SCHEME}://${INVITE_PATH_SEGMENT}/${requireToken(token)}`;
}

/** Link auf die Web-App, z. B. `https://q0ochen.github.io/DrinkWithMe/einladung/<token>`. */
export function inviteWebLink(webAppUrl: string, token: string): string {
  return `${webAppUrl.replace(/\/+$/, "")}/${INVITE_PATH_SEGMENT}/${requireToken(token)}`;
}

/**
 * Liest das Token aus einem App-Link, einem https-Link der Web-App oder einem nackten Token.
 * `null`, wenn die Eingabe keine gültige Einladung ist (z. B. http statt https).
 */
export function parseInviteToken(input: string): string | null {
  const text = input.trim();
  if (isValidInviteToken(text)) return text;
  if (/\s/.test(text)) return null;
  const match = URI_PATTERN.exec(text);
  if (!match) return null;
  const scheme = match[1].toLowerCase();
  const host = match[2].replace(/^[^@]*@/, "").replace(/:\d*$/, "");
  const segments = match[3].split("/").filter((segment) => segment.length > 0);
  let token: string | null = null;
  if (scheme === INVITE_APP_SCHEME) {
    token = segments.length === 1 && host === INVITE_PATH_SEGMENT ? segments[0] : null;
  } else if (scheme === "https") {
    token = segments.length >= 2 && segments[segments.length - 2] === INVITE_PATH_SEGMENT ? segments[segments.length - 1] : null;
  }
  return token !== null && isValidInviteToken(token) ? token : null;
}
