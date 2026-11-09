import { Timestamp } from "firebase-admin/firestore";
import { describe, expect, it } from "vitest";
import { JOIN_THROTTLE_POLICY } from "../../src/domain/joinThrottle";
import { parseJoinAttempts, toJoinAttemptsDocument } from "../../src/firestore/joinAttemptsDocument";

const HOUR = 60 * 60 * 1000;
const START = Date.UTC(2026, 10, 9, 12, 0, 0);

describe("joinAttempts-Dokument", () => {
  it("schreibt laut Vertrag windowStart, failures und expireAt = windowStart + 1 Stunde (TTL)", () => {
    expect(toJoinAttemptsDocument({ windowStartMillis: START, failures: 4 }, JOIN_THROTTLE_POLICY)).toEqual({
      windowStart: Timestamp.fromMillis(START),
      failures: 4,
      expireAt: Timestamp.fromMillis(START + HOUR),
    });
  });

  it("richtet expireAt nach der übergebenen Fensterlänge", () => {
    const document = toJoinAttemptsDocument({ windowStartMillis: START, failures: 1 }, { maxFailures: 2, windowMillis: 1_000 });

    expect(document.expireAt).toEqual(Timestamp.fromMillis(START + 1_000));
  });

  it("liest windowStart und failures – mit und ohne expireAt (ältere Zähler)", () => {
    const expected = { windowStartMillis: START, failures: 4 };

    expect(parseJoinAttempts(toJoinAttemptsDocument(expected, JOIN_THROTTLE_POLICY))).toEqual(expected);
    expect(parseJoinAttempts({ windowStart: Timestamp.fromMillis(START), failures: 4 })).toEqual(expected);
  });

  it("behandelt fehlende oder kaputte Dokumente als „keine Versuche“", () => {
    expect(parseJoinAttempts(undefined)).toBeNull();
    expect(parseJoinAttempts({ failures: 4 })).toBeNull();
    expect(parseJoinAttempts({ windowStart: START, failures: 4 })).toBeNull();
    expect(parseJoinAttempts({ windowStart: Timestamp.fromMillis(START), failures: 1.5 })).toBeNull();
    expect(parseJoinAttempts({ windowStart: Timestamp.fromMillis(START), failures: "4" })).toBeNull();
  });
});
