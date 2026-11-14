# Drink With Me – Web-App (PWA)

Die Web-App bringt Drink With Me aufs **iPhone** (Safari, ab iOS 16.4 über „Zum Home-Bildschirm“) und in jeden
Browser auf Desktop und Android. Vertrag: [`docs/ARCHITEKTUR.md`](../docs/ARCHITEKTUR.md), Abschnitt 4.
Adresse: **https://q0ochen.github.io/DrinkWithMe/** (GitHub Pages, kostenlos).

- **Bar**: große Icon-Knöpfe, ein Tipp = jetzt getrunken, 10 s „Rückgängig“, optional laute Bestätigung,
  Spracheingabe („Sprechen“), wenn der Browser `SpeechRecognition` anbietet. Bar bearbeiten, eigene Getränke.
  „⋮“ an jeder Kachel (auch Rechtsklick/langes Drücken): **„Privat eintragen (nicht teilen)“** und „Bar bearbeiten“
  – wie das Menü in der Android-App. Angemeldet zeigt die Bestätigung „Mit deinen Freunden geteilt.“ bzw. „Nicht geteilt.“
- **Verlauf**: Kennzahlen (heute, alkoholfreie Tage in Folge, Vergleich mit den 30 Tagen davor, Durchschnitt),
  SVG-Diagramme mit denselben Bezugslinien (Zucker 50 g/25 g) und -bändern (Alkohol DGE 2024, ausblendbar, kein
  Grün), alle Werte zusätzlich als Tabelle, Liste der Einträge, Hinweistext und **„Als Bild teilen“** (PNG,
  Web Share API, sonst Herunterladen).
- **Freunde**: Google-Anmeldung (Supabase, PKCE), Feed, Anfragen, Freundesliste, Einladungs-Link, „Per E-Mail
  hinzufügen“ (neutrale Antwort; eigene Anfragen erscheinen nur als „Anfrage an b***@example.org“ – nie mit Name oder
  Bild, siehe „Aufbau“). **Ereignis**-Seite mit großem Satz und Knopf **„Vorlesen“** (`speechSynthesis`, de-DE).
- **Einstellungen**: Konto (abmelden, Konto löschen), Zustimmung zum Teilen alkoholischer Getränke,
  Benachrichtigungen (Web Push), Ansagen, **Sicherung** von Bar und Verlauf (Datei speichern/einlesen), Info, Lizenzen.

Ohne Anmeldung und ohne Server funktionieren Bar, Verlauf, Statistik, Sprache und Vorlesen vollständig.
Bar, vollständige Historie, Einstellungen und die Anmeldesitzung liegen **nur in IndexedDB** auf dem Gerät. Nach dem
Einrichten der Bar bittet die App einmal um dauerhaften Speicher (`navigator.storage.persist()`).

## Aufbau

