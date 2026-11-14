/** Tests von delete-account: nur das eigene Konto, idempotent, saubere Fehlercodes. */

import { assertEquals } from "@std/assert";
import { ConfigError } from "../_shared/config.ts";
import { SupabaseApiError } from "../_shared/supabase_api.ts";
import { createDeleteAccountHandler } from "../delete-account/handler.ts";
import { ANNA, BEN, FakeUserAdmin, FakeVerifier, RecordingLogger } from "./helpers/fakes.ts";

function setup() {
  const verifier = new FakeVerifier();
  verifier.tokens.set("token-anna", { kind: "valid", userId: ANNA });
  verifier.tokens.set("token-geloescht", { kind: "user_not_found" });
  const admin = new FakeUserAdmin();
  admin.users.add(ANNA);
  admin.users.add(BEN);
  const logger = new RecordingLogger();
  const handler = createDeleteAccountHandler(() => Promise.resolve({ verifier, admin }), logger);
  return { verifier, admin, logger, handler };
}

function post(token: string | null): Request {
  return new Request("https://example.supabase.co/functions/v1/delete-account", {
    method: "POST",
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
}

Deno.test("Löscht genau das Konto aus dem JWT", async () => {
  const { handler, admin } = setup();
  const response = await handler(post("token-anna"));
  assertEquals(response.status, 200);
  assertEquals(await response.json(), { deleted: true });
  assertEquals(admin.deleted, [ANNA]);
  assertEquals([...admin.users], [BEN]);
  assertEquals(response.headers.get("Access-Control-Allow-Origin"), "*");
});

Deno.test("Idempotent: zweiter Aufruf (Konto schon weg) antwortet ebenfalls 200", async () => {
  const { handler, admin } = setup();
  assertEquals((await handler(post("token-anna"))).status, 200);
  assertEquals((await handler(post("token-anna"))).status, 200, "Admin-API meldet not_found");
  const response = await handler(post("token-geloescht"));
  assertEquals(response.status, 200, "Auth-Server meldet user_not_found");
  assertEquals(await response.json(), { deleted: true });
  assertEquals(admin.deleted, [ANNA, ANNA], "Bei user_not_found wird nichts gelöscht");
});

Deno.test("Ohne oder mit ungültigem JWT: 401, nichts wird gelöscht", async () => {
  const { handler, admin } = setup();
  for (const token of [null, "token-falsch"]) {
    const response = await handler(post(token));
    assertEquals(response.status, 401);
    assertEquals(await response.json(), { error: "unauthorized" });
  }
  assertEquals(admin.deleted, []);
});

Deno.test("Nur POST; OPTIONS für CORS", async () => {
  const { handler } = setup();
  assertEquals((await handler(new Request("https://x/delete-account", { method: "DELETE" }))).status, 405);
  assertEquals((await handler(new Request("https://x/delete-account", { method: "OPTIONS" }))).status, 204);
});

Deno.test("Fehler der Admin-API: 500 internal_error; fehlende Konfiguration: 500 server_misconfigured", async () => {
  const { handler, admin, logger } = setup();
  admin.failWith = new SupabaseApiError(500, "kaputt");
  const response = await handler(post("token-anna"));
  assertEquals(response.status, 500);
  assertEquals(await response.json(), { error: "internal_error" });
  assertEquals(logger.entries.at(-1)?.level, "error");

  const misconfigured = createDeleteAccountHandler(
    () => Promise.reject(new ConfigError("Fehlende Umgebungsvariablen: SUPABASE_URL")),
    logger,
  );
  const failed = await misconfigured(post("token-anna"));
  assertEquals(failed.status, 500);
  assertEquals(await failed.json(), { error: "server_misconfigured" });
});
