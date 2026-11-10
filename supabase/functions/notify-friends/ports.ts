/**
 * Ports (kleine Schnittstellen) des Anwendungsfalls „Freunde benachrichtigen“. Die Supabase-Umsetzungen stehen
 * in supabase_adapters.ts, Test-Attrappen in supabase/functions/tests/.
 */

/** Ein geteiltes Ereignis, so weit es für die Benachrichtigung gebraucht wird. */
export interface ClaimedDrinkEvent {
  id: string;
  authorId: string;
  name: string;
  volumeMl: number;
}

/** Push-Abo eines Empfängers. */
export interface StoredSubscription {
  id: string;
  userId: string;
  endpoint: string;
  p256dh: string;
  auth: string;
}

/** Zugriff auf drink_events. */
export interface DrinkEventClaimer {
  /**
   * Setzt `notified_at` atomar und liefert das Ereignis – nur wenn es dem Autor gehört, nach `createdAfter`
   * angelegt wurde und noch nicht benachrichtigt ist. Sonst `null` (auch bei unbekannter ID).
   */
  claimForNotification(eventId: string, authorId: string, createdAfter: Date): Promise<ClaimedDrinkEvent | null>;
}

/** Zugriff auf friendships. */
export interface FriendshipReader {
  /** IDs aller Freunde des Nutzers. */
  friendIdsOf(userId: string): Promise<string[]>;
}

/** Zugriff auf profiles. */
export interface ProfileReader {
  /** Anzeigename des Nutzers oder `null`, wenn es kein Profil gibt. */
  displayNameOf(userId: string): Promise<string | null>;
}

/** Zugriff auf push_subscriptions. */
export interface SubscriptionStore {
  /** Alle Push-Abos der angegebenen Nutzer. */
  subscriptionsOf(userIds: readonly string[]): Promise<StoredSubscription[]>;
  /** Löscht ein Abo (z. B. nach 404/410 vom Push-Dienst). */
  remove(subscriptionId: string): Promise<void>;
}

/** Aufruflimit (rate_limits). */
export interface RateLimiter {
  /** Zählt einen Aufruf und liefert `true`, solange das Limit nicht überschritten ist. */
  consume(userId: string, action: string, maxCount: number, windowSeconds: number): Promise<boolean>;
}
