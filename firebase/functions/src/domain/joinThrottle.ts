// Schutz vor dem Erraten von Einladungscodes: Pro Nutzer zählt `joinAttempts/{uid}` die
// fehlgeschlagenen Beitrittsversuche (unbekannter Code) in einem festen Zeitfenster von einer Stunde.
// Ab 10 Fehlversuchen im Fenster lehnt `joinGroup` jeden weiteren Versuch ab – noch bevor der Code
// nachgeschlagen wird. Diese Datei enthält nur die Regeln; das Lesen und Schreiben übernimmt der
// GroupService in derselben Transaktion wie das Nachschlagen des Codes.

/** Liefert die aktuelle Zeit in Millisekunden seit 1970 (in Produktion `Date.now`, im Test steuerbar). */
export type Clock = () => number;

/** Inhalt von `joinAttempts/{uid}` – Zeitpunkte als Millisekunden. */
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

/** Laut Vertrag: ab 10 Fehlversuchen pro Nutzer innerhalb einer Stunde. */
export const JOIN_THROTTLE_POLICY: JoinThrottlePolicy = {
  maxFailures: 10,
  windowMillis: 60 * 60 * 1000,
};

/**
 * `true`, solange das Fenster der gespeicherten Versuche noch läuft. Geringe Uhrabweichungen
 * zwischen Instanzen (Beginn minimal in der Zukunft) zählen als laufendes Fenster.
 */
function isWindowOpen(attempts: JoinAttempts, now: number, policy: JoinThrottlePolicy): boolean {
  return now - attempts.windowStartMillis < policy.windowMillis;
}

/** `true`, wenn der Nutzer im laufenden Fenster schon zu oft einen falschen Code eingegeben hat. */
export function isJoinBlocked(
  attempts: JoinAttempts | null,
  now: number,
  policy: JoinThrottlePolicy = JOIN_THROTTLE_POLICY,
): boolean {
  return attempts !== null && isWindowOpen(attempts, now, policy) && attempts.failures >= policy.maxFailures;
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
  if (attempts === null || !isWindowOpen(attempts, now, policy)) {
    return { windowStartMillis: now, failures: 1 };
  }
  return { windowStartMillis: attempts.windowStartMillis, failures: attempts.failures + 1 };
}
