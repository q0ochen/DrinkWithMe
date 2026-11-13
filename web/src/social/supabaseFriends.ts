import { isAuthRetryableFetchError, type SupabaseClient } from "@supabase/supabase-js";
import { drinkCategoryFromName } from "../core/bar/drinkCategory";
import { drinkIconFromKey } from "../core/bar/drinkIcon";
import type { DrinkEntry } from "../core/log/drinkEntry";
import { toSharedDrink } from "../core/sharing/sharedDrink";
import { inviteWebLink } from "../core/social/inviteLinks";
import {
  FEED_DEFAULT_LIMIT,
  type EventPublisher,
  type FeedService,
  type Friend,
  type FriendDrinkEvent,
  type FriendRequest,
  type FriendsService,
  type UserProfile,
} from "../core/social/ports";
import { fromPostgrestError, SocialError } from "./errors";
import type { OutgoingFriendRequest, OutgoingRequestsService } from "./types";

interface ProfileRow {
  id: string;
  display_name: string;
  avatar_url: string | null;
}

interface EventRow {
  id: string;
  author_id: string;
  name: string;
  icon: string;
  category: string;
  volume_ml: number;
  consumed_at: string;
}

const EVENT_COLUMNS = "id,author_id,name,icon,category,volume_ml,consumed_at";
const UNKNOWN_NAME = "Jemand";

type Result<T> = { data: T | null; error: { message?: string; code?: string } | null; status?: number };

function unwrap<T>(result: Result<T>): T {
  if (result.error) throw fromPostgrestError(result.error, result.status);
  return result.data as T;
}

function toProfile(row: ProfileRow): UserProfile {
  return { id: row.id, displayName: row.display_name, avatarUrl: row.avatar_url };
}

/** Gemeinsame Hilfen: angemeldete ID und Profile per ID (RLS liefert nur erlaubte Profile). */
class SupabaseAccess {
  constructor(
    readonly client: SupabaseClient,
    private readonly currentUserId: () => string | null,
  ) {}

  me(): string {
    const id = this.currentUserId();
    if (!id) throw new SocialError("not_authenticated");
    return id;
  }

  async profiles(ids: readonly string[]): Promise<Map<string, UserProfile>> {
    const unique = [...new Set(ids)];
    if (unique.length === 0) return new Map();
    const rows = unwrap<ProfileRow[]>(await this.client.from("profiles").select("id,display_name,avatar_url").in("id", unique));
    return new Map(rows.map((row) => [row.id, toProfile(row)]));
  }
}

function placeholder(id: string): UserProfile {
  return { id, displayName: UNKNOWN_NAME, avatarUrl: null };
}

const byName = (a: UserProfile, b: UserProfile) => a.displayName.localeCompare(b.displayName, "de");

/**
 * {@link FriendsService} über PostgREST-Tabellen und die RPCs aus supabase/README.md. Ausgehende Anfragen gibt es
 * nur ohne Empfänger (RPC `list_outgoing_friend_requests`), damit niemand per E-Mail-Adresse Konten ausforschen kann.
 */
export class SupabaseFriendsService implements FriendsService, OutgoingRequestsService {
  private readonly access: SupabaseAccess;

  constructor(
    client: SupabaseClient,
    currentUserId: () => string | null,
    private readonly webAppUrl: string,
  ) {
    this.access = new SupabaseAccess(client, currentUserId);
  }

  private get client(): SupabaseClient {
    return this.access.client;
  }

  async friends(): Promise<Friend[]> {
    const me = this.access.me();
    const rows = unwrap<Array<{ user_a: string; user_b: string; created_at: string }>>(
      await this.client.from("friendships").select("user_a,user_b,created_at"),
    );
    const others = rows.map((row) => ({ id: row.user_a === me ? row.user_b : row.user_a, since: Date.parse(row.created_at) }));
    const profiles = await this.access.profiles(others.map((other) => other.id));
    return others
      .map((other) => ({ profile: profiles.get(other.id) ?? placeholder(other.id), since: other.since }))
      .sort((a, b) => byName(a.profile, b.profile));
  }

  /** Eingehende Anfragen (nur Empfänger dürfen die Tabelle lesen) mit dem Profil des Absenders. */
  async requests(): Promise<FriendRequest[]> {
    const me = this.access.me();
    const rows = unwrap<Array<{ id: string; from_user: string; created_at: string }>>(
      await this.client.from("friend_requests").select("id,from_user,created_at").eq("to_user", me).order("created_at", { ascending: false }),
    );
    const profiles = await this.access.profiles(rows.map((row) => row.from_user));
    return rows.map((row) => ({
      id: row.id,
      other: profiles.get(row.from_user) ?? placeholder(row.from_user),
      direction: "INCOMING",
      createdAt: Date.parse(row.created_at),
    }));
  }

  /** Eigene offene Anfragen: nur ID, maskierte Adresse und Zeitpunkt – nie, ob bzw. wen sie erreichen. */
  async outgoingRequests(): Promise<OutgoingFriendRequest[]> {
    this.access.me();
    const rows = unwrap<unknown[] | null>(await this.client.rpc("list_outgoing_friend_requests", {})) ?? [];
    return rows.flatMap((row): OutgoingFriendRequest[] => {
      const { id, email_hint: emailHint, created_at: createdAt } = (row ?? {}) as Record<string, unknown>;
      if (typeof id !== "string" || typeof emailHint !== "string" || typeof createdAt !== "string") return [];
      const at = Date.parse(createdAt);
      return Number.isFinite(at) ? [{ id, emailHint, createdAt: at }] : [];
    });
  }

