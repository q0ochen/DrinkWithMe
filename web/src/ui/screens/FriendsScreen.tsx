import { useCallback, useEffect, useState } from "preact/hooks";
import type { Friend, FriendDrinkEvent, FriendRequest } from "../../core/social/ports";
import { EventTexts } from "../../core/speech/eventTexts";
import { SpeechTexts } from "../../core/speech/speechTexts";
import { needsHomeScreenForPush } from "../../platform/platform";
import { copyText, shareLink } from "../../platform/share";
import { germanMessage } from "../../social/errors";
import type { SocialServices } from "../../social/types";
import { Dialog } from "../components/Dialog";
import { SignInPanel } from "../components/SignInPanel";
import { Avatar, DrinkIconImage, ErrorText, IosHomeScreenHint, Link, Notice, PageHeader, Section, ServerNotConfigured, Spinner, UiIcon } from "../components/common";
import { useAction, useAnnouncer, useDocumentTitle, useNow, useServices, useSessionUser, useSocial } from "../context";
import { formatRelative } from "../format";

/** „Freunde“: Feed, Freundesliste, Anfragen, Einladungs-Link und „Per E-Mail hinzufügen“. */
export function FriendsScreen() {
  useDocumentTitle("Freunde");
  const services = useServices();
  const social = useSocial();
  const user = useSessionUser();

  return (
    <div class="screen">
      <PageHeader title="Freunde">
        <p class="lead">Sieh, was deine Freunde gerade trinken – und lass dir Ereignisse vorlesen.</p>
      </PageHeader>
      {!services.config.supabase ? (
        <ServerNotConfigured />
      ) : social === undefined ? (
        <Spinner label="Freunde werden geladen …" />
      ) : social === null ? (
        <Notice tone="error" role="alert">
          <p>Die Verbindung zum Server konnte nicht aufgebaut werden. Bitte lade die Seite später neu.</p>
        </Notice>
      ) : !user ? (
        <Section title="Anmelden" id="anmelden">
          <SignInPanel returnPath="/freunde" />
        </Section>
      ) : (
        <SignedInFriends social={social} />
      )}
      {services.config.supabase && needsHomeScreenForPush(services.platform) ? <IosHomeScreenHint /> : null}
    </div>
  );
}

interface FriendsData {
  feed: FriendDrinkEvent[];
  friends: Friend[];
  requests: FriendRequest[];
}

