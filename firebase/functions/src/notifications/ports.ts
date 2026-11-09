import type { DrinkNotification } from "../domain/drinkNotification";

// Schnittstellen („Ports“) der Push-Benachrichtigung. Der DrinkNotifier kennt nur diese
// Abstraktionen; Firestore- und FCM-Implementierungen werden im Composition Root verdrahtet.

/** Ein registriertes Gerät: `users/{userId}/tokens/{token}`. */
export interface DeviceToken {
  userId: string;
  token: string;
}

/** Liest und entfernt Geräte-Tokens. */
export interface DeviceTokenStore {
  tokensOf(userIds: readonly string[]): Promise<DeviceToken[]>;
  remove(tokens: readonly DeviceToken[]): Promise<void>;
}

/** Ergebnis pro Token; `errorCode` ist der FCM-Fehlercode (z. B. `messaging/invalid-registration-token`). */
export interface SendResult {
  token: string;
  errorCode?: string;
}

/** Versendet eine Push-Nachricht an mehrere Geräte. */
export interface NotificationSender {
  send(tokens: readonly string[], notification: DrinkNotification): Promise<SendResult[]>;
}

/** Schmale Logging-Schnittstelle (in Produktion `firebase-functions/logger`). */
export interface Logger {
  info(message: string, data?: Record<string, unknown>): void;
  warn(message: string, data?: Record<string, unknown>): void;
  error(message: string, data?: Record<string, unknown>): void;
}
