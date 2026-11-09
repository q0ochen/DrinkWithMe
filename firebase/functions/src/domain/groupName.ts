import { SocialErrors } from "./errors";

/** Höchstlänge eines Rundennamens – wie `CreateGroupUseCase.MAXIMUM_GROUP_NAME_LENGTH`. */
export const MAX_GROUP_NAME_LENGTH = 30;

/**
 * Prüft einen Rundennamen (1–30 Zeichen nach dem Trimmen).
 *
 * @returns den getrimmten Namen.
 * @throws SocialError `invalid-argument`, wenn der Name leer oder zu lang ist.
 */
export function validateGroupName(raw: unknown): string {
  const name = typeof raw === "string" ? raw.trim() : "";
  if (name.length === 0) throw SocialErrors.emptyGroupName();
  if (name.length > MAX_GROUP_NAME_LENGTH) throw SocialErrors.groupNameTooLong(MAX_GROUP_NAME_LENGTH);
  return name;
}
