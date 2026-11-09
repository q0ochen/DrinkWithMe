import type { BatchResponse, MulticastMessage } from "firebase-admin/messaging";
import { describe, expect, it } from "vitest";
import type { DrinkNotification } from "../../src/domain/drinkNotification";
import { FcmNotificationSender, toMulticastMessage } from "../../src/notifications/fcmNotificationSender";
import type { MulticastMessaging } from "../../src/notifications/fcmNotificationSender";

const notification: DrinkNotification = {
  notification: { title: "Anna trinkt gerade", body: "🍺 Helles (500 ml) · Stammtisch" },
  android: { notification: { channelId: "drinks", tag: "d1" } },
  data: { type: "drink", groupId: "g1", drinkId: "d1" },
};

/** Antwortet pro Token mit Erfolg oder dem hinterlegten Fehlercode. */
class FakeMessaging implements MulticastMessaging {
  readonly messages: MulticastMessage[] = [];

  constructor(private readonly errorCodes: Record<string, string> = {}) {}

  sendEachForMulticast(message: MulticastMessage): Promise<BatchResponse> {
    this.messages.push(message);
    const responses = message.tokens.map((token) => {
      const code = this.errorCodes[token];
      return code === undefined
        ? { success: true, messageId: `m-${token}` }
        : { success: false, error: { code, message: code } };
    });
    return Promise.resolve({
      responses,
      successCount: responses.filter((r) => r.success).length,
      failureCount: responses.filter((r) => !r.success).length,
    } as BatchResponse);
  }
}

describe("toMulticastMessage", () => {
  it("entspricht dem Vertrag (notification, android, data)", () => {
    expect(toMulticastMessage(["t1", "t2"], notification)).toEqual({
      tokens: ["t1", "t2"],
      notification: { title: "Anna trinkt gerade", body: "🍺 Helles (500 ml) · Stammtisch" },
      android: { notification: { channelId: "drinks", tag: "d1" } },
      data: { type: "drink", groupId: "g1", drinkId: "d1" },
    });
  });
});

describe("FcmNotificationSender", () => {
  it("ordnet Ergebnisse und Fehlercodes den Tokens zu", async () => {
    const messaging = new FakeMessaging({ t2: "messaging/registration-token-not-registered" });

    const results = await new FcmNotificationSender(messaging).send(["t1", "t2"], notification);

    expect(results).toEqual([
      { token: "t1" },
      { token: "t2", errorCode: "messaging/registration-token-not-registered" },
    ]);
  });

  it("teilt große Empfängerlisten in Blöcke auf", async () => {
    const messaging = new FakeMessaging({ t4: "messaging/invalid-registration-token" });
    const tokens = ["t1", "t2", "t3", "t4", "t5"];

    const results = await new FcmNotificationSender(messaging, 2).send(tokens, notification);

    expect(messaging.messages.map((message) => message.tokens)).toEqual([["t1", "t2"], ["t3", "t4"], ["t5"]]);
    expect(results.map((result) => result.token)).toEqual(tokens);
    expect(results[3]).toEqual({ token: "t4", errorCode: "messaging/invalid-registration-token" });
  });

  it("verwendet standardmäßig Blöcke zu 500 Tokens", async () => {
    const messaging = new FakeMessaging();
    const tokens = Array.from({ length: 1_001 }, (_, i) => `t${i}`);

    await new FcmNotificationSender(messaging).send(tokens, notification);

    expect(messaging.messages.map((message) => message.tokens.length)).toEqual([500, 500, 1]);
  });
});
