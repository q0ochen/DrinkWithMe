/**
 * Inhalt der Push-Nachricht „Freund trinkt gerade“. Die Apps zeigen `title` und `body` an und lesen sie auf
 * Wunsch vor; `recipientId` erlaubt dem Gerät, Nachrichten für ein anderes (früheres) Konto zu verwerfen.
 *
 * Geteilt werden nur Name und Menge in ml – nie Gramm Alkohol/Zucker oder Statistiken.
 */

/** Ersatzname, falls das Profil des Autors fehlt. */
export const UNKNOWN_AUTHOR_NAME = "Jemand";

/** Nutzlast der Push-Nachricht (JSON). */
export interface DrinkPushPayload {
  type: "drink";
  eventId: string;
  authorId: string;
  recipientId: string;
  /** z. B. „Anna trinkt gerade“ */
  title: string;
  /** z. B. „Apfelschorle (500 ml)“ */
  body: string;
}

/** Baut die Nutzlast für genau einen Empfänger. */
export function buildDrinkPushPayload(params: {
  eventId: string;
  authorId: string;
  authorName: string;
  recipientId: string;
  drinkName: string;
  volumeMl: number;
}): DrinkPushPayload {
  const authorName = params.authorName.trim() || UNKNOWN_AUTHOR_NAME;
  return {
    type: "drink",
    eventId: params.eventId,
    authorId: params.authorId,
    recipientId: params.recipientId,
    title: `${authorName} trinkt gerade`,
    body: `${params.drinkName.trim()} (${params.volumeMl} ml)`,
  };
}
