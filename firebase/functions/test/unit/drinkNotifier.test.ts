import { describe, expect, it } from "vitest";
import { DrinkNotifier } from "../../src/notifications/drinkNotifier";
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

function setup(options: { tokens?: { userId: string; token: string }[]; errorCodes?: Record<string, string> } = {}) {
  const groups = new InMemoryGroupLookup({ g1: groupOf(["anna", "bob", "cem"], "Stammtisch") });
  const tokens = new InMemoryTokenStore(options.tokens ?? []);
  const sender = new FakeSender(options.errorCodes);
  const logger = new RecordingLogger();
  const notifier = new DrinkNotifier({ groups, tokens, sender, logger });
  return { notifier, tokens, sender, logger };
}

describe("DrinkNotifier", () => {
  it("benachrichtigt alle anderen Mitglieder, nicht den Autor", async () => {
    const { notifier, tokens, sender } = setup({
      tokens: [
        { userId: "anna", token: "anna-phone" },
        { userId: "bob", token: "bob-phone" },
        { userId: "cem", token: "cem-phone" },
        { userId: "cem", token: "cem-tablet" },
      ],
    });

    const outcome = await notifier.onDrinkCreated("g1", "d1", drinkData);

    expect(tokens.requestedUserIds).toEqual([["bob", "cem"]]);
    expect(sender.calls).toHaveLength(1);
    expect(sender.calls[0]?.tokens).toEqual(["bob-phone", "cem-phone", "cem-tablet"]);
    expect(sender.calls[0]?.notification).toEqual({
      notification: { title: "Anna trinkt gerade", body: "🍺 Helles (500 ml) · Stammtisch" },
      android: { notification: { channelId: "drinks", tag: "d1" } },
      data: { type: "drink", groupId: "g1", drinkId: "d1" },
    });
    expect(outcome).toEqual({ recipients: 2, tokens: 3, delivered: 3, removedTokens: 0 });
  });

  it("löscht nur dauerhaft ungültige Tokens", async () => {
    const { notifier, tokens } = setup({
      tokens: [
        { userId: "bob", token: "bob-alt" },
        { userId: "bob", token: "bob-kaputt" },
        { userId: "cem", token: "cem-phone" },
        { userId: "cem", token: "cem-wackelig" },
      ],
      errorCodes: {
        "bob-alt": "messaging/registration-token-not-registered",
        "bob-kaputt": "messaging/invalid-registration-token",
        "cem-wackelig": "messaging/internal-error",
      },
    });

    const outcome = await notifier.onDrinkCreated("g1", "d1", drinkData);

    expect(tokens.tokens).toEqual([
      { userId: "cem", token: "cem-phone" },
      { userId: "cem", token: "cem-wackelig" },
    ]);
    expect(outcome).toEqual({ recipients: 2, tokens: 4, delivered: 1, removedTokens: 2 });
  });

  it("sendet ein Token, das bei zwei Nutzern hängt, nur einmal und löscht es bei beiden", async () => {
    const { notifier, tokens, sender } = setup({
      tokens: [
        { userId: "bob", token: "geteilt" },
        { userId: "cem", token: "geteilt" },
      ],
      errorCodes: { geteilt: "messaging/registration-token-not-registered" },
    });

    await notifier.onDrinkCreated("g1", "d1", drinkData);

    expect(sender.calls[0]?.tokens).toEqual(["geteilt"]);
    expect(tokens.tokens).toEqual([]);
  });

  it("sendet nichts ohne registrierte Geräte", async () => {
    const { notifier, sender } = setup({ tokens: [{ userId: "anna", token: "anna-phone" }] });

    const outcome = await notifier.onDrinkCreated("g1", "d1", drinkData);

    expect(sender.calls).toEqual([]);
    expect(outcome).toEqual({ recipients: 2, tokens: 0, delivered: 0, removedTokens: 0 });
  });

  it("sendet nichts, wenn der Autor allein in der Runde ist", async () => {
    const groups = new InMemoryGroupLookup({ solo: groupOf(["anna"]) });
    const tokens = new InMemoryTokenStore([{ userId: "anna", token: "anna-phone" }]);
    const sender = new FakeSender();
    const notifier = new DrinkNotifier({ groups, tokens, sender, logger: new RecordingLogger() });

    await notifier.onDrinkCreated("solo", "d1", drinkData);

    expect(tokens.requestedUserIds).toEqual([]);
    expect(sender.calls).toEqual([]);
  });

  it("ignoriert unbekannte Runden", async () => {
    const { notifier, sender, logger } = setup({ tokens: [{ userId: "bob", token: "bob-phone" }] });

    await notifier.onDrinkCreated("weg", "d1", drinkData);

    expect(sender.calls).toEqual([]);
    expect(logger.entries.map((entry) => entry.level)).toEqual(["warn"]);
  });

  it("ignoriert Getränke ohne gültige Felder", async () => {
    const { notifier, sender, logger } = setup({ tokens: [{ userId: "bob", token: "bob-phone" }] });

    await notifier.onDrinkCreated("g1", "d1", { ...drinkData, authorName: 7 });
    await notifier.onDrinkCreated("g1", "d1", undefined);

    expect(sender.calls).toEqual([]);
    expect(logger.entries.map((entry) => entry.level)).toEqual(["warn", "warn"]);
  });
});
