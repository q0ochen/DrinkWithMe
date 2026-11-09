import { defineConfig } from "vitest/config";

// Tests gegen die Firebase-Emulatoren (Auth, Firestore, Functions).
// Start: aus firebase/ mit `npx firebase emulators:exec … "npm --prefix functions run test:emulator"`.
export default defineConfig({
  test: {
    include: ["test/emulator/**/*.test.ts"],
    environment: "node",
    // Alle Dateien teilen sich dieselben Emulatoren – nacheinander ausführen.
    fileParallelism: false,
    testTimeout: 30_000,
    hookTimeout: 60_000,
  },
});
