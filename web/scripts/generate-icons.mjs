// Übernimmt die selbst gezeichneten Grafiken aus design/icons in die Web-App (public/icons) und erzeugt daraus
// die PNG-Symbole für Manifest, iPhone-Home-Bildschirm und Benachrichtigungen (mit Chromium über Playwright).
// Quelle bleibt immer design/icons – die Ausgaben sind erzeugt und nicht eingecheckt (siehe web/.gitignore).
//
//   node scripts/generate-icons.mjs          # nur, wenn sich eine Quelle geändert hat
//   node scripts/generate-icons.mjs --force  # alles neu erzeugen
import { copyFile, mkdir, readdir, readFile, stat, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const webRoot = path.resolve(here, "..");
const sourceDir = path.resolve(webRoot, "../design/icons");
const publicDir = path.join(webRoot, "public");
const iconDir = path.join(publicDir, "icons");
const force = process.argv.includes("--force");

/** PNG-Ausgaben: Größe, Quelle und Form („rund“ = abgerundete Ecken mit Transparenz, „voll“ = randlos). */
const RASTER_TARGETS = [
  { file: path.join(iconDir, "app-192.png"), source: "app_icon.svg", size: 192, shape: "rounded" },
  { file: path.join(iconDir, "app-512.png"), source: "app_icon.svg", size: 512, shape: "rounded" },
  { file: path.join(iconDir, "app-maskable-192.png"), source: "app_icon.svg", size: 192, shape: "full" },
  { file: path.join(iconDir, "app-maskable-512.png"), source: "app_icon.svg", size: 512, shape: "full" },
  { file: path.join(publicDir, "apple-touch-icon.png"), source: "app_icon.svg", size: 180, shape: "full" },
  { file: path.join(publicDir, "favicon.png"), source: "app_icon.svg", size: 32, shape: "rounded" },
  { file: path.join(iconDir, "badge-96.png"), source: "notification.svg", size: 96, shape: "full" },
];

async function mtime(file) {
  try {
    return (await stat(file)).mtimeMs;
  } catch {
    return 0;
  }
}

async function copySvgs() {
  await mkdir(iconDir, { recursive: true });
  // TV-Banner und die Einzelebenen des adaptiven Android-Symbols braucht die Web-App nicht.
  const files = (await readdir(sourceDir)).filter((name) => name.endsWith(".svg") && !name.startsWith("tv_") && !name.startsWith("app_icon_"));
  if (files.length === 0) throw new Error(`Keine SVGs in ${sourceDir} gefunden.`);
  let copied = 0;
  for (const name of files) {
    const from = path.join(sourceDir, name);
    const to = path.join(iconDir, name);
    if (force || (await mtime(to)) < (await mtime(from))) {
      await copyFile(from, to);
      copied++;
    }
  }
  const license = await readFile(path.join(sourceDir, "LICENSE"), "utf8").catch(() => null);
  if (license !== null) await writeFile(path.join(iconDir, "LICENSE.txt"), license);
  return { total: files.length, copied };
}

async function rasterize() {
  const scriptTime = await mtime(fileURLToPath(import.meta.url));
  const pending = [];
  for (const target of RASTER_TARGETS) {
    const sourceTime = Math.max(await mtime(path.join(sourceDir, target.source)), scriptTime);
    if (force || (await mtime(target.file)) < sourceTime) pending.push(target);
  }
  if (pending.length === 0) return 0;

  let chromium;
  try {
    ({ chromium } = await import("@playwright/test"));
  } catch (error) {
    throw new Error("Playwright fehlt – bitte zuerst `npm ci` im Ordner web ausführen.", { cause: error });
  }
  let browser;
  try {
    browser = await chromium.launch();
  } catch (error) {
    throw new Error(
      "Chromium für Playwright ist nicht installiert. Einmalig ausführen: `npx playwright install chromium` (in der CI: --with-deps).\n" +
        String(error),
      { cause: error },
    );
  }
  try {
    const page = await browser.newPage({ deviceScaleFactor: 1 });
    for (const target of pending) {
      const svg = await readFile(path.join(sourceDir, target.source));
      const dataUrl = `data:image/svg+xml;base64,${svg.toString("base64")}`;
      const radius = target.shape === "rounded" ? Math.round(target.size * 0.22) : 0;
      await page.setViewportSize({ width: target.size, height: target.size });
      await page.setContent(
        `<!doctype html><html><head><style>html,body{margin:0;padding:0;background:transparent}` +
          `img{display:block;width:${target.size}px;height:${target.size}px;border-radius:${radius}px}</style></head>` +
          `<body><img id="icon" src="${dataUrl}" alt=""></body></html>`,
      );
      await page.waitForFunction(() => {
        const image = document.getElementById("icon");
        return image instanceof HTMLImageElement && image.complete && image.naturalWidth > 0;
      });
      await mkdir(path.dirname(target.file), { recursive: true });
      await page.screenshot({
        path: target.file,
        omitBackground: true,
        clip: { x: 0, y: 0, width: target.size, height: target.size },
      });
    }
  } finally {
    await browser.close();
  }
  return pending.length;
}

try {
  const { total, copied } = await copySvgs();
  const rendered = await rasterize();
  console.log(`Icons: ${total} SVGs (${copied} aktualisiert), ${rendered} PNGs erzeugt → web/public.`);
} catch (error) {
  console.error(`Icons konnten nicht erzeugt werden: ${error instanceof Error ? error.message : String(error)}`);
  process.exit(1);
}
