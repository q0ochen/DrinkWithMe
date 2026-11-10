# Drink With Me – Web-App (PWA)

Die Web-App bringt Drink With Me aufs **iPhone** (Safari, ab iOS 16.4 über „Zum Home-Bildschirm“) und in jeden
Browser auf Desktop und Android. Vertrag: [`docs/ARCHITEKTUR.md`](../docs/ARCHITEKTUR.md), Abschnitt 4.
Adresse: **https://q0ochen.github.io/DrinkWithMe/** (GitHub Pages, kostenlos).

- **Bar**: große Icon-Knöpfe, ein Tipp = jetzt getrunken, 10 s „Rückgängig“, optional laute Bestätigung,
  Spracheingabe („Sprechen“), wenn der Browser `SpeechRecognition` anbietet. Bar bearbeiten, eigene Getränke.
- **Verlauf**: Kennzahlen (heute, alkoholfreie Tage in Folge, Vergleich mit den 30 Tagen davor, Durchschnitt),
  SVG-Diagramme mit denselben Bezugslinien (Zucker 50 g/25 g) und -bändern (Alkohol DGE 2024, ausblendbar, kein
  Grün), alle Werte zusätzlich als Tabelle, Liste der Einträge, Hinweistext und **„Als Bild teilen“** (PNG,
  Web Share API, sonst Herunterladen).
- **Freunde**: Google-Anmeldung (Supabase, PKCE), Feed, Anfragen, Freundesliste, Einladungs-Link, „Per E-Mail
  hinzufügen“ (neutrale Antwort). **Ereignis**-Seite mit großem Satz und Knopf **„Vorlesen“** (`speechSynthesis`, de-DE).
- **Einstellungen**: Konto (abmelden, Konto löschen), Zustimmung zum Teilen alkoholischer Getränke,
  Benachrichtigungen (Web Push), Ansagen, Info, Lizenzen.

Ohne Anmeldung und ohne Server funktionieren Bar, Verlauf, Statistik, Sprache und Vorlesen vollständig.
Bar, vollständige Historie, Einstellungen und die Anmeldesitzung liegen **nur in IndexedDB** auf dem Gerät.

## Aufbau

```
web/
├── src/core/        Port des Kotlin-Kerns (android/core) nach TypeScript – gleiche Regeln, Texte und Werte
│   └── golden/      Golden-Tests: Fälle der Kotlin-Tests, Drift-Test gegen den Kotlin-Quelltext und
│                    vom Kotlin-Kern erzeugte Ausgaben (kotlin-golden.json, siehe tools/kotlin-golden)
├── src/data/        IndexedDB (idb): Bar, Historie, Einstellungen, Sitzungsspeicher für supabase-js
├── src/social/      Supabase-Adapter (Auth/PKCE, Freunde, Feed, Ereignisse, Web Push, Konto löschen) – lazy geladen
├── src/platform/    Browser-Schnittstellen: Vorlesen, Spracheingabe, Teilen, Plattform-Erkennung
├── src/app/         Konfiguration, Router, Kompositionswurzel (container.ts)
├── src/ui/          Preact-Oberfläche (Bildschirme, Diagramme, Bild zum Teilen, CSS)
├── src/sw/          Service Worker (Offline-Start, Push nur bei passender recipientId, Klick → /ereignis/<id>)
├── scripts/         Icon-Erzeugung (Playwright/Chromium), Vite-Plugins (404.html, CSP, Service Worker)
├── e2e/             Playwright-Smoke-Tests (Supabase per page.route abgefangen)
├── tools/           kotlin-golden: erzeugt die Golden-Daten mit dem echten Kotlin-Kern
└── public/          manifest.webmanifest; icons/ wird beim Build aus design/icons erzeugt
```

- Kleine Schnittstellen (Repository-, Dienst- und Browser-Ports), eine Kompositionswurzel (`src/main.tsx` →
  `createAppServices`). Teilen läuft wie in Android über einen Decorator um die Historie
  (`SharingHistoryRepository`): lokal speichern wartet nie auf das Netz.
- Geteilte Ereignisse: `drink_events` nur mit `id, name, icon, category, volume_ml, consumed_at`,
  `Prefer: resolution=ignore-duplicates`, danach Edge Function `notify-friends` – genau wie die Android-App.
- Laufzeit-Abhängigkeiten (alle frei, siehe „Lizenzen“ in den Einstellungen): `preact` (MIT),
  `@supabase/supabase-js` (MIT), `idb` (ISC). Keine Analytics, keine Fremd-Schriften, keine Profilbilder von
  fremden Servern (Anfangsbuchstaben statt Google-Bild). Content-Security-Policy im Build.

## Entwickeln

Voraussetzung: Node.js 22.

