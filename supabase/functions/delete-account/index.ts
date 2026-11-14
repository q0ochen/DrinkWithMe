/**
 * Edge Function „delete-account“ – Einstiegspunkt und Verdrahtung (Composition Root). Logik: handler.ts.
 */

import { readSupabaseServerConfig } from "../_shared/config.ts";
import { consoleLogger } from "../_shared/logger.ts";
import { GoTrueTokenVerifier, GoTrueUserAdmin } from "../_shared/supabase_api.ts";
import { createDeleteAccountHandler, type DeleteAccountDeps } from "./handler.ts";

const logger = consoleLogger("delete-account");

function resolveDeps(): Promise<DeleteAccountDeps> {
  return Promise.resolve().then(() => {
    const supabase = readSupabaseServerConfig(Deno.env);
    return {
      verifier: new GoTrueTokenVerifier(supabase, fetch),
      admin: new GoTrueUserAdmin(supabase, fetch),
    };
  });
}

Deno.serve(createDeleteAccountHandler(resolveDeps, logger));