```
web/
├── src/core/        Port des Kotlin-Kerns (android/core) nach TypeScript – gleiche Regeln, Texte und Werte
│   └── golden/      Golden-Tests: Fälle der Kotlin-Tests, Drift-Test gegen den Kotlin-Quelltext und
│                    vom Kotlin-Kern erzeugte Ausgaben (kotlin-golden.json, siehe tools/kotlin-golden)
├── src/data/        IndexedDB (idb): Bar, Historie, Einstellungen, Sitzungsspeicher für supabase-js; Sicherung (backup.ts)
├── src/social/      Supabase-Adapter (Auth/PKCE, Freunde, Feed, Ereignisse, Web Push, Konto löschen) – lazy geladen
├── src/platform/    Browser-Schnittstellen: Vorlesen, Spracheingabe, Teilen, Plattform-Erkennung, dauerhafter Speicher
├── src/app/         Konfiguration, Router, Kompositionswurzel (container.ts), Nachholen geteilter Einträge (pendingShares.ts)
├── src/ui/          Preact-Oberfläche (Bildschirme, Diagramme, Bild zum Teilen, CSS)
├── src/sw/          Service Worker (Offline-Start, Push nur bei passender recipientId, abbestellen nur nach
│                    ausdrücklicher Abmeldung, Klick → /ereignis/<id>)
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
  Ohne gültige Sitzung wird nichts gesendet (sonst ginge die Anfrage mit dem Anon-Key raus).
- Freundschaftsanfragen: eingehende aus `friend_requests` (nur der Empfänger darf lesen, Spalten
  `id, from_user, created_at`) mit dem Profil des Absenders; **eigene** Anfragen nur per RPC
  `list_outgoing_friend_requests` (`id, email_hint, created_at`) – angezeigt als „Anfrage an b***@example.org“, nie
  mit Name oder Bild, damit niemand per E-Mail-Adresse herausfinden kann, ob und wem ein Konto gehört. Zurückziehen
  per RPC `cancel_friend_request`.
- Fehler `too_many_events` (Server: höchstens 200 geteilte Getränke pro Person in 24 Stunden): „Heute wurden schon
  sehr viele Getränke geteilt. Weitere Einträge bleiben vorerst nur auf diesem Gerät.“ – wird in der Bar angezeigt
  und nicht wiederholt.
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

## Anmeldung: vorübergehende Fehler sind keine Abmeldung

Scheitert das Erneuern der Sitzung nur vorübergehend (offline, Supabase antwortet 5xx – supabase-js meldet
`AuthRetryableFetchError` und behält die Sitzung im Speicher), bleibt der zuletzt bekannte Nutzer angemeldet
(`SupabaseAuthService` liest ihn aus der gespeicherten Sitzung); supabase-js erneuert die Sitzung später selbst.
Abgemeldet ist nur, wer sich abmeldet, wer sein Konto löscht oder wessen Sitzung der Server ablehnt (`SIGNED_OUT`).
Nur dann werden der gespeicherte Nutzer und die Empfänger-ID des Service Workers gelöscht. Beim Start lädt die App
die Vernetzung, sobald ein Nutzer **oder** eine Sitzung gespeichert ist – so werden neue Getränke auch dann geteilt,
wenn ältere Versionen den gespeicherten Nutzer fälschlich gelöscht hatten.

## Teilen ohne Verbindung

Wie `PendingShareQueue` in der Android-App (`src/app/pendingShares.ts`): Scheitert das Teilen oder Zurückziehen
eines Eintrags an der Verbindung (`SocialError` `network`), wird der Auftrag (nur Art und Eintrags-ID) in IndexedDB
vorgemerkt (höchstens 200, ohne Doppelte; ein Zurückziehen ersetzt ein wartendes Teilen desselben Eintrags).
Nachgeholt wird beim App-Start (angemeldet), beim Browser-Ereignis `online` und wenn die App wieder sichtbar wird.
Geteilt wird dabei nur, was es lokal noch gibt, laut Teilen-Regel noch geteilt werden soll und jünger als 23 Stunden
ist (der Server nimmt nur die letzten 24 Stunden an); abgemeldet entfallen alle Aufträge; vom Server abgelehnte
Aufträge werden nicht wiederholt.

## Sicherung (Export/Import)

Einstellungen → „Bar und Verlauf“ → **„Sicherung speichern“** erzeugt eine JSON-Datei
`drink-with-me-sicherung-JJJJ-MM-TT.json` (auf dem iPhone über „Teilen“ → „In Dateien sichern“, sonst Download):

```json
{ "format": "drink-with-me-backup", "version": 1, "exportedAt": "2026-11-10T18:00:00.000Z",
  "bar": [ { "id", "name", "icon", "category", "servingMilliliters", "alcoholPercent", "sugarGramsPer100Ml",
             "aliases", "valueSource", "sortOrder" } ],
  "history": [ { "id", "barItemId", "name", "icon", "category", "volumeMilliliters", "alcoholPercent",
                 "sugarGramsPer100Ml", "consumedAt", "shareWithFriends" } ] }
