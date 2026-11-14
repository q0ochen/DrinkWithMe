/** Tests des WebPushSender: Header nach RFC 8030/8291/8292, Ergebnis je Statuscode, Schutz vor internen Zielen. */

import { assert, assertEquals, assertMatch } from "@std/assert";
import { importVapidCredentials } from "../_shared/webpush/vapid.ts";
import type { HostVerdict, PushHostVerifier } from "../_shared/webpush/endpoint_policy.ts";
import { type PushTarget, WebPushSender } from "../_shared/webpush/sender.ts";
import { utf8 } from "../_shared/bytes.ts";
import { fakeFetch, FixedClock } from "./helpers/fakes.ts";
import { decryptAsReceiver, RFC8291 } from "./helpers/rfc8291.ts";

const target: PushTarget = {
  endpoint: "https://ntfy.example.org/upAbC123?up=1",
  p256dh: RFC8291.uaPublic,
  auth: RFC8291.authSecret,
};

/** DNS-Prüfung als Attrappe: liefert für jeden Host dasselbe Ergebnis und merkt sich die Hosts. */
class FakeHostVerifier implements PushHostVerifier {
  readonly hosts: string[] = [];
  constructor(private readonly verdict: HostVerdict = { kind: "allowed" }) {}
  verify(hostname: string): Promise<HostVerdict> {
    this.hosts.push(hostname);
    return Promise.resolve(this.verdict);
  }
}

async function senderWith(
  respond: () => Response | Promise<Response>,
  hosts: FakeHostVerifier = new FakeHostVerifier(),
) {
  const credentials = await importVapidCredentials(RFC8291.asPublic, RFC8291.asPrivate, "mailto:betrieb@example.org");
  const http = fakeFetch(respond);
  return { sender: new WebPushSender({ credentials, fetch: http.fetch, clock: new FixedClock(), hosts }), http };
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

Deno.test("Versand: kaputte Schlüssel im Abo → invalid, ohne Netzwerkzugriff (auch ohne DNS)", async () => {
  const hosts = new FakeHostVerifier();
  const { sender, http } = await senderWith(() => new Response(null, { status: 201 }), hosts);
  const result = await sender.send({ ...target, p256dh: "AAAA" }, utf8("x"), { ttlSeconds: 60 });
  assertEquals(result, { kind: "invalid", reason: "receiver_keys_invalid" });
  const result2 = await sender.send({ ...target, auth: "%%%" }, utf8("x"), { ttlSeconds: 60 });
  assertEquals(result2.kind, "invalid");
  assertEquals(http.requests.length, 0);
  assertEquals(hosts.hosts, []);
});

Deno.test("Versand: interne oder unsichere Endpoints werden nicht angesprochen (SSRF-Schutz)", async () => {
  const hosts = new FakeHostVerifier();
  const { sender, http } = await senderWith(() => new Response(null, { status: 201 }), hosts);
  for (const endpoint of ["https://127.0.0.1/x", "https://localhost./x", "https://ntfy.example.org:8443/up"]) {
    const result = await sender.send({ ...target, endpoint }, utf8("x"), { ttlSeconds: 60 });
    assertEquals(result, { kind: "invalid", reason: "endpoint_not_allowed" }, endpoint);
  }
  assertEquals(http.requests.length, 0);
  assertEquals(hosts.hosts, [], "Unzulässige Namen werden gar nicht erst per DNS aufgelöst");
});

Deno.test("Versand: vor dem Senden wird der Hostname per DNS geprüft (ohne Punkt am Ende)", async () => {
  const hosts = new FakeHostVerifier();
  const { sender, http } = await senderWith(() => new Response(null, { status: 201 }), hosts);
  const endpoint = "https://NTFY.example.org./upAbC123";
  assertEquals((await sender.send({ ...target, endpoint }, utf8("x"), { ttlSeconds: 60 })).kind, "delivered");
  assertEquals(hosts.hosts, ["ntfy.example.org"]);
  assertEquals(http.requests.length, 1);
});

Deno.test("Versand: Name zeigt per DNS auf eine interne Adresse → invalid, keine Anfrage", async () => {
  const { sender, http } = await senderWith(
    () => new Response(null, { status: 201 }),
    new FakeHostVerifier({ kind: "not_allowed" }),
  );
  const result = await sender.send({ ...target, endpoint: "https://127.0.0.1.nip.io/x" }, utf8("x"), {
    ttlSeconds: 60,
  });
  assertEquals(result, { kind: "invalid", reason: "endpoint_not_allowed" });
  assertEquals(http.requests.length, 0);
});

Deno.test("Versand: DNS-Störung → failed (Abo bleibt), keine Anfrage", async () => {
  const { sender, http } = await senderWith(
    () => new Response(null, { status: 201 }),
    new FakeHostVerifier({ kind: "unresolved", reason: "dns_error" }),
  );
  const result = await sender.send(target, utf8("x"), { ttlSeconds: 60 });
  assertEquals(result, { kind: "failed", reason: "dns_error" });
  assertEquals(http.requests.length, 0);
});
