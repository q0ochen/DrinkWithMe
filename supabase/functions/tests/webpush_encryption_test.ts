/** Tests der aes128gcm-Verschlüsselung gegen das Beispiel aus RFC 8291 (Abschnitt 5, Anhang A). */

import { assertEquals, assertNotEquals, assertRejects } from "@std/assert";
import { decodeBase64Url, encodeBase64Url, utf8 } from "../_shared/bytes.ts";
import {
  deriveSecrets,
  encryptAes128Gcm,
  HEADER_LENGTH,
  MAX_PLAINTEXT_LENGTH,
  PushEncryptionError,
  RECORD_SIZE,
} from "../_shared/webpush/encryption.ts";
import { decryptAsReceiver, importP256KeyPair, RFC8291, rfcEphemeralSource } from "./helpers/rfc8291.ts";

const receiver = {
  publicKey: decodeBase64Url(RFC8291.uaPublic),
  authSecret: decodeBase64Url(RFC8291.authSecret),
};

Deno.test("RFC 8291: Klartext des Beispiels ist „When I grow up, I want to be a watermelon“", () => {
  assertEquals(
    new TextDecoder().decode(decodeBase64Url(RFC8291.plaintext)),
    "When I grow up, I want to be a watermelon",
  );
});

Deno.test("RFC 8291 Anhang A: ECDH-Geheimnis aus den Beispielschlüsseln", async () => {
  const as = await importP256KeyPair(RFC8291.asPrivate, RFC8291.asPublic, "ECDH");
  const ua = await importP256KeyPair(RFC8291.uaPrivate, RFC8291.uaPublic, "ECDH");
  const fromSender = new Uint8Array(
    await crypto.subtle.deriveBits({ name: "ECDH", public: ua.publicKey }, as.privateKey, 256),
  );
  const fromReceiver = new Uint8Array(
    await crypto.subtle.deriveBits({ name: "ECDH", public: as.publicKey }, ua.privateKey, 256),
  );
  assertEquals(encodeBase64Url(fromSender), RFC8291.ecdhSecret);
  assertEquals(encodeBase64Url(fromReceiver), RFC8291.ecdhSecret);
});

Deno.test("RFC 8291 Anhang A: IKM, CEK und NONCE", async () => {
  const secrets = await deriveSecrets({
    ecdhSecret: decodeBase64Url(RFC8291.ecdhSecret),
    authSecret: decodeBase64Url(RFC8291.authSecret),
    receiverPublicKey: decodeBase64Url(RFC8291.uaPublic),
    senderPublicKey: decodeBase64Url(RFC8291.asPublic),
    salt: decodeBase64Url(RFC8291.salt),
  });
  assertEquals(encodeBase64Url(secrets.ikm), RFC8291.ikm);
  assertEquals(encodeBase64Url(secrets.contentEncryptionKey), RFC8291.cek);
  assertEquals(encodeBase64Url(secrets.nonce), RFC8291.nonce);
});

Deno.test("RFC 8291 Abschnitt 5: vollständiger Nachrichtenkörper stimmt Byte für Byte", async () => {
  const body = await encryptAes128Gcm(decodeBase64Url(RFC8291.plaintext), receiver, rfcEphemeralSource());
  // Das RFC-Beispiel nennt „Content-Length: 145“, seine Daten ergeben aber 86 (Kopf) + 58 (Chiffrat) = 144 Byte.
  assertEquals(decodeBase64Url(RFC8291.body).length, 144);
  assertEquals(body.length, 144);
  assertEquals(encodeBase64Url(body.subarray(0, HEADER_LENGTH)), RFC8291.header);
  assertEquals(encodeBase64Url(body.subarray(HEADER_LENGTH)), RFC8291.ciphertext);
  assertEquals(encodeBase64Url(body), RFC8291.body);
});

Deno.test("Kopf: Salt, rs = 4096, idlen = 65, öffentlicher Schlüssel des Absenders", async () => {
  const body = await encryptAes128Gcm(utf8("Hallo"), receiver, rfcEphemeralSource());
  assertEquals(encodeBase64Url(body.subarray(0, 16)), RFC8291.salt);
  assertEquals(new DataView(body.buffer, body.byteOffset + 16, 4).getUint32(0, false), RECORD_SIZE);
  assertEquals(body[20], 65);
  assertEquals(encodeBase64Url(body.subarray(21, 86)), RFC8291.asPublic);
});

Deno.test("Mit echten Zufallswerten: Empfänger kann entschlüsseln, jede Nachricht ist anders", async () => {
  const plaintext = utf8(JSON.stringify({ type: "drink", title: "Anna trinkt gerade", body: "Apfelschorle (500 ml)" }));
  const first = await encryptAes128Gcm(plaintext, receiver);
  const second = await encryptAes128Gcm(plaintext, receiver);
  assertNotEquals(encodeBase64Url(first), encodeBase64Url(second));
  assertNotEquals(encodeBase64Url(first.subarray(0, 16)), encodeBase64Url(second.subarray(0, 16)));

  const decrypted = await decryptAsReceiver(first, RFC8291.uaPrivate, RFC8291.uaPublic, RFC8291.authSecret);
  assertEquals(new TextDecoder().decode(decrypted.plaintext), new TextDecoder().decode(plaintext));
  assertEquals(decrypted.recordSize, RECORD_SIZE);
  assertEquals(decrypted.senderPublicKey.length, 65);
});

Deno.test("Entschlüsseln des RFC-Beispiels liefert den Klartext", async () => {
  const decrypted = await decryptAsReceiver(
    decodeBase64Url(RFC8291.body),
    RFC8291.uaPrivate,
    RFC8291.uaPublic,
    RFC8291.authSecret,
  );
  assertEquals(encodeBase64Url(decrypted.plaintext), RFC8291.plaintext);
});

Deno.test("Größter erlaubter Inhalt ergibt genau 4096 Byte, ein Byte mehr wird abgelehnt", async () => {
  const body = await encryptAes128Gcm(new Uint8Array(MAX_PLAINTEXT_LENGTH), receiver);
  assertEquals(body.length, 4096);
  await assertRejects(
    () => encryptAes128Gcm(new Uint8Array(MAX_PLAINTEXT_LENGTH + 1), receiver),
    PushEncryptionError,
    "zu groß",
  );
});

Deno.test("Ungültige Empfängerschlüssel werden abgelehnt", async () => {
  await assertRejects(
    () => encryptAes128Gcm(utf8("x"), { ...receiver, publicKey: receiver.publicKey.subarray(1) }),
    PushEncryptionError,
  );
  const notOnCurve = receiver.publicKey.slice();
  notOnCurve[64] ^= 0x01;
  await assertRejects(() => encryptAes128Gcm(utf8("x"), { ...receiver, publicKey: notOnCurve }), PushEncryptionError);
  await assertRejects(
    () => encryptAes128Gcm(utf8("x"), { ...receiver, authSecret: new Uint8Array(8) }),
    PushEncryptionError,
  );
});
