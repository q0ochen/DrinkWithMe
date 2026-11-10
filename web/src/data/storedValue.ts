import type { Database } from "./database";

/**
 * Ein einzelner gespeicherter Wert (z. B. die Warteschlange nachzuholender Teilen-Aufträge). Gelesen wird roh
 * (`unknown`) – wer ihn nutzt, prüft ihn selbst. `null` löscht den Wert.
 */
export interface StoredValue {
  read(): Promise<unknown>;
  write(value: unknown): Promise<void>;
}

/** Wert im IndexedDB-Store `settings` unter einem eigenen Schlüssel (ohne die Einstellungs-Beobachter zu wecken). */
export class IndexedDbStoredValue implements StoredValue {
  constructor(
    private readonly db: Database,
    private readonly key: string,
  ) {}

  async read(): Promise<unknown> {
    return this.db.get("settings", this.key);
  }

  async write(value: unknown): Promise<void> {
    if (value === null) await this.db.delete("settings", this.key);
    else await this.db.put("settings", value, this.key);
  }
}

/** Flüchtiger Wert für Tests und ohne IndexedDB. */
export class InMemoryStoredValue implements StoredValue {
  private value: unknown = undefined;

  async read(): Promise<unknown> {
    return structuredClone(this.value);
  }

  async write(value: unknown): Promise<void> {
    this.value = value === null ? undefined : structuredClone(value);
  }
}
