// Gegenstück zu android/app/src/test/java/com/drinkwithme/app/publish/PendingSharesTest.kt: Teilen und Zurückziehen
// gehen ohne Verbindung nicht verloren, sondern werden vorgemerkt und später nachgeholt.
import { describe, expect, it } from "vitest";
import { InMemoryHistoryRepository, type DrinkEntry } from "../core/log/drinkEntry";
import { SharingPolicy, type SharingState } from "../core/sharing/sharingPolicy";
import type { EventPublisher } from "../core/social/ports";
import { catalogItem, entryOf, MutableClock } from "../core/testing/fixtures";
import { CatalogIds } from "../core/bar/defaultBarCatalog";
import { SocialError } from "../social/errors";
import { InMemoryStoredValue } from "../data/storedValue";
import { PendingShareQueue, RetryingEventPublisher, ShareRetryProcessor, type PendingShare } from "./pendingShares";

const HOUR = 60 * 60 * 1000;
const publish = (entryId: string): PendingShare => ({ op: "publish", entryId });
const unpublish = (entryId: string): PendingShare => ({ op: "unpublish", entryId });

class ScriptedPublisher implements EventPublisher {
  readonly calls: string[] = [];
  failure: unknown = null;

  async publish(entry: DrinkEntry): Promise<void> {
    if (this.failure) throw this.failure;
    this.calls.push(`publish:${entry.id}`);
  }

  async unpublish(entryId: string): Promise<void> {
    if (this.failure) throw this.failure;
    this.calls.push(`unpublish:${entryId}`);
  }
}

const water = catalogItem(CatalogIds.WASSER);
const shared = (id: string, at: number, share = true) => entryOf(water, at, { id, share });

describe("PendingShareQueue", () => {
  it("behält die Reihenfolge, verwirft Doppelte; Zurückziehen macht ein wartendes Teilen überflüssig", async () => {
    const queue = new PendingShareQueue(new InMemoryStoredValue());
    await queue.add(publish("a"));
    await queue.add(publish("b"));
    await queue.add(publish("a"));
    await queue.add(unpublish("b"));
    expect(await queue.snapshot()).toEqual([publish("a"), unpublish("b")]);
    await queue.remove(publish("a"));
    expect(await queue.snapshot()).toEqual([unpublish("b")]);
    await queue.clear();
    expect(await queue.snapshot()).toEqual([]);
  });

  it("übersteht einen Neustart und ignoriert Unbrauchbares; höchstens `capacity` Aufträge", async () => {
    const store = new InMemoryStoredValue();
    await new PendingShareQueue(store).add(unpublish("x"));
    expect(await new PendingShareQueue(store).snapshot()).toEqual([unpublish("x")]);
    await store.write([{ op: "zaubern", entryId: "y" }, { op: "publish" }, "kaputt", { op: "publish", entryId: "ok" }]);
    expect(await new PendingShareQueue(store).snapshot()).toEqual([publish("ok")]);
    await store.write("kein Array");
    expect(await new PendingShareQueue(store).snapshot()).toEqual([]);

    const small = new PendingShareQueue(new InMemoryStoredValue(), 2);
    for (const id of ["1", "2", "3"]) await small.add(publish(id));
    expect(await small.snapshot()).toEqual([publish("2"), publish("3")]);
  });

  it("gleichzeitige Aufträge gehen nicht verloren", async () => {
    const queue = new PendingShareQueue(new InMemoryStoredValue());
    await Promise.all(["a", "b", "c", "d"].map((id) => queue.add(publish(id))));
    expect((await queue.snapshot()).map((share) => share.entryId).sort()).toEqual(["a", "b", "c", "d"]);
  });
});

