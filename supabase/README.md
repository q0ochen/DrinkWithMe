# Supabase-Backend von Drink With Me

Das Backend ist nur für die **Vernetzung** da (Freunde, Benachrichtigungen). Bar, Trinkhistorie und Statistik
liegen ausschließlich auf dem Gerät. Der Server kennt nur Profil (Name, Bild), Freundschaften, Push-Abos und
**kurzlebige** geteilte Ereignisse (Name, Icon, Kategorie, Menge in ml, Zeitpunkt – nach 30 Tagen gelöscht).
Gramm Alkohol/Zucker und Statistiken werden nie übertragen.

Verbindlicher Vertrag: [`docs/ARCHITEKTUR.md`](../docs/ARCHITEKTUR.md), Abschnitt 2. Kosten: **Supabase Free**,
ohne Kreditkarte.

## Inhalt

| Pfad | Inhalt |
|---|---|
| `config.toml` | Konfiguration für die Supabase CLI (lokal und CI): nur Google-Anmeldung, keine E-Mail-/anonyme Anmeldung |
| `migrations/` | Datenbank: Tabellen, Constraints, Indizes, Row Level Security, Trigger, RPCs, tägliches Aufräumen (pg_cron) |
| `tests/` | pgTAP-Tests (`supabase test db`): RLS-Matrix für jede Tabelle, alle RPCs, Limits, Trigger, Konto löschen |
| `functions/notify-friends/` | Edge Function: Freunde per Web Push (RFC 8291 + VAPID/RFC 8292) benachrichtigen |
| `functions/delete-account/` | Edge Function: eigenes Konto löschen (alles andere folgt per `ON DELETE CASCADE`) |
| `functions/_shared/` | Gemeinsamer Code: Web-Push-Verschlüsselung, VAPID, Supabase-Zugriffe, HTTP-Hilfen |
| `functions/tests/` | Deno-Tests (u. a. gegen die Beispielwerte aus RFC 8291) |
| `scripts/vapid-schluessel-erzeugen.ts` | Erzeugt kostenlos ein VAPID-Schlüsselpaar |

Die Edge Functions kommen **ohne Fremdpakete** aus (nur Web Crypto und `fetch`); nur die Tests nutzen
`jsr:@std/assert` (MIT-Lizenz, Version fest in `functions/deno.json` und `functions/deno.lock`).

## Lokal entwickeln und testen

