import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import { APP_WITHOUT_SERVER, recordSpeech, setUpBar, spoken } from "./support";

// Alles, was ohne Anmeldung und ohne Server funktioniert (lokal zuerst).

test("Erster Start → Getränk eintragen → Rückgängig → Diagramm-Texte im Verlauf", async ({ page }) => {
  await setUpBar(page);
  const tiles = page.getByRole("list", { name: "Getränke in deiner Bar" }).getByRole("button");
  await expect(tiles).toHaveCount(7);
  await expect(tiles.first()).toContainText("Wasser");

  // Ein Tipp = getrunken, mit 10-Sekunden-Rückgängig.
  await page.getByRole("button", { name: /^Apfelschorle/ }).click();
  const snackbar = page.getByRole("region", { name: "Zuletzt eingetragen" });
  await expect(snackbar).toContainText("Apfelschorle eingetragen.");
  await expect(page.getByRole("status").filter({ hasText: "Apfelschorle eingetragen." })).toHaveCount(1);
  await snackbar.getByRole("button", { name: /Rückgängig/ }).click();
  await expect(snackbar).toBeHidden();
  await expect(page.getByText("Apfelschorle wieder entfernt.")).toBeAttached();
  await expect(page.getByText(/Heute:\s*0 Getränke/)).toBeVisible();

  // Eintrag bleibt: Cola (330 ml × 10,6 g/100 ml = 34,98 g Zucker).
  await page.getByRole("button", { name: /^Cola/ }).click();
  await expect(page.getByText(/Heute:\s*1 Getränk\b/)).toBeVisible();

  await page.getByRole("navigation", { name: "Hauptbereiche" }).getByRole("link", { name: "Verlauf" }).click();
  await expect(page.getByRole("heading", { level: 1, name: "Verlauf" })).toBeVisible();
  await expect(page.locator(".figure").filter({ hasText: "Zucker aus Getränken" })).toContainText("35 g");
  await expect(page.locator("figcaption").filter({ hasText: "Zucker aus Getränken pro Tag (letzte 14 Tage)" })).toBeVisible();
  await expect(page.getByRole("img", { name: /Zucker aus Getränken pro Tag/ })).toBeVisible();
  await expect(page.getByText("Obergrenze für freien Zucker in der gesamten Ernährung (bei 2000 kcal)")).toBeVisible();
  await expect(page.getByText("WHO: unter 25 g bringt zusätzlichen Nutzen")).toBeVisible();
  await expect(page.getByText("Getränke sind nur ein Teil davon.")).toBeVisible();
  await expect(page.locator("figcaption").filter({ hasText: "Alkohol pro Woche (letzte 8 Wochen, Montag bis Sonntag)" })).toBeVisible();
  await expect(page.getByText("Es gibt keine unbedenkliche Menge Alkohol. Weniger ist besser.").first()).toBeVisible();
  await expect(page.getByText(/risikoarm/).first()).toBeVisible();

  // Werte als Tabelle (Screenreader, große Schrift).
  const sugarTable = page.locator("details.chart-table").first();
  await sugarTable.locator("summary").click();
  await expect(sugarTable.getByRole("row", { name: /\(heute\)/ })).toContainText("35 g");

  // Liste der Einträge und Hinweis.
  await expect(page.getByRole("heading", { level: 3, name: /^Heute/ })).toBeVisible();
  await expect(page.locator(".entry-row")).toHaveCount(1);
  await expect(page.getByText(/persönliches Getränketagebuch/).first()).toBeVisible();
});

test("Daten bleiben nach dem Neuladen erhalten (IndexedDB)", async ({ page }) => {
  await setUpBar(page);
  await page.getByRole("button", { name: /^Wasser/ }).click();
  // Erst nach der Bestätigung ist der Eintrag sicher gespeichert (IndexedDB-Transaktion abgeschlossen).
  await expect(page.getByRole("region", { name: "Zuletzt eingetragen" })).toContainText("Wasser eingetragen.");
  await page.reload();
  await expect(page.getByRole("heading", { level: 1, name: "Bar" })).toBeVisible();
  await expect(page.getByText(/Heute:\s*1 Getränk\b/)).toBeVisible();
  const local = await page.evaluate(() => Object.keys(localStorage));
  expect(local).toEqual([]);
});

test("Ansage nach dem Eintragen und Vorlesen der Tageszusammenfassung", async ({ page }) => {
  await recordSpeech(page);
  await setUpBar(page);
  await page.getByRole("navigation", { name: "Hauptbereiche" }).getByRole("link", { name: "Einstellungen" }).click();
  await page.getByRole("checkbox", { name: /Nach dem Eintragen laut bestätigen/ }).check();
  await page.getByRole("navigation", { name: "Hauptbereiche" }).getByRole("link", { name: "Bar" }).click();
  await page.getByRole("button", { name: /^Kaffee/ }).click();
  await expect.poll(() => spoken(page)).toContain("Kaffee eingetragen.");
  await page.getByRole("navigation", { name: "Hauptbereiche" }).getByRole("link", { name: "Verlauf" }).click();
  await page.getByRole("button", { name: "Heute vorlesen" }).click();
  await expect.poll(() => spoken(page)).toContain("Heute aus Getränken: kein Zucker. Insgesamt zweihundert Milliliter.");
});

test("Bedienung nur mit der Tastatur", async ({ page }) => {
  await setUpBar(page);
  const water = page.getByRole("button", { name: /^Wasser/ });
  await water.focus();
  await page.keyboard.press("Enter");
  await expect(page.getByRole("region", { name: "Zuletzt eingetragen" })).toContainText("Wasser eingetragen.");
  await page.keyboard.press("Tab");
  await expect(page.getByRole("button", { name: /^Kaffee/ })).toBeFocused();
});

