/** Tests der Byte-Hilfen (base64url) und der Kurvenprüfung für P-256. */

import { assertEquals, assertFalse, assertThrows } from "@std/assert";
import { concatBytes, decodeBase64Url, encodeBase64Url, utf8 } from "../_shared/bytes.ts";
import { isValidP256PublicKey } from "../_shared/webpush/p256.ts";
import { RFC8291 } from "./helpers/rfc8291.ts";

Deno.test("base64url: Kodieren ohne Auffüllzeichen und mit - und _", () => {
  assertEquals(encodeBase64Url(new Uint8Array([0xfb, 0xff, 0xfe])), "-__-");
  assertEquals(encodeBase64Url(utf8("a")), "YQ");
  assertEquals(encodeBase64Url(new Uint8Array()), "");
});

Deno.test("base64url: Dekodieren akzeptiert base64url, base64 und Auffüllzeichen", () => {
  assertEquals([...decodeBase64Url("-__-")], [0xfb, 0xff, 0xfe]);
  assertEquals([...decodeBase64Url("+//+")], [0xfb, 0xff, 0xfe]);
  assertEquals(new TextDecoder().decode(decodeBase64Url("YQ==")), "a");
  assertEquals(new TextDecoder().decode(decodeBase64Url(" YQ ")), "a");
});

Deno.test("base64url: ungültige Eingaben werfen", () => {
  assertThrows(() => decodeBase64Url("ab$c"), TypeError);
  assertThrows(() => decodeBase64Url("abcde"), TypeError);
});

Deno.test("base64url: Hin und zurück für alle Bytewerte und große Daten", () => {
  const all = Uint8Array.from({ length: 256 }, (_, i) => i);
  assertEquals([...decodeBase64Url(encodeBase64Url(all))], [...all]);
  const large = new Uint8Array(100_000).map((_, i) => (i * 31) % 256);
  assertEquals(decodeBase64Url(encodeBase64Url(large)).length, large.length);
});

Deno.test("concatBytes hängt aneinander", () => {
  assertEquals([...concatBytes(new Uint8Array([1]), new Uint8Array(), new Uint8Array([2, 3]))], [1, 2, 3]);
});

Deno.test("P-256: Schlüssel aus RFC 8291 liegen auf der Kurve", () => {
  assertEquals(isValidP256PublicKey(decodeBase64Url(RFC8291.uaPublic)), true);
  assertEquals(isValidP256PublicKey(decodeBase64Url(RFC8291.asPublic)), true);
});

Deno.test("P-256: veränderte, zu kurze oder komprimierte Punkte werden abgelehnt", () => {
  const key = decodeBase64Url(RFC8291.uaPublic);
  const flipped = key.slice();
  flipped[64] ^= 0x01;
  assertFalse(isValidP256PublicKey(flipped));
  assertFalse(isValidP256PublicKey(key.subarray(0, 64)));
  const compressed = key.slice();
  compressed[0] = 0x02;
  assertFalse(isValidP256PublicKey(compressed));
  const outOfRange = new Uint8Array(65).fill(0xff);
  outOfRange[0] = 0x04;
  assertFalse(isValidP256PublicKey(outOfRange));
});
