import { useState } from "preact/hooks";
import type { BarItem } from "../../core/bar/barItem";
import { DefaultBarItemValidator, InvalidBarItemError, type BarItemError } from "../../core/bar/barItemValidation";
import { CATALOG_ENTRIES } from "../../core/bar/defaultBarCatalog";
import { categoryDisplayName, DRINK_CATEGORIES, type DrinkCategory } from "../../core/bar/drinkCategory";
import { defaultIconFor, DRINK_ICON_KEYS, drinkIconLabel, type DrinkIcon } from "../../core/bar/drinkIcon";
import { Dialog } from "../components/Dialog";
import { DrinkIconImage, ErrorText, Link, PageHeader, Section, Spinner, UiIcon } from "../components/common";
import { useAnnouncer, useBarItems, useDocumentTitle, useServices } from "../context";
import { describeContents } from "./drinkTexts";

interface Draft {
  id: string | null;
  name: string;
  icon: DrinkIcon;
  category: DrinkCategory;
  serving: string;
  alcohol: string;
  sugar: string;
  aliases: string;
  iconChosen: boolean;
}

const toNumber = (text: string): number => Number(text.trim().replace(",", "."));

function draftOf(item: BarItem | null): Draft {
  if (!item) {
    return { id: null, name: "", icon: "other", category: "OTHER", serving: "250", alcohol: "0", sugar: "0", aliases: "", iconChosen: false };
  }
  return {
    id: item.id,
    name: item.name,
    icon: item.icon,
    category: item.category,
    serving: String(item.servingMilliliters),
    alcohol: String(item.alcoholPercent).replace(".", ","),
    sugar: String(item.sugarGramsPer100Ml).replace(".", ","),
    aliases: item.aliases.join(", "),
    iconChosen: true,
  };
}

