import { defineConfig } from "vitest/config";

// Unit-Tests der reinen Logik – laufen ohne Emulatoren.
export default defineConfig({
  test: {
    include: ["test/unit/**/*.test.ts"],
    environment: "node",
  },
});
