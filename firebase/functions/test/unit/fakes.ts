import type { DrinkNotification } from "../../src/domain/drinkNotification";
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

export class InMemoryTokenStore implements DeviceTokenStore {
  readonly requestedUserIds: string[][] = [];

  constructor(public tokens: DeviceToken[] = []) {}

  tokensOf(userIds: readonly string[]): Promise<DeviceToken[]> {
    this.requestedUserIds.push([...userIds]);
    return Promise.resolve(this.tokens.filter((token) => userIds.includes(token.userId)));
  }

  remove(tokens: readonly DeviceToken[]): Promise<void> {
    this.tokens = this.tokens.filter(
      (stored) => !tokens.some((removed) => removed.userId === stored.userId && removed.token === stored.token),
    );
    return Promise.resolve();
  }
}

/** Versendet nichts, merkt sich die Aufrufe und liefert vorgegebene Fehlercodes. */
export class FakeSender implements NotificationSender {
  readonly calls: { tokens: string[]; notification: DrinkNotification }[] = [];

  constructor(private readonly errorCodes: Record<string, string> = {}) {}

  send(tokens: readonly string[], notification: DrinkNotification): Promise<SendResult[]> {
    this.calls.push({ tokens: [...tokens], notification });
    return Promise.resolve(
      tokens.map((token) => {
        const errorCode = this.errorCodes[token];
        return errorCode === undefined ? { token } : { token, errorCode };
      }),
    );
  }
}

/** Eine Runde mit den angegebenen Mitgliedern (erstes Mitglied = Besitzer). */
export function groupOf(memberIds: string[], name = "Stammtisch"): GroupState {
  return {
    name,
    inviteCode: "K7MX2Q",
    ownerId: memberIds[0] ?? "",
    memberIds,
    members: Object.fromEntries(memberIds.map((id) => [id, { displayName: `Name ${id}` }])),
  };
}
