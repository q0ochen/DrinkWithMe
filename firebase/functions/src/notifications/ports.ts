import type { DrinkMessage } from "../domain/drinkNotification";

// Schnittstellen („Ports“) der Push-Benachrichtigung. Der DrinkNotifier kennt nur diese
// Abstraktionen; Firestore- und FCM-Implementierungen werden im Composition Root verdrahtet.

/** Ein registriertes Gerät: `users/{userId}/tokens/{token}`. */
export interface DeviceToken {
  userId: string;
  token: string;
}

/** Liest und entfernt Geräte-Tokens. */
export interface DeviceTokenStore {
  /** Die höchstens `limit` neuesten Tokens eines Nutzers (nach `updatedAt` absteigend). */
  newestTokensOf(userId: string, limit: number): Promise<string[]>;
  remove(tokens: readonly DeviceToken[]): Promise<void>;
}

/**
 * Ergebnis pro Token; bei einem Fehler `errorCode` (FCM-Fehlercode, z. B.
 * `messaging/registration-token-not-registered`) und `errorMessage` (Text von FCM).
 */
export interface SendResult {
  token: string;
  errorCode?: string;
  errorMessage?: string;
}

/** Versendet eine Daten-Nachricht an die Geräte eines Empfängers. */
export interface NotificationSender {
  send(tokens: readonly string[], message: DrinkMessage): Promise<SendResult[]>;
}

/** Schmale Logging-Schnittstelle (in Produktion `firebase-functions/logger`). */
export interface Logger {
  info(message: string, data?: Record<string, unknown>): void;
  warn(message: string, data?: Record<string, unknown>): void;
  error(message: string, data?: Record<string, unknown>): void;
}
