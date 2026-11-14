import { describe, expect, it } from "vitest";
import { decidePush, eventUrl, parsePushPayload, planNotification } from "./pushMessages";

const SCOPE = "https://q0ochen.github.io/DrinkWithMe/";
const payload = {
  type: "drink",
  eventId: "6f1c2a4e-0000-4000-8000-000000000001",
  authorId: "author-1",
  recipientId: "me-1",
  title: "Anna trinkt gerade",
  body: "Apfelschorle (500 ml)",
};

describe("Push-Nachrichten im Service Worker", () => {
  it("liest den Inhalt von notify-friends", () => {
    expect(parsePushPayload(JSON.stringify(payload))).toEqual(payload);
    expect(parsePushPayload("kein json")).toBeNull();
    expect(parsePushPayload(null)).toBeNull();
    expect(parsePushPayload(JSON.stringify({ ...payload, type: "andere" }))).toBeNull();
    expect(parsePushPayload(JSON.stringify({ ...payload, recipientId: "" }))).toBeNull();
    expect(parsePushPayload(JSON.stringify({ ...payload, title: 5 }))).toBeNull();
  });

  it("zeigt nur Nachrichten für den angemeldeten Nutzer an", () => {
    const parsed = parsePushPayload(JSON.stringify(payload));
    expect(planNotification(parsed, "someone-else", SCOPE)).toBeNull();
    expect(planNotification(parsed, null, SCOPE)).toBeNull();
    expect(planNotification(null, "me-1", SCOPE)).toBeNull();
    const plan = planNotification(parsed, "me-1", SCOPE);
    expect(plan).toEqual({
      title: "Anna trinkt gerade",
      options: {
        body: "Apfelschorle (500 ml)",
        tag: `drink-${payload.eventId}`,
        icon: `${SCOPE}icons/app-192.png`,
        badge: `${SCOPE}icons/badge-96.png`,
        lang: "de-DE",
        data: { url: `${SCOPE}ereignis/${payload.eventId}`, eventId: payload.eventId },
      },
    });
  });

  it("bestellt nur nach ausdrücklicher Abmeldung ab – nie, nur weil keine Nutzer-ID gespeichert ist", () => {
    const parsed = parsePushPayload(JSON.stringify(payload));
    expect(decidePush(parsed, { kind: "signedIn", userId: "me-1" }, SCOPE)).toMatchObject({ kind: "show", plan: { title: "Anna trinkt gerade" } });
    expect(decidePush(parsed, { kind: "signedIn", userId: "someone-else" }, SCOPE)).toEqual({ kind: "ignore" });
    expect(decidePush(parsed, { kind: "unknown" }, SCOPE)).toEqual({ kind: "ignore" });
    expect(decidePush(parsed, { kind: "signedOut" }, SCOPE)).toEqual({ kind: "unsubscribe" });
    expect(decidePush(null, { kind: "signedOut" }, SCOPE)).toEqual({ kind: "ignore" });
  });

  it("Tipp öffnet /ereignis/<eventId> innerhalb der App", () => {
    expect(eventUrl(SCOPE, "abc/../x")).toBe(`${SCOPE}ereignis/abc%2F..%2Fx`);
  });
});
