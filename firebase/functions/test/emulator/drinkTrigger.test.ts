import { randomUUID } from "node:crypto";
import { FieldValue, Timestamp } from "firebase-admin/firestore";
import type { DocumentData } from "firebase-admin/firestore";
import { beforeEach, describe, expect, it } from "vitest";
import {
  EMULATOR_MALFORMED_TOKEN_PREFIX,
  EMULATOR_OUTBOX_COLLECTION,
  EMULATOR_UNREGISTERED_TOKEN_PREFIX,
} from "../../src/notifications/emulatorOutboxSender";
import { adminDb, clearFirestore, shareDrinkAsAdmin, waitFor } from "./support";

// Die Trigger onGroupDrinkCreated und onGroupDeleted über Firestore- und Functions-Emulator.
// FCM lässt sich nicht emulieren: Im Emulator schreibt die Funktion jede Nachricht in `emulatorOutbox`.

const db = adminDb();

beforeEach(async () => {
  await clearFirestore();
});

/** Eine eindeutige Runden-ID je Test – verspätete Trigger früherer Tests stören so nicht. */
function newGroupId(): string {
  return `g-${randomUUID()}`;
}

/** Tokens je Nutzer, ältestes zuerst (`updatedAt` steigt in Listenreihenfolge). */
async function seedGroup(groupId: string, memberIds: string[], tokens: Record<string, string[]>) {
  await db.doc(`groups/${groupId}`).set({
    name: "Stammtisch",
    inviteCode: "TTTTTTTT",
    ownerId: memberIds[0],
    memberIds,
    members: Object.fromEntries(memberIds.map((id) => [id, { displayName: id }])),
    createdAt: FieldValue.serverTimestamp(),
  });
  const base = Date.now() - 60 * 60 * 1000;
  for (const [userId, userTokens] of Object.entries(tokens)) {
    for (const [index, token] of userTokens.entries()) {
      await db
        .doc(`users/${userId}/tokens/${token}`)
        .set({ platform: "android", updatedAt: Timestamp.fromMillis(base + index * 60_000) });
    }
  }
}

/** Wartet, bis für das Getränk `count` Nachrichten (eine je Empfänger) im Postausgang liegen. */
async function sentMessagesFor(docId: string, count: number): Promise<Map<string, DocumentData>> {
  const messages = await waitFor(async () => {
    const snapshot = await db.collection(EMULATOR_OUTBOX_COLLECTION).where("data.docId", "==", docId).get();
    return snapshot.size >= count ? snapshot.docs.map((doc) => doc.data()) : undefined;
  }, `${count} Nachricht(en) zu ${docId} in ${EMULATOR_OUTBOX_COLLECTION}`);
  expect(messages).toHaveLength(count);
  return new Map(messages.map((message) => [String((message.data as DocumentData).recipientId), message]));
}

async function tokenExists(userId: string, token: string): Promise<boolean> {
  return (await db.doc(`users/${userId}/tokens/${token}`).get()).exists;
}

async function drinksIn(groupId: string): Promise<string[]> {
  return (await db.collection(`groups/${groupId}/drinks`).get()).docs.map((doc) => doc.id).sort();
}

describe("onGroupDrinkCreated", () => {
  it("schickt jedem anderen Mitglied eine eigene Daten-Nachricht mit recipientId", async () => {
    const groupId = newGroupId();
    await seedGroup(groupId, ["anna", "bob", "cem"], {
      anna: ["anna-phone"],
      bob: ["bob-phone"],
      cem: ["cem-phone", "cem-tablet"],
    });

    const docId = await shareDrinkAsAdmin(groupId, "drink-1", "anna");
    const messages = await sentMessagesFor(docId, 2);

    expect(docId).toBe("anna_drink-1");
    expect([...messages.keys()].sort()).toEqual(["bob", "cem"]);
    const toBob = messages.get("bob");
    expect(toBob?.tokens).toEqual(["bob-phone"]);
    expect(toBob).not.toHaveProperty("notification");
    expect(toBob?.android).toEqual({ priority: "high" });
    expect(toBob?.data).toEqual({
      type: "drink",
      groupId,
      drinkId: "drink-1",
      docId: "anna_drink-1",
      recipientId: "bob",
      title: "Anna trinkt gerade",
      body: "🍺 Helles (500 ml) · Stammtisch",
    });
    expect(messages.get("cem")?.tokens).toEqual(["cem-tablet", "cem-phone"]);
    expect((messages.get("cem")?.data as DocumentData).recipientId).toBe("cem");
  });

  it("nutzt je Empfänger höchstens die 5 neuesten Tokens", async () => {
    const groupId = newGroupId();
    const bobTokens = Array.from({ length: 7 }, (_, i) => `bob-${i}`);
    await seedGroup(groupId, ["anna", "bob"], { bob: bobTokens });

    const docId = await shareDrinkAsAdmin(groupId, "drink-2", "anna");
    const messages = await sentMessagesFor(docId, 1);

    expect(messages.get("bob")?.tokens).toEqual(["bob-6", "bob-5", "bob-4", "bob-3", "bob-2"]);
  });

  it("löscht abgemeldete und kaputte Tokens und behält die übrigen", async () => {
    const groupId = newGroupId();
    const unregistered = `${EMULATOR_UNREGISTERED_TOKEN_PREFIX}bob-alt`;
    const malformed = `${EMULATOR_MALFORMED_TOKEN_PREFIX}bob-kaputt`;
    await seedGroup(groupId, ["anna", "bob"], { anna: ["anna-phone"], bob: [unregistered, malformed, "bob-phone"] });

    const docId = await shareDrinkAsAdmin(groupId, "drink-3", "anna");
    await sentMessagesFor(docId, 1);

    await waitFor(
      async () => ((await tokenExists("bob", unregistered)) || (await tokenExists("bob", malformed)) ? undefined : true),
      "Löschen der ungültigen Tokens",
    );
    expect(await tokenExists("bob", "bob-phone")).toBe(true);
    expect(await tokenExists("anna", "anna-phone")).toBe(true);
  });
});

describe("onGroupDeleted", () => {
  it("löscht die Getränke einer aufgelösten Runde vollständig", async () => {
    const groupId = newGroupId();
    await seedGroup(groupId, ["anna", "bob"], {});
    for (const drinkId of ["d1", "d2", "d3"]) await shareDrinkAsAdmin(groupId, drinkId, "anna");
    await shareDrinkAsAdmin(groupId, "d4", "bob");
    expect(await drinksIn(groupId)).toHaveLength(4);

    await db.doc(`groups/${groupId}`).delete();

    await waitFor(async () => ((await drinksIn(groupId)).length === 0 ? true : undefined), "Aufräumen der Getränke");
  });

  it("lässt andere Runden unberührt", async () => {
    const deleted = newGroupId();
    const kept = newGroupId();
    await seedGroup(deleted, ["anna"], {});
    await seedGroup(kept, ["anna"], {});
    await shareDrinkAsAdmin(deleted, "d1", "anna");
    await shareDrinkAsAdmin(kept, "d1", "anna");

    await db.doc(`groups/${deleted}`).delete();

    await waitFor(async () => ((await drinksIn(deleted)).length === 0 ? true : undefined), "Aufräumen der Getränke");
    expect(await drinksIn(kept)).toEqual(["anna_d1"]);
  });
});
