// Tests der Supabase-Adapter gegen ein aufzeichnendes `fetch` (kein Netz): geprüft werden genau die Anfragen
// aus supabase/README.md (Spalten, Prefer-Header, RPC-Namen, Fehlercodes, Edge Functions, PKCE).
import { describe, expect, it, vi } from "vitest";
import { CatalogIds } from "../core/bar/defaultBarCatalog";
import { catalogItem, entryOf } from "../core/testing/fixtures";
import { detectPlatform } from "../platform/platform";
import { germanMessage, SocialError } from "./errors";
import { createSocialServices, type AsyncStorage } from "./index";
import type { PushEnvironment } from "./webPush";

const SUPABASE = "https://projekt.supabase.co";
const ANON = "sb_publishable_test";
const WEB = "https://q0ochen.github.io/DrinkWithMe";
const ME = "11111111-1111-4111-8111-111111111111";
const ANNA = "22222222-2222-4222-8222-222222222222";
const BEN = "33333333-3333-4333-8333-333333333333";
const ACCESS_TOKEN = "jwt-des-nutzers";

interface Recorded {
  method: string;
  url: URL;
  headers: Headers;
  body: unknown;
}

/** Antwort eines Responders; `offline` lässt `fetch` wie ohne Verbindung scheitern. */
type Responder = (request: Recorded) => { status?: number; body?: unknown; offline?: true } | undefined;

class MemoryStorage implements AsyncStorage {
  readonly values = new Map<string, string>();
  async getItem(key: string) {
    return this.values.get(key) ?? null;
  }
  async setItem(key: string, value: string) {
    this.values.set(key, value);
  }
  async removeItem(key: string) {
    this.values.delete(key);
  }
}

function session(userId = ME, expiresInSeconds = 3600) {
  const now = Math.floor(Date.now() / 1000);
  return {
    access_token: ACCESS_TOKEN,
    refresh_token: "refresh",
    token_type: "bearer",
    expires_in: 3600,
    expires_at: now + expiresInSeconds,
    user: {
      id: userId,
      aud: "authenticated",
      role: "authenticated",
      app_metadata: { provider: "google" },
      user_metadata: { full_name: "Ich Selbst", avatar_url: "https://lh3.example/a.png" },
      created_at: new Date().toISOString(),
    },
  };
}

function setUp(options: { signedIn?: boolean; storedSession?: unknown; responders?: Responder[]; push?: PushEnvironment } = {}) {
  const requests: Recorded[] = [];
  const storage = new MemoryStorage();
  if (options.storedSession !== undefined) storage.values.set("drink-with-me-auth", JSON.stringify(options.storedSession));
  else if (options.signedIn ?? true) storage.values.set("drink-with-me-auth", JSON.stringify(session()));
  const responders = options.responders ?? [];
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const request = input instanceof Request ? input : new Request(input, init);
    const text = await request.clone().text();
    let body: unknown;
    try {
      body = text ? JSON.parse(text) : undefined;
    } catch {
      body = text;
    }
    const recorded: Recorded = { method: request.method, url: new URL(request.url), headers: request.headers, body };
    requests.push(recorded);
    for (const responder of responders) {
      const answer = responder(recorded);
      if (answer?.offline) throw new TypeError("Failed to fetch");
      if (answer) {
        return new Response(answer.body === undefined ? null : JSON.stringify(answer.body), {
          status: answer.status ?? 200,
          headers: { "Content-Type": "application/json" },
        });
      }
    }
    return new Response(null, { status: 204 });
  });
  const opened: string[] = [];
  const returnPaths: string[] = [];
  const services = createSocialServices({
    supabase: { url: SUPABASE, anonKey: ANON },
    webAppUrl: WEB,
    vapidPublicKey: "BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkvMeAtA3LFgDzkrxZJjSgSnfckjBJuBkr3qBUYIHBQFLXYp5Nksh8U",
    storage,
    fetch: fetchMock as unknown as typeof fetch,
    auth: {
      redirectTo: `${WEB}/auth/callback`,
      rememberReturnPath: async (path) => void returnPaths.push(path),
      openUrl: (url) => void opened.push(url),
    },
    pushEnvironment: options.push ?? fakePushEnvironment().environment,
  });
  const rest = (path: string) => requests.filter((request) => request.url.pathname === path);
  return { services, requests, storage, opened, returnPaths, rest };
}

