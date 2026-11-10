/** Test-Attrappen für die Ports der Edge Functions (keine Netzwerkzugriffe in Tests). */

import type { Clock } from "../../_shared/clock.ts";
import type { Logger } from "../../_shared/logger.ts";
import type { AccessTokenVerifier, TokenCheck, UserAdmin } from "../../_shared/supabase_api.ts";
import type { PushOptions, PushResult, PushSender, PushTarget } from "../../_shared/webpush/sender.ts";
import type {
  ClaimedDrinkEvent,
  DrinkEventClaimer,
  FriendshipReader,
  ProfileReader,
  RateLimiter,
  StoredSubscription,
  SubscriptionStore,
} from "../../notify-friends/ports.ts";

export const ANNA = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
export const BEN = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
export const CLARA = "cccccccc-cccc-4ccc-8ccc-cccccccccccc";
export const DORA = "dddddddd-dddd-4ddd-8ddd-dddddddddddd";
export const EVENT_ID = "11111111-2222-4333-8444-555555555555";

/** Feste Uhr. */
export class FixedClock implements Clock {
  constructor(public current: Date = new Date("2026-11-10T12:00:00Z")) {}
  now(): Date {
    return new Date(this.current.getTime());
  }
}

/** Protokoll, das Einträge sammelt. */
export class RecordingLogger implements Logger {
  readonly entries: Array<{ level: string; message: string; data?: Record<string, unknown> }> = [];
  info(message: string, data?: Record<string, unknown>) {
    this.entries.push({ level: "info", message, data });
  }
  warn(message: string, data?: Record<string, unknown>) {
    this.entries.push({ level: "warn", message, data });
  }
  error(message: string, data?: Record<string, unknown>) {
    this.entries.push({ level: "error", message, data });
  }
}

/** Gespeichertes Ereignis in der Attrappen-Datenbank. */
export interface FakeEvent extends ClaimedDrinkEvent {
  createdAt: Date;
  notifiedAt: Date | null;
}

/**
 * In-Memory-Nachbau der benötigten Tabellen. Verhält sich wie die PostgREST-Adapter, inklusive der
 * 10-Minuten- und notified_at-Bedingung beim Beanspruchen.
 */
export class FakeDatabase implements DrinkEventClaimer, FriendshipReader, ProfileReader, SubscriptionStore {
  readonly events = new Map<string, FakeEvent>();
  readonly friendships: Array<[string, string]> = [];
  readonly profiles = new Map<string, string>();
  subscriptions: StoredSubscription[] = [];
  readonly removed: string[] = [];
  readonly requestedSubscriptionUsers: string[][] = [];

  constructor(private readonly clock: Clock) {}

  claimForNotification(eventId: string, authorId: string, createdAfter: Date): Promise<ClaimedDrinkEvent | null> {
    const event = this.events.get(eventId);
    if (!event || event.authorId !== authorId || event.notifiedAt !== null || event.createdAt <= createdAfter) {
      return Promise.resolve(null);
    }
    event.notifiedAt = this.clock.now();
    return Promise.resolve({ id: event.id, authorId: event.authorId, name: event.name, volumeMl: event.volumeMl });
  }

  friendIdsOf(userId: string): Promise<string[]> {
    return Promise.resolve(
      this.friendships
        .filter(([a, b]) => a === userId || b === userId)
        .map(([a, b]) => (a === userId ? b : a)),
    );
  }

  displayNameOf(userId: string): Promise<string | null> {
    return Promise.resolve(this.profiles.get(userId) ?? null);
  }

  subscriptionsOf(userIds: readonly string[]): Promise<StoredSubscription[]> {
    this.requestedSubscriptionUsers.push([...userIds]);
    return Promise.resolve(this.subscriptions.filter((s) => userIds.includes(s.userId)));
  }

  remove(subscriptionId: string): Promise<void> {
    this.removed.push(subscriptionId);
    this.subscriptions = this.subscriptions.filter((s) => s.id !== subscriptionId);
    return Promise.resolve();
  }
}

/** Aufruflimit im Speicher (festes Fenster wie public.consume_rate_limit). */
export class FakeRateLimiter implements RateLimiter {
  readonly counts = new Map<string, number>();
  readonly calls: Array<{ userId: string; action: string; maxCount: number; windowSeconds: number }> = [];

  consume(userId: string, action: string, maxCount: number, windowSeconds: number): Promise<boolean> {
    this.calls.push({ userId, action, maxCount, windowSeconds });
    const key = `${userId}:${action}`;
    const count = (this.counts.get(key) ?? 0) + 1;
    this.counts.set(key, count);
    return Promise.resolve(count <= maxCount);
  }
}

/** Push-Versand, der Nachrichten mitschreibt und je Endpoint ein Ergebnis liefert. */
export class FakePushSender implements PushSender {
  readonly sent: Array<{ target: PushTarget; payload: unknown; options: PushOptions }> = [];
  readonly results = new Map<string, PushResult | Error>();

  send(target: PushTarget, payload: Uint8Array, options: PushOptions): Promise<PushResult> {
    this.sent.push({ target, payload: JSON.parse(new TextDecoder().decode(payload)), options });
    const result = this.results.get(target.endpoint) ?? { kind: "delivered", status: 201 };
    return result instanceof Error ? Promise.reject(result) : Promise.resolve(result);
  }
}

/** Token-Prüfung: Token → Ergebnis. */
export class FakeVerifier implements AccessTokenVerifier {
  readonly tokens = new Map<string, TokenCheck>();
  verify(accessToken: string): Promise<TokenCheck> {
    return Promise.resolve(this.tokens.get(accessToken) ?? { kind: "invalid" });
  }
}

/** Admin-API-Attrappe. */
export class FakeUserAdmin implements UserAdmin {
  readonly users = new Set<string>();
  readonly deleted: string[] = [];
  failWith?: Error;

  deleteUser(userId: string): Promise<"deleted" | "not_found"> {
    if (this.failWith) return Promise.reject(this.failWith);
    this.deleted.push(userId);
    return Promise.resolve(this.users.delete(userId) ? "deleted" : "not_found");
  }
}

/** Aufgezeichnete HTTP-Anfrage der fetch-Attrappe. */
export interface RecordedRequest {
  url: string;
  method: string;
  headers: Headers;
  body: string | Uint8Array | null;
  redirect?: RequestRedirect;
}

/** fetch-Attrappe: zeichnet Anfragen auf und beantwortet sie über eine Funktion. */
export function fakeFetch(
  respond: (request: RecordedRequest) => Response | Promise<Response>,
): { fetch: typeof fetch; requests: RecordedRequest[] } {
  const requests: RecordedRequest[] = [];
  const fetchFn = (async (input: string | URL | Request, init?: RequestInit) => {
    const url = typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url;
    let body: string | Uint8Array | null = null;
    if (typeof init?.body === "string") body = init.body;
    else if (init?.body instanceof Uint8Array) body = init.body;
    const request: RecordedRequest = {
      url,
      method: init?.method ?? "GET",
      headers: new Headers(init?.headers),
      body,
      redirect: init?.redirect,
    };
    requests.push(request);
    return await respond(request);
  }) as typeof fetch;
  return { fetch: fetchFn, requests };
}

/** JSON-Antwort für die fetch-Attrappe. */
export function json(status: number, body: unknown): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
