import { HttpsError } from "firebase-functions/v2/https";
import type { CallableRequest } from "firebase-functions/v2/https";
import type { AccountService } from "../accounts/accountService";
import { SocialError, SocialErrors } from "../domain/errors";
import type { GroupDto } from "../domain/group";
import type { Caller, GroupService } from "../groups/groupService";
import type { Logger } from "../notifications/ports";

// Übersetzt zwischen dem Callable-Protokoll (Anfrage/HttpsError) und GroupService/AccountService.
// Hier steckt keine Fachlogik – nur Anmeldung prüfen, Felder herausziehen, Fehler übersetzen.

type CallableHandler<Response> = (request: CallableRequest<unknown>) => Promise<Response>;

/** Meldung für unerwartete Fehler (Details nur im Log). */
export const INTERNAL_ERROR_MESSAGE = "Da ist etwas schiefgelaufen. Bitte versuche es später noch einmal.";

/** `createGroup({ name })` → `{ group }` */
export function createGroupHandler(
  service: GroupService,
  logger: Logger,
): CallableHandler<{ group: GroupDto }> {
  return translateErrors(logger, "createGroup", async (request) => ({
    group: await service.createGroup(callerOf(request), field(request.data, "name")),
  }));
}

/** `joinGroup({ code })` → `{ group }` */
export function joinGroupHandler(
  service: GroupService,
  logger: Logger,
): CallableHandler<{ group: GroupDto }> {
  return translateErrors(logger, "joinGroup", async (request) => ({
    group: await service.joinGroup(callerOf(request), field(request.data, "code")),
  }));
}

/** `leaveGroup({ groupId })` → `{}` */
export function leaveGroupHandler(
  service: GroupService,
  logger: Logger,
): CallableHandler<Record<string, never>> {
  return translateErrors(logger, "leaveGroup", async (request) => {
    await service.leaveGroup(callerOf(request), field(request.data, "groupId"));
    return {};
  });
}

/** `deleteAccount({})` → `{}` (idempotent) */
export function deleteAccountHandler(
  service: AccountService,
  logger: Logger,
): CallableHandler<Record<string, never>> {
  return translateErrors(logger, "deleteAccount", async (request) => {
    await service.deleteAccount(callerOf(request));
    return {};
  });
}

/** Der angemeldete Aufrufer; ohne Anmeldung `unauthenticated`. */
export function callerOf(request: CallableRequest<unknown>): Caller {
  const auth = request.auth;
  if (!auth) throw SocialErrors.unauthenticated();
  const tokenName: unknown = auth.token.name;
  return typeof tokenName === "string" ? { uid: auth.uid, tokenName } : { uid: auth.uid };
}

/** Übersetzt Fehler in `HttpsError`: fachliche Fehler 1:1, alles Unerwartete als `internal`. */
export function toHttpsError(error: unknown, logger: Logger, functionName: string): HttpsError {
  if (error instanceof HttpsError) return error;
  if (error instanceof SocialError) return new HttpsError(error.code, error.message);
  logger.error(`${functionName}: unerwarteter Fehler`, {
    error: error instanceof Error ? (error.stack ?? error.message) : String(error),
  });
  return new HttpsError("internal", INTERNAL_ERROR_MESSAGE);
}

function translateErrors<Response>(
  logger: Logger,
  functionName: string,
  handler: CallableHandler<Response>,
): CallableHandler<Response> {
  return async (request) => {
    try {
      return await handler(request);
    } catch (error) {
      throw toHttpsError(error, logger, functionName);
    }
  };
}

function field(data: unknown, key: string): unknown {
  return typeof data === "object" && data !== null ? (data as Record<string, unknown>)[key] : undefined;
}
