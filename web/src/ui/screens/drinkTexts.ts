import type { BarItem } from "../../core/bar/barItem";
import { isAlcoholic, isSugary, isVerySugary } from "../../core/health/healthMath";
import { formatVolume } from "../../core/sharing/sharedDrink";
import { formatNumber } from "../format";

/** „500 ml · 5,5 g Zucker/100 ml“ bzw. „500 ml · 4,9 % Alkohol“ – Anzeige im Katalog und in der Bar. */
export function describeContents(item: BarItem): string {
  const parts = [formatVolume(item.servingMilliliters)];
  if (isAlcoholic(item.alcoholPercent)) parts.push(`${formatNumber(item.alcoholPercent)} % Alkohol`);
  else if (item.alcoholPercent > 0) parts.push(`${formatNumber(item.alcoholPercent)} % (alkoholfrei)`);
  if (item.sugarGramsPer100Ml > 0) parts.push(`${formatNumber(item.sugarGramsPer100Ml)} g Zucker/100 ml`);
  // Zahl und Einheit nicht trennen (geschütztes Leerzeichen), nur zwischen den Angaben umbrechen.
  return parts.map((part) => part.replace(/ /g, "\u00a0")).join(" · ");
}

/** Kurzes Merkmal für die Kachel: „stark zuckerhaltig“, „zuckerhaltig“, „alkoholisch“ (Form + Text, nicht nur Farbe). */
export function healthTag(item: { alcoholPercent: number; sugarGramsPer100Ml: number }): string | null {
  if (isAlcoholic(item.alcoholPercent)) return "alkoholisch";
  if (isVerySugary(item.sugarGramsPer100Ml)) return "stark zuckerhaltig";
  if (isSugary(item.sugarGramsPer100Ml)) return "zuckerhaltig";
  return null;
}
