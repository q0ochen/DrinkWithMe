/**
 * VAPID (RFC 8292): Der Anwendungsserver weist sich gegenüber dem Push-Dienst mit einem kurzlebigen,
 * ES256-signierten JWT aus. Header: `Authorization: vapid t=<JWT>, k=<öffentlicher Schlüssel>`.
 *
 * Schlüsselformat (wie bei den üblichen Web-Push-Bibliotheken):
 *   VAPID_PUBLIC_KEY  = unkomprimierter P-256-Punkt (65 Byte), base64url
 *   VAPID_PRIVATE_KEY = privater Skalar d (32 Byte), base64url
 */

import { decodeBase64Url, encodeBase64Url, toArrayBuffer, utf8 } from "../bytes.ts";

/** Höchstens 24 Stunden laut RFC 8292; wir nehmen 12 Stunden. */
export const VAPID_TOKEN_LIFETIME_SECONDS = 12 * 60 * 60;

/** Fehler bei unbrauchbaren VAPID-Schlüsseln. */
export class VapidKeyError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "VapidKeyError";
  }
}

/** Importierte VAPID-Zugangsdaten. */
export interface VapidCredentials {
  /** Öffentlicher Schlüssel (65 Byte) – geht als `k=` in den Authorization-Header. */
  readonly publicKey: Uint8Array;
  /** Privater Schlüssel zum Signieren (ECDSA P-256). */
  readonly signingKey: CryptoKey;
  /** Kontakt des Betreibers, `mailto:…` oder `https://…`. */
  readonly subject: string;
}

const ECDSA_P256 = { name: "ECDSA", namedCurve: "P-256" } as const;
const ECDSA_SHA256 = { name: "ECDSA", hash: "SHA-256" } as const;

/** Prüft die Kontaktangabe nach RFC 8292, Abschnitt 2.1. */
export function isValidVapidSubject(subject: string): boolean {
  return /^mailto:[^\s@]+@[^\s@]+$/.test(subject) || /^https:\/\/[^\s]+$/.test(subject);
}

/**
 * Importiert die VAPID-Schlüssel aus base64url-Texten und prüft, dass öffentlicher und privater Schlüssel
 * zusammenpassen (Probesignatur).
 */
export async function importVapidCredentials(
  publicKeyBase64Url: string,
  privateKeyBase64Url: string,
  subject: string,
): Promise<VapidCredentials> {
  if (!isValidVapidSubject(subject)) {
    throw new VapidKeyError("VAPID_SUBJECT muss mit mailto: oder https:// beginnen");
  }
  let publicKey: Uint8Array;
  let privateScalar: Uint8Array;
  try {
    publicKey = decodeBase64Url(publicKeyBase64Url);
    privateScalar = decodeBase64Url(privateKeyBase64Url);
  } catch {
    throw new VapidKeyError("VAPID-Schlüssel sind kein gültiges base64url");
  }
  if (publicKey.length !== 65 || publicKey[0] !== 0x04) {
    throw new VapidKeyError("VAPID_PUBLIC_KEY muss ein unkomprimierter P-256-Schlüssel (65 Byte) sein");
  }
  if (privateScalar.length !== 32) {
    throw new VapidKeyError("VAPID_PRIVATE_KEY muss 32 Byte lang sein");
  }

  const jwk: JsonWebKey = {
    kty: "EC",
    crv: "P-256",
    x: encodeBase64Url(publicKey.subarray(1, 33)),
    y: encodeBase64Url(publicKey.subarray(33, 65)),
    d: encodeBase64Url(privateScalar),
    ext: false,
  };
  let signingKey: CryptoKey;
  let verifyKey: CryptoKey;
  try {
    signingKey = await crypto.subtle.importKey("jwk", jwk, ECDSA_P256, false, ["sign"]);
    verifyKey = await crypto.subtle.importKey("raw", toArrayBuffer(publicKey), ECDSA_P256, false, ["verify"]);
  } catch {
    throw new VapidKeyError("VAPID-Schlüssel lassen sich nicht importieren (kein gültiges P-256-Paar)");
  }

  const probe = utf8("drinkwithme-vapid-probe");
  const signature = await crypto.subtle.sign(ECDSA_SHA256, signingKey, toArrayBuffer(probe));
  if (!(await crypto.subtle.verify(ECDSA_SHA256, verifyKey, signature, toArrayBuffer(probe)))) {
    throw new VapidKeyError("VAPID_PUBLIC_KEY passt nicht zu VAPID_PRIVATE_KEY");
  }
  return { publicKey, signingKey, subject };
}

