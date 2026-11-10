import type { EventPublisher, FeedService, FriendsService, SessionProvider } from "../core/social/ports";

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
  /** Dieses Gerät ist für den angemeldeten Nutzer angemeldet. */
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
  /** Nach dem Start bzw. der Anmeldung: vorhandenes Abo beim Server sicherstellen. */
  sync(): Promise<void>;
}

/** Konto löschen über die Edge Function `delete-account`. */
export interface AccountService {
  deleteAccount(): Promise<void>;
}

/** Alles, was die Vernetzung anbietet – nur vorhanden, wenn der Server eingerichtet ist. */
export interface SocialServices {
  readonly auth: AuthService;
  readonly friends: FriendsService;
  readonly feed: FeedService;
  readonly publisher: EventPublisher;
  readonly push: PushService;
  readonly account: AccountService;
}
