/**
 * Tests der Logik von notify-friends mit Attrappen: nur Freunde als Empfänger, Nutzlast je Empfänger
 * (inkl. recipientId), 404/410 löscht Abos, 10-Minuten- und notified_at-Sperre, Limit 60 pro Stunde.
 */

import { assertEquals } from "@std/assert";
import type { PushResult } from "../_shared/webpush/sender.ts";
import { buildDrinkPushPayload } from "../notify-friends/messages.ts";
import {
  mapWithConcurrency,
  NOTIFY_MAX_EVENT_AGE_MS,
  NOTIFY_RATE_LIMIT,
  NotifyFriendsService,
  PUSH_TTL_SECONDS,
} from "../notify-friends/service.ts";
import type { StoredSubscription } from "../notify-friends/ports.ts";
import {
  ANNA,
  BEN,
  CLARA,
  DORA,
  EVENT_ID,
  FakeDatabase,
  FakePushSender,
  FakeRateLimiter,
  FixedClock,
  RecordingLogger,
} from "./helpers/fakes.ts";

function subscription(id: string, userId: string): StoredSubscription {
  return {
    id,
    userId,
    endpoint: `https://ntfy.example.org/up-${id}`,
    p256dh: "B".padEnd(87, "A"),
    auth: "b".repeat(22),
  };
}

/** Anna ist mit Ben und Clara befreundet, Dora ist fremd. Jeder hat Abos, Ben sogar zwei. */
function setup(options: { eventAgeMs?: number } = {}) {
  const clock = new FixedClock();
  const db = new FakeDatabase(clock);
  db.profiles.set(ANNA, "Anna");
  db.profiles.set(BEN, "Ben");
  db.friendships.push([ANNA, BEN], [CLARA, ANNA], [BEN, DORA]);
  db.subscriptions = [
    subscription("sub-anna", ANNA),
    subscription("sub-ben-1", BEN),
    subscription("sub-ben-2", BEN),
    subscription("sub-clara", CLARA),
    subscription("sub-dora", DORA),
  ];
  db.events.set(EVENT_ID, {
    id: EVENT_ID,
    authorId: ANNA,
    name: "Apfelschorle",
    volumeMl: 500,
    createdAt: new Date(clock.now().getTime() - (options.eventAgeMs ?? 30_000)),
    notifiedAt: null,
  });
  const rateLimiter = new FakeRateLimiter();
  const push = new FakePushSender();
  const logger = new RecordingLogger();
  const service = new NotifyFriendsService({
    rateLimiter,
    events: db,
    friendships: db,
    profiles: db,
    subscriptions: db,
    push,
    clock,
    logger,
  });
  return { clock, db, rateLimiter, push, logger, service };
}

Deno.test("Empfänger sind genau die Freunde des Autors – nicht der Autor selbst, keine Fremden", async () => {
  const { service, push, db } = setup();
  const outcome = await service.notify(ANNA, EVENT_ID);

  assertEquals(outcome, { kind: "sent", recipients: 3, delivered: 3, failed: 0, removed: 0 });
  assertEquals(push.sent.map((s) => s.target.endpoint).sort(), [
    "https://ntfy.example.org/up-sub-ben-1",
    "https://ntfy.example.org/up-sub-ben-2",
    "https://ntfy.example.org/up-sub-clara",
  ]);
  assertEquals(db.requestedSubscriptionUsers.map((ids) => [...ids].sort()), [[BEN, CLARA].sort()]);
});

Deno.test("Nutzlast je Empfänger: type, eventId, authorId, recipientId, Titel und Text; TTL 1 Tag", async () => {
  const { service, push } = setup();
  await service.notify(ANNA, EVENT_ID);

  for (const message of push.sent) {
    const recipientId = message.target.endpoint.includes("clara") ? CLARA : BEN;
    assertEquals(message.payload, {
      type: "drink",
      eventId: EVENT_ID,
      authorId: ANNA,
      recipientId,
      title: "Anna trinkt gerade",
      body: "Apfelschorle (500 ml)",
    });
    assertEquals(message.options.ttlSeconds, PUSH_TTL_SECONDS);
    assertEquals(PUSH_TTL_SECONDS, 86400);
  }
});

