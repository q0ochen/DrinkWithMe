import { describe, expect, it } from "vitest";
import {
  INVITE_CODE_ALPHABET,
  INVITE_CODE_LENGTH,
  RandomInviteCodeGenerator,
  parseInviteCode,
} from "../../src/domain/inviteCode";

describe("parseInviteCode", () => {
  it("hat 8 Zeichen (wie InviteCode.LENGTH im Kotlin-Kern)", () => {
    expect(INVITE_CODE_LENGTH).toBe(8);
  });

  it("normalisiert wie InviteCode.parse im Kotlin-Kern", () => {
    expect(parseInviteCode(" k7mx-2qab ")).toBe("K7MX2QAB");
    expect(parseInviteCode("k7mx2qab")).toBe("K7MX2QAB");
    expect(parseInviteCode("K7 MX\t2Q AB")).toBe("K7MX2QAB");
  });

  it("lehnt falsche Längen ab – auch die alten 6-stelligen Codes", () => {
    expect(parseInviteCode("K7MX2Q")).toBeNull();
    expect(parseInviteCode("K7MX2QA")).toBeNull();
    expect(parseInviteCode("K7MX2QABC")).toBeNull();
    expect(parseInviteCode("")).toBeNull();
  });

  it("lehnt verwechselbare und fremde Zeichen ab", () => {
    for (const suffix of ["O", "I", "L", "0", "1", "!", "Ä"]) {
      const code = `K7MX2QA${suffix}`;
      expect(parseInviteCode(code), code).toBeNull();
    }
  });

  it("lehnt Nicht-Texte ab", () => {
    for (const value of [undefined, null, 12345678, {}, ["K7MX2QAB"]]) {
      expect(parseInviteCode(value)).toBeNull();
    }
  });
});

describe("RandomInviteCodeGenerator", () => {
  it("bildet Codes aus der injizierten Zufallsquelle", () => {
    const indexes = [0, 1, 2, 3, 27, 28, 29, 30];
    const bounds: number[] = [];
    const generator = new RandomInviteCodeGenerator((bound) => {
      bounds.push(bound);
      return indexes.shift() ?? 0;
    });

    expect(generator.next()).toBe("ABCD6789");
    expect(bounds).toEqual(Array(INVITE_CODE_LENGTH).fill(INVITE_CODE_ALPHABET.length));
  });

  it("erzeugt immer gültige 8-stellige Codes", () => {
    const generator = new RandomInviteCodeGenerator((bound) => Math.floor(Math.random() * bound));
    for (let i = 0; i < 1_000; i++) {
      const code = generator.next();
      expect(code).toHaveLength(8);
      expect(parseInviteCode(code)).toBe(code);
    }
  });
});
