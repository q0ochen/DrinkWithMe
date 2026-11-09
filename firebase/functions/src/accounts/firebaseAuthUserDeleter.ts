import type { AuthUserDeleter } from "./accountService";

/** Der einzige Teil von `firebase-admin/auth`, den das Löschen braucht (für Tests austauschbar). */
export interface AuthAdmin {
  deleteUser(uid: string): Promise<void>;
}

/** Fehlercode des Admin SDK, wenn es den Nutzer nicht (mehr) gibt. */
export const USER_NOT_FOUND_ERROR_CODE = "auth/user-not-found";

/** Löscht den Nutzer in Firebase Auth; „gibt es nicht mehr“ gilt als Erfolg (Idempotenz). */
export class FirebaseAuthUserDeleter implements AuthUserDeleter {
  constructor(private readonly auth: AuthAdmin) {}

  async deleteUser(uid: string): Promise<void> {
    try {
      await this.auth.deleteUser(uid);
    } catch (error) {
      if (errorCodeOf(error) === USER_NOT_FOUND_ERROR_CODE) return;
      throw error;
    }
  }
}

function errorCodeOf(error: unknown): unknown {
  return typeof error === "object" && error !== null ? (error as { code?: unknown }).code : undefined;
}
