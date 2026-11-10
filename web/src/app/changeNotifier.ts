import type { BarItem } from "../core/bar/barItem";
import type { BarRepository } from "../core/bar/barRepository";
import type { DrinkEntry, HistoryRepository } from "../core/log/drinkEntry";

export type ChangeTopic = "bar" | "history";

/** Meldet Datenänderungen an die Oberfläche (damit z. B. der Verlauf nach dem Eintragen neu rechnet). */
export class ChangeNotifier {
  private readonly listeners = new Map<ChangeTopic, Set<() => void>>();

  subscribe(topic: ChangeTopic, listener: () => void): () => void {
    const set = this.listeners.get(topic) ?? new Set();
    set.add(listener);
    this.listeners.set(topic, set);
    return () => set.delete(listener);
  }

  notify(topic: ChangeTopic): void {
    for (const listener of this.listeners.get(topic) ?? []) listener();
  }
}

/** Decorator: meldet jede Änderung der Historie (Open/Closed – die Repositories bleiben unverändert). */
export class NotifyingHistoryRepository implements HistoryRepository {
  constructor(
    private readonly inner: HistoryRepository,
    private readonly changes: ChangeNotifier,
  ) {}

  entries(from: number, to: number): Promise<DrinkEntry[]> {
    return this.inner.entries(from, to);
  }

  entry(id: string): Promise<DrinkEntry | null> {
    return this.inner.entry(id);
  }

  async save(entry: DrinkEntry): Promise<void> {
    await this.inner.save(entry);
    this.changes.notify("history");
  }

  async delete(id: string): Promise<void> {
    await this.inner.delete(id);
    this.changes.notify("history");
  }
}

/** Decorator: meldet jede Änderung der Bar. */
export class NotifyingBarRepository implements BarRepository {
  constructor(
    private readonly inner: BarRepository,
    private readonly changes: ChangeNotifier,
  ) {}

  items(): Promise<BarItem[]> {
    return this.inner.items();
  }

  item(id: string): Promise<BarItem | null> {
    return this.inner.item(id);
  }

  async save(item: BarItem): Promise<void> {
    await this.inner.save(item);
    this.changes.notify("bar");
  }

  async delete(id: string): Promise<void> {
    await this.inner.delete(id);
    this.changes.notify("bar");
  }

  async reorder(ids: readonly string[]): Promise<void> {
    await this.inner.reorder(ids);
    this.changes.notify("bar");
  }
}
