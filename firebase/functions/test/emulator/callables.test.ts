import { createHash } from "node:crypto";
import { FieldValue, Timestamp } from "firebase-admin/firestore";
import type { FunctionsError } from "firebase/functions";
import { httpsCallable } from "firebase/functions";
import type { Functions } from "firebase/functions";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import type { GroupDto } from "../../src/domain/group";
import { parseInviteCode } from "../../src/domain/inviteCode";
import {
  adminAuth,
  adminDb,
  anonymousClient,
  clearAuth,
  clearFirestore,
  googleSignedInClient,
  shareDrinkAsAdmin,
  signedInClient,
  waitFor,
} from "./support";
import type { SignedInClient } from "./support";

// Die Callables über den Functions-Emulator – genau so, wie die App sie aufruft
// (Firebase.functions("europe-west3").getHttpsCallable(...)).

const db = adminDb();
const openClients: { close(): Promise<void> }[] = [];

beforeEach(async () => {
  await Promise.all([clearFirestore(), clearAuth()]);
});

afterEach(async () => {
  await Promise.all(openClients.splice(0).map((client) => client.close()));
});

async function user(uid: string, options: { profileName?: string; tokenName?: string } = {}) {
  if (options.profileName !== undefined) {
    await db.doc(`users/${uid}`).set({
      displayName: options.profileName,
      photoUrl: null,
      updatedAt: FieldValue.serverTimestamp(),
    });
  }
  const client = await signedInClient(uid, options.tokenName);
  openClients.push(client);
  return client;
}

/** Ein mit Google angemeldeter Client (wie die App); dieselbe Kennung nach „Konto löschen“ → neue uid. */
async function googleUser(googleSubject: string, displayName?: string) {
  const client = await googleSignedInClient(googleSubject, displayName);
  openClients.push(client);
  return client;
}

function anonymous() {
  const client = anonymousClient();
  openClients.push(client);
  return client;
}

async function call<Response>(client: { functions: Functions }, name: string, data: unknown): Promise<Response> {
  const result = await httpsCallable<unknown, Response>(client.functions, name)(data);
  return result.data;
}

async function callFailure(client: { functions: Functions }, name: string, data: unknown): Promise<FunctionsError> {
  try {
    await call(client, name, data);
  } catch (error) {
    return error as FunctionsError;
  }
  throw new Error(`${name} hätte fehlschlagen sollen`);
}

/** Die Meldung des Servers – das JS-SDK hängt clientseitig den HTTP-Status an („… [404]“). */
function serverMessage(error: FunctionsError): string {
  return error.message.replace(/ \[\d{3}\]$/u, "");
}

const createGroup = (client: SignedInClient, name: unknown) =>
  call<{ group: GroupDto }>(client, "createGroup", { name }).then((response) => response.group);
const joinGroup = (client: SignedInClient, code: unknown) =>
  call<{ group: GroupDto }>(client, "joinGroup", { code }).then((response) => response.group);
const leaveGroup = (client: SignedInClient, groupId: unknown) =>
  call<Record<string, never>>(client, "leaveGroup", { groupId });
const deleteAccount = (client: SignedInClient) => call<Record<string, never>>(client, "deleteAccount", {});

async function storedGroup(groupId: string) {
  return (await db.doc(`groups/${groupId}`).get()).data();
}

async function drinksIn(groupId: string): Promise<string[]> {
  return (await db.collection(`groups/${groupId}/drinks`).get()).docs.map((doc) => doc.id).sort();
}

/** Wartet, bis der Trigger onGroupDeleted die Getränke einer aufgelösten Runde gelöscht hat. */
function drinksCleanedUp(groupId: string): Promise<true> {
  return waitFor(
    async () => ((await drinksIn(groupId)).length === 0 ? true : undefined),
    `Aufräumen der Getränke von ${groupId}`,
  );
}

async function authUserExists(uid: string): Promise<boolean> {
  try {
    await adminAuth().getUser(uid);
    return true;
  } catch (error) {
    if ((error as { code?: unknown }).code === "auth/user-not-found") return false;
    throw error;
  }
}

