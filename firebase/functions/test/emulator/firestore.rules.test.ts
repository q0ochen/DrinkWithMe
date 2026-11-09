import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import type { RulesTestContext, RulesTestEnvironment } from "@firebase/rules-unit-testing";
import type { DocumentData, Firestore } from "firebase/firestore";
import {
  Timestamp,
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  limit,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  writeBatch,
} from "firebase/firestore";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";
import { EMULATORS, PROJECT_ID } from "./support";

// Erlaubte und verbotene Fälle für jede Regel aus firebase/firestore.rules.

let testEnv: RulesTestEnvironment;

beforeAll(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: readFileSync(resolve(__dirname, "../../../firestore.rules"), "utf8"),
      host: EMULATORS.firestore.host,
      port: EMULATORS.firestore.port,
    },
  });
});

afterAll(async () => {
  await testEnv?.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
  await seed({
    "groups/g1": group(["alice", "bob"]),
    "groups/g2": group(["carol"]),
    "groups/g1/drinks/bob-drink": { ...sharedDrink("bob-drink", "bob"), createdAt: Timestamp.now() },
    "users/alice": { displayName: "Alice", photoUrl: null, updatedAt: Timestamp.now() },
    "users/alice/tokens/alice-token": { platform: "android", updatedAt: Timestamp.now() },
    "inviteCodes/K7MX2Q": { groupId: "g1" },
  });
});

// ---------- Helfer ----------

/** Die modulare API funktioniert mit der Instanz aus rules-unit-testing. */
function db(context: RulesTestContext): Firestore {
  return context.firestore() as unknown as Firestore;
}

const as = (uid: string) => db(testEnv.authenticatedContext(uid));
const anonymous = () => db(testEnv.unauthenticatedContext());

async function seed(documents: Record<string, DocumentData>): Promise<void> {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    for (const [path, data] of Object.entries(documents)) {
      await setDoc(doc(db(context), path), data);
    }
  });
}

function group(memberIds: string[]): DocumentData {
  return {
    name: "Stammtisch",
    inviteCode: "K7MX2Q",
    ownerId: memberIds[0],
    memberIds,
    members: Object.fromEntries(memberIds.map((id) => [id, { displayName: id }])),
    createdAt: Timestamp.now(),
  };
}

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;

function minutesFromNow(minutes: number): Timestamp {
  return Timestamp.fromMillis(Date.now() + minutes * MINUTE);
}

/** Ein gültiges geteiltes Getränk laut Vertrag. */
function sharedDrink(drinkId: string, authorId = "alice", overrides: DocumentData = {}): DocumentData {
  return {
    drinkId,
    authorId,
    authorName: "Alice",
    name: "Helles",
    category: "BEER",
    volumeInMilliliters: 500,
    note: null,
    consumedAt: minutesFromNow(-1),
    createdAt: serverTimestamp(),
    ...overrides,
  };
}

function createDrink(firestore: Firestore, groupId: string, drinkId: string, data: DocumentData) {
  return setDoc(doc(firestore, `groups/${groupId}/drinks/${drinkId}`), data);
}

function withoutField(data: DocumentData, field: string): DocumentData {
  return Object.fromEntries(Object.entries(data).filter(([key]) => key !== field));
}

// ---------- users/{uid} ----------

describe("users/{uid}", () => {
  const profile = (overrides: DocumentData = {}) => ({
    displayName: "Bob",
    photoUrl: "https://example.com/bob.png",
    updatedAt: serverTimestamp(),
    ...overrides,
  });

  it("erlaubt dem Nutzer, sein Profil anzulegen, zu ändern, zu lesen und zu löschen", async () => {
    await assertSucceeds(setDoc(doc(as("bob"), "users/bob"), profile()));
    await assertSucceeds(
      setDoc(doc(as("bob"), "users/bob"), { displayName: "Bobby", updatedAt: serverTimestamp() }, { merge: true }),
    );
    await assertSucceeds(getDoc(doc(as("bob"), "users/bob")));
    await assertSucceeds(deleteDoc(doc(as("bob"), "users/bob")));
  });

  it("erlaubt photoUrl = null und ein Profil ohne photoUrl", async () => {
    await assertSucceeds(setDoc(doc(as("bob"), "users/bob"), profile({ photoUrl: null })));
    await assertSucceeds(setDoc(doc(as("carol"), "users/carol"), withoutField(profile(), "photoUrl")));
  });

  it("verbietet fremde Profile (lesen, schreiben, löschen) und Abfragen", async () => {
    await assertFails(getDoc(doc(as("bob"), "users/alice")));
    await assertFails(setDoc(doc(as("bob"), "users/alice"), profile()));
    await assertFails(deleteDoc(doc(as("bob"), "users/alice")));
    await assertFails(getDocs(collection(as("bob"), "users")));
  });

  it("verbietet Zugriff ohne Anmeldung", async () => {
    await assertFails(getDoc(doc(anonymous(), "users/alice")));
    await assertFails(setDoc(doc(anonymous(), "users/alice"), profile()));
  });

  it.each([
    ["leerer Name", { displayName: "" }],
    ["Name mit 51 Zeichen", { displayName: "x".repeat(51) }],
    ["Name keine Zeichenkette", { displayName: 42 }],
    ["photoUrl als Zahl", { photoUrl: 7 }],
    ["photoUrl zu lang", { photoUrl: `https://example.com/${"x".repeat(2048)}` }],
    ["updatedAt vom Client", { updatedAt: Timestamp.now() }],
    ["zusätzliches Feld", { admin: true }],
  ])("verbietet ungültige Profile: %s", async (_, overrides) => {
    await assertFails(setDoc(doc(as("bob"), "users/bob"), profile(overrides)));
  });

  it("verlangt displayName und updatedAt", async () => {
    await assertFails(setDoc(doc(as("bob"), "users/bob"), withoutField(profile(), "displayName")));
    await assertFails(setDoc(doc(as("bob"), "users/bob"), withoutField(profile(), "updatedAt")));
  });

  it("erlaubt 50 Zeichen mit Umlauten", async () => {
    await assertSucceeds(setDoc(doc(as("bob"), "users/bob"), profile({ displayName: "ä".repeat(50) })));
  });
});

