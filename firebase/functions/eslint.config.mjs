// ESLint (Flat Config) mit typgestützten Regeln von typescript-eslint.
import js from "@eslint/js";
import { defineConfig } from "eslint/config";
import tseslint from "typescript-eslint";

export default defineConfig(
  { ignores: ["lib/**", "node_modules/**", "coverage/**"] },
  js.configs.recommended,
  tseslint.configs.recommendedTypeChecked,
  {
    languageOptions: {
      parserOptions: {
        project: "./tsconfig.dev.json",
        tsconfigRootDir: import.meta.dirname,
      },
    },
    rules: {
      "@typescript-eslint/consistent-type-imports": "error",
      "@typescript-eslint/no-floating-promises": "error",
      eqeqeq: ["error", "always"],
    },
  },
  {
    files: ["**/*.mjs"],
    extends: [tseslint.configs.disableTypeChecked],
  },
);
