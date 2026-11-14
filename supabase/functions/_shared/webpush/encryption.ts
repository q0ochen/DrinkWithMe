/**
 * Verschlüsselung von Push-Nachrichten nach RFC 8291 („Message Encryption for Web Push“) mit der
 * Inhaltskodierung „aes128gcm“ aus RFC 8188 – ausschließlich mit Web Crypto (keine Fremdpakete).
 *
 * Ablauf (RFC 8291, Abschnitt 3.4):
 *   ecdh_secret = ECDH(as_private, ua_public)
 *   IKM         = HKDF(salt = auth_secret, ikm = ecdh_secret, info = "WebPush: info" 0x00 ua_public as_public, L = 32)
 *   CEK         = HKDF(salt, IKM, "Content-Encoding: aes128gcm" 0x00, 16)
 *   NONCE       = HKDF(salt, IKM, "Content-Encoding: nonce" 0x00, 12)
 *   Körper      = salt(16) | rs(4) | idlen(1) | as_public(65) | AES-128-GCM(CEK, NONCE, Klartext | 0x02)
 */

import { concatBytes, toArrayBuffer, utf8 } from "../bytes.ts";
import { isValidP256PublicKey } from "./p256.ts";

/** Datensatzgröße (rs) im Kopf der Nachricht; die Nachricht besteht aus genau einem Datensatz. */
export const RECORD_SIZE = 4096;

/** Länge des aes128gcm-Kopfes: salt (16) + rs (4) + idlen (1) + öffentlicher Schlüssel (65). */
export const HEADER_LENGTH = 86;

/**
 * Höchste Klartextlänge, damit der gesamte Körper höchstens 4096 Byte groß ist (Grenze der Push-Dienste):
 * 4096 − Kopf (86) − Trennbyte (1) − GCM-Prüfsumme (16).
 */
export const MAX_PLAINTEXT_LENGTH = RECORD_SIZE - HEADER_LENGTH - 1 - 16;

const P256_PUBLIC_KEY_LENGTH = 65;
const AUTH_SECRET_LENGTH = 16;
const SALT_LENGTH = 16;

/** Quelle für Zufallswerte pro Nachricht; in Tests durch feste Werte (RFC-Beispiel) ersetzbar. */
export interface EphemeralSource {
  /** Erzeugt ein neues ECDH-Schlüsselpaar (P-256) des Anwendungsservers. */
  createKeyPair(): Promise<CryptoKeyPair>;
  /** Liefert ein neues, zufälliges 16-Byte-Salt. */
  createSalt(): Uint8Array;
}

/** Echte Zufallsquelle: für jede Nachricht neues Schlüsselpaar und neues Salt. */
export const randomEphemeralSource: EphemeralSource = {
  createKeyPair: () =>
    // Der private Schlüssel bleibt nicht exportierbar; öffentliche Schlüssel sind immer exportierbar.
    crypto.subtle.generateKey({ name: "ECDH", namedCurve: "P-256" }, false, ["deriveBits"]) as Promise<
      CryptoKeyPair
    >,
  createSalt: () => crypto.getRandomValues(new Uint8Array(SALT_LENGTH)),
};

/** Schlüssel des Empfängers aus dem Push-Abo (beide als Bytes). */
export interface ReceiverKeys {
  /** Öffentlicher P-256-Schlüssel des Empfängers, unkomprimiert (65 Byte, beginnt mit 0x04). */
  publicKey: Uint8Array;
  /** Authentifizierungsgeheimnis des Empfängers (16 Byte). */
  authSecret: Uint8Array;
}

/** Fehler bei ungültigen Schlüsseln oder zu großem Inhalt. */
export class PushEncryptionError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "PushEncryptionError";
  }
}

/** Zwischenergebnisse der Schlüsselableitung (für Tests gegen RFC 8291, Anhang A). */
export interface DerivedSecrets {
  ikm: Uint8Array;
  contentEncryptionKey: Uint8Array;
  nonce: Uint8Array;
}

/** HKDF-SHA-256 (RFC 5869) über Web Crypto. */
export async function hkdf(
  salt: Uint8Array,
  inputKeyMaterial: Uint8Array,
  info: Uint8Array,
  length: number,
): Promise<Uint8Array> {
  const key = await crypto.subtle.importKey("raw", toArrayBuffer(inputKeyMaterial), "HKDF", false, [
    "deriveBits",
  ]);
  const bits = await crypto.subtle.deriveBits(
    { name: "HKDF", hash: "SHA-256", salt: toArrayBuffer(salt), info: toArrayBuffer(info) },
    key,
    length * 8,
  );
  return new Uint8Array(bits);
}

