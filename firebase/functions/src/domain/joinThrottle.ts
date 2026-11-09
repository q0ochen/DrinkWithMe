import { createHash } from "node:crypto";

// Schutz vor dem Erraten von Einladungscodes: Pro Nutzer zählt `joinAttempts/{key}` die
// fehlgeschlagenen Beitrittsversuche (unbekannter Code) in einem festen Zeitfenster von einer Stunde.
// Ab 10 Fehlversuchen im Fenster lehnt `joinGroup` jeden weiteren Versuch ab – noch bevor der Code
// nachgeschlagen wird. Der Schlüssel hängt am Google-Konto (nicht an der uid), damit „Konto löschen
// und neu anmelden“ keinen frischen Zähler liefert. Diese Datei enthält nur die Regeln; das Lesen und
// Schreiben übernehmen GroupService (Transaktion mit dem Nachschlagen des Codes) und AccountService.

/** Liefert die aktuelle Zeit in Millisekunden seit 1970 (in Produktion `Date.now`, im Test steuerbar). */
export type Clock = () => number;

/** Inhalt von `joinAttempts/{key}` – Zeitpunkte als Millisekunden. */
export interface JoinAttempts {
  /** Beginn des laufenden Zeitfensters (erster Fehlversuch darin). */
  windowStartMillis: number;
  /** Fehlversuche seit `windowStartMillis`. */
  failures: number;
}

export interface JoinThrottlePolicy {
  /** Ab so vielen Fehlversuchen im Fenster wird abgelehnt. */
  maxFailures: number;
  /** Länge des Zeitfensters. */
  windowMillis: number;
}

/** Laut Vertrag: ab 10 Fehlversuchen pro Nutzer (Google-Konto, sonst uid) innerhalb einer Stunde. */
export const JOIN_THROTTLE_POLICY: JoinThrottlePolicy = {
  maxFailures: 10,
  windowMillis: 60 * 60 * 1000,
};

/** Wer zählt: die uid und – bei Google-Anmeldung – die Google-Kennung (`sub`) aus dem ID-Token. */
export interface JoinThrottleIdentity {
  uid: string;
  /** `request.auth.token.firebase.identities["google.com"][0]` */
  googleSubject?: string;
}

/**
 * Dokument-ID des Zählers in `joinAttempts`. Mit Google-Kennung: SHA-256 (hex) von `"google:" + sub` –
 * dieselbe ID auch nach dem Löschen und Neuanlegen des Firebase-Kontos (neue uid), ohne die Kennung
 * selbst zu speichern. Ohne Google-Kennung: die uid.
 */
export function joinThrottleKey(identity: JoinThrottleIdentity): string {
  const subject = identity.googleSubject;
  if (subject === undefined || subject.length === 0) return identity.uid;
  return createHash("sha256").update(`google:${subject}`).digest("hex");
}

/**
 * `true`, solange das Fenster der gespeicherten Versuche noch läuft. Geringe Uhrabweichungen
 * zwischen Instanzen (Beginn minimal in der Zukunft) zählen als laufendes Fenster.
 */
export function isJoinWindowActive(
  attempts: JoinAttempts,
  now: number,
  policy: JoinThrottlePolicy = JOIN_THROTTLE_POLICY,
): boolean {
  return now - attempts.windowStartMillis < policy.windowMillis;
}

/**
 * Ende des Fensters (`windowStart + 1 h`) – gespeichert als `expireAt`; danach löscht die
 * TTL-Richtlinie von Firestore das Dokument automatisch.
 */
export function joinAttemptsExpiresAt(attempts: JoinAttempts, policy: JoinThrottlePolicy = JOIN_THROTTLE_POLICY): number {
  return attempts.windowStartMillis + policy.windowMillis;
}

/** `true`, wenn der Nutzer im laufenden Fenster schon zu oft einen falschen Code eingegeben hat. */
export function isJoinBlocked(
  attempts: JoinAttempts | null,
  now: number,
  policy: JoinThrottlePolicy = JOIN_THROTTLE_POLICY,
): boolean {
  return attempts !== null && isJoinWindowActive(attempts, now, policy) && attempts.failures >= policy.maxFailures;
}

/**
 * Zählt einen weiteren Fehlversuch. Ist das Fenster abgelaufen (oder gibt es noch keins),
 * beginnt ein neues Fenster mit diesem Versuch.
 */
export function recordFailedJoin(
  attempts: JoinAttempts | null,
  now: number,
  policy: JoinThrottlePolicy = JOIN_THROTTLE_POLICY,
): JoinAttempts {
  if (attempts === null || !isJoinWindowActive(attempts, now, policy)) {
    return { windowStartMillis: now, failures: 1 };
  }
  return { windowStartMillis: attempts.windowStartMillis, failures: attempts.failures + 1 };
}
