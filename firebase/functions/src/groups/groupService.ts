import type { GroupDto } from "../domain/group";

/** Der angemeldete Aufrufer einer Callable-Funktion. */
export interface Caller {
  uid: string;
  /** `request.auth.token.name` – Ersatz, wenn das Profil keinen Anzeigenamen hat. */
  tokenName?: string;
}

/**
 * Erstellen, Beitreten und Verlassen von Trinkrunden (Gegenstück zu `GroupMembershipService`
 * im Kotlin-Kern). Eingaben kommen ungeprüft aus der Anfrage und werden hier validiert.
 */
export interface GroupService {
  /** @throws SocialError `invalid-argument` bei leerem oder zu langem Namen. */
  createGroup(caller: Caller, name: unknown): Promise<GroupDto>;

  /**
   * Idempotent: Wer schon Mitglied ist, bekommt die Runde zurück.
   *
   * @throws SocialError `invalid-argument` (Format), `not-found` (unbekannter Code),
   *   `resource-exhausted` (Runde voll).
   */
  joinGroup(caller: Caller, code: unknown): Promise<GroupDto>;

  /** Idempotent: Nicht-Mitglieder und unbekannte Runden sind kein Fehler. */
  leaveGroup(caller: Caller, groupId: unknown): Promise<void>;
}

/** Liest den Anzeigenamen aus dem Profil (`users/{uid}.displayName`). */
export interface ProfileReader {
  displayNameOf(userId: string): Promise<string | null>;
}
