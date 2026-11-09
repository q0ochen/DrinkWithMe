import { describe, expect, it } from "vitest";
import { DrinkNotifier, MAX_TOKENS_PER_RECIPIENT } from "../../src/notifications/drinkNotifier";
import { FakeSender, InMemoryGroupLookup, InMemoryTokenStore, RecordingLogger, groupOf } from "./fakes";

const drinkData = {
  drinkId: "d1",
  authorId: "anna",
  authorName: "Anna",
  name: "Helles",
  category: "BEER",
  volumeInMilliliters: 500,
  note: null,
};

const TOKEN_MESSAGE = "The registration token is not a valid FCM registration token";

function setup(
  options: {
    tokens?: { userId: string; token: string }[];
    errors?: Record<string, { code: string; message?: string }>;
  } = {},
) {
  const groups = new InMemoryGroupLookup({ g1: groupOf(["anna", "bob", "cem"], "Stammtisch") });
  const tokens = new InMemoryTokenStore(options.tokens ?? []);
  const sender = new FakeSender(options.errors);
  const logger = new RecordingLogger();
  const notifier = new DrinkNotifier({ groups, tokens, sender, logger });
  return { notifier, tokens, sender, logger };
}

describe("DrinkNotifier", () => {
  it("schickt jedem anderen Mitglied eine eigene Daten-Nachricht mit seiner recipientId", async () => {
    const { notifier, tokens, sender } = setup({
      tokens: [
        { userId: "anna", token: "anna-phone" },
        { userId: "bob", token: "bob-phone" },
        { userId: "cem", token: "cem-phone" },
        { userId: "cem", token: "cem-tablet" },
      ],
    });

    const outcome = await notifier.onDrinkCreated("g1", "anna_d1", drinkData);

    expect(tokens.requests.map((request) => request.userId).sort()).toEqual(["bob", "cem"]);
    expect(sender.calls).toHaveLength(2);
    expect(sender.callFor("bob")).toEqual({
      tokens: ["bob-phone"],
      message: {
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
      },
    });
    expect(sender.callFor("cem")?.tokens).toEqual(["cem-tablet", "cem-phone"]);
    expect(sender.callFor("cem")?.message.data.recipientId).toBe("cem");
    expect(sender.callFor("anna")).toBeUndefined();
    expect(outcome).toEqual({ recipients: 2, tokens: 3, delivered: 3, removedTokens: 0, failedRecipients: 0 });
  });

  it("liest je Empfänger höchstens die 5 neuesten Tokens", async () => {
    const bobTokens = Array.from({ length: 8 }, (_, i) => ({ userId: "bob", token: `bob-${i}` }));
    const { notifier, tokens, sender } = setup({ tokens: bobTokens });

    await notifier.onDrinkCreated("g1", "anna_d1", drinkData);

    expect(MAX_TOKENS_PER_RECIPIENT).toBe(5);
    expect(tokens.requests.every((request) => request.limit === 5)).toBe(true);
    expect(sender.callFor("bob")?.tokens).toEqual(["bob-7", "bob-6", "bob-5", "bob-4", "bob-3"]);
  });

  it("löscht nur dauerhaft ungültige Tokens – und nur beim betroffenen Nutzer", async () => {
    const { notifier, tokens } = setup({
      tokens: [
        { userId: "bob", token: "bob-alt" },
        { userId: "bob", token: "bob-kaputt" },
        { userId: "bob", token: "bob-falsch" },
        { userId: "cem", token: "cem-phone" },
        { userId: "cem", token: "cem-wackelig" },
        { userId: "cem", token: "cem-nachricht" },
      ],
      errors: {
        "bob-alt": { code: "messaging/registration-token-not-registered" },
        "bob-kaputt": { code: "messaging/invalid-registration-token" },
        "bob-falsch": { code: "messaging/invalid-argument", message: TOKEN_MESSAGE },
        "cem-wackelig": { code: "messaging/internal-error" },
        "cem-nachricht": { code: "messaging/invalid-argument", message: "Request contains an invalid argument." },
      },
    });

    const outcome = await notifier.onDrinkCreated("g1", "anna_d1", drinkData);

    expect(tokens.tokens).toEqual([
      { userId: "cem", token: "cem-phone" },
      { userId: "cem", token: "cem-wackelig" },
      { userId: "cem", token: "cem-nachricht" },
    ]);
    expect(outcome).toEqual({ recipients: 2, tokens: 6, delivered: 1, removedTokens: 3, failedRecipients: 0 });
  });

  it("löscht nichts, wenn die Nachricht selbst fehlerhaft ist (invalid-argument ohne Token-Bezug)", async () => {
    const payloadError = { code: "messaging/invalid-argument", message: "Invalid value at 'message.data[0].value'" };
    const { notifier, tokens } = setup({
      tokens: [
        { userId: "bob", token: "bob-phone" },
        { userId: "cem", token: "cem-phone" },
      ],
      errors: { "bob-phone": payloadError, "cem-phone": payloadError },
    });

    const outcome = await notifier.onDrinkCreated("g1", "anna_d1", drinkData);

    expect(tokens.tokens).toHaveLength(2);
    expect(outcome.removedTokens).toBe(0);
  });

  it("schickt ein Token, das bei zwei Nutzern hängt, jedem Nutzer mit eigener recipientId", async () => {
    const { notifier, tokens, sender } = setup({
      tokens: [
        { userId: "bob", token: "geteilt" },
        { userId: "cem", token: "geteilt" },
      ],
      errors: { geteilt: { code: "messaging/registration-token-not-registered" } },
    });

    await notifier.onDrinkCreated("g1", "anna_d1", drinkData);

    expect(sender.callFor("bob")?.tokens).toEqual(["geteilt"]);
    expect(sender.callFor("cem")?.tokens).toEqual(["geteilt"]);
    expect(tokens.tokens).toEqual([]);
  });

  it("überspringt Empfänger ohne registrierte Geräte", async () => {
    const { notifier, sender } = setup({ tokens: [{ userId: "anna", token: "anna-phone" }, { userId: "cem", token: "cem-phone" }] });

    const outcome = await notifier.onDrinkCreated("g1", "anna_d1", drinkData);

    expect(sender.calls.map((call) => call.message.data.recipientId)).toEqual(["cem"]);
    expect(outcome).toEqual({ recipients: 2, tokens: 1, delivered: 1, removedTokens: 0, failedRecipients: 0 });
  });

  it("benachrichtigt die übrigen Empfänger, wenn einer scheitert, und protokolliert den Fehler", async () => {
    const { notifier, tokens, sender, logger } = setup({
      tokens: [
        { userId: "bob", token: "bob-phone" },
        { userId: "cem", token: "cem-phone" },
      ],
    });
    tokens.failingUsers.add("bob");

    const outcome = await notifier.onDrinkCreated("g1", "anna_d1", drinkData);

    expect(sender.calls.map((call) => call.message.data.recipientId)).toEqual(["cem"]);
    expect(outcome).toEqual({ recipients: 2, tokens: 1, delivered: 1, removedTokens: 0, failedRecipients: 1 });
    expect(logger.entries.filter((entry) => entry.level === "error")).toHaveLength(1);
    expect(logger.entries.find((entry) => entry.level === "error")?.data?.recipientId).toBe("bob");
  });

  it("sendet nichts, wenn der Autor allein in der Runde ist", async () => {
    const groups = new InMemoryGroupLookup({ solo: groupOf(["anna"]) });
    const tokens = new InMemoryTokenStore([{ userId: "anna", token: "anna-phone" }]);
    const sender = new FakeSender();
    const notifier = new DrinkNotifier({ groups, tokens, sender, logger: new RecordingLogger() });

    await notifier.onDrinkCreated("solo", "anna_d1", drinkData);

    expect(tokens.requests).toEqual([]);
    expect(sender.calls).toEqual([]);
  });

  it("ignoriert unbekannte Runden", async () => {
    const { notifier, sender, logger } = setup({ tokens: [{ userId: "bob", token: "bob-phone" }] });

    await notifier.onDrinkCreated("weg", "anna_d1", drinkData);

    expect(sender.calls).toEqual([]);
    expect(logger.entries.map((entry) => entry.level)).toEqual(["warn"]);
  });

  it("ignoriert Getränke ohne gültige Felder", async () => {
    const { notifier, sender, logger } = setup({ tokens: [{ userId: "bob", token: "bob-phone" }] });

    await notifier.onDrinkCreated("g1", "anna_d1", { ...drinkData, authorName: 7 });
    await notifier.onDrinkCreated("g1", "anna_d1", undefined);

    expect(sender.calls).toEqual([]);
    expect(logger.entries.map((entry) => entry.level)).toEqual(["warn", "warn"]);
  });
});