describe("RetryingEventPublisher", () => {
  it("Netzfehler werden vorgemerkt statt gemeldet", async () => {
    const queue = new PendingShareQueue(new InMemoryStoredValue());
    const delegate = new ScriptedPublisher();
    let queued = 0;
    const publisher = new RetryingEventPublisher(delegate, queue, () => queued++);
    delegate.failure = new SocialError("network");
    await publisher.publish(shared("a", 0));
    await publisher.unpublish("b");
    expect(await queue.snapshot()).toEqual([publish("a"), unpublish("b")]);
    expect(queued).toBe(2);
  });

  it("andere Fehler (abgelehnt, zu viele Ereignisse) gehen weiter und werden nicht wiederholt", async () => {
    const queue = new PendingShareQueue(new InMemoryStoredValue());
    const delegate = new ScriptedPublisher();
    const publisher = new RetryingEventPublisher(delegate, queue);
    delegate.failure = new SocialError("too_many_events");
    await expect(publisher.publish(shared("a", 0))).rejects.toMatchObject({ code: "too_many_events" });
    delegate.failure = new Error("unerwartet");
    await expect(publisher.unpublish("a")).rejects.toThrow("unerwartet");
    expect(await queue.snapshot()).toEqual([]);
  });

  it("Erfolg geht unverändert durch", async () => {
    const queue = new PendingShareQueue(new InMemoryStoredValue());
    const delegate = new ScriptedPublisher();
    await new RetryingEventPublisher(delegate, queue).publish(shared("a", 0));
    expect(delegate.calls).toEqual(["publish:a"]);
    expect(await queue.snapshot()).toEqual([]);
  });
});

describe("ShareRetryProcessor", () => {
  const now = Date.parse("2026-11-10T18:00:00Z");

  function setUp(initial: Partial<SharingState> = {}) {
    const clock = new MutableClock(now);
    const history = new InMemoryHistoryRepository();
    const queue = new PendingShareQueue(new InMemoryStoredValue());
    const publisher = new ScriptedPublisher();
    let state: SharingState = { isSignedIn: true, alcoholConsent: "UNKNOWN", ...initial };
    const processor = new ShareRetryProcessor(queue, history, new SharingPolicy({ current: () => state }), async () => publisher, clock);
    return { history, queue, publisher, processor, setState: (next: Partial<SharingState>) => (state = { ...state, ...next }) };
  }

  it("teilt noch geteilte, frische Einträge nach und zieht zurück; Altes, Privates und Gelöschtes entfällt", async () => {
    const { history, queue, publisher, processor } = setUp();
    await history.save(shared("fresh", now - 60_000));
    await history.save(shared("old", now - 30 * HOUR));
    await history.save(shared("private", now, false));
    for (const id of ["fresh", "old", "private", "deleted"]) await queue.add(publish(id));
    await queue.add(unpublish("gone"));

    expect(await processor.run()).toBe("done");
    expect(publisher.calls).toEqual(["publish:fresh", "unpublish:gone"]);
    expect(await queue.snapshot()).toEqual([]);
  });

  it("hört beim Netzfehler auf und behält den Rest", async () => {
    const { history, queue, publisher, processor } = setUp();
    await history.save(shared("a", now));
    await queue.add(publish("a"));
    await queue.add(unpublish("b"));
    publisher.failure = new SocialError("network");
    expect(await processor.run()).toBe("retryLater");
    expect(await queue.snapshot()).toHaveLength(2);
  });

  it("vom Server abgelehnte Aufträge werden verworfen (Wiederholen hilft nicht)", async () => {
    const { history, queue, publisher, processor } = setUp();
    await history.save(shared("a", now));
    await queue.add(publish("a"));
    publisher.failure = new SocialError("too_many_events");
    expect(await processor.run()).toBe("done");
    expect(await queue.snapshot()).toEqual([]);
  });

  it("abgemeldet: wartende Aufträge entfallen, ohne den Server zu fragen", async () => {
    const { history, queue, publisher, processor } = setUp({ isSignedIn: false });
    await history.save(shared("a", now));
    await queue.add(publish("a"));
    await queue.add(unpublish("b"));
    expect(await processor.run()).toBe("done");
    expect(publisher.calls).toEqual([]);
    expect(await queue.snapshot()).toEqual([]);
  });

  it("Server nicht erreichbar (Vernetzung lädt nicht): später erneut", async () => {
    const queue = new PendingShareQueue(new InMemoryStoredValue());
    const history = new InMemoryHistoryRepository([shared("a", now)]);
    await queue.add(publish("a"));
    const policy = new SharingPolicy({ current: () => ({ isSignedIn: true, alcoholConsent: "UNKNOWN" }) });
    const processor = new ShareRetryProcessor(queue, history, policy, async () => null, new MutableClock(now));
    expect(await processor.run()).toBe("retryLater");
    expect(await queue.snapshot()).toEqual([publish("a")]);
  });
});
