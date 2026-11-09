import { FieldValue } from "firebase-admin/firestore";
import type { Firestore } from "firebase-admin/firestore";
import { resolveDisplayName } from "../domain/displayName";
import { SocialErrors } from "../domain/errors";
import type { GroupDto, GroupState } from "../domain/group";
import { parseGroupId, parseGroupState, toGroupDto } from "../domain/group";
import { validateGroupName } from "../domain/groupName";
import type { InviteCodeGenerator } from "../domain/inviteCode";
import { parseInviteCode } from "../domain/inviteCode";
import { newGroup, planJoin, planLeave } from "../domain/membership";
import type { Member } from "../domain/membership";
import { Collections } from "../firestore/paths";
import type { Caller, GroupService, ProfileReader } from "./groupService";

export interface FirestoreGroupServiceOptions {
  /** Wie oft ein neuer Code versucht wird, falls ein erzeugter Code schon vergeben ist. */
  maxInviteCodeAttempts: number;
}

const DEFAULT_OPTIONS: FirestoreGroupServiceOptions = { maxInviteCodeAttempts: 10 };

/**
 * Trinkrunden in Cloud Firestore. Jede Änderung läuft in einer Transaktion, damit gleichzeitige
 * Beitritte/Austritte sich nicht gegenseitig überschreiben. Die fachlichen Regeln stecken in
 * `domain/membership.ts`; diese Klasse liest und schreibt nur.
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

    return this.db.runTransaction(async (transaction) => {
      const codeSnapshot = await transaction.get(this.codes().doc(code));
      const groupId: unknown = codeSnapshot.get("groupId");
      if (!codeSnapshot.exists || typeof groupId !== "string") throw SocialErrors.groupNotFound();

      const groupRef = this.groups().doc(groupId);
      const group = parseGroupState((await transaction.get(groupRef)).data());
      if (group === null) throw SocialErrors.groupNotFound();

      const plan = planJoin(group, member);
      if (plan.kind === "join") {
        transaction.update(groupRef, {
          memberIds: plan.group.memberIds,
          members: plan.group.members,
        });
      }
      return toGroupDto(groupId, plan.group);
    });
  }

  async leaveGroup(caller: Caller, rawGroupId: unknown): Promise<void> {
    const groupId = parseGroupId(rawGroupId);
    if (groupId === null) throw SocialErrors.invalidGroupId();
    const groupRef = this.groups().doc(groupId);

    const groupDeleted = await this.db.runTransaction(async (transaction) => {
      const group = parseGroupState((await transaction.get(groupRef)).data());
      if (group === null) return false;

      const plan = planLeave(group, caller.uid);
      switch (plan.kind) {
        case "not-member":
          return false;
        case "update":
          transaction.update(groupRef, updatedMembership(plan.group));
          return false;
        case "delete-group": {
          const codeRef = this.codes().doc(group.inviteCode);
          const codeSnapshot = await transaction.get(codeRef);
          if (codeSnapshot.get("groupId") === groupId) transaction.delete(codeRef);
          transaction.delete(groupRef);
          return true;
        }
      }
    });

    // Erst nach dem Löschen der Runde: Ohne Runden-Dokument verweigern die Regeln neue Getränke,
    // danach werden die vorhandenen Getränke (und ggf. weitere Unterdokumente) entfernt.
    if (groupDeleted) await this.db.recursiveDelete(groupRef);
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
}

function updatedMembership(group: GroupState) {
  return { ownerId: group.ownerId, memberIds: group.memberIds, members: group.members };
}
