import type { CallableRequest } from "firebase-functions/v2/https";
import { HttpsError } from "firebase-functions/v2/https";
import { describe, expect, it } from "vitest";
import type { AccountService } from "../../src/accounts/accountService";
import { SocialErrors } from "../../src/domain/errors";
import type { GroupDto } from "../../src/domain/group";
import type { Caller, GroupService } from "../../src/groups/groupService";
import {
  INTERNAL_ERROR_MESSAGE,
  callerOf,
  createGroupHandler,
  deleteAccountHandler,
  joinGroupHandler,
  leaveGroupHandler,
} from "../../src/handlers/callables";
import { RecordingLogger } from "./fakes";

const group: GroupDto = {
  id: "g1",
  name: "Stammtisch",
  inviteCode: "K7MX2QAB",
  ownerId: "anna",
  members: [{ userId: "anna", displayName: "Anna" }],
};

/** Merkt sich die Aufrufe; Verhalten pro Test einstellbar. */
class FakeGroupService implements GroupService {
  readonly calls: { method: string; caller: Caller; input: unknown }[] = [];
  failure: Error | undefined = undefined;

  createGroup(caller: Caller, name: unknown) {
    return this.record("createGroup", caller, name, group);
  }

  joinGroup(caller: Caller, code: unknown) {
    return this.record("joinGroup", caller, code, group);
  }

  leaveGroup(caller: Caller, groupId: unknown) {
    return this.record("leaveGroup", caller, groupId, undefined);
  }

  leaveAllGroups(caller: Caller) {
    return this.record("leaveAllGroups", caller, undefined, 0);
  }

  private record<T>(method: string, caller: Caller, input: unknown, result: T): Promise<T> {
    this.calls.push({ method, caller, input });
    return this.failure === undefined ? Promise.resolve(result) : Promise.reject(this.failure);
  }
}

/** Merkt sich die Aufrufer; Fehler pro Test einstellbar. */
class FakeAccountService implements AccountService {
  readonly callers: Caller[] = [];
  failure: Error | undefined = undefined;

  deleteAccount(caller: Caller): Promise<void> {
    this.callers.push(caller);
    return this.failure === undefined ? Promise.resolve() : Promise.reject(this.failure);
  }
}

function request(data: unknown, auth?: { uid: string; name?: string }): CallableRequest<unknown> {
  return {
    data,
    auth: auth && { uid: auth.uid, token: { name: auth.name } },
    rawRequest: {},
    acceptsStreaming: false,
  } as unknown as CallableRequest<unknown>;
}

/** Eine Anfrage mit frei gewähltem ID-Token-Inhalt (z. B. `firebase.identities`). */
function requestWithToken(uid: string, token: Record<string, unknown>): CallableRequest<unknown> {
  return { data: {}, auth: { uid, token }, rawRequest: {}, acceptsStreaming: false } as unknown as CallableRequest<unknown>;
}

async function rejection(promise: Promise<unknown>): Promise<HttpsError> {
  try {
    await promise;
  } catch (error) {
    expect(error).toBeInstanceOf(HttpsError);
    return error as HttpsError;
  }
  throw new Error("Es wurde ein Fehler erwartet.");
}

