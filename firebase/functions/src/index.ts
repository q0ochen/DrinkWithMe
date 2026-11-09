import { randomInt } from "node:crypto";
import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import * as logger from "firebase-functions/logger";
import { setGlobalOptions } from "firebase-functions/v2";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { onCall } from "firebase-functions/v2/https";
import { MAX_INSTANCES, REGION, SHARED_DRINK_DOCUMENT } from "./config";
import { createContainer } from "./container";
import { createGroupHandler, joinGroupHandler, leaveGroupHandler } from "./handlers/callables";

// Cloud Functions (2nd gen) der Trinkrunden – Vertrag: docs/TRINKRUNDEN_ARCHITEKTUR.md
// Diese Datei verdrahtet nur; die Logik liegt in domain/, groups/ und notifications/.

setGlobalOptions({ region: REGION, maxInstances: MAX_INSTANCES });

const app = initializeApp();
const { groupService, drinkNotifier } = createContainer({
  db: getFirestore(app),
  messaging: getMessaging(app),
  logger,
  randomIndex: (bound) => randomInt(bound),
  isEmulator: process.env.FUNCTIONS_EMULATOR === "true",
});

/** Neue Trinkrunde: `{ name }` → `{ group }`. */
export const createGroup = onCall(createGroupHandler(groupService, logger));

/** Beitreten per Einladungscode: `{ code }` → `{ group }`. */
export const joinGroup = onCall(joinGroupHandler(groupService, logger));

/** Runde verlassen: `{ groupId }` → `{}`. */
export const leaveGroup = onCall(leaveGroupHandler(groupService, logger));

/** Push an alle anderen Mitglieder, sobald ein Getränk geteilt wird. */
export const onGroupDrinkCreated = onDocumentCreated(SHARED_DRINK_DOCUMENT, async (event) => {
  await drinkNotifier.onDrinkCreated(event.params.groupId, event.params.drinkId, event.data?.data());
});
