/**
 * Erzeugt kostenlos und lokal ein VAPID-Schlüsselpaar (P-256) für Web Push/UnifiedPush.
 *
 * Aufruf (keine Berechtigungen nötig, nichts verlässt den Rechner):
 *   deno run supabase/scripts/vapid-schluessel-erzeugen.ts
 *
 * Danach (siehe supabase/README.md):
 *   - VAPID_PUBLIC_KEY in android/app/supabase.properties (vapidPublicKey) und in die Web-App eintragen,
 *   - alle drei Werte als Secrets der Edge Functions setzen:
 *       supabase secrets set VAPID_PUBLIC_KEY=… VAPID_PRIVATE_KEY=… VAPID_SUBJECT=mailto:…
 *   - den privaten Schlüssel NIE einchecken oder weitergeben.
 */

import { generateVapidKeyPair } from "../functions/_shared/webpush/vapid.ts";

if (import.meta.main) {
  const { publicKey, privateKey } = await generateVapidKeyPair();
  console.log("# Neues VAPID-Schlüsselpaar (P-256, base64url)");
  console.log("# Öffentlich – kommt in die Apps und als Secret in Supabase:");
  console.log(`VAPID_PUBLIC_KEY=${publicKey}`);
  console.log("# GEHEIM – nur als Secret in Supabase setzen, nie einchecken:");
  console.log(`VAPID_PRIVATE_KEY=${privateKey}`);
  console.log("# Kontakt für die Push-Dienste (eigene Adresse eintragen):");
  console.log("VAPID_SUBJECT=mailto:deine-adresse@example.org");
}
