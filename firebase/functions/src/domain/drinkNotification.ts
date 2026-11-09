import { emojiFor } from "./drinkCategory";
import { formatVolume } from "./volumeFormatter";

/** Android-Benachrichtigungskanal der App für Getränke. */
export const DRINK_NOTIFICATION_CHANNEL_ID = "drinks";

/** Die Felder eines geteilten Getränks (`groups/{groupId}/drinks/{drinkId}`), die der Push braucht. */
export interface SharedDrink {
  authorId: string;
  authorName: string;
  name: string;
  category: string;
  volumeInMilliliters: number;
}

/**
 * Push-Nachricht für ein neues Getränk. Der Aufbau entspricht einer FCM-`MulticastMessage`
 * ohne `tokens`, damit diese reine Logik nicht vom Admin SDK abhängt.
 */
export interface DrinkNotification {
  notification: { title: string; body: string };
  android: { notification: { channelId: string; tag: string } };
  data: { type: "drink"; groupId: string; drinkId: string };
}

/**
 * Liest ein geteiltes Getränk defensiv ein (die Sicherheitsregeln prüfen bereits alle Felder).
 *
 * @returns das Getränk oder `null`, wenn Pflichtfelder fehlen.
 */
export function parseSharedDrink(data: unknown): SharedDrink | null {
  if (typeof data !== "object" || data === null) return null;
  const { authorId, authorName, name, category, volumeInMilliliters } = data as Record<string, unknown>;
  if (
    typeof authorId !== "string" ||
    typeof authorName !== "string" ||
    typeof name !== "string" ||
    typeof category !== "string" ||
    typeof volumeInMilliliters !== "number" ||
    !Number.isInteger(volumeInMilliliters)
  ) {
    return null;
  }
  return { authorId, authorName, name, category, volumeInMilliliters };
}

/** Empfänger: alle Mitglieder außer dem Autor (ohne Duplikate). */
export function selectRecipients(memberIds: readonly string[], authorId: string): string[] {
  return [...new Set(memberIds)].filter((id) => id !== authorId);
}

/**
 * Baut die Push-Nachricht laut Vertrag:
 * Titel „{authorName} trinkt gerade“, Text „{emoji} {name} ({Menge}) · {Rundenname}“.
 * `tag = drinkId`, damit sich dasselbe Getränk aus mehreren Runden ersetzt statt zu stapeln.
 */
export function buildDrinkNotification(
  drink: SharedDrink,
  ids: { groupId: string; drinkId: string },
  groupName: string,
): DrinkNotification {
  return {
    notification: {
      title: `${drink.authorName} trinkt gerade`,
      body: `${emojiFor(drink.category)} ${drink.name} (${formatVolume(drink.volumeInMilliliters)}) · ${groupName}`,
    },
    android: {
      notification: { channelId: DRINK_NOTIFICATION_CHANNEL_ID, tag: ids.drinkId },
    },
    data: { type: "drink", groupId: ids.groupId, drinkId: ids.drinkId },
  };
}
