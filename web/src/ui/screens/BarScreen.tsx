import { useEffect, useRef, useState } from "preact/hooks";
import type { BarItem } from "../../core/bar/barItem";
import type { DrinkEntry } from "../../core/log/drinkEntry";
import { UNDO_WINDOW_MILLIS } from "../../core/log/logUseCases";
import { formatVolume } from "../../core/sharing/sharedDrink";
import { SharingTexts } from "../../core/sharing/sharingPolicy";
import { SpeechTexts } from "../../core/speech/speechTexts";
import type { VoiceMatch } from "../../core/voice/voiceCommandMatcher";
import { RecognitionError } from "../../platform/recognition";
import { germanMessage, SocialError } from "../../social/errors";
import { Dialog } from "../components/Dialog";
import { DrinkIconImage, ErrorText, Link, Notice, PageHeader, Spinner, UiIcon } from "../components/common";
import { useAnnouncer, useBarItems, useDocumentTitle, useHistoryEntries, useNow, useRouter, useServices, useSettings } from "../context";
import { formatGrams, formatTime, pluralize } from "../format";
import { healthTag } from "./drinkTexts";

interface UndoState {
  readonly entry: DrinkEntry;
  readonly until: number;
  /** „Mit deinen Freunden geteilt.“ / „Nicht geteilt.“ (nur angemeldet, wie in der Android-App). */
  readonly hint: string | null;
}

const SharingHints = {
  SHARED: "Mit deinen Freunden geteilt.",
  PRIVATE: "Nicht geteilt.",
} as const;

