import { describe, expect, it } from "vitest";
import { SocialError } from "../../src/domain/errors";
import { newGroup, planJoin, planLeave } from "../../src/domain/membership";
import { groupOf } from "./fakes";

describe("newGroup", () => {
  it("macht den Ersteller zum Besitzer und einzigen Mitglied", () => {
    expect(newGroup("Stammtisch", "K7MX2Q", { userId: "anna", displayName: "Anna" })).toEqual({
      name: "Stammtisch",
      inviteCode: "K7MX2Q",
      ownerId: "anna",
      memberIds: ["anna"],
      members: { anna: { displayName: "Anna" } },
    });
  });
});

describe("planJoin", () => {
  it("hängt neue Mitglieder hinten an", () => {
    const plan = planJoin(groupOf(["a"]), { userId: "b", displayName: "Bea" });

    expect(plan.kind).toBe("join");
    expect(plan.group.memberIds).toEqual(["a", "b"]);
    expect(plan.group.members.b).toEqual({ displayName: "Bea" });
    expect(plan.group.ownerId).toBe("a");
  });

  it("ist idempotent für bestehende Mitglieder", () => {
    const group = groupOf(["a", "b"]);
    const plan = planJoin(group, { userId: "b", displayName: "Neuer Name" });

    expect(plan).toEqual({ kind: "already-member", group });
  });

  it("lässt bestehende Mitglieder auch in einer vollen Runde durch", () => {
    const full = groupOf(Array.from({ length: 50 }, (_, i) => `u${i}`));

    expect(planJoin(full, { userId: "u7", displayName: "X" }).kind).toBe("already-member");
  });

  it("lehnt das 51. Mitglied mit resource-exhausted ab", () => {
    const full = groupOf(Array.from({ length: 50 }, (_, i) => `u${i}`));

    expect(() => planJoin(full, { userId: "neu", displayName: "Neu" })).toThrowError(
      expect.objectContaining({ code: "resource-exhausted" }),
    );
    expect(() => planJoin(full, { userId: "neu", displayName: "Neu" })).toThrowError(SocialError);
  });

  it("nimmt das 50. Mitglied noch auf", () => {
    const almostFull = groupOf(Array.from({ length: 49 }, (_, i) => `u${i}`));

    expect(planJoin(almostFull, { userId: "neu", displayName: "Neu" }).group.memberIds).toHaveLength(50);
  });
});

describe("planLeave", () => {
  it("ist idempotent für Nicht-Mitglieder", () => {
    expect(planLeave(groupOf(["a", "b"]), "c")).toEqual({ kind: "not-member" });
  });

  it("entfernt ein Mitglied, der Besitzer bleibt", () => {
    const plan = planLeave(groupOf(["a", "b", "c"]), "b");

    expect(plan).toEqual({
      kind: "update",
      group: {
        name: "Stammtisch",
        inviteCode: "K7MX2Q",
        ownerId: "a",
        memberIds: ["a", "c"],
        members: { a: { displayName: "Name a" }, c: { displayName: "Name c" } },
      },
    });
  });

  it("überträgt den Besitz an das dienstälteste verbleibende Mitglied", () => {
    const plan = planLeave(groupOf(["a", "b", "c"]), "a");

    expect(plan.kind).toBe("update");
    if (plan.kind === "update") {
      expect(plan.group.ownerId).toBe("b");
      expect(plan.group.memberIds).toEqual(["b", "c"]);
    }
  });

  it("löst die Runde auf, wenn das letzte Mitglied geht", () => {
    expect(planLeave(groupOf(["a"]), "a")).toEqual({ kind: "delete-group" });
  });
});
