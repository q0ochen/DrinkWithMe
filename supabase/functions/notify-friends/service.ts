/**
 * Anwendungsfall „Freunde benachrichtigen“ (docs/ARCHITEKTUR.md, Abschnitt 2.3):
 *
 * 1. Limit: höchstens 60 Aufrufe pro Nutzer und Stunde.
 * 2. Ereignis laden und `notified_at` setzen – nur eigenes Ereignis, jünger als 10 Minuten, noch nicht
 *    benachrichtigt (atomar, damit parallele Aufrufe nicht doppelt senden).
 * 3. Freunde und deren Push-Abos laden.
 * 4. Jedem Abo eine verschlüsselte WebPush-Nachricht senden (TTL 1 Tag); 404/410 → Abo löschen.
 */

import type { Clock } from "../_shared/clock.ts";
import { describeError, type Logger } from "../_shared/logger.ts";
import { utf8 } from "../_shared/bytes.ts";
import type { PushSender } from "../_shared/webpush/sender.ts";
import { buildDrinkPushPayload, UNKNOWN_AUTHOR_NAME } from "./messages.ts";
import type {
  DrinkEventClaimer,
  FriendshipReader,
  ProfileReader,
  RateLimiter,
  StoredSubscription,
  SubscriptionStore,
} from "./ports.ts";

/** Limit laut Vertrag: 60 Aufrufe pro Nutzer und Stunde. */
export const NOTIFY_RATE_LIMIT = { action: "notify_friends", maxCount: 60, windowSeconds: 3600 } as const;

/** Nur Ereignisse, die vor weniger als 10 Minuten angelegt wurden, lösen Benachrichtigungen aus. */
export const NOTIFY_MAX_EVENT_AGE_MS = 10 * 60 * 1000;

/** Push-Dienste bewahren die Nachricht höchstens einen Tag auf. */
export const PUSH_TTL_SECONDS = 24 * 60 * 60;

/** Wie viele Push-Nachrichten gleichzeitig unterwegs sein dürfen. */
export const PUSH_CONCURRENCY = 8;

/** Ergebnis eines Aufrufs. */
export type NotifyOutcome =
  | { kind: "rate_limited" }
  | { kind: "event_not_found" }
  | { kind: "sent"; recipients: number; delivered: number; failed: number; removed: number };

/** Port des Anwendungsfalls (für den HTTP-Handler). */
export interface NotifyFriendsUseCase {
  notify(callerId: string, eventId: string): Promise<NotifyOutcome>;
}

/** Alle Abhängigkeiten werden injiziert (Dependency Inversion); siehe index.ts für die echte Verdrahtung. */
export interface NotifyFriendsDeps {
  rateLimiter: RateLimiter;
  events: DrinkEventClaimer;
  friendships: FriendshipReader;
  profiles: ProfileReader;
  subscriptions: SubscriptionStore;
  push: PushSender;
  clock: Clock;
  logger: Logger;
}

export class NotifyFriendsService implements NotifyFriendsUseCase {
  constructor(private readonly deps: NotifyFriendsDeps) {}

  async notify(callerId: string, eventId: string): Promise<NotifyOutcome> {
    const { action, maxCount, windowSeconds } = NOTIFY_RATE_LIMIT;
    if (!(await this.deps.rateLimiter.consume(callerId, action, maxCount, windowSeconds))) {
      return { kind: "rate_limited" };
    }

    const createdAfter = new Date(this.deps.clock.now().getTime() - NOTIFY_MAX_EVENT_AGE_MS);
    const event = await this.deps.events.claimForNotification(eventId, callerId, createdAfter);
    if (!event || event.authorId !== callerId) {
      return { kind: "event_not_found" };
    }

    // Nur Freunde – nie der Autor selbst, auch wenn ein Adapter ihn fälschlich liefern sollte.
    const friendIds = new Set((await this.deps.friendships.friendIdsOf(callerId)).filter((id) => id !== callerId));
    if (friendIds.size === 0) {
      return { kind: "sent", recipients: 0, delivered: 0, failed: 0, removed: 0 };
    }
    const subscriptions = (await this.deps.subscriptions.subscriptionsOf([...friendIds]))
      .filter((subscription) => friendIds.has(subscription.userId));
    const authorName = (await this.deps.profiles.displayNameOf(callerId)) ?? UNKNOWN_AUTHOR_NAME;

    const results = await mapWithConcurrency(
      subscriptions,
      PUSH_CONCURRENCY,
      (subscription) => this.#deliver(subscription, event.id, callerId, authorName, event.name, event.volumeMl),
    );

    const outcome = {
      kind: "sent" as const,
      recipients: subscriptions.length,
      delivered: results.filter((r) => r === "delivered").length,
      failed: results.filter((r) => r === "failed").length,
      removed: results.filter((r) => r === "removed").length,
    };
    this.deps.logger.info("Benachrichtigungen verschickt", { eventId: event.id, ...outcome });
    return outcome;
  }

  async #deliver(
    subscription: StoredSubscription,
    eventId: string,
    authorId: string,
    authorName: string,
    drinkName: string,
    volumeMl: number,
  ): Promise<"delivered" | "failed" | "removed"> {
    const payload = buildDrinkPushPayload({
      eventId,
      authorId,
      authorName,
      recipientId: subscription.userId,
      drinkName,
      volumeMl,
    });
    try {
      const result = await this.deps.push.send(subscription, utf8(JSON.stringify(payload)), {
        ttlSeconds: PUSH_TTL_SECONDS,
        urgency: "normal",
      });
      if (result.kind === "delivered") return "delivered";
      if (result.kind === "gone" || result.kind === "invalid") {
        await this.deps.subscriptions.remove(subscription.id);
        this.deps.logger.info("Push-Abo entfernt", {
          subscriptionId: subscription.id,
          grund: result.kind === "gone" ? `http_${result.status}` : result.reason,
        });
        return "removed";
      }
      this.deps.logger.warn("Push-Versand fehlgeschlagen", {
        subscriptionId: subscription.id,
        endpointHost: safeHost(subscription.endpoint),
        grund: result.reason,
      });
      return "failed";
    } catch (error) {
      this.deps.logger.error("Push-Versand abgebrochen", {
        subscriptionId: subscription.id,
        fehler: describeError(error),
      });
      return "failed";
    }
  }
}

/** Führt `task` für alle Elemente aus, höchstens `limit` gleichzeitig; Reihenfolge der Ergebnisse bleibt. */
export async function mapWithConcurrency<T, R>(
  items: readonly T[],
  limit: number,
  task: (item: T) => Promise<R>,
): Promise<R[]> {
  const results = new Array<R>(items.length);
  let next = 0;
  const worker = async () => {
    while (next < items.length) {
      const index = next++;
      results[index] = await task(items[index]);
    }
  };
  await Promise.all(Array.from({ length: Math.min(Math.max(limit, 1), items.length) }, worker));
  return results;
}

/** Nur der Hostname eines Endpoints (der vollständige Endpoint ist ein Geheimnis). */
function safeHost(endpoint: string): string {
  try {
    return new URL(endpoint).hostname;
  } catch {
    return "ungültig";
  }
}
