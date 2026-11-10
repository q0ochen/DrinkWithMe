import { expect, type Page, type Route } from "@playwright/test";

/** Erfundenes Supabase-Projekt der Test-Builds (siehe .env.e2e) – jede Anfrage wird hier beantwortet. */
export const SUPABASE = "https://e2e-projekt.supabase.co";
export const APP = "http://127.0.0.1:4173/DrinkWithMe/";
export const APP_WITHOUT_SERVER = "http://127.0.0.1:4174/DrinkWithMe/";
export const ME = "11111111-1111-4111-8111-111111111111";
export const ANNA = "22222222-2222-4222-8222-222222222222";
export const BEN = "33333333-3333-4333-8333-333333333333";
export const EVENT_ID = "6f1c2a4e-0000-4000-8000-000000000001";
export const INVITE_TOKEN = "AbCdEfGhIjKlMnOpQrStUvWxYz012345";

export interface RecordedRequest {
  method: string;
  path: string;
  search: URLSearchParams;
  headers: Record<string, string>;
  body: unknown;
}

const CORS = {
  "access-control-allow-origin": "*",
  "access-control-allow-headers": "*",
  "access-control-allow-methods": "GET, POST, PATCH, DELETE, OPTIONS",
  "access-control-expose-headers": "*",
};

function session() {
  const now = Math.floor(Date.now() / 1000);
  return {
    access_token: "e2e-jwt",
    refresh_token: "e2e-refresh",
    token_type: "bearer",
    expires_in: 3600,
    expires_at: now + 3600,
    user: {
      id: ME,
      aud: "authenticated",
      role: "authenticated",
      email: "ich@example.org",
      app_metadata: { provider: "google" },
      user_metadata: { full_name: "Mia Muster" },
      created_at: new Date().toISOString(),
    },
  };
}

/** Ein kleines, zustandsbehaftetes Supabase-Double für page.route. */
export class FakeSupabase {
  readonly requests: RecordedRequest[] = [];
  profiles = [
    { id: ME, display_name: "Mia Muster", avatar_url: null },
    { id: ANNA, display_name: "Anna", avatar_url: null },
    { id: BEN, display_name: "Ben", avatar_url: null },
  ];
  friendships = [{ user_a: ME, user_b: ANNA, created_at: "2026-11-01T10:00:00Z" }];
  friendRequests = [{ id: "r-1", from_user: BEN, to_user: ME, created_at: "2026-11-09T10:00:00Z" }];
  events = [
    {
      id: EVENT_ID,
      author_id: ANNA,
      name: "Apfelschorle",
      icon: "apple_spritzer",
      category: "JUICE",
      volume_ml: 500,
      consumed_at: new Date(Date.now() - 5 * 60_000).toISOString(),
    },
  ];

  async install(page: Page): Promise<void> {
    await page.route(`${SUPABASE}/**`, (route) => this.handle(route));
  }

  calls(method: string, path: string): RecordedRequest[] {
    return this.requests.filter((request) => request.method === method && request.path === path);
  }

