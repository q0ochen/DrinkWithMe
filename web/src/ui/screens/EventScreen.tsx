import { useEffect, useState } from "preact/hooks";
import type { FriendDrinkEvent } from "../../core/social/ports";
import { EventTexts } from "../../core/speech/eventTexts";
import { SpeechTexts } from "../../core/speech/speechTexts";
import { germanMessage } from "../../social/errors";
import { SignInPanel } from "../components/SignInPanel";
import { DrinkIconImage, ErrorText, Link, Notice, PageHeader, ServerNotConfigured, Spinner, UiIcon } from "../components/common";
import { useAnnouncer, useDocumentTitle, useNow, useServices, useSessionUser, useSocial } from "../context";
import { formatRelative, formatTime } from "../format";

/**
 * Ereignis-Bildschirm (Tipp auf die Benachrichtigung): großer Satz und bildschirmbreiter Knopf „Vorlesen“.
 * Vorgelesen wird erst nach einem Tipp (Browser erlauben Sprachausgabe nur nach einer Nutzeraktion).
 */
export function EventScreen({ eventId }: { eventId: string }) {
  useDocumentTitle("Ereignis");
  const services = useServices();
  const social = useSocial();
  const user = useSessionUser();
  const announcer = useAnnouncer();
  const now = useNow();
  const [event, setEvent] = useState<FriendDrinkEvent | null | undefined>(undefined);
  const [error, setError] = useState<string | null>(null);
  const [germanVoice, setGermanVoice] = useState(true);

  useEffect(() => {
    void services.speaker.hasGermanVoice().then(setGermanVoice);
  }, [services]);

  useEffect(() => {
    if (!social || !user) return;
    let active = true;
    setEvent(undefined);
    social.feed
      .event(eventId)
      .then((loaded) => {
        if (active) setEvent(loaded);
      })
      .catch((caught: unknown) => {
        if (active) {
          setError(germanMessage(caught));
          setEvent(null);
        }
      });
    return () => {
      active = false;
    };
  }, [social, user, eventId]);

  if (!services.config.supabase) {
    return (
      <div class="screen">
        <PageHeader title="Ereignis" />
        <ServerNotConfigured />
      </div>
    );
  }
  if (social === undefined) return <Spinner label="Ereignis wird geladen …" />;
  if (!user) {
    return (
      <div class="screen">
        <PageHeader title="Ereignis" />
        <SignInPanel returnPath={`/ereignis/${eventId}`} intro="Melde dich an, um zu sehen, was deine Freunde trinken." />
      </div>
    );
  }
  if (event === undefined) return <Spinner label="Ereignis wird geladen …" />;

  return (
    <div class="screen event-screen">
      <PageHeader title="Ereignis" />
      {event === null ? (
        <>
          <Notice>
            <p>Dieses Ereignis ist nicht mehr verfügbar. Geteilte Getränke werden nach 30 Tagen gelöscht.</p>
          </Notice>
          <ErrorText error={error} />
        </>
      ) : (
        <article class="event-card" aria-labelledby="ereignis-satz">
          <DrinkIconImage icon={event.icon} size={112} />
          <p id="ereignis-satz" class="event-sentence">
            {EventTexts.headline(event)}
          </p>
          <p class="event-meta">
            {EventTexts.eventBody(event)} · {formatTime(event.consumedAt)} Uhr ({formatRelative(event.consumedAt, now, services.zone)})
          </p>
          {services.speaker.available ? (
            <button
              type="button"
              class="button button-primary button-read-aloud"
              onClick={() => {
                const sentence = SpeechTexts.friendEvent(event);
                services.speaker.speak(sentence);
                announcer.announce(sentence);
              }}
            >
              <UiIcon name="speaker" />
              Vorlesen
            </button>
          ) : (
            <Notice>
              <p>Dieser Browser kann nicht vorlesen.</p>
            </Notice>
          )}
          {services.speaker.available && !germanVoice ? (
            <Notice tone="warning">
              <p>
                Auf diesem Gerät ist keine deutsche Stimme eingerichtet. Du kannst eine in den Bedienungshilfen bzw. Spracheinstellungen deines
                Geräts installieren.
              </p>
            </Notice>
          ) : null}
        </article>
      )}
      <Link to="/freunde" class="button button-secondary">
        <UiIcon name="back" />
        Zu den Freunden
      </Link>
    </div>
  );
}