// ---------- users/{uid}/tokens/{token} ----------

describe("users/{uid}/tokens/{token}", () => {
  const token = (overrides: DocumentData = {}) => ({
    platform: "android",
    updatedAt: serverTimestamp(),
    ...overrides,
  });

  it("erlaubt dem Nutzer, eigene Tokens zu registrieren, zu lesen und zu löschen", async () => {
    await assertSucceeds(setDoc(doc(as("bob"), "users/bob/tokens/fcm:bob-1"), token()));
    await assertSucceeds(setDoc(doc(as("bob"), "users/bob/tokens/fcm:bob-1"), token()));
    await assertSucceeds(getDoc(doc(as("bob"), "users/bob/tokens/fcm:bob-1")));
    await assertSucceeds(getDocs(collection(as("bob"), "users/bob/tokens")));
    await assertSucceeds(deleteDoc(doc(as("bob"), "users/bob/tokens/fcm:bob-1")));
  });

  it("verbietet fremde Tokens", async () => {
    await assertFails(getDoc(doc(as("bob"), "users/alice/tokens/alice-token")));
    await assertFails(getDocs(collection(as("bob"), "users/alice/tokens")));
    await assertFails(setDoc(doc(as("bob"), "users/alice/tokens/bob-token"), token()));
    await assertFails(deleteDoc(doc(as("bob"), "users/alice/tokens/alice-token")));
    await assertFails(getDoc(doc(anonymous(), "users/alice/tokens/alice-token")));
  });

  it.each([
    ["andere Plattform", { platform: "ios" }],
    ["updatedAt vom Client", { updatedAt: Timestamp.now() }],
    ["zusätzliches Feld", { userId: "bob" }],
  ])("verbietet ungültige Tokens: %s", async (_, overrides) => {
    await assertFails(setDoc(doc(as("bob"), "users/bob/tokens/t"), token(overrides)));
  });

  it("verlangt platform und updatedAt", async () => {
    await assertFails(setDoc(doc(as("bob"), "users/bob/tokens/t"), withoutField(token(), "platform")));
    await assertFails(setDoc(doc(as("bob"), "users/bob/tokens/t"), withoutField(token(), "updatedAt")));
  });
});

// ---------- groups/{groupId} ----------

describe("groups/{groupId}", () => {
  it("erlaubt Mitgliedern das Lesen ihrer Runde", async () => {
    await assertSucceeds(getDoc(doc(as("alice"), "groups/g1")));
    await assertSucceeds(getDoc(doc(as("bob"), "groups/g1")));
  });

  it("verbietet Nicht-Mitgliedern und Abgemeldeten das Lesen", async () => {
    await assertFails(getDoc(doc(as("carol"), "groups/g1")));
    await assertFails(getDoc(doc(anonymous(), "groups/g1")));
    await assertFails(getDoc(doc(as("alice"), "groups/gibt-es-nicht")));
  });

  it("erlaubt die Abfrage where(memberIds array-contains eigene uid)", async () => {
    await assertSucceeds(
      getDocs(query(collection(as("alice"), "groups"), where("memberIds", "array-contains", "alice"))),
    );
  });

  it("verbietet Abfragen nach fremden oder ohne Mitgliedschaft", async () => {
    await assertFails(
      getDocs(query(collection(as("alice"), "groups"), where("memberIds", "array-contains", "carol"))),
    );
    await assertFails(getDocs(collection(as("alice"), "groups")));
  });

  it("verbietet Clients jedes Schreiben (nur Cloud Functions)", async () => {
    await assertFails(setDoc(doc(as("alice"), "groups/neu"), group(["alice"])));
    await assertFails(updateDoc(doc(as("alice"), "groups/g1"), { name: "Gekapert" }));
    await assertFails(updateDoc(doc(as("carol"), "groups/g1"), { memberIds: ["alice", "bob", "carol"] }));
    await assertFails(deleteDoc(doc(as("alice"), "groups/g1")));
  });
});

