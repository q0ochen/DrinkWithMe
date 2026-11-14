import type { DrinkEntry, HistoryReader } from "../core/log/drinkEntry";
import type { SharingPolicy } from "../core/sharing/sharingPolicy";
import type { EventPublisher } from "../core/social/ports";
import type { Clock } from "../core/time/clock";
import type { StoredValue } from "../data/storedValue";
import { SocialError } from "../social/errors";

// Wie `publish/PendingShares.kt` in der Android-App: Teilen und Zurückziehen gehen ohne Verbindung nicht verloren,
// sondern werden vorgemerkt und später nachgeholt (beim App-Start, wenn der Browser wieder online ist, und wenn die
// App wieder sichtbar wird). Gespeichert wird nur die ID des Eintrags – der Inhalt kommt beim Nachholen aus der
// lokalen Historie.

/** Ein Teilen-Auftrag, der wegen fehlender Verbindung später nachgeholt wird. */
export interface PendingShare {
  readonly op: "publish" | "unpublish";
  readonly entryId: string;
}

const same = (a: PendingShare, b: PendingShare) => a.op === b.op && a.entryId === b.entryId;

function parse(value: unknown): PendingShare[] {
  if (!Array.isArray(value)) return [];
  return value.flatMap((item): PendingShare[] => {
    if (typeof item !== "object" || item === null) return [];
    const { op, entryId } = item as Record<string, unknown>;
    if ((op !== "publish" && op !== "unpublish") || typeof entryId !== "string" || entryId.length === 0) return [];
    return [{ op, entryId }];
  });
}

/**
 * Warteschlange der nachzuholenden Aufträge (in Aufrufreihenfolge, ohne Doppelte, höchstens `capacity`). Ein
 * „Zurückziehen“ macht ein noch wartendes „Teilen“ desselben Eintrags überflüssig. Änderungen laufen nacheinander,
 * damit gleichzeitige Aufträge sich nicht gegenseitig überschreiben.
 */
export class PendingShareQueue {
  private tail: Promise<unknown> = Promise.resolve();

  constructor(
    private readonly store: StoredValue,
    private readonly capacity = 200,
  ) {}

  add(share: PendingShare): Promise<void> {
    return this.update((current) => {
      const rest = current.filter(
        (item) => !same(item, share) && !(share.op === "unpublish" && item.op === "publish" && item.entryId === share.entryId),
      );
      return [...rest, share].slice(-this.capacity);
    });
  }

  remove(share: PendingShare): Promise<void> {
    return this.update((current) => current.filter((item) => !same(item, share)));
  }

  clear(): Promise<void> {
    return this.update(() => []);
  }

  snapshot(): Promise<PendingShare[]> {
    return this.serialized(async () => parse(await this.store.read()));
  }

  private update(change: (current: PendingShare[]) => PendingShare[]): Promise<void> {
    return this.serialized(async () => {
      const next = change(parse(await this.store.read()));
      await this.store.write(next.length === 0 ? null : next.map(({ op, entryId }) => ({ op, entryId })));
    });
  }

  private serialized<T>(operation: () => Promise<T>): Promise<T> {
    const result = this.tail.then(operation, operation);
    this.tail = result.catch(() => undefined);
    return result;
  }
}

/** Nur fehlende Verbindung (bzw. ein vorübergehend nicht erreichbarer Server) lohnt ein späteres Nachholen. */
export function isRetryableShareError(error: unknown): boolean {
  return error instanceof SocialError && error.code === "network";
}

/**
 * Decorator um den echten {@link EventPublisher}: Ohne Verbindung wird der Auftrag vorgemerkt, statt verloren zu
 * gehen. Andere Fehler (z. B. abgelehnt, `too_many_events`) gehen unverändert weiter.
 */
export class RetryingEventPublisher implements EventPublisher {
  constructor(
    private readonly delegate: EventPublisher,
    private readonly queue: PendingShareQueue,
    private readonly onQueued: () => void = () => {},
  ) {}

  publish(entry: DrinkEntry): Promise<void> {
    return this.attempt({ op: "publish", entryId: entry.id }, () => this.delegate.publish(entry));
  }

  unpublish(entryId: string): Promise<void> {
    return this.attempt({ op: "unpublish", entryId }, () => this.delegate.unpublish(entryId));
  }

  private async attempt(share: PendingShare, operation: () => Promise<void>): Promise<void> {
    try {
      await operation();
    } catch (error) {
      if (!isRetryableShareError(error)) throw error;
      await this.queue.add(share);
      this.onQueued();
    }
  }
}

export type RetryOutcome = "done" | "retryLater";

/** Der Server nimmt nur Zeitpunkte der letzten 24 Stunden an; mit Abstand davor wird nicht mehr nachgeholt. */
export const PENDING_SHARE_MAX_AGE_MILLIS = 23 * 60 * 60 * 1000;

/**
 * Holt vorgemerkte Aufträge nach: „Teilen“ nur, wenn der Eintrag noch existiert, laut `policy` noch geteilt werden
 * soll und jünger als `maxAge` ist; „Zurückziehen“ nur angemeldet. Beim ersten Netzfehler wird abgebrochen (der Rest
 * bleibt vorgemerkt); vom Server abgelehnte Aufträge entfallen.
 */
export class ShareRetryProcessor {
  constructor(
    private readonly queue: PendingShareQueue,
    private readonly history: HistoryReader,
    private readonly policy: SharingPolicy,
    /** Der echte (nicht wiederholende) Publisher; `null`, wenn die Vernetzung gerade nicht lädt. */
    private readonly publisher: () => Promise<EventPublisher | null>,
    private readonly clock: Clock,
    private readonly maxAge = PENDING_SHARE_MAX_AGE_MILLIS,
  ) {}

  async run(): Promise<RetryOutcome> {
    for (const share of await this.queue.snapshot()) {
      try {
        if (share.op === "publish") {
          const entry = await this.history.entry(share.entryId);
          if (entry && this.policy.shouldPublish(entry) && this.clock.now() - entry.consumedAt < this.maxAge) {
            await (await this.requirePublisher()).publish(entry);
          }
        } else if (this.policy.canUnpublish()) {
          await (await this.requirePublisher()).unpublish(share.entryId);
        }
        await this.queue.remove(share);
      } catch (error) {
        if (isRetryableShareError(error)) return "retryLater";
        // Abgelehnt oder abgemeldet: Nachholen würde nicht helfen.
        await this.queue.remove(share);
      }
    }
    return "done";
  }

  private async requirePublisher(): Promise<EventPublisher> {
    const publisher = await this.publisher();
    if (!publisher) throw new SocialError("network");
    return publisher;
  }
}
