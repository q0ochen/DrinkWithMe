import type { BatchResponse, MulticastMessage } from "firebase-admin/messaging";
import type { DrinkMessage } from "../domain/drinkNotification";
import type { NotificationSender, SendResult } from "./ports";

/** Höchstzahl an Tokens pro `sendEachForMulticast`-Aufruf (Grenze von FCM). */
export const FCM_MULTICAST_LIMIT = 500;

/** Der einzige Teil des Admin SDK, den der Sender braucht (für Tests austauschbar). */
export interface MulticastMessaging {
  sendEachForMulticast(message: MulticastMessage): Promise<BatchResponse>;
}

/** Baut die FCM-Nachricht für einen Block von Tokens – nur `android` und `data`, kein `notification`. */
export function toMulticastMessage(tokens: readonly string[], message: DrinkMessage): MulticastMessage {
  return {
    tokens: [...tokens],
    android: { ...message.android },
    data: { ...message.data },
  };
}

/** Versendet über Firebase Cloud Messaging (`sendEachForMulticast`), in Blöcken zu je 500 Tokens. */
export class FcmNotificationSender implements NotificationSender {
  constructor(
    private readonly messaging: MulticastMessaging,
    private readonly batchSize: number = FCM_MULTICAST_LIMIT,
  ) {}

  async send(tokens: readonly string[], message: DrinkMessage): Promise<SendResult[]> {
    const results: SendResult[] = [];
    for (let start = 0; start < tokens.length; start += this.batchSize) {
      const batch = tokens.slice(start, start + this.batchSize);
      const response = await this.messaging.sendEachForMulticast(toMulticastMessage(batch, message));
      response.responses.forEach((sendResponse, index) => {
        const token = batch[index];
        if (token === undefined) return;
        if (sendResponse.success) {
          results.push({ token });
          return;
        }
        const errorCode = sendResponse.error?.code ?? "unknown";
        const errorMessage = sendResponse.error?.message;
        results.push(errorMessage === undefined ? { token, errorCode } : { token, errorCode, errorMessage });
      });
    }
    return results;
  }
}
