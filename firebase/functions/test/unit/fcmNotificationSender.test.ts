import type { BatchResponse, MulticastMessage } from "firebase-admin/messaging";
import { describe, expect, it } from "vitest";
import type { DrinkMessage } from "../../src/domain/drinkNotification";
import { FcmNotificationSender, toMulticastMessage } from "../../src/notifications/fcmNotificationSender";
import type { MulticastMessaging } from "../../src/notifications/fcmNotificationSender";

const message: DrinkMessage = {
  android: { priority: "high" },
  data: {
    type: "drink",
    groupId: "g1",
    drinkId: "d1",
    docId: "anna_d1",
    recipientId: "bob",
    title: "Anna trinkt gerade",
    body: "🍺 Helles (500 ml) · Stammtisch",
  },
};

/** Antwortet pro Token mit Erfolg oder dem hinterlegten Fehler. */
class FakeMessaging implements MulticastMessaging {
  readonly messages: MulticastMessage[] = [];

  constructor(private readonly errors: Record<string, { code: string; message: string }> = {}) {}

  sendEachForMulticast(multicast: MulticastMessage): Promise<BatchResponse> {
    this.messages.push(multicast);
    const responses = multicast.tokens.map((token) => {
      const error = this.errors[token];
      return error === undefined ? { success: true, messageId: `m-${token}` } : { success: false, error };
    });
    return Promise.resolve({
      responses,
      successCount: responses.filter((r) => r.success).length,
      failureCount: responses.filter((r) => !r.success).length,
    } as BatchResponse);
  }
}

describe("toMulticastMessage", () => {
  it("ist eine reine Daten-Nachricht mit hoher Priorität (kein notification-Block)", () => {
    const multicast = toMulticastMessage(["t1", "t2"], message);

    expect(multicast).toEqual({
      tokens: ["t1", "t2"],
      android: { priority: "high" },
      data: {
        type: "drink",
        groupId: "g1",
        drinkId: "d1",
        docId: "anna_d1",
        recipientId: "bob",
        title: "Anna trinkt gerade",
        body: "🍺 Helles (500 ml) · Stammtisch",
      },
    });
    expect(multicast).not.toHaveProperty("notification");
    expect(multicast.android).not.toHaveProperty("notification");
  });
});

describe("FcmNotificationSender", () => {
  it("ordnet Ergebnisse, Fehlercodes und Fehlermeldungen den Tokens zu", async () => {
    const messaging = new FakeMessaging({
      t2: { code: "messaging/invalid-argument", message: "The registration token is not a valid FCM registration token" },
    });

    const results = await new FcmNotificationSender(messaging).send(["t1", "t2"], message);

    expect(results).toEqual([
      { token: "t1" },
      {
        token: "t2",
        errorCode: "messaging/invalid-argument",
        errorMessage: "The registration token is not a valid FCM registration token",
      },
    ]);
  });

  it("teilt große Empfängerlisten in Blöcke auf", async () => {
    const messaging = new FakeMessaging({ t4: { code: "messaging/invalid-registration-token", message: "x" } });
    const tokens = ["t1", "t2", "t3", "t4", "t5"];

    const results = await new FcmNotificationSender(messaging, 2).send(tokens, message);

    expect(messaging.messages.map((multicast) => multicast.tokens)).toEqual([["t1", "t2"], ["t3", "t4"], ["t5"]]);
    expect(results.map((result) => result.token)).toEqual(tokens);
    expect(results[3]).toEqual({ token: "t4", errorCode: "messaging/invalid-registration-token", errorMessage: "x" });
  });

  it("verwendet standardmäßig Blöcke zu 500 Tokens", async () => {
    const messaging = new FakeMessaging();
    const tokens = Array.from({ length: 1_001 }, (_, i) => `t${i}`);

    await new FcmNotificationSender(messaging).send(tokens, message);

    expect(messaging.messages.map((multicast) => multicast.tokens.length)).toEqual([500, 500, 1]);
  });
});
