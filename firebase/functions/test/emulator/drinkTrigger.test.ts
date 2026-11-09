import { FieldValue, Timestamp } from "firebase-admin/firestore";
import type { DocumentData } from "firebase-admin/firestore";
import { beforeEach, describe, expect, it } from "vitest";
import {
  EMULATOR_OUTBOX_COLLECTION,
  EMULATOR_UNREGISTERED_TOKEN_PREFIX,
} from "../../src/notifications/emulatorOutboxSender";
import { adminDb, clearFirestore, waitFor } from "./support";

// Der Trigger onGroupDrinkCreated über Firestore- und Functions-Emulator. FCM lässt sich nicht
// emulieren: Im Emulator schreibt die Funktion ihre Nachrichten in `emulatorOutbox`.

const db = adminDb();

beforeEach(async () => {
  await clearFirestore();
});

async function seedGroup(groupId: string, memberIds: string[], tokens: Record<string, string[]>) {
  await db.doc(`groups/${groupId}`).set({
    name: "Stammtisch",
    inviteCode: "TTTTTT",
    ownerId: memberIds[0],
    memberIds,
    members: Object.fromEntries(memberIds.map((id) => [id, { displayName: id }])),
    createdAt: FieldValue.serverTimestamp(),
  });
  for (const [userId, userTokens] of Object.entries(tokens)) {
    for (const token of userTokens) {
      await db.doc(`users/${userId}/tokens/${token}`).set({ platform: "android", updatedAt: FieldValue.serverTimestamp() });
    }
  }
}

async function shareDrink(groupId: string, drinkId: string, authorId: string): Promise<void> {
  await db.doc(`groups/${groupId}/drinks/${drinkId}`).set({
    drinkId,
    authorId,
    authorName: "Anna",
    name: "Helles",
    category: "BEER",
    volumeInMilliliters: 500,
    note: null,
    consumedAt: Timestamp.now(),
    createdAt: FieldValue.serverTimestamp(),
  });
}

function sentMessageFor(drinkId: string): Promise<DocumentData> {
  return waitFor(async () => {
    const snapshot = await db.collection(EMULATOR_OUTBOX_COLLECTION).where("data.drinkId", "==", drinkId).get();
    return snapshot.docs[0]?.data();
  }, `Nachricht zu ${drinkId} in ${EMULATOR_OUTBOX_COLLECTION}`);
}

async function tokenExists(userId: string, token: string): Promise<boolean> {
  return (await db.doc(`users/${userId}/tokens/${token}`).get()).exists;
}

describe("onGroupDrinkCreated", () => {
  it("benachrichtigt alle anderen Mitglieder laut Vertrag", async () => {
    await seedGroup("g1", ["anna", "bob", "cem"], {
      anna: ["anna-phone"],
      bob: ["bob-phone"],
      cem: ["cem-phone", "cem-tablet"],
    });

    await shareDrink("g1", "drink-1", "anna");
    const message = await sentMessageFor("drink-1");

    expect((message.tokens as string[]).sort()).toEqual(["bob-phone", "cem-phone", "cem-tablet"]);
    expect(message.notification).toEqual({
      title: "Anna trinkt gerade",
      body: "🍺 Helles (500 ml) · Stammtisch",
    });
    expect(message.android).toEqual({ notification: { channelId: "drinks", tag: "drink-1" } });
    expect(message.data).toEqual({ type: "drink", groupId: "g1", drinkId: "drink-1" });
  });

  it("löscht abgemeldete Tokens und behält die übrigen", async () => {
    const unregistered = `${EMULATOR_UNREGISTERED_TOKEN_PREFIX}bob-alt`;
    await seedGroup("g2", ["anna", "bob"], { anna: ["anna-phone"], bob: ["bob-phone", unregistered] });

    await shareDrink("g2", "drink-2", "anna");
    await sentMessageFor("drink-2");

    await waitFor(
      async () => ((await tokenExists("bob", unregistered)) ? undefined : true),
      "Löschen des abgemeldeten Tokens",
    );
    expect(await tokenExists("bob", "bob-phone")).toBe(true);
    expect(await tokenExists("anna", "anna-phone")).toBe(true);
  });
});
