// Kompositionswurzel: Anmeldung ↔ gespeicherter Nutzer, Push-Abgleich, Nachholen geteilter Einträge,
// Sicherung ohne Teilen und dauerhafter Speicher – mit einer Attrappe der Vernetzung (kein Netz).
import { describe, expect, it, vi } from "vitest";
import { InMemoryBarRepository } from "../core/bar/barRepository";
import { CatalogIds } from "../core/bar/defaultBarCatalog";
import { InMemoryHistoryRepository, type DrinkEntry } from "../core/log/drinkEntry";
import type { UserProfile } from "../core/social/ports";
import { catalogItem, entryOf, MutableClock, SequentialIds } from "../core/testing/fixtures";
import { serializeBackup } from "../data/backup";
import { InMemorySettingsStore, type AppSettings } from "../data/settingsStore";
import { InMemoryStoredValue } from "../data/storedValue";
import { detectPlatform } from "../platform/platform";
import { SocialError } from "../social/errors";
import type { AuthService, PushService, SocialServices } from "../social/types";
import { createAppServices } from "./container";

const ME: UserProfile = { id: "me-1", displayName: "Mia", avatarUrl: null };
const NOW = Date.parse("2026-11-10T18:00:00Z");

class FakeAuth implements AuthService {
  user: UserProfile | null;
  private readonly listeners = new Set<(user: UserProfile | null) => void>();
  constructor(user: UserProfile | null) {
    this.user = user;
  }
  ready = async () => {};
  currentUser = () => this.user;
  subscribe(listener: (user: UserProfile | null) => void) {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }
  signInWithGoogle = async () => {};
  completeSignIn = async () => {};
  async signOut() {
    this.user = null;
    for (const listener of this.listeners) listener(null);
  }
}

function setUp(options: { user?: UserProfile | null; settings?: Partial<AppSettings>; failPublish?: () => unknown } = {}) {
  const auth = new FakeAuth(options.user === undefined ? ME : options.user);
  const published: string[] = [];
  const syncCalls: boolean[] = [];
  const recipients: Array<string | null> = [];
  const persistCalls: number[] = [];
  const push: PushService = {
    status: async () => ({ supported: true, needsHomeScreen: false, permission: "granted", subscribed: true, configured: true }),
    enable: async () => {},
    disable: async () => {},
    sync: async (wanted) => void syncCalls.push(wanted),
  };
  const social = {
    auth,
    push,
    publisher: {
      publish: async (entry: DrinkEntry) => {
        const failure = options.failPublish?.();
        if (failure) throw failure;
        published.push(`publish:${entry.id}`);
      },
      unpublish: async (id: string) => {
        const failure = options.failPublish?.();
        if (failure) throw failure;
        published.push(`unpublish:${id}`);
      },
    },
  } as unknown as SocialServices;
  const settings = new InMemorySettingsStore({
    onboardingDone: true,
    signedInUser: auth.user ? { id: auth.user.id, displayName: auth.user.displayName } : null,
    ...options.settings,
  });
  const history = new InMemoryHistoryRepository();
  const bar = new InMemoryBarRepository();
  const pendingStore = new InMemoryStoredValue();
  const services = createAppServices({
    config: { supabase: { url: "https://x.supabase.co", anonKey: "anon" }, webAppUrl: "https://example.org/app", vapidPublicKey: "key", basePath: "/app/", version: "test" },
    platform: detectPlatform({ userAgent: "Mozilla/5.0 (X11; Linux x86_64) Chrome/130" }, false),
    bar,
    history,
    settings,
    speaker: { available: false, speak: () => false, cancel: () => {}, hasGermanVoice: async () => false },
    recognizer: null,
    imageSharer: { canShareFiles: () => false, share: async () => false, download: () => {} },
    loadSocial: async () => social,
    onSignedInUserChanged: (user) => void recipients.push(user?.id ?? null),
    pendingShareStore: pendingStore,
    persistentStorage: { request: async () => (persistCalls.push(1), true) },
    clock: new MutableClock(NOW),
    ids: new SequentialIds(),
  } as Parameters<typeof createAppServices>[0]);
  return { services, auth, published, syncCalls, recipients, persistCalls, settings, history, bar, pendingStore };
}

