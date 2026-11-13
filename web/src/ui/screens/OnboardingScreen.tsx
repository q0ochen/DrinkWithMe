import { useState } from "preact/hooks";
import { CATALOG_ENTRIES, DEFAULT_SELECTION_IDS, type CatalogEntry } from "../../core/bar/defaultBarCatalog";
import { HealthReferences } from "../../core/health/healthReferences";
import { needsHomeScreenForPush } from "../../platform/platform";
import { ImportBackupButton } from "../components/BackupControls";
import { DrinkIconImage, ErrorText, IosInstallFirstHint, Notice, PageHeader, Section } from "../components/common";
import { useAction, useDocumentTitle, useRouter, useServices } from "../context";
import { describeContents } from "./drinkTexts";

/** Erster Start: Bar aus dem Katalog zusammenstellen (Vorauswahl laut Vertrag). */
export function OnboardingScreen() {
  useDocumentTitle("Willkommen");
  const services = useServices();
  const router = useRouter();
  const [selected, setSelected] = useState<string[]>([...DEFAULT_SELECTION_IDS]);
  const [validation, setValidation] = useState<string | null>(null);

  const toggle = (entry: CatalogEntry) => {
    setValidation(null);
    setSelected((current) => (current.includes(entry.item.id) ? current.filter((id) => id !== entry.item.id) : [...current, entry.item.id]));
  };

  const finish = useAction(async () => {
    if (selected.length === 0) {
      setValidation("Wähle mindestens ein Getränk aus. Du kannst die Bar später jederzeit ändern.");
      return;
    }
    const existing = await services.bar.items();
    if (existing.length === 0) await services.setUpBar.execute(selected);
    await done();
  });

  const done = async () => {
    await services.settings.update({ onboardingDone: true });
    // Bar und Verlauf liegen nur auf dem Gerät: Browser bitten, sie nicht bei Platzmangel zu räumen.
    void services.requestPersistentStorage();
    router.navigate("/", { replace: true });
  };

  return (
    <div class="screen">
      <PageHeader title="Willkommen bei Drink With Me">
        <p class="lead">
          Stell dir deine <strong>Bar</strong> zusammen: Wähle die Getränke, die du oft trinkst. Danach trägst du mit einem Tipp ein, was
          du gerade trinkst. Alles bleibt auf diesem Gerät.
        </p>
      </PageHeader>
      {needsHomeScreenForPush(services.platform) ? <IosInstallFirstHint /> : null}
      <form
        class="card"
        onSubmit={(event) => {
          event.preventDefault();
          void finish.run();
        }}
      >
        <fieldset class="catalog-fieldset">
          <legend>
            <h2>Deine Getränke</h2>
            <span class="muted">{selected.length} ausgewählt – alles ist später änderbar, eigene Getränke sind möglich.</span>
          </legend>
          <div class="catalog-grid">
            {CATALOG_ENTRIES.map((entry) => {
              const checked = selected.includes(entry.item.id);
              const id = `katalog-${entry.item.id}`;
              return (
                <label key={entry.item.id} class={checked ? "catalog-option is-checked" : "catalog-option"} for={id}>
                  <input id={id} type="checkbox" checked={checked} onChange={() => toggle(entry)} />
                  <DrinkIconImage icon={entry.item.icon} size={48} />
                  <span class="catalog-text">
                    <span class="catalog-name">{entry.item.name}</span>
                    <span class="catalog-meta">{describeContents(entry.item)}</span>
                    {entry.note ? <span class="catalog-meta">{entry.note}</span> : null}
                  </span>
                </label>
              );
            })}
          </div>
        </fieldset>
        <ErrorText error={validation} />
        <div class="form-actions">
          <button type="submit" class="button button-primary button-large" disabled={finish.busy}>
            Bar einrichten ({selected.length} {selected.length === 1 ? "Getränk" : "Getränke"})
          </button>
        </div>
      </form>
      <Section title="Schon eine Sicherung?" id="sicherung">
        <p>Du hast Drink With Me schon in einem anderen Browser oder auf einem anderen Gerät benutzt? Lies die Sicherungsdatei ein – Bar und Verlauf werden übernommen.</p>
        <ImportBackupButton
          onImported={async (summary) => {
            if (summary.addedBarItems + summary.updatedBarItems + summary.addedEntries > 0) await done();
          }}
        />
      </Section>
      <Notice>
        <p>{HealthReferences.INFO_TEXT}</p>
      </Notice>
    </div>
  );
}
