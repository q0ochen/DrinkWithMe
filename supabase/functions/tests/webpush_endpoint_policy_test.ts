/**
 * Tests des SSRF-Schutzes für Push-Endpoints: Prüfung der URL ohne Netz, Einordnung von IP-Adressen und die
 * DNS-Prüfung (A und AAAA) mit einer Attrappe statt echter Namensauflösung.
 */

import { assert, assertEquals, assertRejects } from "@std/assert";
import {
  type DnsLookup,
  DnsPushHostVerifier,
  isAllowedPushEndpoint,
  isPublicIpAddress,
  pushEndpointHost,
  ResolvingHostResolver,
} from "../_shared/webpush/endpoint_policy.ts";

Deno.test("Endpoint-Prüfung: öffentliche https-Hosts auf Port 443 sind erlaubt", () => {
  for (
    const ok of [
      "https://ntfy.sh/upABC",
      "https://fcm.googleapis.com/fcm/send/abc",
      "https://updates.push.services.mozilla.com/wpush/v2/x",
      "https://web.push.apple.com/abc",
      "https://wns2-par02p.notify.windows.com/w/?token=abc",
      "https://ntfy.example.org:443/up",
      "https://NTFY.Example.ORG/up",
    ]
  ) {
    assert(isAllowedPushEndpoint(ok), ok);
  }
});

Deno.test("Endpoint-Prüfung: interne Namen, IP-Adressen, fremde Ports und Zugangsdaten sind verboten", () => {
  for (
    const bad of [
      "http://ntfy.sh/upABC",
      "https://localhost/x",
      "https://127.0.0.1/x",
      "https://127.1/x",
      "https://0x7f000001/x",
      "https://[::1]/x",
      "https://[::ffff:127.0.0.1]/x",
      "https://10.0.0.1/x",
      "https://intranet/x",
      "https://server.local/x",
      "https://db.internal/x",
      "https://router.home.arpa/x",
      "https://user:pass@ntfy.sh/x",
      "kein-url",
      "",
    ]
  ) {
    assertEquals(isAllowedPushEndpoint(bad), false, bad);
  }
});

Deno.test("Endpoint-Prüfung: ein Punkt am Ende umgeht die Namensliste nicht mehr", () => {
  for (
    const bad of [
      "https://localhost./x",
      "https://foo.localhost./",
      "https://metadata.google.internal./computeMetadata/v1/",
      "https://server.local./x",
      "https://localhost../x",
      "https://supabase_kong_x./",
      "https://127.0.0.1./x",
    ]
  ) {
    assertEquals(isAllowedPushEndpoint(bad), false, bad);
  }
  assertEquals(pushEndpointHost("https://fcm.googleapis.com./fcm/send/abc"), "fcm.googleapis.com");
});

Deno.test("Endpoint-Prüfung: leere Labels und andere Ports als 443 sind verboten", () => {
  for (
    const bad of [
      "https://a..b.example.org/x",
      "https://.example.org/x",
      "https://example.com:5432/",
      "https://ntfy.example.org:8443/up",
      "https://ntfy.example.org:80/up",
    ]
  ) {
    assertEquals(isAllowedPushEndpoint(bad), false, bad);
  }
});

Deno.test("Endpoint-Prüfung: liefert den bereinigten Hostnamen (klein, ohne Punkt am Ende)", () => {
  assertEquals(pushEndpointHost("https://NTFY.Example.org./up?x=1"), "ntfy.example.org");
  assertEquals(pushEndpointHost("https://localhost./x"), null);
});

Deno.test("IP-Adressen: öffentliche Adressen (v4 und v6) sind erlaubt", () => {
  for (
    const ok of [
      "8.8.8.8",
      "142.250.185.106",
      "1.1.1.1",
      "100.63.255.255",
      "100.128.0.0",
      "172.15.255.255",
      "172.32.0.0",
      "192.169.0.1",
      "2a00:1450:4001:82b::200a",
      "2606:4700:4700::1111",
      "2001:200::1",
    ]
  ) {
    assert(isPublicIpAddress(ok), ok);
  }
});

