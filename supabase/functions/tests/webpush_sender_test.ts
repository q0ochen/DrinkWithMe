/** Tests des WebPushSender: Header nach RFC 8030/8291/8292, Ergebnis je Statuscode, Schutz vor internen Zielen. */

import { assert, assertEquals, assertMatch } from "@std/assert";
import { importVapidCredentials } from "../_shared/webpush/vapid.ts";
import { isAllowedPushEndpoint, type PushTarget, WebPushSender } from "../_shared/webpush/sender.ts";
import { utf8 } from "../_shared/bytes.ts";
import { fakeFetch, FixedClock } from "./helpers/fakes.ts";
import { decryptAsReceiver, RFC8291 } from "./helpers/rfc8291.ts";

const target: PushTarget = {
  endpoint: "https://ntfy.example.org/upAbC123?up=1",
  p256dh: RFC8291.uaPublic,
  auth: RFC8291.authSecret,
};

async function senderWith(respond: () => Response | Promise<Response>) {
  const credentials = await importVapidCredentials(RFC8291.asPublic, RFC8291.asPrivate, "mailto:betrieb@example.org");
  const http = fakeFetch(respond);
  return { sender: new WebPushSender({ credentials, fetch: http.fetch, clock: new FixedClock() }), http };
}

Deno.test("Versand: POST an den Endpoint mit aes128gcm, TTL, Urgency und VAPID; Inhalt entschlüsselbar", async () => {
  const { sender, http } = await senderWith(() => new Response(null, { status: 201 }));
  const payload = utf8(JSON.stringify({ type: "drink", title: "Anna trinkt gerade" }));
  const result = await sender.send(target, payload, { ttlSeconds: 86400, urgency: "normal" });

  assertEquals(result, { kind: "delivered", status: 201 });
  assertEquals(http.requests.length, 1);
  const request = http.requests[0];
  assertEquals(request.url, target.endpoint);
  assertEquals(request.method, "POST");
  assertEquals(request.redirect, "manual");
  assertEquals(request.headers.get("Content-Encoding"), "aes128gcm");
  assertEquals(request.headers.get("Content-Type"), "application/octet-stream");
  assertEquals(request.headers.get("TTL"), "86400");
  assertEquals(request.headers.get("Urgency"), "normal");
  assertMatch(request.headers.get("Authorization") ?? "", /^vapid t=[\w-]+\.[\w-]+\.[\w-]+, k=[\w-]{87}$/);

  assert(request.body instanceof Uint8Array);
  const decrypted = await decryptAsReceiver(request.body, RFC8291.uaPrivate, RFC8291.uaPublic, RFC8291.authSecret);
  assertEquals(new TextDecoder().decode(decrypted.plaintext), new TextDecoder().decode(payload));
});

Deno.test("Versand: 404 und 410 bedeuten „Abo existiert nicht mehr“", async () => {
  for (const status of [404, 410]) {
    const { sender } = await senderWith(() => new Response("weg", { status }));
    assertEquals(await sender.send(target, utf8("x"), { ttlSeconds: 60 }), { kind: "gone", status });
  }
});

Deno.test("Versand: andere Fehler und Netzwerkfehler behalten das Abo", async () => {
  for (const status of [400, 401, 403, 413, 429, 500, 503, 301]) {
    const { sender } = await senderWith(() => new Response(null, { status }));
    const result = await sender.send(target, utf8("x"), { ttlSeconds: 60 });
    assertEquals(result.kind, "failed", `Status ${status}`);
  }
  const { sender } = await senderWith(() => Promise.reject(new TypeError("Verbindung abgelehnt")));
  assertEquals((await sender.send(target, utf8("x"), { ttlSeconds: 60 })).kind, "failed");
});

Deno.test("Versand: kaputte Schlüssel im Abo → invalid, ohne Netzwerkzugriff", async () => {
  const { sender, http } = await senderWith(() => new Response(null, { status: 201 }));
  const result = await sender.send({ ...target, p256dh: "AAAA" }, utf8("x"), { ttlSeconds: 60 });
  assertEquals(result, { kind: "invalid", reason: "receiver_keys_invalid" });
  const result2 = await sender.send({ ...target, auth: "%%%" }, utf8("x"), { ttlSeconds: 60 });
  assertEquals(result2.kind, "invalid");
  assertEquals(http.requests.length, 0);
});

Deno.test("Versand: interne oder unsichere Endpoints werden nicht angesprochen (SSRF-Schutz)", async () => {
  const { sender, http } = await senderWith(() => new Response(null, { status: 201 }));
  const result = await sender.send({ ...target, endpoint: "https://127.0.0.1/x" }, utf8("x"), { ttlSeconds: 60 });
  assertEquals(result, { kind: "invalid", reason: "endpoint_not_allowed" });
  assertEquals(http.requests.length, 0);
});

Deno.test("Endpoint-Prüfung: öffentliche https-Hosts erlaubt, alles andere nicht", () => {
  for (
    const ok of [
      "https://ntfy.sh/upABC",
      "https://fcm.googleapis.com/fcm/send/abc",
      "https://updates.push.services.mozilla.com/wpush/v2/x",
      "https://web.push.apple.com/abc",
      "https://ntfy.example.org:8443/up",
    ]
  ) {
    assert(isAllowedPushEndpoint(ok), ok);
  }
  for (
    const bad of [
      "http://ntfy.sh/upABC",
      "https://localhost/x",
      "https://127.0.0.1/x",
      "https://[::1]/x",
      "https://10.0.0.1/x",
      "https://intranet/x",
      "https://server.local/x",
      "https://db.internal/x",
      "https://user:pass@ntfy.sh/x",
      "kein-url",
    ]
  ) {
    assertEquals(isAllowedPushEndpoint(bad), false, bad);
  }
});
