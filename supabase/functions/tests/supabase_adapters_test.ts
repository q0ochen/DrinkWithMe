/**
 * Tests der Supabase-Anbindung mit einer fetch-Attrappe: PostgREST-Abfragen der Ports von notify-friends,
 * Token-Prüfung bei GoTrue, Admin-Löschung, Server-Header und Konfiguration.
 */

import { assert, assertEquals, assertRejects, assertThrows } from "@std/assert";
import { ConfigError, readSupabaseServerConfig, readVapidConfig } from "../_shared/config.ts";
import {
  GoTrueTokenVerifier,
  GoTrueUserAdmin,
  PostgrestClient,
  serverHeaders,
  SupabaseApiError,
  uuidList,
} from "../_shared/supabase_api.ts";
import {
  PostgrestDrinkEventClaimer,
  PostgrestFriendshipReader,
  PostgrestProfileReader,
  PostgrestRateLimiter,
  PostgrestSubscriptionStore,
} from "../notify-friends/supabase_adapters.ts";
import { ANNA, BEN, CLARA, EVENT_ID, fakeFetch, FixedClock, json } from "./helpers/fakes.ts";

const CONFIG = {
  url: "https://abcd.supabase.co",
  serviceKey: "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoic2VydmljZV9yb2xlIn0.sig",
};

function env(values: Record<string, string>) {
  return { get: (name: string) => values[name] };
}

Deno.test("claimForNotification: ein bedingtes PATCH (Autor, notified_at leer, jünger als Grenze)", async () => {
  const clock = new FixedClock();
  const http = fakeFetch(() => json(200, [{ id: EVENT_ID, author_id: ANNA, name: "Apfelschorle", volume_ml: 500 }]));
  const claimer = new PostgrestDrinkEventClaimer(new PostgrestClient(CONFIG, http.fetch), clock);
  const createdAfter = new Date("2026-11-10T11:50:00.000Z");

  const event = await claimer.claimForNotification(EVENT_ID, ANNA, createdAfter);

  assertEquals(event, { id: EVENT_ID, authorId: ANNA, name: "Apfelschorle", volumeMl: 500 });
  const request = http.requests[0];
  assertEquals(request.method, "PATCH");
  const url = new URL(request.url);
  assertEquals(url.origin + url.pathname, "https://abcd.supabase.co/rest/v1/drink_events");
  assertEquals(url.searchParams.get("id"), `eq.${EVENT_ID}`);
  assertEquals(url.searchParams.get("author_id"), `eq.${ANNA}`);
  assertEquals(url.searchParams.get("notified_at"), "is.null");
  assertEquals(url.searchParams.get("created_at"), "gt.2026-11-10T11:50:00.000Z");
  assertEquals(JSON.parse(request.body as string), { notified_at: "2026-11-10T12:00:00.000Z" });
  assertEquals(request.headers.get("Prefer"), "return=representation");
  assertEquals(request.headers.get("apikey"), CONFIG.serviceKey);
  assertEquals(request.headers.get("Authorization"), `Bearer ${CONFIG.serviceKey}`);
});

Deno.test("claimForNotification: keine Zeile → null; ungültige IDs → kein Netzwerkzugriff", async () => {
  const http = fakeFetch(() => json(200, []));
  const claimer = new PostgrestDrinkEventClaimer(new PostgrestClient(CONFIG, http.fetch), new FixedClock());
  assertEquals(await claimer.claimForNotification(EVENT_ID, ANNA, new Date()), null);
  await assertRejects(() => claimer.claimForNotification("1,id.neq.x", ANNA, new Date()), TypeError);
  assertEquals(http.requests.length, 1);
});

Deno.test("friendIdsOf: Freunde in beiden Spalten, als jeweils andere ID", async () => {
  const http = fakeFetch(() =>
    json(200, [{ user_a: ANNA, user_b: BEN }, { user_a: "00000000-0000-4000-8000-000000000001", user_b: ANNA }])
  );
  const reader = new PostgrestFriendshipReader(new PostgrestClient(CONFIG, http.fetch));
  assertEquals(await reader.friendIdsOf(ANNA), [BEN, "00000000-0000-4000-8000-000000000001"]);
  const url = new URL(http.requests[0].url);
  assertEquals(url.pathname, "/rest/v1/friendships");
  assertEquals(url.searchParams.get("or"), `(user_a.eq.${ANNA},user_b.eq.${ANNA})`);
});

