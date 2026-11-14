/**
 * Beispielwerte aus RFC 8291 („Message Encryption for Web Push“), Abschnitt 5 und Anhang A, sowie eine
 * Empfänger-Implementierung (Entschlüsselung) nur für Tests.
 */

import { decodeBase64Url, encodeBase64Url, toArrayBuffer } from "../../_shared/bytes.ts";
import { deriveSecrets, type EphemeralSource } from "../../_shared/webpush/encryption.ts";

/** Werte aus RFC 8291, Anhang A (Leerzeichen der Zeilenumbrüche entfernt). */
export const RFC8291 = {
  plaintext: "V2hlbiBJIGdyb3cgdXAsIEkgd2FudCB0byBiZSBhIHdhdGVybWVsb24",
  asPublic: "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8",
  asPrivate: "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw",
  uaPublic: "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4",
  uaPrivate: "q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94",
  salt: "DGv6ra1nlYgDCS1FRnbzlw",
  authSecret: "BTBZMqHH6r4Tts7J_aSIgg",
  ecdhSecret: "kyrL1jIIOHEzg3sM2ZWRHDRB62YACZhhSlknJ672kSs",
  ikm: "S4lYMb_L0FxCeq0WhDx813KgSYqU26kOyzWUdsXYyrg",
  cek: "oIhVW04MRdy2XN9CiKLxTg",
  nonce: "4h_95klXJ5E_qnoN",
  header:
    "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8",
  ciphertext: "8pfeW0KbunFT06SuDKoJH9Ql87S1QUrdirN6GcG7sFz1y1sqLgVi1VhjVkHsUoEsbI_0LpXMuGvnzQ",
  body:
    "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN",
} as const;

/** Importiert ein P-256-Schlüsselpaar aus privatem Skalar und unkomprimiertem öffentlichem Punkt (base64url). */
export async function importP256KeyPair(
  privateB64: string,
  publicB64: string,
  algorithm: "ECDH" | "ECDSA",
): Promise<CryptoKeyPair> {
  const publicKey = decodeBase64Url(publicB64);
  const jwk: JsonWebKey = {
    kty: "EC",
    crv: "P-256",
    d: privateB64,
    x: encodeBase64Url(publicKey.subarray(1, 33)),
    y: encodeBase64Url(publicKey.subarray(33, 65)),
    ext: true,
  };
  const params = { name: algorithm, namedCurve: "P-256" };
  const privateUsages: KeyUsage[] = algorithm === "ECDH" ? ["deriveBits"] : ["sign"];
  const publicUsages: KeyUsage[] = algorithm === "ECDH" ? [] : ["verify"];
  return {
    privateKey: await crypto.subtle.importKey("jwk", jwk, params, true, privateUsages),
    publicKey: await crypto.subtle.importKey("raw", toArrayBuffer(publicKey), params, true, publicUsages),
  };
}

/** Feste „Zufallswerte“ des RFC-Beispiels: Schlüsselpaar des Anwendungsservers und Salt. */
export function rfcEphemeralSource(): EphemeralSource {
  return {
    createKeyPair: () => importP256KeyPair(RFC8291.asPrivate, RFC8291.asPublic, "ECDH"),
    createSalt: () => decodeBase64Url(RFC8291.salt),
  };
}

/** Entschlüsselt einen aes128gcm-Körper als Empfänger (RFC 8291/8188) – nur für Tests. */
export async function decryptAsReceiver(
  body: Uint8Array,
  receiverPrivateB64: string,
  receiverPublicB64: string,
  authSecretB64: string,
): Promise<{ plaintext: Uint8Array; recordSize: number; senderPublicKey: Uint8Array }> {
  const salt = body.subarray(0, 16);
  const recordSize = new DataView(body.buffer, body.byteOffset + 16, 4).getUint32(0, false);
  const idLength = body[20];
  const senderPublicKey = body.slice(21, 21 + idLength);
  const ciphertext = body.subarray(21 + idLength);

  const receiver = await importP256KeyPair(receiverPrivateB64, receiverPublicB64, "ECDH");
  const senderKey = await crypto.subtle.importKey(
    "raw",
    toArrayBuffer(senderPublicKey),
    { name: "ECDH", namedCurve: "P-256" },
    false,
    [],
  );
  const ecdhSecret = new Uint8Array(
    await crypto.subtle.deriveBits({ name: "ECDH", public: senderKey }, receiver.privateKey, 256),
  );
  const secrets = await deriveSecrets({
    ecdhSecret,
    authSecret: decodeBase64Url(authSecretB64),
    receiverPublicKey: decodeBase64Url(receiverPublicB64),
    senderPublicKey,
    salt: salt.slice(),
  });
  const cek = await crypto.subtle.importKey(
    "raw",
    toArrayBuffer(secrets.contentEncryptionKey),
    "AES-GCM",
    false,
    ["decrypt"],
  );
  const record = new Uint8Array(
    await crypto.subtle.decrypt(
      { name: "AES-GCM", iv: toArrayBuffer(secrets.nonce), tagLength: 128 },
      cek,
      toArrayBuffer(ciphertext),
    ),
  );
  let end = record.length - 1;
  while (end >= 0 && record[end] === 0) end--;
  if (end < 0 || record[end] !== 0x02) {
    throw new Error("Trennbyte 0x02 fehlt (kein letzter Datensatz)");
  }
  return { plaintext: record.slice(0, end), recordSize, senderPublicKey };
}
