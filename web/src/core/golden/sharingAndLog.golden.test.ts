// Golden-Tests: dieselben Eingaben und Erwartungen wie in den Kotlin-Tests SharingPolicyTest,
// SharingHistoryRepositoryTest, LogDrinkFromBarUseCaseTest, UndoAndDeleteTest und HistoryRepositoryTest.
import { describe, expect, it } from "vitest";
import { InvalidBarItemError } from "../bar/barItemValidation";
import { CatalogIds } from "../bar/defaultBarCatalog";
import {
  HISTORY_EARLIEST,
  HISTORY_LATEST,
  InMemoryHistoryRepository,
  type DrinkEntry,
  type ShareDecider,
} from "../log/drinkEntry";
import { DeleteEntryUseCase, LogDrinkFromBarUseCase, UndoLastEntryUseCase, UNDO_WINDOW_MILLIS } from "../log/logUseCases";
import { formatVolume, toSharedDrink } from "../sharing/sharedDrink";
import { SharingHistoryRepository } from "../sharing/sharingHistoryRepository";
import { SharingPolicy, SharingTexts, type AlcoholSharingConsent, type SharingState } from "../sharing/sharingPolicy";
import type { EventPublisher } from "../social/ports";
import { berlin, catalogItem, entry, entryOf, instant, MINUTE, MutableClock, SECOND, SequentialIds } from "../testing/fixtures";

const CONSENTS: AlcoholSharingConsent[] = ["UNKNOWN", "ALLOWED", "DENIED"];

