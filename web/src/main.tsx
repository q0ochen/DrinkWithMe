import { render } from "preact";
import { readConfig } from "./app/config";
import { createAppServices } from "./app/container";
import { Router } from "./app/router";
import { openDatabase } from "./data/database";
import { IndexedDbAuthStorage, IndexedDbBarRepository, IndexedDbHistoryRepository } from "./data/repositories";
import { IndexedDbSettingsStore } from "./data/settingsStore";
import { IndexedDbStoredValue } from "./data/storedValue";
import { currentPlatform } from "./platform/platform";
import { createSpeechRecognizer } from "./platform/recognition";
import { createImageSharer } from "./platform/share";
import { createBrowserSpeaker } from "./platform/speech";
import { createPersistentStorage } from "./platform/storage";
import { writeRecipientId } from "./sw/recipientStore";
import { App } from "./ui/App";
import "./ui/styles.css";

/** Kompositionswurzel: hier – und nur hier – werden die konkreten Implementierungen zusammengesteckt. */
async function start(): Promise<void> {
  const root = document.getElementById("app");
  if (!root) return;
  const config = readConfig(import.meta.env);
  const platform = currentPlatform();
  const db = await openDatabase();
  const settings = await IndexedDbSettingsStore.load(db);
  const router = new Router(config.basePath);
  const authStorage = new IndexedDbAuthStorage(db);

  const services = createAppServices({
    config,
    platform,
    bar: new IndexedDbBarRepository(db),
    history: new IndexedDbHistoryRepository(db),
    settings,
    speaker: createBrowserSpeaker(),
    recognizer: createSpeechRecognizer(),
    imageSharer: createImageSharer(),
    loadSocial: async () => {
      const [{ createSocialServices }, { browserPushEnvironment }] = await Promise.all([import("./social/index"), import("./social/webPush")]);
      if (!config.supabase) throw new Error("Server nicht eingerichtet");
      return createSocialServices({
        supabase: config.supabase,
        webAppUrl: config.webAppUrl,
        vapidPublicKey: config.vapidPublicKey,
        storage: authStorage,
        auth: {
          redirectTo: `${config.webAppUrl}/auth/callback`,
          rememberReturnPath: async (path) => {
            await settings.update({ returnPath: path });
          },
          openUrl: (url) => window.location.assign(url),
        },
        pushEnvironment: browserPushEnvironment(platform),
      });
    },
    // Der Service Worker zeigt Push-Nachrichten nur für diese Nutzer-ID an; `null` = ausdrücklich abgemeldet
    // (nur dann bestellt er ein Abo ab).
    onSignedInUserChanged: (user) => writeRecipientId(user?.id ?? null).catch(() => {}),
    pendingShareStore: new IndexedDbStoredValue(db, "pendingShares"),
    persistentStorage: createPersistentStorage(),
    onBackgroundError: (error) => {
      console.warn("Drink With Me: Hintergrundaufgabe fehlgeschlagen", error);
    },
  });

  render(<App services={services} router={router} />, root);

  // Vernetzung im Hintergrund laden, wenn sie gebraucht wird: angemeldet (gespeicherter Nutzer **oder** gespeicherte
  // Sitzung – sonst würden neue Getränke nicht geteilt) oder auf einer Freunde-Seite.
  const route = router.current().name;
  if (config.supabase) {
    const signedIn = settings.current().signedInUser !== null || (await authStorage.hasSession().catch(() => false));
    if (signedIn || route === "friends" || route === "invite" || route === "event" || route === "settings") void services.social();
  }

  // Ohne Verbindung vorgemerktes Teilen/Zurückziehen nachholen, sobald es wieder geht (Start: siehe container.ts).
  const retry = () => void services.retryPendingShares();
  window.addEventListener("online", retry);
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") retry();
  });

  // Bar und Verlauf liegen nur hier: einmalig um dauerhaften Speicher bitten (auch für ältere Installationen).
  if (settings.current().onboardingDone) void services.requestPersistentStorage();

  if (import.meta.env.PROD && "serviceWorker" in navigator) {
    navigator.serviceWorker.register(`${config.basePath}sw.js`, { scope: config.basePath }).catch((error: unknown) => {
      console.warn("Drink With Me: Service Worker nicht registriert", error);
    });
  }
}

start().catch((error: unknown) => {
  console.error(error);
  const root = document.getElementById("app");
  if (root) {
    root.innerHTML =
      '<main class="content"><h1>Drink With Me</h1><p>Die App konnte nicht starten. Bitte erlaube den Website-Speicher (z. B. nicht im privaten Modus) und lade die Seite neu.</p></main>';
  }
});