  async sendRequestByEmail(email: string): Promise<void> {
    this.access.me();
    unwrap(await this.client.rpc("send_friend_request", { email: email.trim() }));
  }

  async respond(requestId: string, accept: boolean): Promise<void> {
    unwrap(await this.client.rpc("respond_friend_request", { request_id: requestId, accept }));
  }

  /** Eigene Anfrage zurückziehen (idempotent; fremde oder erledigte Anfragen ignoriert der Server). */
  async cancel(requestId: string): Promise<void> {
    unwrap(await this.client.rpc("cancel_friend_request", { request_id: requestId }));
  }

  async createInviteLink(): Promise<string> {
    this.access.me();
    const token = unwrap<string>(await this.client.rpc("create_invite_link"));
    return inviteWebLink(this.webAppUrl, token);
  }

  async acceptInvite(token: string): Promise<UserProfile> {
    this.access.me();
    const row = unwrap<ProfileRow | ProfileRow[]>(await this.client.rpc("accept_invite", { token }));
    const profile = Array.isArray(row) ? row[0] : row;
    if (!profile) throw new SocialError("invite_invalid");
    return toProfile(profile);
  }

  async removeFriend(userId: string): Promise<void> {
    unwrap(await this.client.rpc("remove_friend", { friend_id: userId }));
  }
}

/** {@link FeedService}: geteilte Getränke der Freunde (RLS: nur eigene und die der Freunde). */
export class SupabaseFeedService implements FeedService {
  private readonly access: SupabaseAccess;

  constructor(client: SupabaseClient, currentUserId: () => string | null) {
    this.access = new SupabaseAccess(client, currentUserId);
  }

  async recentEvents(since: number | null, limit: number = FEED_DEFAULT_LIMIT): Promise<FriendDrinkEvent[]> {
    const me = this.access.me();
    let query = this.access.client
      .from("drink_events")
      .select(EVENT_COLUMNS)
      .neq("author_id", me)
      .order("consumed_at", { ascending: false })
      .limit(limit);
    if (since !== null) query = query.gt("consumed_at", new Date(since).toISOString());
    const rows = unwrap<EventRow[]>(await query);
    return this.withAuthors(rows);
  }

  async event(id: string): Promise<FriendDrinkEvent | null> {
    this.access.me();
    const row = unwrap<EventRow | null>(await this.access.client.from("drink_events").select(EVENT_COLUMNS).eq("id", id).maybeSingle());
    if (!row) return null;
    return (await this.withAuthors([row]))[0] ?? null;
  }

  private async withAuthors(rows: readonly EventRow[]): Promise<FriendDrinkEvent[]> {
    const profiles = await this.access.profiles(rows.map((row) => row.author_id));
    return rows.map((row) => ({
      id: row.id,
      author: profiles.get(row.author_id) ?? placeholder(row.author_id),
      name: row.name,
      icon: drinkIconFromKey(row.icon),
      category: drinkCategoryFromName(row.category),
      volumeMilliliters: row.volume_ml,
      consumedAt: Date.parse(row.consumed_at),
    }));
  }
}

/** Antwort der Edge Function `notify-friends`, falls sie mit einem Fehlercode antwortet. */
async function functionStatus(error: unknown): Promise<number | null> {
  const context = (error as { context?: unknown } | null)?.context;
  return context instanceof Response ? context.status : null;
}

/**
 * {@link EventPublisher} wie in der Android-App: `drink_events` nur mit `id, name, icon, category, volume_ml,
 * consumed_at` anlegen (`Prefer: resolution=ignore-duplicates`, wiederholbar), danach `notify-friends`.
 */
export class SupabaseEventPublisher implements EventPublisher {
  constructor(
    private readonly client: SupabaseClient,
    private readonly onNotifyProblem: (message: string) => void = () => {},
  ) {}

  async publish(entry: DrinkEntry): Promise<void> {
    await this.requireSession();
    const shared = toSharedDrink(entry);
    const row = {
      id: shared.id,
      name: shared.name,
      icon: shared.icon,
      category: shared.category,
      volume_ml: shared.volumeMilliliters,
      consumed_at: new Date(shared.consumedAt).toISOString(),
    };
    unwrap(await this.client.from("drink_events").upsert(row, { onConflict: "id", ignoreDuplicates: true }));
    const { error } = await this.client.functions.invoke("notify-friends", { body: { eventId: shared.id } });
    if (!error) return;
    const status = await functionStatus(error);
    // 404: schon benachrichtigt oder älter als 10 min (z. B. erneutes Senden) – kein Fehler.
    if (status === 404) return;
    if (status === 429) {
      this.onNotifyProblem("rate_limited");
      return;
    }
    throw new SocialError(status === 401 ? "not_authenticated" : "network", error);
  }

  async unpublish(entryId: string): Promise<void> {
    await this.requireSession();
    unwrap(await this.client.from("drink_events").delete().eq("id", entryId));
  }

  /**
   * Ohne gültige Sitzung würde supabase-js mit dem Anon-Key senden (und der Server lehnt ab). Scheitert nur das
   * Erneuern (offline, 5xx), ist das ein Netzfehler – der Auftrag wird dann später nachgeholt.
   */
  private async requireSession(): Promise<void> {
    const { data, error } = await this.client.auth.getSession();
    if (data.session) return;
    throw new SocialError(error && isAuthRetryableFetchError(error) ? "network" : "not_authenticated", error);
  }
}
