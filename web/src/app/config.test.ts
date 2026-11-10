import { describe, expect, it } from "vitest";
import { detectPlatform, needsHomeScreenForPush } from "../platform/platform";
import { basePathFromWebAppUrl } from "../../scripts/base-path.mjs";
import { authRedirectUrl, DEFAULT_WEB_APP_URL, readConfig } from "./config";

const VAPID = "BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkvMeAtA3LFgDzkrxZJjSgSnfckjBJuBkr3qBUYIHBQFLXYp5Nksh8U";

describe("Konfiguration", () => {
  it("ohne Supabase-Werte: lokal, Server noch nicht eingerichtet", () => {
    const config = readConfig({ BASE_URL: "/DrinkWithMe/" });
    expect(config.supabase).toBeNull();
    expect(config.vapidPublicKey).toBeNull();
    expect(config.webAppUrl).toBe(DEFAULT_WEB_APP_URL);
    expect(config.basePath).toBe("/DrinkWithMe/");
    expect(authRedirectUrl(config)).toBe("https://q0ochen.github.io/DrinkWithMe/auth/callback");
  });

  it("mit Werten aus der CI (Repository-Variablen)", () => {
    const config = readConfig({
      VITE_SUPABASE_URL: " https://abcdefghijklmnopqrst.supabase.co/ ",
      VITE_SUPABASE_ANON_KEY: "sb_publishable_123",
      VITE_VAPID_PUBLIC_KEY: VAPID,
      VITE_WEB_APP_URL: "https://example.github.io/Fork/",
      BASE_URL: "/Fork/",
      VITE_APP_VERSION: "2.0.0",
    });
    expect(config.supabase).toEqual({ url: "https://abcdefghijklmnopqrst.supabase.co", anonKey: "sb_publishable_123" });
    expect(config.vapidPublicKey).toBe(VAPID);
    expect(authRedirectUrl(config)).toBe("https://example.github.io/Fork/auth/callback");
    expect(config.version).toBe("2.0.0");
  });

  it("verwirft unsichere oder kaputte Werte", () => {
    expect(readConfig({ VITE_SUPABASE_URL: "http://example.org", VITE_SUPABASE_ANON_KEY: "x" }).supabase).toBeNull();
    expect(readConfig({ VITE_SUPABASE_URL: "https://x.supabase.co", VITE_SUPABASE_ANON_KEY: " " }).supabase).toBeNull();
    expect(readConfig({ VITE_SUPABASE_URL: "http://127.0.0.1:54321", VITE_SUPABASE_ANON_KEY: "lokal" }).supabase).not.toBeNull();
    expect(readConfig({ VITE_VAPID_PUBLIC_KEY: "zu kurz" }).vapidPublicKey).toBeNull();
    expect(readConfig({ VITE_WEB_APP_URL: "ftp://x" }).webAppUrl).toBe(DEFAULT_WEB_APP_URL);
  });

  it("Vite-base folgt der Web-App-Adresse", () => {
    expect(basePathFromWebAppUrl("https://q0ochen.github.io/DrinkWithMe")).toBe("/DrinkWithMe/");
    expect(basePathFromWebAppUrl("https://q0ochen.github.io/DrinkWithMe/")).toBe("/DrinkWithMe/");
    expect(basePathFromWebAppUrl("https://drink.example")).toBe("/");
    expect(() => basePathFromWebAppUrl("kaputt")).toThrow(/VITE_WEB_APP_URL/);
  });
});

describe("Plattform-Erkennung", () => {
  const iphone = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1";
  const android = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36";

  it("iPhone im Browser braucht den Home-Bildschirm für Push", () => {
    const platform = detectPlatform({ userAgent: iphone }, false);
    expect(platform).toEqual({ isIos: true, isAndroid: false, isStandalone: false });
    expect(needsHomeScreenForPush(platform)).toBe(true);
    expect(needsHomeScreenForPush(detectPlatform({ userAgent: iphone, standalone: true }, false))).toBe(false);
  });

  it("iPadOS mit Desktop-Kennung und Android", () => {
    expect(detectPlatform({ userAgent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)", platform: "MacIntel", maxTouchPoints: 5 }, false).isIos).toBe(true);
    expect(detectPlatform({ userAgent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)", platform: "MacIntel", maxTouchPoints: 0 }, false).isIos).toBe(false);
    const platform = detectPlatform({ userAgent: android }, true);
    expect(platform).toEqual({ isIos: false, isAndroid: true, isStandalone: true });
    expect(needsHomeScreenForPush(platform)).toBe(false);
  });
});
