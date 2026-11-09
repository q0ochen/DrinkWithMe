import type { Firestore } from "firebase-admin/firestore";
import type { AccountService } from "./accounts/accountService";
import type { AuthAdmin } from "./accounts/firebaseAuthUserDeleter";
import { FirebaseAuthUserDeleter } from "./accounts/firebaseAuthUserDeleter";
import { FirestoreAccountService } from "./accounts/firestoreAccountService";
import type { RandomIndex } from "./domain/inviteCode";
import { RandomInviteCodeGenerator } from "./domain/inviteCode";
import type { Clock } from "./domain/joinThrottle";
import { FirestoreDeviceTokenStore } from "./firestore/firestoreDeviceTokenStore";
import { FirestoreGroupLookup } from "./firestore/firestoreGroupLookup";
import { FirestoreProfileReader } from "./firestore/firestoreProfileReader";
import { FirestoreGroupCleanup } from "./groups/firestoreGroupCleanup";
import { FirestoreGroupService } from "./groups/firestoreGroupService";
import type { GroupCleanup } from "./groups/groupCleanup";
import type { GroupService } from "./groups/groupService";
import { DrinkNotifier } from "./notifications/drinkNotifier";
import { EmulatorOutboxSender } from "./notifications/emulatorOutboxSender";
import type { MulticastMessaging } from "./notifications/fcmNotificationSender";
import { FcmNotificationSender } from "./notifications/fcmNotificationSender";
import type { Logger, NotificationSender } from "./notifications/ports";

/** Alles, was von außen kommt (Admin SDK, Zufall, Uhr, Umgebung) – im Test austauschbar. */
export interface ContainerEnvironment {
  db: Firestore;
  messaging: MulticastMessaging;
  auth: AuthAdmin;
  logger: Logger;
  randomIndex: RandomIndex;
  clock: Clock;
  /** `true` im Firebase-Emulator: Push-Nachrichten gehen dann in `emulatorOutbox` statt an FCM. */
  isEmulator: boolean;
}

export interface Container {
  groupService: GroupService;
  accountService: AccountService;
  groupCleanup: GroupCleanup;
  drinkNotifier: DrinkNotifier;
  logger: Logger;
}

/** Composition Root: Hier – und nur hier – werden die konkreten Klassen verdrahtet. */
export function createContainer(environment: ContainerEnvironment): Container {
  const { db, messaging, auth, logger, randomIndex, clock, isEmulator } = environment;

  const groupService = new FirestoreGroupService(
    db,
    new RandomInviteCodeGenerator(randomIndex),
    new FirestoreProfileReader(db),
    { clock },
  );

  const accountService = new FirestoreAccountService({
    db,
    groups: groupService,
    authUsers: new FirebaseAuthUserDeleter(auth),
    logger,
    clock,
  });

  const sender: NotificationSender = isEmulator
    ? new EmulatorOutboxSender(db)
    : new FcmNotificationSender(messaging);

  const drinkNotifier = new DrinkNotifier({
    groups: new FirestoreGroupLookup(db),
    tokens: new FirestoreDeviceTokenStore(db),
    sender,
    logger,
  });

  return { groupService, accountService, groupCleanup: new FirestoreGroupCleanup(db), drinkNotifier, logger };
}