const on =
  (method: string, path: string, status: number, body: unknown): Responder =>
  (request) =>
    request.method === method && request.url.pathname === path ? { status, body } : undefined;

const rpcError = (message: string) => ({ code: "P0001", message, details: null, hint: null });

describe("Anmeldung (PKCE)", () => {
  it("leitet zu Google mit der exakten Rückkehradresse und merkt sich den Rückweg", async () => {
    const { services, opened, returnPaths, storage } = setUp({ signedIn: false });
    await services.auth.ready();
    expect(services.auth.currentUser()).toBeNull();
    await services.auth.signInWithGoogle("/einladung/AbCdEfGhIjKlMnOpQrStUv");
    expect(returnPaths).toEqual(["/einladung/AbCdEfGhIjKlMnOpQrStUv"]);
    const url = new URL(opened[0]);
    expect(url.origin + url.pathname).toBe(`${SUPABASE}/auth/v1/authorize`);
    expect(url.searchParams.get("provider")).toBe("google");
    expect(url.searchParams.get("redirect_to")).toBe(`${WEB}/auth/callback`);
    expect(url.searchParams.get("code_challenge_method")).toBe("s256");
    expect(url.searchParams.get("code_challenge")).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect([...storage.values.keys()].some((key) => key.includes("code-verifier"))).toBe(true);
  });

  it("tauscht den Code auf /auth/callback gegen eine Sitzung", async () => {
    const { services, requests } = setUp({
      signedIn: false,
      responders: [on("POST", "/auth/v1/token", 200, session())],
    });
    await services.auth.ready();
    await services.auth.signInWithGoogle("/freunde");
    const changes: Array<string | null> = [];
    services.auth.subscribe((user) => changes.push(user?.displayName ?? null));
    await services.auth.completeSignIn(new URL(`${WEB}/auth/callback?code=abc123`));
    const token = requests.find((request) => request.url.pathname === "/auth/v1/token");
    expect(token?.url.searchParams.get("grant_type")).toBe("pkce");
    expect(token?.body).toMatchObject({ auth_code: "abc123" });
    expect((token?.body as { code_verifier?: string }).code_verifier).toMatch(/.{43,}/);
    expect(services.auth.currentUser()).toEqual({ id: ME, displayName: "Ich Selbst", avatarUrl: "https://lh3.example/a.png" });
    expect(changes).toContain("Ich Selbst");
  });

  it("meldet einen abgebrochenen Google-Dialog verständlich", async () => {
    const { services } = setUp({ signedIn: false });
    await expect(services.auth.completeSignIn(new URL(`${WEB}/auth/callback?error=access_denied`))).rejects.toMatchObject({
      code: "not_authenticated",
    });
  });

  it("stellt eine gespeicherte Sitzung wieder her und meldet lokal ab", async () => {
    const { services, requests } = setUp();
    await services.auth.ready();
    expect(services.auth.currentUser()?.id).toBe(ME);
    await services.auth.signOut("local");
    expect(services.auth.currentUser()).toBeNull();
    const logout = requests.filter((request) => request.url.pathname === "/auth/v1/logout");
    expect(logout.every((request) => request.url.searchParams.get("scope") === "local")).toBe(true);
  });
});