/** Bar bearbeiten: Reihenfolge, Werte, Entfernen, Vorschläge und eigene Getränke. */
export function BarEditScreen() {
  useDocumentTitle("Bar bearbeiten");
  const services = useServices();
  const announcer = useAnnouncer();
  const items = useBarItems();
  const [editing, setEditing] = useState<{ draft: Draft; original: BarItem | null } | null>(null);
  const [removing, setRemoving] = useState<BarItem | null>(null);

  if (items === null) return <Spinner label="Bar wird geladen …" />;
  const inBar = new Set(items.map((item) => item.id));
  const suggestions = CATALOG_ENTRIES.filter((entry) => !inBar.has(entry.item.id));

  const move = async (index: number, delta: number) => {
    const target = index + delta;
    if (target < 0 || target >= items.length) return;
    const ids = items.map((item) => item.id);
    [ids[index], ids[target]] = [ids[target], ids[index]];
    await services.bar.reorder(ids);
    announcer.announce(`${items[index].name} ist jetzt an Position ${target + 1} von ${items.length}.`);
  };

  const addSuggestion = async (item: BarItem) => {
    await services.saveBarItem.execute({ ...item, sortOrder: items.length });
    announcer.announce(`${item.name} ist jetzt in deiner Bar.`);
  };

  return (
    <div class="screen">
      <PageHeader title="Bar bearbeiten">
        <p class="lead">Ändere Reihenfolge und Werte, entferne Getränke oder lege eigene an. Die Historie bleibt unverändert.</p>
      </PageHeader>
      <div class="toolbar">
        <Link to="/" class="button button-primary">
          <UiIcon name="back" />
          Fertig
        </Link>
        <button type="button" class="button button-secondary" onClick={() => setEditing({ draft: draftOf(null), original: null })}>
          <UiIcon name="plus" />
          Eigenes Getränk
        </button>
      </div>

      <Section title="In deiner Bar" id="in-der-bar">
        {items.length === 0 ? <p>Noch keine Getränke. Füge unten Vorschläge hinzu oder lege ein eigenes Getränk an.</p> : null}
        <ol class="edit-list">
          {items.map((item, index) => (
            <li key={item.id} class="edit-row">
              <DrinkIconImage icon={item.icon} size={44} />
              <span class="edit-text">
                <span class="edit-name">{item.name}</span>
                <span class="muted">{describeContents(item)}</span>
              </span>
              <span class="edit-actions">
                <button type="button" class="icon-button" onClick={() => void move(index, -1)} disabled={index === 0} aria-label={`${item.name} nach oben`}>
                  <UiIcon name="up" />
                </button>
                <button
                  type="button"
                  class="icon-button"
                  onClick={() => void move(index, 1)}
                  disabled={index === items.length - 1}
                  aria-label={`${item.name} nach unten`}
                >
                  <UiIcon name="down" />
                </button>
                <button type="button" class="icon-button" onClick={() => setEditing({ draft: draftOf(item), original: item })} aria-label={`${item.name} bearbeiten`}>
                  <UiIcon name="edit" />
                </button>
                <button type="button" class="icon-button icon-button-danger" onClick={() => setRemoving(item)} aria-label={`${item.name} entfernen`}>
                  <UiIcon name="trash" />
                </button>
              </span>
            </li>
          ))}
        </ol>
      </Section>

      {suggestions.length > 0 ? (
        <Section title="Vorschläge hinzufügen" id="vorschlaege">
          <ul class="suggestion-list">
            {suggestions.map((entry) => (
              <li key={entry.item.id}>
                <button type="button" class="suggestion" onClick={() => void addSuggestion(entry.item)}>
                  <DrinkIconImage icon={entry.item.icon} size={40} />
                  <span class="edit-text">
                    <span class="edit-name">{entry.item.name}</span>
                    <span class="muted">
                      {describeContents(entry.item)}
                      {entry.note ? ` · ${entry.note}` : ""}
                    </span>
                  </span>
                  <span class="suggestion-add" aria-hidden="true">
                    <UiIcon name="plus" />
                  </span>
                  <span class="visually-hidden">hinzufügen</span>
                </button>
              </li>
            ))}
          </ul>
        </Section>
      ) : null}

      {editing ? (
        <BarItemEditor
          draft={editing.draft}
          original={editing.original}
          nextSortOrder={items.length}
          onClose={() => setEditing(null)}
          onSaved={(item) => {
            setEditing(null);
            announcer.announce(`${item.name} gespeichert.`);
          }}
        />
      ) : null}

      <Dialog
        open={removing !== null}
        title={`${removing?.name ?? ""} entfernen?`}
        onClose={() => setRemoving(null)}
        actions={
          <>
            <button
              type="button"
              class="button button-danger"
              onClick={async () => {
                const item = removing;
                setRemoving(null);
                if (item) {
                  await services.bar.delete(item.id);
                  announcer.announce(`${item.name} wurde aus der Bar entfernt.`);
                }
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
        <p>Das Getränk verschwindet aus deiner Bar. Bisherige Einträge im Verlauf bleiben erhalten.</p>
      </Dialog>
    </div>
  );
}

function BarItemEditor({
  draft: initial,
  original,
  nextSortOrder,
  onClose,
  onSaved,
}: {
  draft: Draft;
  original: BarItem | null;
  nextSortOrder: number;
  onClose: () => void;
  onSaved: (item: BarItem) => void;
}) {
  const services = useServices();
  const [draft, setDraft] = useState(initial);
  const [errors, setErrors] = useState<BarItemError[]>([]);
  const update = (patch: Partial<Draft>) => setDraft((current) => ({ ...current, ...patch }));

  const build = (): BarItem => {
    const values = { serving: toNumber(draft.serving), alcohol: toNumber(draft.alcohol), sugar: toNumber(draft.sugar) };
    const changed =
      !original ||
      values.serving !== original.servingMilliliters ||
      values.alcohol !== original.alcoholPercent ||
      values.sugar !== original.sugarGramsPer100Ml;
    return {
      id: original?.id ?? services.ids.newId(),
      name: draft.name,
      icon: draft.iconChosen ? draft.icon : defaultIconFor(draft.category),
      category: draft.category,
      servingMilliliters: values.serving,
      alcoholPercent: values.alcohol,
      sugarGramsPer100Ml: values.sugar,
      aliases: draft.aliases.split(",").map((alias) => alias.trim()).filter(Boolean),
      valueSource: changed ? "USER" : (original?.valueSource ?? "USER"),
      sortOrder: original?.sortOrder ?? nextSortOrder,
    };
  };

  const save = async () => {
    const item = build();
    const problems = new DefaultBarItemValidator().validate(item);
    setErrors(problems);
    if (problems.length > 0) return;
    try {
      onSaved(await services.saveBarItem.execute(item));
    } catch (error) {
      if (error instanceof InvalidBarItemError) setErrors([...error.errors]);
      else throw error;
    }
  };

  const errorFor = (...kinds: BarItemError["kind"][]) => errors.find((error) => kinds.includes(error.kind))?.message ?? null;
  const field = (id: string, error: string | null) => ({ id, "aria-invalid": error ? ("true" as const) : undefined, "aria-describedby": error ? `${id}-fehler` : undefined });
  const nameError = errorFor("EmptyName", "NameTooLong");
  const servingError = errorFor("ServingOutOfRange");
  const alcoholError = errorFor("AlcoholOutOfRange");
  const sugarError = errorFor("SugarOutOfRange");
  const aliasError = errorFor("BlankAlias", "AliasTooLong");

  return (
    <Dialog
      open
      wide
      title={original ? `${original.name} bearbeiten` : "Eigenes Getränk"}
      onClose={onClose}
      actions={
        <>
          <button type="submit" form="getraenk-formular" class="button button-primary">
            Speichern
          </button>
          <button type="button" class="button button-secondary" onClick={onClose}>
            Abbrechen
          </button>
        </>
      }
    >
      <form
        id="getraenk-formular"
        class="form"
        noValidate
        onSubmit={(event) => {
          event.preventDefault();
          void save();
        }}
      >
        <div class="field">
          <label for="getraenk-name">Name</label>
          <input {...field("getraenk-name", nameError)} type="text" maxLength={40} autoComplete="off" value={draft.name} onInput={(event) => update({ name: event.currentTarget.value })} />
          <FieldError id="getraenk-name" error={nameError} />
        </div>
        <div class="field">
          <label for="getraenk-art">Art</label>
          <select id="getraenk-art" value={draft.category} onChange={(event) => update({ category: event.currentTarget.value as DrinkCategory })}>
            {DRINK_CATEGORIES.map((category) => (
              <option key={category} value={category}>
                {categoryDisplayName(category)}
              </option>
            ))}
          </select>
        </div>
        <fieldset class="field icon-picker">
          <legend>Bild</legend>
          <div class="icon-options">
            {DRINK_ICON_KEYS.map((icon) => (
              <label key={icon} class={draft.icon === icon && draft.iconChosen ? "icon-option is-checked" : "icon-option"}>
                <input type="radio" name="getraenk-bild" value={icon} checked={draft.iconChosen && draft.icon === icon} onChange={() => update({ icon, iconChosen: true })} />
                <DrinkIconImage icon={icon} size={40} />
                <span class="visually-hidden">{drinkIconLabel(icon)}</span>
              </label>
            ))}
          </div>
          {!draft.iconChosen ? <p class="muted">Ohne Auswahl passt das Bild zur Art ({drinkIconLabel(defaultIconFor(draft.category))}).</p> : null}
        </fieldset>
        <div class="field-row">
          <div class="field">
            <label for="getraenk-portion">Portion in ml</label>
            <input {...field("getraenk-portion", servingError)} type="text" inputMode="numeric" value={draft.serving} onInput={(event) => update({ serving: event.currentTarget.value })} />
            <FieldError id="getraenk-portion" error={servingError} />
          </div>
          <div class="field">
            <label for="getraenk-alkohol">Alkohol in Vol.-%</label>
            <input {...field("getraenk-alkohol", alcoholError)} type="text" inputMode="decimal" value={draft.alcohol} onInput={(event) => update({ alcohol: event.currentTarget.value })} />
            <FieldError id="getraenk-alkohol" error={alcoholError} />
          </div>
          <div class="field">
            <label for="getraenk-zucker">Zucker in g pro 100 ml</label>
            <input {...field("getraenk-zucker", sugarError)} type="text" inputMode="decimal" value={draft.sugar} onInput={(event) => update({ sugar: event.currentTarget.value })} />
            <FieldError id="getraenk-zucker" error={sugarError} />
          </div>
        </div>
        <div class="field">
          <label for="getraenk-aliase">Andere Namen für die Sprachsteuerung (mit Komma getrennt)</label>
          <input {...field("getraenk-aliase", aliasError)} type="text" autoComplete="off" value={draft.aliases} onInput={(event) => update({ aliases: event.currentTarget.value })} />
          <FieldError id="getraenk-aliase" error={aliasError} />
        </div>
        {errors.length > 0 ? <ErrorText error="Bitte prüfe die markierten Felder." /> : null}
      </form>
    </Dialog>
  );
}

function FieldError({ id, error }: { id: string; error: string | null }) {
  if (!error) return null;
  return (
    <p id={`${id}-fehler`} class="field-error">
      {error}
    </p>
  );
}