Deno.test("404 und 410 löschen das Abo, andere Fehler nicht; Ausnahmen stoppen den Versand nicht", async () => {
  const { service, push, db, logger } = setup();
  push.results.set("https://ntfy.example.org/up-sub-ben-1", { kind: "gone", status: 410 } satisfies PushResult);
  push.results.set("https://ntfy.example.org/up-sub-ben-2", { kind: "failed", status: 500, reason: "http_500" });
  push.results.set("https://ntfy.example.org/up-sub-clara", { kind: "gone", status: 404 });
  const outcome = await service.notify(ANNA, EVENT_ID);

  assertEquals(outcome, { kind: "sent", recipients: 3, delivered: 0, failed: 1, removed: 2 });
  assertEquals(db.removed.sort(), ["sub-ben-1", "sub-clara"]);
  assertEquals(db.subscriptions.map((s) => s.id).sort(), ["sub-anna", "sub-ben-2", "sub-dora"]);
  assertEquals(
    logger.entries.some((e) => JSON.stringify(e).includes("ntfy.example.org/up-")),
    false,
    "Vollständige Endpoints dürfen nicht im Protokoll landen",
  );
});

Deno.test("Unbrauchbare Abos (invalid) werden gelöscht; geworfene Fehler zählen als fehlgeschlagen", async () => {
  const { service, push, db } = setup();
  push.results.set("https://ntfy.example.org/up-sub-ben-1", { kind: "invalid", reason: "receiver_keys_invalid" });
  push.results.set("https://ntfy.example.org/up-sub-ben-2", new Error("Zeitüberschreitung"));
  const outcome = await service.notify(ANNA, EVENT_ID);

  assertEquals(outcome, { kind: "sent", recipients: 3, delivered: 1, failed: 1, removed: 1 });
  assertEquals(db.removed, ["sub-ben-1"]);
});

Deno.test("10-Minuten-Grenze: zu altes Ereignis löst nichts aus", async () => {
  const { service, push, db } = setup({ eventAgeMs: NOTIFY_MAX_EVENT_AGE_MS + 1000 });
  assertEquals(await service.notify(ANNA, EVENT_ID), { kind: "event_not_found" });
  assertEquals(push.sent.length, 0);
  assertEquals(db.events.get(EVENT_ID)?.notifiedAt, null);
});

Deno.test("10-Minuten-Grenze: knapp darunter wird verschickt", async () => {
  const { service, push } = setup({ eventAgeMs: NOTIFY_MAX_EVENT_AGE_MS - 1000 });
  assertEquals((await service.notify(ANNA, EVENT_ID)).kind, "sent");
  assertEquals(push.sent.length, 3);
});

Deno.test("notified_at-Sperre: zweiter Aufruf für dasselbe Ereignis verschickt nichts mehr", async () => {
  const { service, push, db, clock } = setup();
  await service.notify(ANNA, EVENT_ID);
  assertEquals(db.events.get(EVENT_ID)?.notifiedAt, clock.now());
  assertEquals(await service.notify(ANNA, EVENT_ID), { kind: "event_not_found" });
  assertEquals(push.sent.length, 3);
});

Deno.test("Fremde Ereignisse: Ben kann für Annas Ereignis keine Benachrichtigung auslösen", async () => {
  const { service, push, db } = setup();
  assertEquals(await service.notify(BEN, EVENT_ID), { kind: "event_not_found" });
  assertEquals(push.sent.length, 0);
  assertEquals(db.events.get(EVENT_ID)?.notifiedAt, null);
});