Deno.test("displayNameOf: Name oder null", async () => {
  let rows: unknown = [{ display_name: "Anna" }];
  const http = fakeFetch(() => json(200, rows));
  const reader = new PostgrestProfileReader(new PostgrestClient(CONFIG, http.fetch));
  assertEquals(await reader.displayNameOf(ANNA), "Anna");
  rows = [];
  assertEquals(await reader.displayNameOf(ANNA), null);
  assertEquals(new URL(http.requests[0].url).searchParams.get("id"), `eq.${ANNA}`);
});

Deno.test("subscriptionsOf: in Blöcken zu 100 IDs; remove löscht genau ein Abo", async () => {
  const http = fakeFetch((request) =>
    request.method === "DELETE"
      ? new Response(null, { status: 204 })
      : json(200, [{ id: "s1", user_id: BEN, endpoint: "https://ntfy.sh/x", p256dh: "p", auth: "a" }])
  );
  const store = new PostgrestSubscriptionStore(new PostgrestClient(CONFIG, http.fetch));
  const ids = Array.from({ length: 150 }, (_, i) => `00000000-0000-4000-8000-${String(i).padStart(12, "0")}`);
  const result = await store.subscriptionsOf(ids);
  assertEquals(http.requests.length, 2);
  assertEquals(result.length, 2);
  assertEquals(result[0], { id: "s1", userId: BEN, endpoint: "https://ntfy.sh/x", p256dh: "p", auth: "a" });
  const first = new URL(http.requests[0].url).searchParams.get("user_id") ?? "";
  assert(first.startsWith("in.(") && first.split(",").length === 100);

  await store.remove("11111111-1111-4111-8111-111111111111");
  const del = http.requests[2];
  assertEquals(del.method, "DELETE");
  assertEquals(new URL(del.url).searchParams.get("id"), "eq.11111111-1111-4111-8111-111111111111");
});

Deno.test("RateLimiter: ruft public.consume_rate_limit mit benannten Parametern auf", async () => {
  let allowed = true;
  const http = fakeFetch(() => json(200, allowed));
  const limiter = new PostgrestRateLimiter(new PostgrestClient(CONFIG, http.fetch));
  assertEquals(await limiter.consume(ANNA, "notify_friends", 60, 3600), true);
  allowed = false;
  assertEquals(await limiter.consume(ANNA, "notify_friends", 60, 3600), false);
  const request = http.requests[0];
  assertEquals(request.method, "POST");
  assertEquals(new URL(request.url).pathname, "/rest/v1/rpc/consume_rate_limit");
  assertEquals(JSON.parse(request.body as string), {
    p_user_id: ANNA,
    p_action: "notify_friends",
    p_max_count: 60,
    p_window_seconds: 3600,
  });
});

Deno.test("PostgREST-Fehler werden als SupabaseApiError weitergereicht", async () => {
  const http = fakeFetch(() => json(500, { message: "kaputt" }));
  const reader = new PostgrestProfileReader(new PostgrestClient(CONFIG, http.fetch));
  const error = await assertRejects(() => reader.displayNameOf(ANNA), SupabaseApiError);
  assertEquals(error.status, 500);
});

Deno.test("Server-Header: JWT-Schlüssel auch als Bearer, neue Secret Keys nur als apikey", () => {
  assertEquals(serverHeaders("a.b.c"), { apikey: "a.b.c", Authorization: "Bearer a.b.c" });
  assertEquals(serverHeaders("sb_secret_abc"), { apikey: "sb_secret_abc" });
});

Deno.test("uuidList akzeptiert nur UUIDs", () => {
  assertEquals(uuidList([ANNA, BEN]), `(${ANNA},${BEN})`);
  assertThrows(() => uuidList([ANNA, "x),or=(id.neq.0"]), TypeError);
});

