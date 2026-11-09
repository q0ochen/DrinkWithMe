import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

// Prüft die Firestore-Konfiguration, die nur beim Deploy wirkt (der Emulator kennt keine Indizes).

const FIREBASE_DIR = resolve(__dirname, "../../..");

interface FieldOverride {
  collectionGroup: string;
  fieldPath: string;
  ttl?: boolean;
  indexes: { order?: string; arrayConfig?: string; queryScope?: string }[];
}

describe("firestore.indexes.json", () => {
  const spec = JSON.parse(readFileSync(resolve(FIREBASE_DIR, "firestore.indexes.json"), "utf8")) as {
    indexes: unknown[];
    fieldOverrides: FieldOverride[];
  };

  it("hat einen Collection-Group-Index für drinks.authorId (deleteAccount)", () => {
    const authorId = spec.fieldOverrides.find(
      (field) => field.collectionGroup === "drinks" && field.fieldPath === "authorId",
    );

    expect(authorId?.indexes).toContainEqual({ order: "ASCENDING", queryScope: "COLLECTION_GROUP" });
  });

  it("behält für drinks.authorId die automatischen Einzelfeld-Indizes der Sammlung", () => {
    const authorId = spec.fieldOverrides.find((field) => field.fieldPath === "authorId");

    expect(authorId?.indexes).toEqual(
      expect.arrayContaining([
        { order: "ASCENDING", queryScope: "COLLECTION" },
        { order: "DESCENDING", queryScope: "COLLECTION" },
        { arrayConfig: "CONTAINS", queryScope: "COLLECTION" },
      ]),
    );
  });

  it("löscht abgelaufene Zähler gegen Code-Raten per TTL-Richtlinie auf joinAttempts.expireAt", () => {
    const expireAt = spec.fieldOverrides.find(
      (field) => field.collectionGroup === "joinAttempts" && field.fieldPath === "expireAt",
    );

    expect(expireAt?.ttl).toBe(true);
    // Firestore empfiehlt, das TTL-Feld nicht zu indizieren (stetig wachsende Zeitstempel).
    expect(expireAt?.indexes).toEqual([]);
  });

  it("schaltet keine TTL-Richtlinie für andere Felder ein", () => {
    const ttlFields = spec.fieldOverrides.filter((field) => field.ttl === true).map((field) => field.fieldPath);

    expect(ttlFields).toEqual(["expireAt"]);
  });

  it("braucht keine zusammengesetzten Indizes", () => {
    expect(spec.indexes).toEqual([]);
  });
});

describe("firestore.rules", () => {
  const rules = readFileSync(resolve(FIREBASE_DIR, "firestore.rules"), "utf8");

  it("prüft die Token-ID wie das Muster ^[A-Za-z0-9_:-]{20,1024}$ aus dem Vertrag", () => {
    // RE2 erlaubt höchstens {…,1000} – deshalb Zeichen per Regex und Länge per size().
    expect(rules).toContain("token.size() >= 20 && token.size() <= 1024 && token.matches('^[A-Za-z0-9_:-]+$')");
  });

  it("verwendet in Mustern keine Wiederholung über 1000 (sonst kompilieren die Regeln nicht)", () => {
    const patterns = [...rules.matchAll(/\.matches\('([^']*)'\)/gu)].map(([, pattern]) => pattern ?? "");
    expect(patterns.length).toBeGreaterThan(0);
    for (const pattern of patterns) {
      for (const [quantifier, upper] of pattern.matchAll(/\{\d*,?(\d+)\}/gu)) {
        expect(Number(upper), `${pattern}: ${quantifier}`).toBeLessThanOrEqual(1000);
      }
    }
  });
});