```bash
cd web
npm ci
npx playwright install chromium    # einmalig: für die PNG-Symbole und die Smoke-Tests
npm run dev                        # http://127.0.0.1:5173/DrinkWithMe/ (bzw. Basis aus VITE_WEB_APP_URL)
```

| Befehl | Zweck |
|---|---|
| `npm run typecheck` | TypeScript prüfen |
| `npm run lint` | ESLint |
| `npm test` | Unit-Tests (Vitest): Kern-Port + Golden-Tests, IndexedDB (fake-indexeddb), Supabase-Adapter (Fetch-Attrappe), Router, Service-Worker-Logik |
| `npm run build` | Produktions-Build nach `dist/` (inkl. `404.html`, `sw.js`, Symbole) |
| `npm run test:e2e` | Playwright-Smoke-Tests gegen `vite preview` (zwei Test-Builds: mit und ohne Server) |
| `SCREENSHOT_DIR=/pfad npm run test:e2e -- screenshots` | Bildschirmfotos aller Bereiche (Handy 390 × 844 und Desktop) |
| `npm run icons -- --force` | Symbole neu erzeugen |

## Konfiguration

Alle Werte sind **öffentlich** (keine Geheimnisse) und werden beim Build eingesetzt:

| Vite-Variable | GitHub-Repository-Variable | Bedeutung |
|---|---|---|
| `VITE_SUPABASE_URL` | `SUPABASE_URL` | `https://<projekt-ref>.supabase.co` |
| `VITE_SUPABASE_ANON_KEY` | `SUPABASE_ANON_KEY` | Publishable/Anon-Key |
| `VITE_VAPID_PUBLIC_KEY` | `VAPID_PUBLIC_KEY` | derselbe öffentliche VAPID-Schlüssel wie in Supabase und Android |
| `VITE_WEB_APP_URL` | `WEB_APP_URL` (optional) | Standard `https://q0ochen.github.io/DrinkWithMe`; bestimmt auch die Vite-Basis (`/DrinkWithMe/`) |

Lokal: `.env.example` als `.env.local` kopieren. Fehlt die Supabase-Konfiguration, zeigt „Freunde“
**„Server noch nicht eingerichtet“** – alles andere funktioniert.

### In Supabase einzutragen (Authentication → URL Configuration)

- **Redirect URL** (muss exakt passen): `https://q0ochen.github.io/DrinkWithMe/auth/callback`
- **Site URL**: `https://q0ochen.github.io/DrinkWithMe`
- Lokal (supabase/config.toml): `http://127.0.0.1:5173/auth/callback` – dazu `VITE_WEB_APP_URL=http://127.0.0.1:5173`
  setzen (dann ist die Basis `/`).

## Routing auf GitHub Pages

History-Routing unter der Basis `/DrinkWithMe/`. GitHub Pages kennt keine SPA-Umleitung und liefert für unbekannte
Pfade die Datei `404.html` aus – sie ist eine exakte Kopie von `index.html` (Vite-Plugin `spaFallbackPlugin`). So
funktionieren `…/auth/callback?code=…`, `…/einladung/<token>` und `…/ereignis/<eventId>` direkt (HTTP-Status 404,
Inhalt = App). Ist der Service Worker installiert, beantwortet er Seitenaufrufe zuerst aus dem Netz und offline aus
der gespeicherten `index.html`.

## Web Push

- Einschalten nur per Tipp in den Einstellungen (Berechtigungsabfrage), dann `pushManager.subscribe` mit
  `applicationServerKey = VITE_VAPID_PUBLIC_KEY`; gespeichert in `push_subscriptions` (`platform = 'web'`;
  zum Erneuern löschen und neu anlegen – kein UPDATE).
- Die Seite legt die ID des angemeldeten Nutzers in einer eigenen kleinen IndexedDB ab (`drink-with-me-push`); der
  Service Worker zeigt eine Nachricht **nur** an, wenn `recipientId` passt, und bestellt verwaiste Abos ab.
- Tipp auf die Nachricht öffnet `/ereignis/<eventId>`; dort liest „Vorlesen“ den Satz vor.
- iPhone: Push nur nach „Zum Home-Bildschirm“ (iOS 16.4+); die App zeigt dazu einen Hinweis.

## Deploy

Workflow [`.github/workflows/web.yml`](../.github/workflows/web.yml): bei jedem Push auf `web/**` oder
`design/icons/**` Typprüfung, Lint, Unit-Tests, Build, Playwright-Smoke-Tests und Artefakt `DrinkWithMe-web`;
auf `main` zusätzlich Veröffentlichung mit `actions/deploy-pages`. Einmalig: Settings → Pages → Source
„GitHub Actions“.

## Lizenz

Code und Grafiken: CC0 1.0 (siehe [`LICENSE`](../LICENSE) und [`design/icons/LICENSE`](../design/icons/LICENSE)).