Deno.test("GoTrueTokenVerifier: gültig, ungültig, Konto gelöscht, Serverfehler", async () => {
  let response = () => json(200, { id: CLARA, email: "clara@example.org" });
  const http = fakeFetch(() => response());
  const verifier = new GoTrueTokenVerifier(CONFIG, http.fetch);

  assertEquals(await verifier.verify("nutzer-jwt"), { kind: "valid", userId: CLARA });
  const request = http.requests[0];
  assertEquals(request.url, "https://abcd.supabase.co/auth/v1/user");
  assertEquals(request.headers.get("Authorization"), "Bearer nutzer-jwt");
  assertEquals(request.headers.get("apikey"), CONFIG.serviceKey);

  response = () => json(401, { code: 401, error_code: "bad_jwt", msg: "invalid JWT" });
  assertEquals(await verifier.verify("x"), { kind: "invalid" });
  response = () => json(403, { code: 403, error_code: "session_not_found", msg: "Session not found" });
  assertEquals(await verifier.verify("x"), { kind: "invalid" });
  response = () =>
    json(403, { code: 403, error_code: "user_not_found", msg: "User from sub claim in JWT does not exist" });
  assertEquals(await verifier.verify("x"), { kind: "user_not_found" });
  response = () => json(200, { email: "ohne-id@example.org" });
  assertEquals(await verifier.verify("x"), { kind: "invalid" });
  response = () => json(503, { msg: "nicht erreichbar" });
  await assertRejects(() => verifier.verify("x"), SupabaseApiError);
});

Deno.test("GoTrueUserAdmin: hartes Löschen über die Admin-API, 404 = schon gelöscht", async () => {
  let status = 200;
  const http = fakeFetch(() => json(status, {}));
  const admin = new GoTrueUserAdmin(CONFIG, http.fetch);
  assertEquals(await admin.deleteUser(ANNA), "deleted");
  const request = http.requests[0];
  assertEquals(request.method, "DELETE");
  assertEquals(request.url, `https://abcd.supabase.co/auth/v1/admin/users/${ANNA}`);
  assertEquals(JSON.parse(request.body as string), { should_soft_delete: false });
  assertEquals(request.headers.get("Authorization"), `Bearer ${CONFIG.serviceKey}`);
  status = 404;
  assertEquals(await admin.deleteUser(ANNA), "not_found");
  status = 500;
  await assertRejects(() => admin.deleteUser(ANNA), SupabaseApiError);
});

Deno.test("Konfiguration: Service-Role-Key oder neue Secret Keys, fehlende Werte → ConfigError", () => {
  assertEquals(
    readSupabaseServerConfig(env({ SUPABASE_URL: "https://abcd.supabase.co/", SUPABASE_SERVICE_ROLE_KEY: "k.e.y" })),
    { url: "https://abcd.supabase.co", serviceKey: "k.e.y" },
  );
  assertEquals(
    readSupabaseServerConfig(
      env({ SUPABASE_URL: "http://kong:8000", SUPABASE_SECRET_KEYS: '{"default":"sb_secret_123"}' }),
    ).serviceKey,
    "sb_secret_123",
  );
  assertThrows(
    () => readSupabaseServerConfig(env({ SUPABASE_URL: "https://x" })),
    ConfigError,
    "SUPABASE_SERVICE_ROLE_KEY",
  );
  assertThrows(() => readSupabaseServerConfig(env({ SUPABASE_SERVICE_ROLE_KEY: "k" })), ConfigError, "SUPABASE_URL");
  assertThrows(
    () => readSupabaseServerConfig(env({ SUPABASE_URL: "ftp://x", SUPABASE_SERVICE_ROLE_KEY: "k" })),
    ConfigError,
  );
});

Deno.test("Konfiguration: VAPID-Secrets vollständig oder ConfigError mit allen fehlenden Namen", () => {
  assertEquals(
    readVapidConfig(env({ VAPID_PUBLIC_KEY: "pub", VAPID_PRIVATE_KEY: "priv", VAPID_SUBJECT: "mailto:a@b.de" })),
    { publicKey: "pub", privateKey: "priv", subject: "mailto:a@b.de" },
  );
  assertThrows(
    () => readVapidConfig(env({ VAPID_PUBLIC_KEY: "pub" })),
    ConfigError,
    "VAPID_PRIVATE_KEY, VAPID_SUBJECT",
  );
});
