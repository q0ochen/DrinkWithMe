import { describe, expect, it } from "vitest";
import { invalidTokens, isInvalidTokenResult } from "../../src/notifications/invalidTokens";

const TOKEN_MESSAGE = "The registration token is not a valid FCM registration token";

describe("isInvalidTokenResult", () => {
  it.each([
    ["nicht mehr registriert", { token: "t", errorCode: "messaging/registration-token-not-registered" }],
    ["ungültiges Token (Legacy-Code)", { token: "t", errorCode: "messaging/invalid-registration-token" }],
    ["invalid-argument mit Token-Meldung", { token: "t", errorCode: "messaging/invalid-argument", errorMessage: TOKEN_MESSAGE }],
    [
      "invalid-argument, Meldung in anderer Schreibweise",
      { token: "t", errorCode: "messaging/invalid-argument", errorMessage: "INVALID REGISTRATION TOKEN" },
    ],
  ])("löscht: %s", (_, result) => {
    expect(isInvalidTokenResult(result)).toBe(true);
  });

  it.each([
    ["Erfolg", { token: "t" }],
    ["vorübergehender Fehler", { token: "t", errorCode: "messaging/internal-error" }],
    ["Kontingent erschöpft", { token: "t", errorCode: "messaging/message-rate-exceeded" }],
    [
      "invalid-argument wegen der Nachricht",
      {
        token: "t",
        errorCode: "messaging/invalid-argument",
        errorMessage: "Invalid value at 'message.data[0].value' (TYPE_STRING)",
      },
    ],
    ["invalid-argument ohne Meldung", { token: "t", errorCode: "messaging/invalid-argument" }],
  ])("behält: %s", (_, result) => {
    expect(isInvalidTokenResult(result)).toBe(false);
  });
});

describe("invalidTokens", () => {
  it("löscht bei einem Fehler in der Nachricht nie alle Tokens", () => {
    const payloadError = { errorCode: "messaging/invalid-argument", errorMessage: "Request contains an invalid argument." };

    expect(invalidTokens([{ token: "a", ...payloadError }, { token: "b", ...payloadError }])).toEqual([]);
  });

  it("liefert jedes ungültige Token nur einmal", () => {
    expect(
      invalidTokens([
        { token: "a", errorCode: "messaging/registration-token-not-registered" },
        { token: "a", errorCode: "messaging/registration-token-not-registered" },
        { token: "b" },
        { token: "c", errorCode: "messaging/invalid-argument", errorMessage: TOKEN_MESSAGE },
      ]),
    ).toEqual(["a", "c"]);
  });
});
