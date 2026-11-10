// ESLint (Flat Config) für die Web-App: empfohlene Regeln für JavaScript und TypeScript.
import js from "@eslint/js";
import globals from "globals";
import tseslint from "typescript-eslint";

export default tseslint.config(
  { ignores: ["dist/", "dist-e2e/", "dist-e2e-ohne-server/", "public/", "playwright-report/", "test-results/", "node_modules/"] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    languageOptions: {
      ecmaVersion: 2023,
      sourceType: "module",
      globals: { ...globals.browser, ...globals.node },
    },
    rules: {
      "@typescript-eslint/no-unused-vars": ["error", { argsIgnorePattern: "^_", varsIgnorePattern: "^_" }],
      "@typescript-eslint/consistent-type-imports": "error",
      eqeqeq: ["error", "always", { null: "ignore" }],
      "no-console": ["error", { allow: ["warn", "error", "log"] }],
    },
  },
);
