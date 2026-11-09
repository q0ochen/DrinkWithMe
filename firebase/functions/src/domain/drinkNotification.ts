import { emojiFor } from "./drinkCategory";
import { formatVolume } from "./volumeFormatter";

/** Die Felder eines geteilten Getränks (`groups/{groupId}/drinks/{authorId}_{drinkId}`), die der Push braucht. */
export interface SharedDrink {
  drinkId: string;
  authorId: string;
  authorName: string;
  name: string;
  category: string;
  volumeInMilliliters: number;
}

/** Wo das Getränk liegt: Runde und Dokument-ID (`"{authorId}_{drinkId}"`). */
export interface SharedDrinkLocation {
  groupId: string;
  docId: string;
}

/** Nutzdaten einer Push-Nachricht – FCM verlangt für `data` ausschließlich Zeichenketten. */
export interface DrinkMessageData {
  type: "drink";
  groupId: string;
  drinkId: string;
  docId: string;
  /** Für wen die Nachricht bestimmt ist – die App zeigt sie nur diesem angemeldeten Nutzer. */
  recipientId: string;
  title: string;
  body: string;
}

/**
 * Reine Daten-Nachricht (ohne `notification`-Block) an genau einen Empfänger.
 * Der Aufbau entspricht einer FCM-`MulticastMessage` ohne `tokens`, damit diese reine
 * Logik nicht vom Admin SDK abhängt. `priority: "high"` lässt die App die Nachricht
 * auch im Hintergrund sofort selbst anzeigen.
 */
export interface DrinkMessage {
  android: { priority: "high" };
  data: DrinkMessageData;
}

/**
 * Liest ein geteiltes Getränk defensiv ein (die Sicherheitsregeln prüfen bereits alle Felder).
 *
 * @returns das Getränk oder `null`, wenn Pflichtfelder fehlen.
 */
export function parseSharedDrink(data: unknown): SharedDrink | null {
  if (typeof data !== "object" || data === null) return null;
  const { drinkId, authorId, authorName, name, category, volumeInMilliliters } = data as Record<string, unknown>;
  if (
    typeof drinkId !== "string" ||
    typeof authorId !== "string" ||
    typeof authorName !== "string" ||
    typeof name !== "string" ||
    typeof category !== "string" ||
    typeof volumeInMilliliters !== "number" ||
    !Number.isInteger(volumeInMilliliters)
  ) {
    return null;
  }
  return { drinkId, authorId, authorName, name, category, volumeInMilliliters };
}

/** Empfänger: alle Mitglieder außer dem Autor (ohne Duplikate). */
export function selectRecipients(memberIds: readonly string[], authorId: string): string[] {
  return [...new Set(memberIds)].filter((id) => id !== authorId);
}

/** Titel laut Vertrag: „{authorName} trinkt gerade“. */
export function drinkTitle(drink: SharedDrink): string {
  return `${drink.authorName} trinkt gerade`;
}

/** Text laut Vertrag: „{emoji} {name} ({Menge}) · {Rundenname}“. */
export function drinkBody(drink: SharedDrink, groupName: string): string {
  return `${emojiFor(drink.category)} ${drink.name} (${formatVolume(drink.volumeInMilliliters)}) · ${groupName}`;
}

/**
 * Baut die Daten-Nachricht für einen Empfänger laut Vertrag:
 * `data = { type, groupId, drinkId, docId, recipientId, title, body }`, `android.priority = "high"`.
 */
export function buildDrinkMessage(
  drink: SharedDrink,
  location: SharedDrinkLocation,
  groupName: string,
  recipientId: string,
): DrinkMessage {
  return {
    android: { priority: "high" },
    data: {
      type: "drink",
      groupId: location.groupId,
      drinkId: drink.drinkId,
      docId: location.docId,
      recipientId,
      title: drinkTitle(drink),
      body: drinkBody(drink, groupName),
    },
  };
}
