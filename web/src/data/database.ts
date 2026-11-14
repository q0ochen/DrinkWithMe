import { openDB, type DBSchema, type IDBPDatabase } from "idb";
import type { BarItem } from "../core/bar/barItem";
import type { DrinkEntry } from "../core/log/drinkEntry";

/**
 * Lokale Datenbank der Web-App (IndexedDB). Bar, vollständige Historie, Einstellungen (Zustimmung, zuletzt
 * gesehen …) und die Anmeldesitzung liegen **nur** hier – nie auf dem Server und nie im localStorage.
 */
export interface DrinkWithMeSchema extends DBSchema {
  bar: { key: string; value: BarItem };
  history: { key: string; value: DrinkEntry; indexes: { consumedAt: number } };
  settings: { key: string; value: unknown };
  /** Schlüssel/Wert-Speicher für die Supabase-Sitzung (inkl. PKCE-Code-Verifier). */
  auth: { key: string; value: string };
}

export const DATABASE_NAME = "drink-with-me";
export const DATABASE_VERSION = 1;

export type Database = IDBPDatabase<DrinkWithMeSchema>;

export function openDatabase(name: string = DATABASE_NAME): Promise<Database> {
  return openDB<DrinkWithMeSchema>(name, DATABASE_VERSION, {
    upgrade(db, oldVersion) {
      if (oldVersion < 1) {
        db.createObjectStore("bar", { keyPath: "id" });
        const history = db.createObjectStore("history", { keyPath: "id" });
        history.createIndex("consumedAt", "consumedAt");
        db.createObjectStore("settings");
        db.createObjectStore("auth");
      }
    },
  });
}
