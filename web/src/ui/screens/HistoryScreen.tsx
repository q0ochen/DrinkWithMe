import { useMemo, useState } from "preact/hooks";
import { entryAlcoholGrams, entrySugarGrams, isAlcoholic } from "../../core/health/healthMath";
import { HealthReferences } from "../../core/health/healthReferences";
import type { DrinkEntry } from "../../core/log/drinkEntry";
import { formatVolume } from "../../core/sharing/sharedDrink";
import { SpeechTexts } from "../../core/speech/speechTexts";
import { alcoholChartModel, sugarChartModel } from "../charts/chartModel";
import { ChartView } from "../charts/ChartView";
import { renderShareImage } from "../charts/shareImage";
import { Dialog } from "../components/Dialog";
import { DrinkIconImage, ErrorText, Link, Notice, PageHeader, Section, Spinner, UiIcon } from "../components/common";
import { useAnnouncer, useDocumentTitle, useHistoryEntries, useNow, useServices, useSettings } from "../context";
import type { StatisticsCalculator } from "../../core/stats/statisticsCalculator";
import type { LocalDate } from "../../core/time/localDate";
import { dayAndMonth, dayHeading, formatGrams, formatPercentChange, formatTime, pluralize } from "../format";

const DAYS_PER_PAGE = 14;