/** „Bar“: großes Raster, ein Tipp = jetzt getrunken, 10 s Rückgängig, optional Ansage und Spracheingabe. */
export function BarScreen() {
  useDocumentTitle("Bar");
  const services = useServices();
  const router = useRouter();
  const settings = useSettings();
  const announcer = useAnnouncer();
  const items = useBarItems();
  const entries = useHistoryEntries();
  useNow(60_000); // „Heute“ nach Mitternacht neu berechnen
  const [undo, setUndo] = useState<UndoState | null>(null);
  const [tick, setTick] = useState(services.clock.now());
  const [consentFor, setConsentFor] = useState<DrinkEntry | null>(null);
  const [confirmUndo, setConfirmUndo] = useState<DrinkEntry | null>(null);
  const [ambiguous, setAmbiguous] = useState<[BarItem, BarItem] | null>(null);
  const [listening, setListening] = useState(false);
  const [voiceMessage, setVoiceMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [menuFor, setMenuFor] = useState<BarItem | null>(null);
  const [sharingProblem, setSharingProblem] = useState<string | null>(null);
  const undoTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Der Server nimmt keine weiteren geteilten Getränke an (Tageslimit): einmal sichtbar sagen.
  useEffect(
    () =>
      services.subscribeBackgroundErrors((caught) => {
        if (caught instanceof SocialError && caught.code === "too_many_events") setSharingProblem(germanMessage(caught));
      }),
    [services],
  );

  useEffect(() => () => {
    if (undoTimer.current) clearTimeout(undoTimer.current);
    services.recognizer?.abort();
  }, [services]);

  // Sekundenanzeige am Rückgängig-Knopf (nur sichtbar, nicht angesagt).
  useEffect(() => {
    if (!undo) return;
    setTick(services.clock.now());
    const timer = setInterval(() => setTick(services.clock.now()), 1000);
    return () => clearInterval(timer);
  }, [undo, services]);

  const say = (text: string, force = false) => {
    announcer.announce(text);
    if ((force || settings.speakConfirmations) && services.speaker.available) services.speaker.speak(text);
  };

  /** `privately`: „Privat eintragen“ – dieser Eintrag wird nicht geteilt (und es wird nicht nach Alkohol gefragt). */
  const log = async (item: BarItem, spoken = false, privately = false) => {
    setError(null);
    try {
      const askConsent = !privately && services.policy.needsAlcoholConsentQuestion(item);
      const entry = await services.logDrink.execute(item, null, privately ? false : null);
      if (undoTimer.current) clearTimeout(undoTimer.current);
      const hint = askConsent ? null : entry.shareWithFriends ? SharingHints.SHARED : services.session.current() ? SharingHints.PRIVATE : null;
      setUndo({ entry, until: services.clock.now() + UNDO_WINDOW_MILLIS, hint });
      undoTimer.current = setTimeout(() => setUndo(null), UNDO_WINDOW_MILLIS);
      say(SpeechTexts.logged(entry.name), spoken);
      if (askConsent) setConsentFor(entry);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Das Getränk konnte nicht eingetragen werden.");
    }
  };

  const undoLast = async (spoken = false) => {
    const result = await services.undo.execute();
    if (result.kind === "Undone") {
      if (undoTimer.current) clearTimeout(undoTimer.current);
      setUndo(null);
      say(SpeechTexts.undone(result.entry.name), spoken);
    } else if (result.kind === "ConfirmationRequired") {
      setConfirmUndo(result.entry);
    } else {
      say("Es gibt nichts zum Rückgängigmachen.", spoken);
    }
  };

  const answerConsent = async (allow: boolean) => {
    const entry = consentFor;
    setConsentFor(null);
    await services.settings.update({ alcoholConsent: allow ? "ALLOWED" : "DENIED" });
    if (allow && entry) {
      const stored = await services.history.entry(entry.id);
      if (stored && !stored.shareWithFriends) await services.history.save({ ...stored, shareWithFriends: true });
      setUndo((current) => (current?.entry.id === entry.id ? { ...current, hint: SharingHints.SHARED } : current));
    }
    announcer.announce(allow ? "Alkoholische Getränke werden mit Freunden geteilt." : "Alkoholische Getränke bleiben privat.");
  };

  const readAloud = async () => {
    const social = await services.social();
    if (social?.auth.currentUser()) {
      const [latest] = await social.feed.recentEvents(null, 1).catch(() => []);
      if (latest) {
        say(SpeechTexts.friendEvent(latest), true);
        return;
      }
    }
    say(SpeechTexts.today(services.statistics.today(entries ?? [])), true);
  };

  const handleVoice = async (match: VoiceMatch) => {
    setVoiceMessage(null);
    switch (match.kind) {
      case "Drink":
        await log(match.item, true);
        break;
      case "Ambiguous":
        setAmbiguous([match.first, match.second]);
        say(SpeechTexts.whichDrink(match.first.name, match.second.name), true);
        break;
      case "Command":
        if (match.cmd === "UNDO") await undoLast(true);
        else if (match.cmd === "TODAY") say(SpeechTexts.today(services.statistics.today(entries ?? [])), true);
        else await readAloud();
        break;
      case "NoMatch":
        setVoiceMessage(SpeechTexts.notUnderstood());
        say(SpeechTexts.notUnderstood(), true);
        break;
    }
  };

  const listen = async () => {
    if (!services.recognizer || !items) return;
    setListening(true);
    setVoiceMessage(null);
    try {
      const candidates = await services.recognizer.listen();
      await handleVoice(services.voice.match(candidates, items));
    } catch (caught) {
      setVoiceMessage(caught instanceof RecognitionError ? caught.message : "Die Spracherkennung hat nicht geklappt.");
    } finally {
      setListening(false);
    }
  };

  const today = services.statistics.today(entries ?? []);
  const secondsLeft = undo ? Math.max(0, Math.ceil((undo.until - tick) / 1000)) : 0;

  return (
    <div class={undo ? "screen has-snackbar" : "screen"}>
      <PageHeader title="Bar">
        <p class="lead">Antippen = jetzt getrunken.</p>
      </PageHeader>

      <section class="today-strip" aria-label="Heute">
        <p>
          <strong>Heute:</strong> {pluralize(today.drinkCount, "Getränk", "Getränke")} · {formatVolume(today.totalMilliliters)} ·{" "}
          {formatGrams(today.sugarGrams)} Zucker · {formatGrams(today.alcoholGrams)} Alkohol
        </p>
        <Link to="/verlauf" class="text-link">
          Zum Verlauf
        </Link>
      </section>

      <div class="toolbar">
        {services.recognizer ? (
          <button type="button" class="button button-secondary" onClick={() => void listen()} disabled={listening || !items?.length} aria-describedby="sprechen-hinweis">
            <UiIcon name="mic" />
            {listening ? "Ich höre zu …" : "Sprechen"}
          </button>
        ) : null}
        <Link to="/bar/bearbeiten" class="button button-secondary">
          <UiIcon name="edit" />
          Bar bearbeiten
        </Link>
      </div>
      {services.recognizer ? (
        <p id="sprechen-hinweis" class="visually-hidden">
          Sag zum Beispiel „Apfelschorle“, „rückgängig“ oder „wie viel heute“.
        </p>
      ) : null}
      {voiceMessage ? (
        <p class="notice notice-info" role="status">
          {voiceMessage}
        </p>
      ) : null}
      <ErrorText error={error} />
      {sharingProblem ? (
        <Notice tone="warning" role="status">
          <p>{sharingProblem}</p>
        </Notice>
      ) : null}

      {items === null ? (
        <Spinner label="Bar wird geladen …" />
      ) : items.length === 0 ? (
        <div class="card empty">
          <p>Deine Bar ist leer.</p>
          <Link to="/bar/bearbeiten" class="button button-primary">
            <UiIcon name="plus" />
            Getränke hinzufügen
          </Link>
        </div>
      ) : (
        <ul class="drink-grid" aria-label="Getränke in deiner Bar">
          {items.map((item) => {
            const tag = healthTag(item);
            return (
              <li key={item.id} class="drink-cell">
                <button
                  type="button"
                  class="drink-tile"
                  onClick={() => void log(item)}
                  onContextMenu={(event) => {
                    // Rechtsklick bzw. langes Drücken (wie in der Android-App): weitere Möglichkeiten.
                    event.preventDefault();
                    setMenuFor(item);
                  }}
                  aria-describedby="eintragen-hinweis"
                >
                  <DrinkIconImage icon={item.icon} size={64} />
                  <span class="drink-name">{item.name}</span>
                  <span class="drink-meta">
                    {formatVolume(item.servingMilliliters)}
                    {tag ? <span class={`tag tag-${tag.startsWith("alkohol") ? "alcohol" : "sugar"}`}>{tag}</span> : null}
                  </span>
                </button>
                <button type="button" class="icon-button tile-more" aria-haspopup="dialog" aria-label={`Mehr zu ${item.name}`} onClick={() => setMenuFor(item)}>
                  <UiIcon name="more" />
                </button>
              </li>
            );
          })}
        </ul>
      )}
      <p id="eintragen-hinweis" class="visually-hidden">
        Trägt das Getränk mit der aktuellen Uhrzeit ein.
      </p>

      {undo ? (
        <div class="snackbar" role="region" aria-label="Zuletzt eingetragen">
          <p class="snackbar-text">
            <strong>{SpeechTexts.logged(undo.entry.name)}</strong>
            <span class="muted"> {formatTime(undo.entry.consumedAt)} Uhr</span>
            {undo.hint ? <span class="muted"> · {undo.hint}</span> : null}
          </p>
          <button type="button" class="button button-undo" onClick={() => void undoLast()}>
            <UiIcon name="undo" />
            Rückgängig
            <span class="countdown" aria-hidden="true">
              {secondsLeft > 0 ? ` (${secondsLeft} s)` : ""}
            </span>
          </button>
          <span class="snackbar-progress" aria-hidden="true" style={{ animationDuration: `${UNDO_WINDOW_MILLIS}ms` }} key={undo.entry.id} />
        </div>
      ) : null}

      <Dialog
        open={menuFor !== null}
        title={menuFor?.name ?? ""}
        onClose={() => setMenuFor(null)}
        actions={
          <>
            <button
              type="button"
              class="button button-primary"
              onClick={() => {
                const item = menuFor;
                setMenuFor(null);
                if (item) void log(item, false, true);
              }}
            >
              Privat eintragen (nicht teilen)
            </button>
            <button
              type="button"
              class="button button-secondary"
              onClick={() => {
                setMenuFor(null);
                router.navigate("/bar/bearbeiten");
              }}
            >
              Bar bearbeiten
            </button>
            <button type="button" class="button button-secondary" onClick={() => setMenuFor(null)}>
              Abbrechen
            </button>
          </>
        }
      >
        <p>„Privat eintragen“ speichert das Getränk nur auf diesem Gerät – deine Freunde sehen es nicht.</p>
      </Dialog>

      <Dialog
        open={consentFor !== null}
        title={SharingTexts.ALCOHOL_CONSENT_QUESTION}
        onClose={() => void answerConsent(false)}
        actions={
          <>
            <button type="button" class="button button-primary" onClick={() => void answerConsent(true)}>
              {SharingTexts.ALCOHOL_CONSENT_YES}
            </button>
            <button type="button" class="button button-secondary" onClick={() => void answerConsent(false)}>
              {SharingTexts.ALCOHOL_CONSENT_NO}
            </button>
          </>
        }
      >
        <p>
          Deine Freunde sehen dann zum Beispiel „{consentFor?.name} ({consentFor?.volumeMilliliters} ml)“. Gramm, Zucker und deine Statistik
          werden nie geteilt.
        </p>
        <p class="muted">{SharingTexts.ALCOHOL_CONSENT_HINT}</p>
      </Dialog>

      <Dialog
        open={confirmUndo !== null}
        title="Eintrag entfernen?"
        onClose={() => setConfirmUndo(null)}
        actions={
          <>
            <button
              type="button"
              class="button button-danger"
              onClick={async () => {
                const entry = confirmUndo;
                setConfirmUndo(null);
                if (entry) {
                  await services.undo.confirm(entry);
                  setUndo(null);
                  say(SpeechTexts.undone(entry.name));
                }
              }}
            >
              Entfernen
            </button>
            <button type="button" class="button button-secondary" onClick={() => setConfirmUndo(null)}>
              Behalten
            </button>
          </>
        }
      >
        <p>
          Der letzte Eintrag ist älter als 10 Sekunden: {confirmUndo?.name} um {confirmUndo ? formatTime(confirmUndo.consumedAt) : ""} Uhr.
        </p>
      </Dialog>

      <Dialog
        open={ambiguous !== null}
        title="Welches Getränk meinst du?"
        onClose={() => setAmbiguous(null)}
        actions={
          <>
            {ambiguous?.map((item) => (
              <button
                key={item.id}
                type="button"
                class="button button-primary"
                onClick={() => {
                  setAmbiguous(null);
                  void log(item, true);
                }}
              >
                {item.name}
              </button>
            ))}
            <button type="button" class="button button-secondary" onClick={() => setAmbiguous(null)}>
              Keins davon
            </button>
          </>
        }
      >
        <p>{ambiguous ? SpeechTexts.whichDrink(ambiguous[0].name, ambiguous[1].name) : ""}</p>
      </Dialog>
    </div>
  );
}
