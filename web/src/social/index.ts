import { createClient, type SupabaseClient, type User } from "@supabase/supabase-js";
import type { SupabaseConfig } from "../app/config";
import { AUTH_STORAGE_KEY } from "../data/repositories";
import { SocialError } from "./errors";
import { SupabaseAuthService, type StoredSessionReader, type SupabaseAuthOptions } from "./supabaseAuth";
import { SupabaseEventPublisher, SupabaseFeedService, SupabaseFriendsService } from "./supabaseFriends";
import type { AccountService, AuthService, SocialServices } from "./types";
import { WebPushService, type PushEnvironment } from "./webPush";

export interface AsyncStorage {
  getItem(key: string): Promise<string | null>;
  setItem(key: string, value: string): Promise<void>;
  removeItem(key: string): Promise<void>;
}

export interface SocialDependencies {
  readonly supabase: SupabaseConfig;
  readonly webAppUrl: string;
  readonly vapidPublicKey: string | null;
  /** Sitzungsspeicher (IndexedDB). */
  readonly storage: AsyncStorage;
  readonly auth: SupabaseAuthOptions;
  readonly pushEnvironment: PushEnvironment;
  /** Für Tests: eigenes `fetch`. */
  readonly fetch?: typeof fetch;
  readonly onNotifyProblem?: (message: string) => void;
}


/** Liest die gespeicherte Sitzung (JSON von supabase-js) ohne sie zu erneuern. */
export function storedSessionReader(storage: AsyncStorage): StoredSessionReader {
  return async () => {
    const text = await storage.getItem(AUTH_STORAGE_KEY);
    if (!text) return null;
    try {
      const value: unknown = JSON.parse(text);
      return typeof value === "object" && value !== null ? (value as { user?: User }) : null;
    } catch {
      return null;
    }
  };
}

export function createSupabaseClient(dependencies: Pick<SocialDependencies, "supabase" | "storage" | "fetch">): SupabaseClient {
  return createClient(dependencies.supabase.url, dependencies.supabase.anonKey, {
    auth: {
      flowType: "pkce",
      // Der Code wird gezielt auf /auth/callback getauscht (siehe AuthCallbackScreen), nicht auf jeder Seite.
      detectSessionInUrl: false,
      persistSession: true,
      autoRefreshToken: true,
      storage: dependencies.storage,
      storageKey: AUTH_STORAGE_KEY,
    },
    global: dependencies.fetch ? { fetch: dependencies.fetch } : {},
  });
}

/** Konto löschen: Edge Function `delete-account`, danach lokal abmelden (lokale Historie bleibt). */
export class SupabaseAccountService implements AccountService {
  constructor(
    private readonly client: SupabaseClient,
    private readonly auth: AuthService,
  ) {}

  async deleteAccount(): Promise<void> {
    if (!this.auth.currentUser()) throw new SocialError("not_authenticated");
    const { error } = await this.client.functions.invoke("delete-account", { body: {} });
    if (error) {
      const status = (error as { context?: unknown }).context instanceof Response ? ((error as { context: Response }).context.status) : 0;
      throw new SocialError(status === 401 ? "not_authenticated" : "network", error);
    }
    await this.auth.signOut("local");
  }
}

/** Kompositionswurzel der Vernetzung (wird erst bei Bedarf geladen, damit der Start schnell bleibt). */
export function createSocialServices(dependencies: SocialDependencies): SocialServices {
  const client = createSupabaseClient(dependencies);
  const auth = new SupabaseAuthService(client, dependencies.auth, storedSessionReader(dependencies.storage));
  const currentUserId = () => auth.currentUser()?.id ?? null;
  return {
    auth,
    friends: new SupabaseFriendsService(client, currentUserId, dependencies.webAppUrl),
    feed: new SupabaseFeedService(client, currentUserId),
    publisher: new SupabaseEventPublisher(client, dependencies.onNotifyProblem),
    push: new WebPushService(client, dependencies.pushEnvironment, dependencies.vapidPublicKey, () => currentUserId() !== null),
    account: new SupabaseAccountService(client, auth),
  };
}