/** Dokument-ID des Zählers laut Vertrag: SHA-256 (hex) von "google:" + Google-sub. */
function googleThrottleKey(googleSubject: string): string {
  return createHash("sha256").update(`google:${googleSubject}`).digest("hex");
}

async function joinAttempts(key: string) {
  return (await db.doc(`joinAttempts/${key}`).get()).data();
}

/** expireAt muss genau eine Stunde nach windowStart liegen (Feld der TTL-Richtlinie). */
function expectExpireAtOneHourAfterWindowStart(attempts: Record<string, unknown> | undefined): void {
  const windowStart = attempts?.windowStart as Timestamp;
  const expireAt = attempts?.expireAt as Timestamp;
  expect(expireAt.toMillis() - windowStart.toMillis()).toBe(60 * 60 * 1000);
}

/** Ein Code im richtigen Format, den es nicht gibt. */
function unknownCode(index: number): string {
  return `ZZZZZZ${"ABCDEFGHJK".charAt(index % 10)}Z`;
}

describe("Anmeldung", () => {
  it.each([
    ["createGroup", { name: "Stammtisch" }],
    ["joinGroup", { code: "K7MX2QAB" }],
    ["leaveGroup", { groupId: "g1" }],
    ["deleteAccount", {}],
  ])("%s verlangt eine Anmeldung (unauthenticated)", async (name, data) => {
    const error = await callFailure(anonymous(), name, data);

    expect(error.code).toBe("functions/unauthenticated");
    expect(serverMessage(error)).toBe("Bitte melde dich zuerst an.");
  });
});

describe("createGroup", () => {
  it("legt Runde und Einladungscode an und antwortet mit { group }", async () => {
    const anna = await user("anna", { profileName: "Anna", tokenName: "Anna Google" });

    const group = await createGroup(anna, "  Stammtisch ");

    expect(parseInviteCode(group.inviteCode)).toBe(group.inviteCode);
    expect(group).toEqual({
      id: expect.any(String) as unknown,
      name: "Stammtisch",
      inviteCode: group.inviteCode,
      ownerId: "anna",
      members: [{ userId: "anna", displayName: "Anna" }],
    });
    expect(await storedGroup(group.id)).toEqual({
      name: "Stammtisch",
      inviteCode: group.inviteCode,
      ownerId: "anna",
      memberIds: ["anna"],
      members: { anna: { displayName: "Anna" } },
      createdAt: expect.any(Timestamp) as unknown,
    });
    expect((await db.doc(`inviteCodes/${group.inviteCode}`).get()).data()).toEqual({ groupId: group.id });
  });

  it("nimmt ohne Profil den Namen aus dem Anmelde-Token", async () => {
    const bob = await user("bob", { tokenName: "Bob Google" });

    expect((await createGroup(bob, "Büro")).members).toEqual([{ userId: "bob", displayName: "Bob Google" }]);
  });

  it("nennt Mitglieder ohne jeden Namen „Unbekannt“", async () => {
    const nobody = await user("nobody");

    expect((await createGroup(nobody, "Büro")).members).toEqual([{ userId: "nobody", displayName: "Unbekannt" }]);
  });

  it("erzeugt für jede Runde einen eigenen Code", async () => {
    const anna = await user("anna", { profileName: "Anna" });

    const codes = await Promise.all(
      Array.from({ length: 5 }, (_, i) => createGroup(anna, `Runde ${i}`).then((group) => group.inviteCode)),
    );

    expect(new Set(codes).size).toBe(5);
  });

  it.each([
    ["leer", "", "Bitte gib der Runde einen Namen."],
    ["nur Leerzeichen", "   ", "Bitte gib der Runde einen Namen."],
    ["fehlend", undefined, "Bitte gib der Runde einen Namen."],
    ["31 Zeichen", "x".repeat(31), "Der Rundenname darf höchstens 30 Zeichen lang sein."],
  ])("lehnt ungültige Namen ab (%s) – invalid-argument", async (_, name, message) => {
    const anna = await user("anna", { profileName: "Anna" });

    const error = await callFailure(anna, "createGroup", name === undefined ? {} : { name });

    expect(error.code).toBe("functions/invalid-argument");
    expect(serverMessage(error)).toBe(message);
    expect((await db.collection("groups").get()).size).toBe(0);
  });
});