describe("Kotlin-Kern: SharingPolicyTest", () => {
  const pils = catalogItem(CatalogIds.PILS);
  const alcoholFreeBeer = catalogItem(CatalogIds.ALKOHOLFREIES_BIER);
  const water = catalogItem(CatalogIds.WASSER);
  let state: SharingState;
  const policy = new SharingPolicy({ current: () => state });
  const reset = () => {
    state = { isSignedIn: true, alcoholConsent: "UNKNOWN" };
  };

  it("fullRuleMatrix", () => {
    for (const signedIn of [true, false]) {
      for (const markedPrivate of [true, false]) {
        for (const alcoholic of [true, false]) {
          for (const consent of CONSENTS) {
            const expected = signedIn && !markedPrivate && (!alcoholic || consent === "ALLOWED");
            expect(SharingPolicy.shouldShare({ isSignedIn: signedIn, alcoholConsent: consent }, alcoholic, markedPrivate)).toBe(expected);
          }
        }
      }
    }
  });

  it("explicitExamplesOfTheMatrix", () => {
    const allowed: SharingState = { isSignedIn: true, alcoholConsent: "ALLOWED" };
    expect(SharingPolicy.shouldShare(allowed, true, false)).toBe(true);
    expect(SharingPolicy.shouldShare({ isSignedIn: true, alcoholConsent: "DENIED" }, false, false)).toBe(true);
    expect(SharingPolicy.shouldShare({ isSignedIn: true, alcoholConsent: "UNKNOWN" }, true, false)).toBe(false);
    expect(SharingPolicy.shouldShare({ isSignedIn: true, alcoholConsent: "DENIED" }, true, false)).toBe(false);
    expect(SharingPolicy.shouldShare(allowed, false, true)).toBe(false);
    expect(SharingPolicy.shouldShare({ isSignedIn: false, alcoholConsent: "ALLOWED" }, false, false)).toBe(false);
  });

  it("shareOverrideFalseMarksTheEntryPrivate", () => {
    reset();
    expect(policy.shareWithFriends(water, null)).toBe(true);
    expect(policy.shareWithFriends(water, false)).toBe(false);
    expect(policy.shareWithFriends(water, true)).toBe(true);
  });

  it("shareOverrideTrueNeverReplacesConsentOrSignIn", () => {
    reset();
    expect(policy.shareWithFriends(pils, true)).toBe(false);
    state = { ...state, alcoholConsent: "DENIED" };
    expect(policy.shareWithFriends(pils, true)).toBe(false);
    state = { isSignedIn: false, alcoholConsent: "ALLOWED" };
    expect(policy.shareWithFriends(water, true)).toBe(false);
  });

  it("alcoholFreeBeerIsNotAlcoholicForSharing", () => {
    reset();
    expect(policy.shareWithFriends(alcoholFreeBeer, null)).toBe(true);
    expect(policy.shareWithFriends(pils, null)).toBe(false);
    state = { ...state, alcoholConsent: "ALLOWED" };
    expect(policy.shareWithFriends(pils, null)).toBe(true);
  });

  it("consentQuestionOnlyForTheFirstAlcoholicDrinkWhileSignedIn", () => {
    reset();
    expect(policy.needsAlcoholConsentQuestion(pils)).toBe(true);
    expect(policy.needsAlcoholConsentQuestion(water)).toBe(false);
    expect(policy.needsAlcoholConsentQuestion(alcoholFreeBeer)).toBe(false);
    state = { ...state, alcoholConsent: "DENIED" };
    expect(policy.needsAlcoholConsentQuestion(pils)).toBe(false);
    state = { isSignedIn: false, alcoholConsent: "UNKNOWN" };
    expect(policy.needsAlcoholConsentQuestion(pils)).toBe(false);
    expect(SharingTexts.ALCOHOL_CONSENT_QUESTION).toBe("Alkoholische Getränke mit Freunden teilen?");
  });

  it("publishingRechecksTheCurrentState", () => {
    const sharedBeer = entryOf(pils, 0, { share: true });
    const privateWater = entryOf(water, 0, { share: false });
    state = { isSignedIn: true, alcoholConsent: "ALLOWED" };
    expect(policy.shouldPublish(sharedBeer)).toBe(true);
    expect(policy.shouldPublish(privateWater)).toBe(false);
    state = { ...state, alcoholConsent: "DENIED" };
    expect(policy.shouldPublish(sharedBeer)).toBe(false);
    state = { isSignedIn: false, alcoholConsent: "ALLOWED" };
    expect(policy.shouldPublish(sharedBeer)).toBe(false);
    expect(policy.canUnpublish()).toBe(false);
  });

  it("sharedDrinkContainsOnlyTheAllowedFields", () => {
    const logged = entryOf(pils, instant("2026-11-10T18:00:00Z"), { volume: 330, share: true, id: "e-1" });
    const shared = toSharedDrink(logged);
    expect(shared).toEqual({ id: "e-1", name: "Pils", icon: pils.icon, category: pils.category, volumeMilliliters: 330, consumedAt: logged.consumedAt });
    expect(Object.keys(shared).sort()).toEqual(["category", "consumedAt", "icon", "id", "name", "volumeMilliliters"]);
  });

  it("volumeFormatterForDisplay", () => {
    expect(formatVolume(250)).toBe("250 ml");
    expect(formatVolume(999)).toBe("999 ml");
    expect(formatVolume(1_000)).toBe("1 l");
    expect(formatVolume(1_250)).toBe("1,3 l");
    expect(formatVolume(1_500)).toBe("1,5 l");
    expect(formatVolume(2_480)).toBe("2,5 l");
  });
});

/** Zeichnet alle Netzwerkaufträge auf; optional mit Fehlern oder Wartepunkt. */
class RecordingPublisher implements EventPublisher {
  calls: string[] = [];
  published: DrinkEntry[] = [];
  failNextWith: Error | null = null;
  gate: Promise<void> | null = null;

  async publish(drink: DrinkEntry): Promise<void> {
    if (this.gate) await this.gate;
    if (this.failNextWith) {
      const failure = this.failNextWith;
      this.failNextWith = null;
      throw failure;
    }
    this.calls.push(`publish:${drink.id}`);
    this.published.push(drink);
  }

