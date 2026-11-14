import { describe, expect, it } from "vitest";
import { hrefOf, parseRoute, pathOf, sectionOf, type Route } from "./router";

const BASE = "/DrinkWithMe/";
const TOKEN = "AbCdEfGhIjKlMnOpQrStUvWxYz012345";
const EVENT = "6f1c2a4e-0000-4000-8000-000000000001";

describe("Routing unter /DrinkWithMe/ (GitHub Pages mit 404.html)", () => {
  it("erkennt alle Bereiche und Spezialseiten", () => {
    expect(parseRoute("/DrinkWithMe/", BASE)).toEqual({ name: "bar" });
    expect(parseRoute("/DrinkWithMe", BASE)).toEqual({ name: "bar" });
    expect(parseRoute("/DrinkWithMe/index.html", BASE)).toEqual({ name: "bar" });
    expect(parseRoute("/DrinkWithMe/verlauf", BASE)).toEqual({ name: "history" });
    expect(parseRoute("/DrinkWithMe/freunde/", BASE)).toEqual({ name: "friends" });
    expect(parseRoute("/DrinkWithMe/einstellungen", BASE)).toEqual({ name: "settings" });
    expect(parseRoute("/DrinkWithMe/bar/bearbeiten", BASE)).toEqual({ name: "barEdit" });
    expect(parseRoute("/DrinkWithMe/willkommen", BASE)).toEqual({ name: "onboarding" });
    expect(parseRoute("/DrinkWithMe/auth/callback", BASE)).toEqual({ name: "authCallback" });
    expect(parseRoute(`/DrinkWithMe/einladung/${TOKEN}`, BASE)).toEqual({ name: "invite", token: TOKEN });
    expect(parseRoute(`/DrinkWithMe/ereignis/${EVENT.toUpperCase()}`, BASE)).toEqual({ name: "event", eventId: EVENT });
  });

  it("lehnt Unbekanntes und ungültige Tokens ab", () => {
    expect(parseRoute("/DrinkWithMe/einladung/kurz", BASE)).toEqual({ name: "notFound" });
    expect(parseRoute("/DrinkWithMe/ereignis/123", BASE)).toEqual({ name: "notFound" });
    expect(parseRoute("/anderes/", BASE)).toEqual({ name: "notFound" });
    expect(parseRoute("/DrinkWithMe/%E0%A4%A", BASE)).toEqual({ name: "notFound" });
  });

  it("Pfade und Bereiche sind umkehrbar", () => {
    const routes: Route[] = [
      { name: "bar" },
      { name: "barEdit" },
      { name: "onboarding" },
      { name: "history" },
      { name: "friends" },
      { name: "settings" },
      { name: "invite", token: TOKEN },
      { name: "event", eventId: EVENT },
      { name: "authCallback" },
    ];
    for (const route of routes) expect(parseRoute(hrefOf(BASE, pathOf(route)), BASE)).toEqual(route);
    expect(sectionOf({ name: "invite", token: TOKEN })).toBe("friends");
    expect(sectionOf({ name: "barEdit" })).toBe("bar");
    expect(sectionOf({ name: "authCallback" })).toBeNull();
  });

  it("funktioniert auch mit Basis „/“ (eigene Domain)", () => {
    expect(parseRoute("/verlauf", "/")).toEqual({ name: "history" });
    expect(hrefOf("/", "/verlauf")).toBe("/verlauf");
  });
});
