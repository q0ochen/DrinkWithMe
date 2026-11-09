import { describe, expect, it } from "vitest";
import * as functions from "../../src/index";

// Prüft die Registrierung der Functions (Namen, Region, Trigger) ohne Emulator:
// Das Firebase-SDK hängt die Deploy-Beschreibung als `__endpoint` an jede Funktion.
interface Endpoint {
  platform: string;
  region?: string[];
  timeoutSeconds?: number;
  callableTrigger?: unknown;
  eventTrigger?: {
    eventType: string;
    eventFilters: Record<string, string>;
    eventFilterPathPatterns: Record<string, string>;
    retry: boolean;
  };
}

function endpoints(): Record<string, Endpoint> {
  const exported = functions as unknown as Record<string, { __endpoint: Endpoint }>;
  return Object.fromEntries(Object.entries(exported).map(([name, fn]) => [name, fn.__endpoint]));
}

describe("Functions-Registrierung", () => {
  it("exportiert genau die Funktionen aus dem Vertrag", () => {
    expect(Object.keys(endpoints()).sort()).toEqual([
      "createGroup",
      "deleteAccount",
      "joinGroup",
      "leaveGroup",
      "onGroupDeleted",
      "onGroupDrinkCreated",
    ]);
  });

  it("legt alle Funktionen als 2nd gen in europe-west3 an", () => {
    for (const endpoint of Object.values(endpoints())) {
      expect(endpoint.platform).toBe("gcfv2");
      expect(endpoint.region).toEqual(["europe-west3"]);
    }
  });

  it("stellt createGroup, joinGroup, leaveGroup und deleteAccount als Callables bereit", () => {
    const all = endpoints();
    for (const name of ["createGroup", "joinGroup", "leaveGroup", "deleteAccount"]) {
      expect(all[name]?.callableTrigger, name).toBeDefined();
    }
  });

  it("gibt deleteAccount 540 s Zeit (viele Getränke)", () => {
    expect(endpoints().deleteAccount?.timeoutSeconds).toBe(540);
  });

  it("triggert onGroupDrinkCreated beim Anlegen von groups/{groupId}/drinks/{docId}", () => {
    const trigger = endpoints().onGroupDrinkCreated?.eventTrigger;

    expect(trigger?.eventType).toBe("google.cloud.firestore.document.v1.created");
    expect(trigger?.eventFilterPathPatterns).toEqual({ document: "groups/{groupId}/drinks/{docId}" });
    expect(trigger?.eventFilters.database).toBe("(default)");
    expect(trigger?.retry).toBe(false);
  });

  it("triggert onGroupDeleted beim Löschen von groups/{groupId} – mit Wiederholung und 540 s", () => {
    const endpoint = endpoints().onGroupDeleted;
    const trigger = endpoint?.eventTrigger;

    expect(trigger?.eventType).toBe("google.cloud.firestore.document.v1.deleted");
    expect(trigger?.eventFilterPathPatterns).toEqual({ document: "groups/{groupId}" });
    expect(trigger?.eventFilters.database).toBe("(default)");
    expect(trigger?.retry).toBe(true);
    expect(endpoint?.timeoutSeconds).toBe(540);
  });
});
