import { describe, expect, it } from "vitest";
import {
  JOIN_THROTTLE_POLICY,
  isJoinBlocked,
  isJoinWindowActive,
  joinAttemptsExpiresAt,
  joinThrottleKey,
  recordFailedJoin,
} from "../../src/domain/joinThrottle";
import type { JoinAttempts } from "../../src/domain/joinThrottle";

const HOUR = 60 * 60 * 1000;
const START = Date.UTC(2026, 10, 9, 12, 0, 0);

/** Zählt `count` Fehlversuche ab `START` (eine Sekunde Abstand). */
function failures(count: number): JoinAttempts | null {
  let attempts: JoinAttempts | null = null;
  for (let i = 0; i < count; i++) attempts = recordFailedJoin(attempts, START + i * 1000);
  return attempts;
}

describe("Schutz vor Code-Raten (joinThrottle)", () => {
  it("erlaubt laut Vertrag 10 Fehlversuche pro Stunde", () => {
    expect(JOIN_THROTTLE_POLICY).toEqual({ maxFailures: 10, windowMillis: HOUR });
  });

  it("lässt ohne bisherige Versuche durch", () => {
    expect(isJoinBlocked(null, START)).toBe(false);
  });

  it("beginnt mit dem ersten Fehlversuch ein Fenster und zählt weiter", () => {
    expect(recordFailedJoin(null, START)).toEqual({ windowStartMillis: START, failures: 1 });
    expect(recordFailedJoin({ windowStartMillis: START, failures: 3 }, START + 10 * 60 * 1000)).toEqual({
      windowStartMillis: START,
      failures: 4,
    });
  });

  it("lässt nach 9 Fehlversuchen noch einen Versuch zu", () => {
    expect(isJoinBlocked(failures(9), START + 60_000)).toBe(false);
  });

  it("sperrt ab 10 Fehlversuchen innerhalb einer Stunde", () => {
    const attempts = failures(10);

    expect(attempts?.failures).toBe(10);
    expect(isJoinBlocked(attempts, START + 60_000)).toBe(true);
    expect(isJoinBlocked(attempts, START + HOUR - 1)).toBe(true);
  });

  it("gibt nach Ablauf der Stunde wieder frei und beginnt dann ein neues Fenster", () => {
    const attempts = failures(25);

    expect(isJoinBlocked(attempts, START + HOUR)).toBe(false);
    expect(recordFailedJoin(attempts, START + HOUR)).toEqual({ windowStartMillis: START + HOUR, failures: 1 });
  });

  it("behandelt einen Fensterbeginn minimal in der Zukunft (Uhrabweichung) als laufendes Fenster", () => {
    const attempts = { windowStartMillis: START + 500, failures: 10 };

    expect(isJoinBlocked(attempts, START)).toBe(true);
    expect(recordFailedJoin(attempts, START)).toEqual({ windowStartMillis: START + 500, failures: 11 });
  });

  it("verwendet eine übergebene Grenze", () => {
    const policy = { maxFailures: 2, windowMillis: 1_000 };
    const attempts = recordFailedJoin(recordFailedJoin(null, START, policy), START + 10, policy);

    expect(isJoinBlocked(attempts, START + 20, policy)).toBe(true);
    expect(isJoinBlocked(attempts, START + 1_000, policy)).toBe(false);
  });

  it("liefert das Ende des Fensters (expireAt = windowStart + 1 Stunde)", () => {
    expect(joinAttemptsExpiresAt({ windowStartMillis: START, failures: 3 })).toBe(START + HOUR);
    expect(joinAttemptsExpiresAt({ windowStartMillis: START, failures: 3 }, { maxFailures: 2, windowMillis: 1_000 })).toBe(
      START + 1_000,
    );
  });

  it("erkennt ein laufendes Fenster – unabhängig von der Zahl der Fehlversuche", () => {
    const attempts = { windowStartMillis: START, failures: 1 };

    expect(isJoinWindowActive(attempts, START + HOUR - 1)).toBe(true);
    expect(isJoinWindowActive(attempts, START + HOUR)).toBe(false);
    expect(isJoinWindowActive({ windowStartMillis: START + 500, failures: 1 }, START)).toBe(true);
  });
});

describe("Schlüssel des Zählers (joinThrottleKey)", () => {
  // SHA-256 (hex) von "google:109876543210987654321" – fest, damit ein geänderter Algorithmus auffällt
  // (sonst bekämen alle Nutzer nach einem Deploy unbemerkt frische Zähler).
  const HASHED_KEY = "82710d02b53970fb633e12dcd9fe3c7fe7be5c5931f9232b04ab1f79e7060bd5";

  it("verwendet bei Google-Anmeldung den SHA-256 von \"google:\" + sub – nicht die uid", () => {
    expect(joinThrottleKey({ uid: "uid-1", googleSubject: "109876543210987654321" })).toBe(HASHED_KEY);
  });

  it("bleibt für dasselbe Google-Konto gleich, auch mit neuer uid (Konto gelöscht und neu angelegt)", () => {
    const before = joinThrottleKey({ uid: "uid-alt", googleSubject: "109876543210987654321" });
    const after = joinThrottleKey({ uid: "uid-neu", googleSubject: "109876543210987654321" });

    expect(after).toBe(before);
    expect(joinThrottleKey({ uid: "uid-alt", googleSubject: "anderes-konto" })).not.toBe(before);
  });

  it("speichert die Google-Kennung nicht im Klartext", () => {
    const key = joinThrottleKey({ uid: "uid-1", googleSubject: "109876543210987654321" });

    expect(key).toMatch(/^[0-9a-f]{64}$/u);
    expect(key).not.toContain("109876543210987654321");
  });

  it("fällt ohne Google-Kennung auf die uid zurück", () => {
    expect(joinThrottleKey({ uid: "uid-1" })).toBe("uid-1");
    expect(joinThrottleKey({ uid: "uid-1", googleSubject: "" })).toBe("uid-1");
  });
});
