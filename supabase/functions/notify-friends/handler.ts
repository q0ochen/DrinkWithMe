/**
 * HTTP-Schicht von notify-friends: `POST { "eventId": "<uuid>" }` mit `Authorization: Bearer <Nutzer-JWT>`.
 *
 * Antworten:
 *   200 { recipients, delivered, failed, removed }  – verschickt (auch 0 Empfänger)
 *   400 { error: "invalid_request" }                – Körper fehlt oder eventId ist keine UUID
 *   401 { error: "unauthorized" }                   – kein/ungültiges JWT
 *   404 { error: "event_not_found" }                – unbekannt, fremd, älter als 10 min oder schon benachrichtigt
 *   405 { error: "method_not_allowed" }
 *   429 { error: "rate_limited" }                   – mehr als 60 Aufrufe pro Stunde
 *   500 { error: "server_misconfigured" | "internal_error" }
 */

import { ConfigError } from "../_shared/config.ts";
import { bearerToken, errorResponse, isUuid, jsonResponse, preflightResponse, readSmallJson } from "../_shared/http.ts";
import { describeError, type Logger } from "../_shared/logger.ts";
import type { AccessTokenVerifier } from "../_shared/supabase_api.ts";
import type { NotifyFriendsUseCase } from "./service.ts";

/** Abhängigkeiten des Handlers; `resolve` liefert sie bei Bedarf (z. B. nach dem Laden der VAPID-Schlüssel). */
export interface NotifyFriendsHandlerDeps {
  verifier: AccessTokenVerifier;
  useCase: NotifyFriendsUseCase;
}

/** Erzeugt den Request-Handler. `resolveDeps` darf mit ConfigError scheitern (fehlende Secrets). */
export function createNotifyFriendsHandler(
  resolveDeps: () => Promise<NotifyFriendsHandlerDeps>,
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
      if (check.kind !== "valid") return errorResponse(401, "unauthorized");

      const body = await readSmallJson(request);
      const eventId = (body as { eventId?: unknown } | undefined)?.eventId;
      if (!isUuid(eventId)) return errorResponse(400, "invalid_request");

      const outcome = await deps.useCase.notify(check.userId, eventId.toLowerCase());
      switch (outcome.kind) {
        case "rate_limited":
          return errorResponse(429, "rate_limited");
        case "event_not_found":
          return errorResponse(404, "event_not_found");
        case "sent":
          return jsonResponse(200, {
            recipients: outcome.recipients,
            delivered: outcome.delivered,
            failed: outcome.failed,
            removed: outcome.removed,
          });
      }
    } catch (error) {
      if (error instanceof ConfigError) {
        logger.error("Konfiguration unvollständig", { fehler: error.message });
        return errorResponse(500, "server_misconfigured");
      }
      logger.error("Unerwarteter Fehler", { fehler: describeError(error) });
      return errorResponse(500, "internal_error");
    }
  };
}
