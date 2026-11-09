import type { GroupDto } from "../domain/group";

/** Der angemeldete Aufrufer einer Callable-Funktion. */
export interface Caller {
  uid: string;
  /** `request.auth.token.name` – Ersatz, wenn das Profil keinen Anzeigenamen hat. */
  tokenName?: string;
  /**
   * Google-Kennung (`sub`) aus `request.auth.token.firebase.identities["google.com"][0]`. Bleibt gleich,
   * wenn das Firebase-Konto gelöscht und neu angelegt wird – Schlüssel des Schutzes vor Code-Raten.
   */
  googleSubject?: string;
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
   *   `failed-precondition` (Runde voll), `resource-exhausted` (zu viele falsche Codes).
   */
  joinGroup(caller: Caller, code: unknown): Promise<GroupDto>;

  /**
   * Idempotent: Nicht-Mitglieder und unbekannte Runden sind kein Fehler. Verlässt das letzte
   * Mitglied die Runde, werden Runde und Code gelöscht; die Getränke räumt `onGroupDeleted` auf.
   */
  leaveGroup(caller: Caller, groupId: unknown): Promise<void>;

  /** Verlässt alle Runden des Aufrufers (gleiche Logik wie `leaveGroup`). @returns die Anzahl der Runden. */
  leaveAllGroups(caller: Caller): Promise<number>;
}

/** Liest den Anzeigenamen aus dem Profil (`users/{uid}.displayName`). */
export interface ProfileReader {
  displayNameOf(userId: string): Promise<string | null>;
}
