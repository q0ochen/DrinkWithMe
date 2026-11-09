import { describe, expect, it } from "vitest";
import * as functions from "../../src/index";

// Prüft die Registrierung der Functions (Namen, Region, Trigger) ohne Emulator:
// Das Firebase-SDK hängt die Deploy-Beschreibung als `__endpoint` an jede Funktion.
interface Endpoint {
  platform: string;
  region?: string[];
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
      "joinGroup",
      "leaveGroup",
      "onGroupDrinkCreated",
    ]);
  });

  it("legt alle Funktionen als 2nd gen in europe-west3 an", () => {
    for (const endpoint of Object.values(endpoints())) {
      expect(endpoint.platform).toBe("gcfv2");
      expect(endpoint.region).toEqual(["europe-west3"]);
    }
  });

  it("stellt createGroup, joinGroup und leaveGroup als Callables bereit", () => {
    const all = endpoints();
    for (const name of ["createGroup", "joinGroup", "leaveGroup"]) {
      expect(all[name]?.callableTrigger, name).toBeDefined();
    }
  });

  it("triggert onGroupDrinkCreated beim Anlegen von groups/{groupId}/drinks/{drinkId}", () => {
    const trigger = endpoints().onGroupDrinkCreated?.eventTrigger;

    expect(trigger?.eventType).toBe("google.cloud.firestore.document.v1.created");
    expect(trigger?.eventFilterPathPatterns).toEqual({ document: "groups/{groupId}/drinks/{drinkId}" });
    expect(trigger?.eventFilters.database).toBe("(default)");
    expect(trigger?.retry).toBe(false);
  });
});
