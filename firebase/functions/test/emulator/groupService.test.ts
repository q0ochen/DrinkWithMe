import { Timestamp } from "firebase-admin/firestore";
import { beforeEach, describe, expect, it } from "vitest";
import type { AuthUserDeleter } from "../../src/accounts/accountService";
import { FirestoreAccountService } from "../../src/accounts/firestoreAccountService";
import type { InviteCodeGenerator } from "../../src/domain/inviteCode";
import { INVITE_CODE_ALPHABET, RandomInviteCodeGenerator } from "../../src/domain/inviteCode";
import { FirestoreProfileReader } from "../../src/firestore/firestoreProfileReader";
import { FirestoreGroupCleanup } from "../../src/groups/firestoreGroupCleanup";
import { FirestoreGroupService } from "../../src/groups/firestoreGroupService";
import { RecordingLogger } from "../unit/fakes";
import { adminDb, clearFirestore, shareDrinkAsAdmin } from "./support";

// Die Services direkt gegen den Firestore-Emulator – ohne Callables. Deckt ab, was über die
// Callables schwer erreichbar ist: Code-Kollisionen, Nebenläufigkeit, eine steuerbare Uhr
// für den Schutz vor Code-Raten und das seitenweise Löschen beim Konto-Löschen.

const db = adminDb();
const randomCodes = new RandomInviteCodeGenerator((bound) => Math.floor(Math.random() * bound));
const HOUR = 60 * 60 * 1000;
const START = Date.UTC(2026, 10, 9, 20, 0, 0);

/** Liefert die vorgegebenen Codes der Reihe nach. */
class ScriptedCodes implements InviteCodeGenerator {
  constructor(private readonly codes: string[]) {}

  next(): string {
    const code = this.codes.shift();
    if (code === undefined) throw new Error("Keine Codes mehr");
    return code;
  }
}

/** Eine Uhr, die nur weiterläuft, wenn der Test es sagt. */
class ManualClock {
  constructor(public now: number = START) {}

  readonly read = () => this.now;

  advance(milliseconds: number): void {
    this.now += milliseconds;
  }
}

function service(
  options: { codes?: InviteCodeGenerator; maxInviteCodeAttempts?: number; clock?: ManualClock } = {},
) {
  return new FirestoreGroupService(db, options.codes ?? randomCodes, new FirestoreProfileReader(db), {
    maxInviteCodeAttempts: options.maxInviteCodeAttempts ?? 10,
    ...(options.clock === undefined ? {} : { clock: options.clock.read }),
  });
}

/** Ein Code, den es nicht gibt (gültiges Format). */
function unknownCode(index: number): string {
  return `ZZZZZZ${INVITE_CODE_ALPHABET.charAt(index % INVITE_CODE_ALPHABET.length)}Z`;
}

async function joinAttempts(uid: string) {
  return (await db.doc(`joinAttempts/${uid}`).get()).data();
}

async function drinksIn(groupId: string): Promise<string[]> {
  return (await db.collection(`groups/${groupId}/drinks`).get()).docs.map((doc) => doc.id).sort();
}

beforeEach(async () => {
  await clearFirestore();
});

