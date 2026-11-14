import type { FriendDrinkEvent } from "../social/ports";
import { withIndefiniteArticle } from "./germanArticles";

/**
 * **Anzeige-Texte** zu Ereignissen von Freunden (Port von `EventTexts`, mit Ziffern und Einheitenkürzeln) –
 * gleich wie die vom Server gesendeten Benachrichtigungen. Zum Vorlesen: `SpeechTexts`.
 */
export const EventTexts = {
  /** Titel der Benachrichtigung: „Anna trinkt gerade“. */
  notificationTitle(authorName: string): string {
    return `${authorName.trim()} trinkt gerade`;
  },

  /** Text der Benachrichtigung: „Apfelschorle (500 ml)“. */
  notificationBody(drinkName: string, volumeMilliliters: number): string {
    return `${drinkName.trim()} (${volumeMilliliters} ml)`;
  },

  eventTitle(event: FriendDrinkEvent): string {
    return EventTexts.notificationTitle(event.author.displayName);
  },

  eventBody(event: FriendDrinkEvent): string {
    return EventTexts.notificationBody(event.name, event.volumeMilliliters);
  },

  /** Großer Satz auf dem Ereignis-Bildschirm: „Anna trinkt eine Apfelschorle.“ */
  headline(event: FriendDrinkEvent): string {
    return `${event.author.displayName.trim()} trinkt ${withIndefiniteArticle(event.name)}.`;
  },
} as const;