// ---------- groups/{groupId}/drinks/{drinkId} ----------

describe("groups/{groupId}/drinks/{drinkId} – Erstellen", () => {
  it("erlaubt Mitgliedern gültige eigene Getränke", async () => {
    await assertSucceeds(createDrink(as("alice"), "g1", "d1", sharedDrink("d1")));
    await assertSucceeds(createDrink(as("alice"), "g1", "d2", sharedDrink("d2", "alice", { note: "Mit Freunden" })));
  });

  it("akzeptiert alle Kategorien", async () => {
    for (const category of ["WATER", "COFFEE", "TEA", "JUICE", "SOFT_DRINK", "BEER", "WINE", "COCKTAIL", "OTHER"]) {
      const id = `d-${category}`;
      await assertSucceeds(createDrink(as("alice"), "g1", id, sharedDrink(id, "alice", { category })));
    }
  });

  it("akzeptiert die Grenzwerte", async () => {
    const cases: DocumentData[] = [
      { volumeInMilliliters: 1 },
      { volumeInMilliliters: 5000 },
      { name: "x".repeat(50), authorName: "y".repeat(50) },
      { name: "ä".repeat(50) },
      { name: "🍺".repeat(25) }, // 50 UTF-16-Einheiten wie Kotlins String.length
      { note: "n".repeat(280) },
      { consumedAt: Timestamp.fromMillis(Date.now() - 23 * HOUR) },
      { consumedAt: minutesFromNow(4) },
    ];
    for (const [index, overrides] of cases.entries()) {
      const id = `grenze-${index}`;
      await assertSucceeds(createDrink(as("alice"), "g1", id, sharedDrink(id, "alice", overrides)));
    }
  });

  it("erlaubt einen Batch über 20 Runden (Firestore: höchstens 20 Dokumentzugriffe pro Batch)", async () => {
    // Pro Runde genau ein get() in isMemberOf – ein zusätzliches exists() halbiert das auf 10 Runden.
    const groupIds = Array.from({ length: 20 }, (_, i) => `batch-${i}`);
    await seed(Object.fromEntries(groupIds.map((id) => [`groups/${id}`, group(["alice"])])));
    const alice = as("alice");
    const batch = writeBatch(alice);
    for (const id of groupIds) batch.set(doc(alice, `groups/${id}/drinks/d1`), sharedDrink("d1"));

    await assertSucceeds(batch.commit());
  });

  it("verbietet Nicht-Mitgliedern, Abgemeldeten und in unbekannten Runden", async () => {
    await assertFails(createDrink(as("carol"), "g1", "d1", sharedDrink("d1", "carol")));
    await assertFails(createDrink(anonymous(), "g1", "d1", sharedDrink("d1")));
    await assertFails(createDrink(as("alice"), "gibt-es-nicht", "d1", sharedDrink("d1")));
  });

  it("verbietet fremde authorId und abweichende drinkId", async () => {
    await assertFails(createDrink(as("alice"), "g1", "d1", sharedDrink("d1", "bob")));
    await assertFails(createDrink(as("alice"), "g1", "d1", sharedDrink("anders")));
  });

  it.each([
    ["unbekannte Kategorie", { category: "MILKSHAKE" }],
    ["Kategorie kleingeschrieben", { category: "beer" }],
    ["Menge 0", { volumeInMilliliters: 0 }],
    ["Menge 5001", { volumeInMilliliters: 5001 }],
    ["Menge mit Nachkommastellen", { volumeInMilliliters: 250.5 }],
    ["Menge als Text", { volumeInMilliliters: "250" }],
    ["leerer Name", { name: "" }],
    ["Name mit 51 Zeichen", { name: "x".repeat(51) }],
    ["Name keine Zeichenkette", { name: 7 }],
    ["leerer authorName", { authorName: "" }],
    ["authorName mit 51 Zeichen", { authorName: "x".repeat(51) }],
    ["Notiz mit 281 Zeichen", { note: "n".repeat(281) }],
    ["Notiz als Zahl", { note: 7 }],
    ["consumedAt älter als 24 h", { consumedAt: Timestamp.fromMillis(Date.now() - 25 * HOUR) }],
    ["consumedAt mehr als 5 min in der Zukunft", { consumedAt: minutesFromNow(10) }],
    ["consumedAt als Text", { consumedAt: "2026-11-09T12:00:00Z" }],
    ["createdAt vom Client", { createdAt: Timestamp.now() }],
    ["zusätzliches Feld", { visibility: "FRIENDS" }],
  ])("verbietet ungültige Felder: %s", async (_, overrides) => {
    await assertFails(createDrink(as("alice"), "g1", "d1", sharedDrink("d1", "alice", overrides)));
  });

  it.each([
    "drinkId",
    "authorId",
    "authorName",
    "name",
    "category",
    "volumeInMilliliters",
    "note",
    "consumedAt",
    "createdAt",
  ])("verlangt das Feld %s", async (field) => {
    await assertFails(createDrink(as("alice"), "g1", "d1", withoutField(sharedDrink("d1"), field)));
  });
});