describe("joinGroup", () => {
  it("tritt mit normalisiertem Code bei", async () => {
    const anna = await user("anna", { profileName: "Anna" });
    const bob = await user("bob", { profileName: "Bob" });
    const created = await createGroup(anna, "Stammtisch");
    expect(created.inviteCode).toHaveLength(8);
    const typed = `${created.inviteCode.slice(0, 4).toLowerCase()}-${created.inviteCode.slice(4).toLowerCase()} `;

    const joined = await joinGroup(bob, typed);

    expect(joined).toEqual({
      ...created,
      members: [
        { userId: "anna", displayName: "Anna" },
        { userId: "bob", displayName: "Bob" },
      ],
    });
    expect(await storedGroup(created.id)).toMatchObject({
      ownerId: "anna",
      memberIds: ["anna", "bob"],
      members: { anna: { displayName: "Anna" }, bob: { displayName: "Bob" } },
    });
  });

  it("ist idempotent – erneuter Beitritt liefert dieselbe Runde", async () => {
    const anna = await user("anna", { profileName: "Anna" });
    const bob = await user("bob", { profileName: "Bob" });
    const created = await createGroup(anna, "Stammtisch");

    const first = await joinGroup(bob, created.inviteCode);
    const second = await joinGroup(bob, created.inviteCode);
    const owner = await joinGroup(anna, created.inviteCode);

    expect(second).toEqual(first);
    expect(owner).toEqual(first);
    expect((await storedGroup(created.id))?.memberIds).toEqual(["anna", "bob"]);
  });

  it("meldet unbekannte Codes mit not-found", async () => {
    const bob = await user("bob", { profileName: "Bob" });

    const error = await callFailure(bob, "joinGroup", { code: "ZZZZZZZZ" });

    expect(error.code).toBe("functions/not-found");
    expect(serverMessage(error)).toBe("Zu diesem Code gibt es keine Runde.");
  });

  it.each([["K7MX2Q"], ["K7MX2QA"], ["K7MX2QABC"], ["K7MX2QAO"], [""], [undefined], [12345678]])(
    "lehnt das Format %j mit invalid-argument ab",
    async (code) => {
      const bob = await user("bob", { profileName: "Bob" });

      const error = await callFailure(bob, "joinGroup", code === undefined ? {} : { code });

      expect(error.code).toBe("functions/invalid-argument");
      expect(serverMessage(error)).toBe("Der Code muss aus 8 Zeichen bestehen, z. B. K7MX2QAB.");
    },
  );

  it("lehnt das 51. Mitglied mit failed-precondition ab", async () => {
    const memberIds = Array.from({ length: 50 }, (_, i) => `m${i}`);
    await db.doc("groups/voll").set({
      name: "Voll",
      inviteCode: "VVVVVVVV",
      ownerId: "m0",
      memberIds,
      members: Object.fromEntries(memberIds.map((id) => [id, { displayName: id }])),
      createdAt: FieldValue.serverTimestamp(),
    });
    await db.doc("inviteCodes/VVVVVVVV").set({ groupId: "voll" });
    const bob = await user("bob", { profileName: "Bob" });

    const error = await callFailure(bob, "joinGroup", { code: "VVVVVVVV" });

    expect(error.code).toBe("functions/failed-precondition");
    expect(serverMessage(error)).toBe("Diese Runde ist voll – es können höchstens 50 Personen mitmachen.");
    expect((await storedGroup("voll"))?.memberIds).toHaveLength(50);
  });

  it("sperrt nach 10 falschen Codes mit resource-exhausted – auch für einen richtigen Code", async () => {
    const anna = await user("anna", { profileName: "Anna" });
    const mallory = await user("mallory", { profileName: "Mallory" });
    const group = await createGroup(anna, "Stammtisch");

    for (let i = 0; i < 10; i++) {
      const miss = await callFailure(mallory, "joinGroup", { code: unknownCode(i) });
      expect(miss.code).toBe("functions/not-found");
    }
    // Ohne Google-Identität (eigenes Token) zählt der Server pro uid.
    const attempts = await joinAttempts("mallory");
    expect(attempts).toEqual({
      windowStart: expect.any(Timestamp) as unknown,
      failures: 10,
      expireAt: expect.any(Timestamp) as unknown,
    });
    expectExpireAtOneHourAfterWindowStart(attempts);

    const blocked = await callFailure(mallory, "joinGroup", { code: group.inviteCode });

    expect(blocked.code).toBe("functions/resource-exhausted");
    expect(serverMessage(blocked)).toBe(
      "Zu viele falsche Codes. Bitte warte eine Stunde und versuche es dann noch einmal.",
    );
    expect((await storedGroup(group.id))?.memberIds).toEqual(["anna"]);
  });

  it("gibt nach Ablauf des Zeitfensters (1 Stunde) wieder frei", async () => {
    const anna = await user("anna", { profileName: "Anna" });
    const mallory = await user("mallory", { profileName: "Mallory" });
    const group = await createGroup(anna, "Stammtisch");
    await db.doc("joinAttempts/mallory").set({
      windowStart: Timestamp.fromMillis(Date.now() - 61 * 60 * 1000),
      failures: 10,
    });

    const joined = await joinGroup(mallory, group.inviteCode);

    expect(joined.members.map((member) => member.userId)).toEqual(["anna", "mallory"]);
  });
});

