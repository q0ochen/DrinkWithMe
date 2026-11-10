/**
 * Schlanke Zugriffe auf die Supabase-APIs per fetch (keine Fremdpakete):
 * - GoTrue `/auth/v1/user` zum Prüfen des Nutzer-JWT,
 * - GoTrue Admin-API zum Löschen eines Kontos,
 * - PostgREST `/rest/v1` mit Server-Rechten (Service-Rolle).
 */

import type { SupabaseServerConfig } from "./config.ts";

/** Fehlerhafte Antwort einer Supabase-API (z. B. 500 oder unerwarteter Status). */
export class SupabaseApiError extends Error {
  constructor(readonly status: number, readonly detail: string) {
    super(`Supabase-API antwortete mit ${status}`);
    this.name = "SupabaseApiError";
  }
}

/**
 * Header für Server-Aufrufe. Alte Service-Role-Keys sind JWTs und gehören zusätzlich in `Authorization`;
 * neue Secret Keys (`sb_secret_…`) sind keine JWTs und werden nur als `apikey` gesendet.
 */
export function serverHeaders(serviceKey: string): Record<string, string> {
  const headers: Record<string, string> = { apikey: serviceKey };
  if (serviceKey.split(".").length === 3) {
    headers["Authorization"] = `Bearer ${serviceKey}`;
  }
  return headers;
}

// ---------------------------------------------------------------------------------------------------------
// JWT prüfen
// ---------------------------------------------------------------------------------------------------------

/**
 * Ergebnis der Token-Prüfung:
 * - `valid`: gültige Sitzung des Nutzers `userId`,
 * - `user_not_found`: korrekt signiert, aber das Konto existiert nicht mehr (schon gelöscht),
 * - `invalid`: abgelaufen, gefälscht oder widerrufen.
 */
export type TokenCheck =
  | { kind: "valid"; userId: string }
  | { kind: "user_not_found" }
  | { kind: "invalid" };

/** Port: prüft ein Nutzer-JWT. */
export interface AccessTokenVerifier {
  verify(accessToken: string): Promise<TokenCheck>;
}

/**
 * Prüft das JWT beim Auth-Server (GoTrue). Das funktioniert mit alten (HS256) und neuen (asymmetrischen)
 * JWT-Signaturschlüsseln und erkennt zusätzlich widerrufene Sitzungen und gelöschte Konten.
 */
export class GoTrueTokenVerifier implements AccessTokenVerifier {
  constructor(private readonly config: SupabaseServerConfig, private readonly fetchFn: typeof fetch) {}

  async verify(accessToken: string): Promise<TokenCheck> {
    const response = await this.fetchFn(`${this.config.url}/auth/v1/user`, {
      method: "GET",
      headers: { apikey: this.config.serviceKey, Authorization: `Bearer ${accessToken}` },
    });
    const text = await response.text();
    if (response.ok) {
      const user = parseJson(text) as { id?: unknown } | undefined;
      return typeof user?.id === "string" && user.id.length > 0
        ? { kind: "valid", userId: user.id }
        : { kind: "invalid" };
    }
    if (response.status >= 500) {
      throw new SupabaseApiError(response.status, text.slice(0, 200));
    }
    const error = parseJson(text) as { error_code?: unknown } | undefined;
    if (error?.error_code === "user_not_found" || response.status === 404) {
      return { kind: "user_not_found" };
    }
    return { kind: "invalid" };
  }
}

// ---------------------------------------------------------------------------------------------------------
// Konto löschen
// ---------------------------------------------------------------------------------------------------------

/** Port: löscht Auth-Nutzer (Admin-Rechte). */
export interface UserAdmin {
  /** Löscht den Nutzer endgültig; `not_found`, wenn er schon gelöscht war. */
  deleteUser(userId: string): Promise<"deleted" | "not_found">;
}

/** Admin-API von GoTrue: `DELETE /auth/v1/admin/users/{id}` (hartes Löschen, kein Soft-Delete). */
export class GoTrueUserAdmin implements UserAdmin {
  constructor(private readonly config: SupabaseServerConfig, private readonly fetchFn: typeof fetch) {}

  async deleteUser(userId: string): Promise<"deleted" | "not_found"> {
    const response = await this.fetchFn(
      `${this.config.url}/auth/v1/admin/users/${encodeURIComponent(userId)}`,
      {
        method: "DELETE",
        headers: { ...serverHeaders(this.config.serviceKey), "Content-Type": "application/json" },
        body: JSON.stringify({ should_soft_delete: false }),
      },
    );
    const text = await response.text();
    if (response.ok) return "deleted";
    if (response.status === 404) return "not_found";
    throw new SupabaseApiError(response.status, text.slice(0, 200));
  }
}

// ---------------------------------------------------------------------------------------------------------
// PostgREST
// ---------------------------------------------------------------------------------------------------------

/** Minimaler PostgREST-Client mit Server-Rechten (umgeht RLS – nur für geprüfte Abfragen verwenden). */
export class PostgrestClient {
  constructor(private readonly config: SupabaseServerConfig, private readonly fetchFn: typeof fetch) {}

  /**
   * Führt eine Anfrage aus und liefert das JSON-Ergebnis (bei 204 `null`).
   * @param path Pfad relativ zu /rest/v1, z. B. `drink_events?id=eq.…`
   */
  async request<T>(
    method: "GET" | "POST" | "PATCH" | "DELETE",
    path: string,
    options: { body?: unknown; prefer?: string } = {},
  ): Promise<T> {
    const headers: Record<string, string> = {
      ...serverHeaders(this.config.serviceKey),
      Accept: "application/json",
    };
    if (options.body !== undefined) headers["Content-Type"] = "application/json";
    if (options.prefer) headers["Prefer"] = options.prefer;

    const response = await this.fetchFn(`${this.config.url}/rest/v1/${path}`, {
      method,
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    });
    const text = await response.text();
    if (!response.ok) {
      throw new SupabaseApiError(response.status, text.slice(0, 200));
    }
    return (text.length === 0 ? null : JSON.parse(text)) as T;
  }

  /** Ruft eine Datenbankfunktion auf (`POST /rest/v1/rpc/<name>`). */
  rpc<T>(functionName: string, args: Record<string, unknown>): Promise<T> {
    return this.request<T>("POST", `rpc/${encodeURIComponent(functionName)}`, { body: args });
  }
}

/** Werte für PostgREST-Filter wie `in.(a,b)` – nur UUIDs, daher ohne weiteres Maskieren sicher. */
export function uuidList(ids: readonly string[]): string {
  for (const id of ids) {
    if (!/^[0-9a-f-]{36}$/i.test(id)) throw new TypeError("Keine UUID");
  }
  return `(${ids.join(",")})`;
}

function parseJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}
