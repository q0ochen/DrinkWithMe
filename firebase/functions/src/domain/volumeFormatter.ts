/**
 * Wandelt Milliliter in gut lesbaren Text um („250 ml“, „1,5 l“) –
 * identisch zu `VolumeFormatter` im Kotlin-Kern: ab 1000 ml auf Zehntelliter gerundet,
 * mit Dezimalkomma, ganze Liter ohne Nachkommastelle.
 */
export function formatVolume(milliliters: number): string {
  if (milliliters < 1_000) return `${milliliters} ml`;
  const tenthsOfLiter = Math.trunc((milliliters + 50) / 100);
  const whole = Math.trunc(tenthsOfLiter / 10);
  const fraction = tenthsOfLiter % 10;
  return fraction === 0 ? `${whole} l` : `${whole},${fraction} l`;
}
