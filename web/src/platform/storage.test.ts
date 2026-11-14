import { describe, expect, it } from "vitest";
import { createPersistentStorage } from "./storage";

describe("Dauerhafter Speicher (navigator.storage.persist)", () => {
  it("fragt nur, wenn der Speicher noch nicht dauerhaft ist", async () => {
    const calls: string[] = [];
    const storage = createPersistentStorage({
      storage: {
        persisted: async () => (calls.push("persisted"), false),
        persist: async () => (calls.push("persist"), true),
      },
    });
    expect(await storage.request()).toBe(true);
    expect(calls).toEqual(["persisted", "persist"]);

    const already = createPersistentStorage({ storage: { persisted: async () => true, persist: async () => (calls.push("unnötig"), true) } });
    expect(await already.request()).toBe(true);
    expect(calls).not.toContain("unnötig");
  });

  it("ohne Unterstützung oder bei Fehlern: false, nie eine Ausnahme", async () => {
    expect(await createPersistentStorage({}).request()).toBe(false);
    const broken = createPersistentStorage({
      storage: {
        persisted: async () => {
          throw new Error("SecurityError");
        },
        persist: async () => true,
      },
    });
    expect(await broken.request()).toBe(false);
  });
});