describe("leaveGroup", () => {
  it("entfernt ein Mitglied, der Besitzer bleibt", async () => {
    const anna = await user("anna", { profileName: "Anna" });
    const bob = await user("bob", { profileName: "Bob" });
    const group = await createGroup(anna, "Stammtisch");
    await joinGroup(bob, group.inviteCode);

    expect(await leaveGroup(bob, group.id)).toEqual({});

    expect(await storedGroup(group.id)).toMatchObject({
      ownerId: "anna",
      memberIds: ["anna"],
      members: { anna: { displayName: "Anna" } },
    });
  });

  it("überträgt den Besitz, wenn der Besitzer geht", async () => {
    const anna = await user("anna", { profileName: "Anna" });
    const bob = await user("bob", { profileName: "Bob" });
    const cem = await user("cem", { profileName: "Cem" });
    const group = await createGroup(anna, "Stammtisch");
    await joinGroup(bob, group.inviteCode);
    await joinGroup(cem, group.inviteCode);

    await leaveGroup(anna, group.id);

    const stored = await storedGroup(group.id);
    expect(stored).toMatchObject({ ownerId: "bob", memberIds: ["bob", "cem"] });
    expect(Object.keys(stored?.members as object).sort()).toEqual(["bob", "cem"]);
    expect((await joinGroup(bob, group.inviteCode)).ownerId).toBe("bob");
  });

  it("ist idempotent – auch für Nicht-Mitglieder und unbekannte Runden", async () => {
    const anna = await user("anna", { profileName: "Anna" });
    const bob = await user("bob", { profileName: "Bob" });
    const carol = await user("carol", { profileName: "Carol" });
    const group = await createGroup(anna, "Stammtisch");
    await joinGroup(bob, group.inviteCode);

    expect(await leaveGroup(bob, group.id)).toEqual({});
    expect(await leaveGroup(bob, group.id)).toEqual({});
    expect(await leaveGroup(carol, group.id)).toEqual({});
    expect(await leaveGroup(carol, "gibtEsNicht")).toEqual({});

    expect((await storedGroup(group.id))?.memberIds).toEqual(["anna"]);
  });

  it("löscht beim letzten Mitglied Runde und Code; die Getränke räumt onGroupDeleted auf", async () => {
    const anna = await user("anna", { profileName: "Anna" });
    const group = await createGroup(anna, "Kurz");
    for (const drinkId of ["d1", "d2"]) await shareDrinkAsAdmin(group.id, drinkId, "anna");

    expect(await leaveGroup(anna, group.id)).toEqual({});

    expect((await db.doc(`groups/${group.id}`).get()).exists).toBe(false);
    expect((await db.doc(`inviteCodes/${group.inviteCode}`).get()).exists).toBe(false);
    await drinksCleanedUp(group.id);
    const error = await callFailure(anna, "joinGroup", { code: group.inviteCode });
    expect(error.code).toBe("functions/not-found");
  });

  it.each([["a/b"], [""], [undefined], [42]])("lehnt die Runden-ID %j mit invalid-argument ab", async (groupId) => {
    const anna = await user("anna", { profileName: "Anna" });

    const error = await callFailure(anna, "leaveGroup", groupId === undefined ? {} : { groupId });

    expect(error.code).toBe("functions/invalid-argument");
  });
});