describe("groups/{groupId}/drinks/{drinkId} – Lesen, Ändern, Löschen", () => {
  it("erlaubt Mitgliedern das Lesen und die Feed-Abfrage", async () => {
    await assertSucceeds(getDoc(doc(as("alice"), "groups/g1/drinks/bob-drink")));
    await assertSucceeds(
      getDocs(query(collection(as("alice"), "groups/g1/drinks"), orderBy("consumedAt", "desc"), limit(100))),
    );
  });

  it("verbietet Nicht-Mitgliedern und Abgemeldeten das Lesen", async () => {
    await assertFails(getDoc(doc(as("carol"), "groups/g1/drinks/bob-drink")));
    await assertFails(
      getDocs(query(collection(as("carol"), "groups/g1/drinks"), orderBy("consumedAt", "desc"), limit(100))),
    );
    await assertFails(getDoc(doc(anonymous(), "groups/g1/drinks/bob-drink")));
  });

  it("verbietet jedes Ändern – auch durch den Autor", async () => {
    await assertFails(updateDoc(doc(as("bob"), "groups/g1/drinks/bob-drink"), { name: "Radler" }));
    await assertFails(setDoc(doc(as("bob"), "groups/g1/drinks/bob-drink"), sharedDrink("bob-drink", "bob")));
  });

  it("erlaubt dem Autor das Löschen, anderen Mitgliedern nicht", async () => {
    await assertFails(deleteDoc(doc(as("alice"), "groups/g1/drinks/bob-drink")));
    await assertSucceeds(deleteDoc(doc(as("bob"), "groups/g1/drinks/bob-drink")));
  });

  it("erlaubt dem Autor das Löschen auch nach dem Verlassen der Runde", async () => {
    await seed({ "groups/g1": group(["alice"]) });

    await assertSucceeds(deleteDoc(doc(as("bob"), "groups/g1/drinks/bob-drink")));
  });

  it("erlaubt das Löschen nicht vorhandener Getränke (unpublish per Batch über alle Runden)", async () => {
    await assertSucceeds(deleteDoc(doc(as("carol"), "groups/g1/drinks/gibt-es-nicht")));

    const bob = as("bob");
    const batch = writeBatch(bob);
    batch.delete(doc(bob, "groups/g1/drinks/bob-drink"));
    batch.delete(doc(bob, "groups/g2/drinks/bob-drink"));
    await assertSucceeds(batch.commit());
  });

  it("verbietet das Löschen ohne Anmeldung", async () => {
    await assertFails(deleteDoc(doc(anonymous(), "groups/g1/drinks/gibt-es-nicht")));
    await assertFails(deleteDoc(doc(anonymous(), "groups/g1/drinks/bob-drink")));
  });
});

// ---------- inviteCodes und alles andere ----------

describe("inviteCodes/{code} und übrige Pfade", () => {
  it("verbietet jeden Client-Zugriff auf Einladungscodes", async () => {
    await assertFails(getDoc(doc(as("alice"), "inviteCodes/K7MX2Q")));
    await assertFails(getDocs(collection(as("alice"), "inviteCodes")));
    await assertFails(setDoc(doc(as("alice"), "inviteCodes/ABCDEF"), { groupId: "g1" }));
    await assertFails(deleteDoc(doc(as("alice"), "inviteCodes/K7MX2Q")));
  });

  it("verbietet alle anderen Pfade", async () => {
    await assertFails(getDoc(doc(as("alice"), "emulatorOutbox/x")));
    await assertFails(setDoc(doc(as("alice"), "sonstiges/x"), { a: 1 }));
    await assertFails(getDocs(collection(as("alice"), "sonstiges")));
    await assertFails(setDoc(doc(as("alice"), "users/alice/privat/x"), { a: 1 }));
  });
});