  async unpublish(entryId: string): Promise<void> {
    if (this.gate) await this.gate;
    if (this.failNextWith) {
      const failure = this.failNextWith;
      this.failNextWith = null;
      throw failure;
    }
    this.calls.push(`unpublish:${entryId}`);
  }
}

describe("Kotlin-Kern: SharingHistoryRepositoryTest", () => {
  const water = catalogItem(CatalogIds.WASSER);
  const pils = catalogItem(CatalogIds.PILS);
  const at = instant("2026-11-10T18:00:00Z");

  function setUp() {
    const local = new InMemoryHistoryRepository();
    const publisher = new RecordingPublisher();
    const errors: unknown[] = [];
    const holder = { state: { isSignedIn: true, alcoholConsent: "ALLOWED" } as SharingState };
    const policy = new SharingPolicy({ current: () => holder.state });
    const repository = new SharingHistoryRepository(local, publisher, policy, (error) => errors.push(error));
    const allLocal = () => local.entries(HISTORY_EARLIEST, HISTORY_LATEST);
    return { local, publisher, errors, holder, policy, repository, allLocal };
  }

  it("savesLocallyAtOnceAndPublishesInTheBackground", async () => {
    const { publisher, errors, repository, allLocal } = setUp();
    const shared = entryOf(water, at, { share: true });
    await repository.save(shared);
    expect(await allLocal()).toEqual([shared]);
    expect(publisher.calls).toEqual([]);
    await repository.idle();
    expect(publisher.published).toEqual([shared]);
    expect(errors).toEqual([]);
  });

  it("localSaveNeverWaitsForTheNetwork", async () => {
    const { publisher, repository } = setUp();
    let open: () => void = () => {};
    publisher.gate = new Promise<void>((resolve) => (open = resolve));
    const first = entryOf(water, at, { share: true });
    const second = entryOf(water, at + MINUTE, { share: true });
    await repository.save(first);
    await repository.save(second);
    expect(await repository.entries(HISTORY_EARLIEST, HISTORY_LATEST)).toEqual([first, second]);
    expect(publisher.calls).toEqual([]);
    open();
    await repository.idle();
    expect(publisher.calls).toEqual([`publish:${first.id}`, `publish:${second.id}`]);
  });

  it("privateEntriesCauseNoNetworkCalls", async () => {
    const { publisher, repository, allLocal } = setUp();
    const own = entryOf(water, at, { share: false });
    await repository.save(own);
    await repository.delete(own.id);
    await repository.idle();
    expect(publisher.calls).toEqual([]);
    expect(await allLocal()).toEqual([]);
  });

  it("rechecksThePolicyBeforePublishing", async () => {
    const { publisher, holder, repository, allLocal } = setUp();
    holder.state = { isSignedIn: true, alcoholConsent: "DENIED" };
    await repository.save(entryOf(pils, at, { share: true }));
    holder.state = { isSignedIn: false, alcoholConsent: "ALLOWED" };
    await repository.save(entryOf(water, at, { share: true }));
    await repository.idle();
    expect(publisher.calls).toEqual([]);
    expect(await allLocal()).toHaveLength(2);
  });

  it("deleteUnpublishesSharedEntries", async () => {
    const { local, publisher, repository } = setUp();
    const shared = entryOf(water, at, { share: true });
    await repository.save(shared);
    await repository.delete(shared.id);
    expect(await local.entry(shared.id)).toBeNull();
    await repository.idle();
    expect(publisher.calls).toEqual([`publish:${shared.id}`, `unpublish:${shared.id}`]);
  });

  it("deleteWhileSignedOutStaysLocal", async () => {
    const { local, publisher, holder, repository } = setUp();
    const shared = entryOf(water, at, { share: true });
    await local.save(shared);
    holder.state = { ...holder.state, isSignedIn: false };
    await repository.delete(shared.id);
    await repository.idle();
    expect(publisher.calls).toEqual([]);
    expect(await local.entry(shared.id)).toBeNull();
  });

  it("resavingASharedEntryAsPrivateUnpublishesIt", async () => {
    const { publisher, repository, allLocal } = setUp();
    const shared = entryOf(water, at, { share: true });
    await repository.save(shared);
    await repository.save({ ...shared, shareWithFriends: false });
    await repository.idle();
    expect(publisher.calls).toEqual([`publish:${shared.id}`, `unpublish:${shared.id}`]);
    expect(await allLocal()).toEqual([{ ...shared, shareWithFriends: false }]);
  });

  it("failuresAreReportedAndBlockNothing", async () => {
    const { publisher, errors, repository, allLocal } = setUp();
    const failure = new Error("offline");
    publisher.failNextWith = failure;
    const first = entryOf(water, at, { share: true });
    const second = entryOf(water, at + SECOND, { share: true });
    await repository.save(first);
    await repository.save(second);
    await repository.idle();
    expect(await allLocal()).toEqual([first, second]);
    expect(errors).toEqual([failure]);
    expect(publisher.published).toEqual([second]);
  });

  it("aThrowingErrorCallbackDoesNotStopLaterOperations", async () => {
    const { local, publisher, policy } = setUp();
    const repository = new SharingHistoryRepository(local, publisher, policy, () => {
      throw new Error("Callback kaputt");
    });
    publisher.failNextWith = new Error("offline");
    const first = entryOf(water, at, { share: true });
    const second = entryOf(water, at + SECOND, { share: true });
    await repository.save(first);
    await repository.save(second);
    await repository.idle();
    expect(publisher.published).toEqual([second]);
  });

  it("laterOperationsAfterTheQueueRanEmptyStillRun", async () => {
    const { publisher, repository } = setUp();
    const first = entryOf(water, at, { share: true });
    await repository.save(first);
    await repository.idle();
    const second = entryOf(water, at + 5 * SECOND, { share: true });
    await repository.save(second);
    await repository.idle();
    expect(publisher.published).toEqual([first, second]);
  });

  it("readsComeFromTheLocalHistory", async () => {
    const { local, repository } = setUp();
    const own = entryOf(water, at, { share: false });
    await local.save(own);
    expect(await repository.entries(at, at + SECOND)).toEqual([own]);
    expect(await repository.entry(own.id)).toEqual(own);
  });

  it("logAndUndoThroughTheDecorator", async () => {
    const { publisher, policy, repository, allLocal } = setUp();
    const clock = new MutableClock(at);
    const logDrink = new LogDrinkFromBarUseCase(repository, policy, clock, new SequentialIds());
    const undo = new UndoLastEntryUseCase(repository, clock);
    const logged = await logDrink.execute(pils);
    expect(logged.shareWithFriends).toBe(true);
    clock.advance(3 * SECOND);
    expect((await undo.execute()).kind).toBe("Undone");
    await repository.idle();
    expect(await allLocal()).toEqual([]);
    expect(publisher.calls).toEqual(["publish:id-1", "unpublish:id-1"]);
  });
});

