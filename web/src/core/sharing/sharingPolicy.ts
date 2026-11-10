import type { BarItem } from "../bar/barItem";
import { isAlcoholic } from "../health/healthMath";
import type { DrinkEntry, ShareDecider } from "../log/drinkEntry";

/** Einmalige Zustimmung zum Teilen alkoholischer Getränke (später in den Einstellungen änderbar). */
export type AlcoholSharingConsent = "UNKNOWN" | "ALLOWED" | "DENIED";

/** Momentaufnahme dessen, was die Teilen-Regel wissen muss. */
export interface SharingState {
  readonly isSignedIn: boolean;
  readonly alcoholConsent: AlcoholSharingConsent;
}

/** Liefert den aktuellen {@link SharingState} (Anmeldung aus der Sitzung, Zustimmung aus den Einstellungen). */
export interface SharingStateProvider {
  current(): SharingState;
}

export const SharingTexts = {
  /** Einmalige Frage beim ersten alkoholischen Getränk. */
  ALCOHOL_CONSENT_QUESTION: "Alkoholische Getränke mit Freunden teilen?",
  ALCOHOL_CONSENT_YES: "Ja",
  ALCOHOL_CONSENT_NO: "Nein",
  ALCOHOL_CONSENT_HINT: "Das kannst du später in den Einstellungen ändern.",
} as const;

/**
 * Teilen-Regel (Port von `SharingPolicy`): Ein Eintrag wird mit Freunden geteilt, wenn angemeldet **und** das
 * Getränk nicht als privat markiert ist **und** (nicht alkoholisch **oder** der Nutzer hat dem Teilen
 * alkoholischer Getränke einmalig zugestimmt). Geteilt werden nur Name, Icon, Kategorie, ml und Zeitpunkt.
 */
export class SharingPolicy implements ShareDecider {
  constructor(private readonly state: SharingStateProvider) {}

  /** Die Regel als reine Funktion. */
  static shouldShare(state: SharingState, alcoholic: boolean, markedPrivate: boolean): boolean {
    return state.isSignedIn && !markedPrivate && (!alcoholic || state.alcoholConsent === "ALLOWED");
  }

  /** Beim Eintragen: `false` markiert privat; `true` ersetzt **nicht** Anmeldung oder Alkohol-Zustimmung. */
  shareWithFriends(item: BarItem, shareOverride: boolean | null): boolean {
    return SharingPolicy.shouldShare(this.state.current(), isAlcoholic(item.alcoholPercent), shareOverride === false);
  }

  /** Beim Veröffentlichen: erneute Prüfung, falls sich Anmeldung oder Zustimmung inzwischen geändert haben. */
  shouldPublish(entry: DrinkEntry): boolean {
    return entry.shareWithFriends && SharingPolicy.shouldShare(this.state.current(), isAlcoholic(entry.alcoholPercent), false);
  }

  /** Zurückziehen geteilter Ereignisse ist nur angemeldet möglich. */
  canUnpublish(): boolean {
    return this.state.current().isSignedIn;
  }

  /** `true`, wenn die App vor dem Teilen einmal fragen soll: angemeldet, alkoholisch, noch keine Antwort. */
  needsAlcoholConsentQuestion(item: BarItem): boolean {
    const current = this.state.current();
    return current.isSignedIn && isAlcoholic(item.alcoholPercent) && current.alcoholConsent === "UNKNOWN";
  }
}
