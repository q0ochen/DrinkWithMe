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

type Responder = (request: Recorded) => { status?: number; body?: unknown } | undefined;

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

function session(userId = ME) {
  const now = Math.floor(Date.now() / 1000);
  return {
    access_token: ACCESS_TOKEN,
    refresh_token: "refresh",
    token_type: "bearer",
    expires_in: 3600,
    expires_at: now + 3600,
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

function setUp(options: { signedIn?: boolean; responders?: Responder[]; push?: PushEnvironment } = {}) {
  const requests: Recorded[] = [];
  const storage = new MemoryStorage();
  if (options.signedIn ?? true) storage.values.set("drink-with-me-auth", JSON.stringify(session()));
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

  it("Anfragen: Richtung aus Sicht des Nutzers", async () => {
    const { services } = setUp({
      responders: [
        on("GET", "/rest/v1/friend_requests", 200, [
          { id: "r-1", from_user: ANNA, to_user: ME, created_at: "2026-11-09T10:00:00Z" },
          { id: "r-2", from_user: ME, to_user: BEN, created_at: "2026-11-08T10:00:00Z" },
        ]),
        profiles,
      ],
    });
    await services.auth.ready();
    const requests = await services.friends.requests();
    expect(requests.map((request) => [request.id, request.direction, request.other.displayName])).toEqual([
      ["r-1", "INCOMING", "Anna"],
      ["r-2", "OUTGOING", "Ben"],
    ]);
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
    expect(rest("/rest/v1/friend_requests")[0]).toMatchObject({ method: "DELETE" });
    expect(rest("/rest/v1/friend_requests")[0].url.searchParams.get("id")).toBe("eq.r-2");
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
  it("abonniert mit dem VAPID-Schlüssel und speichert das Abo mit platform 'web'", async () => {
    const push = fakePushEnvironment();
    const { services, rest } = setUp({ push: push.environment });
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

    await services.push.disable();
    expect(rest("/rest/v1/push_subscriptions").at(-1)?.method).toBe("DELETE");
    expect(push.current()).toBeNull();
  });

  it("zu viele Geräte: deutsche Meldung", async () => {
    const push = fakePushEnvironment();
    const { services } = setUp({
      push: push.environment,
      responders: [on("POST", "/rest/v1/push_subscriptions", 400, rpcError("too_many_subscriptions"))],
    });
    await services.auth.ready();
    const failure = await services.push.enable().catch((error: unknown) => error);
    expect(failure).toMatchObject({ code: "too_many_subscriptions" });
    expect(germanMessage(failure)).toContain("fünf Geräten");
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
