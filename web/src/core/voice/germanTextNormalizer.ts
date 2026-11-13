/**
 * Vereinheitlicht gesprochenen bzw. erkannten Text (Port von `GermanTextNormalizer`): Kleinschreibung,
 * ä→ae, ö→oe, ü→ue, ß→ss, sonstige Akzente entfernt (é→e), alles außer a–z und 0–9 wird zu Leerzeichen.
 */
export function normalizeGerman(text: string): string {
  const composed = text.normalize("NFC").toLocaleLowerCase("de-DE");
  let replaced = "";
  for (const char of composed) {
    switch (char) {
      case "ä":
        replaced += "ae";
        break;
      case "ö":
        replaced += "oe";
        break;
      case "ü":
        replaced += "ue";
        break;
      case "ß":
        replaced += "ss";
        break;
      default:
        replaced += char;
    }
  }
  const withoutAccents = replaced.normalize("NFD").replace(/\p{M}+/gu, "");
  return withoutAccents.replace(/[^a-z0-9]+/g, " ").trim();
}

export function germanTokens(text: string): string[] {
  return normalizeGerman(text)
    .split(" ")
    .filter((token) => token.length > 0);
}
