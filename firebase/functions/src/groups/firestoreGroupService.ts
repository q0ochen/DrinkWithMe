import { FieldValue } from "firebase-admin/firestore";
import type { Firestore } from "firebase-admin/firestore";
import { resolveDisplayName } from "../domain/displayName";
import { SocialErrors } from "../domain/errors";
import type { GroupDto, GroupState } from "../domain/group";
import { parseGroupId, parseGroupState, toGroupDto } from "../domain/group";
import { validateGroupName } from "../domain/groupName";
import type { InviteCodeGenerator } from "../domain/inviteCode";
import { parseInviteCode } from "../domain/inviteCode";
import type { Clock, JoinThrottlePolicy } from "../domain/joinThrottle";
import { JOIN_THROTTLE_POLICY, isJoinBlocked, joinThrottleKey, recordFailedJoin } from "../domain/joinThrottle";
import { newGroup, planJoin, planLeave } from "../domain/membership";
import type { Member } from "../domain/membership";
import { parseJoinAttempts, toJoinAttemptsDocument } from "../firestore/joinAttemptsDocument";
import { Collections } from "../firestore/paths";
import type { Caller, GroupService, ProfileReader } from "./groupService";

export interface FirestoreGroupServiceOptions {
  /** Wie oft ein neuer Code versucht wird, falls ein erzeugter Code schon vergeben ist. */
  maxInviteCodeAttempts: number;
  /** Zeitquelle für den Schutz vor Code-Raten (im Test steuerbar). */
  clock: Clock;
  /** Grenze für Fehlversuche beim Beitreten. */
  joinThrottle: JoinThrottlePolicy;
}

const DEFAULT_OPTIONS: FirestoreGroupServiceOptions = {
  maxInviteCodeAttempts: 10,
  clock: () => Date.now(),
  joinThrottle: JOIN_THROTTLE_POLICY,
};

/** Ergebnis der Beitritts-Transaktion. „Nicht gefunden“ wird erst nach dem Commit geworfen. */
type JoinOutcome = { kind: "joined"; group: GroupDto } | { kind: "not-found" };

/**
 * Trinkrunden in Cloud Firestore. Jede Änderung läuft in einer Transaktion, damit gleichzeitige
 * Beitritte/Austritte sich nicht gegenseitig überschreiben. Die fachlichen Regeln stecken in
 * `domain/membership.ts` und `domain/joinThrottle.ts`; diese Klasse liest und schreibt nur.
 */
export class FirestoreGroupService implements GroupService {
  private readonly options: FirestoreGroupServiceOptions;

  constructor(
    private readonly db: Firestore,
    private readonly inviteCodes: InviteCodeGenerator,
    private readonly profiles: ProfileReader,
    options: Partial<FirestoreGroupServiceOptions> = {},
  ) {
    this.options = { ...DEFAULT_OPTIONS, ...options };
  }

  async createGroup(caller: Caller, rawName: unknown): Promise<GroupDto> {
    const name = validateGroupName(rawName);
    const owner = await this.memberFor(caller);
    const groupRef = this.groups().doc();

    // Der Code wird in derselben Transaktion reserviert, in der die Runde entsteht:
    // inviteCodes/{code} existiert genau dann, wenn der Code vergeben ist.
    for (let attempt = 1; attempt <= this.options.maxInviteCodeAttempts; attempt++) {
      const group = newGroup(name, this.inviteCodes.next(), owner);
      const codeRef = this.codes().doc(group.inviteCode);
      const reserved = await this.db.runTransaction(async (transaction) => {
        const existing = await transaction.get(codeRef);
        if (existing.exists) return false;
        transaction.create(codeRef, { groupId: groupRef.id });
        transaction.create(groupRef, { ...group, createdAt: FieldValue.serverTimestamp() });
        return true;
      });
      if (reserved) return toGroupDto(groupRef.id, group);
    }
    throw SocialErrors.inviteCodesExhausted();
  }

