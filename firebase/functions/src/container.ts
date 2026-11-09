import type { Firestore } from "firebase-admin/firestore";
import type { RandomIndex } from "./domain/inviteCode";
import { RandomInviteCodeGenerator } from "./domain/inviteCode";
import { FirestoreDeviceTokenStore } from "./firestore/firestoreDeviceTokenStore";
import { FirestoreGroupLookup } from "./firestore/firestoreGroupLookup";
import { FirestoreProfileReader } from "./firestore/firestoreProfileReader";
import { FirestoreGroupService } from "./groups/firestoreGroupService";
import type { GroupService } from "./groups/groupService";
import { DrinkNotifier } from "./notifications/drinkNotifier";
import { EmulatorOutboxSender } from "./notifications/emulatorOutboxSender";
import type { MulticastMessaging } from "./notifications/fcmNotificationSender";
import { FcmNotificationSender } from "./notifications/fcmNotificationSender";
import type { Logger, NotificationSender } from "./notifications/ports";

/** Alles, was von außen kommt (Admin SDK, Zufall, Umgebung) – im Test austauschbar. */
export interface ContainerEnvironment {
  db: Firestore;
  messaging: MulticastMessaging;
  logger: Logger;
  randomIndex: RandomIndex;
  /** `true` im Firebase-Emulator: Push-Nachrichten gehen dann in `emulatorOutbox` statt an FCM. */
  isEmulator: boolean;
}

export interface Container {
  groupService: GroupService;
  drinkNotifier: DrinkNotifier;
  logger: Logger;
}

/** Composition Root: Hier – und nur hier – werden die konkreten Klassen verdrahtet. */
export function createContainer(environment: ContainerEnvironment): Container {
  const { db, messaging, logger, randomIndex, isEmulator } = environment;

  const groupService = new FirestoreGroupService(
    db,
    new RandomInviteCodeGenerator(randomIndex),
    new FirestoreProfileReader(db),
  );

  const sender: NotificationSender = isEmulator
    ? new EmulatorOutboxSender(db)
    : new FcmNotificationSender(messaging);

  const drinkNotifier = new DrinkNotifier({
    groups: new FirestoreGroupLookup(db),
    tokens: new FirestoreDeviceTokenStore(db),
    sender,
    logger,
  });

  return { groupService, drinkNotifier, logger };
}
