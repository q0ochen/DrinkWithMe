// Gemeinsame Hilfen für vite.config.ts und die Build-Skripte (reines JavaScript, damit Node sie direkt lädt).

/** Standardadresse der Web-App (GitHub Pages), siehe docs/ARCHITEKTUR.md, Abschnitt 4. */
export const DEFAULT_WEB_APP_URL = "https://q0ochen.github.io/DrinkWithMe";

/**
 * Vite-`base` aus der Web-App-Adresse: „https://q0ochen.github.io/DrinkWithMe“ → „/DrinkWithMe/“.
 * So bleibt die Basis-URL konfigurierbar (z. B. für einen Fork mit anderem Repository-Namen).
 * @param {string} webAppUrl
 * @returns {string}
 */
export function basePathFromWebAppUrl(webAppUrl) {
  let pathname;
  try {
    pathname = new URL(webAppUrl).pathname;
  } catch {
    throw new Error(`VITE_WEB_APP_URL ist keine gültige Adresse: ${webAppUrl}`);
  }
  const trimmed = pathname.replace(/\/+$/, "");
  return `${trimmed}/`;
}
