/**
 * Konfiguration aus Vite-Umgebungsvariablen (öffentliche, unkritische Werte):
 * VITE_SUPABASE_URL, VITE_SUPABASE_ANON_KEY, VITE_VAPID_PUBLIC_KEY, VITE_WEB_APP_URL.
 * Fehlt die Supabase-Konfiguration, funktioniert alles lokal; der Bereich Freunde zeigt
 * „Server noch nicht eingerichtet“.
 */
export interface SupabaseConfig {
  readonly url: string;
  readonly anonKey: string;
}

export interface AppConfig {
  /** `null` = Server noch nicht eingerichtet. */
  readonly supabase: SupabaseConfig | null;
  /** Öffentlicher VAPID-Schlüssel (base64url) oder `null`. */
  readonly vapidPublicKey: string | null;
  /** Adresse der Web-App ohne abschließenden Schrägstrich, z. B. https://q0ochen.github.io/DrinkWithMe */
  readonly webAppUrl: string;
  /** Vite-`base`, z. B. „/DrinkWithMe/“. */
  readonly basePath: string;
  /** Version der App (aus package.json). */
  readonly version: string;
}

export const DEFAULT_WEB_APP_URL = "https://q0ochen.github.io/DrinkWithMe";

export interface RawEnv {
  readonly VITE_SUPABASE_URL?: string;
  readonly VITE_SUPABASE_ANON_KEY?: string;
  readonly VITE_VAPID_PUBLIC_KEY?: string;
  readonly VITE_WEB_APP_URL?: string;
  readonly BASE_URL?: string;
  readonly VITE_APP_VERSION?: string;
}

function clean(value: string | undefined): string {
  return (value ?? "").trim();
}

function isHttpsUrl(value: string, allowLocalHttp: boolean): boolean {
  try {
    const url = new URL(value);
    if (url.protocol === "https:") return true;
    return allowLocalHttp && url.protocol === "http:" && (url.hostname === "localhost" || url.hostname === "127.0.0.1");
  } catch {
    return false;
  }
}

/** Liest und prüft die Konfiguration (reine Funktion, damit testbar). */
export function readConfig(env: RawEnv): AppConfig {
  const url = clean(env.VITE_SUPABASE_URL).replace(/\/+$/, "");
  const anonKey = clean(env.VITE_SUPABASE_ANON_KEY);
  const vapid = clean(env.VITE_VAPID_PUBLIC_KEY);
  const webAppUrl = (clean(env.VITE_WEB_APP_URL) || DEFAULT_WEB_APP_URL).replace(/\/+$/, "");
  const basePath = clean(env.BASE_URL) || "/";
  return {
    supabase: url && anonKey && isHttpsUrl(url, true) ? { url, anonKey } : null,
    vapidPublicKey: /^[A-Za-z0-9_-]{80,100}={0,2}$/.test(vapid) ? vapid : null,
    webAppUrl: isHttpsUrl(webAppUrl, true) ? webAppUrl : DEFAULT_WEB_APP_URL,
    basePath: basePath.endsWith("/") ? basePath : `${basePath}/`,
    version: clean(env.VITE_APP_VERSION) || "dev",
  };
}

/** Rückkehradresse nach der Google-Anmeldung (muss in Supabase exakt so eingetragen sein). */
export function authRedirectUrl(config: AppConfig): string {
  return `${config.webAppUrl}/auth/callback`;
}