describe("Callable-Handler", () => {
  it("createGroup reicht Aufrufer und Namen weiter und antwortet mit { group }", async () => {
    const service = new FakeGroupService();

    const response = await createGroupHandler(service, new RecordingLogger())(
      request({ name: "Stammtisch" }, { uid: "anna", name: "Anna G." }),
    );

    expect(response).toEqual({ group });
    expect(service.calls).toEqual([
      { method: "createGroup", caller: { uid: "anna", tokenName: "Anna G." }, input: "Stammtisch" },
    ]);
  });

  it("joinGroup reicht den Code weiter", async () => {
    const service = new FakeGroupService();

    const response = await joinGroupHandler(service, new RecordingLogger())(request({ code: "k7mx2qab" }, { uid: "bob" }));

    expect(response).toEqual({ group });
    expect(service.calls).toEqual([{ method: "joinGroup", caller: { uid: "bob" }, input: "k7mx2qab" }]);
  });

  it("leaveGroup antwortet mit {}", async () => {
    const service = new FakeGroupService();

    const response = await leaveGroupHandler(service, new RecordingLogger())(request({ groupId: "g1" }, { uid: "bob" }));

    expect(response).toEqual({});
    expect(service.calls).toEqual([{ method: "leaveGroup", caller: { uid: "bob" }, input: "g1" }]);
  });

  it("deleteAccount reicht den Aufrufer weiter und antwortet mit {}", async () => {
    const service = new FakeAccountService();

    const response = await deleteAccountHandler(service, new RecordingLogger())(request({}, { uid: "bob", name: "Bob" }));

    expect(response).toEqual({});
    expect(service.callers).toEqual([{ uid: "bob", tokenName: "Bob" }]);
  });

  it("deleteAccount ignoriert die Eingabe", async () => {
    const service = new FakeAccountService();

    await deleteAccountHandler(service, new RecordingLogger())(request({ uid: "anna" }, { uid: "bob" }));
    await deleteAccountHandler(service, new RecordingLogger())(request(null, { uid: "bob" }));

    expect(service.callers).toEqual([{ uid: "bob" }, { uid: "bob" }]);
  });

  it("verlangt eine Anmeldung (unauthenticated)", async () => {
    const service = new FakeGroupService();
    const accounts = new FakeAccountService();
    const logger = new RecordingLogger();

    const handlers = [
      createGroupHandler(service, logger),
      joinGroupHandler(service, logger),
      leaveGroupHandler(service, logger),
      deleteAccountHandler(accounts, logger),
    ];
    for (const handler of handlers) {
      const error = await rejection(handler(request({ name: "x", code: "x", groupId: "x" })));
      expect(error.code).toBe("unauthenticated");
      expect(error.message).toBe("Bitte melde dich zuerst an.");
    }
    expect(service.calls).toEqual([]);
    expect(accounts.callers).toEqual([]);
  });

  it("übernimmt die Google-Kennung aus dem ID-Token (Schlüssel des Schutzes vor Code-Raten)", async () => {
    const service = new FakeGroupService();
    const googleToken = {
      name: "Bob",
      firebase: {
        sign_in_provider: "google.com",
        identities: { "google.com": ["109876543210987654321"], email: ["bob@example.com"] },
      },
    };

    await joinGroupHandler(service, new RecordingLogger())({
      ...requestWithToken("bob", googleToken),
      data: { code: "K7MX2QAB" },
    });

    expect(service.calls).toEqual([
      {
        method: "joinGroup",
        caller: { uid: "bob", tokenName: "Bob", googleSubject: "109876543210987654321" },
        input: "K7MX2QAB",
      },
    ]);
  });

  it.each([
    ["ohne firebase-Block", {}],
    ["ohne Google-Identität (z. B. eigenes Token)", { firebase: { identities: { email: ["bob@example.com"] } } }],
    ["mit leerer Liste", { firebase: { identities: { "google.com": [] } } }],
    ["mit leerer Kennung", { firebase: { identities: { "google.com": [""] } } }],
    ["mit Zahl statt Text", { firebase: { identities: { "google.com": [42] } } }],
    ["mit Text statt Liste", { firebase: { identities: { "google.com": "109876543210987654321" } } }],
    ["mit identities = null", { firebase: { identities: null } }],
  ])("lässt die Google-Kennung %s weg (Zähler dann pro uid)", (_, token) => {
    expect(callerOf(requestWithToken("bob", token))).toEqual({ uid: "bob" });
  });

  it("übergibt fehlende Felder als undefined (Validierung im Service)", async () => {
    const service = new FakeGroupService();

    await createGroupHandler(service, new RecordingLogger())(request(null, { uid: "anna" }));
    await joinGroupHandler(service, new RecordingLogger())(request("K7MX2QAB", { uid: "anna" }));

    expect(service.calls.map((call) => call.input)).toEqual([undefined, undefined]);
  });

  it("übersetzt fachliche Fehler 1:1 in HttpsError", async () => {
    const service = new FakeGroupService();
    service.failure = SocialErrors.groupNotFound();

    const error = await rejection(joinGroupHandler(service, new RecordingLogger())(request({ code: "K7MX2QAB" }, { uid: "bob" })));

    expect(error.code).toBe("not-found");
    expect(error.message).toBe("Zu diesem Code gibt es keine Runde.");
  });

  it.each([
    ["Runde voll", SocialErrors.groupFull(50), "failed-precondition"],
    ["zu viele falsche Codes", SocialErrors.tooManyJoinAttempts(), "resource-exhausted"],
  ])("übersetzt %s in %s", async (_, failure, code) => {
    const service = new FakeGroupService();
    service.failure = failure;

    const error = await rejection(joinGroupHandler(service, new RecordingLogger())(request({ code: "K7MX2QAB" }, { uid: "bob" })));

    expect(error.code).toBe(code);
    expect(error.message).toBe(failure.message);
  });

  it("meldet unerwartete Fehler beim Konto-Löschen als internal", async () => {
    const service = new FakeAccountService();
    const logger = new RecordingLogger();
    service.failure = new Error("Auth weg");

    const error = await rejection(deleteAccountHandler(service, logger)(request({}, { uid: "bob" })));

    expect(error.code).toBe("internal");
    expect(error.message).toBe(INTERNAL_ERROR_MESSAGE);
    expect(logger.entries.map((entry) => entry.message)).toEqual(["deleteAccount: unerwarteter Fehler"]);
  });

  it("meldet unerwartete Fehler als internal und protokolliert sie", async () => {
    const service = new FakeGroupService();
    const logger = new RecordingLogger();
    service.failure = new Error("Datenbank weg");

    const error = await rejection(createGroupHandler(service, logger)(request({ name: "x" }, { uid: "anna" })));

    expect(error.code).toBe("internal");
    expect(error.message).toBe(INTERNAL_ERROR_MESSAGE);
    expect(logger.entries).toHaveLength(1);
    expect(logger.entries[0]?.level).toBe("error");
  });
});
