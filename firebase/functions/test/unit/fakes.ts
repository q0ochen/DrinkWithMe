import type { DrinkMessage } from "../../src/domain/drinkNotification";
import type { GroupState } from "../../src/domain/group";
import type { GroupLookup } from "../../src/notifications/drinkNotifier";
import type {
  DeviceToken,
  DeviceTokenStore,
  Logger,
  NotificationSender,
  SendResult,
} from "../../src/notifications/ports";

/** Merkt sich alle Log-Einträge. */
export class RecordingLogger implements Logger {
  readonly entries: { level: "info" | "warn" | "error"; message: string; data?: Record<string, unknown> }[] = [];

  info(message: string, data?: Record<string, unknown>): void {
    this.entries.push({ level: "info", message, data });
  }

  warn(message: string, data?: Record<string, unknown>): void {
    this.entries.push({ level: "warn", message, data });
  }

  error(message: string, data?: Record<string, unknown>): void {
    this.entries.push({ level: "error", message, data });
  }
}

export class InMemoryGroupLookup implements GroupLookup {
  constructor(private readonly groups: Record<string, GroupState> = {}) {}

  find(groupId: string): Promise<GroupState | null> {
    return Promise.resolve(this.groups[groupId] ?? null);
  }
}

/** Tokens in Registrierungsreihenfolge: Spätere Einträge gelten als neuer (`updatedAt`). */
export class InMemoryTokenStore implements DeviceTokenStore {
  readonly requests: { userId: string; limit: number }[] = [];
  /** Nutzer, bei denen das Lesen scheitern soll. */
  readonly failingUsers = new Set<string>();

  constructor(public tokens: DeviceToken[] = []) {}

  newestTokensOf(userId: string, limit: number): Promise<string[]> {
    this.requests.push({ userId, limit });
    if (this.failingUsers.has(userId)) return Promise.reject(new Error(`Tokens von ${userId} nicht lesbar`));
    const newestFirst = this.tokens.filter((token) => token.userId === userId).reverse();
    return Promise.resolve(newestFirst.slice(0, limit).map((token) => token.token));
  }

  remove(tokens: readonly DeviceToken[]): Promise<void> {
    this.tokens = this.tokens.filter(
      (stored) => !tokens.some((removed) => removed.userId === stored.userId && removed.token === stored.token),
    );
    return Promise.resolve();
  }
}

/** Versendet nichts, merkt sich die Aufrufe und liefert vorgegebene Fehler pro Token. */
export class FakeSender implements NotificationSender {
  readonly calls: { tokens: string[]; message: DrinkMessage }[] = [];

  constructor(private readonly errors: Record<string, { code: string; message?: string }> = {}) {}

  send(tokens: readonly string[], message: DrinkMessage): Promise<SendResult[]> {
    this.calls.push({ tokens: [...tokens], message });
    return Promise.resolve(
      tokens.map((token): SendResult => {
        const error = this.errors[token];
        if (error === undefined) return { token };
        return error.message === undefined
          ? { token, errorCode: error.code }
          : { token, errorCode: error.code, errorMessage: error.message };
      }),
    );
  }

  /** Der Aufruf für einen Empfänger (jeder Empfänger bekommt genau eine Nachricht). */
  callFor(recipientId: string) {
    return this.calls.find((call) => call.message.data.recipientId === recipientId);
  }
}

/** Eine Runde mit den angegebenen Mitgliedern (erstes Mitglied = Besitzer). */
export function groupOf(memberIds: string[], name = "Stammtisch"): GroupState {
  return {
    name,
    inviteCode: "K7MX2QAB",
    ownerId: memberIds[0] ?? "",
    memberIds,
    members: Object.fromEntries(memberIds.map((id) => [id, { displayName: `Name ${id}` }])),
  };
}
