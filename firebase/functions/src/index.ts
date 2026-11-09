import { randomInt } from "node:crypto";
import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import * as logger from "firebase-functions/logger";
import { setGlobalOptions } from "firebase-functions/v2";
import { onDocumentCreated, onDocumentDeleted } from "firebase-functions/v2/firestore";
import { onCall } from "firebase-functions/v2/https";
import {
  GROUP_DOCUMENT,
  LONG_RUNNING_TIMEOUT_SECONDS,
  MAX_INSTANCES,
  REGION,
  SHARED_DRINK_DOCUMENT,
} from "./config";
import { createContainer } from "./container";
import {
  createGroupHandler,
  deleteAccountHandler,
  joinGroupHandler,
  leaveGroupHandler,
} from "./handlers/callables";

// Cloud Functions (2nd gen) der Trinkrunden – Vertrag: docs/TRINKRUNDEN_ARCHITEKTUR.md
// Diese Datei verdrahtet nur; die Logik liegt in domain/, groups/, accounts/ und notifications/.

setGlobalOptions({ region: REGION, maxInstances: MAX_INSTANCES });

const app = initializeApp();
const { groupService, accountService, groupCleanup, drinkNotifier } = createContainer({
  db: getFirestore(app),
  messaging: getMessaging(app),
  auth: getAuth(app),
  logger,
  randomIndex: (bound) => randomInt(bound),
  clock: () => Date.now(),
  isEmulator: process.env.FUNCTIONS_EMULATOR === "true",
});

/** Neue Trinkrunde: `{ name }` → `{ group }`. */
export const createGroup = onCall(createGroupHandler(groupService, logger));

/** Beitreten per Einladungscode: `{ code }` → `{ group }` (mit Schutz vor Code-Raten). */
export const joinGroup = onCall(joinGroupHandler(groupService, logger));

/** Runde verlassen: `{ groupId }` → `{}`. */
export const leaveGroup = onCall(leaveGroupHandler(groupService, logger));

/** Konto löschen: `{}` → `{}` – idempotent, darf nach einem Fehler einfach wiederholt werden. */
export const deleteAccount = onCall(
  { timeoutSeconds: LONG_RUNNING_TIMEOUT_SECONDS },
  deleteAccountHandler(accountService, logger),
);

/** Push an alle anderen Mitglieder, sobald ein Getränk geteilt wird. */
export const onGroupDrinkCreated = onDocumentCreated(SHARED_DRINK_DOCUMENT, async (event) => {
  await drinkNotifier.onDrinkCreated(event.params.groupId, event.params.docId, event.data?.data());
});

/** Löscht die Getränke einer aufgelösten Runde – wiederholt bei Fehlern, idempotent. */
export const onGroupDeleted = onDocumentDeleted(
  { document: GROUP_DOCUMENT, retry: true, timeoutSeconds: LONG_RUNNING_TIMEOUT_SECONDS },
  async (event) => {
    await groupCleanup.deleteDrinksOf(event.params.groupId);
  },
);
