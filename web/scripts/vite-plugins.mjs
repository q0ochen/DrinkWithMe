// Kleine Vite-Plugins der Web-App (reines JavaScript, damit vite.config.ts sie ohne Übersetzung lädt).
import { createHash } from "node:crypto";
import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import { build } from "vite";

/**
 * GitHub Pages kennt keine SPA-Umleitung: Unbekannte Pfade (z. B. /DrinkWithMe/einladung/<token> oder
 * /DrinkWithMe/auth/callback) liefern die Datei 404.html aus. Sie ist hier eine exakte Kopie von index.html,
 * die App liest dann den Pfad selbst (History-Routing). Alle Asset-Pfade sind absolut (Vite-`base`).
 * @returns {import("vite").Plugin}
 */
export function spaFallbackPlugin() {
  return {
    name: "drink-with-me:spa-fallback",
    apply: "build",
    enforce: "post",
    generateBundle(_options, bundle) {
      const index = bundle["index.html"];
      if (index && index.type === "asset") {
        this.emitFile({ type: "asset", fileName: "404.html", source: index.source });
      }
    },
  };
}

/**
 * Content-Security-Policy als <meta> (nur im Build; der Entwicklungsserver braucht Inline-Skripte).
 * Erlaubt Verbindungen nur zur eigenen Herkunft und zum konfigurierten Supabase-Projekt.
 * @param {{ supabaseUrl?: string }} options
 * @returns {import("vite").Plugin}
 */
export function contentSecurityPolicyPlugin({ supabaseUrl }) {
  let supabaseOrigin = "";
  if (supabaseUrl) {
    try {
      supabaseOrigin = new URL(supabaseUrl).origin;
    } catch {
      supabaseOrigin = "";
    }
  }
  const policy = [
    "default-src 'self'",
    "script-src 'self'",
    "style-src 'self'",
    "img-src 'self' blob: data:",
    `connect-src 'self'${supabaseOrigin ? ` ${supabaseOrigin}` : ""}`,
    "worker-src 'self'",
    "manifest-src 'self'",
    "font-src 'self'",
    "media-src 'none'",
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'none'",
  ].join("; ");
  return {
    name: "drink-with-me:csp",
    apply: "build",
    transformIndexHtml: {
      order: "post",
      handler() {
        return [{ tag: "meta", attrs: { "http-equiv": "Content-Security-Policy", content: policy }, injectTo: "head-prepend" }];
      },
    },
  };
}

/**
 * Baut den Service Worker (src/sw/sw.ts) als eigenständiges IIFE nach <outDir>/sw.js – an der Wurzel der
 * App, damit er die ganze App (Scope = base) steuern darf. Er bekommt die Liste aller ausgelieferten Dateien
 * (für den Offline-Start) und eine Version (Hash über alle Inhalte) eingesetzt.
 * @param {{ entry: string }} options
 * @returns {import("vite").Plugin}
 */
export function serviceWorkerPlugin({ entry }) {
  /** @type {import("vite").ResolvedConfig} */
  let config;
  /** @type {string[]} */
  let precache = [];
  let version = "";
  return {
    name: "drink-with-me:service-worker",
    apply: "build",
    enforce: "post",
    configResolved(resolved) {
      config = resolved;
    },
    async generateBundle(_options, bundle) {
      const hash = createHash("sha256");
      const files = [];
      for (const [fileName, output] of Object.entries(bundle).sort(([a], [b]) => a.localeCompare(b))) {
        if (fileName.endsWith(".map") || fileName === "404.html") continue;
        files.push(fileName);
        hash.update(fileName);
        hash.update(output.type === "chunk" ? output.code : output.source);
      }
      if (config.publicDir) {
        for (const file of await listFiles(config.publicDir)) {
          if (file.relative.endsWith(".txt") && file.relative.includes("LICENSE")) continue;
          files.push(file.relative);
          hash.update(file.relative);
          hash.update(await readFile(file.absolute));
        }
      }
      precache = [...new Set(files)].filter((file) => file !== "sw.js").sort();
      version = hash.digest("hex").slice(0, 16);
    },
    async closeBundle() {
      if (!config || precache.length === 0) return;
      await build({
        configFile: false,
        root: config.root,
        base: config.base,
        mode: config.mode,
        logLevel: "warn",
        publicDir: false,
        define: {
          __DWM_PRECACHE__: JSON.stringify(precache),
          __DWM_SW_VERSION__: JSON.stringify(version),
        },
        build: {
          outDir: config.build.outDir,
          emptyOutDir: false,
          copyPublicDir: false,
          sourcemap: false,
          minify: true,
          target: "es2022",
          lib: {
            entry: path.resolve(config.root, entry),
            formats: ["iife"],
            name: "DrinkWithMeServiceWorker",
            fileName: () => "sw.js",
          },
        },
      });
    },
  };
}

/**
 * @param {string} directory
 * @param {string} [prefix]
 * @returns {Promise<{ relative: string; absolute: string }[]>}
 */
async function listFiles(directory, prefix = "") {
  /** @type {{ relative: string; absolute: string }[]} */
  const result = [];
  let entries;
  try {
    entries = await readdir(directory, { withFileTypes: true });
  } catch {
    return result;
  }
  for (const entry of entries) {
    const absolute = path.join(directory, entry.name);
    const relative = prefix ? `${prefix}/${entry.name}` : entry.name;
    if (entry.isDirectory()) result.push(...(await listFiles(absolute, relative)));
    else if (entry.isFile()) result.push({ relative, absolute });
  }
  return result;
}
