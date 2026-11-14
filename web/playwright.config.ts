import { defineConfig, devices } from "@playwright/test";

/**
 * Smoke-Tests gegen die gebaute App (`vite preview`). Zwei Builds:
 * - Port 4173: mit (erfundener) Supabase-Konfiguration – alle Server-Aufrufe fangen die Tests mit page.route ab.
 * - Port 4174: ohne Konfiguration („Server noch nicht eingerichtet“).
 */
export default defineConfig({
  testDir: "e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["list"], ["html", { open: "never" }]] : "list",
  timeout: 30_000,
  use: {
    baseURL: "http://127.0.0.1:4173/DrinkWithMe/",
    locale: "de-DE",
    timezoneId: "Europe/Berlin",
    serviceWorkers: "block",
    trace: "retain-on-failure",
  },
  projects: [
    // iPhone-Größe (Zielgerät der Web-App), gerendert mit Chromium (WebKit ist in der CI optional).
    { name: "handy", use: { ...devices["iPhone 13"], browserName: "chromium" } },
    { name: "desktop", use: { ...devices["Desktop Chrome"], viewport: { width: 1280, height: 860 } } },
  ],
  webServer: [
    {
      command: "npx vite build --mode e2e --outDir dist-e2e --logLevel warn && npx vite preview --mode e2e --outDir dist-e2e --host 127.0.0.1 --port 4173 --strictPort",
      url: "http://127.0.0.1:4173/DrinkWithMe/",
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
    },
    {
      command:
        "npx vite build --mode e2e-ohne-server --outDir dist-e2e-ohne-server --logLevel warn && npx vite preview --mode e2e-ohne-server --outDir dist-e2e-ohne-server --host 127.0.0.1 --port 4174 --strictPort",
      url: "http://127.0.0.1:4174/DrinkWithMe/",
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
    },
  ],
});
