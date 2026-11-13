/**
 * Byte-Hilfen für die Edge Functions: base64url (RFC 4648, Abschnitt 5) und Verketten von Byte-Folgen.
 *
 * Eigene, abhängigkeitsfreie Implementierung, damit die Edge Functions keine Fremdpakete brauchen.
 */

const textEncoder = new TextEncoder();

/** Wandelt Text in UTF-8-Bytes um. */
export function utf8(text: string): Uint8Array {
  return textEncoder.encode(text);
}

/** Kodiert Bytes als base64url ohne Auffüllzeichen („=“). */
export function encodeBase64Url(bytes: Uint8Array): string {
  let binary = "";
  for (let i = 0; i < bytes.length; i += 0x8000) {
    binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  }
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
}

/**
 * Dekodiert base64url – akzeptiert zur Sicherheit auch normales base64 (mit „+“ und „/“) und Auffüllzeichen,
 * weil Push-Clients die Schlüssel unterschiedlich liefern.
 *
 * @throws TypeError bei ungültigen Zeichen oder unmöglicher Länge.
 */
export function decodeBase64Url(text: string): Uint8Array {
  const cleaned = text.trim().replace(/=+$/, "");
  if (!/^[A-Za-z0-9_+/-]*$/.test(cleaned) || cleaned.length % 4 === 1) {
    throw new TypeError("Ungültiges base64url");
  }
  const standard = cleaned.replaceAll("-", "+").replaceAll("_", "/");
  const padded = standard + "=".repeat((4 - (standard.length % 4)) % 4);
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

/** Hängt mehrere Byte-Folgen aneinander. */
export function concatBytes(...parts: Uint8Array[]): Uint8Array {
  const total = parts.reduce((sum, part) => sum + part.length, 0);
  const result = new Uint8Array(total);
  let offset = 0;
  for (const part of parts) {
    result.set(part, offset);
    offset += part.length;
  }
  return result;
}

/**
 * Liefert eine Kopie mit eigenem ArrayBuffer. Web Crypto erwartet `BufferSource`; so ist der Typ eindeutig
 * und Teilansichten (subarray) werden nicht versehentlich mit dem ganzen Puffer übergeben.
 */
export function toArrayBuffer(bytes: Uint8Array): ArrayBuffer {
  const copy = new Uint8Array(bytes.length);
  copy.set(bytes);
  return copy.buffer;
}
