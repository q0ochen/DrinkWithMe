import type { BarItem } from "./barItem";

/** Lesender Zugriff auf die eigene Bar (Interface Segregation). */
export interface BarReader {
  /** Alle Getränke der Bar, aufsteigend nach `sortOrder`. */
  items(): Promise<BarItem[]>;
  item(id: string): Promise<BarItem | null>;
}

/** Schreibender Zugriff auf die eigene Bar. */
export interface BarWriter {
  /** Legt an oder ersetzt das Getränk mit derselben ID. */
  save(item: BarItem): Promise<void>;
  /** Entfernt das Getränk; unbekannte IDs werden ignoriert. Die Historie bleibt unverändert (Snapshot). */
  delete(id: string): Promise<void>;
  /**
   * Neue Reihenfolge: Die genannten IDs erhalten `sortOrder` 0, 1, 2 …; nicht genannte Getränke folgen
   * dahinter in ihrer bisherigen Reihenfolge. Unbekannte IDs werden ignoriert.
   */
  reorder(ids: readonly string[]): Promise<void>;
}

/** Die eigene Bar – liegt nur auf dem Gerät. */
export interface BarRepository extends BarReader, BarWriter {}

/** Reihenfolge-Regel von `reorder` als reine Funktion (gemeinsam für alle Implementierungen). */
export function reorderItems(current: readonly BarItem[], ids: readonly string[]): BarItem[] {
  const sorted = [...current].sort((a, b) => a.sortOrder - b.sortOrder);
  const byId = new Map(sorted.map((item) => [item.id, item]));
  const requested: BarItem[] = [];
  const seen = new Set<string>();
  for (const id of ids) {
    const item = byId.get(id);
    if (item && !seen.has(id)) {
      seen.add(id);
      requested.push(item);
    }
  }
  const ordered = [...requested, ...sorted.filter((item) => !seen.has(item.id))];
  return ordered.map((item, index) => ({ ...item, sortOrder: index }));
}

/** Flüchtige Bar für Tests und Vorschauen. */
export class InMemoryBarRepository implements BarRepository {
  private readonly byId = new Map<string, BarItem>();

  constructor(initial: readonly BarItem[] = []) {
    for (const item of initial) this.byId.set(item.id, item);
  }

  async items(): Promise<BarItem[]> {
    return [...this.byId.values()].sort((a, b) => a.sortOrder - b.sortOrder);
  }

  async item(id: string): Promise<BarItem | null> {
    return this.byId.get(id) ?? null;
  }

  async save(item: BarItem): Promise<void> {
    this.byId.set(item.id, item);
  }

  async delete(id: string): Promise<void> {
    this.byId.delete(id);
  }

  async reorder(ids: readonly string[]): Promise<void> {
    for (const item of reorderItems([...this.byId.values()], ids)) this.byId.set(item.id, item);
  }
}
