import { FieldValue } from "firebase-admin/firestore";
import type { Firestore } from "firebase-admin/firestore";
import type { DrinkMessage } from "../domain/drinkNotification";
import type { NotificationSender, SendResult } from "./ports";

/** Sammlung, in die der Emulator-Sender statt an FCM schreibt (nur im Emulator vorhanden). */
export const EMULATOR_OUTBOX_COLLECTION = "emulatorOutbox";

/** Tokens mit diesem Präfix gelten im Emulator als abgemeldet (zum Testen des Aufräumens). */
export const EMULATOR_UNREGISTERED_TOKEN_PREFIX = "unregistered-";

/** Tokens mit diesem Präfix gelten im Emulator als kaputt (`invalid-argument` mit Token-Meldung). */
export const EMULATOR_MALFORMED_TOKEN_PREFIX = "malformed-";

/** Meldung von FCM v1 für ein kaputtes Registrierungs-Token. */
export const MALFORMED_TOKEN_MESSAGE = "The registration token is not a valid FCM registration token";

/**
 * Ersatz für FCM im Firebase-Emulator: Cloud Messaging lässt sich nicht emulieren und ein
 * Demo-Projekt hat keine Zugangsdaten. Jede Nachricht landet als Dokument in `emulatorOutbox`,
 * damit Emulator-Tests den Trigger Ende-zu-Ende prüfen können. Wird nur verdrahtet, wenn
 * `FUNCTIONS_EMULATOR === "true"` ist – in Produktion nie.
 */
export class EmulatorOutboxSender implements NotificationSender {
  constructor(private readonly db: Firestore) {}

  async send(tokens: readonly string[], message: DrinkMessage): Promise<SendResult[]> {
    await this.db.collection(EMULATOR_OUTBOX_COLLECTION).add({
      tokens: [...tokens],
      ...message,
      sentAt: FieldValue.serverTimestamp(),
    });
    return tokens.map((token): SendResult => {
      if (token.startsWith(EMULATOR_UNREGISTERED_TOKEN_PREFIX)) {
        return { token, errorCode: "messaging/registration-token-not-registered" };
      }
      if (token.startsWith(EMULATOR_MALFORMED_TOKEN_PREFIX)) {
        return { token, errorCode: "messaging/invalid-argument", errorMessage: MALFORMED_TOKEN_MESSAGE };
      }
      return { token };
    });
  }
}
