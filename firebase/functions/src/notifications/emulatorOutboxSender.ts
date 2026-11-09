import { FieldValue } from "firebase-admin/firestore";
import type { Firestore } from "firebase-admin/firestore";
import type { DrinkNotification } from "../domain/drinkNotification";
import type { NotificationSender, SendResult } from "./ports";

/** Sammlung, in die der Emulator-Sender statt an FCM schreibt (nur im Emulator vorhanden). */
export const EMULATOR_OUTBOX_COLLECTION = "emulatorOutbox";

/** Tokens mit diesem Präfix gelten im Emulator als abgemeldet (zum Testen des Aufräumens). */
export const EMULATOR_UNREGISTERED_TOKEN_PREFIX = "unregistered-";

/**
 * Ersatz für FCM im Firebase-Emulator: Cloud Messaging lässt sich nicht emulieren und ein
 * Demo-Projekt hat keine Zugangsdaten. Die Nachrichten landen in `emulatorOutbox`, damit
 * Emulator-Tests den Trigger Ende-zu-Ende prüfen können. Wird nur verdrahtet, wenn
 * `FUNCTIONS_EMULATOR === "true"` ist – in Produktion nie.
 */
export class EmulatorOutboxSender implements NotificationSender {
  constructor(private readonly db: Firestore) {}

  async send(tokens: readonly string[], notification: DrinkNotification): Promise<SendResult[]> {
    await this.db.collection(EMULATOR_OUTBOX_COLLECTION).add({
      tokens: [...tokens],
      ...notification,
      sentAt: FieldValue.serverTimestamp(),
    });
    return tokens.map((token) =>
      token.startsWith(EMULATOR_UNREGISTERED_TOKEN_PREFIX)
        ? { token, errorCode: "messaging/registration-token-not-registered" }
        : { token },
    );
  }
}