/** Leitet IKM, Inhaltsschlüssel (CEK) und Nonce aus dem gemeinsamen ECDH-Geheimnis ab (RFC 8291, 3.3/3.4). */
export async function deriveSecrets(params: {
  ecdhSecret: Uint8Array;
  authSecret: Uint8Array;
  receiverPublicKey: Uint8Array;
  senderPublicKey: Uint8Array;
  salt: Uint8Array;
}): Promise<DerivedSecrets> {
  const keyInfo = concatBytes(
    utf8("WebPush: info\0"),
    params.receiverPublicKey,
    params.senderPublicKey,
  );
  const ikm = await hkdf(params.authSecret, params.ecdhSecret, keyInfo, 32);
  const contentEncryptionKey = await hkdf(params.salt, ikm, utf8("Content-Encoding: aes128gcm\0"), 16);
  const nonce = await hkdf(params.salt, ikm, utf8("Content-Encoding: nonce\0"), 12);
  return { ikm, contentEncryptionKey, nonce };
}

/** Prüft die Schlüssel des Empfängers und importiert dessen öffentlichen Schlüssel für ECDH. */
async function importReceiverKey(receiver: ReceiverKeys): Promise<CryptoKey> {
  if (receiver.publicKey.length !== P256_PUBLIC_KEY_LENGTH || receiver.publicKey[0] !== 0x04) {
    throw new PushEncryptionError("Öffentlicher Schlüssel des Empfängers ist kein unkomprimierter P-256-Punkt");
  }
  if (!isValidP256PublicKey(receiver.publicKey)) {
    throw new PushEncryptionError("Öffentlicher Schlüssel des Empfängers liegt nicht auf der Kurve P-256");
  }
  if (receiver.authSecret.length !== AUTH_SECRET_LENGTH) {
    throw new PushEncryptionError("Authentifizierungsgeheimnis des Empfängers muss 16 Byte lang sein");
  }
  try {
    return await crypto.subtle.importKey(
      "raw",
      toArrayBuffer(receiver.publicKey),
      { name: "ECDH", namedCurve: "P-256" },
      false,
      [],
    );
  } catch {
    throw new PushEncryptionError("Öffentlicher Schlüssel des Empfängers lässt sich nicht importieren");
  }
}

/**
 * Verschlüsselt einen Klartext für einen Empfänger und liefert den vollständigen Nachrichtenkörper
 * (Content-Encoding: aes128gcm).
 */
export async function encryptAes128Gcm(
  plaintext: Uint8Array,
  receiver: ReceiverKeys,
  ephemeral: EphemeralSource = randomEphemeralSource,
): Promise<Uint8Array> {
  if (plaintext.length > MAX_PLAINTEXT_LENGTH) {
    throw new PushEncryptionError(
      `Inhalt zu groß: ${plaintext.length} Byte (höchstens ${MAX_PLAINTEXT_LENGTH} Byte)`,
    );
  }
  const receiverKey = await importReceiverKey(receiver);

  const senderKeys = await ephemeral.createKeyPair();
  const senderPublicKey = new Uint8Array(await crypto.subtle.exportKey("raw", senderKeys.publicKey));
  const salt = ephemeral.createSalt();
  if (salt.length !== SALT_LENGTH) {
    throw new PushEncryptionError("Salt muss 16 Byte lang sein");
  }

  let ecdhSecret: Uint8Array;
  try {
    ecdhSecret = new Uint8Array(
      await crypto.subtle.deriveBits({ name: "ECDH", public: receiverKey }, senderKeys.privateKey, 256),
    );
  } catch {
    throw new PushEncryptionError("ECDH mit dem Schlüssel des Empfängers ist fehlgeschlagen");
  }
  const secrets = await deriveSecrets({
    ecdhSecret,
    authSecret: receiver.authSecret,
    receiverPublicKey: receiver.publicKey,
    senderPublicKey,
    salt,
  });

  const cek = await crypto.subtle.importKey(
    "raw",
    toArrayBuffer(secrets.contentEncryptionKey),
    "AES-GCM",
    false,
    ["encrypt"],
  );
  // Ein einziger (und damit letzter) Datensatz: Trennbyte 0x02, keine zusätzliche Auffüllung.
  const record = concatBytes(plaintext, new Uint8Array([0x02]));
  const ciphertext = new Uint8Array(
    await crypto.subtle.encrypt(
      { name: "AES-GCM", iv: toArrayBuffer(secrets.nonce), tagLength: 128 },
      cek,
      toArrayBuffer(record),
    ),
  );

  const header = new Uint8Array(HEADER_LENGTH);
  header.set(salt, 0);
  new DataView(header.buffer).setUint32(SALT_LENGTH, RECORD_SIZE, false);
  header[SALT_LENGTH + 4] = P256_PUBLIC_KEY_LENGTH;
  header.set(senderPublicKey, SALT_LENGTH + 5);

  return concatBytes(header, ciphertext);
}
