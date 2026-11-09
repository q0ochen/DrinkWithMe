import { Timestamp } from "firebase-admin/firestore";
import type { DocumentData } from "firebase-admin/firestore";
import type { JoinAttempts, JoinThrottlePolicy } from "../domain/joinThrottle";
import { joinAttemptsExpiresAt } from "../domain/joinThrottle";

// Abbildung zwischen `JoinAttempts` (Domäne) und dem Dokument `joinAttempts/{key}` laut Vertrag:
// `{ windowStart: timestamp, failures: int, expireAt: timestamp }`. `expireAt` (= windowStart + 1 h)
// ist das Feld der TTL-Richtlinie (firestore.indexes.json): Firestore löscht abgelaufene Zähler selbst.

/** Das zu schreibende Dokument – mit jeder Änderung des Zählers wird auch `expireAt` geschrieben. */
export function toJoinAttemptsDocument(attempts: JoinAttempts, policy: JoinThrottlePolicy): DocumentData {
  return {
    windowStart: Timestamp.fromMillis(attempts.windowStartMillis),
    failures: attempts.failures,
    expireAt: Timestamp.fromMillis(joinAttemptsExpiresAt(attempts, policy)),
  };
}

/** Liest den Zähler; fehlende oder kaputte Dokumente gelten als „keine Versuche“ (`null`). */
export function parseJoinAttempts(data: DocumentData | undefined): JoinAttempts | null {
  if (data === undefined) return null;
  const windowStart: unknown = data.windowStart;
  const failures: unknown = data.failures;
  if (!(windowStart instanceof Timestamp) || typeof failures !== "number" || !Number.isInteger(failures)) {
    return null;
  }
  return { windowStartMillis: windowStart.toMillis(), failures };
}
