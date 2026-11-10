import { useEffect, useState } from "preact/hooks";
import { THIRD_PARTY_LICENSES } from "../../app/licenses";
import { HealthReferences } from "../../core/health/healthReferences";
import { SharingTexts } from "../../core/sharing/sharingPolicy";
import { needsHomeScreenForPush } from "../../platform/platform";
import { germanMessage } from "../../social/errors";
import type { PushStatus, SocialServices } from "../../social/types";
import { Dialog } from "../components/Dialog";
import { SignInPanel } from "../components/SignInPanel";
import { ErrorText, IosHomeScreenHint, Link, Notice, PageHeader, Section, ServerNotConfigured, Spinner, UiIcon } from "../components/common";
import { useAction, useAnnouncer, useDocumentTitle, useRouter, useServices, useSessionUser, useSettings, useSocial } from "../context";

export function SettingsScreen() {
  useDocumentTitle("Einstellungen");
  const services = useServices();
  const settings = useSettings();
  const social = useSocial();
  const user = useSessionUser();
  const announcer = useAnnouncer();
  const [germanVoice, setGermanVoice] = useState<boolean | null>(null);

  useEffect(() => {
    void services.speaker.hasGermanVoice().then(setGermanVoice);
  }, [services]);

  const consentText =
    settings.alcoholConsent === "ALLOWED"
      ? "Ja – alkoholische Getränke werden wie alle anderen geteilt."
      : settings.alcoholConsent === "DENIED"
        ? "Nein – alkoholische Getränke bleiben privat."
        : "Noch nicht entschieden – du wirst beim ersten alkoholischen Getränk gefragt. Bis dahin bleiben sie privat.";

  return (
    <div class="screen">
      <PageHeader title="Einstellungen" />

      <Section title="Konto" id="konto">
        {!services.config.supabase ? (
          <ServerNotConfigured />
        ) : social === undefined ? (
          <Spinner label="Konto wird geladen …" />
        ) : !social ? (
          <p class="error-text">Der Server ist gerade nicht erreichbar.</p>
        ) : user ? (
          <AccountActions social={social} name={user.displayName} />
        ) : (
          <SignInPanel returnPath="/einstellungen" />
        )}
      </Section>

      <Section title="Teilen mit Freunden" id="teilen">
        <label class="switch">
          <input
            type="checkbox"
            checked={settings.alcoholConsent === "ALLOWED"}
            onChange={async (event) => {
              const allowed = event.currentTarget.checked;
              await services.settings.update({ alcoholConsent: allowed ? "ALLOWED" : "DENIED" });
              announcer.announce(allowed ? "Alkoholische Getränke werden geteilt." : "Alkoholische Getränke bleiben privat.");
            }}
          />
          <span>{SharingTexts.ALCOHOL_CONSENT_QUESTION}</span>
        </label>
        <p class="muted">{consentText}</p>
        <p class="muted">
          Geteilt werden nur Name, Bild, Art, Menge in ml und Zeitpunkt eines Getränks – und nur, wenn du angemeldet bist. Gramm, Zucker und
          Statistiken werden nie geteilt. Geteilte Ereignisse löscht der Server nach 30 Tagen.
        </p>
      </Section>

      <Section title="Benachrichtigungen" id="benachrichtigungen">
        {!services.config.supabase ? <ServerNotConfigured /> : <PushSettings social={social} signedIn={user !== null} />}
      </Section>

      <Section title="Ansagen und Vorlesen" id="vorlesen">
        <label class="switch">
          <input
            type="checkbox"
            checked={settings.speakConfirmations}
            disabled={!services.speaker.available}
            onChange={(event) => void services.settings.update({ speakConfirmations: event.currentTarget.checked })}
          />
          <span>Nach dem Eintragen laut bestätigen („Apfelschorle eingetragen.“)</span>
        </label>
        <p class="muted">Aus, wenn du einen Screenreader nutzt – er sagt die Bestätigung ohnehin an.</p>
        {services.speaker.available ? (
          <button type="button" class="button button-secondary" onClick={() => services.speaker.speak("Anna trinkt eine Apfelschorle.")}>
            <UiIcon name="speaker" />
            Vorlesen testen
          </button>
        ) : (
          <Notice>
            <p>Dieser Browser kann nicht vorlesen.</p>
          </Notice>
        )}
        {services.speaker.available && germanVoice === false ? (
          <Notice tone="warning">
            <p>Keine deutsche Stimme gefunden. Installiere eine in den Sprach- bzw. Bedienungshilfe-Einstellungen deines Geräts.</p>
          </Notice>
        ) : null}
        <p class="muted">
          {services.recognizer
            ? "Spracheingabe: In der Bar auf „Sprechen“ tippen und z. B. „Apfelschorle“, „rückgängig“ oder „wie viel heute“ sagen. Die Erkennung übernimmt dein Browser."
            : "Spracheingabe bietet dieser Browser nicht an – alles geht auch per Tippen."}
        </p>
      </Section>

      <Section title="Bar und Verlauf" id="bar">
        <p>Bar und vollständiger Verlauf liegen nur auf diesem Gerät (im Browser-Speicher). Wer die Websitedaten löscht, löscht auch sie.</p>
        <Link to="/bar/bearbeiten" class="button button-secondary">
          <UiIcon name="edit" />
          Bar bearbeiten
        </Link>
      </Section>

      <Section title="Info" id="info">
        <p>{HealthReferences.INFO_TEXT}</p>
        <p>{HealthReferences.ADULTS_ONLY_NOTE}</p>
        <p class="muted">
          Bezugswerte: Alkohol nach {HealthReferences.ALCOHOL_SOURCE} (pro Woche: unter 27 g risikoarm, 27 bis 81 g moderates Risiko, über 81
          g riskant). {HealthReferences.ALCOHOL_MANDATORY_NOTE} Zucker: 50 g pro Tag {HealthReferences.DAILY_SUGAR_LIMIT_LABEL}; 25 g:{" "}
          {HealthReferences.DAILY_SUGAR_WHO_LABEL.replace("WHO: ", "laut WHO ")}.
        </p>
        <details>
          <summary>Lizenzen</summary>
          <p>Code und alle Grafiken von Drink With Me: CC0 1.0 (gemeinfrei). Verwendete freie Bibliotheken:</p>
          <ul class="license-list">
            {THIRD_PARTY_LICENSES.map((entry) => (
              <li key={entry.name}>
                <a href={entry.url} rel="noopener noreferrer" target="_blank">
                  {entry.name}
                </a>{" "}
                – {entry.license}
              </li>
            ))}
          </ul>
        </details>
        <p class="muted">Version {services.config.version}</p>
      </Section>
    </div>
  );
}