describe("deleteAccount", () => {
  it("löscht Mitgliedschaften, alle eigenen Getränke (auch in verlassenen Runden), Profil, Tokens, Zähler und Auth-Nutzer", async () => {
    const anna = await user("anna", { profileName: "Anna" });
    const bob = await user("bob", { profileName: "Bob" });
    const left = await createGroup(anna, "Verlassen");
    const shared = await createGroup(anna, "Gemeinsam");
    const solo = await createGroup(bob, "Allein");
    await joinGroup(bob, left.inviteCode);
    await joinGroup(bob, shared.inviteCode);
    await shareDrinkAsAdmin(left.id, "alt-1", "bob");
    await shareDrinkAsAdmin(left.id, "alt-2", "bob");
    await leaveGroup(bob, left.id);
    await shareDrinkAsAdmin(shared.id, "neu", "bob");
    await shareDrinkAsAdmin(shared.id, "annas", "anna");
    await shareDrinkAsAdmin(solo.id, "allein", "bob");
    await db.doc("users/bob/tokens/bob-phone").set({ platform: "android", updatedAt: FieldValue.serverTimestamp() });
    // Abgelaufenes Fenster: Der Zähler wird sofort mit gelöscht.
    await db.doc("joinAttempts/bob").set({ windowStart: Timestamp.fromMillis(Date.now() - 61 * 60 * 1000), failures: 4 });

    expect(await deleteAccount(bob)).toEqual({});

    expect(await drinksIn(left.id)).toEqual([]);
    expect(await drinksIn(shared.id)).toEqual(["anna_annas"]);
    expect(await storedGroup(left.id)).toMatchObject({ memberIds: ["anna"] });
    expect(await storedGroup(shared.id)).toMatchObject({ ownerId: "anna", memberIds: ["anna"] });
    expect(Object.keys((await storedGroup(shared.id))?.members as object)).toEqual(["anna"]);
    expect((await db.doc(`groups/${solo.id}`).get()).exists).toBe(false);
    expect((await db.doc(`inviteCodes/${solo.inviteCode}`).get()).exists).toBe(false);
    await drinksCleanedUp(solo.id);
    expect((await db.doc("users/bob").get()).exists).toBe(false);
    expect((await db.collection("users/bob/tokens").get()).size).toBe(0);
    expect((await db.doc("joinAttempts/bob").get()).exists).toBe(false);
    expect(await authUserExists("bob")).toBe(false);

    expect((await db.doc("users/anna").get()).exists).toBe(true);
    expect(await authUserExists("anna")).toBe(true);
  });

  it("ist idempotent – ein zweiter Aufruf (auch nach gelöschtem Auth-Nutzer) gelingt", async () => {
    const bob = await user("bob", { profileName: "Bob" });
    const group = await createGroup(bob, "Allein");

    expect(await deleteAccount(bob)).toEqual({});
    expect(await deleteAccount(bob)).toEqual({});

    expect((await db.doc(`groups/${group.id}`).get()).exists).toBe(false);
    expect(await authUserExists("bob")).toBe(false);
  });

  it("behält einen Zähler mit laufendem Fenster (bis expireAt, dann TTL) – auch einen ohne expireAt", async () => {
    const bob = await user("bob", { profileName: "Bob" });
    const windowStart = Timestamp.fromMillis(Date.now() - 10 * 60 * 1000);
    await db.doc("joinAttempts/bob").set({ windowStart, failures: 4 });

    expect(await deleteAccount(bob)).toEqual({});

    expect(await joinAttempts("bob")).toEqual({
      windowStart,
      failures: 4,
      expireAt: Timestamp.fromMillis(windowStart.toMillis() + 60 * 60 * 1000),
    });
    expect((await db.doc("users/bob").get()).exists).toBe(false);
    expect(await authUserExists("bob")).toBe(false);
  });

  it("gelingt auch für ein Konto ohne Daten", async () => {
    const nobody = await user("nobody");

    expect(await deleteAccount(nobody)).toEqual({});

    expect(await authUserExists("nobody")).toBe(false);
  });
});

