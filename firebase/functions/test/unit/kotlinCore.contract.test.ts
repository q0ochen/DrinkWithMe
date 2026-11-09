import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { DRINK_CATEGORY_EMOJIS } from "../../src/domain/drinkCategory";
import { MAX_GROUP_NAME_LENGTH } from "../../src/domain/groupName";
import { INVITE_CODE_ALPHABET, INVITE_CODE_LENGTH } from "../../src/domain/inviteCode";

// Gleicht die gespiegelten Konstanten mit dem Kotlin-Kern ab, damit App und Server
// nicht unbemerkt auseinanderlaufen (Quelle der Wahrheit: android/core).
const KOTLIN_CORE = resolve(__dirname, "../../../../android/core/src/main/kotlin/com/drinkwithme/core");

function kotlinSource(relativePath: string): string {
  return readFileSync(resolve(KOTLIN_CORE, relativePath), "utf8");
}

describe("Abgleich mit dem Kotlin-Kern", () => {
  it("Kategorien und Emojis entsprechen DrinkCategory.kt", () => {
    const source = kotlinSource("domain/DrinkCategory.kt");
    const kotlinCategories: Record<string, string> = {};
    for (const [, name, emoji] of source.matchAll(/^\s*(\w+)\("[^"]*",\s*"([^"]*)"\)/gmu)) {
      if (name !== undefined && emoji !== undefined) kotlinCategories[name] = emoji;
    }

    expect(kotlinCategories).toEqual(DRINK_CATEGORY_EMOJIS);
  });

  it("Einladungscode entspricht InviteCode.kt", () => {
    const source = kotlinSource("social/InviteCode.kt");

    expect(source).toContain(`const val LENGTH = ${INVITE_CODE_LENGTH}`);
    expect(source).toContain(`const val ALPHABET = "${INVITE_CODE_ALPHABET}"`);
  });

  it("Höchstlänge des Rundennamens entspricht CreateGroupUseCase", () => {
    const source = kotlinSource("social/usecase/GroupUseCases.kt");

    expect(source).toContain(`const val MAXIMUM_GROUP_NAME_LENGTH = ${MAX_GROUP_NAME_LENGTH}`);
  });

  it("Fehlermeldungen entsprechen SocialExceptions.kt", () => {
    const source = kotlinSource("social/SocialExceptions.kt");

    expect(source).toContain('"Bitte gib der Runde einen Namen."');
    expect(source).toContain('"Der Rundenname darf höchstens $maximum Zeichen lang sein."');
    expect(source).toContain('"Der Code muss aus ${InviteCode.LENGTH} Zeichen bestehen, z. B. K7MX2Q."');
    expect(source).toContain('"Zu diesem Code gibt es keine Runde."');
  });
});
