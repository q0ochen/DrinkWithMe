import { expect, test } from "@playwright/test";
import { ANNA, APP, EVENT_ID, FakeSupabase, INVITE_TOKEN, recordSpeech, setUpBar, signIn, spoken, SUPABASE } from "./support";

// Vernetzung mit Supabase – alle Server-Aufrufe beantwortet das FakeSupabase-Double (page.route).

test("Anmelde-Knopf erscheint, wenn der Server eingerichtet ist (PKCE mit exakter Rückkehradresse)", async ({ page }) => {
  const supabase = new FakeSupabase();
  await supabase.install(page);
  await page.goto("freunde");
  await expect(page.getByRole("heading", { level: 1, name: "Freunde" })).toBeVisible();
  const button = page.getByRole("button", { name: "Mit Google anmelden" });
  await expect(button).toBeVisible();
  await button.click();
  await expect.poll(() => supabase.calls("POST", "/auth/v1/token").length).toBe(1);
  await expect(page).toHaveURL(/\/DrinkWithMe\/freunde$/);
  const authorize = supabase.calls("GET", "/auth/v1/authorize")[0];
  expect(authorize.search.get("provider")).toBe("google");
  expect(authorize.search.get("redirect_to")).toBe(`${APP}auth/callback`);
  expect(authorize.search.get("code_challenge_method")).toBe("s256");
  const token = supabase.calls("POST", "/auth/v1/token")[0];
  expect(token.search.get("grant_type")).toBe("pkce");
  expect(token.body).toMatchObject({ auth_code: "e2e-code" });
  // Angemeldet: Feed, Anfrage und Freundesliste.
  await expect(page.getByText("Anna trinkt gerade")).toBeVisible();
  await expect(page.getByText("Apfelschorle (500 ml)")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Anfragen an dich" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Annehmen" })).toBeVisible();
  await expect(page.locator(".person-name").filter({ hasText: "Anna" })).toBeVisible();
});

test("Einladungsseite: anmelden, zurückkehren und im Browser annehmen", async ({ page }) => {
  const supabase = new FakeSupabase();
  await supabase.install(page);
  await page.goto(`einladung/${INVITE_TOKEN}`);
  await expect(page.getByRole("heading", { level: 1, name: "Einladung zu Drink With Me" })).toBeVisible();
  await expect(page.getByRole("link", { name: "In der App öffnen" })).toHaveCount(0); // kein Android
  await page.getByRole("button", { name: "Mit Google anmelden" }).click();
  await expect(page).toHaveURL(new RegExp(`/DrinkWithMe/einladung/${INVITE_TOKEN}$`));
  await page.getByRole("button", { name: "Einladung annehmen" }).click();
  await expect(page.getByText("Du bist jetzt mit Anna befreundet.").first()).toBeVisible();
  expect(supabase.calls("POST", "/rest/v1/rpc/accept_invite")[0].body).toEqual({ token: INVITE_TOKEN });
});

test("Einladungsseite auf Android: „In der App öffnen“ mit drinkwithme://", async ({ browser }) => {
  const context = await browser.newContext({
    userAgent: "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36",
    serviceWorkers: "block",
  });
  const page = await context.newPage();
  await new FakeSupabase().install(page);
  await page.goto(`${APP}einladung/${INVITE_TOKEN}`);
  await expect(page.getByRole("link", { name: "In der App öffnen" })).toHaveAttribute("href", `drinkwithme://einladung/${INVITE_TOKEN}`);
  await page.getByRole("button", { name: "Stattdessen im Browser annehmen" }).click();
  await expect(page.getByRole("button", { name: "Mit Google anmelden" })).toBeVisible();
  await context.close();
});

test("Ungültiger Einladungs-Link", async ({ page }) => {
  await page.goto("einladung/kurz");
  await expect(page.getByRole("heading", { level: 1, name: "Seite nicht gefunden" })).toBeVisible();
});

test("Geteiltes Getränk: drink_events mit erlaubten Spalten (ignore-duplicates), dann notify-friends", async ({ page }) => {
  const supabase = new FakeSupabase();
  await supabase.install(page);
  await setUpBar(page);
  await signIn(page, "freunde");
  await expect(page.getByText("Anna trinkt gerade")).toBeVisible();
  await page.getByRole("navigation", { name: "Hauptbereiche" }).getByRole("link", { name: "Bar" }).click();
  await page.getByRole("button", { name: /^Wasser/ }).click();
  await expect.poll(() => supabase.calls("POST", "/functions/v1/notify-friends").length).toBe(1);
  const insert = supabase.calls("POST", "/rest/v1/drink_events")[0];
  expect(insert.headers.prefer).toContain("resolution=ignore-duplicates");
  expect(insert.headers.authorization).toBe("Bearer e2e-jwt");
  expect(Object.keys(insert.body as object).sort()).toEqual(["category", "consumed_at", "icon", "id", "name", "volume_ml"]);
  expect(insert.body).toMatchObject({ name: "Wasser", icon: "water", category: "WATER", volume_ml: 250 });
  const notify = supabase.calls("POST", "/functions/v1/notify-friends")[0];
  expect(notify.body).toEqual({ eventId: (insert.body as { id: string }).id });

  // Rückgängig zieht das geteilte Ereignis zurück.
  await page.getByRole("button", { name: /Rückgängig/ }).click();
  await expect.poll(() => supabase.calls("DELETE", "/rest/v1/drink_events").length).toBe(1);
});