describe("Kotlin-Kern: LogDrinkFromBarUseCaseTest", () => {
  const now = instant("2026-11-10T17:30:00Z");
  const schorle = catalogItem(CatalogIds.APFELSCHORLE);

  function setUp() {
    const clock = new MutableClock(now);
    const history = new InMemoryHistoryRepository();
    const decisions: Array<[string, boolean | null]> = [];
    const shareAll: ShareDecider = {
      shareWithFriends: (item, override) => {
        decisions.push([item.id, override]);
        return override !== false;
      },
    };
    const logDrink = new LogDrinkFromBarUseCase(history, shareAll, clock, new SequentialIds());
    return { clock, history, decisions, logDrink };
  }

  it("createsASnapshotWithTheCurrentTime", async () => {
    const { history, logDrink } = setUp();
    const logged = await logDrink.execute(schorle);
    expect(logged).toEqual({
      id: "id-1",
      barItemId: CatalogIds.APFELSCHORLE,
      name: "Apfelschorle",
      icon: "apple_spritzer",
      category: "JUICE",
      volumeMilliliters: 500,
      alcoholPercent: 0.0,
      sugarGramsPer100Ml: 5.5,
      consumedAt: now,
      shareWithFriends: true,
    });
    expect(await history.entries(HISTORY_EARLIEST, HISTORY_LATEST)).toEqual([logged]);
  });

  it("timeIsAlwaysNow", async () => {
    const { clock, logDrink } = setUp();
    const first = await logDrink.execute(schorle);
    clock.advance(45 * MINUTE);
    const second = await logDrink.execute(schorle);
    expect(first.consumedAt).toBe(now);
    expect(second.consumedAt).toBe(now + 45 * MINUTE);
  });

  it("laterChangesToTheBarDoNotChangeTheHistory", async () => {
    const { history, logDrink } = setUp();
    const logged = await logDrink.execute(schorle);
    await logDrink.execute({ ...schorle, name: "Schorle light", sugarGramsPer100Ml: 2.0, servingMilliliters: 300 });
    const stored = await history.entry(logged.id);
    expect(stored?.name).toBe("Apfelschorle");
    expect(stored?.sugarGramsPer100Ml).toBe(5.5);
    expect(stored?.volumeMilliliters).toBe(500);
  });

  it("volumeOverrideReplacesTheServing", async () => {
    const { logDrink } = setUp();
    expect((await logDrink.execute(schorle, 330)).volumeMilliliters).toBe(330);
    expect((await logDrink.execute(schorle, 1)).volumeMilliliters).toBe(1);
    expect((await logDrink.execute(schorle, 2000)).volumeMilliliters).toBe(2000);
  });

  it("invalidVolumeIsRejectedAndNothingIsSaved", async () => {
    const { history, logDrink } = setUp();
    for (const volume of [0, -5, 2001]) {
      await expect(logDrink.execute(schorle, volume)).rejects.toThrow("Die Menge muss zwischen 1 und 2000 ml liegen.");
    }
    expect(await history.entries(HISTORY_EARLIEST, HISTORY_LATEST)).toEqual([]);
  });

  it("invalidBarItemIsRejected", async () => {
    const { history, logDrink } = setUp();
    await expect(logDrink.execute({ ...schorle, name: " " })).rejects.toBeInstanceOf(InvalidBarItemError);
    expect(await history.entries(HISTORY_EARLIEST, HISTORY_LATEST)).toEqual([]);
  });

  it("sharingIsDecidedByThePolicyWithTheOverride", async () => {
    const { decisions, logDrink } = setUp();
    expect((await logDrink.execute(schorle)).shareWithFriends).toBe(true);
    expect((await logDrink.execute(schorle, null, false)).shareWithFriends).toBe(false);
    expect((await logDrink.execute(schorle, null, true)).shareWithFriends).toBe(true);
    expect(decisions.map(([, override]) => override)).toEqual([null, false, true]);
    expect(decisions.every(([id]) => id === schorle.id)).toBe(true);
  });

  it("nameIsTrimmed", async () => {
    const { logDrink } = setUp();
    expect((await logDrink.execute({ ...schorle, name: "  Apfelschorle " })).name).toBe("Apfelschorle");
  });
});

