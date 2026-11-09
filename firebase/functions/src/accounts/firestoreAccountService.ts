import type { Firestore } from "firebase-admin/firestore";
import type { Clock, JoinThrottlePolicy } from "../domain/joinThrottle";
import { JOIN_THROTTLE_POLICY, isJoinWindowActive, joinThrottleKey } from "../domain/joinThrottle";
import { parseJoinAttempts, toJoinAttemptsDocument } from "../firestore/joinAttemptsDocument";
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
  /** Zeitquelle für „läuft das Fenster des Zählers gegen Code-Raten noch?“ (Standard: `Date.now`). */
  clock?: Clock;
  /** Grenze des Schutzes vor Code-Raten (Standard: Vertrag, 1 Stunde). */
  joinThrottle?: JoinThrottlePolicy;
}

/** Was mit dem Zähler gegen Code-Raten geschah (nur fürs Log). */
type JoinAttemptsRelease = "none" | "deleted" | "kept";

/**
 * Löscht ein Konto in der Reihenfolge des Vertrags. Zuerst werden alle Runden verlassen – danach
 * lassen die Regeln keine neuen geteilten Getränke mehr zu, sodass die anschließende
 * Collection-Group-Abfrage alle eigenen Getränke erwischt (auch in längst verlassenen Runden).
 * Der Auth-Nutzer wird zuletzt gelöscht: Scheitert vorher etwas, kann die App es erneut versuchen.
 */
export class FirestoreAccountService implements AccountService {
  private readonly batchSize: number;
  private readonly clock: Clock;
  private readonly joinThrottle: JoinThrottlePolicy;

  constructor(private readonly deps: FirestoreAccountServiceDependencies) {
    this.batchSize = Math.min(deps.batchSize ?? BATCH_LIMIT, BATCH_LIMIT);
    this.clock = deps.clock ?? (() => Date.now());
    this.joinThrottle = deps.joinThrottle ?? JOIN_THROTTLE_POLICY;
  }

  async deleteAccount(caller: Caller): Promise<void> {
    const { db, groups, authUsers, logger } = this.deps;
    const uid = caller.uid;

    const groupsLeft = await groups.leaveAllGroups(caller);
    const drinksDeleted = await this.deleteSharedDrinksOf(uid);
    await db.recursiveDelete(db.collection(Collections.users).doc(uid));
    const joinAttempts = await this.releaseJoinAttempts(caller);
    await authUsers.deleteUser(uid);

    logger.info("Konto gelöscht.", { uid, groupsLeft, drinksDeleted, joinAttempts });
  }

  /**
   * Zähler gegen Code-Raten (`joinAttempts/{key}`, Schlüssel am Google-Konto): Läuft sein Fenster noch,
   * bleibt er erhalten – sonst ließe sich die Sperre mit „Konto löschen + neu anmelden“ aufheben. Er
   * bekommt dabei sicher `expireAt`, sodass die TTL-Richtlinie ihn nach Ablauf löscht. Ein abgelaufener
   * oder unlesbarer Zähler wird sofort gelöscht. In einer Transaktion, damit ein gleichzeitiger
   * Fehlversuch kein frisch begonnenes Fenster verliert. Ein alter Zähler unter der uid (vor dem
   * Schlüssel am Google-Konto geschrieben) wird nicht mehr gelesen und deshalb immer gelöscht.
   */
  private async releaseJoinAttempts(caller: Caller): Promise<JoinAttemptsRelease> {
    const { db } = this.deps;
    const key = joinThrottleKey(caller);
    const attemptsRef = db.collection(Collections.joinAttempts).doc(key);

    const release = await db.runTransaction(async (transaction): Promise<JoinAttemptsRelease> => {
      const snapshot = await transaction.get(attemptsRef);
      if (!snapshot.exists) return "none";
      const attempts = parseJoinAttempts(snapshot.data());
      if (attempts !== null && isJoinWindowActive(attempts, this.clock(), this.joinThrottle)) {
        transaction.set(attemptsRef, toJoinAttemptsDocument(attempts, this.joinThrottle));
        return "kept";
      }
      transaction.delete(attemptsRef);
      return "deleted";
    });

    if (key !== caller.uid) await db.collection(Collections.joinAttempts).doc(caller.uid).delete();
    return release;
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