Deno.test("IP-Adressen: Loopback, privat, Link-Local, CGNAT, ULA, Multicast und Sonderbereiche sind verboten", () => {
  for (
    const bad of [
      "127.0.0.1",
      "127.255.255.254",
      "0.0.0.0",
      "10.1.2.3",
      "172.16.0.1",
      "172.31.255.255",
      "192.168.178.1",
      "169.254.169.254",
      "100.64.0.1",
      "100.127.255.255",
      "192.0.0.8",
      "192.0.2.1",
      "198.18.0.1",
      "198.51.100.7",
      "203.0.113.9",
      "224.0.0.251",
      "239.255.255.250",
      "240.0.0.1",
      "255.255.255.255",
      "::",
      "::1",
      "::ffff:127.0.0.1",
      "::ffff:8.8.8.8",
      "64:ff9b::a00:1",
      "fc00::1",
      "fd12:3456:789a::1",
      "fe80::1",
      "fe80::1%eth0",
      "fec0::1",
      "ff02::1",
      "2001:db8::1",
      "2001::1",
      "2002:a00:1::1",
      "3fff::1",
      "100::1",
      "kein-ip",
      "1.2.3",
      "1.2.3.256",
      "1:2:3:4:5:6:7:8:9",
      "1::2::3",
      "",
    ]
  ) {
    assertEquals(isPublicIpAddress(bad), false, bad);
  }
});

/** DNS-Attrappe: Name → Antworten je Typ; ein Fehlerobjekt wird geworfen. */
function fakeLookup(answers: Record<string, { A?: string[] | Error; AAAA?: string[] | Error }>) {
  const calls: Array<{ hostname: string; recordType: string }> = [];
  const lookup: DnsLookup = (hostname, recordType) => {
    calls.push({ hostname, recordType });
    const answer = answers[hostname]?.[recordType] ?? new Deno.errors.NotFound("keine Einträge");
    return answer instanceof Error ? Promise.reject(answer) : Promise.resolve(answer);
  };
  return { lookup, calls };
}

Deno.test("DNS: A- und AAAA-Antworten werden zusammengeführt, fehlende Einträge sind kein Fehler", async () => {
  const { lookup, calls } = fakeLookup({
    "fcm.googleapis.com": { A: ["142.250.185.106"], AAAA: ["2a00:1450:4001:82b::200a"] },
    "nur-v4.example.org": { A: ["93.184.215.14"] },
  });
  const resolver = new ResolvingHostResolver(lookup);
  assertEquals(await resolver.resolve("fcm.googleapis.com"), ["142.250.185.106", "2a00:1450:4001:82b::200a"]);
  assertEquals(await resolver.resolve("nur-v4.example.org"), ["93.184.215.14"]);
  assertEquals(calls.map((c) => c.recordType).sort(), ["A", "A", "AAAA", "AAAA"]);
});

Deno.test("DNS: ein anderer Fehler als „keine Einträge“ bricht ab (sonst bliebe eine Adressart ungeprüft)", async () => {
  const { lookup } = fakeLookup({
    "langsam.example.org": { A: ["93.184.215.14"], AAAA: new Deno.errors.TimedOut("Zeit abgelaufen") },
  });
  await assertRejects(() => new ResolvingHostResolver(lookup).resolve("langsam.example.org"), Deno.errors.TimedOut);
});

Deno.test("DNS-Prüfung: nur öffentliche Adressen → erlaubt", async () => {
  const { lookup } = fakeLookup({
    "fcm.googleapis.com": { A: ["142.250.185.106"], AAAA: ["2a00:1450:4001:82b::200a"] },
  });
  const verifier = new DnsPushHostVerifier(new ResolvingHostResolver(lookup));
  assertEquals(await verifier.verify("fcm.googleapis.com"), { kind: "allowed" });
});

Deno.test("DNS-Prüfung: Name zeigt auf eine interne Adresse (z. B. 127.0.0.1.nip.io) → verboten", async () => {
  const { lookup } = fakeLookup({
    "127.0.0.1.nip.io": { A: ["127.0.0.1"] },
    "gemischt.example.org": { A: ["93.184.215.14", "10.0.0.5"] },
    "v6-intern.example.org": { A: ["93.184.215.14"], AAAA: ["fd00::5"] },
    "metadaten.example.org": { A: ["169.254.169.254"] },
  });
  const verifier = new DnsPushHostVerifier(new ResolvingHostResolver(lookup));
  for (const host of ["127.0.0.1.nip.io", "gemischt.example.org", "v6-intern.example.org", "metadaten.example.org"]) {
    assertEquals(await verifier.verify(host), { kind: "not_allowed" }, host);
  }
});

Deno.test("DNS-Prüfung: nicht auflösbar oder DNS-Störung → unresolved (Abo bleibt, kein Versand)", async () => {
  const { lookup } = fakeLookup({
    "kaputt.example.org": { A: new Error("SERVFAIL"), AAAA: new Error("SERVFAIL") },
  });
  const verifier = new DnsPushHostVerifier(new ResolvingHostResolver(lookup));
  assertEquals(await verifier.verify("gibt-es-nicht.example.org"), { kind: "unresolved", reason: "dns_no_address" });
  assertEquals(await verifier.verify("kaputt.example.org"), { kind: "unresolved", reason: "dns_error" });
});