describe("Kotlin-Kern: UndoAndDeleteTest", () => {
  const loggedAt = instant("2026-11-10T17:30:00Z");

  function setUp() {
    const clock = new MutableClock(loggedAt);
    const latest = entry(loggedAt);
    const earlier = entry(loggedAt - 5 * MINUTE);
    const history = new InMemoryHistoryRepository([earlier, latest]);
    const undo = new UndoLastEntryUseCase(history, clock);
    return { clock, latest, earlier, history, undo };
  }

  it("undoesImmediatelyWithoutQuestion", async () => {
    const { latest, earlier, history, undo } = setUp();
    expect(await undo.execute()).toEqual({ kind: "Undone", entry: latest });
    expect(await history.entry(latest.id)).toBeNull();
    expect(await history.entry(earlier.id)).toEqual(earlier);
  });

  it("undoesUpToExactlyTenSeconds", async () => {
    const { clock, latest, undo } = setUp();
    clock.advance(10 * SECOND);
    expect(await undo.execute()).toEqual({ kind: "Undone", entry: latest });
  });

  it("asksForConfirmationAfterTenSeconds", async () => {
    const { clock, latest, history, undo } = setUp();
    clock.advance(10 * SECOND + 1);
    expect(await undo.execute()).toEqual({ kind: "ConfirmationRequired", entry: latest });
    expect(await history.entry(latest.id)).toEqual(latest);
    expect(await undo.confirm(latest)).toEqual({ kind: "Undone", entry: latest });
    expect(await history.entry(latest.id)).toBeNull();
  });

  it("repeatedUndoRemovesTheNextNewestEntry", async () => {
    const { clock, earlier, undo } = setUp();
    await undo.execute();
    clock.advance(SECOND);
    expect(await undo.execute()).toEqual({ kind: "ConfirmationRequired", entry: earlier });
  });

  it("nothingToUndoWithoutRecentEntries", async () => {
    const { clock, undo } = setUp();
    expect(await new UndoLastEntryUseCase(new InMemoryHistoryRepository(), clock).execute()).toEqual({ kind: "NothingToUndo" });
    clock.advance(25 * 60 * MINUTE);
    expect(await undo.execute()).toEqual({ kind: "NothingToUndo" });
  });

  it("windowIsConfigurable", async () => {
    const { clock, latest, history } = setUp();
    clock.advance(20 * SECOND);
    expect(await new UndoLastEntryUseCase(history, clock, 30 * SECOND).execute()).toEqual({ kind: "Undone", entry: latest });
    expect(UNDO_WINDOW_MILLIS).toBe(10_000);
  });

  it("deleteEntryRemovesIt", async () => {
    const { latest, earlier, history } = setUp();
    await new DeleteEntryUseCase(history).execute(earlier.id);
    expect(await history.entries(HISTORY_EARLIEST, HISTORY_LATEST)).toEqual([latest]);
    await new DeleteEntryUseCase(history).execute("unbekannt");
    expect(await history.entry(latest.id)).not.toBeNull();
  });
});

