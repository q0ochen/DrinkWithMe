/**
 * HTTP-Hilfen für die Edge Functions: JSON-Antworten, CORS (die Web-App ruft die Funktionen aus dem Browser
 * auf) und das Auslesen des Nutzer-JWT. Fehler werden als maschinenlesbare Codes geliefert
 * (`{ "error": "rate_limited" }`); die Apps zeigen dazu deutsche Texte an.
 */

/** CORS-Header: Aufruf mit Bearer-Token aus jeder Herkunft (keine Cookies, daher `*` unkritisch). */
export const CORS_HEADERS: Readonly<Record<string, string>> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Max-Age": "86400",
};

/** Maschinenlesbare Fehlercodes der Edge Functions. */
export type ErrorCode =
  | "method_not_allowed"
  | "unauthorized"
  | "invalid_request"
  | "rate_limited"
  | "event_not_found"
  | "server_misconfigured"
  | "internal_error"
  | "upstream_unavailable";

/** JSON-Antwort mit CORS-Headern. */
export function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS_HEADERS, "Content-Type": "application/json; charset=utf-8" },
  });
}

/** Fehlerantwort `{ error: <code> }`. */
export function errorResponse(status: number, code: ErrorCode): Response {
  return jsonResponse(status, { error: code });
}

/** Antwort auf den CORS-Preflight (OPTIONS). */
export function preflightResponse(): Response {
  return new Response(null, { status: 204, headers: { ...CORS_HEADERS } });
}

/** Liest das Bearer-Token aus dem Authorization-Header (oder `null`). */
export function bearerToken(request: Request): string | null {
  const header = request.headers.get("Authorization") ?? "";
  const match = /^Bearer\s+(\S+)\s*$/i.exec(header);
  return match ? match[1] : null;
}

/** Größe, ab der ein Anfragekörper abgelehnt wird (die Funktionen erwarten nur winzige JSON-Objekte). */
export const MAX_REQUEST_BODY_BYTES = 4096;

/** Liest einen kleinen JSON-Körper; `undefined` bei fehlendem, zu großem oder ungültigem JSON. */
export async function readSmallJson(request: Request): Promise<unknown | undefined> {
  const declaredLength = Number(request.headers.get("Content-Length") ?? "0");
  if (declaredLength > MAX_REQUEST_BODY_BYTES) {
    await request.body?.cancel();
    return undefined;
  }
  const text = await request.text();
  if (text.length === 0 || new TextEncoder().encode(text).length > MAX_REQUEST_BODY_BYTES) {
    return undefined;
  }
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Prüft, ob ein Wert eine UUID im Textformat ist. */
export function isUuid(value: unknown): value is string {
  return typeof value === "string" && UUID_PATTERN.test(value);
}
