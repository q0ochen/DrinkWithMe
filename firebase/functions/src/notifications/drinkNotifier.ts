import type { GroupState } from "../domain/group";
import { buildDrinkNotification, parseSharedDrink, selectRecipients } from "../domain/drinkNotification";
import { invalidTokens } from "./invalidTokens";
import type { DeviceTokenStore, Logger, NotificationSender } from "./ports";

/** Liest eine Runde (in Produktion aus Firestore). */
export interface GroupLookup {
  find(groupId: string): Promise<GroupState | null>;
}

export interface DrinkNotifierDependencies {
  groups: GroupLookup;
  tokens: DeviceTokenStore;
  sender: NotificationSender;
  logger: Logger;
}

/** Ergebnis eines Durchlaufs – für Logs und Tests. */
export interface NotifyOutcome {
  recipients: number;
  tokens: number;
  delivered: number;
  removedTokens: number;
}

const NOTHING_TO_DO: NotifyOutcome = { recipients: 0, tokens: 0, delivered: 0, removedTokens: 0 };

/**
 * Benachrichtigt alle anderen Rundenmitglieder über ein neu geteiltes Getränk
 * und räumt dabei ungültige Geräte-Tokens auf.
 */
export class DrinkNotifier {
  constructor(private readonly deps: DrinkNotifierDependencies) {}

  async onDrinkCreated(groupId: string, drinkId: string, data: unknown): Promise<NotifyOutcome> {
    const { groups, tokens, sender, logger } = this.deps;

    const drink = parseSharedDrink(data);
    if (drink === null) {
      logger.warn("Getränk ohne gültige Felder – keine Benachrichtigung.", { groupId, drinkId });
      return NOTHING_TO_DO;
    }

    const group = await groups.find(groupId);
    if (group === null) {
      logger.warn("Runde nicht gefunden – keine Benachrichtigung.", { groupId, drinkId });
      return NOTHING_TO_DO;
    }

    const recipients = selectRecipients(group.memberIds, drink.authorId);
    if (recipients.length === 0) return NOTHING_TO_DO;

    const deviceTokens = await tokens.tokensOf(recipients);
    const uniqueTokens = [...new Set(deviceTokens.map((deviceToken) => deviceToken.token))];
    if (uniqueTokens.length === 0) {
      return { ...NOTHING_TO_DO, recipients: recipients.length };
    }

    const notification = buildDrinkNotification(drink, { groupId, drinkId }, group.name);
    const results = await sender.send(uniqueTokens, notification);

    const toRemove = invalidTokens(deviceTokens, results);
    if (toRemove.length > 0) await tokens.remove(toRemove);

    const outcome: NotifyOutcome = {
      recipients: recipients.length,
      tokens: uniqueTokens.length,
      delivered: results.filter((result) => result.errorCode === undefined).length,
      removedTokens: toRemove.length,
    };
    logger.info("Benachrichtigung zu neuem Getränk versendet.", { groupId, drinkId, ...outcome });
    return outcome;
  }
}