describe("Kotlin-Kern: HistoryRepositoryTest", () => {
  const morning = entry(berlin("2026-11-10T08:00"));
  const noon = entry(berlin("2026-11-10T12:00"));
  const evening = entry(berlin("2026-11-10T20:00"));

  it("entriesUsesAHalfOpenRangeAndSortsAscending", async () => {
    const repository = new InMemoryHistoryRepository([evening, morning, noon]);
    expect(await repository.entries(berlin("2026-11-10T00:00"), berlin("2026-11-11T00:00"))).toEqual([morning, noon, evening]);
    expect(await repository.entries(morning.consumedAt, noon.consumedAt)).toEqual([morning]);
    expect(await repository.entries(berlin("2026-11-11T00:00"), berlin("2026-11-12T00:00"))).toEqual([]);
  });

  it("saveAddsOrReplacesAndDeleteRemoves", async () => {
    const repository = new InMemoryHistoryRepository();
    await repository.save(morning);
    await repository.save({ ...morning, volumeMilliliters: 330 });
    await repository.save(noon);
    await repository.delete(noon.id);
    await repository.delete("unbekannt");
    expect(await repository.entries(HISTORY_EARLIEST, HISTORY_LATEST)).toEqual([{ ...morning, volumeMilliliters: 330 }]);
    expect(await repository.entry(noon.id)).toBeNull();
  });

  it("latestFitsIntoEpochMilliseconds", () => {
    expect(HISTORY_LATEST).toBeGreaterThan(Date.parse("2999-12-31T00:00:00Z"));
  });
});
