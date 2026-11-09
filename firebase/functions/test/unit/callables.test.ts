import type { CallableRequest } from "firebase-functions/v2/https";
import { HttpsError } from "firebase-functions/v2/https";
import { describe, expect, it } from "vitest";
import { SocialErrors } from "../../src/domain/errors";
import type { GroupDto } from "../../src/domain/group";
import type { Caller, GroupService } from "../../src/groups/groupService";
import {
  INTERNAL_ERROR_MESSAGE,
  createGroupHandler,
  joinGroupHandler,
  leaveGroupHandler,
} from "../../src/handlers/callables";
import { RecordingLogger } from "./fakes";

const group: GroupDto = {
  id: "g1",
  name: "Stammtisch",
  inviteCode: "K7MX2Q",
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

  private record<T>(method: string, caller: Caller, input: unknown, result: T): Promise<T> {
    this.calls.push({ method, caller, input });
    return this.failure === undefined ? Promise.resolve(result) : Promise.reject(this.failure);
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

    const response = await joinGroupHandler(service, new RecordingLogger())(request({ code: "k7mx2q" }, { uid: "bob" }));

    expect(response).toEqual({ group });
    expect(service.calls).toEqual([{ method: "joinGroup", caller: { uid: "bob" }, input: "k7mx2q" }]);
  });

  it("leaveGroup antwortet mit {}", async () => {
    const service = new FakeGroupService();

    const response = await leaveGroupHandler(service, new RecordingLogger())(request({ groupId: "g1" }, { uid: "bob" }));

    expect(response).toEqual({});
    expect(service.calls).toEqual([{ method: "leaveGroup", caller: { uid: "bob" }, input: "g1" }]);
  });

  it("verlangt eine Anmeldung (unauthenticated)", async () => {
    const service = new FakeGroupService();
    const logger = new RecordingLogger();

    for (const handler of [createGroupHandler, joinGroupHandler, leaveGroupHandler]) {
      const error = await rejection(handler(service, logger)(request({ name: "x", code: "x", groupId: "x" })));
      expect(error.code).toBe("unauthenticated");
      expect(error.message).toBe("Bitte melde dich zuerst an.");
    }
    expect(service.calls).toEqual([]);
  });

  it("übergibt fehlende Felder als undefined (Validierung im Service)", async () => {
    const service = new FakeGroupService();

    await createGroupHandler(service, new RecordingLogger())(request(null, { uid: "anna" }));
    await joinGroupHandler(service, new RecordingLogger())(request("K7MX2Q", { uid: "anna" }));

    expect(service.calls.map((call) => call.input)).toEqual([undefined, undefined]);
  });

  it("übersetzt fachliche Fehler 1:1 in HttpsError", async () => {
    const service = new FakeGroupService();
    service.failure = SocialErrors.groupNotFound();

    const error = await rejection(joinGroupHandler(service, new RecordingLogger())(request({ code: "K7MX2Q" }, { uid: "bob" })));

    expect(error.code).toBe("not-found");
    expect(error.message).toBe("Zu diesem Code gibt es keine Runde.");
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