function AccountActions({ social, name }: { social: SocialServices; name: string }) {
  const services = useServices();
  const router = useRouter();
  const announcer = useAnnouncer();
  const [confirmDelete, setConfirmDelete] = useState(false);
  const signOut = useAction(async () => {
    await social.push.disable().catch(() => {});
    await social.auth.signOut();
    announcer.announce("Du bist abgemeldet.");
  });
  const deleteAccount = useAction(async () => {
    setConfirmDelete(false);
    await social.push.disable().catch(() => {});
    await social.account.deleteAccount();
    await services.settings.update({ feedLastSeenAt: null });
    announcer.announce("Dein Konto wurde gelöscht. Bar und Verlauf auf diesem Gerät bleiben erhalten.");
    router.navigate("/einstellungen", { replace: true });
  });
  const error = signOut.error ?? deleteAccount.error;
  return (
    <>
      <p>
        Angemeldet als <strong>{name}</strong> (Google).
      </p>
      <div class="toolbar">
        <button type="button" class="button button-secondary" onClick={() => void signOut.run()} disabled={signOut.busy}>
          Abmelden
        </button>
        <button type="button" class="button button-danger-outline" onClick={() => setConfirmDelete(true)} disabled={deleteAccount.busy}>
          Konto löschen
        </button>
      </div>
      <ErrorText error={error ? germanMessage(error) : null} />
      <Dialog
        open={confirmDelete}
        title="Konto wirklich löschen?"
        onClose={() => setConfirmDelete(false)}
        actions={
          <>
            <button type="button" class="button button-danger" onClick={() => void deleteAccount.run()}>
              Konto endgültig löschen
            </button>
            <button type="button" class="button button-secondary" onClick={() => setConfirmDelete(false)}>
              Abbrechen
            </button>
          </>
        }
      >
        <p>
          Gelöscht werden dein Profil, deine Freundschaften und Anfragen, deine geteilten Ereignisse und deine Benachrichtigungs-Abos auf dem
          Server. Bar und Verlauf auf diesem Gerät bleiben erhalten.
        </p>
      </Dialog>
    </>
  );
}

function PushSettings({ social, signedIn }: { social: SocialServices | null | undefined; signedIn: boolean }) {
  const services = useServices();
  const [status, setStatus] = useState<PushStatus | null>(null);
  const refresh = async () => {
    if (!social) return;
    setStatus(
      await social.push.status().catch(() => ({ supported: false, needsHomeScreen: false, permission: "unsupported" as const, subscribed: false, configured: true })),
    );
  };
  useEffect(() => {
    void refresh();
  }, [social, signedIn]);
  const enable = useAction(async () => {
    if (!social) return;
    await social.push.enable();
    await services.settings.update({ pushWanted: true });
    await refresh();
  });
  const disable = useAction(async () => {
    if (!social) return;
    await social.push.disable();
    await services.settings.update({ pushWanted: false });
    await refresh();
  });

  if (needsHomeScreenForPush(services.platform)) return <IosHomeScreenHint />;
  if (social === undefined || (social && !status)) return <Spinner label="Wird geprüft …" />;
  if (!social) return <p class="error-text">Der Server ist gerade nicht erreichbar.</p>;
  if (!signedIn) return <p>Melde dich an, um Benachrichtigungen zu bekommen, wenn deine Freunde etwas trinken.</p>;
  if (!status?.configured) return <p>Benachrichtigungen sind auf dem Server noch nicht eingerichtet.</p>;
  if (!status.supported) return <p>Dieser Browser unterstützt keine Benachrichtigungen. Neue Ereignisse siehst du unter „Freunde“.</p>;
  const error = enable.error ?? disable.error;
  return (
    <>
      <p>
        {status.subscribed
          ? "Eingeschaltet: Du bekommst eine Nachricht, wenn deine Freunde etwas trinken."
          : status.permission === "denied"
            ? "Benachrichtigungen sind für diese Seite blockiert. Du kannst sie in den Einstellungen des Browsers erlauben."
            : "Aus: Neue Ereignisse siehst du unter „Freunde“."}
      </p>
      {status.subscribed ? (
        <button type="button" class="button button-secondary" onClick={() => void disable.run()} disabled={disable.busy}>
          Benachrichtigungen ausschalten
        </button>
      ) : status.permission !== "denied" ? (
        <button type="button" class="button button-primary" onClick={() => void enable.run()} disabled={enable.busy}>
          Benachrichtigungen einschalten
        </button>
      ) : null}
      <ErrorText error={error ? germanMessage(error) : null} />
    </>
  );
}