describe("Sitzung erneuern: vorübergehende Fehler sind keine Abmeldung (Review 3, Befund 1)", () => {
  /** supabase-js versucht das Erneuern ~25 s lang; mit falschen Timern läuft das sofort durch. */
  async function restoreWithFakeTimers(options: Parameters<typeof setUp>[0]) {
    vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout", "setInterval", "clearInterval", "Date"] });
    try {
      const setup = setUp(options);
      const seen: Array<string | null> = [];
      setup.services.auth.subscribe((user) => seen.push(user?.id ?? null));
      const ready = setup.services.auth.ready();
      await vi.advanceTimersByTimeAsync(40_000);
      await ready;
      return { ...setup, seen };
    } finally {
      vi.useRealTimers();
    }
  }

  const expired = session(ME, -60);

  it("ohne Verbindung bleibt der gespeicherte Nutzer angemeldet (nichts wird gelöscht)", async () => {
    const { services, storage, seen } = await restoreWithFakeTimers({
      storedSession: expired,
      responders: [(request) => (request.url.pathname === "/auth/v1/token" ? { offline: true } : undefined)],
    });
    expect(services.auth.currentUser()).toMatchObject({ id: ME, displayName: "Ich Selbst" });
    expect(seen).not.toContain(null);
    expect(storage.values.has("drink-with-me-auth")).toBe(true);
  });

  it("Serverfehler 503 beim Erneuern: ebenfalls weiter angemeldet", async () => {
    const { services, seen } = await restoreWithFakeTimers({
      storedSession: expired,
      responders: [on("POST", "/auth/v1/token", 503, { message: "upstream" })],
    });
    expect(services.auth.currentUser()?.id).toBe(ME);
    expect(seen).not.toContain(null);
  });

  it("abgelehnter Refresh-Token (400): wirklich abgemeldet", async () => {
    const { services, storage } = await restoreWithFakeTimers({
      storedSession: expired,
      responders: [on("POST", "/auth/v1/token", 400, { code: "refresh_token_not_found", message: "Invalid Refresh Token" })],
    });
    expect(services.auth.currentUser()).toBeNull();
    expect(storage.values.has("drink-with-me-auth")).toBe(false);
  });

  it("Teilen ohne gültige Sitzung schickt nichts mit dem Anon-Key, sondern meldet „Netz“ (wird nachgeholt)", async () => {
    const { services, rest } = await restoreWithFakeTimers({
      storedSession: expired,
      responders: [(request) => (request.url.pathname === "/auth/v1/token" ? { offline: true } : undefined)],
    });
    const pils = entryOf(catalogItem(CatalogIds.PILS), Date.now(), { id: "6f1c2a4e-0000-4000-8000-0000000000aa", share: true });
    vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout", "Date"] });
    try {
      const attempt = services.publisher.publish(pils).catch((error: unknown) => error);
      await vi.advanceTimersByTimeAsync(40_000);
      expect(await attempt).toMatchObject({ code: "network" });
    } finally {
      vi.useRealTimers();
    }
    expect(rest("/rest/v1/drink_events")).toEqual([]);
  });
});

describe("Geteilte Ereignisse (wie Android)", () => {
  const pils = catalogItem(CatalogIds.PILS);
  const shared = entryOf(pils, Date.parse("2026-11-10T18:00:00.123Z"), { id: "6f1c2a4e-0000-4000-8000-000000000001", share: true });

  it("legt drink_events nur mit den erlaubten Spalten an (ignore-duplicates) und benachrichtigt danach", async () => {
    const { services, requests } = setUp({ responders: [on("POST", "/functions/v1/notify-friends", 200, { recipients: 2, delivered: 2, failed: 0, removed: 0 })] });
    await services.auth.ready();
    await services.publisher.publish(shared);
    const insert = requests.find((request) => request.url.pathname === "/rest/v1/drink_events");
    expect(insert?.method).toBe("POST");
    expect(insert?.url.searchParams.get("on_conflict")).toBe("id");
    expect(insert?.headers.get("Prefer")).toContain("resolution=ignore-duplicates");
    expect(insert?.headers.get("Authorization")).toBe(`Bearer ${ACCESS_TOKEN}`);
    expect(insert?.headers.get("apikey")).toBe(ANON);
    expect(insert?.body).toEqual({
      id: shared.id,
      name: "Pils",
      icon: "beer",
      category: "BEER",
      volume_ml: 500,
      consumed_at: "2026-11-10T18:00:00.123Z",
    });
    const notify = requests.find((request) => request.url.pathname === "/functions/v1/notify-friends");
    expect(notify?.method).toBe("POST");
    expect(notify?.body).toEqual({ eventId: shared.id });
    expect(notify?.headers.get("Authorization")).toBe(`Bearer ${ACCESS_TOKEN}`);
    expect(requests.indexOf(insert as Recorded)).toBeLessThan(requests.indexOf(notify as Recorded));
  });

  it("404 von notify-friends (schon benachrichtigt) ist kein Fehler, 500 schon", async () => {
    const ok = setUp({ responders: [on("POST", "/functions/v1/notify-friends", 404, { error: "event_not_found" })] });
    await ok.services.auth.ready();
    await expect(ok.services.publisher.publish(shared)).resolves.toBeUndefined();
    const broken = setUp({ responders: [on("POST", "/functions/v1/notify-friends", 500, { error: "internal_error" })] });
    await broken.services.auth.ready();
    await expect(broken.services.publisher.publish(shared)).rejects.toBeInstanceOf(SocialError);
  });

  it("Limit des Servers (too_many_events): deutsche Meldung, keine Benachrichtigung", async () => {
    const { services, requests } = setUp({ responders: [on("POST", "/rest/v1/drink_events", 400, rpcError("too_many_events"))] });
    await services.auth.ready();
    const failure = await services.publisher.publish(shared).catch((error: unknown) => error);
    expect(failure).toMatchObject({ code: "too_many_events" });
    expect(germanMessage(failure)).toBe("Heute wurden schon sehr viele Getränke geteilt. Weitere Einträge bleiben vorerst nur auf diesem Gerät.");
    expect(requests.some((request) => request.url.pathname === "/functions/v1/notify-friends")).toBe(false);
  });

  it("zieht ein Ereignis per DELETE zurück", async () => {
    const { services, rest } = setUp();
    await services.auth.ready();
    await services.publisher.unpublish(shared.id);
    const deleted = rest("/rest/v1/drink_events")[0];
    expect(deleted.method).toBe("DELETE");
    expect(deleted.url.searchParams.get("id")).toBe(`eq.${shared.id}`);
  });
});

