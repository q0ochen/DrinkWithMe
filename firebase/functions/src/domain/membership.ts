import { SocialErrors } from "./errors";
import type { GroupState, MemberEntry } from "./group";
import { MAX_GROUP_MEMBERS } from "./group";

// Reine Regeln für Erstellen, Beitreten und Verlassen. Sie berechnen nur den neuen Zustand;
// das Lesen und Schreiben in Firestore (Transaktionen) übernimmt der GroupService.

/** Ein (zukünftiges) Mitglied. */
export interface Member {
  userId: string;
  displayName: string;
}

/** Neue Runde: Der Ersteller ist Besitzer und einziges Mitglied. */
export function newGroup(name: string, inviteCode: string, owner: Member): GroupState {
  return {
    name,
    inviteCode,
    ownerId: owner.userId,
    memberIds: [owner.userId],
    members: { [owner.userId]: { displayName: owner.displayName } },
  };
}

export type JoinPlan =
  | { kind: "already-member"; group: GroupState }
  | { kind: "join"; group: GroupState };

/**
 * Beitreten ist idempotent: Wer schon Mitglied ist, bekommt die Runde unverändert zurück.
 *
 * @throws SocialError `resource-exhausted`, wenn die Runde bereits voll ist.
 */
export function planJoin(
  group: GroupState,
  member: Member,
  maximumMembers: number = MAX_GROUP_MEMBERS,
): JoinPlan {
  if (group.memberIds.includes(member.userId)) return { kind: "already-member", group };
  if (group.memberIds.length >= maximumMembers) throw SocialErrors.groupFull(maximumMembers);
  return {
    kind: "join",
    group: {
      ...group,
      memberIds: [...group.memberIds, member.userId],
      members: { ...group.members, [member.userId]: { displayName: member.displayName } },
    },
  };
}

export type LeavePlan =
  | { kind: "not-member" }
  | { kind: "delete-group" }
  | { kind: "update"; group: GroupState };

/**
 * Verlassen ist idempotent. Das letzte Mitglied löst die Runde auf; verlässt der Besitzer
 * die Runde, übernimmt das am längsten dabei gewesene verbleibende Mitglied.
 */
export function planLeave(group: GroupState, userId: string): LeavePlan {
  if (!group.memberIds.includes(userId)) return { kind: "not-member" };
  const memberIds = group.memberIds.filter((id) => id !== userId);
  const nextOwner = memberIds[0];
  if (nextOwner === undefined) return { kind: "delete-group" };
  const members: Record<string, MemberEntry> = Object.fromEntries(
    Object.entries(group.members).filter(([id]) => id !== userId),
  );
  const ownerId = memberIds.includes(group.ownerId) ? group.ownerId : nextOwner;
  return { kind: "update", group: { ...group, ownerId, memberIds, members } };
}
