import type { Firestore } from "firebase-admin/firestore";
import { Collections } from "../firestore/paths";
import type { Caller, GroupService } from "../groups/groupService";
import type { Logger } from "../notifications/ports";
import type { AccountService, AuthUserDeleter } from "./accountService";

/** Höchstzahl an Schreibvorgängen pro Batch. */
const BATCH_LIMIT = 500;

export interface FirestoreAccountServiceDependencies {
  db: Firestore;
  /** Verlassen der Runden – dieselbe Logik wie `leaveGroup`. */
  groups: Pick<GroupService, "leaveAllGroups">;
  authUsers: AuthUserDeleter;
  logger: Logger;
  /** Dokumente pro Lösch-Batch (Standard und Höchstwert: 500). */
  batchSize?: number;
}

/**
 * Löscht ein Konto in der Reihenfolge des Vertrags. Zuerst werden alle Runden verlassen – danach
 * lassen die Regeln keine neuen geteilten Getränke mehr zu, sodass die anschließende
 * Collection-Group-Abfrage alle eigenen Getränke erwischt (auch in längst verlassenen Runden).
 * Der Auth-Nutzer wird zuletzt gelöscht: Scheitert vorher etwas, kann die App es erneut versuchen.
 */
export class FirestoreAccountService implements AccountService {
  private readonly batchSize: number;

  constructor(private readonly deps: FirestoreAccountServiceDependencies) {
    this.batchSize = Math.min(deps.batchSize ?? BATCH_LIMIT, BATCH_LIMIT);
  }

  async deleteAccount(caller: Caller): Promise<void> {
    const { db, groups, authUsers, logger } = this.deps;
    const uid = caller.uid;

    const groupsLeft = await groups.leaveAllGroups(caller);
    const drinksDeleted = await this.deleteSharedDrinksOf(uid);
    await db.recursiveDelete(db.collection(Collections.users).doc(uid));
    await db.collection(Collections.joinAttempts).doc(uid).delete();
    await authUsers.deleteUser(uid);

    logger.info("Konto gelöscht.", { uid, groupsLeft, drinksDeleted });
  }

  /** Alle Getränke mit `authorId == uid` in allen Runden (Collection-Group-Abfrage), in Batches. */
  private async deleteSharedDrinksOf(uid: string): Promise<number> {
    const { db } = this.deps;
    const query = db.collectionGroup(Collections.drinks).where("authorId", "==", uid).select().limit(this.batchSize);
    let deleted = 0;
    for (;;) {
      const snapshot = await query.get();
      if (snapshot.empty) return deleted;
      const batch = db.batch();
      for (const doc of snapshot.docs) batch.delete(doc.ref);
      await batch.commit();
      deleted += snapshot.size;
      if (snapshot.size < this.batchSize) return deleted;
    }
  }
}
