import { describe, expect, it } from "vitest";
import { JOIN_THROTTLE_POLICY, isJoinBlocked, recordFailedJoin } from "../../src/domain/joinThrottle";
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
});