test("Als Bild teilen: Vorschau als PNG", async ({ page }) => {
  await setUpBar(page);
  await page.getByRole("button", { name: /^Pils/ }).click();
  await page.goto("verlauf");
  await page.getByRole("button", { name: "Als Bild teilen" }).click();
  const dialog = page.getByRole("dialog", { name: "Bild teilen" });
  await expect(dialog).toBeVisible();
  const image = dialog.getByRole("img", { name: /Vorschau/ });
  await expect(image).toBeVisible();
  expect(await image.evaluate((element: HTMLImageElement) => [element.naturalWidth, element.naturalHeight])).toEqual([1080, 1350]);
  await expect(dialog.getByRole("button", { name: "Bild speichern" })).toBeVisible();
});

test("Bar bearbeiten: eigenes Getränk mit Prüfung der Eingaben", async ({ page }) => {
  await setUpBar(page);
  await page.getByRole("link", { name: "Bar bearbeiten" }).click();
  await page.getByRole("button", { name: "Eigenes Getränk" }).click();
  const dialog = page.getByRole("dialog", { name: "Eigenes Getränk" });
  await dialog.getByRole("button", { name: "Speichern" }).click();
  await expect(dialog.getByText("Bitte gib einen Namen ein.")).toBeVisible();
  await dialog.getByLabel("Name", { exact: true }).fill("Kombucha");
  await dialog.getByLabel("Portion in ml").fill("330");
  await dialog.getByLabel("Zucker in g pro 100 ml").fill("4,5");
  await dialog.getByRole("button", { name: "Speichern" }).click();
  await expect(dialog).toBeHidden();
  await expect(page.locator(".edit-name").filter({ hasText: "Kombucha" })).toBeVisible();
  await page.getByRole("button", { name: "Kombucha nach oben" }).click();
  await page.getByRole("link", { name: "Fertig" }).click();
  await expect(page.getByRole("button", { name: /^Kombucha/ })).toBeVisible();
});

test("Freunde ohne Server: „Server noch nicht eingerichtet“, alles andere geht", async ({ page }) => {
  await page.goto(`${APP_WITHOUT_SERVER}freunde`);
  await expect(page.getByRole("heading", { level: 1, name: "Freunde" })).toBeVisible();
  await expect(page.getByText("Server noch nicht eingerichtet.")).toBeVisible();
  await expect(page.getByRole("button", { name: "Mit Google anmelden" })).toHaveCount(0);
  await setUpBar(page, APP_WITHOUT_SERVER);
  await page.getByRole("button", { name: /^Tee/ }).click();
  await expect(page.getByText("Tee eingetragen.").first()).toBeVisible();
});

test("Barrierefreiheit (axe): Bar, Verlauf, Freunde, Einstellungen ohne Verstöße", async ({ page }) => {
  await setUpBar(page);
  await page.getByRole("button", { name: /^Cola/ }).click();
  for (const path of ["", "verlauf", "freunde", "einstellungen", "bar/bearbeiten"]) {
    await page.goto(path);
    await expect(page.locator("h1")).toBeVisible();
    const results = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"]).analyze();
    expect(results.violations.map((violation) => `${path}: ${violation.id} – ${violation.help}`)).toEqual([]);
  }
});

test("Ziele mindestens 48 px; bei 200 % Schriftgröße kein waagerechtes Scrollen", async ({ page }) => {
  await setUpBar(page);
  await page.getByRole("button", { name: /^Cola/ }).click();
  for (const path of ["", "verlauf", "freunde", "einstellungen", "bar/bearbeiten"]) {
    await page.goto(path);
    await expect(page.locator("h1")).toBeVisible();
    // Bedienelemente (Knöpfe, Links als Knöpfe, Navigation) sind mindestens 48 × 48 px groß.
    const small = await page.evaluate(() =>
      [...document.querySelectorAll<HTMLElement>("main button, main a.button, .nav-link, main summary, .switch")]
        .filter((element) => element.offsetParent !== null)
        .map((element) => ({ label: element.textContent?.trim() || element.getAttribute("aria-label"), box: element.getBoundingClientRect() }))
        .filter(({ box }) => box.height < 47.5 || box.width < 47.5)
        .map(({ label, box }) => `${label}: ${Math.round(box.width)}×${Math.round(box.height)}`),
    );
    expect(small, path).toEqual([]);
    // Schriftgröße verdoppeln (wie „Größerer Text“ 200 %): nichts darf seitlich herausragen.
    await page.evaluate(() => {
      document.documentElement.style.fontSize = "212.5%";
    });
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    expect(overflow, path).toBeLessThanOrEqual(1);
  }
});

test("Service Worker: Offline-Start und 404.html für App-Pfade (GitHub Pages)", async ({ browser }) => {
  const context = await browser.newContext({ serviceWorkers: "allow", baseURL: "http://127.0.0.1:4173/DrinkWithMe/" });
  const page = await context.newPage();
  const fallback = await page.request.get("404.html");
  expect(await fallback.text()).toContain('<div id="app">');
  await setUpBar(page);
  const scope = await page.evaluate(async () => (await navigator.serviceWorker.ready).scope);
  expect(scope).toBe("http://127.0.0.1:4173/DrinkWithMe/");
  await expect.poll(() => page.evaluate(() => navigator.serviceWorker.controller !== null)).toBe(true);
  await page.getByRole("button", { name: /^Wasser/ }).click();
  await expect(page.getByRole("region", { name: "Zuletzt eingetragen" })).toContainText("Wasser eingetragen.");
  await context.setOffline(true);
  await page.goto("verlauf");
  await expect(page.getByRole("heading", { level: 1, name: "Verlauf" })).toBeVisible();
  await expect(page.locator(".entry-row")).toHaveCount(1);
  await context.close();
});