describe("Freunde, Anfragen und Einladungen", () => {
  const profiles = on("GET", "/rest/v1/profiles", 200, [
    { id: ANNA, display_name: "Anna", avatar_url: null },
    { id: BEN, display_name: "Ben", avatar_url: "https://lh3.example/b.png" },
  ]);

  it("Freundesliste: Freundschaften + Profile, nach Namen sortiert", async () => {
    const { services, rest } = setUp({
      responders: [
        on("GET", "/rest/v1/friendships", 200, [
          { user_a: ME, user_b: BEN, created_at: "2026-11-01T10:00:00Z" },
          { user_a: ANNA, user_b: ME, created_at: "2026-11-02T10:00:00Z" },
        ]),
        profiles,
      ],
    });
    await services.auth.ready();
    const friends = await services.friends.friends();
    expect(friends.map((friend) => friend.profile.displayName)).toEqual(["Anna", "Ben"]);
    expect(friends[0].since).toBe(Date.parse("2026-11-02T10:00:00Z"));
    expect(rest("/rest/v1/profiles")[0].url.searchParams.get("id")).toMatch(/^in\.\(/);
  });

  it("Eingehende Anfragen: nur als Empfänger, mit dem Profil des Absenders", async () => {
    const { services, rest } = setUp({
      responders: [on("GET", "/rest/v1/friend_requests", 200, [{ id: "r-1", from_user: ANNA, created_at: "2026-11-09T10:00:00Z" }]), profiles],
    });
    await services.auth.ready();
    const requests = await services.friends.requests();
    expect(requests.map((request) => [request.id, request.direction, request.other.displayName])).toEqual([["r-1", "INCOMING", "Anna"]]);
    const query = rest("/rest/v1/friend_requests")[0].url.searchParams;
    expect(query.get("select")).toBe("id,from_user,created_at");
    expect(query.get("to_user")).toBe(`eq.${ME}`);
    expect(query.get("order")).toBe("created_at.desc");
  });

  it("Ausgehende Anfragen nur per RPC: maskierte Adresse statt Profil (Review 3, Befund 6)", async () => {
    const { services, rest } = setUp({
      responders: [
        on("POST", "/rest/v1/rpc/list_outgoing_friend_requests", 200, [
          { id: "r-2", email_hint: "b***@example.org", created_at: "2026-11-08T10:00:00Z" },
          { id: "kaputt" },
        ]),
      ],
    });
    await services.auth.ready();
    expect(await services.friends.outgoingRequests()).toEqual([
      { id: "r-2", emailHint: "b***@example.org", createdAt: Date.parse("2026-11-08T10:00:00Z") },
    ]);
    expect(rest("/rest/v1/rpc/list_outgoing_friend_requests")[0].body).toEqual({});
    expect(rest("/rest/v1/profiles")).toEqual([]);
  });

  it("RPCs mit den Parametern aus dem Vertrag", async () => {
    const { services, rest } = setUp({ responders: [on("POST", "/rest/v1/rpc/create_invite_link", 200, "AbCdEfGhIjKlMnOpQrStUvWxYz012345")] });
    await services.auth.ready();
    await services.friends.sendRequestByEmail("  anna@example.org ");
    await services.friends.respond("r-1", true);
    await services.friends.removeFriend(ANNA);
    await services.friends.cancel("r-2");
    const link = await services.friends.createInviteLink();
    expect(rest("/rest/v1/rpc/send_friend_request")[0].body).toEqual({ email: "anna@example.org" });
    expect(rest("/rest/v1/rpc/respond_friend_request")[0].body).toEqual({ request_id: "r-1", accept: true });
    expect(rest("/rest/v1/rpc/remove_friend")[0].body).toEqual({ friend_id: ANNA });
    expect(rest("/rest/v1/rpc/cancel_friend_request")[0].body).toEqual({ request_id: "r-2" });
    expect(rest("/rest/v1/friend_requests")).toEqual([]);
    expect(link).toBe(`${WEB}/einladung/AbCdEfGhIjKlMnOpQrStUvWxYz012345`);
  });

  it("Fehlercodes der RPCs werden zu deutschen Meldungen", async () => {
    const { services } = setUp({
      responders: [
        on("POST", "/rest/v1/rpc/send_friend_request", 400, rpcError("rate_limited")),
        (request) =>
          request.url.pathname === "/rest/v1/rpc/accept_invite"
            ? { status: 400, body: rpcError((request.body as { token: string }).token === "own" ? "invite_own" : "invite_invalid") }
            : undefined,
        on("POST", "/rest/v1/rpc/respond_friend_request", 400, rpcError("request_not_found")),
      ],
    });
    await services.auth.ready();
    const failure = await services.friends.sendRequestByEmail("x@example.org").catch((error: unknown) => error);
    expect(failure).toMatchObject({ code: "rate_limited" });
    expect(germanMessage(failure)).toBe("Für heute sind keine weiteren Versuche möglich. Bitte versuche es morgen noch einmal.");
    await expect(services.friends.acceptInvite("own")).rejects.toMatchObject({ code: "invite_own" });
    await expect(services.friends.acceptInvite("unbekannt")).rejects.toMatchObject({ code: "invite_invalid" });
    await expect(services.friends.respond("r-9", false)).rejects.toMatchObject({ code: "request_not_found" });
  });

  it("Einladung annehmen liefert das Profil des Einladenden", async () => {
    const { services } = setUp({
      responders: [on("POST", "/rest/v1/rpc/accept_invite", 200, { id: ANNA, display_name: "Anna", avatar_url: null, created_at: "x" })],
    });
    await services.auth.ready();
    expect(await services.friends.acceptInvite("AbCdEfGhIjKlMnOpQrStUv")).toEqual({ id: ANNA, displayName: "Anna", avatarUrl: null });
  });

  it("ohne Anmeldung: verständlicher Fehler, keine Anfrage", async () => {
    const { services, requests } = setUp({ signedIn: false });
    await services.auth.ready();
    await expect(services.friends.createInviteLink()).rejects.toMatchObject({ code: "not_authenticated" });
    expect(requests.filter((request) => request.url.pathname.startsWith("/rest/"))).toEqual([]);
  });
});

describe("Feed", () => {
  it("lädt Ereignisse der Freunde (neueste zuerst, ohne eigene) mit Autoren", async () => {
    const { services, rest } = setUp({
      responders: [
        on("GET", "/rest/v1/drink_events", 200, [
          { id: "e-2", author_id: ANNA, name: "Apfelschorle", icon: "apple_spritzer", category: "JUICE", volume_ml: 500, consumed_at: "2026-11-10T17:00:00Z" },
          { id: "e-1", author_id: BEN, name: "Kombucha", icon: "kombucha", category: "FERMENTED", volume_ml: 330, consumed_at: "2026-11-10T16:00:00Z" },
        ]),
        on("GET", "/rest/v1/profiles", 200, [{ id: ANNA, display_name: "Anna", avatar_url: null }]),
      ],
    });
    await services.auth.ready();
    const events = await services.feed.recentEvents(Date.parse("2026-11-10T12:00:00Z"));
    const query = rest("/rest/v1/drink_events")[0].url.searchParams;
    expect(query.get("author_id")).toBe(`neq.${ME}`);
    expect(query.get("order")).toBe("consumed_at.desc");
    expect(query.get("limit")).toBe("100");
    expect(query.get("consumed_at")).toBe("gt.2026-11-10T12:00:00.000Z");
    expect(events.map((event) => [event.author.displayName, event.name, event.icon, event.category])).toEqual([
      ["Anna", "Apfelschorle", "apple_spritzer", "JUICE"],
      ["Jemand", "Kombucha", "other", "OTHER"],
    ]);
  });
});

describe("Konto löschen", () => {
  it("ruft delete-account auf und meldet danach lokal ab", async () => {
    const { services, requests } = setUp({ responders: [on("POST", "/functions/v1/delete-account", 200, { deleted: true })] });
    await services.auth.ready();
    await services.account.deleteAccount();
    const call = requests.find((request) => request.url.pathname === "/functions/v1/delete-account");
    expect(call?.headers.get("Authorization")).toBe(`Bearer ${ACCESS_TOKEN}`);
    expect(services.auth.currentUser()).toBeNull();
  });
});

function fakePushEnvironment(initialPermission: NotificationPermission = "default") {
  let permission: NotificationPermission = initialPermission;
  let current: PushSubscription | null = null;
  const subscribeCalls: PushSubscriptionOptionsInit[] = [];
  let counter = 0;
  const makeSubscription = (options: PushSubscriptionOptionsInit): PushSubscription => {
    counter++;
    const endpoint = `https://push.example/abo-${counter}`;
    const subscription = {
      endpoint,
      options: { applicationServerKey: (options.applicationServerKey as Uint8Array).buffer, userVisibleOnly: true },
      toJSON: () => ({ endpoint, keys: { p256dh: "B".repeat(87), auth: "A".repeat(22) } }),
      unsubscribe: async () => {
        current = null;
        return true;
      },
    } as unknown as PushSubscription;
    return subscription;
  };
  const registration = {
    pushManager: {
      getSubscription: async () => current,
      subscribe: async (options: PushSubscriptionOptionsInit) => {
        subscribeCalls.push(options);
        current = makeSubscription(options);
        return current;
      },
    },
  } as unknown as ServiceWorkerRegistration;
  const environment: PushEnvironment = {
    platform: detectPlatform({ userAgent: "Mozilla/5.0 (X11; Linux x86_64) Chrome/130" }, false),
    registration: async () => registration,
    permission: () => permission,
    requestPermission: async () => {
      permission = "granted";
      return permission;
    },
  };
  return { environment, subscribeCalls, current: () => current };
}

describe("Web Push", () => {
  const serverRow = (request: Recorded) =>
    request.method === "GET" && request.url.pathname === "/rest/v1/push_subscriptions"
      ? { status: 200, body: [{ endpoint: request.url.searchParams.get("endpoint")?.slice(3) }] }
      : undefined;

  it("abonniert mit dem VAPID-Schlüssel und speichert das Abo mit platform 'web'", async () => {
    const push = fakePushEnvironment();
    const { services, rest } = setUp({ push: push.environment, responders: [serverRow] });
    await services.auth.ready();
    expect(await services.push.status()).toMatchObject({ supported: true, subscribed: false, configured: true, needsHomeScreen: false });
    await services.push.enable();
    expect(push.subscribeCalls).toHaveLength(1);
    expect(push.subscribeCalls[0].userVisibleOnly).toBe(true);
    expect((push.subscribeCalls[0].applicationServerKey as Uint8Array).length).toBe(65);
    const calls = rest("/rest/v1/push_subscriptions");
    expect(calls.map((call) => call.method)).toEqual(["DELETE", "POST"]);
    expect(calls[0].url.searchParams.get("endpoint")).toBe("eq.https://push.example/abo-1");
    expect(calls[1].body).toEqual({ endpoint: "https://push.example/abo-1", p256dh: "B".repeat(87), auth: "A".repeat(22), platform: "web" });
    expect(await services.push.status()).toMatchObject({ subscribed: true, permission: "granted" });
    const check = rest("/rest/v1/push_subscriptions").at(-1);
    expect(check?.method).toBe("GET");
    expect(check?.url.searchParams.get("endpoint")).toBe("eq.https://push.example/abo-1");

    await services.push.disable();
    expect(rest("/rest/v1/push_subscriptions").at(-1)?.method).toBe("DELETE");
    expect(push.current()).toBeNull();
  });

  it("zu viele Geräte: deutsche Meldung, und das neue Browser-Abo wird wieder abbestellt (Review 3, Befund 21)", async () => {
    const push = fakePushEnvironment();
    const { services } = setUp({
      push: push.environment,
      responders: [on("POST", "/rest/v1/push_subscriptions", 400, rpcError("too_many_subscriptions")), on("GET", "/rest/v1/push_subscriptions", 200, [])],
    });
    await services.auth.ready();
    const failure = await services.push.enable().catch((error: unknown) => error);
    expect(failure).toMatchObject({ code: "too_many_subscriptions" });
    expect(germanMessage(failure)).toContain("fünf Geräten");
    expect(push.current()).toBeNull();
    expect(await services.push.status()).toMatchObject({ subscribed: false, permission: "granted" });
  });

  it("Endpoint gehört noch einem anderen Konto und das erneute Speichern scheitert: kein verwaistes Abo", async () => {
    const push = fakePushEnvironment();
    let posts = 0;
    const { services } = setUp({
      push: push.environment,
      responders: [
        (request) => {
          if (request.method !== "POST" || request.url.pathname !== "/rest/v1/push_subscriptions") return undefined;
          posts++;
          return posts === 1
            ? { status: 409, body: { code: "23505", message: "duplicate key value violates unique constraint", details: null, hint: null } }
            : { status: 400, body: rpcError("too_many_subscriptions") };
        },
      ],
    });
    await services.auth.ready();
    await expect(services.push.enable()).rejects.toMatchObject({ code: "too_many_subscriptions" });
    expect(push.subscribeCalls).toHaveLength(2);
    expect(push.current()).toBeNull();
  });

  it("status() prüft die Zeile auf dem Server; ohne Verbindung zählt das Browser-Abo", async () => {
    const push = fakePushEnvironment("granted");
    let answer: { status?: number; body?: unknown; offline?: true } = { status: 200, body: [] };
    const { services } = setUp({
      push: push.environment,
      responders: [(request) => (request.method === "GET" && request.url.pathname === "/rest/v1/push_subscriptions" ? answer : undefined)],
    });
    await services.auth.ready();
    const registration = await push.environment.registration();
    await registration?.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: new Uint8Array(65) });
    expect(await services.push.status()).toMatchObject({ subscribed: false });
    answer = { status: 200, body: [{ endpoint: "https://push.example/abo-1" }] };
    expect(await services.push.status()).toMatchObject({ subscribed: true });
    answer = { offline: true };
    expect(await services.push.status()).toMatchObject({ subscribed: true });
  });

  it("sync(): ein gewünschtes, aber verlorenes Abo wird neu angelegt – ohne Berechtigungsabfrage (Review 3, Befund 1)", async () => {
    const granted = fakePushEnvironment("granted");
    const { services, rest } = setUp({ push: granted.environment });
    await services.auth.ready();
    await services.push.sync(false);
    expect(granted.subscribeCalls).toHaveLength(0);
    await services.push.sync(true);
    expect(granted.subscribeCalls).toHaveLength(1);
    expect(rest("/rest/v1/push_subscriptions").map((call) => call.method)).toEqual(["DELETE", "POST"]);

    const notAsked = fakePushEnvironment("default");
    const second = setUp({ push: notAsked.environment });
    await second.services.auth.ready();
    await second.services.push.sync(true);
    expect(notAsked.subscribeCalls).toHaveLength(0);
  });

  it("abgelehnte Berechtigung und fehlende Anmeldung", async () => {
    const denied = fakePushEnvironment("denied");
    denied.environment.requestPermission = async () => "denied";
    const { services } = setUp({ push: denied.environment });
    await services.auth.ready();
    await expect(services.push.enable()).rejects.toMatchObject({ code: "push_denied" });
    const signedOut = setUp({ signedIn: false });
    await signedOut.services.auth.ready();
    await expect(signedOut.services.push.enable()).rejects.toMatchObject({ code: "not_authenticated" });
  });
});
