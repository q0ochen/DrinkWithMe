import type { DrinkEntry, HistoryRepository } from "../log/drinkEntry";
import type { EventPublisher } from "../social/ports";
import type { SharingPolicy } from "./sharingPolicy";

/**
 * Decorator um die lokale Historie (Port von `SharingHistoryRepository`): speichert und löscht **zuerst lokal**
 * und teilt danach nach der {@link SharingPolicy} im Hintergrund mit Freunden.
 *
 * - Lokales Speichern wartet nie auf das Netz; Netzwerkfehler gehen an `onError` und blockieren nichts.
 * - Netzwerkaufträge laufen nacheinander in Aufrufreihenfolge (ein schnelles „Rückgängig“ zieht sicher zurück).
 * - Zurückgezogen wird nur, was geteilt war.
 */
export class SharingHistoryRepository implements HistoryRepository {
  private tail: Promise<void> = Promise.resolve();

  constructor(
    private readonly local: HistoryRepository,
    private readonly publisher: EventPublisher,
    private readonly policy: SharingPolicy,
    private readonly onError: (error: unknown) => void = () => {},
  ) {}

  entries(from: number, to: number): Promise<DrinkEntry[]> {
    return this.local.entries(from, to);
  }

  entry(id: string): Promise<DrinkEntry | null> {
    return this.local.entry(id);
  }

  async save(entry: DrinkEntry): Promise<void> {
    const publish = this.policy.shouldPublish(entry);
    const wasShared = !publish && (await this.local.entry(entry.id))?.shareWithFriends === true;
    await this.local.save(entry);
    if (publish) this.enqueue(() => this.publisher.publish(entry));
    else if (wasShared && this.policy.canUnpublish()) this.enqueue(() => this.publisher.unpublish(entry.id));
  }

  async delete(id: string): Promise<void> {
    const wasShared = (await this.local.entry(id))?.shareWithFriends === true;
    await this.local.delete(id);
    if (wasShared && this.policy.canUnpublish()) this.enqueue(() => this.publisher.unpublish(id));
  }

  /** Wartet, bis alle bisher eingereihten Netzwerkaufträge erledigt sind (für Tests und vor dem Abmelden). */
  idle(): Promise<void> {
    return this.tail;
  }

  private enqueue(operation: () => Promise<void>): void {
    this.tail = this.tail.then(operation).catch((error: unknown) => this.report(error));
  }

  private report(error: unknown): void {
    try {
      this.onError(error);
    } catch {
      // Ein fehlerhafter Fehler-Callback darf das Teilen nachfolgender Einträge nicht stoppen.
    }
  }
}
