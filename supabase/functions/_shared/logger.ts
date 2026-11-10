/**
 * Einfaches, strukturiertes Protokoll (eine JSON-Zeile pro Eintrag) für die Logs der Edge Functions.
 * Niemals Tokens, Schlüssel oder vollständige Push-Endpoints protokollieren – Endpoints sind Geheimnisse.
 */
export interface Logger {
  info(message: string, data?: Record<string, unknown>): void;
  warn(message: string, data?: Record<string, unknown>): void;
  error(message: string, data?: Record<string, unknown>): void;
}

/** Schreibt auf die Konsole (erscheint im Supabase-Dashboard unter Edge Functions → Logs). */
export function consoleLogger(functionName: string): Logger {
  const write = (level: string, message: string, data?: Record<string, unknown>) => {
    const line = JSON.stringify({ level, function: functionName, message, ...data });
    if (level === "error") console.error(line);
    else if (level === "warn") console.warn(line);
    else console.log(line);
  };
  return {
    info: (message, data) => write("info", message, data),
    warn: (message, data) => write("warn", message, data),
    error: (message, data) => write("error", message, data),
  };
}

/** Verwirft alles (für Tests). */
export const silentLogger: Logger = { info() {}, warn() {}, error() {} };

/** Fehlertext für das Protokoll, ohne Stacktrace-Details nach außen zu geben. */
export function describeError(error: unknown): string {
  return error instanceof Error ? `${error.name}: ${error.message}` : String(error);
}
