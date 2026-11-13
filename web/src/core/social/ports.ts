import type { DrinkCategory } from "../bar/drinkCategory";
import type { DrinkIcon } from "../bar/drinkIcon";
import type { DrinkEntry } from "../log/drinkEntry";

// Ports der Vernetzung (wie `SocialPorts.kt`). Der Kern kennt nur diese kleinen Schnittstellen;
// die Supabase-Implementierungen liegen in `src/social` (Dependency Inversion).

/** Öffentliches Profil (Tabelle `profiles`) – ohne E-Mail-Adresse. */
export interface UserProfile {
  readonly id: string;
  readonly displayName: string;
  readonly avatarUrl: string | null;
}

/** Eine bestätigte Freundschaft. */
export interface Friend {
  readonly profile: UserProfile;
  readonly since: number;
}

/** Offene Freundschaftsanfrage – eingehend (an mich) oder ausgehend (von mir). */
export interface FriendRequest {
  readonly id: string;
  readonly other: UserProfile;
  readonly direction: "INCOMING" | "OUTGOING";
  readonly createdAt: number;
}

/** Ein geteiltes Getränk eines Freundes (Tabelle `drink_events`). */
export interface FriendDrinkEvent {
  readonly id: string;
  readonly author: UserProfile;
  readonly name: string;
  readonly icon: DrinkIcon;
  readonly category: DrinkCategory;
  readonly volumeMilliliters: number;
  readonly consumedAt: number;
}

/** Liefert den angemeldeten Nutzer oder `null` und meldet Änderungen. */
export interface SessionProvider {
  currentUser(): UserProfile | null;
  /** Gibt eine Abmeldefunktion zurück. */
  subscribe(listener: (user: UserProfile | null) => void): () => void;
}

/** Freunde, Anfragen und Einladungs-Links. */
export interface FriendsService {
  friends(): Promise<Friend[]>;
  requests(): Promise<FriendRequest[]>;
  /** Antwort immer neutral (kein Hinweis, ob die Adresse existiert). */
  sendRequestByEmail(email: string): Promise<void>;
  respond(requestId: string, accept: boolean): Promise<void>;
  cancel(requestId: string): Promise<void>;
  /** Vollständige https-URL. */
  createInviteLink(): Promise<string>;
  acceptInvite(token: string): Promise<UserProfile>;
  removeFriend(userId: string): Promise<void>;
}

/** Geteilte Getränke der Freunde, neueste zuerst; `since` = nur neuere als dieser Zeitpunkt. */
export interface FeedService {
  recentEvents(since: number | null, limit?: number): Promise<FriendDrinkEvent[]>;
  event(id: string): Promise<FriendDrinkEvent | null>;
}

/** Teilt eigene Einträge bzw. zieht sie zurück (nur die Felder aus `SharedDrink`). */
export interface EventPublisher {
  publish(entry: DrinkEntry): Promise<void>;
  unpublish(entryId: string): Promise<void>;
}

export const FEED_DEFAULT_LIMIT = 100;
