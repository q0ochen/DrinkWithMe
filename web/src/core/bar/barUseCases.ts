import { normalizedBarItem, type BarItem } from "./barItem";
import { DefaultBarItemValidator, requireValid, type BarItemValidator } from "./barItemValidation";
import type { BarWriter } from "./barRepository";
import { catalogItem, DEFAULT_SELECTION_IDS } from "./defaultBarCatalog";

/**
 * Speichert ein neues oder geändertes Bar-Getränk: bereinigt es, prüft es und legt es ab.
 * Wirft `InvalidBarItemError`, wenn das Getränk ungültig ist; dann wird nichts gespeichert.
 */
export class SaveBarItemUseCase {
  constructor(
    private readonly writer: BarWriter,
    private readonly validator: BarItemValidator = new DefaultBarItemValidator(),
  ) {}

  async execute(item: BarItem): Promise<BarItem> {
    const clean = requireValid(this.validator, normalizedBarItem(item));
    await this.writer.save(clean);
    return clean;
  }
}

/**
 * Erster Start: übernimmt die ausgewählten Vorschläge in die eigene Bar, in der Reihenfolge der Auswahl.
 * Unbekannte IDs werden ignoriert. Gibt die gespeicherten Getränke zurück.
 */
export class SetUpBarFromCatalogUseCase {
  constructor(
    private readonly writer: BarWriter,
    private readonly lookup: (id: string) => BarItem | null = catalogItem,
  ) {}

  async execute(selectedIds: readonly string[] = DEFAULT_SELECTION_IDS): Promise<BarItem[]> {
    const items = [...new Set(selectedIds)]
      .map(this.lookup)
      .filter((item): item is BarItem => item !== null)
      .map((item, index) => ({ ...item, sortOrder: index }));
    for (const item of items) await this.writer.save(item);
    return items;
  }
}
