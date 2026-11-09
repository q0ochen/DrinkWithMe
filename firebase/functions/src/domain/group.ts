import { UNKNOWN_DISPLAY_NAME } from "./displayName";

/** Höchstzahl der Mitglieder einer Trinkrunde. */
export const MAX_GROUP_MEMBERS = 50;

/** Eintrag in `groups/{groupId}.members`. */
export interface MemberEntry {
  displayName: string;
}

/**
 * Fachliche Felder von `groups/{groupId}` (ohne `createdAt`, das der Server setzt).
 * `memberIds` ist in Beitrittsreihenfolge sortiert.
 */
export interface GroupState {
  name: string;
  inviteCode: string;
  ownerId: string;
  memberIds: string[];
  members: Record<string, MemberEntry>;
}

/** Mitglied in der Antwort der Callables (`GroupMember` im Kotlin-Kern). */
export interface GroupMemberDto {
  userId: string;
  displayName: string;
}

/** Antwortformat der Callables (`DrinkGroup` im Kotlin-Kern). */
export interface GroupDto {
  id: string;
  name: string;
  inviteCode: string;
  ownerId: string;
  members: GroupMemberDto[];
}

/**
 * Prüft eine Runden-ID aus einer Anfrage. Erlaubt sind die Zeichen automatisch erzeugter
 * Firestore-IDs (plus `-` und `_`), keine Pfade und keine reservierten IDs wie `__name__`.
 *
 * @returns die ID oder `null`, wenn sie ungültig ist.
 */
export function parseGroupId(raw: unknown): string | null {
  if (typeof raw !== "string") return null;
  if (!/^[A-Za-z0-9_-]{1,128}$/u.test(raw) || /^__.*__$/u.test(raw)) return null;
  return raw;
}

/** Bildet ein Rundendokument auf das Antwortformat ab – Mitglieder in Beitrittsreihenfolge. */
export function toGroupDto(id: string, group: GroupState): GroupDto {
  return {
    id,
    name: group.name,
    inviteCode: group.inviteCode,
    ownerId: group.ownerId,
    members: group.memberIds.map((userId) => ({
      userId,
      displayName: group.members[userId]?.displayName ?? UNKNOWN_DISPLAY_NAME,
    })),
  };
}

/**
 * Liest die Daten eines Rundendokuments defensiv ein.
 *
 * @returns die Runde oder `null`, wenn die Daten nicht dem Vertrag entsprechen.
 */
export function parseGroupState(data: unknown): GroupState | null {
  if (!isRecord(data)) return null;
  const { name, inviteCode, ownerId, memberIds, members } = data;
  if (typeof name !== "string" || typeof inviteCode !== "string" || typeof ownerId !== "string") {
    return null;
  }
  if (!Array.isArray(memberIds) || !memberIds.every((id): id is string => typeof id === "string")) {
    return null;
  }
  const memberMap = isRecord(members) ? members : {};
  const parsedMembers: Record<string, MemberEntry> = {};
  for (const userId of memberIds) {
    const entry = memberMap[userId];
    const displayName = isRecord(entry) ? entry.displayName : undefined;
    parsedMembers[userId] = {
      displayName: typeof displayName === "string" ? displayName : UNKNOWN_DISPLAY_NAME,
    };
  }
  return { name, inviteCode, ownerId, memberIds: [...memberIds], members: parsedMembers };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