  async joinGroup(caller: Caller, rawCode: unknown): Promise<GroupDto> {
    const code = parseInviteCode(rawCode);
    if (code === null) throw SocialErrors.invalidInviteCode();
    const member = await this.memberFor(caller);
    // Gezählt wird pro Google-Konto (sonst pro uid) – so bringt „Konto löschen + neu anmelden“ keinen neuen Zähler.
    const attemptsRef = this.joinAttempts().doc(joinThrottleKey(caller));
    const { clock, joinThrottle } = this.options;

    const outcome = await this.db.runTransaction(async (transaction): Promise<JoinOutcome> => {
      // Schutz vor Code-Raten: Wer zu oft falsch lag, wird abgewiesen, bevor der Code nachgeschlagen wird.
      // Erfolgreiche Beitritte setzen den Zähler bewusst nicht zurück (sonst ließe er sich mit dem
      // Code einer eigenen Runde beliebig zurücksetzen).
      const now = clock();
      const attempts = parseJoinAttempts((await transaction.get(attemptsRef)).data());
      if (isJoinBlocked(attempts, now, joinThrottle)) throw SocialErrors.tooManyJoinAttempts();

      const codeSnapshot = await transaction.get(this.codes().doc(code));
      const groupId: unknown = codeSnapshot.get("groupId");
      const groupRef = typeof groupId === "string" ? this.groups().doc(groupId) : null;
      const group = groupRef === null ? null : parseGroupState((await transaction.get(groupRef)).data());
      if (groupRef === null || group === null) {
        // Fehlversuch zählen und committen – deshalb wird `not-found` erst nach der Transaktion geworfen.
        transaction.set(attemptsRef, toJoinAttemptsDocument(recordFailedJoin(attempts, now, joinThrottle), joinThrottle));
        return { kind: "not-found" };
      }

      const plan = planJoin(group, member);
      if (plan.kind === "join") {
        transaction.update(groupRef, {
          memberIds: plan.group.memberIds,
          members: plan.group.members,
        });
      }
      return { kind: "joined", group: toGroupDto(groupRef.id, plan.group) };
    });

    if (outcome.kind === "not-found") throw SocialErrors.groupNotFound();
    return outcome.group;
  }

  async leaveGroup(caller: Caller, rawGroupId: unknown): Promise<void> {
    const groupId = parseGroupId(rawGroupId);
    if (groupId === null) throw SocialErrors.invalidGroupId();
    await this.leave(caller.uid, groupId);
  }

  async leaveAllGroups(caller: Caller): Promise<number> {
    const snapshot = await this.groups().where("memberIds", "array-contains", caller.uid).select().get();
    for (const doc of snapshot.docs) await this.leave(caller.uid, doc.id);
    return snapshot.size;
  }

  /**
   * Verlässt eine Runde in einer Transaktion. Das letzte Mitglied löst sie auf: Runde und Code
   * werden gelöscht. Die Getränke entfernt danach der wiederholbare Trigger `onGroupDeleted` –
   * ohne Runden-Dokument verweigern die Regeln bereits neue Getränke.
   */
  private async leave(userId: string, groupId: string): Promise<void> {
    const groupRef = this.groups().doc(groupId);
    await this.db.runTransaction(async (transaction) => {
      const group = parseGroupState((await transaction.get(groupRef)).data());
      if (group === null) return;

      const plan = planLeave(group, userId);
      switch (plan.kind) {
        case "not-member":
          return;
        case "update":
          transaction.update(groupRef, updatedMembership(plan.group));
          return;
        case "delete-group": {
          const codeRef = this.codes().doc(group.inviteCode);
          const codeSnapshot = await transaction.get(codeRef);
          if (codeSnapshot.get("groupId") === groupId) transaction.delete(codeRef);
          transaction.delete(groupRef);
          return;
        }
      }
    });
  }

  private async memberFor(caller: Caller): Promise<Member> {
    const profileName = await this.profiles.displayNameOf(caller.uid);
    return { userId: caller.uid, displayName: resolveDisplayName(profileName, caller.tokenName) };
  }

  private groups() {
    return this.db.collection(Collections.groups);
  }

  private codes() {
    return this.db.collection(Collections.inviteCodes);
  }

  private joinAttempts() {
    return this.db.collection(Collections.joinAttempts);
  }
}

function updatedMembership(group: GroupState) {
  return { ownerId: group.ownerId, memberIds: group.memberIds, members: group.members };
}
