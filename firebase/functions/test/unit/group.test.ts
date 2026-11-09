import { describe, expect, it } from "vitest";
import { parseGroupId, parseGroupState, toGroupDto } from "../../src/domain/group";

describe("toGroupDto", () => {
  it("bildet das Dokument auf GroupDto ab – Mitglieder in Beitrittsreihenfolge", () => {
    const dto = toGroupDto("g1", {
      name: "Stammtisch",
      inviteCode: "K7MX2Q",
      ownerId: "bob",
      memberIds: ["bob", "anna"],
      members: { anna: { displayName: "Anna" }, bob: { displayName: "Bob" } },
    });

    expect(dto).toEqual({
      id: "g1",
      name: "Stammtisch",
      inviteCode: "K7MX2Q",
      ownerId: "bob",
      members: [
        { userId: "bob", displayName: "Bob" },
        { userId: "anna", displayName: "Anna" },
      ],
    });
  });

  it("nennt Mitglieder ohne Eintrag „Unbekannt“", () => {
    const dto = toGroupDto("g1", {
      name: "R",
      inviteCode: "K7MX2Q",
      ownerId: "a",
      memberIds: ["a"],
      members: {},
    });

    expect(dto.members).toEqual([{ userId: "a", displayName: "Unbekannt" }]);
  });
});

describe("parseGroupState", () => {
  const valid = {
    name: "Stammtisch",
    inviteCode: "K7MX2Q",
    ownerId: "a",
    memberIds: ["a", "b"],
    members: { a: { displayName: "Anna" }, b: { displayName: 7 }, c: { displayName: "Ehemalig" } },
    createdAt: new Date(),
  };

  it("liest gültige Daten und ergänzt fehlende Namen", () => {
    expect(parseGroupState(valid)).toEqual({
      name: "Stammtisch",
      inviteCode: "K7MX2Q",
      ownerId: "a",
      memberIds: ["a", "b"],
      members: { a: { displayName: "Anna" }, b: { displayName: "Unbekannt" } },
    });
  });

  it.each([
    ["kein Objekt", undefined],
    ["ohne Namen", { ...valid, name: undefined }],
    ["memberIds kein Array", { ...valid, memberIds: "a" }],
    ["memberIds mit Zahl", { ...valid, memberIds: ["a", 1] }],
    ["ohne Besitzer", { ...valid, ownerId: null }],
  ])("liefert null: %s", (_, data) => {
    expect(parseGroupState(data)).toBeNull();
  });
});

describe("parseGroupId", () => {
  it("akzeptiert Firestore-IDs", () => {
    expect(parseGroupId("Xy3kP9aBcDeFgHiJkLmN")).toBe("Xy3kP9aBcDeFgHiJkLmN");
    expect(parseGroupId("gruppe-1_a")).toBe("gruppe-1_a");
  });

  it.each([[""], ["a/b"], ["../x"], ["__name__"], ["x".repeat(129)], [42], [null], ["ä"]])(
    "lehnt %j ab",
    (value) => {
      expect(parseGroupId(value)).toBeNull();
    },
  );
});
