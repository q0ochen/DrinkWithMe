/** Maschinenlesbare Fehler der Vernetzung (RPC-Meldungen und Edge-Function-Codes aus supabase/README.md). */
export type SocialErrorCode =
  | "not_configured"
  | "not_authenticated"
  | "rate_limited"
  | "invite_invalid"
  | "invite_own"
  | "too_many_subscriptions"
  | "request_not_found"
  | "push_unsupported"
  | "push_denied"
  | "network"
  | "unknown";

const MESSAGES: Record<SocialErrorCode, string> = {
  not_configured: "Server noch nicht eingerichtet.",
  not_authenticated: "Bitte melde dich erneut mit Google an.",
  rate_limited: "Für heute sind keine weiteren Versuche möglich. Bitte versuche es morgen noch einmal.",
  invite_invalid: "Dieser Einladungs-Link ist ungültig oder abgelaufen. Bitte lass dir einen neuen schicken.",
  invite_own: "Das ist dein eigener Einladungs-Link. Schicke ihn an jemanden, mit dem du dich vernetzen möchtest.",
  too_many_subscriptions:
    "Benachrichtigungen sind schon auf fünf Geräten eingeschaltet. Schalte sie auf einem anderen Gerät aus und versuche es erneut.",
  request_not_found: "Diese Anfrage gibt es nicht mehr.",
  push_unsupported: "Dieser Browser unterstützt keine Benachrichtigungen.",
  push_denied: "Benachrichtigungen sind für diese Seite blockiert. Du kannst sie in den Einstellungen des Browsers erlauben.",
  network: "Keine Verbindung zum Server. Bitte versuche es später noch einmal.",
  unknown: "Das hat leider nicht geklappt. Bitte versuche es später noch einmal.",
};

const SERVER_CODES: ReadonlySet<string> = new Set([
  "not_authenticated",
  "rate_limited",
  "invite_invalid",
  "invite_own",
  "too_many_subscriptions",
  "request_not_found",
]);

export class SocialError extends Error {
  constructor(
    readonly code: SocialErrorCode,
    cause?: unknown,
  ) {
    super(MESSAGES[code], { cause });
    this.name = "SocialError";
  }
}

/** Deutscher Text zu einem Fehler (auch zu unerwarteten). */
export function germanMessage(error: unknown): string {
  return error instanceof SocialError ? error.message : MESSAGES.unknown;
}

interface PostgrestLikeError {
  readonly message?: string;
  readonly code?: string;
  readonly details?: string | null;
  readonly hint?: string | null;
}

/** Übersetzt einen PostgREST-Fehler (RPC-Fehler kommen als P0001 mit maschinenlesbarer `message`). */
export function fromPostgrestError(error: PostgrestLikeError, status?: number): SocialError {
  const message = (error.message ?? "").trim();
  if (SERVER_CODES.has(message)) return new SocialError(message as SocialErrorCode, error);
  if (status === 401 || error.code === "PGRST301" || error.code === "PGRST303") {
    return new SocialError("not_authenticated", error);
  }
  if (status === 0 || /fetch|network|failed to/i.test(message)) return new SocialError("network", error);
  return new SocialError("unknown", error);
}
