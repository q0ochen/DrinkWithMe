import type { AlcoholSharingConsent } from "../core/sharing/sharingPolicy";
import type { Database } from "./database";

/** Angemeldeter Nutzer (nur ID und Name, für den schnellen Start ohne Netz). */
export interface StoredUser {
  readonly id: string;
  readonly displayName: string;
}

/** Lokale Einstellungen – nur auf dem Gerät. */
export interface AppSettings {
  /** Bar beim ersten Start eingerichtet. */
  readonly onboardingDone: boolean;
  /** Einmalige Antwort auf „Alkoholische Getränke mit Freunden teilen?“. */
  readonly alcoholConsent: AlcoholSharingConsent;
  /** Nach dem Eintragen laut bestätigen („Apfelschorle eingetragen.“). */
  readonly speakConfirmations: boolean;
  /** DGE-Bänder im Wochen-Diagramm anzeigen (ausblendbar). */
  readonly showReferenceBands: boolean;
  /** Zeitpunkt des neuesten gesehenen Freundes-Ereignisses. */
  readonly feedLastSeenAt: number | null;
  /** Der Nutzer möchte Benachrichtigungen (Web Push) bekommen. */
  readonly pushWanted: boolean;
  readonly signedInUser: StoredUser | null;
  /** Wohin nach der Google-Anmeldung zurückgekehrt wird (z. B. /einladung/<token>). */
  readonly returnPath: string | null;
}

export const DEFAULT_SETTINGS: AppSettings = {
  onboardingDone: false,
  alcoholConsent: "UNKNOWN",
  speakConfirmations: false,
  showReferenceBands: true,
  feedLastSeenAt: null,
  pushWanted: false,
  signedInUser: null,
  returnPath: null,
};

type Key = keyof AppSettings;
const KEYS = Object.keys(DEFAULT_SETTINGS) as Key[];

/** Prüft gespeicherte Werte; Unpassendes fällt auf den Standard zurück. */
function sanitize<K extends Key>(key: K, value: unknown): AppSettings[K] {
  const fallback = DEFAULT_SETTINGS[key];
  switch (key) {
    case "onboardingDone":
    case "speakConfirmations":
    case "showReferenceBands":
    case "pushWanted":
      return (typeof value === "boolean" ? value : fallback) as AppSettings[K];
    case "alcoholConsent":
      return (value === "ALLOWED" || value === "DENIED" || value === "UNKNOWN" ? value : fallback) as AppSettings[K];
    case "feedLastSeenAt":
      return (typeof value === "number" && Number.isFinite(value) ? value : null) as AppSettings[K];
    case "returnPath":
      return (typeof value === "string" && value.startsWith("/") && !value.startsWith("//") ? value : null) as AppSettings[K];
    case "signedInUser": {
      if (typeof value !== "object" || value === null) return null as AppSettings[K];
      const user = value as Record<string, unknown>;
      return (typeof user.id === "string" && typeof user.displayName === "string"
        ? { id: user.id, displayName: user.displayName }
        : null) as AppSettings[K];
    }
    default:
      return fallback;
  }
}

/** Liest und schreibt Einstellungen; hält eine Kopie im Speicher, damit Regeln synchron prüfen können. */
export interface SettingsStore {
  current(): AppSettings;
  update(patch: Partial<AppSettings>): Promise<AppSettings>;
  subscribe(listener: (settings: AppSettings) => void): () => void;
}

export class IndexedDbSettingsStore implements SettingsStore {
  private settings: AppSettings;
  private readonly listeners = new Set<(settings: AppSettings) => void>();

  private constructor(
    private readonly db: Database,
    initial: AppSettings,
  ) {
    this.settings = initial;
  }

  static async load(db: Database): Promise<IndexedDbSettingsStore> {
    const tx = db.transaction("settings", "readonly");
    const values: Record<string, unknown> = {};
    for (const key of KEYS) values[key] = await tx.store.get(key);
    await tx.done;
    const loaded = Object.fromEntries(KEYS.map((key) => [key, values[key] === undefined ? DEFAULT_SETTINGS[key] : sanitize(key, values[key])]));
    return new IndexedDbSettingsStore(db, loaded as unknown as AppSettings);
  }

  current(): AppSettings {
    return this.settings;
  }

  async update(patch: Partial<AppSettings>): Promise<AppSettings> {
    const next = { ...this.settings };
    const tx = this.db.transaction("settings", "readwrite");
    for (const key of Object.keys(patch) as Key[]) {
      if (!KEYS.includes(key)) continue;
      const value = sanitize(key, patch[key]);
      (next as Record<Key, unknown>)[key] = value;
      await tx.store.put(value, key);
    }
    await tx.done;
    this.settings = next;
    for (const listener of this.listeners) listener(next);
    return next;
  }

  subscribe(listener: (settings: AppSettings) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }
}

/** Flüchtige Einstellungen für Tests. */
export class InMemorySettingsStore implements SettingsStore {
  private settings: AppSettings;
  private readonly listeners = new Set<(settings: AppSettings) => void>();

  constructor(initial: Partial<AppSettings> = {}) {
    this.settings = { ...DEFAULT_SETTINGS, ...initial };
  }

  current(): AppSettings {
    return this.settings;
  }

  async update(patch: Partial<AppSettings>): Promise<AppSettings> {
    this.settings = { ...this.settings, ...patch };
    for (const listener of this.listeners) listener(this.settings);
    return this.settings;
  }

  subscribe(listener: (settings: AppSettings) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }
}