/** Erzeugt ein neues VAPID-Schlüsselpaar (für supabase/scripts/vapid-schluessel-erzeugen.ts). */
export async function generateVapidKeyPair(): Promise<{ publicKey: string; privateKey: string }> {
  const pair = (await crypto.subtle.generateKey(ECDSA_P256, true, ["sign", "verify"])) as CryptoKeyPair;
  const publicKey = new Uint8Array(await crypto.subtle.exportKey("raw", pair.publicKey));
  const jwk = await crypto.subtle.exportKey("jwk", pair.privateKey);
  if (!jwk.d) {
    throw new VapidKeyError("Privater Schlüssel ließ sich nicht exportieren");
  }
  return { publicKey: encodeBase64Url(publicKey), privateKey: jwk.d };
}

/** Zielgruppe (aud) des JWT: Ursprung des Push-Endpoints, z. B. `https://ntfy.sh`. */
export function audienceOf(endpoint: string): string {
  return new URL(endpoint).origin;
}

/** Erzeugt das signierte VAPID-JWT (ES256) für eine Zielgruppe. */
export async function createVapidJwt(
  credentials: VapidCredentials,
  audience: string,
  expiresAtSeconds: number,
): Promise<string> {
  const header = encodeBase64Url(utf8(JSON.stringify({ typ: "JWT", alg: "ES256" })));
  const claims = encodeBase64Url(
    utf8(JSON.stringify({ aud: audience, exp: expiresAtSeconds, sub: credentials.subject })),
  );
  const signingInput = `${header}.${claims}`;
  // Web Crypto liefert die Signatur bereits im JWS-Format (r | s, je 32 Byte).
  const signature = new Uint8Array(
    await crypto.subtle.sign(ECDSA_SHA256, credentials.signingKey, toArrayBuffer(utf8(signingInput))),
  );
  return `${signingInput}.${encodeBase64Url(signature)}`;
}

/** Zwischenspeicher für VAPID-Header pro Zielgruppe (spart Signaturen bei vielen Empfängern). */
export class VapidHeaderFactory {
  readonly #cache = new Map<string, { header: string; expiresAtSeconds: number }>();

  constructor(
    private readonly credentials: VapidCredentials,
    private readonly lifetimeSeconds: number = VAPID_TOKEN_LIFETIME_SECONDS,
  ) {}

  /** Liefert `vapid t=…, k=…` für den Endpoint; erneuert das JWT, wenn es in unter einer Stunde abläuft. */
  async authorizationFor(endpoint: string, now: Date): Promise<string> {
    const audience = audienceOf(endpoint);
    const nowSeconds = Math.floor(now.getTime() / 1000);
    const cached = this.#cache.get(audience);
    if (cached && cached.expiresAtSeconds - nowSeconds > 3600) {
      return cached.header;
    }
    const expiresAtSeconds = nowSeconds + this.lifetimeSeconds;
    const jwt = await createVapidJwt(this.credentials, audience, expiresAtSeconds);
    const header = `vapid t=${jwt}, k=${encodeBase64Url(this.credentials.publicKey)}`;
    this.#cache.set(audience, { header, expiresAtSeconds });
    return header;
  }
}
