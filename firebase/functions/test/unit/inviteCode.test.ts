import { describe, expect, it } from "vitest";
import {
  INVITE_CODE_ALPHABET,
  INVITE_CODE_LENGTH,
  RandomInviteCodeGenerator,
  parseInviteCode,
} from "../../src/domain/inviteCode";

describe("parseInviteCode", () => {
  it("normalisiert wie InviteCode.parse im Kotlin-Kern", () => {
    expect(parseInviteCode(" k7m-x2q ")).toBe("K7MX2Q");
    expect(parseInviteCode("k7mx2q")).toBe("K7MX2Q");
    expect(parseInviteCode("K7 MX\t2Q")).toBe("K7MX2Q");
  });

  it("lehnt falsche Längen ab", () => {
    expect(parseInviteCode("K7MX2")).toBeNull();
    expect(parseInviteCode("K7MX2QA")).toBeNull();
    expect(parseInviteCode("")).toBeNull();
  });

  it("lehnt verwechselbare und fremde Zeichen ab", () => {
    for (const code of ["K7MX2O", "K7MX2I", "K7MX2L", "K7MX20", "K7MX21", "K7MX2!", "K7MX2Ä"]) {
      expect(parseInviteCode(code), code).toBeNull();
    }
  });

  it("lehnt Nicht-Texte ab", () => {
    for (const value of [undefined, null, 123456, {}, ["K7MX2Q"]]) {
      expect(parseInviteCode(value)).toBeNull();
    }
  });
});

describe("RandomInviteCodeGenerator", () => {
  it("bildet Codes aus der injizierten Zufallsquelle", () => {
    const indexes = [0, 1, 2, 28, 29, 30];
    const bounds: number[] = [];
    const generator = new RandomInviteCodeGenerator((bound) => {
      bounds.push(bound);
      return indexes.shift() ?? 0;
    });

    expect(generator.next()).toBe("ABC789");
    expect(bounds).toEqual(Array(INVITE_CODE_LENGTH).fill(INVITE_CODE_ALPHABET.length));
  });

  it("erzeugt immer gültige Codes", () => {
    const generator = new RandomInviteCodeGenerator((bound) => Math.floor(Math.random() * bound));
    for (let i = 0; i < 1_000; i++) {
      const code = generator.next();
      expect(parseInviteCode(code)).toBe(code);
    }
  });
});