```

**„Sicherung einlesen“** (auch schon beim ersten Start, statt die Bar neu einzurichten) prüft jede Zeile mit
denselben Regeln wie beim Eintragen (Name 1–40 Zeichen, 1–2000 ml, 0–80 Vol.-%, 0–100 g Zucker, Zeitpunkt nicht
mehr als einen Tag in der Zukunft; unbekannte Icons/Kategorien werden „Sonstiges“) und überspringt Ungültiges und
Doppeltes. Zusammengeführt wird, ohne etwas zu löschen: neue Einträge kommen dazu, vorhandene (gleiche ID) bleiben
unverändert; die Bar übernimmt Reihenfolge und Werte aus der Datei, Getränke, die nur auf diesem Gerät sind, bleiben
hinten erhalten. Eingelesene Einträge werden nie (erneut) mit Freunden geteilt. Die Datei verlässt das Gerät nur,
wenn der Nutzer sie selbst weitergibt.

## iPhone: Safari-Tab und Home-Bildschirm

Die App auf dem Home-Bildschirm hat auf dem iPhone einen **eigenen Speicher** – Bar und Verlauf aus dem Safari-Tab
werden nicht übernommen. Deshalb rät der erste Start im Safari-Tab, die App zuerst auf den Home-Bildschirm zu legen
und die Bar dort einzurichten; der Hinweis „Zum Home-Bildschirm hinzufügen“ (Freunde, Benachrichtigungen) sagt
dazu, dass die Daten nicht mitwandern, und verweist auf die Sicherung. Safari kann Websitedaten auch von selbst
löschen, wenn die Seite längere Zeit nicht geöffnet wurde – die Einstellungen raten deshalb zu regelmäßigen Sicherungen.

## Web Push

- Einschalten nur per Tipp in den Einstellungen (Berechtigungsabfrage), dann `pushManager.subscribe` mit
  `applicationServerKey = VITE_VAPID_PUBLIC_KEY`; gespeichert in `push_subscriptions` (`platform = 'web'`;
  zum Erneuern löschen und neu anlegen – kein UPDATE).
- Die Seite legt die ID des angemeldeten Nutzers in einer eigenen kleinen IndexedDB ab (`drink-with-me-push`); der
  Service Worker zeigt eine Nachricht **nur** an, wenn `recipientId` passt. Nach einer **ausdrücklichen** Abmeldung
  steht dort eine Markierung „abgemeldet“ – nur dann bestellt der Service Worker das Abo ab. Fehlt die ID nur oder ist
  sie nicht lesbar, zeigt er nichts an, behält das Abo aber.
- Gilt als „eingeschaltet“ nur mit Browser-Abo **und** Zeile in `push_subscriptions` (die Einstellungen prüfen das;
  offline zählt das Browser-Abo). Scheitert das Speichern auf dem Server (z. B. `too_many_subscriptions`), wird ein
  dabei neu angelegtes Browser-Abo wieder abbestellt.
- Beim Start gleicht die App das Abo mit dem Server ab; hat der Nutzer Benachrichtigungen eingeschaltet
  (`pushWanted`) und fehlt das Browser-Abo, wird ohne erneute Abfrage neu abonniert (Berechtigung schon erteilt).
  Abmelden und Konto löschen schalten `pushWanted` aus.
- Tipp auf die Nachricht öffnet `/ereignis/<eventId>`; dort liest „Vorlesen“ den Satz vor.
- iPhone: Push nur nach „Zum Home-Bildschirm“ (iOS 16.4+); die App zeigt dazu einen Hinweis.

## Deploy

Workflow [`.github/workflows/web.yml`](../.github/workflows/web.yml): bei jedem Push auf `web/**` oder
`design/icons/**` Typprüfung, Lint, Unit-Tests, Build, Playwright-Smoke-Tests und Artefakt `DrinkWithMe-web`;
auf `main` zusätzlich Veröffentlichung mit `actions/deploy-pages` – bei einem Push **und** bei einem manuellen Start
(Actions → „Web-App“ → „Run workflow“, Branch `main`), z. B. nachdem die Repository-Variablen gesetzt wurden.
Einmalig: Settings → Pages → Source „GitHub Actions“.

## Lizenz

Code und Grafiken: CC0 1.0 (siehe [`LICENSE`](../LICENSE) und [`design/icons/LICENSE`](../design/icons/LICENSE)).
