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

/** Ein realistisch aussehendes FCM-Token (Instanz-ID, Doppelpunkt, „APA91b…“). */
function fcmToken(prefix: string): string {
  return `${prefix}Xk3_9:APA91bH-${"a1B2_c3D-".repeat(16)}`;
}

const ALICE_TOKEN = fcmToken("alice");
const BOB_TOKEN = fcmToken("bob");

// Je Test eigene Runden-IDs: Das Leeren des Emulators löst onGroupDeleted für die Runden des
// vorigen Tests aus. Mit festen IDs könnte dieses verspätete Aufräumen die neu angelegten
// Getränke treffen (in Produktion werden Runden-IDs nie wiederverwendet).
let run = 0;
let g1 = "";
let g2 = "";

beforeEach(async () => {
  run += 1;
  g1 = `g1-${run}`;
  g2 = `g2-${run}`;
  await testEnv.clearFirestore();
  await seed({
    [`groups/${g1}`]: group(["alice", "bob"]),
    [`groups/${g2}`]: group(["carol"]),
    [`groups/${g1}/drinks/bob_bob-drink`]: { ...sharedDrink("bob-drink", "bob"), createdAt: Timestamp.now() },
    "users/alice": { displayName: "Alice", photoUrl: null, updatedAt: Timestamp.now() },
    [`users/alice/tokens/${ALICE_TOKEN}`]: { platform: "android", updatedAt: Timestamp.now() },
    "inviteCodes/K7MX2QAB": { groupId: g1 },
    "joinAttempts/alice": { windowStart: Timestamp.now(), failures: 3 },
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
    inviteCode: "K7MX2QAB",
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

/** Legt ein Getränk unter der angegebenen Dokument-ID an (laut Vertrag `"{authorId}_{drinkId}"`). */
function createDrink(firestore: Firestore, groupId: string, docId: string, data: DocumentData) {
  return setDoc(doc(firestore, `groups/${groupId}/drinks/${docId}`), data);
}

/** Legt ein Getränk unter seiner vertragsgemäßen Dokument-ID an. */
function shareDrink(firestore: Firestore, groupId: string, data: DocumentData) {
  return createDrink(firestore, groupId, `${String(data.authorId)}_${String(data.drinkId)}`, data);
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
    await assertSucceeds(setDoc(doc(as("bob"), `users/bob/tokens/${BOB_TOKEN}`), token()));
    await assertSucceeds(setDoc(doc(as("bob"), `users/bob/tokens/${BOB_TOKEN}`), token()));
    await assertSucceeds(getDoc(doc(as("bob"), `users/bob/tokens/${BOB_TOKEN}`)));
    await assertSucceeds(getDocs(collection(as("bob"), "users/bob/tokens")));
    await assertSucceeds(deleteDoc(doc(as("bob"), `users/bob/tokens/${BOB_TOKEN}`)));
  });

  it("verbietet fremde Tokens", async () => {
    await assertFails(getDoc(doc(as("bob"), `users/alice/tokens/${ALICE_TOKEN}`)));
    await assertFails(getDocs(collection(as("bob"), "users/alice/tokens")));
    await assertFails(setDoc(doc(as("bob"), `users/alice/tokens/${BOB_TOKEN}`), token()));
    await assertFails(deleteDoc(doc(as("bob"), `users/alice/tokens/${ALICE_TOKEN}`)));
    await assertFails(getDoc(doc(anonymous(), `users/alice/tokens/${ALICE_TOKEN}`)));
  });

  it.each([
    ["20 Zeichen (Untergrenze)", "a".repeat(20)],
    ["1024 Zeichen (Obergrenze)", "b".repeat(1024)],
    ["alle erlaubten Zeichen", "AZaz09_:-".repeat(3)],
    ["typisches FCM-Token", BOB_TOKEN],
  ])("erlaubt Token-IDs wie ein FCM-Token: %s", async (_, id) => {
    await assertSucceeds(setDoc(doc(as("bob"), `users/bob/tokens/${id}`), token()));
  });

  it.each([
    ["19 Zeichen", "c".repeat(19)],
    ["1025 Zeichen", "d".repeat(1025)],
    ["Punkt", `${"e".repeat(20)}.x`],
    ["Leerzeichen", `${"f".repeat(20)} x`],
    ["Prozentzeichen", `${"g".repeat(20)}%41`],
    ["Plus und Gleichheitszeichen", `${"h".repeat(20)}+x=`],
    ["Umlaut", `${"i".repeat(20)}ä`],
    ["kurze Fantasie-ID", "t"],
  ])("verbietet Token-IDs, die kein FCM-Token sein können: %s", async (_, id) => {
    await assertFails(setDoc(doc(as("bob"), `users/bob/tokens/${id}`), token()));
  });

  it("erlaubt das Löschen eigener alter Einträge mit ungültiger ID", async () => {
    await seed({ "users/bob/tokens/alt": { platform: "android", updatedAt: Timestamp.now() } });

    await assertFails(setDoc(doc(as("bob"), "users/bob/tokens/alt"), token()));
    await assertSucceeds(deleteDoc(doc(as("bob"), "users/bob/tokens/alt")));
  });

  it.each([
    ["andere Plattform", { platform: "ios" }],
    ["updatedAt vom Client", { updatedAt: Timestamp.now() }],
    ["zusätzliches Feld", { userId: "bob" }],
  ])("verbietet ungültige Tokens: %s", async (_, overrides) => {
    await assertFails(setDoc(doc(as("bob"), `users/bob/tokens/${BOB_TOKEN}`), token(overrides)));
  });

  it("verlangt platform und updatedAt", async () => {
    await assertFails(setDoc(doc(as("bob"), `users/bob/tokens/${BOB_TOKEN}`), withoutField(token(), "platform")));
    await assertFails(setDoc(doc(as("bob"), `users/bob/tokens/${BOB_TOKEN}`), withoutField(token(), "updatedAt")));
  });
});

// ---------- groups/{groupId} ----------

describe("groups/{groupId}", () => {
  it("erlaubt Mitgliedern das Lesen ihrer Runde", async () => {
    await assertSucceeds(getDoc(doc(as("alice"), `groups/${g1}`)));
    await assertSucceeds(getDoc(doc(as("bob"), `groups/${g1}`)));
  });

  it("verbietet Nicht-Mitgliedern und Abgemeldeten das Lesen", async () => {
    await assertFails(getDoc(doc(as("carol"), `groups/${g1}`)));
    await assertFails(getDoc(doc(anonymous(), `groups/${g1}`)));
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
    await assertFails(updateDoc(doc(as("alice"), `groups/${g1}`), { name: "Gekapert" }));
    await assertFails(updateDoc(doc(as("carol"), `groups/${g1}`), { memberIds: ["alice", "bob", "carol"] }));
    await assertFails(deleteDoc(doc(as("alice"), `groups/${g1}`)));
  });
});

// ---------- groups/{groupId}/drinks/{docId} ----------

describe("groups/{groupId}/drinks/{docId} – Erstellen", () => {
  it("erlaubt Mitgliedern gültige eigene Getränke unter \"{uid}_{drinkId}\"", async () => {
    await assertSucceeds(createDrink(as("alice"), g1, "alice_d1", sharedDrink("d1")));
    await assertSucceeds(
      createDrink(as("alice"), g1, "alice_d2", sharedDrink("d2", "alice", { note: "Mit Freunden" })),
    );
    await assertSucceeds(
      createDrink(as("bob"), g1, "bob_8c4b2f1e-0d5a-4c1e-9f3a-2b7d6e5c4a10", sharedDrink("8c4b2f1e-0d5a-4c1e-9f3a-2b7d6e5c4a10", "bob")),
    );
  });

  it("akzeptiert alle Kategorien", async () => {
    for (const category of ["WATER", "COFFEE", "TEA", "JUICE", "SOFT_DRINK", "BEER", "WINE", "COCKTAIL", "OTHER"]) {
      await assertSucceeds(shareDrink(as("alice"), g1, sharedDrink(`d-${category}`, "alice", { category })));
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
      await assertSucceeds(shareDrink(as("alice"), g1, sharedDrink(`grenze-${index}`, "alice", overrides)));
    }
  });

  it("erlaubt einen Batch über 20 Runden (Firestore: höchstens 20 Dokumentzugriffe pro Batch)", async () => {
    // Pro Runde genau ein get() in isMemberOf – ein zusätzliches exists() halbiert das auf 10 Runden.
    const groupIds = Array.from({ length: 20 }, (_, i) => `batch-${i}`);
    await seed(Object.fromEntries(groupIds.map((id) => [`groups/${id}`, group(["alice"])])));
    const alice = as("alice");
    const batch = writeBatch(alice);
    for (const id of groupIds) batch.set(doc(alice, `groups/${id}/drinks/alice_d1`), sharedDrink("d1"));

    await assertSucceeds(batch.commit());
  });

  it("verbietet Nicht-Mitgliedern, Abgemeldeten und in unbekannten Runden", async () => {
    await assertFails(createDrink(as("carol"), g1, "carol_d1", sharedDrink("d1", "carol")));
    await assertFails(createDrink(anonymous(), g1, "alice_d1", sharedDrink("d1")));
    await assertFails(createDrink(as("alice"), "gibt-es-nicht", "alice_d1", sharedDrink("d1")));
  });

  it("verbietet fremde authorId und abweichende drinkId", async () => {
    await assertFails(createDrink(as("alice"), g1, "bob_d1", sharedDrink("d1", "bob")));
    await assertFails(createDrink(as("alice"), g1, "alice_d1", sharedDrink("d1", "bob")));
    await assertFails(createDrink(as("alice"), g1, "alice_d1", sharedDrink("anders")));
  });

  it.each([
    ["alte Dokument-ID = drinkId", "d1", sharedDrink("d1")],
    ["ohne Unterstrich", "aliced1", sharedDrink("d1")],
    ["anderes Trennzeichen", "alice-d1", sharedDrink("d1")],
    ["fremde uid als Präfix", "bob_d1", sharedDrink("d1")],
    ["Präfix nur ähnlich", "alicex_d1", sharedDrink("d1")],
    ["leere drinkId", "alice_", sharedDrink("")],
  ])("verlangt die Dokument-ID \"{uid}_{drinkId}\": %s", async (_, docId, data) => {
    await assertFails(createDrink(as("alice"), g1, docId, data));
  });

  it("verhindert, dass ein Mitglied die Getränke-ID eines anderen in einer Runde besetzt", async () => {
    // Carol kennt Alices Getränk „d1“ (z. B. aus einer gemeinsamen Runde) und ist allein in g2.
    await seed({ [`groups/${g1}/drinks/alice_d1`]: { ...sharedDrink("d1"), createdAt: Timestamp.now() } });
    const carol = as("carol");

    await assertFails(createDrink(carol, g2, "alice_d1", sharedDrink("d1", "carol")));
    await assertFails(createDrink(carol, g2, "alice_d1", sharedDrink("d1", "alice")));
    await assertSucceeds(createDrink(carol, g2, "carol_d1", sharedDrink("d1", "carol")));

    // Alice tritt g2 bei: Ihr Löschen per Batch über alle Runden wird nicht mehr blockiert.
    await seed({ [`groups/${g2}`]: group(["carol", "alice"]) });
    const alice = as("alice");
    const batch = writeBatch(alice);
    batch.delete(doc(alice, `groups/${g1}/drinks/alice_d1`));
    batch.delete(doc(alice, `groups/${g2}/drinks/alice_d1`));
    await assertSucceeds(batch.commit());
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
    await assertFails(createDrink(as("alice"), g1, "alice_d1", sharedDrink("d1", "alice", overrides)));
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
    await assertFails(createDrink(as("alice"), g1, "alice_d1", withoutField(sharedDrink("d1"), field)));
  });
});

describe("groups/{groupId}/drinks/{docId} – Lesen, Ändern, Löschen", () => {
  it("erlaubt Mitgliedern das Lesen und die Feed-Abfrage", async () => {
    await assertSucceeds(getDoc(doc(as("alice"), `groups/${g1}/drinks/bob_bob-drink`)));
    await assertSucceeds(
      getDocs(query(collection(as("alice"), `groups/${g1}/drinks`), orderBy("consumedAt", "desc"), limit(100))),
    );
  });

  it("verbietet Nicht-Mitgliedern und Abgemeldeten das Lesen", async () => {
    await assertFails(getDoc(doc(as("carol"), `groups/${g1}/drinks/bob_bob-drink`)));
    await assertFails(
      getDocs(query(collection(as("carol"), `groups/${g1}/drinks`), orderBy("consumedAt", "desc"), limit(100))),
    );
    await assertFails(getDoc(doc(anonymous(), `groups/${g1}/drinks/bob_bob-drink`)));
  });

  it("verbietet jedes Ändern – auch durch den Autor", async () => {
    await assertFails(updateDoc(doc(as("bob"), `groups/${g1}/drinks/bob_bob-drink`), { name: "Radler" }));
    await assertFails(setDoc(doc(as("bob"), `groups/${g1}/drinks/bob_bob-drink`), sharedDrink("bob-drink", "bob")));
  });

  it("erlaubt dem Autor das Löschen, anderen Mitgliedern nicht", async () => {
    await assertFails(deleteDoc(doc(as("alice"), `groups/${g1}/drinks/bob_bob-drink`)));
    await assertSucceeds(deleteDoc(doc(as("bob"), `groups/${g1}/drinks/bob_bob-drink`)));
  });

  it("erlaubt dem Autor das Löschen auch nach dem Verlassen der Runde", async () => {
    await seed({ [`groups/${g1}`]: group(["alice"]) });

    await assertSucceeds(deleteDoc(doc(as("bob"), `groups/${g1}/drinks/bob_bob-drink`)));
  });

  it("erlaubt das Löschen nicht vorhandener Getränke (unpublish per Batch über alle Runden)", async () => {
    await assertSucceeds(deleteDoc(doc(as("carol"), `groups/${g1}/drinks/carol_gibt-es-nicht`)));

    const bob = as("bob");
    const batch = writeBatch(bob);
    batch.delete(doc(bob, `groups/${g1}/drinks/bob_bob-drink`));
    batch.delete(doc(bob, `groups/${g2}/drinks/bob_bob-drink`));
    await assertSucceeds(batch.commit());
  });

  it("verbietet das Löschen ohne Anmeldung", async () => {
    await assertFails(deleteDoc(doc(anonymous(), `groups/${g1}/drinks/carol_gibt-es-nicht`)));
    await assertFails(deleteDoc(doc(anonymous(), `groups/${g1}/drinks/bob_bob-drink`)));
  });
});

// ---------- inviteCodes und alles andere ----------

describe("inviteCodes/{code}, joinAttempts/{uid} und übrige Pfade", () => {
  it("verbietet jeden Client-Zugriff auf Einladungscodes", async () => {
    await assertFails(getDoc(doc(as("alice"), "inviteCodes/K7MX2QAB")));
    await assertFails(getDocs(collection(as("alice"), "inviteCodes")));
    await assertFails(setDoc(doc(as("alice"), "inviteCodes/ABCDEFGH"), { groupId: g1 }));
    await assertFails(deleteDoc(doc(as("alice"), "inviteCodes/K7MX2QAB")));
  });

  it("verbietet jeden Client-Zugriff auf die Zähler der Beitrittsversuche – auch auf den eigenen", async () => {
    await assertFails(getDoc(doc(as("alice"), "joinAttempts/alice")));
    await assertFails(getDoc(doc(as("bob"), "joinAttempts/alice")));
    await assertFails(getDocs(collection(as("alice"), "joinAttempts")));
    await assertFails(setDoc(doc(as("alice"), "joinAttempts/alice"), { windowStart: Timestamp.now(), failures: 0 }));
    await assertFails(updateDoc(doc(as("alice"), "joinAttempts/alice"), { failures: 0 }));
    await assertFails(deleteDoc(doc(as("alice"), "joinAttempts/alice")));
    await assertFails(setDoc(doc(as("bob"), "joinAttempts/bob"), { windowStart: Timestamp.now(), failures: 0 }));
    await assertFails(getDoc(doc(anonymous(), "joinAttempts/alice")));
  });

  it("verbietet alle anderen Pfade", async () => {
    await assertFails(getDoc(doc(as("alice"), "emulatorOutbox/x")));
    await assertFails(setDoc(doc(as("alice"), "sonstiges/x"), { a: 1 }));
    await assertFails(getDocs(collection(as("alice"), "sonstiges")));
    await assertFails(setDoc(doc(as("alice"), "users/alice/privat/x"), { a: 1 }));
  });
});