describe("FirestoreGroupService (Firestore-Emulator)", () => {
  it("weicht bei vergebenem Code auf einen neuen aus", async () => {
    await db.doc("inviteCodes/AAAAAAAA").set({ groupId: "alt" });

    const group = await service({ codes: new ScriptedCodes(["AAAAAAAA", "BBBBBBBB"]) }).createGroup(
      { uid: "anna" },
      "Runde",
    );

    expect(group.inviteCode).toBe("BBBBBBBB");
    expect((await db.doc("inviteCodes/AAAAAAAA").get()).data()).toEqual({ groupId: "alt" });
    expect((await db.doc("inviteCodes/BBBBBBBB").get()).data()).toEqual({ groupId: group.id });
  });

  it("gibt nach zu vielen Kollisionen mit aborted auf und legt nichts an", async () => {
    await db.doc("inviteCodes/AAAAAAAA").set({ groupId: "alt" });

    await expect(
      service({ codes: new ScriptedCodes(["AAAAAAAA", "AAAAAAAA", "AAAAAAAA"]), maxInviteCodeAttempts: 3 }).createGroup(
        { uid: "anna" },
        "Runde",
      ),
    ).rejects.toMatchObject({ code: "aborted" });
    expect((await db.collection("groups").get()).size).toBe(0);
  });

  it("speichert die Runde laut Datenmodell – mit 8-stelligem Code", async () => {
    await db.doc("users/anna").set({ displayName: "Anna", photoUrl: null, updatedAt: Timestamp.now() });

    const group = await service().createGroup({ uid: "anna", tokenName: "Anna Google" }, "  Stammtisch ");
    const stored = (await db.doc(`groups/${group.id}`).get()).data();

    expect(group.inviteCode).toMatch(/^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}$/u);
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

  it("hält das Limit von 50 Mitgliedern auch bei gleichzeitigen Beitritten ein (failed-precondition)", async () => {
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
    for (const result of rejected) expect(result.reason).toMatchObject({ code: "failed-precondition" });
    expect(((await db.doc(`groups/${group.id}`).get()).get("memberIds") as string[]).length).toBe(50);
    // Eine volle Runde ist kein Fehlversuch beim Code.
    expect(await joinAttempts("x1")).toBeUndefined();
  });

  it("löst die Runde beim Austritt des letzten Mitglieds in der Transaktion auf (Runde und Code)", async () => {
    const groups = service();
    const group = await groups.createGroup({ uid: "anna" }, "Kurz");

    await groups.leaveGroup({ uid: "anna" }, group.id);

    expect((await db.doc(`groups/${group.id}`).get()).exists).toBe(false);
    expect((await db.doc(`inviteCodes/${group.inviteCode}`).get()).exists).toBe(false);
  });

  it("löscht beim Auflösen keinen Code, der inzwischen einer anderen Runde gehört", async () => {
    const groups = service({ codes: new ScriptedCodes(["CCCCCCCC"]) });
    const group = await groups.createGroup({ uid: "anna" }, "Alt");
    await db.doc("inviteCodes/CCCCCCCC").set({ groupId: "andere-runde" });

    await groups.leaveGroup({ uid: "anna" }, group.id);

    expect((await db.doc("inviteCodes/CCCCCCCC").get()).data()).toEqual({ groupId: "andere-runde" });
  });

  it("verlässt mit leaveAllGroups alle Runden – mit derselben Logik wie leaveGroup", async () => {
    const groups = service();
    const solo = await groups.createGroup({ uid: "bob" }, "Allein");
    const owned = await groups.createGroup({ uid: "bob" }, "Eigene");
    const other = await groups.createGroup({ uid: "anna" }, "Fremde");
    const untouched = await groups.createGroup({ uid: "anna" }, "Ohne Bob");
    await groups.joinGroup({ uid: "anna" }, owned.inviteCode);
    await groups.joinGroup({ uid: "bob" }, other.inviteCode);

    expect(await groups.leaveAllGroups({ uid: "bob" })).toBe(3);

    expect((await db.doc(`groups/${solo.id}`).get()).exists).toBe(false);
    expect((await db.doc(`inviteCodes/${solo.inviteCode}`).get()).exists).toBe(false);
    expect((await db.doc(`groups/${owned.id}`).get()).data()).toMatchObject({ ownerId: "anna", memberIds: ["anna"] });
    expect((await db.doc(`groups/${other.id}`).get()).data()).toMatchObject({ ownerId: "anna", memberIds: ["anna"] });
    expect((await db.doc(`groups/${untouched.id}`).get()).get("memberIds")).toEqual(["anna"]);
    expect(await groups.leaveAllGroups({ uid: "bob" })).toBe(0);
  });
});

describe("Schutz vor Code-Raten (joinAttempts, steuerbare Uhr)", () => {
  it("zählt falsche Codes und sperrt ab 10 Fehlversuchen – auch für einen richtigen Code", async () => {
    const clock = new ManualClock();
    const groups = service({ clock });
    const group = await groups.createGroup({ uid: "anna" }, "Stammtisch");

    for (let i = 0; i < 10; i++) {
      await expect(groups.joinGroup({ uid: "mallory" }, unknownCode(i))).rejects.toMatchObject({ code: "not-found" });
      clock.advance(1_000);
    }
    expect(await joinAttempts("mallory")).toEqual({ windowStart: Timestamp.fromMillis(START), failures: 10 });

    await expect(groups.joinGroup({ uid: "mallory" }, group.inviteCode)).rejects.toMatchObject({
      code: "resource-exhausted",
      message: "Zu viele falsche Codes. Bitte warte eine Stunde und versuche es dann noch einmal.",
    });
    expect((await db.doc(`groups/${group.id}`).get()).get("memberIds")).toEqual(["anna"]);
    // Gesperrte Versuche zählen nicht weiter und verlängern das Fenster nicht.
    expect((await joinAttempts("mallory"))?.failures).toBe(10);
  });

  it("gibt nach einer Stunde wieder frei und beginnt dann ein neues Fenster", async () => {
    const clock = new ManualClock();
    const groups = service({ clock });
    const group = await groups.createGroup({ uid: "anna" }, "Stammtisch");
    for (let i = 0; i < 10; i++) {
      await expect(groups.joinGroup({ uid: "mallory" }, unknownCode(i))).rejects.toMatchObject({ code: "not-found" });
    }

    clock.advance(HOUR - 1);
    await expect(groups.joinGroup({ uid: "mallory" }, group.inviteCode)).rejects.toMatchObject({
      code: "resource-exhausted",
    });

    clock.advance(1);
    await expect(groups.joinGroup({ uid: "mallory" }, unknownCode(99))).rejects.toMatchObject({ code: "not-found" });
    expect(await joinAttempts("mallory")).toEqual({ windowStart: Timestamp.fromMillis(START + HOUR), failures: 1 });
    expect((await groups.joinGroup({ uid: "mallory" }, group.inviteCode)).members.map((m) => m.userId)).toEqual([
      "anna",
      "mallory",
    ]);
  });

  it("setzt den Zähler bei einem erfolgreichen Beitritt nicht zurück", async () => {
    const clock = new ManualClock();
    const groups = service({ clock });
    const own = await groups.createGroup({ uid: "mallory" }, "Eigene");
    const target = await groups.createGroup({ uid: "anna" }, "Ziel");

    for (let i = 0; i < 9; i++) {
      await expect(groups.joinGroup({ uid: "mallory" }, unknownCode(i))).rejects.toMatchObject({ code: "not-found" });
    }
    await groups.joinGroup({ uid: "mallory" }, own.inviteCode);
    await expect(groups.joinGroup({ uid: "mallory" }, unknownCode(9))).rejects.toMatchObject({ code: "not-found" });

    await expect(groups.joinGroup({ uid: "mallory" }, target.inviteCode)).rejects.toMatchObject({
      code: "resource-exhausted",
    });
  });

  it("zählt nur nicht gefundene Codes – keine Formatfehler – und jeden Nutzer für sich", async () => {
    const groups = service({ clock: new ManualClock() });

    await expect(groups.joinGroup({ uid: "bob" }, "K7MX2Q")).rejects.toMatchObject({ code: "invalid-argument" });
    await expect(groups.joinGroup({ uid: "bob" }, "kein Code")).rejects.toMatchObject({ code: "invalid-argument" });
    expect(await joinAttempts("bob")).toBeUndefined();

    await expect(groups.joinGroup({ uid: "bob" }, unknownCode(0))).rejects.toMatchObject({ code: "not-found" });
    expect((await joinAttempts("bob"))?.failures).toBe(1);
    expect(await joinAttempts("anna")).toBeUndefined();
  });

  it("zählt einen Code ohne Runde (verwaist) als Fehlversuch", async () => {
    await db.doc("inviteCodes/WWWWWWWW").set({ groupId: "gibt-es-nicht" });
    const groups = service({ clock: new ManualClock() });

    await expect(groups.joinGroup({ uid: "bob" }, "WWWWWWWW")).rejects.toMatchObject({ code: "not-found" });

    expect((await joinAttempts("bob"))?.failures).toBe(1);
  });

  it("zählt gleichzeitige Fehlversuche vollständig (Transaktion)", async () => {
    const groups = service({ clock: new ManualClock() });

    const results = await Promise.allSettled(
      Array.from({ length: 10 }, (_, i) => groups.joinGroup({ uid: "mallory" }, unknownCode(i))),
    );

    expect(results.every((result) => result.status === "rejected")).toBe(true);
    expect((await joinAttempts("mallory"))?.failures).toBe(10);
  });
});

describe("FirestoreGroupCleanup (Firestore-Emulator)", () => {
  it("löscht alle Getränke einer aufgelösten Runde und ist wiederholbar", async () => {
    await shareDrinkAsAdmin("weg", "d1", "anna");
    await shareDrinkAsAdmin("weg", "d2", "bob");
    const cleanup = new FirestoreGroupCleanup(db);

    expect(await cleanup.deleteDrinksOf("weg")).toBe(true);
    expect(await drinksIn("weg")).toEqual([]);
    expect(await cleanup.deleteDrinksOf("weg")).toBe(true);
  });

  it("lässt die Getränke einer bestehenden Runde in Ruhe", async () => {
    await db.doc("groups/lebt").set({ name: "Lebt", inviteCode: "LLLLLLLL", ownerId: "anna", memberIds: ["anna"], members: {} });
    await shareDrinkAsAdmin("lebt", "d1", "anna");

    expect(await new FirestoreGroupCleanup(db).deleteDrinksOf("lebt")).toBe(false);
    expect(await drinksIn("lebt")).toEqual(["anna_d1"]);
  });
});

describe("FirestoreAccountService (Firestore-Emulator)", () => {
  /** Merkt sich, was beim Löschen des Auth-Nutzers noch in Firestore lag. */
  class RecordingAuthUsers implements AuthUserDeleter {
    readonly deleted: { uid: string; remainingDrinks: number; profileExists: boolean }[] = [];

    async deleteUser(uid: string): Promise<void> {
      const remaining = await db.collectionGroup("drinks").where("authorId", "==", uid).get();
      const profileExists = (await db.doc(`users/${uid}`).get()).exists;
      this.deleted.push({ uid, remainingDrinks: remaining.size, profileExists });
    }
  }

  it("löscht alle eigenen Getränke seitenweise in allen Runden und den Auth-Nutzer zuletzt", async () => {
    const groups = service();
    const kept = await groups.createGroup({ uid: "anna" }, "Bleibt");
    await groups.joinGroup({ uid: "bob" }, kept.inviteCode);
    for (const drinkId of ["d1", "d2", "d3"]) await shareDrinkAsAdmin(kept.id, drinkId, "bob");
    await shareDrinkAsAdmin(kept.id, "d1", "anna");
    // Eine längst verlassene Runde: Bob ist kein Mitglied mehr, seine Getränke liegen noch dort.
    await shareDrinkAsAdmin("verlassen", "d4", "bob");
    await shareDrinkAsAdmin("verlassen", "d5", "bob");
    await db.doc("users/bob").set({ displayName: "Bob", photoUrl: null, updatedAt: Timestamp.now() });
    await db.doc("users/bob/tokens/bob-phone").set({ platform: "android", updatedAt: Timestamp.now() });
    await db.doc("joinAttempts/bob").set({ windowStart: Timestamp.now(), failures: 2 });
    const authUsers = new RecordingAuthUsers();
    const accounts = new FirestoreAccountService({
      db,
      groups,
      authUsers,
      logger: new RecordingLogger(),
      batchSize: 2,
    });

    await accounts.deleteAccount({ uid: "bob" });

    expect(authUsers.deleted).toEqual([{ uid: "bob", remainingDrinks: 0, profileExists: false }]);
    expect(await drinksIn(kept.id)).toEqual(["anna_d1"]);
    expect(await drinksIn("verlassen")).toEqual([]);
    expect((await db.doc(`groups/${kept.id}`).get()).get("memberIds")).toEqual(["anna"]);
    expect((await db.collection("users/bob/tokens").get()).size).toBe(0);
    expect((await db.doc("joinAttempts/bob").get()).exists).toBe(false);

    await accounts.deleteAccount({ uid: "bob" });
    expect(authUsers.deleted.map((entry) => entry.uid)).toEqual(["bob", "bob"]);
  });
});
