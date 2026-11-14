import { expect, test, type Page } from "@playwright/test";
import { EVENT_ID, FakeSupabase, INVITE_TOKEN, setUpBar, signIn } from "./support";

// Bildschirmfotos der Hauptbereiche zur Sichtprüfung (nur mit SCREENSHOT_DIR, z. B.
// `SCREENSHOT_DIR=/tmp/fotos npx playwright test screenshots`). In der normalen Testsuite übersprungen.
const directory = process.env.SCREENSHOT_DIR;
test.skip(!directory, "Nur für Bildschirmfotos (SCREENSHOT_DIR setzen).");

/** Füllt den Verlauf mit 8 Wochen realistischer Einträge (direkt in IndexedDB) und lädt neu. */
async function seedHistory(page: Page): Promise<void> {
  await page.evaluate(async () => {
    const drinks = [
      { name: "Wasser", icon: "water", category: "WATER", ml: 250, alcohol: 0, sugar: 0 },
      { name: "Kaffee", icon: "coffee", category: "COFFEE", ml: 200, alcohol: 0, sugar: 0 },
      { name: "Apfelschorle", icon: "apple_spritzer", category: "JUICE", ml: 500, alcohol: 0, sugar: 5.5 },
      { name: "Cola", icon: "cola", category: "SOFT_DRINK", ml: 330, alcohol: 0, sugar: 10.6 },
      { name: "Pils", icon: "beer", category: "BEER", ml: 500, alcohol: 4.9, sugar: 0.3 },
      { name: "Rotwein trocken", icon: "red_wine", category: "WINE", ml: 125, alcohol: 12, sugar: 0.3 },
    ];
    const db = await new Promise<IDBDatabase>((resolve, reject) => {
      const request = indexedDB.open("drink-with-me");
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
    const tx = db.transaction("history", "readwrite");
    const store = tx.objectStore("history");
    let seed = 7;
    const random = () => {
      seed = (seed * 16807) % 2147483647;
      return seed / 2147483647;
    };
    const now = Date.now();
    for (let day = 70; day >= 1; day--) {
      const count = 2 + Math.floor(random() * 4);
      for (let index = 0; index < count; index++) {
        const pick = random();
        const weekend = new Date(now - day * 86_400_000).getDay() % 6 === 0;
        const drink = pick < 0.3 ? drinks[0] : pick < 0.5 ? drinks[1] : pick < 0.68 ? drinks[2] : pick < 0.82 ? drinks[3] : weekend || pick > 0.93 ? drinks[4 + Math.floor(random() * 2)] : drinks[0];
        const at = now - day * 86_400_000 + (8 + index * 3) * 3_600_000 - 12 * 3_600_000;
        store.put({
          id: crypto.randomUUID(),
          barItemId: null,
          name: drink.name,
          icon: drink.icon,
          category: drink.category,
          volumeMilliliters: drink.ml,
          alcoholPercent: drink.alcohol,
          sugarGramsPer100Ml: drink.sugar,
          consumedAt: at,
          shareWithFriends: false,
        });
      }
    }
    await new Promise((resolve) => (tx.oncomplete = resolve));
    db.close();
  });
  await page.reload();
}

async function shot(page: Page, name: string, fullPage = true): Promise<void> {
  await page.waitForTimeout(250);
  await page.screenshot({ path: `${directory}/${test.info().project.name}-${name}.png`, fullPage });
}

test("Hauptbereiche", async ({ page }) => {
  const supabase = new FakeSupabase();
  await supabase.install(page);
  await page.goto("");
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  await shot(page, "01-willkommen");
  await setUpBar(page);
  await seedHistory(page);
  await page.getByRole("button", { name: /^Apfelschorle/ }).click();
  await shot(page, "02-bar-rueckgaengig", false);
  await page.goto("verlauf");
  await expect(page.getByRole("heading", { level: 1, name: "Verlauf" })).toBeVisible();
  await shot(page, "03-verlauf");
  await page.goto("bar/bearbeiten");
  await shot(page, "04-bar-bearbeiten");
  await page.goto("freunde");
  await shot(page, "05-freunde-abgemeldet");
  await signIn(page, "freunde");
  await expect(page.getByText("Anna trinkt gerade")).toBeVisible();
  await shot(page, "06-freunde");
  await page.goto(`ereignis/${EVENT_ID}`);
  await expect(page.getByText("Anna trinkt eine Apfelschorle.")).toBeVisible();
  await shot(page, "07-ereignis");
  await page.goto("einstellungen");
  await expect(page.getByText("Angemeldet als")).toBeVisible();
  await shot(page, "08-einstellungen");
  await page.goto(`einladung/${INVITE_TOKEN}`);
  await shot(page, "09-einladung");
  await page.goto("verlauf");
  await page.getByRole("button", { name: "Als Bild teilen" }).click();
  await expect(page.getByRole("dialog", { name: "Bild teilen" }).getByRole("img")).toBeVisible();
  await shot(page, "10-bild-teilen", false);
  await page.getByRole("dialog", { name: "Bild teilen" }).getByRole("img").screenshot({ path: `${directory}/${test.info().project.name}-11-bild.png` });
});

test("Dunkelmodus und 200 % Schrift", async ({ page }) => {
  await page.emulateMedia({ colorScheme: "dark" });
  await setUpBar(page);
  await seedHistory(page);
  await shot(page, "20-bar-dunkel");
  await page.goto("verlauf");
  await shot(page, "21-verlauf-dunkel");
  await page.emulateMedia({ colorScheme: "light" });
  await page.goto("");
  await page.evaluate(() => {
    document.documentElement.style.fontSize = "212.5%";
  });
  await shot(page, "22-bar-200-prozent", false);
  await page.goto("verlauf");
  await page.evaluate(() => {
    document.documentElement.style.fontSize = "212.5%";
  });
  await shot(page, "23-verlauf-200-prozent", false);
});
