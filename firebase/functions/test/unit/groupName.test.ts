import { describe, expect, it } from "vitest";
import { resolveDisplayName } from "../../src/domain/displayName";
import { SocialError } from "../../src/domain/errors";
import { validateGroupName } from "../../src/domain/groupName";

describe("validateGroupName", () => {
  it("trimmt und akzeptiert 1–30 Zeichen", () => {
    expect(validateGroupName("  Stammtisch ")).toBe("Stammtisch");
    expect(validateGroupName("A")).toBe("A");
    expect(validateGroupName("x".repeat(30))).toBe("x".repeat(30));
  });

  it.each([[""], ["   "], [undefined], [null], [42]])("lehnt %j als leer ab", (name) => {
    expect(() => validateGroupName(name)).toThrowError(
      expect.objectContaining({ code: "invalid-argument", message: "Bitte gib der Runde einen Namen." }),
    );
  });

  it("lehnt mehr als 30 Zeichen ab", () => {
    expect(() => validateGroupName("x".repeat(31))).toThrowError(SocialError);
    expect(() => validateGroupName("x".repeat(31))).toThrowError(
      "Der Rundenname darf höchstens 30 Zeichen lang sein.",
    );
  });
});

describe("resolveDisplayName", () => {
  it("bevorzugt den Profilnamen", () => {
    expect(resolveDisplayName("Anna", "Anna Google")).toBe("Anna");
  });

  it("nimmt sonst den Namen aus dem Anmelde-Token", () => {
    expect(resolveDisplayName(null, "Anna Google")).toBe("Anna Google");
    expect(resolveDisplayName("   ", " Anna Google ")).toBe("Anna Google");
    expect(resolveDisplayName(42, "Anna Google")).toBe("Anna Google");
  });

  it("fällt auf „Unbekannt“ zurück", () => {
    expect(resolveDisplayName(null, undefined)).toBe("Unbekannt");
    expect(resolveDisplayName("", "")).toBe("Unbekannt");
  });

  it("kürzt auf 50 Zeichen, ohne Emojis zu zerteilen", () => {
    expect(resolveDisplayName("a".repeat(60), null)).toBe("a".repeat(50));
    const withEmoji = `${"a".repeat(49)}🍺`;
    expect(resolveDisplayName(withEmoji, null)).toBe("a".repeat(49));
  });
});