/** „Verlauf“: Statistik (Diagramme, Kennzahlen) und Historie – mindestens gleichwertig zum sozialen Teil. */
export function HistoryScreen() {
  useDocumentTitle("Verlauf");
  const services = useServices();
  const settings = useSettings();
  const announcer = useAnnouncer();
  const entries = useHistoryEntries();
  const now = useNow();
  const [daysShown, setDaysShown] = useState(DAYS_PER_PAGE);
  const [deleting, setDeleting] = useState<DrinkEntry | null>(null);
  const [preview, setPreview] = useState<{ blob: Blob; url: string; file: File } | null>(null);
  const [shareError, setShareError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);

  const stats = services.statistics;
  const computed = useMemo(() => {
    if (!entries) return null;
    const today = stats.currentDate();
    return {
      today,
      summary: stats.today(entries),
      daily: stats.dailySugar(entries),
      weekly: stats.weeklyAlcohol(entries),
      comparison: stats.monthComparison(entries),
      average: stats.movingAverage4Weeks(entries),
      streak: stats.alcoholFreeStreak(entries),
    };
    // `now` sorgt dafür, dass nach Mitternacht neu gerechnet wird.
  }, [entries, stats, now]);

  if (!entries || !computed) return <Spinner label="Verlauf wird geladen …" />;
  const { today, summary, daily, weekly, comparison, average, streak } = computed;
  const sugarModel = sugarChartModel(daily, today);
  const alcoholModel = alcoholChartModel(weekly, today, settings.showReferenceBands);
  const currentWeek = weekly[weekly.length - 1];

  const groups = groupByDay(entries, stats, today, daysShown);

  const speak = (text: string) => {
    announcer.announce(text);
    services.speaker.speak(text);
  };

  const createImage = async () => {
    setShareError(null);
    setCreating(true);
    try {
      const blob = await renderShareImage({
        dateText: `Stand: ${dayAndMonth(today)} ${today.year}`,
        figures: [
          { label: "Zucker heute", value: formatGrams(summary.sugarGrams) },
          { label: "Alkohol diese Woche", value: formatGrams(currentWeek?.grams ?? 0) },
          { label: "Alkoholfrei in Folge", value: pluralize(streak, "Tag", "Tage") },
        ],
        sugar: sugarModel,
        alcohol: alcoholChartModel(weekly, today, settings.showReferenceBands),
        footer: [HealthReferences.ALCOHOL_MANDATORY_NOTE, HealthReferences.SUGAR_NOTE + " " + HealthReferences.ADULTS_ONLY_NOTE, HealthReferences.INFO_TEXT],
        appIconUrl: `${services.config.basePath}icons/app-512.png`,
      });
      const file = new File([blob], `drink-with-me-verlauf-${today.toString()}.png`, { type: "image/png" });
      setPreview({ blob, url: URL.createObjectURL(blob), file });
    } catch {
      setShareError("Das Bild konnte nicht erzeugt werden.");
    } finally {
      setCreating(false);
    }
  };

  const closePreview = () => {
    if (preview) URL.revokeObjectURL(preview.url);
    setPreview(null);
  };

  return (
    <div class="screen">
      <PageHeader title="Verlauf">
        <p class="lead">Deine persönliche Übersicht – nur auf diesem Gerät.</p>
      </PageHeader>

      <Section title="Heute" id="heute">
        <dl class="figures">
          <Figure label="Zucker aus Getränken" value={formatGrams(summary.sugarGrams)} />
          <Figure label="Alkohol" value={formatGrams(summary.alcoholGrams)} />
          <Figure label="Getränke" value={`${summary.drinkCount} · ${formatVolume(summary.totalMilliliters)}`} />
          <Figure label="davon zuckerhaltig" value={String(summary.sugaryDrinks)} />
          <Figure label="davon alkoholisch" value={String(summary.alcoholicDrinks)} />
          <Figure label="Alkoholfrei in Folge" value={pluralize(streak, "Tag", "Tage")} hint={streak >= 3 ? "Stark – weiter so!" : undefined} />
        </dl>
        <div class="toolbar">
          {services.speaker.available ? (
            <button type="button" class="button button-secondary" onClick={() => speak(SpeechTexts.today(summary))}>
              <UiIcon name="speaker" />
              Heute vorlesen
            </button>
          ) : null}
          <button type="button" class="button button-secondary" onClick={() => void createImage()} disabled={creating}>
            <UiIcon name="share" />
            {creating ? "Bild wird erstellt …" : "Als Bild teilen"}
          </button>
        </div>
        <ErrorText error={shareError} />
      </Section>

      <Section title="Vergleich und Durchschnitt" id="vergleich">
        <dl class="figures">
          <Figure
            label="Zucker: letzte 30 Tage gegenüber den 30 Tagen davor"
            value={comparison.sugarChangePercent === null ? "noch kein Vergleichswert" : formatPercentChange(comparison.sugarChangePercent)}
            hint={comparison.sugarChangePercent !== null && comparison.sugarChangePercent < -0.5 ? "weniger – gut gemacht" : undefined}
          />
          <Figure
            label="Alkohol: letzte 30 Tage gegenüber den 30 Tagen davor"
            value={comparison.alcoholChangePercent === null ? "noch kein Vergleichswert" : formatPercentChange(comparison.alcoholChangePercent)}
            hint={comparison.alcoholChangePercent !== null && comparison.alcoholChangePercent < -0.5 ? "weniger – gut gemacht" : undefined}
          />
          <Figure
            label="Durchschnitt Alkohol pro Woche (4 Wochen)"
            value={average.alcoholGramsPerWeek === null ? "ab 7 erfassten Tagen" : formatGrams(average.alcoholGramsPerWeek)}
          />
          <Figure
            label="Durchschnitt Zucker pro Tag (4 Wochen)"
            value={average.sugarGramsPerDay === null ? "ab 7 erfassten Tagen" : formatGrams(average.sugarGramsPerDay)}
          />
        </dl>
        {comparison.sugarChangePercent === null || comparison.alcoholChangePercent === null ? (
          <p class="muted">Ein Vergleich ist möglich, sobald der vorherige Zeitraum vollständig erfasst ist und Werte enthält.</p>
        ) : null}
        {services.speaker.available ? (
          <button type="button" class="button button-secondary" onClick={() => speak(SpeechTexts.monthComparison(comparison))}>
            <UiIcon name="speaker" />
            Vergleich vorlesen
          </button>
        ) : null}
      </Section>

      <Section title="Zucker" id="zucker">
        <ChartView model={sugarModel} />
      </Section>

      <Section title="Alkohol" id="alkohol">
        <ChartView model={alcoholModel} />
        <div class="toolbar">
          <label class="switch">
            <input
              type="checkbox"
              checked={settings.showReferenceBands}
              onChange={(event) => void services.settings.update({ showReferenceBands: event.currentTarget.checked })}
            />
            <span>Bezugsbereiche anzeigen</span>
          </label>
          {services.speaker.available && currentWeek ? (
            <button type="button" class="button button-secondary" onClick={() => speak(SpeechTexts.weeklyAlcohol(currentWeek, today))}>
              <UiIcon name="speaker" />
              Woche vorlesen
            </button>
          ) : null}
        </div>
      </Section>

      <Section title="Einträge" id="eintraege">
        {entries.length === 0 ? (
          <p>
            Noch keine Einträge. Tippe in der <Link to="/">Bar</Link> auf ein Getränk.
          </p>
        ) : (
          <>
            {groups.map((group) => (
              <div key={group.key} class="day-group">
                <h3>
                  {group.heading} <span class="muted">· {formatGrams(group.sugar)} Zucker · {formatGrams(group.alcohol)} Alkohol</span>
                </h3>
                <ul class="entry-list">
                  {group.entries.map((entry) => (
                    <li key={entry.id} class="entry-row">
                      <DrinkIconImage icon={entry.icon} size={40} />
                      <span class="entry-text">
                        <span class="entry-name">{entry.name}</span>
                        <span class="muted">
                          {formatTime(entry.consumedAt)} Uhr · {formatVolume(entry.volumeMilliliters)} · {formatGrams(entrySugarGrams(entry))} Zucker
                          {isAlcoholic(entry.alcoholPercent) || entry.alcoholPercent > 0 ? ` · ${formatGrams(entryAlcoholGrams(entry))} Alkohol` : ""}
                        </span>
                      </span>
                      <button type="button" class="icon-button icon-button-danger" onClick={() => setDeleting(entry)} aria-label={`${entry.name} um ${formatTime(entry.consumedAt)} Uhr löschen`}>
                        <UiIcon name="trash" />
                      </button>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
            {groups.hasMore ? (
              <button type="button" class="button button-secondary" onClick={() => setDaysShown((days) => days + DAYS_PER_PAGE)}>
                Ältere Einträge anzeigen
              </button>
            ) : null}
          </>
        )}
      </Section>

      <Notice>
        <p>{HealthReferences.INFO_TEXT}</p>
        <p>
          {HealthReferences.ADULTS_ONLY_NOTE} Alle Werte sind Schätzungen aus deinen Angaben (Alkohol in Gramm = ml × Vol.-% ÷ 100 × 0,8).
        </p>
      </Notice>

      <Dialog
        open={deleting !== null}
        title="Eintrag löschen?"
        onClose={() => setDeleting(null)}
        actions={
          <>
            <button
              type="button"
              class="button button-danger"
              onClick={async () => {
                const entry = deleting;
                setDeleting(null);
                if (entry) {
                  await services.deleteEntry.execute(entry.id);
                  announcer.announce(`${entry.name} um ${formatTime(entry.consumedAt)} Uhr wurde gelöscht.`);
                }
              }}
            >
              Löschen
            </button>
            <button type="button" class="button button-secondary" onClick={() => setDeleting(null)}>
              Abbrechen
            </button>
          </>
        }
      >
        <p>
          {deleting?.name} um {deleting ? formatTime(deleting.consumedAt) : ""} Uhr ({deleting ? formatVolume(deleting.volumeMilliliters) : ""}).
          {deleting?.shareWithFriends ? " Das geteilte Ereignis wird bei deinen Freunden ebenfalls entfernt." : ""}
        </p>
      </Dialog>

      <Dialog
        open={preview !== null}
        wide
        title="Bild teilen"
        onClose={closePreview}
        actions={
          <>
            {preview && services.imageSharer.canShareFiles(preview.file) ? (
              <button
                type="button"
                class="button button-primary"
                onClick={async () => {
                  if (!preview) return;
                  try {
                    if (await services.imageSharer.share(preview.file, "Mein Getränke-Verlauf", "Mein Verlauf aus Drink With Me")) closePreview();
                  } catch {
                    setShareError("Teilen hat nicht geklappt. Du kannst das Bild stattdessen speichern.");
                  }
                }}
              >
                <UiIcon name="share" />
                Teilen
              </button>
            ) : null}
            <button
              type="button"
              class="button button-secondary"
              onClick={() => {
                if (preview) services.imageSharer.download(preview.blob, preview.file.name);
              }}
            >
              Bild speichern
            </button>
            <button type="button" class="button button-secondary" onClick={closePreview}>
              Schließen
            </button>
          </>
        }
      >
        <p class="muted">Vorschau – das Bild verlässt dein Gerät nur, wenn du es selbst teilst.</p>
        {preview ? (
          <img
            class="share-preview"
            src={preview.url}
            alt={`Vorschau: Zucker heute ${formatGrams(summary.sugarGrams)}, Alkohol diese Woche ${formatGrams(currentWeek?.grams ?? 0)}, ${pluralize(streak, "alkoholfreier Tag", "alkoholfreie Tage")} in Folge, dazu beide Diagramme.`}
          />
        ) : null}
        <ErrorText error={shareError} />
      </Dialog>
    </div>
  );
}

function Figure({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div class="figure">
      <dt>{label}</dt>
      <dd>
        <span class="figure-value">{value}</span>
        {hint ? <span class="figure-hint">{hint}</span> : null}
      </dd>
    </div>
  );
}

interface DayGroup {
  key: string;
  heading: string;
  entries: DrinkEntry[];
  sugar: number;
  alcohol: number;
}

function groupByDay(
  entries: readonly DrinkEntry[],
  stats: Pick<StatisticsCalculator, "dateOf">,
  today: LocalDate,
  daysShown: number,
): DayGroup[] & { hasMore: boolean } {
  const byDay = new Map<number, DayGroup>();
  for (const entry of [...entries].sort((a, b) => b.consumedAt - a.consumedAt)) {
    const date = stats.dateOf(entry);
    let group = byDay.get(date.epochDay);
    if (!group) {
      group = { key: date.toString(), heading: dayHeading(date, today), entries: [], sugar: 0, alcohol: 0 };
      byDay.set(date.epochDay, group);
    }
    group.entries.push(entry);
    group.sugar += entrySugarGrams(entry);
    group.alcohol += entryAlcoholGrams(entry);
  }
  const all = [...byDay.values()];
  const shown = all.slice(0, daysShown) as DayGroup[] & { hasMore: boolean };
  shown.hasMore = all.length > daysShown;
  return shown;
}
