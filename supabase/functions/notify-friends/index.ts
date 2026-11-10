/**
 * Edge Function „notify-friends“ – Einstiegspunkt und Verdrahtung (Composition Root).
 * Logik: service.ts · HTTP: handler.ts · Datenbank: supabase_adapters.ts · Push: ../_shared/webpush/.
 */

import { systemClock } from "../_shared/clock.ts";
import { ConfigError, readSupabaseServerConfig, readVapidConfig } from "../_shared/config.ts";
import { consoleLogger } from "../_shared/logger.ts";
import { GoTrueTokenVerifier, PostgrestClient } from "../_shared/supabase_api.ts";
import { WebPushSender } from "../_shared/webpush/sender.ts";
import { importVapidCredentials, VapidKeyError } from "../_shared/webpush/vapid.ts";
import { createNotifyFriendsHandler, type NotifyFriendsHandlerDeps } from "./handler.ts";
import {
  PostgrestDrinkEventClaimer,
  PostgrestFriendshipReader,
  PostgrestProfileReader,
  PostgrestRateLimiter,
  PostgrestSubscriptionStore,
} from "./supabase_adapters.ts";
import { NotifyFriendsService } from "./service.ts";

const logger = consoleLogger("notify-friends");

/** Baut alle Abhängigkeiten aus den Umgebungsvariablen (einmal pro Worker). */
async function buildDeps(): Promise<NotifyFriendsHandlerDeps> {
  const supabase = readSupabaseServerConfig(Deno.env);
  const vapid = readVapidConfig(Deno.env);
  const credentials = await importVapidCredentials(vapid.publicKey, vapid.privateKey, vapid.subject).catch(
    (error) => {
      throw error instanceof VapidKeyError ? new ConfigError(error.message) : error;
    },
  );
  const db = new PostgrestClient(supabase, fetch);
  return {
    verifier: new GoTrueTokenVerifier(supabase, fetch),
    useCase: new NotifyFriendsService({
      rateLimiter: new PostgrestRateLimiter(db),
      events: new PostgrestDrinkEventClaimer(db, systemClock),
      friendships: new PostgrestFriendshipReader(db),
      profiles: new PostgrestProfileReader(db),
      subscriptions: new PostgrestSubscriptionStore(db),
      push: new WebPushSender({ credentials, fetch, clock: systemClock }),
      clock: systemClock,
      logger,
    }),
  };
}

let deps: Promise<NotifyFriendsHandlerDeps> | undefined;

/** Lädt die Abhängigkeiten beim ersten Aufruf; schlägt das fehl, wird es beim nächsten Aufruf erneut versucht. */
function resolveDeps(): Promise<NotifyFriendsHandlerDeps> {
  deps ??= buildDeps().catch((error) => {
    deps = undefined;
    throw error;
  });
  return deps;
}

Deno.serve(createNotifyFriendsHandler(resolveDeps, logger));
