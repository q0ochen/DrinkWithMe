import type { Session, SupabaseClient, User } from "@supabase/supabase-js";
import type { UserProfile } from "../core/social/ports";
import { SocialError } from "./errors";
import type { AuthService } from "./types";

/** Anzeigename aus den Google-Daten (wie der Profil-Trigger: full_name | name). Nie die E-Mail-Adresse. */
export function profileFromUser(user: User): UserProfile {
  const metadata = (user.user_metadata ?? {}) as Record<string, unknown>;
  const pick = (...keys: string[]) => {
    for (const key of keys) {
      const value = metadata[key];
      if (typeof value === "string" && value.trim().length > 0) return value.trim();
    }
    return null;
  };
  const avatar = pick("avatar_url", "picture");
  return {
    id: user.id,
    displayName: (pick("full_name", "name") ?? "Ich").slice(0, 50),
    avatarUrl: avatar && avatar.startsWith("https://") ? avatar : null,
  };
}

export interface SupabaseAuthOptions {
  /** Adresse, zu der Google/Supabase nach der Anmeldung zurückleitet: `<WEB_APP_URL>/auth/callback`. */
  readonly redirectTo: string;
  /** Merkt sich, wohin nach der Rückkehr navigiert wird. */
  readonly rememberReturnPath: (path: string) => Promise<void>;
  /** Öffnet die Google-Anmeldeseite (im Browser: `location.assign`). */
  readonly openUrl: (url: string) => void;
}

/** {@link AuthService} über supabase-js (PKCE-Ablauf, Sitzung in IndexedDB). */
export class SupabaseAuthService implements AuthService {
  private user: UserProfile | null = null;
  private readonly listeners = new Set<(user: UserProfile | null) => void>();
  private readonly initialized: Promise<void>;

  constructor(
    private readonly client: SupabaseClient,
    private readonly options: SupabaseAuthOptions,
  ) {
    this.client.auth.onAuthStateChange((_event, session) => {
      // Keine weiteren Supabase-Aufrufe direkt im Rückruf (Empfehlung von supabase-js) – nur Zustand setzen.
      this.apply(session);
    });
    this.initialized = this.client.auth
      .getSession()
      .then(({ data }) => this.apply(data.session))
      .catch(() => this.apply(null));
  }

  ready(): Promise<void> {
    return this.initialized;
  }

  currentUser(): UserProfile | null {
    return this.user;
  }

  subscribe(listener: (user: UserProfile | null) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  async signInWithGoogle(returnPath: string): Promise<void> {
    await this.options.rememberReturnPath(returnPath);
    const { data, error } = await this.client.auth.signInWithOAuth({
      provider: "google",
      options: { redirectTo: this.options.redirectTo, skipBrowserRedirect: true },
    });
    if (error || !data.url) throw new SocialError("network", error);
    this.options.openUrl(data.url);
  }

  async completeSignIn(callbackUrl: URL): Promise<void> {
    const failure = callbackUrl.searchParams.get("error") ?? callbackUrl.searchParams.get("error_code");
    if (failure) throw new SocialError("not_authenticated", failure);
    const code = callbackUrl.searchParams.get("code");
    if (!code) {
      // Schon angemeldet (z. B. Seite neu geladen) – dann ist nichts zu tun.
      await this.initialized;
      if (this.user) return;
      throw new SocialError("not_authenticated");
    }
    const { data, error } = await this.client.auth.exchangeCodeForSession(code);
    if (error || !data.session) throw new SocialError("not_authenticated", error);
    this.apply(data.session);
  }

  async signOut(scope: "global" | "local" = "global"): Promise<void> {
    const { error } = await this.client.auth.signOut({ scope });
    if (error && scope === "global") {
      // Server nicht erreichbar: trotzdem lokal abmelden.
      await this.client.auth.signOut({ scope: "local" });
    }
    this.apply(null);
  }

  private apply(session: Session | null): void {
    const next = session?.user ? profileFromUser(session.user) : null;
    if (next?.id === this.user?.id && next?.displayName === this.user?.displayName) return;
    this.user = next;
    for (const listener of this.listeners) listener(next);
  }
}
