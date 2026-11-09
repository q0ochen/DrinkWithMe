/** Region aller Functions – identisch zur Firestore-Datenbank (Frankfurt, siehe Vertrag). */
export const REGION = "europe-west3";

/** Obergrenze gleichzeitiger Instanzen pro Funktion (Kostenbremse). */
export const MAX_INSTANCES = 10;

/** Pfad des Triggers für neu geteilte Getränke. */
export const SHARED_DRINK_DOCUMENT = "groups/{groupId}/drinks/{drinkId}";
