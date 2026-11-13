import { useRef, useState } from "preact/hooks";
import { BackupError, type ImportSummary } from "../../data/backup";
import { useAction, useAnnouncer, useServices } from "../context";
import { pluralize } from "../format";
import { ErrorText, UiIcon } from "./common";

/** Zusammenfassung nach dem Einlesen, z. B. „Sicherung eingelesen: 12 Einträge und 3 Getränke übernommen.“ */
export function importSummaryText(summary: ImportSummary): string {
  const parts = [
    `Sicherung eingelesen: ${pluralize(summary.addedEntries, "Eintrag", "Einträge")} und ${pluralize(summary.addedBarItems, "Getränk", "Getränke")} übernommen.`,
  ];
  if (summary.updatedBarItems > 0) parts.push(`${pluralize(summary.updatedBarItems, "Getränk", "Getränke")} der Bar aktualisiert.`);
  if (summary.existingEntries > 0) {
    parts.push(`${pluralize(summary.existingEntries, "Eintrag war", "Einträge waren")} schon vorhanden.`);
  }
  if (summary.invalid > 0) parts.push(`${pluralize(summary.invalid, "ungültige Zeile", "ungültige Zeilen")} übersprungen.`);
  return parts.join(" ");
}

/** Dateiname mit dem heutigen Datum, z. B. drink-with-me-sicherung-2026-11-10.json. */
export function backupFileName(isoDate: string): string {
  return `drink-with-me-sicherung-${isoDate}.json`;
}

/** „Sicherung speichern“: JSON-Datei mit Bar und Verlauf – iPhone über „Teilen“ (In Dateien sichern), sonst Download. */
export function ExportBackupButton() {
  const services = useServices();
  const announcer = useAnnouncer();
  const [status, setStatus] = useState<string | null>(null);
  const save = useAction(async () => {
    setStatus(null);
    const json = await services.backup.exportJson();
    const blob = new Blob([json], { type: "application/json" });
    const name = backupFileName(services.statistics.currentDate().toString());
    const file = new File([blob], name, { type: "application/json" });
    if (services.platform.isIos && services.imageSharer.canShareFiles(file)) {
      try {
        if (!(await services.imageSharer.share(file, "Drink With Me – Sicherung", "Bar und Verlauf aus Drink With Me"))) return;
        setStatus("Sicherung geteilt. Bewahre die Datei gut auf – sie enthält deinen ganzen Verlauf.");
        return;
      } catch {
        // Teilen ging nicht – dann herunterladen.
      }
    }
    services.imageSharer.download(blob, name);
    const text = `Sicherung „${name}“ gespeichert. Bewahre die Datei gut auf – sie enthält deinen ganzen Verlauf.`;
    setStatus(text);
    announcer.announce(text);
  });
  return (
    <>
      <button type="button" class="button button-secondary" onClick={() => void save.run()} disabled={save.busy}>
        <UiIcon name="down" />
        Sicherung speichern
      </button>
      {status ? (
        <p class="success-text" role="status">
          {status}
        </p>
      ) : null}
      <ErrorText error={save.error ? "Die Sicherung konnte nicht erstellt werden." : null} />
    </>
  );
}

/**
 * „Sicherung einlesen“: Datei wählen, prüfen und zusammenführen (nichts wird gelöscht, nichts wird geteilt).
 * `onImported` z. B. für den ersten Start (Bar ist dann eingerichtet).
 */
export function ImportBackupButton({ onImported, label = "Sicherung einlesen" }: { onImported?: (summary: ImportSummary) => Promise<void> | void; label?: string }) {
  const services = useServices();
  const announcer = useAnnouncer();
  const input = useRef<HTMLInputElement>(null);
  const [status, setStatus] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const importFile = useAction(async (file: File) => {
    setStatus(null);
    setError(null);
    try {
      const summary = await services.backup.importJson(await file.text());
      const text = importSummaryText(summary);
      setStatus(text);
      announcer.announce(text);
      await onImported?.(summary);
    } catch (caught) {
      setError(caught instanceof BackupError ? caught.message : "Die Datei konnte nicht gelesen werden.");
    }
  });
  return (
    <>
      <input
        ref={input}
        type="file"
        accept="application/json,.json"
        class="visually-hidden"
        tabIndex={-1}
        aria-hidden="true"
        data-testid="sicherung-datei"
        onChange={(event) => {
          const file = event.currentTarget.files?.[0];
          event.currentTarget.value = "";
          if (file) void importFile.run(file);
        }}
      />
      <button type="button" class="button button-secondary" onClick={() => input.current?.click()} disabled={importFile.busy}>
        <UiIcon name="up" />
        {importFile.busy ? "Wird eingelesen …" : label}
      </button>
      {status ? (
        <p class="success-text" role="status">
          {status}
        </p>
      ) : null}
      <ErrorText error={error} />
    </>
  );
}
