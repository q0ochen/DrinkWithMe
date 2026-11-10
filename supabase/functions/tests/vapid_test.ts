/** Tests für VAPID (RFC 8292): Schlüsselimport, JWT-Aufbau (ES256), Authorization-Header, Schlüsselerzeugung. */

import { assert, assertEquals, assertMatch, assertNotEquals, assertRejects } from "@std/assert";
import { decodeBase64Url, toArrayBuffer, utf8 } from "../_shared/bytes.ts";
import {
  audienceOf,
  createVapidJwt,
  generateVapidKeyPair,
  importVapidCredentials,
  isValidVapidSubject,
  VAPID_TOKEN_LIFETIME_SECONDS,
  VapidHeaderFactory,
  VapidKeyError,
} from "../_shared/webpush/vapid.ts";
import { RFC8291 } from "./helpers/rfc8291.ts";

// Ein gültiges P-256-Schlüsselpaar aus RFC 8291 dient hier als VAPID-Testschlüssel.
const PUBLIC = RFC8291.asPublic;
const PRIVATE = RFC8291.asPrivate;
const SUBJECT = "mailto:betrieb@example.org";

function decodeJwt(jwt: string) {
  const [header, claims, signature] = jwt.split(".");
  const text = (part: string) => JSON.parse(new TextDecoder().decode(decodeBase64Url(part)));
  return { header: text(header), claims: text(claims), signingInput: `${header}.${claims}`, signature };
}

async function verifySignature(jwt: string, publicKeyB64: string): Promise<boolean> {
  const { signingInput, signature } = decodeJwt(jwt);
  const key = await crypto.subtle.importKey(
    "raw",
    toArrayBuffer(decodeBase64Url(publicKeyB64)),
    { name: "ECDSA", namedCurve: "P-256" },
    false,
    ["verify"],
  );
  return crypto.subtle.verify(
    { name: "ECDSA", hash: "SHA-256" },
    key,
    toArrayBuffer(decodeBase64Url(signature)),
    toArrayBuffer(utf8(signingInput)),
  );
}

Deno.test("VAPID-Subject: mailto: oder https://", () => {
  assert(isValidVapidSubject("mailto:betrieb@example.org"));
  assert(isValidVapidSubject("https://example.org/kontakt"));
  assertEquals(isValidVapidSubject("betrieb@example.org"), false);
  assertEquals(isValidVapidSubject("http://example.org"), false);
  assertEquals(isValidVapidSubject("mailto:"), false);
});

Deno.test("VAPID-JWT: Header typ=JWT/alg=ES256, Claims aud/exp/sub, gültige 64-Byte-Signatur", async () => {
  const credentials = await importVapidCredentials(PUBLIC, PRIVATE, SUBJECT);
  const jwt = await createVapidJwt(credentials, "https://ntfy.sh", 1_800_000_000);
  assertMatch(jwt, /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/);
  const { header, claims, signature } = decodeJwt(jwt);
  assertEquals(header, { typ: "JWT", alg: "ES256" });
  assertEquals(claims, { aud: "https://ntfy.sh", exp: 1_800_000_000, sub: SUBJECT });
  assertEquals(decodeBase64Url(signature).length, 64);
  assert(await verifySignature(jwt, PUBLIC), "Signatur muss mit dem öffentlichen VAPID-Schlüssel prüfbar sein");
});

Deno.test("Authorization-Header: vapid t=<JWT>, k=<öffentlicher Schlüssel>; aud = Ursprung des Endpoints", async () => {
  const credentials = await importVapidCredentials(PUBLIC, PRIVATE, SUBJECT);
  const factory = new VapidHeaderFactory(credentials);
  const now = new Date("2026-11-10T12:00:00Z");
  const header = await factory.authorizationFor("https://push.example.net:8443/up/abc?x=1", now);
  const match = /^vapid t=([^,]+), k=([A-Za-z0-9_-]+)$/.exec(header);
  assert(match, `Unerwartetes Format: ${header}`);
  assertEquals(match[2], PUBLIC);
  const { claims } = decodeJwt(match[1]);
  assertEquals(claims.aud, "https://push.example.net:8443");
  assertEquals(claims.exp, Math.floor(now.getTime() / 1000) + VAPID_TOKEN_LIFETIME_SECONDS);
  assert(claims.exp - Math.floor(now.getTime() / 1000) <= 24 * 3600, "exp höchstens 24 h in der Zukunft (RFC 8292)");
  assert(await verifySignature(match[1], PUBLIC));
});

Deno.test("Authorization-Header: wird pro Ursprung zwischengespeichert und rechtzeitig erneuert", async () => {
  const credentials = await importVapidCredentials(PUBLIC, PRIVATE, SUBJECT);
  const factory = new VapidHeaderFactory(credentials);
  const start = new Date("2026-11-10T12:00:00Z");
  const first = await factory.authorizationFor("https://ntfy.sh/a", start);
  assertEquals(await factory.authorizationFor("https://ntfy.sh/b", new Date(start.getTime() + 60_000)), first);
  assertNotEquals(await factory.authorizationFor("https://other.example/a", start), first);
  const later = new Date(start.getTime() + (VAPID_TOKEN_LIFETIME_SECONDS - 3000) * 1000);
  assertNotEquals(await factory.authorizationFor("https://ntfy.sh/a", later), first);
});

Deno.test("audienceOf liefert nur Schema, Host und Port", () => {
  assertEquals(audienceOf("https://fcm.googleapis.com/fcm/send/abc"), "https://fcm.googleapis.com");
  assertEquals(audienceOf("https://ntfy.example.org:8080/upXYZ?up=1"), "https://ntfy.example.org:8080");
});

Deno.test("Schlüsselimport: unpassende, kaputte oder falsch lange Schlüssel werden abgelehnt", async () => {
  await assertRejects(() => importVapidCredentials(RFC8291.uaPublic, PRIVATE, SUBJECT), VapidKeyError, "passt nicht");
  await assertRejects(() => importVapidCredentials(PUBLIC, PRIVATE.slice(0, 20), SUBJECT), VapidKeyError);
  await assertRejects(() => importVapidCredentials(PUBLIC.slice(2), PRIVATE, SUBJECT), VapidKeyError);
  await assertRejects(() => importVapidCredentials("$$$", PRIVATE, SUBJECT), VapidKeyError, "base64url");
  await assertRejects(() => importVapidCredentials(PUBLIC, PRIVATE, "kein-kontakt"), VapidKeyError, "VAPID_SUBJECT");
});

Deno.test("generateVapidKeyPair erzeugt ein passendes, importierbares Paar im üblichen Format", async () => {
  const pair = await generateVapidKeyPair();
  assertMatch(pair.publicKey, /^[A-Za-z0-9_-]{87}$/);
  assertMatch(pair.privateKey, /^[A-Za-z0-9_-]{43}$/);
  assertEquals(decodeBase64Url(pair.publicKey)[0], 0x04);
  const credentials = await importVapidCredentials(pair.publicKey, pair.privateKey, SUBJECT);
  const jwt = await createVapidJwt(credentials, "https://ntfy.sh", 1_800_000_000);
  assert(await verifySignature(jwt, pair.publicKey));
  const other = await generateVapidKeyPair();
  assertNotEquals(other.privateKey, pair.privateKey);
});
