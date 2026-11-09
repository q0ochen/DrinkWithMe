import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import type { App } from "firebase-admin/app";
import { getApps, initializeApp as initializeAdminApp } from "firebase-admin/app";
import type { Auth } from "firebase-admin/auth";
import { getAuth as getAdminAuth } from "firebase-admin/auth";
import type { DocumentData, Firestore } from "firebase-admin/firestore";
import { FieldValue, Timestamp, getFirestore } from "firebase-admin/firestore";
import type { FirebaseApp } from "firebase/app";
import { deleteApp, initializeApp } from "firebase/app";
import { connectAuthEmulator, getAuth, signInWithCustomToken } from "firebase/auth";
import type { Functions } from "firebase/functions";
import { connectFunctionsEmulator, getFunctions } from "firebase/functions";

// Gemeinsame Helfer der Emulator-Tests. Hosts und Ports kommen aus firebase.json
// (eine Quelle der Wahrheit); `firebase emulators:exec` setzt zusätzlich die *_EMULATOR_HOST-Variablen.

export const PROJECT_ID = "demo-drinkwithme";
export const REGION = "europe-west3";

interface EmulatorAddress {
  host: string;
  port: number;
}

const firebaseJson = JSON.parse(
  readFileSync(resolve(__dirname, "../../../firebase.json"), "utf8"),
) as { emulators: Record<"auth" | "firestore" | "functions", EmulatorAddress> };

export const EMULATORS = firebaseJson.emulators;

process.env.GCLOUD_PROJECT ??= PROJECT_ID;
process.env.FIRESTORE_EMULATOR_HOST ??= `${EMULATORS.firestore.host}:${EMULATORS.firestore.port}`;
process.env.FIREBASE_AUTH_EMULATOR_HOST ??= `${EMULATORS.auth.host}:${EMULATORS.auth.port}`;

/** Admin-Zugriff auf die Emulatoren (umgeht die Sicherheitsregeln). */
export function adminApp(): App {
  const name = "emulator-tests";
  return getApps().find((app) => app.name === name) ?? initializeAdminApp({ projectId: PROJECT_ID }, name);
}

export function adminDb(): Firestore {
  return getFirestore(adminApp());
}

export function adminAuth(): Auth {
  return getAdminAuth(adminApp());
}

/** Dokument-ID eines geteilten Getränks laut Vertrag: `"{authorId}_{drinkId}"`. */
export function sharedDrinkDocId(authorId: string, drinkId: string): string {
  return `${authorId}_${drinkId}`;
}

/** Ein gültiges geteiltes Getränk, wie die App es schreibt (für Admin-Schreibzugriffe). */
export function sharedDrinkData(drinkId: string, authorId: string, overrides: DocumentData = {}): DocumentData {
  return {
    drinkId,
    authorId,
    authorName: "Anna",
    name: "Helles",
    category: "BEER",
    volumeInMilliliters: 500,
    note: null,
    consumedAt: Timestamp.now(),
    createdAt: FieldValue.serverTimestamp(),
    ...overrides,
  };
}

/** Legt `groups/{groupId}/drinks/{authorId}_{drinkId}` per Admin SDK an. @returns die Dokument-ID. */
export async function shareDrinkAsAdmin(groupId: string, drinkId: string, authorId: string): Promise<string> {
  const docId = sharedDrinkDocId(authorId, drinkId);
  await adminDb().doc(`groups/${groupId}/drinks/${docId}`).set(sharedDrinkData(drinkId, authorId));
  return docId;
}

/** Löscht alle Firestore-Daten des Projekts im Emulator. */
export async function clearFirestore(): Promise<void> {
  const { host, port } = EMULATORS.firestore;
  const response = await fetch(
    `http://${host}:${port}/emulator/v1/projects/${PROJECT_ID}/databases/(default)/documents`,
    { method: "DELETE" },
  );
  if (!response.ok) throw new Error(`Firestore-Emulator konnte nicht geleert werden: ${response.status}`);
}

/** Löscht alle Konten im Auth-Emulator. */
export async function clearAuth(): Promise<void> {
  const { host, port } = EMULATORS.auth;
  const response = await fetch(`http://${host}:${port}/emulator/v1/projects/${PROJECT_ID}/accounts`, {
    method: "DELETE",
  });
  if (!response.ok) throw new Error(`Auth-Emulator konnte nicht geleert werden: ${response.status}`);
}

/** Wartet, bis `probe` einen Wert liefert (für asynchrone Trigger). */
export async function waitFor<T>(
  probe: () => Promise<T | undefined>,
  description: string,
  timeoutMs = 20_000,
): Promise<T> {
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    const value = await probe();
    if (value !== undefined) return value;
    if (Date.now() > deadline) throw new Error(`Zeitüberschreitung beim Warten auf: ${description}`);
    await new Promise((done) => setTimeout(done, 250));
  }
}

/** Ein angemeldeter App-Client (wie die Android-App) mit Functions-Zugang in europe-west3. */
export interface SignedInClient {
  uid: string;
  functions: Functions;
  close(): Promise<void>;
}

let clientCounter = 0;

function clientApp(): FirebaseApp {
  clientCounter += 1;
  return initializeApp({ projectId: PROJECT_ID, apiKey: "demo-api-key" }, `client-${clientCounter}`);
}

function connectFunctions(app: FirebaseApp): Functions {
  const functions = getFunctions(app, REGION);
  connectFunctionsEmulator(functions, EMULATORS.functions.host, EMULATORS.functions.port);
  return functions;
}

/**
 * Legt ein Konto im Auth-Emulator an und meldet einen Client damit an.
 * `displayName` landet als `name` im ID-Token.
 */
export async function signedInClient(uid: string, displayName?: string): Promise<SignedInClient> {
  const auth = adminAuth();
  await auth.createUser(displayName === undefined ? { uid } : { uid, displayName });
  const customToken = await auth.createCustomToken(uid);

  const app = clientApp();
  const clientAuth = getAuth(app);
  connectAuthEmulator(clientAuth, `http://${EMULATORS.auth.host}:${EMULATORS.auth.port}`, { disableWarnings: true });
  await signInWithCustomToken(clientAuth, customToken);
  return { uid, functions: connectFunctions(app), close: () => deleteApp(app) };
}

/** Ein Client ohne Anmeldung. */
export function anonymousClient(): { functions: Functions; close(): Promise<void> } {
  const app = clientApp();
  return { functions: connectFunctions(app), close: () => deleteApp(app) };
}