Deno.test("Unbekanntes Ereignis: event_not_found", async () => {
  const { service } = setup();
  assertEquals(await service.notify(ANNA, "99999999-9999-4999-8999-999999999999"), { kind: "event_not_found" });
});

Deno.test("Limit: 60 Aufrufe pro Stunde, der 61. wird vor jeder weiteren Arbeit abgelehnt", async () => {
  const { service, rateLimiter, push, db } = setup();
  for (let i = 0; i < 60; i++) {
    await service.notify(ANNA, "99999999-9999-4999-8999-999999999999");
  }
  assertEquals(await service.notify(ANNA, EVENT_ID), { kind: "rate_limited" });
  assertEquals(push.sent.length, 0);
  assertEquals(db.events.get(EVENT_ID)?.notifiedAt, null, "Ereignis bleibt unberührt");
  assertEquals(rateLimiter.calls[0], { userId: ANNA, action: "notify_friends", maxCount: 60, windowSeconds: 3600 });
  assertEquals(NOTIFY_RATE_LIMIT, { action: "notify_friends", maxCount: 60, windowSeconds: 3600 });
  assertEquals((await service.notify(BEN, EVENT_ID)).kind, "event_not_found", "Das Limit gilt pro Nutzer");
});

Deno.test("Ohne Freunde: nichts zu senden, Ereignis trotzdem als benachrichtigt markiert", async () => {
  const { service, push, db } = setup();
  db.friendships.length = 0;
  assertEquals(await service.notify(ANNA, EVENT_ID), {
    kind: "sent",
    recipients: 0,
    delivered: 0,
    failed: 0,
    removed: 0,
  });
  assertEquals(push.sent.length, 0);
  assertEquals(db.requestedSubscriptionUsers.length, 0);
  assertEquals(db.events.get(EVENT_ID)?.notifiedAt !== null, true);
});

Deno.test("Abwehr: liefert ein Adapter den Autor oder Fremde mit, bekommen sie trotzdem nichts", async () => {
  const { service, push, db } = setup();
  db.friendships.push([ANNA, ANNA]);
  db.subscriptionsOf = (_ids) => Promise.resolve([...db.subscriptions]); // liefert absichtlich alle Abos
  await service.notify(ANNA, EVENT_ID);
  assertEquals(
    push.sent.map((s) => (s.payload as { recipientId: string }).recipientId).sort(),
    [BEN, BEN, CLARA].sort(),
  );
});

Deno.test("Fehlt das Profil des Autors, heißt es „Jemand trinkt gerade“", async () => {
  const { service, push, db } = setup();
  db.profiles.delete(ANNA);
  await service.notify(ANNA, EVENT_ID);
  assertEquals((push.sent[0].payload as { title: string }).title, "Jemand trinkt gerade");
});

Deno.test("buildDrinkPushPayload: Texte wie im Vertrag", () => {
  assertEquals(
    buildDrinkPushPayload({
      eventId: EVENT_ID,
      authorId: ANNA,
      authorName: " Anna ",
      recipientId: BEN,
      drinkName: "Apfelschorle",
      volumeMl: 500,
    }),
    {
      type: "drink",
      eventId: EVENT_ID,
      authorId: ANNA,
      recipientId: BEN,
      title: "Anna trinkt gerade",
      body: "Apfelschorle (500 ml)",
    },
  );
});

Deno.test("mapWithConcurrency: Reihenfolge bleibt, höchstens n gleichzeitig", async () => {
  let running = 0;
  let maxRunning = 0;
  const result = await mapWithConcurrency([1, 2, 3, 4, 5, 6, 7], 3, async (n) => {
    running++;
    maxRunning = Math.max(maxRunning, running);
    await new Promise((resolve) => setTimeout(resolve, 5 * (8 - n)));
    running--;
    return n * 10;
  });
  assertEquals(result, [10, 20, 30, 40, 50, 60, 70]);
  assertEquals(maxRunning, 3);
  assertEquals(await mapWithConcurrency([], 3, () => Promise.resolve(1)), []);
});
