import type { BarItem } from "./barItem";
import type { DrinkCategory } from "./drinkCategory";
import type { DrinkIcon } from "./drinkIcon";

/**
 * Ein Vorschlag aus dem Katalog.
 *
 * `note`: Zusatz für die Anzeige (z. B. „Spirituose + Cola“); `isEstimate`: Werte nur grob geschätzt
 * (Milch, Cocktail) und so markiert anzuzeigen.
 */
export interface CatalogEntry {
  readonly item: BarItem;
  readonly note: string | null;
  readonly isEstimate: boolean;
}

export const CATALOG_ID_PREFIX = "katalog-";

/** Stabile Katalog-IDs („katalog-…“), identisch mit `DefaultBarCatalog` im Kotlin-Kern. */
export const CatalogIds = {
  WASSER: "katalog-wasser",
  MINERALWASSER: "katalog-mineralwasser",
  KAFFEE: "katalog-kaffee",
  TEE: "katalog-tee",
  COLA: "katalog-cola",
  COLA_ZERO: "katalog-cola-zero",
  LIMONADE: "katalog-limonade",
  EISTEE: "katalog-eistee",
  ENERGYDRINK: "katalog-energydrink",
  ORANGENSAFT: "katalog-orangensaft",
  APFELSAFT: "katalog-apfelsaft",
  APFELSCHORLE: "katalog-apfelschorle",
  MALZBIER: "katalog-malzbier",
  MILCH: "katalog-milch",
  PILS: "katalog-pils",
  WEIZENBIER: "katalog-weizenbier",
  ALKOHOLFREIES_BIER: "katalog-alkoholfreies-bier",
  RADLER: "katalog-radler",
  ROTWEIN: "katalog-rotwein-trocken",
  WEISSWEIN: "katalog-weisswein-trocken",
  SEKT: "katalog-sekt-trocken",
  SCHNAPS: "katalog-schnaps",
  LONGDRINK: "katalog-longdrink",
  COCKTAIL: "katalog-cocktail",
} as const;

interface EntrySpec {
  id: string;
  name: string;
  icon: DrinkIcon;
  category: DrinkCategory;
  sugar: number;
  alcohol: number;
  serving: number;
  aliases?: string[];
  note?: string;
  isEstimate?: boolean;
}

