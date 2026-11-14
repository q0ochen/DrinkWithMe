/**
 * Umsetzung der Ports aus ports.ts über PostgREST mit der Service-Rolle (umgeht RLS). Jede Abfrage ist daher
 * ausdrücklich auf den aufrufenden Nutzer bzw. seine Freunde eingeschränkt.
 */

import type { Clock } from "../_shared/clock.ts";
import { isUuid } from "../_shared/http.ts";
import { type PostgrestClient, uuidList } from "../_shared/supabase_api.ts";
import type {
  ClaimedDrinkEvent,
  DrinkEventClaimer,
  FriendshipReader,
  ProfileReader,
  RateLimiter,
  StoredSubscription,
  SubscriptionStore,
} from "./ports.ts";

function requireUuid(value: string): string {
  if (!isUuid(value)) throw new TypeError("Keine UUID");
  return value;
}

/** drink_events: setzt notified_at mit einem einzigen, bedingten UPDATE (keine Doppel-Benachrichtigung). */
export class PostgrestDrinkEventClaimer implements DrinkEventClaimer {
  constructor(private readonly db: PostgrestClient, private readonly clock: Clock) {}

  async claimForNotification(
    eventId: string,
    authorId: string,
    createdAfter: Date,
  ): Promise<ClaimedDrinkEvent | null> {
    const filter = [
      `id=eq.${requireUuid(eventId)}`,
      `author_id=eq.${requireUuid(authorId)}`,
      "notified_at=is.null",
      `created_at=gt.${encodeURIComponent(createdAfter.toISOString())}`,
      "select=id,author_id,name,volume_ml",
    ].join("&");
    const rows = await this.db.request<
      Array<{ id: string; author_id: string; name: string; volume_ml: number }>
    >("PATCH", `drink_events?${filter}`, {
      body: { notified_at: this.clock.now().toISOString() },
      prefer: "return=representation",
    });
    const row = rows?.[0];
    return row ? { id: row.id, authorId: row.author_id, name: row.name, volumeMl: row.volume_ml } : null;
  }
}

/** friendships: Freunde eines Nutzers (er kann user_a oder user_b sein). */
export class PostgrestFriendshipReader implements FriendshipReader {
  constructor(private readonly db: PostgrestClient) {}

  async friendIdsOf(userId: string): Promise<string[]> {
    const id = requireUuid(userId);
    const rows = await this.db.request<Array<{ user_a: string; user_b: string }>>(
      "GET",
      `friendships?or=(user_a.eq.${id},user_b.eq.${id})&select=user_a,user_b`,
    );
    return (rows ?? [])
      .filter((row) => row.user_a === id || row.user_b === id)
      .map((row) => (row.user_a === id ? row.user_b : row.user_a));
  }
}

/** profiles: Anzeigename. */
export class PostgrestProfileReader implements ProfileReader {
  constructor(private readonly db: PostgrestClient) {}

  async displayNameOf(userId: string): Promise<string | null> {
    const rows = await this.db.request<Array<{ display_name: string }>>(
      "GET",
      `profiles?id=eq.${requireUuid(userId)}&select=display_name`,
    );
    return rows?.[0]?.display_name ?? null;
  }
}

/** push_subscriptions: lesen in Blöcken (kurze URLs), löschen einzeln. */
export class PostgrestSubscriptionStore implements SubscriptionStore {
  static readonly CHUNK_SIZE = 100;

  constructor(private readonly db: PostgrestClient) {}

  async subscriptionsOf(userIds: readonly string[]): Promise<StoredSubscription[]> {
    const result: StoredSubscription[] = [];
    for (let i = 0; i < userIds.length; i += PostgrestSubscriptionStore.CHUNK_SIZE) {
      const chunk = userIds.slice(i, i + PostgrestSubscriptionStore.CHUNK_SIZE);
      const rows = await this.db.request<
        Array<{ id: string; user_id: string; endpoint: string; p256dh: string; auth: string }>
      >("GET", `push_subscriptions?user_id=in.${uuidList(chunk)}&select=id,user_id,endpoint,p256dh,auth`);
      for (const row of rows ?? []) {
        result.push({
          id: row.id,
          userId: row.user_id,
          endpoint: row.endpoint,
          p256dh: row.p256dh,
          auth: row.auth,
        });
      }
    }
    return result;
  }

  async remove(subscriptionId: string): Promise<void> {
    await this.db.request<null>("DELETE", `push_subscriptions?id=eq.${requireUuid(subscriptionId)}`);
  }
}

/** rate_limits über die Datenbankfunktion public.consume_rate_limit (nur für die Service-Rolle freigegeben). */
export class PostgrestRateLimiter implements RateLimiter {
  constructor(private readonly db: PostgrestClient) {}

  async consume(userId: string, action: string, maxCount: number, windowSeconds: number): Promise<boolean> {
    const allowed = await this.db.rpc<boolean>("consume_rate_limit", {
      p_user_id: requireUuid(userId),
      p_action: action,
      p_max_count: maxCount,
      p_window_seconds: windowSeconds,
    });
    return allowed === true;
  }
}
