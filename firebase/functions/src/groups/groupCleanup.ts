/** Räumt nach dem Auflösen einer Runde auf (Trigger `onGroupDeleted`). */
export interface GroupCleanup {
  /**
   * Löscht `groups/{groupId}/drinks` vollständig. Idempotent – der Trigger wird bei Fehlern
   * wiederholt. Existiert die Runde (wider Erwarten) noch, passiert nichts.
   *
   * @returns `true`, wenn aufgeräumt wurde.
   */
  deleteDrinksOf(groupId: string): Promise<boolean>;
}
