/** Region aller Functions – identisch zur Firestore-Datenbank (Frankfurt, siehe Vertrag). */
export const REGION = "europe-west3";

/** Obergrenze gleichzeitiger Instanzen pro Funktion (Kostenbremse). */
export const MAX_INSTANCES = 10;

/** Pfad des Triggers für neu geteilte Getränke (Dokument-ID = "{authorId}_{drinkId}"). */
export const SHARED_DRINK_DOCUMENT = "groups/{groupId}/drinks/{docId}";

/** Pfad des Triggers, der nach dem Auflösen einer Runde ihre Getränke löscht. */
export const GROUP_DOCUMENT = "groups/{groupId}";

/**
 * Laufzeit-Obergrenze für das Aufräumen einer aufgelösten Runde und für `deleteAccount`
 * (Höchstwert für ereignisgesteuerte Functions). Beides ist idempotent und darf wiederholt werden.
 */
export const LONG_RUNNING_TIMEOUT_SECONDS = 540;
