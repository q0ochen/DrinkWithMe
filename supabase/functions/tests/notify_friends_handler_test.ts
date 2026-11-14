/** Tests der HTTP-Schicht von notify-friends (Statuscodes, CORS, JWT-Prüfung, Eingabeprüfung). */

import { assertEquals } from "@std/assert";
import { ConfigError } from "../_shared/config.ts";
import { createNotifyFriendsHandler } from "../notify-friends/handler.ts";
import type { NotifyFriendsUseCase, NotifyOutcome } from "../notify-friends/service.ts";
import { ANNA, EVENT_ID, FakeVerifier, RecordingLogger } from "./helpers/fakes.ts";

class FakeUseCase implements NotifyFriendsUseCase {
  readonly calls: Array<{ callerId: string; eventId: string }> = [];
  outcome: NotifyOutcome = { kind: "sent", recipients: 2, delivered: 1, failed: 0, removed: 1 };
  notify(callerId: string, eventId: string): Promise<NotifyOutcome> {
    this.calls.push({ callerId, eventId });
    return Promise.resolve(this.outcome);
  }
}

function setup() {
  const verifier = new FakeVerifier();
  verifier.tokens.set("token-anna", { kind: "valid", userId: ANNA });
  verifier.tokens.set("token-geloescht", { kind: "user_not_found" });
  const useCase = new FakeUseCase();
  const logger = new RecordingLogger();
  const handler = createNotifyFriendsHandler(() => Promise.resolve({ verifier, useCase }), logger);
  return { verifier, useCase, logger, handler };
}

function post(body: unknown, token: string | null = "token-anna"): Request {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (token) headers["Authorization"] = `Bearer ${token}`;
  return new Request("https://example.supabase.co/functions/v1/notify-friends", {
    method: "POST",
    headers,
    body: typeof body === "string" ? body : JSON.stringify(body),
  });
}

Deno.test("Erfolg: 200 mit Zählern, Aufruf mit Nutzer-ID aus dem JWT, CORS-Header", async () => {
  const { handler, useCase } = setup();
  const response = await handler(post({ eventId: EVENT_ID.toUpperCase() }));
  assertEquals(response.status, 200);
  assertEquals(await response.json(), { recipients: 2, delivered: 1, failed: 0, removed: 1 });
  assertEquals(response.headers.get("Access-Control-Allow-Origin"), "*");
  assertEquals(useCase.calls, [{ callerId: ANNA, eventId: EVENT_ID }]);
});

Deno.test("Limit überschritten: 429 rate_limited", async () => {
  const { handler, useCase } = setup();
  useCase.outcome = { kind: "rate_limited" };
  const response = await handler(post({ eventId: EVENT_ID }));
  assertEquals(response.status, 429);
  assertEquals(await response.json(), { error: "rate_limited" });
});

Deno.test("Ereignis unbekannt/fremd/zu alt/schon benachrichtigt: 404 event_not_found", async () => {
  const { handler, useCase } = setup();
  useCase.outcome = { kind: "event_not_found" };
  const response = await handler(post({ eventId: EVENT_ID }));
  assertEquals(response.status, 404);
  assertEquals(await response.json(), { error: "event_not_found" });
});

Deno.test("Ohne, mit ungültigem oder zu gelöschtem Konto gehörendem JWT: 401", async () => {
  const { handler, useCase } = setup();
  for (const token of [null, "token-falsch", "token-geloescht"]) {
    const response = await handler(post({ eventId: EVENT_ID }, token));
    assertEquals(response.status, 401, `Token ${token}`);
    assertEquals(await response.json(), { error: "unauthorized" });
  }
  const basic = await handler(
    new Request("https://x/notify-friends", { method: "POST", headers: { Authorization: "Basic abc" } }),
  );
  assertEquals(basic.status, 401);
  assertEquals(useCase.calls.length, 0);
});

Deno.test("Ungültiger Körper: 400 invalid_request", async () => {
  const { handler, useCase } = setup();
  for (const body of ["", "kein json", { eventId: "123" }, { eventId: 5 }, {}, [EVENT_ID], "x".repeat(5000)]) {
    const response = await handler(post(body));
    assertEquals(response.status, 400, JSON.stringify(body).slice(0, 40));
    assertEquals(await response.json(), { error: "invalid_request" });
  }
  assertEquals(useCase.calls.length, 0);
});

Deno.test("Nur POST; OPTIONS beantwortet den CORS-Preflight", async () => {
  const { handler } = setup();
  const get = await handler(new Request("https://x/notify-friends", { method: "GET" }));
  assertEquals(get.status, 405);
  const options = await handler(new Request("https://x/notify-friends", { method: "OPTIONS" }));
  assertEquals(options.status, 204);
  assertEquals(options.headers.get("Access-Control-Allow-Methods"), "POST, OPTIONS");
  assertEquals(options.headers.get("Access-Control-Allow-Headers")?.includes("authorization"), true);
});

Deno.test("Fehlende Secrets: 500 server_misconfigured; sonstige Fehler: 500 internal_error", async () => {
  const logger = new RecordingLogger();
  const misconfigured = createNotifyFriendsHandler(
    () => Promise.reject(new ConfigError("Fehlende Secrets: VAPID_PRIVATE_KEY")),
    logger,
  );
  const response = await misconfigured(post({ eventId: EVENT_ID }));
  assertEquals(response.status, 500);
  assertEquals(await response.json(), { error: "server_misconfigured" });
  assertEquals(logger.entries[0].level, "error");

  const { verifier } = setup();
  const failing = createNotifyFriendsHandler(
    () =>
      Promise.resolve({
        verifier,
        useCase: { notify: () => Promise.reject(new Error("Datenbank weg")) },
      }),
    logger,
  );
  const failed = await failing(post({ eventId: EVENT_ID }));
  assertEquals(failed.status, 500);
  assertEquals(await failed.json(), { error: "internal_error" });
});
