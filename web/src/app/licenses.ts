/**
 * Freie Bibliotheken, die in der Web-App ausgeliefert werden (ein Test prüft, dass die Liste zu package-lock.json
 * passt). Eigener Code und alle Grafiken: CC0 1.0.
 */
export interface LicenseInfo {
  readonly name: string;
  readonly license: string;
  readonly url: string;
}

export const THIRD_PARTY_LICENSES: readonly LicenseInfo[] = [
  { name: "preact", license: "MIT", url: "https://github.com/preactjs/preact" },
  { name: "@supabase/supabase-js", license: "MIT", url: "https://github.com/supabase/supabase-js" },
  { name: "@supabase/auth-js", license: "MIT", url: "https://github.com/supabase/supabase-js" },
  { name: "@supabase/functions-js", license: "MIT", url: "https://github.com/supabase/supabase-js" },
  { name: "@supabase/postgrest-js", license: "MIT", url: "https://github.com/supabase/supabase-js" },
  { name: "@supabase/realtime-js", license: "MIT", url: "https://github.com/supabase/supabase-js" },
  { name: "@supabase/storage-js", license: "MIT", url: "https://github.com/supabase/supabase-js" },
  { name: "@supabase/phoenix", license: "MIT", url: "https://github.com/supabase/phoenix" },
  { name: "iceberg-js", license: "MIT", url: "https://www.npmjs.com/package/iceberg-js" },
  { name: "idb", license: "ISC", url: "https://github.com/jakearchibald/idb" },
  { name: "tslib", license: "0BSD", url: "https://github.com/microsoft/tslib" },
];
