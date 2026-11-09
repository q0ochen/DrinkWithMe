import type { GroupState } from "../domain/group";
import type { SharedDrink } from "../domain/drinkNotification";
import { buildDrinkMessage, parseSharedDrink, selectRecipients } from "../domain/drinkNotification";
import { invalidTokens } from "./invalidTokens";
import type { DeviceToken, DeviceTokenStore, Logger, NotificationSender } from "./ports";

/** Liest eine Runde (in Produktion aus Firestore). */
export interface GroupLookup {
  find(groupId: string): Promise<GroupState | null>;
}

/** Laut Vertrag: je Empfänger höchstens 5 Tokens (die neuesten) – begrenzt Kosten und Missbrauch. */
export const MAX_TOKENS_PER_RECIPIENT = 5;

export interface DrinkNotifierDependencies {
  groups: GroupLookup;
  tokens: DeviceTokenStore;
  sender: NotificationSender;
  logger: Logger;
}

/** Ergebnis eines Durchlaufs – für Logs und Tests. */
export interface NotifyOutcome {
  /** Mitglieder außer dem Autor. */
  recipients: number;
  /** Gelesene Tokens (höchstens 5 je Empfänger). */
  tokens: number;
  /** Erfolgreich an FCM übergebene Nachrichten (pro Token). */
  delivered: number;
  /** Gelöschte ungültige Tokens. */
  removedTokens: number;
  /** Empfänger, bei denen Lesen oder Versand mit einem Fehler abbrach. */
  failedRecipients: number;
}

const NOTHING_TO_DO: NotifyOutcome = {
  recipients: 0,
  tokens: 0,
  delivered: 0,
  removedTokens: 0,
  failedRecipients: 0,
};

/** Ergebnis für einen einzelnen Empfänger. */
interface RecipientOutcome {
  tokens: number;
  delivered: number;
  invalid: DeviceToken[];
}

/**
 * Benachrichtigt alle anderen Rundenmitglieder über ein neu geteiltes Getränk und räumt dabei
 * ungültige Geräte-Tokens auf. Jeder Empfänger bekommt eine eigene Daten-Nachricht mit seiner
 * `recipientId` an seine (höchstens 5 neuesten) Geräte – so zeigt ein Gerät, dessen Token noch
 * bei einem abgemeldeten Konto hängt, fremde Nachrichten nicht an.
 */
export class DrinkNotifier {
  constructor(private readonly deps: DrinkNotifierDependencies) {}

  async onDrinkCreated(groupId: string, docId: string, data: unknown): Promise<NotifyOutcome> {
    const { groups, tokens, logger } = this.deps;

    const drink = parseSharedDrink(data);
    if (drink === null) {
      logger.warn("Getränk ohne gültige Felder – keine Benachrichtigung.", { groupId, docId });
      return NOTHING_TO_DO;
    }

    const group = await groups.find(groupId);
    if (group === null) {
      logger.warn("Runde nicht gefunden – keine Benachrichtigung.", { groupId, docId });
      return NOTHING_TO_DO;
    }

    const recipients = selectRecipients(group.memberIds, drink.authorId);
    if (recipients.length === 0) return NOTHING_TO_DO;

    // Ein Fehler bei einem Empfänger darf die anderen nicht aufhalten.
    const settled = await Promise.allSettled(
      recipients.map((recipientId) => this.notify(recipientId, drink, { groupId, docId }, group.name)),
    );

    const outcomes: RecipientOutcome[] = [];
    settled.forEach((result, index) => {
      if (result.status === "fulfilled") {
        outcomes.push(result.value);
        return;
      }
      const reason: unknown = result.reason;
      logger.error("Benachrichtigung eines Empfängers fehlgeschlagen.", {
        groupId,
        docId,
        recipientId: recipients[index],
        error: reason instanceof Error ? (reason.stack ?? reason.message) : String(reason),
      });
    });

    const toRemove = outcomes.flatMap((outcome) => outcome.invalid);
    if (toRemove.length > 0) await tokens.remove(toRemove);

    const outcome: NotifyOutcome = {
      recipients: recipients.length,
      tokens: sum(outcomes.map((recipient) => recipient.tokens)),
      delivered: sum(outcomes.map((recipient) => recipient.delivered)),
      removedTokens: toRemove.length,
      failedRecipients: recipients.length - outcomes.length,
    };
    logger.info("Benachrichtigung zu neuem Getränk versendet.", { groupId, docId, ...outcome });
    return outcome;
  }

  private async notify(
    recipientId: string,
    drink: SharedDrink,
    location: { groupId: string; docId: string },
    groupName: string,
  ): Promise<RecipientOutcome> {
    const { tokens, sender } = this.deps;
    const recipientTokens = [...new Set(await tokens.newestTokensOf(recipientId, MAX_TOKENS_PER_RECIPIENT))];
    if (recipientTokens.length === 0) return { tokens: 0, delivered: 0, invalid: [] };

    const results = await sender.send(recipientTokens, buildDrinkMessage(drink, location, groupName, recipientId));
    return {
      tokens: recipientTokens.length,
      delivered: results.filter((result) => result.errorCode === undefined).length,
      invalid: invalidTokens(results).map((token) => ({ userId: recipientId, token })),
    };
  }
}

function sum(values: readonly number[]): number {
  return values.reduce((total, value) => total + value, 0);
}