Voraussetzungen: [Supabase CLI](https://supabase.com/docs/guides/local-development/cli/getting-started)
(getestet mit 2.122.1), Docker, [Deno](https://deno.com) 2.

```bash
# im Hauptverzeichnis des Repositorys
supabase start          # startet Postgres, Auth, REST usw. in Docker und wendet die Migrationen an
supabase db reset       # Datenbank leeren und alle Migrationen neu anwenden
supabase test db        # pgTAP-Tests aus supabase/tests

cd supabase/functions
deno test               # Unit-Tests der Edge Functions (ohne Netzwerk)
deno check notify-friends/index.ts delete-account/index.ts
deno lint . ../scripts && deno fmt --check . ../scripts
```

Für die Google-Anmeldung lokal: in `supabase/.env` (nicht eingecheckt) `SUPABASE_AUTH_EXTERNAL_GOOGLE_CLIENT_ID`
und `SUPABASE_AUTH_EXTERNAL_GOOGLE_SECRET` setzen; im Google-Client zusätzlich die lokale Redirect-URL
`http://127.0.0.1:54321/auth/v1/callback` eintragen. Ohne diese Werte startet alles trotzdem, nur die
Google-Anmeldung funktioniert lokal nicht.

Edge Functions lokal ausprobieren: `supabase functions serve` (VAPID-Werte dafür in `supabase/functions/.env`).

Die CI (`.github/workflows/supabase.yml`) macht dasselbe bei jedem Push auf `supabase/**`.

## Einrichtung des echten Projekts (einmalig)

### 1. Projekt anlegen

1. Auf [supabase.com](https://supabase.com) kostenlos registrieren (keine Kreditkarte nötig).
2. **New project** → Name z. B. `drink-with-me`, Region **Frankfurt (eu-central-1)**, Tarif **Free**.
   Ein sicheres **Datenbank-Passwort** vergeben und im Passwort-Manager speichern (wird für das Deployment
   gebraucht).
3. Die **Projekt-Referenz** ist der Teil vor `.supabase.co` in der Projekt-URL
   (Project Settings → General → Project ID), z. B. `abcdefghijklmnopqrst`.

### 2. Google-Anmeldung

1. In der [Google Cloud Console](https://console.cloud.google.com/) ein Projekt anlegen (kostenlos).
2. **APIs & Dienste → OAuth-Zustimmungsbildschirm**: Typ „Extern“, App-Name „Drink With Me“, Scopes nur
   `openid`, `email`, `profile`. Danach **„In Produktion“** veröffentlichen (für diese Scopes ist keine
   Google-Prüfung nötig).
3. **APIs & Dienste → Anmeldedaten → OAuth-Client-ID erstellen**, Typ **Webanwendung**.
   Autorisierte Weiterleitungs-URI: `https://<projekt-ref>.supabase.co/auth/v1/callback`.
4. Im Supabase-Dashboard unter **Authentication → Sign In / Providers**:
   - **Google** aktivieren, Client-ID und Client-Secret eintragen.
   - **Email** deaktivieren (keine Registrierung per E-Mail/Passwort).
   - **Allow anonymous sign-ins** aus.
   - Phone aus.

   Das ist wichtig: Freundschaftsanfragen per E-Mail finden nur bestätigte Google-Adressen.
5. Unter **Authentication → URL Configuration**:
   - **Site URL**: `<WEB_APP_URL>` (Adresse der Web-App, z. B. `https://<name>.github.io/Drink-With-Me`)
   - **Redirect URLs**: `drinkwithme://auth-callback` (Android) und `<WEB_APP_URL>/auth/callback` (Web-App)

`config.toml` enthält für die lokale Entwicklung dieselben Einstellungen mit Platzhaltern
(`http://127.0.0.1:5173` statt `<WEB_APP_URL>`). Bitte **kein** `supabase config push` ausführen, sonst würden die
lokalen Platzhalter ins echte Projekt übernommen.

### 3. VAPID-Schlüssel für Benachrichtigungen (kostenlos, lokal erzeugt)

```bash
deno run supabase/scripts/vapid-schluessel-erzeugen.ts
```

Das Skript braucht keine Berechtigungen und gibt `VAPID_PUBLIC_KEY` und `VAPID_PRIVATE_KEY` aus (P-256,
base64url). Dann die Secrets der Edge Functions setzen – entweder im Dashboard unter
**Edge Functions → Secrets** oder per CLI:

```bash
supabase login
supabase secrets set --project-ref <projekt-ref> \
  VAPID_PUBLIC_KEY=<öffentlicher Schlüssel> \
  VAPID_PRIVATE_KEY=<privater Schlüssel> \
  VAPID_SUBJECT=mailto:<deine-adresse>
```

- `VAPID_PRIVATE_KEY` ist **geheim**: nie einchecken, nie in Apps eintragen.
- `VAPID_PUBLIC_KEY` ist öffentlich: zusätzlich in `android/app/supabase.properties` (`vapidPublicKey`) und in der
  Web-App eintragen. Er muss überall derselbe sein.
- Wer den Schlüssel später wechselt, macht alle bestehenden Push-Abos ungültig (die Apps melden sich neu an).

`SUPABASE_URL` und `SUPABASE_SERVICE_ROLE_KEY` (bzw. `SUPABASE_SECRET_KEYS`) setzt Supabase in den Edge Functions
automatisch.

### 4. Deployment über GitHub Actions

Im GitHub-Repository unter **Settings → Secrets and variables → Actions**:

| Art | Name | Wert |
|---|---|---|
| Secret | `SUPABASE_ACCESS_TOKEN` | Supabase-Dashboard → Account → **Access Tokens** → Generate new token |
| Secret | `SUPABASE_DB_PASSWORD` | Datenbank-Passwort aus Schritt 1 |
| Variable | `SUPABASE_PROJECT_REF` | Projekt-Referenz aus Schritt 1 |

Dann **Actions → „Supabase Deploy“ → Run workflow**. Der Workflow bricht mit einer deutschen Fehlermeldung ab,
wenn etwas fehlt, und führt sonst aus:

```bash
supabase link --project-ref "$SUPABASE_PROJECT_REF"
supabase db push                                   # nur noch nicht angewendete Migrationen
supabase functions deploy notify-friends delete-account
```

Am Ende prüft er, ob die VAPID-Secrets gesetzt sind (nur Hinweis, kein Abbruch).

### 5. Werte für die Apps

Öffentlich und unkritisch (dürfen in die App-Builds): Projekt-URL `https://<projekt-ref>.supabase.co`, der
**Publishable/Anon-Key** (Project Settings → API Keys) und `VAPID_PUBLIC_KEY`. Für Android in
`android/app/supabase.properties`: `supabaseUrl`, `supabaseAnonKey`, `vapidPublicKey`, `webAppUrl`.

**Niemals** in Apps oder ins Repository: Service-Role-/Secret-Key, Datenbank-Passwort, `VAPID_PRIVATE_KEY`,
Google-Client-Secret.

## Schnittstellen für die Apps

Alle Aufrufe mit `apikey: <Anon-Key>` und `Authorization: Bearer <Nutzer-JWT>`. Fehler der RPCs kommen als
PostgREST-Fehler (HTTP 400, `code: "P0001"`) mit maschinenlesbarer `message`; die Apps zeigen dazu deutsche Texte.

### Tabellen (REST `/rest/v1/<tabelle>`)

| Tabelle | Erlaubt für angemeldete Nutzer |
|---|---|
| `profiles` | Lesen: sich selbst, Freunde, Personen mit offener Anfrage (beide Richtungen). Ändern: nur eigene Zeile, nur `display_name` (1–50 Zeichen) und `avatar_url` (https, ≤ 2048). Keine E-Mail-Adressen. |
| `friendships` | Lesen der eigenen Freundschaften (`user_a < user_b`). Schreiben nur per RPC. |
| `friend_requests` | Lesen (Absender oder Empfänger), Löschen = Zurückziehen (nur Absender). |
| `drink_events` | Anlegen: nur `id, name, icon, category, volume_ml, consumed_at` (`author_id` kommt aus dem JWT, `consumed_at` zwischen jetzt − 24 h und jetzt + 5 min). Lesen: eigene und die der Freunde. Löschen: nur eigene. **Kein** Ändern. Wiederholtes Senden idempotent mit `Prefer: resolution=ignore-duplicates` (`ON CONFLICT DO NOTHING`). |
| `push_subscriptions` | Lesen/Anlegen/Löschen nur eigener Abos: `endpoint` (https), `p256dh`, `auth` (base64url), `platform` (`unifiedpush` oder `web`). Höchstens 5 pro Nutzer (sonst Fehler `too_many_subscriptions`). Kein Ändern: zum Erneuern löschen und neu anlegen. |
| `invite_links`, `rate_limits` | Kein Zugriff (nur über RPCs bzw. Server). |

### RPCs (`POST /rest/v1/rpc/<name>`)

| Funktion | Parameter (JSON) | Ergebnis / Fehler |
|---|---|---|
| `send_friend_request` | `{ "email": "…" }` | Immer gleiche, leere Antwort (auch bei unbekannter Adresse). Gibt es eine Gegenanfrage, sind beide sofort befreundet. Fehler `rate_limited` ab dem 21. Aufruf am Tag. |
| `respond_friend_request` | `{ "request_id": "…", "accept": true }` | Nur der Empfänger. Fehler `request_not_found`. |
| `create_invite_link` | `{}` | Token (32 Zeichen base64url, 192 bit, 14 Tage gültig); ein alter Link verfällt. Die App baut daraus `<WEB_APP_URL>/einladung/<token>`. |
| `accept_invite` | `{ "token": "…" }` | Profil des Einladenden (`id`, `display_name`, `avatar_url`, `created_at`); sofort befreundet. Fehler `invite_invalid` (unbekannt/abgelaufen), `invite_own`, `rate_limited` (mehr als 30 erfolgreiche Aufrufe am Tag). |
| `remove_friend` | `{ "friend_id": "…" }` | Leere Antwort, idempotent. |

Ohne gültige Anmeldung: HTTP 401/403 bzw. Fehler `not_authenticated`.

### Edge Functions (`POST /functions/v1/<name>`)

| Funktion | Anfrage | Antworten |
|---|---|---|
| `notify-friends` | `{ "eventId": "<uuid>" }` direkt nach dem Anlegen des Ereignisses | `200 { recipients, delivered, failed, removed }` · `400 invalid_request` · `401 unauthorized` · `404 event_not_found` (unbekannt, fremd, älter als 10 min oder schon benachrichtigt) · `429 rate_limited` (mehr als 60 pro Stunde) · `500 server_misconfigured`/`internal_error` |
| `delete-account` | leer | `200 { deleted: true }` (auch wenn das Konto schon gelöscht war) · `401 unauthorized` · `500 …` |

Push-Nachricht (verschlüsselt nach RFC 8291, `Content-Encoding: aes128gcm`, VAPID, TTL 1 Tag), Inhalt JSON:

```json
{ "type": "drink", "eventId": "…", "authorId": "…", "recipientId": "…",
  "title": "Anna trinkt gerade", "body": "Apfelschorle (500 ml)" }
```

Die Apps zeigen eine Nachricht nur an, wenn `recipientId` dem angemeldeten Nutzer entspricht. Antwortet ein
Push-Dienst mit 404/410, wird das Abo gelöscht.

## Sicherheit und Datenschutz

- Row Level Security auf **jeder** Tabelle; `anon` hat keinerlei Rechte; Rechte sind teils spaltenweise vergeben
  (z. B. darf niemand außer dem Server `created_at`/`notified_at` setzen).
- Alle RPCs sind `SECURITY DEFINER` mit `search_path = ''` und vollständig qualifizierten Namen, ausführbar nur
  für `authenticated`. Interne Hilfsfunktionen liegen im Schema `private`, das die Data API nicht ausliefert.
- Aufruflimits in `rate_limits` (festes Zeitfenster): 20 E-Mail-Anfragen/Tag, 30 Einladungen/Tag,
  60 Benachrichtigungen/Stunde. Fehlversuche bei `accept_invite` zählen nicht (die Datenbank-Transaktion wird
  zurückgerollt); Erraten eines 192-bit-Tokens ist ohnehin aussichtslos.
- pg_cron löscht täglich um 03:17 UTC geteilte Ereignisse älter als 30 Tage, abgelaufene Einladungs-Links und
  veraltete Limit-Zähler.
- Konto löschen entfernt den Auth-Nutzer; Profil, Freundschaften, Anfragen, Links, Ereignisse, Push-Abos und
  Zähler folgen per `ON DELETE CASCADE`.
- Die Edge Functions prüfen das Nutzer-JWT selbst beim Auth-Server (`/auth/v1/user`); die Gateway-Prüfung
  (`verify_jwt`) ist deshalb aus, damit es auch mit den neuen JWT-Signaturschlüsseln funktioniert.
- `notify-friends` schickt nur an öffentliche https-Endpoints (keine IP-Adressen, kein `localhost`) und
  protokolliert nie vollständige Endpoints oder Schlüssel.