describe("Schutz vor Code-Raten über „Konto löschen“ hinweg (Google-Anmeldung)", () => {
  it("zählt pro Google-Konto – unter dem Hash der Google-Kennung, nicht unter der uid", async () => {
    const anna = await user("anna", { profileName: "Anna" });
    await createGroup(anna, "Stammtisch");
    const mallory = await googleUser("google-sub-mallory", "Mallory");

    const miss = await callFailure(mallory, "joinGroup", { code: unknownCode(0) });

    expect(miss.code).toBe("functions/not-found");
    const attempts = await joinAttempts(googleThrottleKey("google-sub-mallory"));
    expect(attempts).toMatchObject({ failures: 1 });
    expectExpireAtOneHourAfterWindowStart(attempts);
    expect(await joinAttempts(mallory.uid)).toBeUndefined();
  });

  it("die Sperre übersteht deleteAccount und die erneute Anmeldung mit demselben Google-Konto", async () => {
    const anna = await user("anna", { profileName: "Anna" });
    const group = await createGroup(anna, "Stammtisch");
    const mallory = await googleUser("google-sub-mallory", "Mallory");
    const key = googleThrottleKey("google-sub-mallory");
    for (let i = 0; i < 10; i++) {
      expect((await callFailure(mallory, "joinGroup", { code: unknownCode(i) })).code).toBe("functions/not-found");
    }
    const blockedAttempts = await joinAttempts(key);

    expect(await deleteAccount(mallory)).toEqual({});

    // Das Konto ist weg, der Zähler bleibt bis expireAt (danach löscht ihn die TTL-Richtlinie).
    expect(await authUserExists(mallory.uid)).toBe(false);
    expect(await joinAttempts(key)).toEqual(blockedAttempts);
    // Mit dem alten, noch gültigen ID-Token: weiterhin gesperrt.
    expect((await callFailure(mallory, "joinGroup", { code: group.inviteCode })).code).toBe(
      "functions/resource-exhausted",
    );
    // Neu angemeldet: neue uid, aber dasselbe Google-Konto – weiterhin gesperrt, auch mit richtigem Code.
    const again = await googleUser("google-sub-mallory", "Mallory");
    expect(again.uid).not.toBe(mallory.uid);
    const blocked = await callFailure(again, "joinGroup", { code: group.inviteCode });

    expect(blocked.code).toBe("functions/resource-exhausted");
    expect((await storedGroup(group.id))?.memberIds).toEqual(["anna"]);
    expect((await joinAttempts(key))?.failures).toBe(10);
  });

  it("ein anderes Google-Konto hat seinen eigenen Zähler", async () => {
    const anna = await user("anna", { profileName: "Anna" });
    const group = await createGroup(anna, "Stammtisch");
    const mallory = await googleUser("google-sub-mallory", "Mallory");
    for (let i = 0; i < 10; i++) await callFailure(mallory, "joinGroup", { code: unknownCode(i) });

    const bob = await googleUser("google-sub-bob", "Bob");
    const joined = await joinGroup(bob, group.inviteCode);

    expect(joined.members.map((member) => member.userId)).toEqual(["anna", bob.uid]);
  });

  it("deleteAccount löscht einen abgelaufenen Zähler des Google-Kontos sofort", async () => {
    const mallory = await googleUser("google-sub-mallory", "Mallory");
    const key = googleThrottleKey("google-sub-mallory");
    await db.doc(`joinAttempts/${key}`).set({
      windowStart: Timestamp.fromMillis(Date.now() - 61 * 60 * 1000),
      failures: 10,
      expireAt: Timestamp.fromMillis(Date.now() - 60 * 1000),
    });

    expect(await deleteAccount(mallory)).toEqual({});

    expect(await joinAttempts(key)).toBeUndefined();
  });
});
