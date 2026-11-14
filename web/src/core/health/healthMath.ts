/** Zuckerstufe eines Getränks (orientiert an den Stufen der britischen Zuckerabgabe). */
export type SugarLevel = "LOW" | "SUGARY" | "VERY_SUGARY";

/** Dichte von Ethanol in g/ml. */
export const ETHANOL_GRAMS_PER_MILLILITER = 0.8;
/** Ab diesem Alkoholgehalt (Vol.-%) gilt ein Getränk als alkoholisch. */
export const ALCOHOLIC_MIN_PERCENT = 1.2;
/** Ab diesem Zuckergehalt (g/100 ml) gilt ein Getränk als zuckerhaltig. */
export const SUGARY_MIN_GRAMS_PER_100_ML = 5.0;
/** Ab diesem Zuckergehalt (g/100 ml) gilt ein Getränk als stark zuckerhaltig. */
export const VERY_SUGARY_MIN_GRAMS_PER_100_ML = 8.0;

/** `alcoholGrams = ml × Vol.-% / 100 × 0,8` (gleiche Rechenreihenfolge wie im Kotlin-Kern). */
export function alcoholGrams(volumeMilliliters: number, alcoholPercent: number): number {
  return ((volumeMilliliters * alcoholPercent) / 100.0) * ETHANOL_GRAMS_PER_MILLILITER;
}

/** `sugarGrams = ml × Zucker / 100` */
export function sugarGrams(volumeMilliliters: number, sugarGramsPer100Ml: number): number {
  return (volumeMilliliters * sugarGramsPer100Ml) / 100.0;
}

export function isAlcoholic(alcoholPercent: number): boolean {
  return alcoholPercent >= ALCOHOLIC_MIN_PERCENT;
}

export function isSugary(sugarGramsPer100Ml: number): boolean {
  return sugarGramsPer100Ml >= SUGARY_MIN_GRAMS_PER_100_ML;
}

export function isVerySugary(sugarGramsPer100Ml: number): boolean {
  return sugarGramsPer100Ml >= VERY_SUGARY_MIN_GRAMS_PER_100_ML;
}

export function sugarLevel(sugarGramsPer100Ml: number): SugarLevel {
  if (isVerySugary(sugarGramsPer100Ml)) return "VERY_SUGARY";
  if (isSugary(sugarGramsPer100Ml)) return "SUGARY";
  return "LOW";
}

/** Alles, was die Gesundheitsrechnung von einem Eintrag oder Bar-Getränk braucht. */
export interface VolumeAndContents {
  readonly alcoholPercent: number;
  readonly sugarGramsPer100Ml: number;
}

export interface LoggedVolume extends VolumeAndContents {
  readonly volumeMilliliters: number;
}

export interface ServingVolume extends VolumeAndContents {
  readonly servingMilliliters: number;
}

/** Gramm Alkohol eines Eintrags (auch bei alkoholfreiem Bier mit 0,4 % vollständig). */
export const entryAlcoholGrams = (entry: LoggedVolume): number => alcoholGrams(entry.volumeMilliliters, entry.alcoholPercent);
/** Gramm Zucker eines Eintrags. */
export const entrySugarGrams = (entry: LoggedVolume): number => sugarGrams(entry.volumeMilliliters, entry.sugarGramsPer100Ml);
/** Gramm Alkohol einer Standardportion. */
export const servingAlcoholGrams = (item: ServingVolume): number => alcoholGrams(item.servingMilliliters, item.alcoholPercent);
/** Gramm Zucker einer Standardportion. */
export const servingSugarGrams = (item: ServingVolume): number => sugarGrams(item.servingMilliliters, item.sugarGramsPer100Ml);
