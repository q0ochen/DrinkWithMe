/**
 * Prüfung öffentlicher P-256-Schlüssel (RFC 8291, Abschnitt 8 „Security Considerations“): Der Punkt muss im
 * gültigen Wertebereich liegen und die Kurvengleichung y² = x³ − 3x + b (mod p) erfüllen. Web Crypto
 * prüft das nicht in jeder Laufzeitumgebung beim Import, daher prüfen wir selbst.
 */

const P = 0xffffffff00000001000000000000000000000000ffffffffffffffffffffffffn;
const B = 0x5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604bn;

function toBigInt(bytes: Uint8Array): bigint {
  let value = 0n;
  for (const byte of bytes) value = (value << 8n) | BigInt(byte);
  return value;
}

function mod(value: bigint): bigint {
  const result = value % P;
  return result < 0n ? result + P : result;
}

/** `true`, wenn `publicKey` ein unkomprimierter Punkt (0x04 | x | y, 65 Byte) auf der Kurve P-256 ist. */
export function isValidP256PublicKey(publicKey: Uint8Array): boolean {
  if (publicKey.length !== 65 || publicKey[0] !== 0x04) return false;
  const x = toBigInt(publicKey.subarray(1, 33));
  const y = toBigInt(publicKey.subarray(33, 65));
  if (x >= P || y >= P) return false;
  const left = mod(y * y);
  const right = mod(x * x * x - 3n * x + B);
  return left === right;
}