function SignedInFriends({ social }: { social: SocialServices }) {
  const services = useServices();
  const announcer = useAnnouncer();
  const now = useNow();
  const [data, setData] = useState<FriendsData | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [removing, setRemoving] = useState<Friend | null>(null);

  const load = useCallback(async () => {
    setLoadError(null);
    try {
      const [feed, friends, requests] = await Promise.all([social.feed.recentEvents(null), social.friends.friends(), social.friends.requests()]);
      setData({ feed, friends, requests });
      const newest = feed[0]?.consumedAt;
      if (newest && newest > (services.settings.current().feedLastSeenAt ?? 0)) await services.settings.update({ feedLastSeenAt: newest });
    } catch (error) {
      setLoadError(germanMessage(error));
    }
  }, [social, services]);

  useEffect(() => {
    void load();
  }, [load]);

  const respond = useAction(async (request: FriendRequest, accept: boolean) => {
    await social.friends.respond(request.id, accept);
    announcer.announce(accept ? `Du bist jetzt mit ${request.other.displayName} befreundet.` : "Anfrage abgelehnt.");
    await load();
  });
  const cancel = useAction(async (request: FriendRequest) => {
    await social.friends.cancel(request.id);
    announcer.announce("Anfrage zurückgezogen.");
    await load();
  });
  const remove = useAction(async (friend: Friend) => {
    await social.friends.removeFriend(friend.profile.id);
    announcer.announce(`${friend.profile.displayName} ist nicht mehr in deiner Freundesliste.`);
    await load();
  });

  const incoming = data?.requests.filter((request) => request.direction === "INCOMING") ?? [];
  const outgoing = data?.requests.filter((request) => request.direction === "OUTGOING") ?? [];
  const actionError = respond.error ?? cancel.error ?? remove.error;

  return (
    <>
      <div class="toolbar">
        <button type="button" class="button button-secondary" onClick={() => void load()}>
          Aktualisieren
        </button>
      </div>
      <ErrorText error={loadError ?? (actionError ? germanMessage(actionError) : null)} />

      {incoming.length > 0 ? (
        <Section title="Anfragen an dich" id="anfragen">
          <ul class="people-list">
            {incoming.map((request) => (
              <li key={request.id} class="person-row">
                <Avatar name={request.other.displayName} />
                <span class="person-name">{request.other.displayName}</span>
                <span class="person-actions">
                  <button type="button" class="button button-primary" onClick={() => void respond.run(request, true)} disabled={respond.busy}>
                    Annehmen
                  </button>
                  <button type="button" class="button button-secondary" onClick={() => void respond.run(request, false)} disabled={respond.busy}>
                    Ablehnen
                  </button>
                </span>
              </li>
            ))}
          </ul>
        </Section>
      ) : null}

      <Section title="Neues von Freunden" id="feed">
        {data === null ? (
          loadError ? null : <Spinner label="Neuigkeiten werden geladen …" />
        ) : data.feed.length === 0 ? (
          <p>Noch nichts Neues. Sobald deine Freunde etwas trinken und teilen, erscheint es hier (bis zu 30 Tage).</p>
        ) : (
          <ul class="feed-list">
            {data.feed.map((event) => (
              <li key={event.id} class="feed-row">
                <DrinkIconImage icon={event.icon} size={48} />
                <Link to={`/ereignis/${event.id}`} class="feed-text">
                  <span class="feed-title">{EventTexts.eventTitle(event)}</span>
                  <span>{EventTexts.eventBody(event)}</span>
                  <span class="muted">{formatRelative(event.consumedAt, now, services.zone)}</span>
                </Link>
                {services.speaker.available ? (
                  <button
                    type="button"
                    class="icon-button"
                    aria-label={`Vorlesen: ${EventTexts.headline(event)}`}
                    onClick={() => {
                      services.speaker.speak(SpeechTexts.friendEvent(event));
                    }}
                  >
                    <UiIcon name="speaker" />
                  </button>
                ) : null}
              </li>
            ))}
          </ul>
        )}
      </Section>

      <InviteSection social={social} />
      <EmailSection social={social} />

      <Section title="Deine Freunde" id="freundesliste">
        {data === null ? null : data.friends.length === 0 ? (
          <p>Noch keine Freunde. Lade jemanden mit einem Link ein oder füge eine Google-Adresse hinzu.</p>
        ) : (
          <ul class="people-list">
            {data.friends.map((friend) => (
              <li key={friend.profile.id} class="person-row">
                <Avatar name={friend.profile.displayName} />
                <span class="person-name">{friend.profile.displayName}</span>
                <span class="person-actions">
                  <button type="button" class="button button-secondary" onClick={() => setRemoving(friend)} aria-label={`${friend.profile.displayName} entfernen`}>
                    Entfernen
                  </button>
                </span>
              </li>
            ))}
          </ul>
        )}
        {outgoing.length > 0 ? (
          <>
            <h3>Gesendete Anfragen</h3>
            <ul class="people-list">
              {outgoing.map((request) => (
                <li key={request.id} class="person-row">
                  <Avatar name={request.other.displayName} />
                  <span class="person-name">
                    {request.other.displayName} <span class="muted">(wartet)</span>
                  </span>
                  <span class="person-actions">
                    <button type="button" class="button button-secondary" onClick={() => void cancel.run(request)} disabled={cancel.busy}>
                      Zurückziehen
                    </button>
                  </span>
                </li>
              ))}
            </ul>
          </>
        ) : null}
      </Section>

      <Dialog
        open={removing !== null}
        title={`${removing?.profile.displayName ?? ""} entfernen?`}
        onClose={() => setRemoving(null)}
        actions={
          <>
            <button
              type="button"
              class="button button-danger"
              onClick={() => {
                const friend = removing;
                setRemoving(null);
                if (friend) void remove.run(friend);
              }}
            >
              Entfernen
            </button>
            <button type="button" class="button button-secondary" onClick={() => setRemoving(null)}>
              Abbrechen
            </button>
          </>
        }
      >
        <p>Ihr seht dann gegenseitig keine Getränke mehr. Ihr könnt euch jederzeit neu einladen.</p>
      </Dialog>
    </>
  );
}