describe("Anmeldung und gespeicherter Nutzer", () => {
  it("nur eine ausdrückliche Abmeldung löscht den gespeicherten Nutzer und die Empfänger-ID", async () => {
    const { services, auth, recipients, settings } = setUp();
    await services.social();
    expect(services.session.current()?.id).toBe(ME.id);
    expect(recipients).toEqual([ME.id]);
    await auth.signOut();
    await vi.waitFor(() => expect(settings.current().signedInUser).toBeNull());
    expect(services.session.current()).toBeNull();
    expect(recipients).toEqual([ME.id, null]);
  });

  it("Push-Abgleich beim Start mit dem Wunsch des Nutzers (pushWanted)", async () => {
    const wanted = setUp({ settings: { pushWanted: true } });
    await wanted.services.social();
    await vi.waitFor(() => expect(wanted.syncCalls).toEqual([true]));
    const notWanted = setUp();
    await notWanted.services.social();
    await vi.waitFor(() => expect(notWanted.syncCalls).toEqual([false]));
    const signedOut = setUp({ user: null });
    await signedOut.services.social();
    expect(signedOut.syncCalls).toEqual([]);
  });
});

describe("Teilen ohne Verbindung wird nachgeholt", () => {
  it("Netzfehler → vorgemerkt; retryPendingShares() holt nach, sobald es wieder geht", async () => {
    let offline = true;
    const { services, published } = setUp({ failPublish: () => (offline ? new SocialError("network") : null) });
    await services.social();
    const entry = await services.logDrink.execute(catalogItem(CatalogIds.WASSER));
    expect(entry.shareWithFriends).toBe(true);
    await vi.waitFor(async () => expect(await services.pendingShares.snapshot()).toEqual([{ op: "publish", entryId: entry.id }]));
    expect(await services.retryPendingShares()).toBe("retryLater");
    offline = false;
    expect(await services.retryPendingShares()).toBe("done");
    expect(published).toEqual([`publish:${entry.id}`]);
    expect(await services.pendingShares.snapshot()).toEqual([]);
  });

  it("Löschen ohne Verbindung: das Zurückziehen wird nachgeholt", async () => {
    let offline = false;
    const { services, published } = setUp({ failPublish: () => (offline ? new SocialError("network") : null) });
    await services.social();
    const entry = await services.logDrink.execute(catalogItem(CatalogIds.WASSER));
    await vi.waitFor(() => expect(published).toEqual([`publish:${entry.id}`]));
    offline = true;
    await services.deleteEntry.execute(entry.id);
    await vi.waitFor(async () => expect(await services.pendingShares.snapshot()).toEqual([{ op: "unpublish", entryId: entry.id }]));
    offline = false;
    await services.retryPendingShares();
    expect(published).toEqual([`publish:${entry.id}`, `unpublish:${entry.id}`]);
  });
});

describe("Sicherung und Speicher", () => {
  it("eingelesene Einträge werden nicht geteilt und lösen keine Netzaufträge aus", async () => {
    const { services, published, history } = setUp();
    await services.social();
    const water = catalogItem(CatalogIds.WASSER);
    const summary = await services.backup.importJson(serializeBackup([water], [entryOf(water, NOW - 60_000, { id: "aus-safari", share: true })], NOW));
    expect(summary).toMatchObject({ addedEntries: 1, addedBarItems: 1 });
    expect(await history.entry("aus-safari")).toMatchObject({ shareWithFriends: true });
    await new Promise((resolve) => setTimeout(resolve, 10));
    expect(published).toEqual([]);
    expect(await services.pendingShares.snapshot()).toEqual([]);
  });

  it("bittet nur einmal um dauerhaften Speicher", async () => {
    const { services, persistCalls, settings } = setUp();
    await services.requestPersistentStorage();
    await services.requestPersistentStorage();
    expect(persistCalls).toHaveLength(1);
    expect(settings.current().persistentStorageRequested).toBe(true);
  });
});