  private async handle(route: Route): Promise<void> {
    const request = route.request();
    const url = new URL(request.url());
    const method = request.method();
    if (method === "OPTIONS") {
      await route.fulfill({ status: 204, headers: CORS });
      return;
    }
    let body: unknown = undefined;
    const text = request.postData();
    if (text) {
      try {
        body = JSON.parse(text);
      } catch {
        body = text;
      }
    }
    this.requests.push({ method, path: url.pathname, search: url.searchParams, headers: request.headers(), body });
    const json = (status: number, payload: unknown) =>
      route.fulfill({ status, headers: { ...CORS, "content-type": "application/json" }, body: JSON.stringify(payload) });

    switch (`${method} ${url.pathname}`) {
      case "GET /auth/v1/authorize": {
        // Google-Anmeldung überspringen: direkt mit Code zur App zurück (wie nach erfolgreicher Anmeldung).
        const redirect = new URL(url.searchParams.get("redirect_to") ?? APP);
        redirect.searchParams.set("code", "e2e-code");
        await route.fulfill({ status: 302, headers: { location: redirect.toString() } });
        return;
      }
      case "POST /auth/v1/token":
        await json(200, session());
        return;
      case "POST /auth/v1/logout":
        await route.fulfill({ status: 204, headers: CORS });
        return;
      case "GET /auth/v1/user":
        await json(200, session().user);
        return;
      case "GET /rest/v1/profiles": {
        const filter = url.searchParams.get("id") ?? "";
        const ids = filter.startsWith("in.(") ? filter.slice(4, -1).split(",").map((id) => id.replace(/"/g, "")) : [];
        await json(200, this.profiles.filter((profile) => ids.includes(profile.id)));
        return;
      }
      case "GET /rest/v1/friendships":
        await json(200, this.friendships);
        return;
      case "GET /rest/v1/friend_requests":
        await json(200, this.friendRequests);
        return;
      case "DELETE /rest/v1/friend_requests":
        this.friendRequests = [];
        await route.fulfill({ status: 204, headers: CORS });
        return;
      case "GET /rest/v1/drink_events": {
        const id = url.searchParams.get("id");
        const since = url.searchParams.get("consumed_at");
        let rows = id ? this.events.filter((event) => `eq.${event.id}` === id) : this.events;
        if (since?.startsWith("gt.")) rows = rows.filter((event) => Date.parse(event.consumed_at) > Date.parse(since.slice(3)));
        await json(200, rows);
        return;
      }
      case "POST /rest/v1/drink_events":
        await route.fulfill({ status: 201, headers: CORS });
        return;
      case "DELETE /rest/v1/drink_events":
        await route.fulfill({ status: 204, headers: CORS });
        return;
      case "POST /functions/v1/notify-friends":
        await json(200, { recipients: 1, delivered: 1, failed: 0, removed: 0 });
        return;
      case "POST /functions/v1/delete-account":
        await json(200, { deleted: true });
        return;
      case "POST /rest/v1/rpc/create_invite_link":
        await json(200, INVITE_TOKEN);
        return;
      case "POST /rest/v1/rpc/accept_invite":
        if ((body as { token?: string })?.token === INVITE_TOKEN) {
          await json(200, { id: ANNA, display_name: "Anna", avatar_url: null, created_at: "2026-11-01T10:00:00Z" });
        } else {
          await json(400, { code: "P0001", message: "invite_invalid", details: null, hint: null });
        }
        return;
      case "POST /rest/v1/rpc/send_friend_request":
      case "POST /rest/v1/rpc/respond_friend_request":
      case "POST /rest/v1/rpc/remove_friend":
        await route.fulfill({ status: 204, headers: CORS });
        return;
      default:
        await json(404, { message: `Im Test nicht vorgesehen: ${method} ${url.pathname}` });
    }
  }
}

/** Sprachausgabe abfangen: gesprochene Sätze landen in `window.__spoken`. */
export async function recordSpeech(page: Page): Promise<void> {
  await page.addInitScript(() => {
    const spoken: string[] = [];
    (window as unknown as { __spoken: string[] }).__spoken = spoken;
    if ("speechSynthesis" in window) {
      window.speechSynthesis.speak = (utterance: SpeechSynthesisUtterance) => {
        spoken.push(utterance.text);
      };
      window.speechSynthesis.cancel = () => {};
    }
  });
}

export async function spoken(page: Page): Promise<string[]> {
  return page.evaluate(() => (window as unknown as { __spoken: string[] }).__spoken ?? []);
}

/** Erster Start: Vorauswahl übernehmen. */
export async function setUpBar(page: Page, base = APP): Promise<void> {
  await page.goto(base);
  await expect(page.getByRole("heading", { level: 1, name: "Willkommen bei Drink With Me" })).toBeVisible();
  await page.getByRole("button", { name: /Bar einrichten \(7 Getränke\)/ }).click();
  await expect(page.getByRole("heading", { level: 1, name: "Bar" })).toBeVisible();
}

/** Anmeldung über den (gefälschten) Google-/Supabase-Ablauf mit PKCE; kehrt zu `returnPath` zurück. */
export async function signIn(page: Page, startPath = "freunde"): Promise<void> {
  await page.goto(`${APP}${startPath}`);
  await page.getByRole("button", { name: "Mit Google anmelden" }).first().click();
}