function InviteSection({ social }: { social: SocialServices }) {
  const [link, setLink] = useState<string | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const create = useAction(async () => {
    setStatus(null);
    const url = await social.friends.createInviteLink();
    setLink(url);
    const shared = await shareLink("Drink With Me", "Trink mit mir! Mit diesem Link sind wir in Drink With Me befreundet:", url);
    if (shared === true) setStatus("Link geteilt.");
    else if (shared === null) setStatus((await copyText(url)) ? "Link in die Zwischenablage kopiert." : "Kopiere den Link unten und schicke ihn weiter.");
  });

  return (
    <Section title="Freund einladen" id="einladen">
      <p>Erstelle einen Link und schicke ihn per Messenger oder E-Mail. Wer ihn öffnet und annimmt, ist sofort mit dir befreundet.</p>
      <button type="button" class="button button-primary" onClick={() => void create.run()} disabled={create.busy}>
        <UiIcon name="share" />
        {link ? "Neuen Link erstellen und teilen" : "Einladungs-Link erstellen"}
      </button>
      {link ? (
        <div class="field">
          <label for="einladungs-link">Dein Einladungs-Link (14 Tage gültig, ein neuer Link ersetzt den alten)</label>
          <input id="einladungs-link" type="text" readOnly value={link} onFocus={(event) => event.currentTarget.select()} />
        </div>
      ) : null}
      {status ? (
        <p class="success-text" role="status">
          {status}
        </p>
      ) : null}
      <ErrorText error={create.error ? germanMessage(create.error) : null} />
    </Section>
  );
}

function EmailSection({ social }: { social: SocialServices }) {
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState(false);
  const [invalid, setInvalid] = useState(false);
  const send = useAction(async () => {
    setSent(false);
    const value = email.trim();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
      setInvalid(true);
      return;
    }
    setInvalid(false);
    await social.friends.sendRequestByEmail(value);
    setEmail("");
    setSent(true);
  });

  return (
    <Section title="Per E-Mail hinzufügen" id="per-email">
      <form
        class="form"
        noValidate
        onSubmit={(event) => {
          event.preventDefault();
          void send.run();
        }}
      >
        <div class="field">
          <label for="freund-email">Google-E-Mail-Adresse</label>
          <input
            id="freund-email"
            type="email"
            autoComplete="off"
            inputMode="email"
            value={email}
            aria-invalid={invalid ? "true" : undefined}
            aria-describedby={invalid ? "freund-email-fehler" : "freund-email-hinweis"}
            onInput={(event) => setEmail(event.currentTarget.value)}
          />
          {invalid ? (
            <p id="freund-email-fehler" class="field-error">
              Bitte gib eine vollständige E-Mail-Adresse ein.
            </p>
          ) : (
            <p id="freund-email-hinweis" class="muted">
              Die Person muss Drink With Me mit diesem Google-Konto nutzen und deine Anfrage bestätigen.
            </p>
          )}
        </div>
        <button type="submit" class="button button-secondary" disabled={send.busy}>
          Anfrage senden
        </button>
      </form>
      {sent ? (
        <p class="success-text" role="status">
          Danke! Wenn es zu dieser Adresse ein Konto gibt, bekommt die Person deine Anfrage.
        </p>
      ) : null}
      <ErrorText error={send.error ? germanMessage(send.error) : null} />
    </Section>
  );
}
