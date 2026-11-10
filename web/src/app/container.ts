import type { BarRepository } from "../core/bar/barRepository";
import { SaveBarItemUseCase, SetUpBarFromCatalogUseCase } from "../core/bar/barUseCases";
import type { HistoryRepository } from "../core/log/drinkEntry";
import { DeleteEntryUseCase, LogDrinkFromBarUseCase, UndoLastEntryUseCase } from "../core/log/logUseCases";
import { SharingHistoryRepository } from "../core/sharing/sharingHistoryRepository";
import { SharingPolicy } from "../core/sharing/sharingPolicy";
import type { EventPublisher, UserProfile } from "../core/social/ports";
import { StatisticsCalculator } from "../core/stats/statisticsCalculator";
import { systemClock, type Clock, type IdGenerator, randomIdGenerator } from "../core/time/clock";
import { systemTimeZone, type TimeZone } from "../core/time/localDate";
import { defaultVoiceMatcher, type VoiceMatcher } from "../core/voice/voiceCommandMatcher";
import { BackupService } from "../data/backup";
import type { SettingsStore } from "../data/settingsStore";
import { InMemoryStoredValue, type StoredValue } from "../data/storedValue";
import type { PlatformInfo } from "../platform/platform";
import type { SpeechRecognizer } from "../platform/recognition";
import type { ImageSharer } from "../platform/share";
import type { Speaker } from "../platform/speech";
import type { PersistentStorage } from "../platform/storage";
import { SocialError } from "../social/errors";
import type { SocialServices } from "../social/types";
import { ChangeNotifier, NotifyingBarRepository, NotifyingHistoryRepository } from "./changeNotifier";
import type { AppConfig } from "./config";
import { PendingShareQueue, RetryingEventPublisher, ShareRetryProcessor, type RetryOutcome } from "./pendingShares";

/** Angemeldeter Nutzer als beobachtbarer Zustand (zuerst aus dem lokalen Speicher, dann aus der Sitzung). */
export class SessionState {
  private user: UserProfile | null;
  private readonly listeners = new Set<(user: UserProfile | null) => void>();

  constructor(initial: UserProfile | null) {
    this.user = initial;
  }

  current(): UserProfile | null {
    return this.user;
  }

  set(user: UserProfile | null): void {
    if (user?.id === this.user?.id && user?.displayName === this.user?.displayName) return;
    this.user = user;
    for (const listener of this.listeners) listener(user);
  }

  subscribe(listener: (user: UserProfile | null) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }
}

/** Alles, was die Oberfläche braucht – zusammengesetzt an genau einer Stelle (Kompositionswurzel). */
export interface AppServices {
  readonly config: AppConfig;
  readonly platform: PlatformInfo;
  readonly clock: Clock;
  readonly zone: TimeZone;
  readonly bar: BarRepository;
  readonly history: HistoryRepository;
  readonly saveBarItem: SaveBarItemUseCase;
  readonly setUpBar: SetUpBarFromCatalogUseCase;
  readonly logDrink: LogDrinkFromBarUseCase;
  readonly undo: UndoLastEntryUseCase;
  readonly deleteEntry: DeleteEntryUseCase;
  readonly policy: SharingPolicy;
  readonly statistics: StatisticsCalculator;
  readonly settings: SettingsStore;
  readonly session: SessionState;
  readonly speaker: Speaker;
  readonly recognizer: SpeechRecognizer | null;
  readonly voice: VoiceMatcher;
  readonly imageSharer: ImageSharer;
  readonly ids: IdGenerator;
  readonly changes: ChangeNotifier;
  /** Export/Import von Bar und Verlauf (direkt lokal – Eingelesenes wird nie geteilt). */
  readonly backup: BackupService;
  /** Ohne Verbindung vorgemerkte Teilen-/Zurückziehen-Aufträge. */
  readonly pendingShares: PendingShareQueue;
  /** Holt vorgemerkte Aufträge nach (beim Start, wenn wieder online, wenn die App sichtbar wird). */
  retryPendingShares(): Promise<RetryOutcome>;
  /** Bittet einmalig um dauerhaften Speicher (nach dem Einrichten der Bar). */
  requestPersistentStorage(): Promise<void>;
  /** Vernetzung (lädt supabase-js erst bei Bedarf); `null`, wenn der Server nicht eingerichtet ist. */
  social(): Promise<SocialServices | null>;
  /** Meldet Hintergrundfehler (z. B. Teilen fehlgeschlagen) – blockiert nie. */
  reportBackgroundError(error: unknown): void;
  /** Hintergrundfehler beobachten (z. B. „too_many_events“ in der Bar anzeigen). */
  subscribeBackgroundErrors(listener: (error: unknown) => void): () => void;
}

export interface AppDependencies {
  readonly config: AppConfig;
  readonly platform: PlatformInfo;
  readonly bar: BarRepository;
  readonly history: HistoryRepository;
  readonly settings: SettingsStore;
  readonly speaker: Speaker;
  readonly recognizer: SpeechRecognizer | null;
  readonly imageSharer: ImageSharer;
  /** Lädt die Vernetzung (z. B. per dynamischem Import); nur aufgerufen, wenn der Server eingerichtet ist. */
  readonly loadSocial: () => Promise<SocialServices>;
  /**
   * Wird bei jeder Änderung der Anmeldung aufgerufen (z. B. für den Service Worker). `null` heißt **ausdrücklich
   * abgemeldet** – ein vorübergehender Fehler beim Erneuern der Sitzung meldet nie `null`.
   */
  readonly onSignedInUserChanged?: (user: UserProfile | null) => Promise<void> | void;
  /** Speicher der vorgemerkten Teilen-Aufträge (Standard: flüchtig). */
  readonly pendingShareStore?: StoredValue;
  readonly persistentStorage?: PersistentStorage;
  readonly clock?: Clock;
  readonly zone?: TimeZone;
  readonly ids?: IdGenerator;
  readonly onBackgroundError?: (error: unknown) => void;
}

