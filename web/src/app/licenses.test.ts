import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import { THIRD_PARTY_LICENSES } from "./licenses";

const here = path.dirname(fileURLToPath(import.meta.url));
const lock = JSON.parse(readFileSync(path.resolve(here, "../../package-lock.json"), "utf8")) as {
  packages: Record<string, { dev?: boolean; optional?: boolean; license?: string; devOptional?: boolean }>;
};

/** Nur freie, F-Droid-taugliche Lizenzen in der ausgelieferten App. */
const ALLOWED = new Set(["MIT", "ISC", "0BSD", "BSD-2-Clause", "BSD-3-Clause", "Apache-2.0"]);

describe("Lizenzen der ausgelieferten Bibliotheken", () => {
  const runtime = Object.entries(lock.packages)
    .filter(([key, value]) => key.startsWith("node_modules/") && !value.dev && !value.devOptional)
    .map(([key, value]) => ({ name: key.replace(/^.*node_modules\//, ""), license: value.license ?? "?" }));

  it("die Liste in den Einstellungen nennt jede Laufzeit-Abhängigkeit mit ihrer Lizenz", () => {
    const listed = new Map(THIRD_PARTY_LICENSES.map((entry) => [entry.name, entry.license]));
    for (const dependency of runtime) expect(listed.get(dependency.name), dependency.name).toBe(dependency.license);
    expect(new Set(runtime.map((dependency) => dependency.name))).toEqual(new Set(listed.keys()));
  });

  it("alle Lizenzen sind frei", () => {
    for (const dependency of runtime) expect(ALLOWED.has(dependency.license), `${dependency.name}: ${dependency.license}`).toBe(true);
  });
});
