import { BAR_ITEM_LIMITS, type BarItem } from "../bar/barItem";
import { DefaultBarItemValidator, requireValid, type BarItemValidator } from "../bar/barItemValidation";
import { randomIdGenerator, type Clock, type IdGenerator } from "../time/clock";
import type { DrinkEntry, HistoryRepository, HistoryWriter, ShareDecider } from "./drinkEntry";

/**
 * „Ein Tipp = getrunken“: erzeugt aus einem Bar-Getränk einen {@link DrinkEntry} und speichert ihn lokal.
 * Zeitpunkt ist **immer jetzt**; Name, Icon, Kategorie, Vol.-% und Zucker werden kopiert (Snapshot);
 * `shareWithFriends` entscheidet der {@link ShareDecider} (die `SharingPolicy`).
 */
export class LogDrinkFromBarUseCase {
  constructor(
    private readonly history: HistoryWriter,
    private readonly shareDecider: ShareDecider,
    private readonly clock: Clock,
    private readonly idGenerator: IdGenerator = randomIdGenerator,
    private readonly validator: BarItemValidator = new DefaultBarItemValidator(),
  ) {}

  /**
   * @param volumeOverride abweichende Menge in ml (1–2000), sonst die Standardportion.
   * @throws Error bei ungültigem Bar-Getränk oder ungültiger Menge; dann wird nichts gespeichert.
   */
  async execute(barItem: BarItem, volumeOverride: number | null = null, shareOverride: boolean | null = null): Promise<DrinkEntry> {
    requireValid(this.validator, barItem);
    const volume = volumeOverride ?? barItem.servingMilliliters;
    const min = BAR_ITEM_LIMITS.SERVING_MIN_MILLILITERS;
    const max = BAR_ITEM_LIMITS.SERVING_MAX_MILLILITERS;
    if (!Number.isInteger(volume) || volume < min || volume > max) {
      throw new RangeError(`Die Menge muss zwischen ${min} und ${max} ml liegen.`);
    }
    const entry: DrinkEntry = {
      id: this.idGenerator.newId(),
      barItemId: barItem.id,
      name: barItem.name.trim(),
      icon: barItem.icon,
      category: barItem.category,
      volumeMilliliters: volume,
      alcoholPercent: barItem.alcoholPercent,
      sugarGramsPer100Ml: barItem.sugarGramsPer100Ml,
      consumedAt: Math.trunc(this.clock.now()),
      shareWithFriends: this.shareDecider.shareWithFriends(barItem, shareOverride),
    };
    await this.history.save(entry);
    return entry;
  }
}

/** Ergebnis von {@link UndoLastEntryUseCase}. */
export type UndoResult =
  | { readonly kind: "Undone"; readonly entry: DrinkEntry }
  | { readonly kind: "ConfirmationRequired"; readonly entry: DrinkEntry }
  | { readonly kind: "NothingToUndo" };

export const UNDO_WINDOW_MILLIS = 10_000;
export const UNDO_LOOKBACK_MILLIS = 24 * 60 * 60 * 1000;
const FUTURE_TOLERANCE_MILLIS = 1_000;

/**
 * „Rückgängig“: löscht den zuletzt eingetragenen Eintrag **ohne Rückfrage**, wenn er höchstens `window`
 * (Standard 10 s) alt ist. Ältere Einträge (bis 24 h) liefern `ConfirmationRequired`.
 */
export class UndoLastEntryUseCase {
  constructor(
    private readonly history: HistoryRepository,
    private readonly clock: Clock,
    private readonly window: number = UNDO_WINDOW_MILLIS,
    private readonly lookback: number = UNDO_LOOKBACK_MILLIS,
  ) {}

  async execute(): Promise<UndoResult> {
    const now = this.clock.now();
    const candidates = await this.history.entries(now - this.lookback, now + FUTURE_TOLERANCE_MILLIS);
    const last = [...candidates].sort((a, b) => a.consumedAt - b.consumedAt).at(-1);
    if (!last) return { kind: "NothingToUndo" };
    if (now - last.consumedAt > this.window) return { kind: "ConfirmationRequired", entry: last };
    await this.history.delete(last.id);
    return { kind: "Undone", entry: last };
  }

  /** Löscht genau den Eintrag, dessen Löschung der Nutzer nach Rückfrage bestätigt hat. */
  async confirm(entry: DrinkEntry): Promise<UndoResult> {
    await this.history.delete(entry.id);
    return { kind: "Undone", entry };
  }
}

/** Löscht einen Eintrag aus der Historie (z. B. aus der Liste im Verlauf). */
export class DeleteEntryUseCase {
  constructor(private readonly history: HistoryWriter) {}

  execute(id: string): Promise<void> {
    return this.history.delete(id);
  }
}