export function createAppServices(dependencies: AppDependencies): AppServices {
  const { config, settings } = dependencies;
  const clock = dependencies.clock ?? systemClock;
  const zone = dependencies.zone ?? systemTimeZone;
  const ids = dependencies.ids ?? randomIdGenerator;
  const changes = new ChangeNotifier();
  const stored = settings.current().signedInUser;
  const session = new SessionState(config.supabase && stored ? { id: stored.id, displayName: stored.displayName, avatarUrl: null } : null);
  const backgroundErrorListeners = new Set<(error: unknown) => void>();
  const reportBackgroundError = (error: unknown) => {
    dependencies.onBackgroundError?.(error);
    for (const listener of backgroundErrorListeners) listener(error);
  };

  let socialPromise: Promise<SocialServices | null> | null = null;
  const social = (): Promise<SocialServices | null> => {
    if (!config.supabase) return Promise.resolve(null);
    socialPromise ??= dependencies
      .loadSocial()
      .then(async (services) => {
        const apply = async (user: UserProfile | null) => {
          session.set(user);
          const current = settings.current().signedInUser;
          if (current?.id !== user?.id || current?.displayName !== user?.displayName) {
            await settings.update({ signedInUser: user ? { id: user.id, displayName: user.displayName } : null });
          }
          await dependencies.onSignedInUserChanged?.(user);
        };
        services.auth.subscribe((user) => {
          apply(user).catch(reportBackgroundError);
        });
        await services.auth.ready();
        await apply(services.auth.currentUser());
        if (services.auth.currentUser()) {
          services.push.sync(settings.current().pushWanted).catch(reportBackgroundError);
          void retryPendingShares();
        }
        return services;
      })
      .catch((error: unknown) => {
        socialPromise = null;
        reportBackgroundError(error);
        return null;
      });
    return socialPromise;
  };

  const policy = new SharingPolicy({
    current: () => ({ isSignedIn: session.current() !== null, alcoholConsent: settings.current().alcoholConsent }),
  });
  /** Der echte Publisher der Vernetzung (lädt sie bei Bedarf); lädt sie nicht, ist das wie „keine Verbindung“. */
  const remotePublisher = async (): Promise<EventPublisher | null> => {
    if (!config.supabase) return null;
    const services = await social();
    if (!services) throw new SocialError("network");
    return services.publisher;
  };
  const lazyPublisher: EventPublisher = {
    async publish(entry) {
      await (await remotePublisher())?.publish(entry);
    },
    async unpublish(entryId) {
      await (await remotePublisher())?.unpublish(entryId);
    },
  };
  // Ohne Verbindung vormerken und später nachholen (wie PendingShareQueue in der Android-App).
  const pendingShares = new PendingShareQueue(dependencies.pendingShareStore ?? new InMemoryStoredValue());
  const retryProcessor = new ShareRetryProcessor(
    pendingShares,
    dependencies.history,
    policy,
    () => remotePublisher().catch(() => null),
    clock,
  );
  let retrying: Promise<RetryOutcome> | null = null;
  const retryPendingShares = (): Promise<RetryOutcome> => {
    retrying ??= retryProcessor
      .run()
      .catch((error: unknown): RetryOutcome => {
        reportBackgroundError(error);
        return "retryLater";
      })
      .finally(() => {
        retrying = null;
      });
    return retrying;
  };
  const history = new NotifyingHistoryRepository(
    new SharingHistoryRepository(dependencies.history, new RetryingEventPublisher(lazyPublisher, pendingShares), policy, reportBackgroundError),
    changes,
  );
  const bar = new NotifyingBarRepository(dependencies.bar, changes);
  const backup = new BackupService(dependencies.bar, dependencies.history, clock, () => {
    changes.notify("bar");
    changes.notify("history");
  });
  const requestPersistentStorage = async (): Promise<void> => {
    if (!dependencies.persistentStorage || settings.current().persistentStorageRequested) return;
    await settings.update({ persistentStorageRequested: true });
    await dependencies.persistentStorage.request();
  };

  return {
    config,
    platform: dependencies.platform,
    clock,
    zone,
    bar,
    history,
    saveBarItem: new SaveBarItemUseCase(bar),
    setUpBar: new SetUpBarFromCatalogUseCase(bar),
    logDrink: new LogDrinkFromBarUseCase(history, policy, clock, ids),
    undo: new UndoLastEntryUseCase(history, clock),
    deleteEntry: new DeleteEntryUseCase(history),
    policy,
    statistics: new StatisticsCalculator(clock, zone),
    settings,
    session,
    speaker: dependencies.speaker,
    recognizer: dependencies.recognizer,
    voice: defaultVoiceMatcher,
    imageSharer: dependencies.imageSharer,
    ids,
    changes,
    backup,
    pendingShares,
    retryPendingShares,
    requestPersistentStorage,
    social,
    reportBackgroundError,
    subscribeBackgroundErrors(listener) {
      backgroundErrorListeners.add(listener);
      return () => backgroundErrorListeners.delete(listener);
    },
  };
}
