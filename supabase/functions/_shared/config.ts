/**
 * Konfiguration der Edge Functions aus Umgebungsvariablen.
 *
 * Von Supabase automatisch gesetzt: SUPABASE_URL und SUPABASE_SERVICE_ROLE_KEY (bzw. bei den neuen API-Schlüsseln
 * SUPABASE_SECRET_KEYS). Vom Projektinhaber einmalig gesetzt (siehe supabase/README.md):
 * VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY, VAPID_SUBJECT.
 */

/** Lesezugriff auf Umgebungsvariablen (in Tests durch ein einfaches Objekt ersetzbar). */
export interface Environment {
  get(name: string): string | undefined;
}

/** Fehlende oder ungültige Konfiguration – die Funktion antwortet dann mit 500 `server_misconfigured`. */
export class ConfigError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "ConfigError";
  }
}

/** Zugang zur Supabase-API mit Server-Rechten (nur innerhalb der Edge Functions). */
export interface SupabaseServerConfig {
  /** Projekt-URL, z. B. https://abcd.supabase.co (ohne Schrägstrich am Ende). */
  url: string;
  /** Service-Role-Key (JWT) oder neuer Secret Key (`sb_secret_…`). */
  serviceKey: string;
}

/** VAPID-Konfiguration als base64url-Texte. */
export interface VapidConfig {
  publicKey: string;
  privateKey: string;
  subject: string;
}

function required(env: Environment, name: string, missing: string[]): string {
  const value = env.get(name)?.trim();
  if (!value) {
    missing.push(name);
    return "";
  }
  return value;
}

/** Liest den ersten Schlüssel aus SUPABASE_SECRET_KEYS (JSON-Objekt, z. B. `{"default":"sb_secret_…"}`). */
function firstSecretKey(raw: string | undefined): string | undefined {
  if (!raw) return undefined;
  try {
    const parsed: unknown = JSON.parse(raw);
    if (parsed && typeof parsed === "object") {
      const values = Object.entries(parsed as Record<string, unknown>);
      const preferred = values.find(([name]) => name === "default") ?? values[0];
      return typeof preferred?.[1] === "string" ? preferred[1] : undefined;
    }
  } catch {
    return undefined;
  }
  return undefined;
}

/** Liest Projekt-URL und Server-Schlüssel. */
export function readSupabaseServerConfig(env: Environment): SupabaseServerConfig {
  const missing: string[] = [];
  const url = required(env, "SUPABASE_URL", missing).replace(/\/+$/, "");
  const serviceKey = env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim() || firstSecretKey(env.get("SUPABASE_SECRET_KEYS"));
  if (!serviceKey) missing.push("SUPABASE_SERVICE_ROLE_KEY");
  if (missing.length > 0) {
    throw new ConfigError(`Fehlende Umgebungsvariablen: ${missing.join(", ")}`);
  }
  if (!/^https?:\/\//.test(url)) {
    throw new ConfigError("SUPABASE_URL muss mit http:// oder https:// beginnen");
  }
  return { url, serviceKey: serviceKey as string };
}

/** Liest die VAPID-Schlüssel (Secrets der Edge Functions). */
export function readVapidConfig(env: Environment): VapidConfig {
  const missing: string[] = [];
  const config = {
    publicKey: required(env, "VAPID_PUBLIC_KEY", missing),
    privateKey: required(env, "VAPID_PRIVATE_KEY", missing),
    subject: required(env, "VAPID_SUBJECT", missing),
  };
  if (missing.length > 0) {
    throw new ConfigError(
      `Fehlende Secrets: ${missing.join(", ")} – bitte mit „supabase secrets set“ setzen (supabase/README.md)`,
    );
  }
  return config;
}
