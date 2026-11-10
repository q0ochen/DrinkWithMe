/**
 * HTTP-Schicht von delete-account: `POST` mit `Authorization: Bearer <Nutzer-JWT>` löscht das eigene Konto.
 * Profil, Freundschaften, Anfragen, Einladungs-Links, geteilte Ereignisse, Push-Abos und Limit-Zähler folgen
 * per ON DELETE CASCADE. Die lokale Trinkhistorie auf dem Gerät bleibt unberührt.
 *
 * Idempotent: Ist das Konto bereits gelöscht (gültig signiertes JWT, aber Nutzer existiert nicht mehr),
 * lautet die Antwort ebenfalls 200 – es wird dabei nichts gelöscht.
 *
 * Antworten: 200 { deleted: true } · 401 unauthorized · 405 method_not_allowed · 500 server_misconfigured |
 * internal_error
 */

import { ConfigError } from "../_shared/config.ts";
import { bearerToken, errorResponse, jsonResponse, preflightResponse } from "../_shared/http.ts";
import { describeError, type Logger } from "../_shared/logger.ts";
import type { AccessTokenVerifier, UserAdmin } from "../_shared/supabase_api.ts";

export interface DeleteAccountDeps {
  verifier: AccessTokenVerifier;
  admin: UserAdmin;
}

/** Erzeugt den Request-Handler. `resolveDeps` darf mit ConfigError scheitern (fehlende Umgebungsvariablen). */
export function createDeleteAccountHandler(
  resolveDeps: () => Promise<DeleteAccountDeps>,
  logger: Logger,
): (request: Request) => Promise<Response> {
  return async (request) => {
    if (request.method === "OPTIONS") return preflightResponse();
    if (request.method !== "POST") return errorResponse(405, "method_not_allowed");

    const token = bearerToken(request);
    if (!token) return errorResponse(401, "unauthorized");

    try {
      const deps = await resolveDeps();
      const check = await deps.verifier.verify(token);
      if (check.kind === "user_not_found") {
        return jsonResponse(200, { deleted: true });
      }
      if (check.kind !== "valid") return errorResponse(401, "unauthorized");

      const result = await deps.admin.deleteUser(check.userId);
      logger.info("Konto gelöscht", { ergebnis: result });
      return jsonResponse(200, { deleted: true });
    } catch (error) {
      if (error instanceof ConfigError) {
        logger.error("Konfiguration unvollständig", { fehler: error.message });
        return errorResponse(500, "server_misconfigured");
      }
      logger.error("Konto konnte nicht gelöscht werden", { fehler: describeError(error) });
      return errorResponse(500, "internal_error");
    }
  };
}