const SPECS: EntrySpec[] = [
  { id: CatalogIds.WASSER, name: "Wasser", icon: "water", category: "WATER", sugar: 0, alcohol: 0, serving: 250,
    aliases: ["Leitungswasser", "Stilles Wasser"] },
  { id: CatalogIds.MINERALWASSER, name: "Mineralwasser", icon: "sparkling_water", category: "WATER", sugar: 0, alcohol: 0,
    serving: 250, aliases: ["Sprudel", "Sprudelwasser"] },
  { id: CatalogIds.KAFFEE, name: "Kaffee", icon: "coffee", category: "COFFEE", sugar: 0, alcohol: 0, serving: 200,
    aliases: ["Filterkaffee"] },
  { id: CatalogIds.TEE, name: "Tee", icon: "tea", category: "TEA", sugar: 0, alcohol: 0, serving: 250,
    aliases: ["Kräutertee", "Früchtetee"] },
  { id: CatalogIds.COLA, name: "Cola", icon: "cola", category: "SOFT_DRINK", sugar: 10.6, alcohol: 0, serving: 330 },
  { id: CatalogIds.COLA_ZERO, name: "Cola zero", icon: "cola", category: "SOFT_DRINK", sugar: 0, alcohol: 0, serving: 330,
    aliases: ["Cola light", "Zuckerfreie Cola"] },
  { id: CatalogIds.LIMONADE, name: "Limonade", icon: "lemonade", category: "SOFT_DRINK", sugar: 8, alcohol: 0, serving: 330,
    aliases: ["Limo", "Brause"] },
  { id: CatalogIds.EISTEE, name: "Eistee", icon: "iced_tea", category: "SOFT_DRINK", sugar: 7.5, alcohol: 0, serving: 500,
    aliases: ["Ice Tea"] },
  { id: CatalogIds.ENERGYDRINK, name: "Energydrink", icon: "energy_drink", category: "ENERGY_DRINK", sugar: 11, alcohol: 0,
    serving: 250, aliases: ["Energy Drink", "Energy"] },
  { id: CatalogIds.ORANGENSAFT, name: "Orangensaft", icon: "juice", category: "JUICE", sugar: 9, alcohol: 0, serving: 200,
    aliases: ["O-Saft"] },
  { id: CatalogIds.APFELSAFT, name: "Apfelsaft", icon: "juice", category: "JUICE", sugar: 10, alcohol: 0, serving: 200 },
  { id: CatalogIds.APFELSCHORLE, name: "Apfelschorle", icon: "apple_spritzer", category: "JUICE", sugar: 5.5, alcohol: 0,
    serving: 500, aliases: ["Apfelsaftschorle", "Schorle"] },
  { id: CatalogIds.MALZBIER, name: "Malzbier", icon: "malt_beer", category: "SOFT_DRINK", sugar: 9, alcohol: 0, serving: 330,
    aliases: ["Malztrunk"] },
  { id: CatalogIds.MILCH, name: "Milch", icon: "milk", category: "MILK", sugar: 4.8, alcohol: 0, serving: 200,
    aliases: ["Vollmilch"], note: "Zucker = Milchzucker (Schätzwert)", isEstimate: true },
  { id: CatalogIds.PILS, name: "Pils", icon: "beer", category: "BEER", sugar: 0.3, alcohol: 4.9, serving: 500,
    aliases: ["Bier", "Pilsener", "Pilsner"] },
  { id: CatalogIds.WEIZENBIER, name: "Weizenbier", icon: "wheat_beer", category: "BEER", sugar: 0.3, alcohol: 5.4, serving: 500,
    aliases: ["Weizen", "Weißbier", "Hefeweizen"] },
  { id: CatalogIds.ALKOHOLFREIES_BIER, name: "Alkoholfreies Bier", icon: "beer", category: "BEER", sugar: 2.5, alcohol: 0.4,
    serving: 500, aliases: ["Alkoholfrei"] },
  { id: CatalogIds.RADLER, name: "Radler", icon: "radler", category: "BEER", sugar: 5, alcohol: 2.5, serving: 500,
    aliases: ["Alster"] },
  { id: CatalogIds.ROTWEIN, name: "Rotwein trocken", icon: "red_wine", category: "WINE", sugar: 0.3, alcohol: 12, serving: 125,
    aliases: ["Rotwein"] },
  { id: CatalogIds.WEISSWEIN, name: "Weißwein trocken", icon: "white_wine", category: "WINE", sugar: 0.3, alcohol: 11.5,
    serving: 125, aliases: ["Weißwein"] },
  { id: CatalogIds.SEKT, name: "Sekt trocken", icon: "sparkling_wine", category: "SPARKLING_WINE", sugar: 2.5, alcohol: 11,
    serving: 100, aliases: ["Sekt", "Prosecco"] },
  { id: CatalogIds.SCHNAPS, name: "Schnaps", icon: "shot", category: "SPIRIT", sugar: 0, alcohol: 40, serving: 20,
    aliases: ["Kurzer", "Klarer", "Shot"] },
  { id: CatalogIds.LONGDRINK, name: "Longdrink", icon: "longdrink", category: "SPIRIT", sugar: 8.7, alcohol: 5.3, serving: 240,
    aliases: ["Rum Cola", "Whisky Cola"], note: "Spirituose + Cola" },
  { id: CatalogIds.COCKTAIL, name: "Cocktail", icon: "cocktail", category: "COCKTAIL", sugar: 8, alcohol: 10, serving: 200,
    note: "Schätzwert", isEstimate: true },
];

/** Alle Vorschläge in Anzeigereihenfolge (`sortOrder` = Position). */
export const CATALOG_ENTRIES: readonly CatalogEntry[] = SPECS.map((spec, index) => ({
  item: {
    id: spec.id,
    name: spec.name,
    icon: spec.icon,
    category: spec.category,
    servingMilliliters: spec.serving,
    alcoholPercent: spec.alcohol,
    sugarGramsPer100Ml: spec.sugar,
    aliases: spec.aliases ?? [],
    valueSource: "DEFAULT",
    sortOrder: index,
  },
  note: spec.note ?? null,
  isEstimate: spec.isEstimate ?? false,
}));

/** Alle Vorschläge als Bar-Getränke. */
export const CATALOG_ITEMS: readonly BarItem[] = CATALOG_ENTRIES.map((entry) => entry.item);

/** Vorauswahl beim ersten Start: Wasser, Kaffee, Tee, Apfelschorle, Cola, Pils, Rotwein. */
export const DEFAULT_SELECTION_IDS: readonly string[] = [
  CatalogIds.WASSER,
  CatalogIds.KAFFEE,
  CatalogIds.TEE,
  CatalogIds.APFELSCHORLE,
  CatalogIds.COLA,
  CatalogIds.PILS,
  CatalogIds.ROTWEIN,
];

export function catalogEntry(id: string): CatalogEntry | null {
  return CATALOG_ENTRIES.find((entry) => entry.item.id === id) ?? null;
}

export function catalogItem(id: string): BarItem | null {
  return catalogEntry(id)?.item ?? null;
}

/** Die Vorauswahl in dieser Reihenfolge, mit `sortOrder` 0, 1, 2 … */
export function defaultSelection(): BarItem[] {
  return DEFAULT_SELECTION_IDS.map(catalogItem)
    .filter((item): item is BarItem => item !== null)
    .map((item, index) => ({ ...item, sortOrder: index }));
}
