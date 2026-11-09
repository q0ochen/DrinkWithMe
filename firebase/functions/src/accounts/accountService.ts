import type { Caller } from "../groups/groupService";

/** Konto löschen (Callable `deleteAccount`). */
export interface AccountService {
  /**
   * Löscht alles, was der Server über den Aufrufer speichert – idempotent, ein erneuter Aufruf
   * räumt Reste auf: alle Runden verlassen, alle eigenen geteilten Getränke in allen Runden
   * (auch in bereits verlassenen), `users/{uid}` samt `tokens`, `joinAttempts/{uid}` und
   * zuletzt den Firebase-Auth-Nutzer.
   */
  deleteAccount(caller: Caller): Promise<void>;
}

/** Löscht einen Firebase-Auth-Nutzer; ein bereits gelöschter Nutzer ist kein Fehler. */
export interface AuthUserDeleter {
  deleteUser(uid: string): Promise<void>;
}