test("Alkohol: einmalige Frage, erst nach „Ja“ geteilt", async ({ page }) => {
  const supabase = new FakeSupabase();
  await supabase.install(page);
  await setUpBar(page);
  await signIn(page, "freunde");
  await expect(page.getByText("Anna trinkt gerade")).toBeVisible();
  await page.getByRole("navigation", { name: "Hauptbereiche" }).getByRole("link", { name: "Bar" }).click();
  await page.getByRole("button", { name: /^Pils/ }).click();
  const dialog = page.getByRole("dialog", { name: "Alkoholische Getränke mit Freunden teilen?" });
  await expect(dialog).toBeVisible();
  await expect(dialog).toContainText("Das kannst du später in den Einstellungen ändern.");
  expect(supabase.calls("POST", "/rest/v1/drink_events")).toHaveLength(0);
  await dialog.getByRole("button", { name: "Ja" }).click();
  await expect.poll(() => supabase.calls("POST", "/rest/v1/drink_events").length).toBe(1);
  expect(supabase.calls("POST", "/rest/v1/drink_events")[0].body).toMatchObject({ name: "Pils", category: "BEER", volume_ml: 500 });
  // Zweites alkoholisches Getränk: keine Frage mehr.
  await page.getByRole("button", { name: /^Rotwein trocken/ }).click();
  await expect(dialog).toBeHidden();
  await expect.poll(() => supabase.calls("POST", "/rest/v1/drink_events").length).toBe(2);
});

test("Ereignis-Bildschirm: großer Satz und „Vorlesen“", async ({ page }) => {
  await recordSpeech(page);
  const supabase = new FakeSupabase();
  await supabase.install(page);
  await signIn(page, `ereignis/${EVENT_ID}`);
  await expect(page).toHaveURL(new RegExp(`/ereignis/${EVENT_ID}$`));
  await expect(page.getByText("Anna trinkt eine Apfelschorle.")).toBeVisible();
  const button = page.getByRole("button", { name: "Vorlesen" });
  await expect(button).toBeVisible();
  expect((await button.boundingBox())?.height ?? 0).toBeGreaterThanOrEqual(96);
  await button.click();
  await expect.poll(() => spoken(page)).toEqual(["Anna trinkt eine Apfelschorle."]);
});

test("Einladungs-Link erstellen und E-Mail-Anfrage mit neutraler Antwort", async ({ page, context }) => {
  await context.grantPermissions(["clipboard-read", "clipboard-write"]);
  await page.addInitScript(() => {
    // Kein Teilen-Dialog im Test: Rückfall auf die Zwischenablage.
    Object.defineProperty(navigator, "share", { value: undefined, configurable: true });
  });
  const supabase = new FakeSupabase();
  await supabase.install(page);
  await signIn(page, "freunde");
  await page.getByRole("button", { name: "Einladungs-Link erstellen" }).click();
  await expect(page.getByLabel(/Dein Einladungs-Link/)).toHaveValue(`${APP}einladung/${INVITE_TOKEN}`);
  await page.getByLabel("Google-E-Mail-Adresse").fill("anna@example.org");
  await page.getByRole("button", { name: "Anfrage senden" }).click();
  await expect(page.getByText("Wenn es zu dieser Adresse ein Konto gibt, bekommt die Person deine Anfrage.")).toBeVisible();
  expect(supabase.calls("POST", "/rest/v1/rpc/send_friend_request")[0].body).toEqual({ email: "anna@example.org" });
});

test("Einstellungen: Konto, Abmelden und Konto löschen", async ({ page }) => {
  const supabase = new FakeSupabase();
  await supabase.install(page);
  await signIn(page, "einstellungen");
  await expect(page.getByText("Angemeldet als")).toContainText("Mia Muster");
  await page.getByRole("button", { name: "Konto löschen" }).click();
  const dialog = page.getByRole("dialog", { name: "Konto wirklich löschen?" });
  await dialog.getByRole("button", { name: "Konto endgültig löschen" }).click();
  await expect(page.getByRole("button", { name: "Mit Google anmelden" })).toBeVisible();
  expect(supabase.calls("POST", "/functions/v1/delete-account")).toHaveLength(1);
});

test("Supabase-Aufrufe gehen nur an das konfigurierte Projekt", async ({ page }) => {
  const foreign: string[] = [];
  page.on("request", (request) => {
    const url = request.url();
    if (!url.startsWith("http://127.0.0.1:4173/") && !url.startsWith(SUPABASE) && !url.startsWith("data:") && !url.startsWith("blob:")) foreign.push(url);
  });
  const supabase = new FakeSupabase();
  await supabase.install(page);
  await setUpBar(page);
  await signIn(page, "freunde");
  await expect(page.getByText("Anna trinkt gerade")).toBeVisible();
  expect(foreign).toEqual([]);
  expect(ANNA).toBeTruthy();
});
