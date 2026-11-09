import { Timestamp } from "firebase-admin/firestore";
import { beforeEach, describe, expect, it } from "vitest";
import type { InviteCodeGenerator } from "../../src/domain/inviteCode";
import { RandomInviteCodeGenerator } from "../../src/domain/inviteCode";
import { FirestoreProfileReader } from "../../src/firestore/firestoreProfileReader";
import { FirestoreGroupService } from "../../src/groups/firestoreGroupService";
import { adminDb, clearFirestore } from "./support";

// Der FirestoreGroupService direkt gegen den Firestore-Emulator – ohne Functions-Emulator.
// Deckt ab, was über die Callables schwer erreichbar ist: Code-Kollisionen und Nebenläufigkeit.

const db = adminDb();
const randomCodes = new RandomInviteCodeGenerator((bound) => Math.floor(Math.random() * bound));

/** Liefert die vorgegebenen Codes der Reihe nach. */
class ScriptedCodes implements InviteCodeGenerator {
  constructor(private readonly codes: string[]) {}

  next(): string {
    const code = this.codes.shift();
    if (code === undefined) throw new Error("Keine Codes mehr");
    return code;
  }
}

function service(codes: InviteCodeGenerator = randomCodes, maxInviteCodeAttempts = 10) {
  return new FirestoreGroupService(db, codes, new FirestoreProfileReader(db), { maxInviteCodeAttempts });
}

beforeEach(async () => {
  await clearFirestore();
});

describe("FirestoreGroupService (Firestore-Emulator)", () => {
  it("weicht bei vergebenem Code auf einen neuen aus", async () => {
    await db.doc("inviteCodes/AAAAAA").set({ groupId: "alt" });

    const group = await service(new ScriptedCodes(["AAAAAA", "BBBBBB"])).createGroup({ uid: "anna" }, "Runde");

    expect(group.inviteCode).toBe("BBBBBB");
    expect((await db.doc("inviteCodes/AAAAAA").get()).data()).toEqual({ groupId: "alt" });
    expect((await db.doc("inviteCodes/BBBBBB").get()).data()).toEqual({ groupId: group.id });
  });

  it("gibt nach zu vielen Kollisionen mit aborted auf und legt nichts an", async () => {
    await db.doc("inviteCodes/AAAAAA").set({ groupId: "alt" });

    await expect(
      service(new ScriptedCodes(["AAAAAA", "AAAAAA", "AAAAAA"]), 3).createGroup({ uid: "anna" }, "Runde"),
    ).rejects.toMatchObject({ code: "aborted" });
    expect((await db.collection("groups").get()).size).toBe(0);
  });

  it("speichert die Runde laut Datenmodell", async () => {
    await db.doc("users/anna").set({ displayName: "Anna", photoUrl: null, updatedAt: Timestamp.now() });

    const group = await service().createGroup({ uid: "anna", tokenName: "Anna Google" }, "  Stammtisch ");
    const stored = (await db.doc(`groups/${group.id}`).get()).data();

    expect(stored).toEqual({
      name: "Stammtisch",
      inviteCode: group.inviteCode,
      ownerId: "anna",
      memberIds: ["anna"],
      members: { anna: { displayName: "Anna" } },
      createdAt: expect.any(Timestamp) as unknown,
    });
  });

  it("verliert bei gleichzeitigen Beitritten niemanden", async () => {
    const groups = service();
    const group = await groups.createGroup({ uid: "owner" }, "Party");
    const joiners = Array.from({ length: 10 }, (_, i) => `gast${i}`);

    await Promise.all(joiners.map((uid) => groups.joinGroup({ uid }, group.inviteCode)));

    const memberIds = (await db.doc(`groups/${group.id}`).get()).get("memberIds") as string[];
    expect(memberIds).toHaveLength(11);
    expect(new Set(memberIds)).toEqual(new Set(["owner", ...joiners]));
  });

  it("hält das Limit von 50 Mitgliedern auch bei gleichzeitigen Beitritten ein", async () => {
    const groups = service();
    const group = await groups.createGroup({ uid: "owner" }, "Fast voll");
    const members = Array.from({ length: 48 }, (_, i) => `m${i}`);
    await db.doc(`groups/${group.id}`).update({
      memberIds: ["owner", ...members],
      members: Object.fromEntries(["owner", ...members].map((id) => [id, { displayName: id }])),
    });

    const results = await Promise.allSettled(
      ["x1", "x2", "x3", "x4", "x5"].map((uid) => groups.joinGroup({ uid }, group.inviteCode)),
    );

    expect(results.filter((result) => result.status === "fulfilled")).toHaveLength(1);
    const rejected = results.filter((result) => result.status === "rejected");
    expect(rejected).toHaveLength(4);
    for (const result of rejected) expect(result.reason).toMatchObject({ code: "resource-exhausted" });
    expect(((await db.doc(`groups/${group.id}`).get()).get("memberIds") as string[]).length).toBe(50);
  });

  it("räumt beim Austritt des letzten Mitglieds alles auf – auch Getränke", async () => {
    const groups = service();
    const group = await groups.createGroup({ uid: "anna" }, "Kurz");
    await db.doc(`groups/${group.id}/drinks/d1`).set({ drinkId: "d1", authorId: "anna" });
    await db.doc(`groups/${group.id}/drinks/d2`).set({ drinkId: "d2", authorId: "anna" });

    await groups.leaveGroup({ uid: "anna" }, group.id);

    expect((await db.doc(`groups/${group.id}`).get()).exists).toBe(false);
    expect((await db.doc(`inviteCodes/${group.inviteCode}`).get()).exists).toBe(false);
    expect((await db.collection(`groups/${group.id}/drinks`).get()).size).toBe(0);
  });

  it("löscht beim Auflösen keinen Code, der inzwischen einer anderen Runde gehört", async () => {
    const groups = service(new ScriptedCodes(["CCCCCC"]));
    const group = await groups.createGroup({ uid: "anna" }, "Alt");
    await db.doc("inviteCodes/CCCCCC").set({ groupId: "andere-runde" });

    await groups.leaveGroup({ uid: "anna" }, group.id);

    expect((await db.doc("inviteCodes/CCCCCC").get()).data()).toEqual({ groupId: "andere-runde" });
  });
});
