import type { EventPublisher, FeedService, FriendsService, SessionProvider } from "../core/social/ports";

/**
 * Eigene, noch offene Anfrage per E-Mail – bewusst **ohne** Empfänger: Der Server verrät nicht, ob bzw. wen sie
 * erreicht; angezeigt wird nur die maskierte Adresse („Anfrage an b***@example.org“).
 */
export interface OutgoingFriendRequest {
  readonly id: string;
  readonly emailHint: string;
  readonly createdAt: number;
}

/** Ausgehende Anfragen (RPC `list_outgoing_friend_requests`); Zurückziehen über {@link FriendsService.cancel}. */
export interface OutgoingRequestsService {
  outgoingRequests(): Promise<OutgoingFriendRequest[]>;
}

/** Anmeldung mit Google über Supabase (PKCE im Browser). */
export interface AuthService extends SessionProvider {
  /** Wartet, bis eine gespeicherte Sitzung wiederhergestellt ist. */
  ready(): Promise<void>;
  /** Leitet zu Google weiter; `returnPath` (z. B. /einladung/<token>) wird nach der Rückkehr geöffnet. */
  signInWithGoogle(returnPath: string): Promise<void>;
  /** Schließt die Anmeldung auf /auth/callback ab (Code gegen Sitzung tauschen). */
  completeSignIn(callbackUrl: URL): Promise<void>;
  /** Abmelden; `local` = nur auf diesem Gerät (z. B. nach dem Löschen des Kontos). */
  signOut(scope?: "global" | "local"): Promise<void>;
}

/** Zustand der Benachrichtigungen auf diesem Gerät. */
export interface PushStatus {
  /** Browser kann Web Push (Service Worker, PushManager, Notification). */
  readonly supported: boolean;
  /** iPhone/iPad im Browser: Push gibt es erst nach „Zum Home-Bildschirm“. */
  readonly needsHomeScreen: boolean;
  readonly permission: NotificationPermission | "unsupported";
  /** Dieses Gerät ist für den angemeldeten Nutzer angemeldet (Browser-Abo und Zeile auf dem Server). */
  readonly subscribed: boolean;
  /** VAPID-Schlüssel vorhanden. */
  readonly configured: boolean;
}

/** Web Push (VAPID) über den Service Worker; Abos in `push_subscriptions` mit `platform = 'web'`. */
export interface PushService {
  status(): Promise<PushStatus>;
  /** Muss direkt aus einer Nutzeraktion aufgerufen werden (Berechtigungsabfrage). */
  enable(): Promise<void>;
  disable(): Promise<void>;
  /**
   * Nach dem Start bzw. der Anmeldung: vorhandenes Abo beim Server sicherstellen. `wanted` = der Nutzer hat
   * Benachrichtigungen eingeschaltet (`pushWanted`): Fehlt das Browser-Abo, wird ohne Abfrage neu abonniert,
   * sofern die Berechtigung schon erteilt ist.
   */
  sync(wanted: boolean): Promise<void>;
}

/** Konto löschen über die Edge Function `delete-account`. */
export interface AccountService {
  deleteAccount(): Promise<void>;
}

/** Alles, was die Vernetzung anbietet – nur vorhanden, wenn der Server eingerichtet ist. */
export interface SocialServices {
  readonly auth: AuthService;
  /** `requests()` liefert nur eingehende Anfragen; ausgehende über `outgoingRequests()`. */
  readonly friends: FriendsService & OutgoingRequestsService;
  readonly feed: FeedService;
  readonly publisher: EventPublisher;
  readonly push: PushService;
  readonly account: AccountService;
}
