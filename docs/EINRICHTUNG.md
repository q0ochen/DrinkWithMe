# Drink With Me einrichten – Schritt für Schritt

Diese Anleitung bringt den Server (Supabase), die Google-Anmeldung, die Web-App fürs iPhone und die Android-App
zum Laufen – **kostenlos und ohne Kreditkarte**. Sie ist für Menschen geschrieben, die **nicht programmieren**.
Du musst nichts installieren: Fast alles passiert im Browser (Supabase, Google Cloud, GitHub). Das eigentliche
„Hochladen“ des Servers und den Bau der Apps erledigt GitHub Actions für dich.

> **Gut zu wissen:** Bar, Verlauf, Statistik, Sprache und Vorlesen funktionieren **ohne** diese Einrichtung.
> Die Einrichtung braucht ihr nur für den sozialen Teil: Freunde, geteilte Getränke und Benachrichtigungen.

---

## Inhalt

0. [Bevor es losgeht](#0-bevor-es-losgeht)
1. [Supabase: Konto und Projekt](#1-supabase-konto-und-projekt-computer-empfohlen)
2. [Google Cloud: Zustimmungsbildschirm und OAuth-Client](#2-google-cloud-zustimmungsbildschirm-und-oauth-client-computer-empfohlen)
3. [Supabase: Anmeldung einstellen](#3-supabase-anmeldung-einstellen-computer-empfohlen)
4. [Schlüssel für Benachrichtigungen (VAPID)](#4-schlüssel-für-benachrichtigungen-vapid-computer-nötig)
5. [GitHub: Secrets, Variablen und GitHub Pages](#5-github-secrets-variablen-und-github-pages-computer-empfohlen)
6. [Android-Konfiguration ins Repository bringen](#6-android-konfiguration-ins-repository-bringen-handy-geht)
7. [Pull Request zusammenführen und Server bereitstellen](#7-pull-request-zusammenführen-und-server-bereitstellen-handy-geht)
8. [Android-App installieren](#8-android-app-installieren-android-handy)
9. [ntfy für sofortige Benachrichtigungen (Android)](#9-ntfy-für-sofortige-benachrichtigungen-android)
10. [iPhone: Web-App einrichten](#10-iphone-web-app-einrichten)
11. [Fire TV Stick 4K Max](#11-fire-tv-stick-4k-max)
12. [Testplan für zwei Personen](#12-testplan-für-zwei-personen)
13. [Wenn etwas nicht klappt](#13-wenn-etwas-nicht-klappt)
14. [Anhang: Kosten, Grenzen, Sicherheit, Konto auf Wunsch löschen](#14-anhang)

---

## 0. Bevor es losgeht

### Was du brauchst

- [ ] Ein **Google-Konto** (am besten ein privates `@gmail.com`-Konto; Firmen- oder Schulkonten sperren oft
      das Anlegen von OAuth-Clients).
- [ ] Ein **GitHub-Konto** mit **Admin-Rechten** am Repository `q0ochen/DrinkWithMe` – nur dann siehst du den
      Reiter **Settings** und darfst Secrets anlegen.
- [ ] Einen **Passwort-Manager** (oder ein sicheres Notizbuch) für die geheimen Werte unterwegs.
- [ ] Etwa **eine bis zwei Stunden** Zeit.
- [ ] Zum Testen: zwei Personen, z. B. ein **Android-Handy** und ein **iPhone** (iOS 16.4 oder neuer).

**Keine Kreditkarte:** Supabase Free, Google Cloud (nur Anmeldung, kein Abrechnungskonto), GitHub (öffentliches
Repository) und ntfy sind kostenlos. Fragt dich Google nach einem Rechnungskonto oder einer „kostenlosen
Testversion“: **ablehnen bzw. überspringen** – das wird nicht gebraucht.

### Wo brauchst du einen Computer?

Die Konsolen von Supabase und Google sind für große Bildschirme gebaut. Am Handy geht vieles in der
„Desktop-Ansicht“ des Browsers (Safari: „aA“ → **Desktop-Website anfordern**; Chrome: Menü → **Desktopwebsite**),
ist aber fummelig. Ein geliehener Laptop, ein Familien-PC oder ein Chromebook reicht – installieren musst du nichts.

| Schritt | Gerät |
|---|---|
| 1–3 Supabase und Google | **Computer empfohlen** (Handy geht notfalls in der Desktop-Ansicht) |
| 4 VAPID-Schlüssel erzeugen | **Computer nötig** (Browser-Konsole) |
| 5 GitHub-Einstellungen | **Computer empfohlen** (lange Werte kopieren) |
| 6 Android-Konfiguration | Handy geht (oder die Werte an Claude schicken) |
| 7 Zusammenführen und bereitstellen | Handy geht |
| 8–9 Android-App, ntfy | Android-Handy |
| 10 iPhone | iPhone |
| 11 Fire TV | Fernseher; für den Download-Link einmal Computer |

### Spickzettel – diese Werte sammelst du unterwegs

Trag sie dir in den Passwort-Manager ein. **GEHEIM** heißt: nie ins Repository, nie in eine App, **nie an Claude
oder in einen Chat schicken**.

| Wert | Beispiel / Aussehen | Wo er gebraucht wird | Geheim? |
|---|---|---|---|
| Projekt-Referenz | `abcdefghijklmnopqrst` (20 Zeichen) | GitHub-Variable `SUPABASE_PROJECT_REF` | nein |
| Projekt-URL | `https://abcdefghijklmnopqrst.supabase.co` | GitHub-Variable `SUPABASE_URL`, Android `supabaseUrl` | nein |
| Publishable Key | `sb_publishable_…` | GitHub-Variable `SUPABASE_ANON_KEY`, Android `supabaseAnonKey` | nein |
| Datenbank-Passwort | selbst vergeben | GitHub-Secret `SUPABASE_DB_PASSWORD` | **GEHEIM** |
| Access Token | `sbp_…` | GitHub-Secret `SUPABASE_ACCESS_TOKEN` | **GEHEIM** |
| Google Client-ID | `1234…apps.googleusercontent.com` | nur Supabase (Google-Anbieter) | nein |
| Google Client-Secret | `GOCSPX-…` | nur Supabase (Google-Anbieter) | **GEHEIM** |
| `VAPID_PUBLIC_KEY` | 87 Zeichen | Supabase-Secret, GitHub-Variable, Android `vapidPublicKey` | nein |
| `VAPID_PRIVATE_KEY` | 43 Zeichen | **nur** Supabase-Secret | **GEHEIM** |
| `VAPID_SUBJECT` | `mailto:deine-adresse@example.org` | nur Supabase-Secret | nein |
| Adresse der Web-App | `https://q0ochen.github.io/DrinkWithMe` | Supabase „Site URL“, Android `webAppUrl` | nein |

---

## 1. Supabase: Konto und Projekt (Computer empfohlen)

1. Auf [supabase.com](https://supabase.com) → **Start your project** → mit GitHub oder E-Mail registrieren.
   Eine Kreditkarte wird nicht verlangt.
2. Falls gefragt: eine **Organisation** anlegen (Typ „Personal“, Tarif **Free**).
3. **New project**:
   - **Name:** z. B. `drink-with-me`
   - **Database Password:** auf **Generate a password** tippen, **kopieren und im Passwort-Manager speichern**
     (wird in Schritt 5 gebraucht).
   - **Region:** **Central EU (Frankfurt)** – technisch `eu-central-1`. Wichtig für den Datenschutz.
   - Tarif: **Free**.
   - Falls „Security options“ angezeigt werden: die Standardwerte lassen (**Data API** eingeschaltet,
     Schema `public`). Die Apps sprechen den Server über diese Data API an.
4. **Create new project** – das Einrichten dauert ein, zwei Minuten.
5. Die **Projekt-Referenz** notieren: **Project Settings → General → Project ID** (20 Kleinbuchstaben/Ziffern).
   Die **Projekt-URL** ist dann `https://<projekt-referenz>.supabase.co`.
6. Den **Publishable Key** notieren: **Project Settings → API Keys** → Schlüssel, der mit `sb_publishable_`
   beginnt. (Der ältere **anon**-Key unter „Legacy API Keys“ funktioniert ebenfalls – nimm einen von beiden und
   bleib dabei.) **Nicht** den „secret“- oder „service_role“-Key nehmen – der ist geheim.

> **Pause nach 7 Tagen ohne Nutzung:** Supabase pausiert kostenlose Projekte, wenn sie etwa eine Woche lang
> kaum benutzt werden, und schickt vorher eine E-Mail. Die Daten bleiben erhalten. So holst du das Projekt
> zurück: im Dashboard das Projekt öffnen → **Restore project** (dauert ein paar Minuten). Wie lange das
> Wiederherstellen möglich ist, gibt Supabase je nach Seite mit 90 Tagen bis einem Jahr an – nicht darauf
> ankommen lassen. Solange jemand angemeldet ist und die App benutzt, fragt sie regelmäßig beim Server nach –
> das zählt als Nutzung.

---

## 2. Google Cloud: Zustimmungsbildschirm und OAuth-Client (Computer empfohlen)

Damit „Mit Google anmelden“ funktioniert, braucht Supabase eine **Client-ID** und ein **Client-Secret** von
Google. Das kostet nichts und braucht kein Rechnungskonto.

### 2.1 Projekt anlegen

1. [console.cloud.google.com](https://console.cloud.google.com/) öffnen, mit deinem Google-Konto anmelden,
   Nutzungsbedingungen bestätigen.
2. Oben links auf die Projektauswahl → **Neues Projekt** → Name z. B. `Drink With Me` → **Erstellen**.
   Danach sicherstellen, dass oben dieses Projekt ausgewählt ist.

### 2.2 Zustimmungsbildschirm („Google Auth Platform“)

Menü → **APIs & Dienste → OAuth-Zustimmungsbildschirm**. Google nennt diesen Bereich inzwischen
**„Google Auth Platform“**; die Unterpunkte heißen **Branding**, **Zielgruppe**, **Clients** und **Datenzugriff**.

1. **Jetzt starten** (bzw. „Konfigurieren“):
   - **App-Name:** `Drink With Me`
   - **Nutzersupport-E-Mail:** deine Adresse
   - **Zielgruppe:** **Extern**
   - **Kontaktdaten:** deine Adresse
   - Der Nutzerdatenrichtlinie zustimmen → **Erstellen**.
2. **Branding:**
   - **Kein Logo hochladen** – ein Logo löst eine Prüfung durch Google aus, die wir nicht brauchen.
   - Unter **Autorisierte Domains** `<projekt-referenz>.supabase.co` hinzufügen (z. B.
     `abcdefghijklmnopqrst.supabase.co`) → **Speichern**.
3. **Datenzugriff** → **Bereiche hinzufügen oder entfernen** → nur diese drei anhaken:
   `openid`, `.../auth/userinfo.email`, `.../auth/userinfo.profile` → **Aktualisieren** → **Speichern**.
   Das sind „nicht sensible“ Bereiche – dafür ist keine Google-Prüfung nötig.
4. **Zielgruppe** → Veröffentlichungsstatus „Testen“ → **App veröffentlichen** → bestätigen.
   Danach steht dort **„In Produktion“**.

   Warum? Im Status „Testen“ dürfen sich nur eingetragene Testnutzer anmelden, und deren Anmeldung läuft nach
   7 Tagen ab.

### 2.3 OAuth-Client vom Typ „Webanwendung“

1. **Clients** → **Client erstellen** (bzw. „Anmeldedaten erstellen → OAuth-Client-ID“).
2. **Anwendungstyp:** **Webanwendung** (auch für die Android-App – die Anmeldung läuft dort im Browser über
   Supabase, nicht über die Google-Dienste des Handys).
3. **Name:** z. B. `Drink With Me (Supabase)`.
4. **Autorisierte JavaScript-Quellen:** leer lassen.
5. **Autorisierte Weiterleitungs-URIs** → **URI hinzufügen** → genau diese Adresse (mit deiner Referenz):

   ```
   https://<projekt-referenz>.supabase.co/auth/v1/callback
   ```

   Tipp: Supabase zeigt dir genau diese Adresse in Schritt 3.1 als **Callback URL (for OAuth)** an – dort
   kopieren ist am sichersten.
6. **Erstellen**. Jetzt erscheinen **Client-ID** und **Clientschlüssel** (Client-Secret).
   **Beide sofort kopieren und speichern** – Google zeigt das Secret seit 2025 nur noch beim Erstellen an.
   Verloren? Im Client einen neuen Schlüssel hinzufügen und den neuen in Supabase eintragen.

> **Hinweis:** Beim Anmelden kann Google statt „Drink With Me“ den Namen `….supabase.co` anzeigen
> („weiter zu …“). Das ist normal, solange Google die Marke nicht geprüft hat – und das brauchen wir nicht.

---

## 3. Supabase: Anmeldung einstellen (Computer empfohlen)

### 3.1 Nur Google erlauben

Im Supabase-Dashboard → **Authentication → Sign In / Providers**:

1. **Allow new users to sign up:** **an lassen**. (Neue Konten entstehen nur über Google. Ist der Schalter aus,
   kann sich niemand zum ersten Mal anmelden.)
2. **Allow anonymous sign-ins:** **aus**.
3. **Allow manual linking:** aus.
4. Bei den Anbietern:
   - **Email:** öffnen → **Enable Email provider** **aus** → Speichern. (Keine Registrierung per
     E-Mail/Passwort – wichtig, damit Freundschaftsanfragen per E-Mail nur echte, von Google bestätigte
     Adressen finden.)
   - **Phone:** aus (Standard).
   - **Google:** öffnen →
     - **Enable Sign in with Google:** an
     - **Client IDs:** die Client-ID aus 2.3
     - **Client Secret (for OAuth):** das Client-Secret aus 2.3
     - **Skip nonce checks:** aus · **Allow users without an email:** aus
     - Die angezeigte **Callback URL (for OAuth)** muss genau der Weiterleitungs-URI aus 2.3 entsprechen.
     - **Save**.

### 3.2 Adressen für die Rückkehr nach der Anmeldung

**Authentication → URL Configuration**:

- **Site URL:** `https://q0ochen.github.io/DrinkWithMe` → **Save**
- **Redirect URLs** → **Add URL** – genau diese zwei Einträge (Groß-/Kleinschreibung und Schrägstriche zählen):

  ```
  drinkwithme://auth-callback
  https://q0ochen.github.io/DrinkWithMe/auth/callback
  ```

  Der erste ist die Rückkehr in die **Android-App**, der zweite in die **Web-App**.

> **Nicht** `supabase config push` ausführen (falls du die Supabase-Befehle kennst): Die Datei
> `supabase/config.toml` enthält nur Platzhalter für die lokale Entwicklung und würde diese Einstellungen
> überschreiben.

### 3.3 Empfohlen: Anmelde-Protokolle nicht in der Datenbank speichern

**Authentication → Audit Logs** (Bereich „Configuration“) → Schalter **„Write audit logs to the database“**
**ausschalten** → speichern. Sonst schreibt Supabase jede Anmeldung, Token-Erneuerung und Abmeldung dauerhaft in
die Projekt-Datenbank – auch über das Löschen eines Kontos hinaus. Ausgeschaltet stehen die Protokolle nur noch
kurz in den Supabase-Logs (siehe [`DATENSCHUTZ_ENTWURF.md`](DATENSCHUTZ_ENTWURF.md), Abschnitt 4.8).

Das geht **nur im Dashboard**: Die Datei `supabase/config.toml` und der Workflow „Supabase Deploy“ können diesen
Schalter nicht setzen.

---

## 4. Schlüssel für Benachrichtigungen (VAPID) (Computer nötig)

Benachrichtigungen werden mit einem eigenen Schlüsselpaar unterschrieben. Der **öffentliche** Schlüssel kommt in
die Apps, der **private** nur als Secret in Supabase.

### 4.1 Schlüsselpaar erzeugen – ohne etwas zu installieren

Das Repository bringt dafür das Skript `supabase/scripts/vapid-schluessel-erzeugen.ts` mit. Es braucht Deno.
Ohne Installation geht dasselbe in der **Entwicklerkonsole deines Browsers** – die Schlüssel entstehen dabei nur
auf deinem Rechner, nichts wird verschickt.

> **Warum nicht über GitHub Actions?** Die Protokolle von Workflows sind in einem öffentlichen Repository für
> alle sichtbar – der private Schlüssel wäre damit öffentlich. Bitte auch keine „VAPID-Generator“-Webseiten
> benutzen: Dann kennt ein Fremder deinen privaten Schlüssel.

1. Am Computer Chrome, Edge oder Firefox öffnen und eine beliebige https-Seite aufrufen, z. B.
   `https://example.org`.
2. **F12** drücken (Mac: `Cmd + Option + I`) → Reiter **Konsole** / **Console**.
3. Den folgenden Text **komplett** kopieren, in die Konsole einfügen und **Enter** drücken.
   Beim ersten Einfügen warnt der Browser und verlangt, dass du einen bestimmten Satz eintippst (in Chrome z. B.
   „allow pasting“ bzw. „Einfügen zulassen“) – tippe genau das, was dort steht, und füge den Text dann erneut ein.

   ```js
   (async () => {
     const b64u = (bytes) => btoa(String.fromCharCode(...new Uint8Array(bytes))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
     const pair = await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]);
     const publicKey = b64u(await crypto.subtle.exportKey("raw", pair.publicKey));
     const privateKey = (await crypto.subtle.exportKey("jwk", pair.privateKey)).d;
     console.log(`VAPID_PUBLIC_KEY=${publicKey}\nVAPID_PRIVATE_KEY=${privateKey}`);
   })();
   ```

   Das ist genau das, was das Skript im Repository macht (P-256, base64url).
4. Die Ausgabe sieht so aus:

   ```
   VAPID_PUBLIC_KEY=B…   (87 Zeichen)
   VAPID_PRIVATE_KEY=…   (43 Zeichen)
   ```

   Beide Zeilen in den Passwort-Manager kopieren, dann den Tab schließen.

**Alternative mit Deno** (für alle, die es installiert haben), im Hauptverzeichnis des Repositorys:

```bash
deno run supabase/scripts/vapid-schluessel-erzeugen.ts
```

> Erzeuge das Schlüsselpaar **nur einmal**. Ein neues Paar macht alle bestehenden Benachrichtigungs-Abos
> ungültig; dann müssen alle die Benachrichtigungen neu einrichten.

### 4.2 Secrets der Edge Functions setzen

Supabase-Dashboard → **Edge Functions → Secrets** → **Add new secret** – drei Einträge:

| Name | Wert |
|---|---|
| `VAPID_PUBLIC_KEY` | der öffentliche Schlüssel (87 Zeichen) |
| `VAPID_PRIVATE_KEY` | der private Schlüssel (43 Zeichen) |
| `VAPID_SUBJECT` | `mailto:` + eine echte E-Mail-Adresse von dir, z. B. `mailto:drinkwithme@example.org` |

→ **Save**. Das geht schon, bevor die Funktionen bereitgestellt sind. `SUPABASE_URL` und die Server-Schlüssel
setzt Supabase in den Edge Functions automatisch – die musst du **nicht** eintragen.

Die Adresse in `VAPID_SUBJECT` sehen die Push-Dienste (z. B. Apple, Google, ntfy), damit sie dich bei Problemen
erreichen können.

---

## 5. GitHub: Secrets, Variablen und GitHub Pages (Computer empfohlen)

Alles unter [github.com/q0ochen/DrinkWithMe](https://github.com/q0ochen/DrinkWithMe) → **Settings**.

> **Reihenfolge ist wichtig:** Mach Schritt 5 und 6 **vor** dem Zusammenführen in Schritt 7. Beim Zusammenführen
> wird die Web-App gebaut und veröffentlicht – mit den Werten, die **in diesem Moment** eingetragen sind.
> (Vergessen? Kein Problem: Werte nachtragen, dann **Actions → Web-App → Run workflow** mit Branch `main` – das
> veröffentlicht die Web-App neu.)

### 5.1 Name des Repositorys prüfen

**Settings → General → Repository name** muss genau **`DrinkWithMe`** lauten. Die Adresse der Web-App
(`https://q0ochen.github.io/DrinkWithMe/`) hängt davon ab.

### 5.2 Secrets (geheim)

**Settings → Secrets and variables → Actions** → Reiter **Secrets** → **New repository secret**:

| Name | Wert |
|---|---|
| `SUPABASE_ACCESS_TOKEN` | [supabase.com/dashboard/account/tokens](https://supabase.com/dashboard/account/tokens) → **Generate new token** → Name z. B. `github-deploy` → Token (`sbp_…`) kopieren |
| `SUPABASE_DB_PASSWORD` | das Datenbank-Passwort aus Schritt 1 |

### 5.3 Variablen (öffentlich, unkritisch)

Gleiche Seite → Reiter **Variables** → **New repository variable**:

| Name | Wert | Wofür |
|---|---|---|
| `SUPABASE_PROJECT_REF` | Projekt-Referenz, z. B. `abcdefghijklmnopqrst` | Workflow „Supabase Deploy“ |
| `SUPABASE_URL` | `https://<projekt-referenz>.supabase.co` | Web-App |
| `SUPABASE_ANON_KEY` | der Publishable Key (`sb_publishable_…`) bzw. anon-Key | Web-App |
| `VAPID_PUBLIC_KEY` | der öffentliche VAPID-Schlüssel (87 Zeichen) | Web-App |

Die Variable `WEB_APP_URL` ist optional; ohne sie gilt `https://q0ochen.github.io/DrinkWithMe`.

### 5.4 GitHub Pages einschalten

**Settings → Pages → Build and deployment → Source:** **GitHub Actions** auswählen. Mehr ist nicht nötig – den
Rest erledigt der Workflow „Web-App“.

### 5.5 F-Droid-Paketquelle: Schlüssel als Secret (einmalig, Handy geht)

Damit Testerinnen und Tester Drink With Me **in der F-Droid-App** installieren können, baut der Workflow „Android“
eine eigene F-Droid-Paketquelle (siehe README, „In F-Droid“). Sie wird mit einem **geheimen Schlüssel**
unterschrieben; daran erkennt F-Droid, dass die Paketquelle echt ist. Unabhängig von den Schritten 1–4 und 6–7 –
geht also auch, bevor der Server eingerichtet ist.

1. **Settings → Secrets and variables → Actions** → Reiter **Secrets** → **New repository secret**.
2. **Name:** `FDROID_REPO_KEYSTORE`
3. **Secret:** den Schlüssel als Base64-Text einfügen – entweder den, den Claude erzeugt und dir als Datei
   gegeben hat (ganzen Inhalt kopieren, eine einzige lange Zeile), oder einen selbst erzeugten (unten).
4. **Add secret** → dann **Actions → Android → Run workflow** → Branch wählen → **Run workflow**. Der Job
   „Eigene F-Droid-Paketquelle aktualisieren“ veröffentlicht die Paketquelle auf dem Zweig `fdroid-repo`.

Danach baut jede neue Testversion die Paketquelle automatisch neu. Fehlt das Secret, überspringt der Workflow
diesen Teil nur (Hinweis im Lauf), alles andere läuft normal.

> **Wichtig:** Den Schlüssel **nicht verlieren und nicht austauschen** – sonst müssen alle die Paketquelle in
> F-Droid neu hinzufügen. Am besten zusätzlich im Passwort-Manager sichern. Der Workflow prüft, dass der
> Schlüssel zum Fingerabdruck `FDROID_REPO_FINGERPRINT` in `.github/workflows/android.yml` passt und dass README,
> QR-Code und `docs/F-DROID.md` denselben Fingerabdruck nennen; sonst bricht der Job mit einer Fehlermeldung ab
> (z. B. „Secret … passt nicht zum erwarteten Fingerabdruck oder ist nicht lesbar“ bei unvollständig kopiertem Text).
>
> **Selbst erzeugen** (am Computer mit Java): `keytool -genkeypair -keystore repo.p12 -storetype PKCS12
> -storepass drinkwithme-fdroid -keypass drinkwithme-fdroid -alias drinkwithme-fdroid -keyalg RSA -keysize 4096
> -validity 10000 -dname "CN=Drink With Me F-Droid Repo"`, dann die Datei als Base64-Text ins Secret – Linux:
> `base64 -w0 repo.p12`, macOS: `base64 -i repo.p12`, Windows (PowerShell):
> `[Convert]::ToBase64String([IO.File]::ReadAllBytes("repo.p12"))`. Das Passwort ist absichtlich nicht geheim –
> geheim ist die Datei. Den neuen Fingerabdruck (`keytool -list -v -keystore repo.p12 -storepass
> drinkwithme-fdroid`, Zeile „SHA256“, ohne Doppelpunkte) dann **überall** übernehmen: `FDROID_REPO_FINGERPRINT`
> in `.github/workflows/android.yml` (klein geschrieben), Link und Zeile „Von Hand“ in der README, Tabelle in
> `docs/F-DROID.md` (11.3) und den QR-Code `docs/fdroid-paketquelle-qr.svg` neu erzeugen (Claude kann das erledigen).

---

## 6. Android-Konfiguration ins Repository bringen (Handy geht)

Die Android-App liest ihre Server-Werte beim Bauen aus der Datei `android/app/supabase.properties`. Alle Werte
darin sind **öffentlich** (sie stecken ohnehin in jeder APK) und dürfen ins Repository. Fehlt die Datei, läuft
die App trotzdem – nur „Freunde“ zeigt dann „Server noch nicht eingerichtet“.

Inhalt (mit deinen Werten):

```properties
supabaseUrl=https://abcdefghijklmnopqrst.supabase.co
supabaseAnonKey=sb_publishable_…
vapidPublicKey=B…(87 Zeichen)
webAppUrl=https://q0ochen.github.io/DrinkWithMe
```

**Weg A – Claude macht es:** Schick Claude im Chat die **drei öffentlichen Werte** (Projekt-URL, Publishable
Key, `VAPID_PUBLIC_KEY`) mit der Bitte, `android/app/supabase.properties` anzulegen. **Niemals** geheime Werte
mitschicken (siehe Spickzettel).

**Weg B – selbst im Browser:**

1. Im Repository oben links den **Branch** des Pull Requests auswählen (der Branch, auf dem die neue App liegt,
   z. B. `claude/…`). Nach dem Zusammenführen (Schritt 7) geht es genauso auf `main`.
2. In den Ordner `android/app` wechseln → **Add file → Create new file**.
3. Name: `supabase.properties` → Inhalt wie oben einfügen.
4. **Commit changes…** → „Commit directly to the …-Branch“ → **Commit changes**.

Danach baut GitHub automatisch eine neue APK mit diesen Werten (Workflow „Android“).

---

## 7. Pull Request zusammenführen und Server bereitstellen (Handy geht)

### 7.1 Warum zusammenführen?

Den Workflow **„Supabase Deploy“** kann man nur starten, wenn er auf dem Standard-Branch **`main`** liegt. Die
neue App liegt aber noch im Pull Request. Erst nach dem Zusammenführen (Merge) erscheint im Reiter **Actions**
der Knopf **Run workflow**. Außerdem wird die Web-App nur von `main` aus auf GitHub Pages veröffentlicht.

### 7.2 Pull Request zusammenführen

1. Reiter **Pull requests** → den Pull Request der neuen App öffnen.
   (Keiner da? Auf der Startseite des Repositorys erscheint oft ein gelber Hinweis **Compare & pull request** –
   oder bitte Claude, einen anzulegen.)
2. Unten warten, bis die Prüfungen grün sind.
3. **Merge pull request** → **Confirm merge**.

Danach starten automatisch: „Web-App“ (baut und veröffentlicht die Web-App), „Android“ (baut die APK) und
„Supabase“ (Tests). Im Reiter **Actions** siehst du den Fortschritt; grün = fertig.

### 7.3 Server bereitstellen: „Supabase Deploy“

1. Reiter **Actions** → links **Supabase Deploy** → rechts **Run workflow** → Branch `main` → **Run workflow**.
2. Nach ein paar Minuten sollte der Lauf grün sein. Er legt die Datenbank-Tabellen an (Migrationen) und lädt die
   Edge Functions `notify-friends` und `delete-account` hoch.
3. Fehlt eine Einstellung, bricht der Workflow gleich am Anfang mit einer **deutschen Fehlermeldung** ab
   (z. B. „Secret SUPABASE_ACCESS_TOKEN fehlt“). Am Ende prüft er, ob die VAPID-Secrets gesetzt sind; fehlen sie,
   gibt es nur eine **Warnung** („VAPID-Secrets fehlen“) – dann zurück zu Schritt 4.2.

Den Workflow startest du nur wieder, wenn sich am Server etwas ändert (neue Migrationen oder Funktionen).
Mehrfaches Starten schadet nicht.

### 7.4 Kurz prüfen

- Supabase-Dashboard → **Table Editor**: Tabellen `profiles`, `friendships`, `friend_requests`, `invite_links`,
  `drink_events`, `push_subscriptions`, `rate_limits` sind da.
- **Edge Functions**: `notify-friends` und `delete-account` sind aufgelistet.
- [https://q0ochen.github.io/DrinkWithMe/](https://q0ochen.github.io/DrinkWithMe/) öffnet die Web-App, und unter
  „Freunde“ steht **nicht** „Server noch nicht eingerichtet“.

---

## 8. Android-App installieren (Android-Handy)

### 8.1 APK herunterladen

**Für alle (ohne Anmeldung):** diesen Link auf dem Android-Gerät öffnen:

`https://github.com/q0ochen/DrinkWithMe/releases/download/testversion/DrinkWithMe.apk`

Die Testversion wird nach jedem erfolgreichen Build auf `main` (bzw. dem Entwicklungszweig) automatisch ersetzt
(`.github/workflows/android.yml`, Release „testversion“). Die Release-Hinweise nennen Datum und Code-Stand. Wer
automatisch auf neue Testversionen hingewiesen werden möchte: [Obtainium](../README.md#android-testversion).

> Die APK muss **nach** Schritt 6 gebaut sein, sonst fehlen die Server-Werte. Das passiert von selbst: Das Einchecken
> von `supabase.properties` startet einen neuen Build, der die Testversion ersetzt. Im Zweifel in den
> Release-Hinweisen nachsehen, ob der Stand neuer ist.

**Alternative mit GitHub-Konto** (am Handy im Browser angemeldet): Reiter **Actions** → links **Android** → den
neuesten **grünen** Lauf öffnen → unten bei **Artifacts** → **DrinkWithMe-android-apk** herunterladen (eine
ZIP-Datei) → in der Dateien-App **entpacken** → darin liegt `app-debug.apk` (dieselbe App).

> Bitte **keine** Releases von Hand anlegen: Obtainium würde sie ebenfalls finden, und Tags mit `v…` sind für die
> spätere F-Droid-Version reserviert.

### 8.2 Installieren

0. **War schon einmal eine „Drink With Me“ auf dem Gerät** – selbst in Android Studio gebaut, aus einem anderen
   Projekt mit demselben Paketnamen oder eine Testversion vom 9. November? Dann zuerst **deinstallieren**
   (Einstellungen → Apps → Drink With Me → Deinstallieren; auch im Privaten Bereich und im Arbeitsprofil
   nachsehen). Sie hat eine andere Signatur, und Android verweigert sonst die Installation.
1. `DrinkWithMe.apk` (bzw. `app-debug.apk` aus der ZIP-Datei) antippen – am besten direkt in Chrome über
   **Öffnen**. Fragt Chrome vorher „Datei könnte schädlich sein“: **Trotzdem herunterladen**.
2. Android fragt, ob die App, mit der du die Datei öffnest (Chrome bzw. Dateien), **unbekannte Apps installieren**
   darf → erlauben. Auf Pixel-Handys heißt der Schalter **„Dieser Quelle vertrauen“**; ab Android 17 QPR2
   (November 2026) kommt stattdessen direkt ein Dialog: **„Nur dieses Mal“** oder **„Immer zulassen“** wählen.
   Dann **Installieren**.
3. Warnt **Google Play Protect** („Von Play Protect blockiert“, „Play Protect kennt diese App noch nicht“ o. Ä.):
   **nicht** auf „OK“ oder „Verstanden“ tippen – das bricht ab –, sondern **Weitere Details → Trotzdem
   installieren** (oder **App scannen**), ggf. mit Fingerabdruck/PIN bestätigen. Die Test-APK ist neu, nicht im
   Play Store und mit einem öffentlichen Test-Schlüssel signiert – deshalb die Warnung.
4. App öffnen → **Willkommen bei Drink With Me** → Getränke für die eigene Bar auswählen.

> **Neues Pixel (Android 17)?** Das Gerät selbst ist kein Hindernis: Die APK ist auf 16-KB-Speicherseiten
> vorbereitet, und die Entwickler-Prüfung von Google gilt in Deutschland noch nicht (siehe Ausblick unten). Jede
> neue APK wird automatisch im Emulator mit dem neuesten Android installiert und gestartet – auch mit
> 16-KB-Speicherseiten (am 10.11.2026 erfolgreich mit Android 17, API 37 und 37.2).

**Updates:** Einfach die neuere APK darüber installieren. Alle Test-APKs aus GitHub haben dieselbe Signatur, Bar
und Verlauf bleiben erhalten. Getränke aus der **alten** App-Version (vor dem Umbau) werden **nicht**
übernommen.

> **Ausblick 2027:** Google will ab 2027 weltweit verlangen, dass Apps auf zertifizierten Android-Geräten von
> registrierten Entwicklern stammen. Nicht registrierte Apps lassen sich dann nur noch über einen „erweiterten
> Ablauf“ (einmalig, mit 24 Stunden Wartezeit) oder per ADB installieren. In Deutschland gilt das (Stand
> November 2026) noch nicht. Mehr dazu in [`F-DROID.md`](F-DROID.md).

**Über F-Droid (optional):** Wer lieber F-Droid nutzt, fügt dort die eigene Paketquelle hinzu und installiert
Drink With Me aus F-Droid – Anleitung mit Link und QR-Code in der [README](../README.md#in-f-droid-eigene-paketquelle).
Voraussetzung: Schritt 5.5 ist erledigt. Updates kommen dann über F-Droid; die App ist dieselbe wie aus dem
Download-Link (gleiche Signatur).

### 8.3 Anmelden

Reiter **Freunde** → **Mit Google anmelden** → im Browser das Google-Konto wählen → die App öffnet sich wieder
und zeigt deinen Namen. Ab Android 13 fragt die App danach, ob sie **Benachrichtigungen** zeigen darf →
**Zulassen**.

---

## 9. ntfy für sofortige Benachrichtigungen (Android)

Drink With Me nutzt **UnifiedPush** statt der Google-Dienste. Damit Benachrichtigungen **sofort** ankommen,
braucht das Handy eine kleine Zusatz-App, die die Nachrichten zustellt – empfohlen ist **ntfy** (kostenlos,
freie Software, ohne Konto).

**Ohne ntfy** funktioniert trotzdem alles: Die App fragt dann **etwa alle 15 Minuten** und **beim Öffnen**
(höchstens alle 5 Minuten) nach neuen Getränken deiner Freunde und zeigt sie als Benachrichtigung. Android kann
diese Abfrage im Stromsparmodus auch mal weiter hinauszögern. Das gilt auch, solange ntfy zwar eingerichtet, aber
beim Server noch nicht bestätigt ist – die App fragt dann vorsichtshalber weiter selbst nach.

**Einrichten:**

1. ntfy installieren: aus **F-Droid** ([f-droid.org/packages/io.heckel.ntfy](https://f-droid.org/packages/io.heckel.ntfy/))
   oder aus dem **Play Store** (Suche „ntfy“). Beide Versionen funktionieren.
2. ntfy einmal öffnen und ihm erlauben, Benachrichtigungen zu zeigen. Ein ntfy-Konto oder ein „Thema“ brauchst
   du **nicht**.
3. In **Drink With Me**: Reiter **Einstellungen** → Karte **Benachrichtigungen** → **Über ntfy einrichten**.
   (Ohne ntfy steht dort stattdessen **ntfy bei F-Droid öffnen**; nach der Installation **Erneut prüfen**.)
   Die App wählt ntfy **nicht** von selbst aus – dieser eine Tipp ist nötig.
4. Falls ntfy nachfragt: dort bestätigen. Danach steht in Drink With Me
   **„Sofortige Benachrichtigungen über ntfy sind aktiv.“**
5. Empfehlenswert: in den Android-Einstellungen für **ntfy** die **Akku-Optimierung ausschalten**
   („Nicht eingeschränkt“) – sonst kann Android ntfy schlafen legen.

ntfy zeigt je nach Version eine dauerhafte, leise Benachrichtigung an, damit Android die Verbindung offen lässt.
Die kannst du in den Android-Einstellungen von ntfy ausblenden, falls sie stört.

> Fehlt in der Karte „Benachrichtigungen“ der ntfy-Knopf ganz und steht dort nur der Satz „Ohne Push-App prüft
> Drink With Me etwa alle 15 Minuten …“, dann fehlt in `supabase.properties` der `vapidPublicKey` (oder er ist
> nicht genau 87 Zeichen lang).

Die Nachrichten sind **Ende-zu-Ende verschlüsselt** (Web-Push-Verschlüsselung): ntfy sieht nur, dass eine
Nachricht kommt, nicht ihren Inhalt.

> **Eigener ntfy-Server?** Das brauchst du normalerweise nicht – der Standard-Server `ntfy.sh` reicht. Wer trotzdem
> einen eigenen ntfy- oder UnifiedPush-Server betreibt: Er muss über **https auf Port 443** und unter einem
> öffentlichen Namen erreichbar sein. Adressen mit anderem Port (z. B. `:8443`) lehnt der Server ab und löscht das
> Abo beim ersten Versand; dann am besten einen Reverse-Proxy auf Port 443 davorschalten.

---

## 10. iPhone: Web-App einrichten

Fürs iPhone gibt es in der Testphase die kostenlose **Web-App** – mit Bar, Verlauf, Freunden, Vorlesen und
Benachrichtigungen. Voraussetzung: **iOS 16.4 oder neuer** (Einstellungen → Allgemein → Info → iOS-Version).

> **Warum nicht „sideloaden“?** Eine Android-APK läuft auf dem iPhone nicht. Kostenloses Sideloading mit Apple-ID
> und Windows-PC (AltStore, SideStore, Sideloadly) könnte nur die **alte, rein lokale** iPhone-App ohne Freunde
> installieren, muss **alle 7 Tage** erneuert werden und war im Herbst 2026 wegen Änderungen bei Apple mehrfach
> gestört. TestFlight und EU-Marktplätze (z. B. AltStore PAL) setzen das Apple-Entwicklerprogramm (99 € pro Jahr)
> und eine neue iPhone-App voraus. Die Web-App hat dagegen alle Funktionen, braucht keinen Computer und kostet
> nichts. Sie ist online, sobald der Arbeitsstand in `main` zusammengeführt ist (Schritt 7).

1. In **Safari** öffnen: **https://q0ochen.github.io/DrinkWithMe/**
2. Auf **Teilen** tippen (Quadrat mit Pfeil nach oben; je nach iOS-Version hinter den drei Punkten „…“ unten
   rechts) → **Zum Home-Bildschirm** → falls angeboten, **„Als Web-App öffnen“** eingeschaltet lassen →
   **Hinzufügen**.
3. Safari schließen und Drink With Me **über das neue Symbol auf dem Home-Bildschirm** öffnen.
   **Ab jetzt nur noch so benutzen:** Die Home-Bildschirm-App hat einen **eigenen Speicher**. Was du vorher im
   Safari-Tab eingetragen hast, ist dort nicht zu sehen. Außerdem gibt es Benachrichtigungen auf dem iPhone nur
   in der Home-Bildschirm-App.
   Hast du schon im Safari-Tab Getränke eingetragen? Dann **vor** dem Schließen dort **Einstellungen → Bar und
   Verlauf → Sicherung speichern** (über „Teilen“ → **In Dateien sichern**).
4. Bar einrichten – oder, falls du eine Sicherung hast, beim ersten Start unter **„Schon eine Sicherung?“** auf
   **Sicherung einlesen** tippen und die Datei wählen.
5. **Freunde** → **Mit Google anmelden** → Google-Konto wählen → zurück in der App.
6. **Einstellungen** → **Benachrichtigungen** → **Benachrichtigungen einschalten** → iOS fragt → **Erlauben**.
   Danach steht dort „Eingeschaltet: Du bekommst eine Nachricht, wenn deine Freunde etwas trinken.“

Hinweise:

- Bar und Verlauf liegen **nur auf diesem iPhone** (im Speicher der Web-App). Löschst du das Symbol vom
  Home-Bildschirm, sind sie weg. Manche Browser (z. B. Safari) löschen Website-Daten auch von selbst, wenn die
  Seite längere Zeit nicht geöffnet wurde; die Web-App bittet den Browser deshalb einmal, ihren Speicher zu
  behalten. Sicherer ist trotzdem: ab und zu **Einstellungen → Bar und Verlauf → Sicherung speichern**. Beim
  Einlesen wird nichts gelöscht und nichts mit Freunden geteilt.
- Ein einzelnes Getränk ohne Teilen eintragen: in der Bar auf **„⋮“** beim Getränk tippen →
  **Privat eintragen (nicht teilen)**.
- Spracheingabe gibt es nur, wenn der Browser sie anbietet; Vorlesen nutzt die deutsche Stimme des iPhones.
- **Noch nicht auf einem echten iPhone getestet** ist die Google-Anmeldung *innerhalb* der Home-Bildschirm-App.
  Falls dort etwas hakt (z. B. die Anmeldung endet in einem Safari-Fenster statt in der App oder Google meldet
  „disallowed_useragent“): das Fenster mit **Fertig** schließen, die App neu öffnen und nachsehen, ob du
  angemeldet bist – und uns den genauen Ablauf schreiben (Testplan, Schritt B2).

---

## 11. Fire TV Stick 4K Max

Eine APK läuft auf Handy, Tablet und Fernseher. Auf dem Fernseher zeigt die App eine Seitenleiste, alles ist mit
der Fernbedienung bedienbar (Steuerkreuz + OK), und zum Eintragen ist keine Texteingabe nötig.

### 11.1 Welches Betriebssystem hat dein Fire TV?

**Einstellungen → Mein Fire TV → Info** → dort steht die Software-/Betriebssystemversion.

| Anzeige | Geht Drink With Me? |
|---|---|
| **Fire OS 7.x** oder **Fire OS 8.x** | **Ja**, per „Downloader“ (unten) |
| **Vega OS** (Versionen, die mit 1 oder 2 beginnen) | **Nein** – Vega OS kann keine Android-Apps installieren |
| Fire OS 6 oder älter | **Nein** – die App braucht mindestens Android 8 |

Der Fire TV Stick 4K Max läuft nach allem, was bekannt ist, mit Fire OS (nicht Vega OS). Neue Modelle wie der
Fire TV Stick 4K Select haben Vega OS – deshalb bitte einmal nachsehen.

### 11.2 Installieren mit „Downloader“

1. Im Amazon Appstore die kostenlose App **Downloader** (von AFTVnews) suchen und installieren.
2. **Einstellungen → Mein Fire TV → Entwickleroptionen → Apps unbekannter Herkunft installieren** →
   **Downloader** → **An**.
   - Keine „Entwickleroptionen“? **Einstellungen → Mein Fire TV → Info** → **7-mal** auf den Gerätenamen
     (z. B. „Fire TV Stick 4K Max“) klicken, bis „Du bist jetzt Entwickler“ erscheint.
   - Taucht Downloader in der Liste nicht auf: erst **Einstellungen → Mein Fire TV → Info → Nach Updates suchen**.
     Ein Fehler in einem Fire-TV-Update vom Herbst 2026 hat genau das verursacht; Amazon verteilt eine Korrektur.
3. Downloader öffnen → in das Adressfeld den **öffentlichen Link** aus Schritt 8.1 eintippen:
   `https://github.com/q0ochen/DrinkWithMe/releases/download/testversion/DrinkWithMe.apk` → **Go**.
   (Der Link aus „Actions → Artifacts“ geht hier **nicht** – er verlangt eine GitHub-Anmeldung und liefert eine
   ZIP-Datei.)
4. **Installieren** → **Fertig**. Downloader bietet an, die APK-Datei zu löschen → **Löschen** (spart Platz).
5. Drink With Me findest du unter **Apps → Meine Apps** (mit eigenem Banner).

### 11.3 Was auf dem Fernseher geht – und was nicht

| Funktion | Fire TV |
|---|---|
| **Bar** (Getränk mit OK eintragen, „Rückgängig“) | funktioniert |
| **Verlauf** (Diagramme, Kennzahlen, alles zusätzlich als Text) | funktioniert – zeigt aber nur, was **auf dem Fernseher** eingetragen wurde (der Verlauf liegt immer nur auf dem jeweiligen Gerät) |
| **Vorlesen** | funktioniert, wenn das Gerät eine deutsche Sprachausgabe für Apps hat – in **Einstellungen → Vorlesen → Vorlesen testen** prüfen (auf dem Fire TV noch nicht getestet) |
| **Sprechen** (Mikrofon-Knopf) | erscheint nur, wenn das Gerät eine Spracherkennung für Apps anbietet. Die Alexa-Taste der Fernbedienung ist das nicht – auf dem Fire TV fehlt der Knopf vermutlich |
| **Anmeldung / Freunde** | **nicht eigens für den Fernseher gebaut.** Die App öffnet zum Anmelden einen Browser. Ob das mit dem Browser des Fire TV klappt (Google-Anmeldung mit der Fernbedienung und Rückkehr in die App), ist **nicht getestet**. Gibt es keinen passenden Browser, zeigt die App „Für die Anmeldung wird ein Browser benötigt …“. Eine Anmeldung per Code oder QR-Code vom Handy aus gibt es noch nicht |
| **Benachrichtigungen** | kein Verlass darauf – der Fernseher ist dafür nicht gedacht |

Kurz: Auf dem Fernseher ist Drink With Me ein **lokales Getränketagebuch mit Vorlesen**. Für Freunde nehmt
ihr das Handy oder das iPhone.

---

## 12. Testplan für zwei Personen

**A** = Android-Handy (mit installierter APK), **B** = iPhone (Web-App vom Home-Bildschirm). Beide haben ein
Google-Konto. Haken setzen, wenn das Erwartete passiert. Klappt etwas nicht: Nummer des Schritts, Uhrzeit und
genaue Meldung notieren (Bildschirmfoto).

| Nr. | Wer | Was tun | Erwartung | OK |
|---|---|---|---|---|
| A1 | A | App ohne Anmeldung öffnen, Bar auswählen, **Wasser** antippen | Kurze Bestätigung, großer Knopf **Rückgängig** für 10 Sekunden; Eintrag im **Verlauf** | [ ] |
| A2 | A | **Rückgängig** innerhalb von 10 s | Eintrag ist wieder weg | [ ] |
| A3 | A | **Einstellungen → Vorlesen testen** | Deutscher Satz wird gesprochen (sonst Hilfe „Vorlesen einrichten“) | [ ] |
| A4 | A | **Freunde → Mit Google anmelden** | Browser öffnet sich, danach zurück in der App, eigener Name sichtbar; Frage nach Benachrichtigungen | [ ] |
| A5 | A | ntfy einrichten (Schritt 9) | „Sofortige Benachrichtigungen über ntfy sind aktiv.“ | [ ] |
| B1 | B | Web-App vom Home-Bildschirm öffnen, Bar auswählen, Kaffee antippen | Eintrag im Verlauf, „Rückgängig“ | [ ] |
| B2 | B | **Freunde → Mit Google anmelden** | Zurück in der Web-App, angemeldet (bitte genau beobachten, siehe Hinweis in Schritt 10) | [ ] |
| B3 | B | **Einstellungen → Benachrichtigungen einschalten** | iOS fragt, danach „Eingeschaltet …“ | [ ] |
| F1 | B | **Freunde → Per E-Mail hinzufügen** → Google-Adresse von A | Neutrale Bestätigung; unter **Gesendete Anfragen** steht nur die gekürzte Adresse, z. B. „Anfrage an a***@gmail.com (wartet)“ – absichtlich ohne Namen und ohne Hinweis, ob es die Adresse gibt | [ ] |
| F2 | A | **Freunde → Aktualisieren** | Unter **Anfragen an dich** steht B → **Annehmen** → B steht bei **Deine Freunde** | [ ] |
| F3 | A | Unter **Deine Freunde** bei B auf **Entfernen**; **erst danach** **Freund einladen** → Link per Messenger an B schicken | Link der Form `https://q0ochen.github.io/DrinkWithMe/einladung/…` (ein Link, den A **vor** dem Entfernen erstellt hat, gilt für B nicht mehr) | [ ] |
| F4 | B | Link antippen (öffnet sich in **Safari**, nicht in der Home-Bildschirm-App) → dort anmelden → **Einladung annehmen** | „Du bist jetzt mit A befreundet.“ – auch in der Home-Bildschirm-App sichtbar (Freundschaften liegen auf dem Server) | [ ] |
| N1 | A | **Apfelschorle** antippen | B bekommt nach wenigen Sekunden „A trinkt gerade“ – „Apfelschorle (500 ml)“ | [ ] |
| N2 | B | Auf die Benachrichtigung tippen → **Vorlesen** | Ereignis-Seite mit großem Satz; „Vorlesen“ spricht z. B. „A trinkt eine Apfelschorle.“ | [ ] |
| N3 | B | Kaffee antippen | A bekommt die Nachricht sofort (mit ntfy) bzw. innerhalb von etwa 15 Minuten (ohne ntfy) | [ ] |
| N4 | A | Auf die Benachrichtigung tippen → **Vorlesen** | Großer Satz, bildschirmbreiter Knopf „Vorlesen“ | [ ] |
| S1 | A | Ein **alkoholisches** Getränk (z. B. Pils) antippen | Einmalige Frage „Alkoholische Getränke mit Freunden teilen?“ → **Nein** → B sieht nichts | [ ] |
| S2 | A | **Einstellungen → Alkoholische Getränke mit Freunden teilen** an → nochmal Pils | B sieht „Pils“ (nur Name, Menge, Zeitpunkt – keine Gramm) | [ ] |
| S3 | A | Getränk lange drücken → **Privat eintragen (nicht teilen)** | B sieht nichts | [ ] |
| S4 | A | Getränk antippen, dann sofort **Rückgängig** | Eintrag verschwindet bei B aus dem Feed (eine schon zugestellte Benachrichtigung bleibt) | [ ] |
| S5 | B | In der Bar beim Kaffee auf **„⋮“** → **Privat eintragen (nicht teilen)** | „Nicht geteilt.“ – A sieht nichts | [ ] |
| V1 | A | **Verlauf → Als Bild teilen** | Vorschau, dann Teilen-Menü mit PNG | [ ] |
| V2 | beide | Supabase-Dashboard → **Table Editor → drink_events** ansehen | Nur Name, Icon, Kategorie, Menge in ml und Zeitpunkte – keine Gramm, kein Zucker | [ ] |
| T1 | – | Fire TV (falls vorhanden): installieren, Wasser mit OK eintragen, Verlauf öffnen, Vorlesen testen | Siehe Tabelle in 11.3 | [ ] |
| L1 | B (zum Schluss, optional) | **Einstellungen → Konto löschen** | Abgemeldet; A sieht B nicht mehr als Freund; Bar und Verlauf auf dem iPhone bleiben | [ ] |

---

## 13. Wenn etwas nicht klappt

| Was du siehst | Wahrscheinliche Ursache | Lösung |
|---|---|---|
| Android: „Server noch nicht eingerichtet“ | Die APK wurde ohne `android/app/supabase.properties` gebaut, oder `supabaseUrl`/`supabaseAnonKey` fehlen bzw. die URL beginnt nicht mit `https://` | Schritt 6 prüfen, dann die **neueste** APK (nach dem Commit gebaut) installieren |
| Web-App: „Server noch nicht eingerichtet“ | Die Variablen `SUPABASE_URL`/`SUPABASE_ANON_KEY` fehlten beim Bauen | Variablen setzen (5.3), dann **Actions → Web-App** → rechts **Run workflow** → Branch `main` → **Run workflow** – das baut und veröffentlicht neu |
| `https://q0ochen.github.io/DrinkWithMe/` zeigt „404 – There isn't a GitHub Pages site here“ | Pages-Quelle nicht „GitHub Actions“, Veröffentlichung fehlgeschlagen oder Repository heißt nicht `DrinkWithMe` | 5.1 und 5.4 prüfen; **Actions → Web-App** → Job „Auf GitHub Pages veröffentlichen“ ansehen |
| Google: **„Fehler 400: redirect_uri_mismatch“** | Weiterleitungs-URI im Google-Client stimmt nicht | In 2.3 genau `https://<projekt-referenz>.supabase.co/auth/v1/callback` eintragen (https, kein Schrägstrich am Ende) |
| Google: **„Fehler 401: invalid_client“** | Client-ID oder Secret in Supabase falsch kopiert | 3.1: beide Werte neu einfügen; Secret verloren → neues Secret im Google-Client anlegen |
| Google: **„Zugriff blockiert … hat die Google-Überprüfung nicht abgeschlossen“** | Zustimmungsbildschirm steht noch auf „Testen“ | 2.2, Punkt 4: **App veröffentlichen** („In Produktion“) |
| Browser zeigt `{"…":"Unsupported provider: provider is not enabled"}` | Google-Anbieter in Supabase nicht eingeschaltet | 3.1: **Enable Sign in with Google** an, **Save** |
| Meldung „Signups not allowed for this instance“ | „Allow new users to sign up“ ist aus | 3.1, Punkt 1: wieder einschalten |
| Android: Nach der Google-Anmeldung landet man auf der Web-App-Startseite statt in der App | `drinkwithme://auth-callback` fehlt in den Redirect URLs (Supabase leitet dann zur Site URL) | 3.2: Eintrag genau so hinzufügen |
| Web-App: Nach der Anmeldung nicht angemeldet bzw. auf der Startseite | `https://q0ochen.github.io/DrinkWithMe/auth/callback` fehlt in den Redirect URLs | 3.2 |
| Android: „Die Anmeldung hat nicht geklappt“ / „Die Anmeldung ist abgelaufen“ | Anmeldung abgebrochen, zu lange gewartet oder Fehler bei Supabase | Erneut versuchen; Details im Supabase-Dashboard unter **Logs → Auth** |
| Android: „Für die Anmeldung wird ein Browser benötigt …“ | Kein Browser installiert (typisch auf Fernsehern) | Auf dem Handy: Browser installieren. Auf dem Fernseher: ohne Anmeldung nutzen (11.3) |
| iPhone: Anmeldung endet in einem Safari-Fenster oder Google meldet „disallowed_useragent“ | Eigenheit von Home-Bildschirm-Web-Apps (noch nicht getestet) | Fenster mit **Fertig** schließen, App neu öffnen; Ablauf genau notieren und melden |
| „Per E-Mail hinzufügen“: Die andere Person sieht keine Anfrage | Absichtlich neutral – du siehst bei dir immer „Anfrage an …“, egal was dahinter steckt: Adresse falsch, Person hat sich noch **nie** angemeldet, ihr seid schon befreundet, die Person hat schon eine Anfrage von dir (unter einer anderen Adresse) oder hat sie abgelehnt, oder die Anfrage ist nach 30 Tagen verfallen | Genaue **Google**-Adresse verwenden (die, mit der sich die Person angemeldet hat). Hat sich die Person erst danach zum ersten Mal angemeldet: die Adresse einfach **noch einmal** senden. Sonst die Anfrage **zurückziehen** und neu senden; bei ihr auf **Aktualisieren** tippen. Am einfachsten: Einladungs-Link nutzen |
| „Diese Einladung ist ungültig oder abgelaufen“ | Link älter als 14 Tage, es wurde inzwischen ein neuer Link erstellt (der alte verfällt dann), oder die Person hat dich als Freund entfernt, **nachdem** sie den Link erstellt hatte | Neuen Link schicken lassen |
| „Das ist dein eigener Einladungs-Link“ | Eigenen Link angetippt | Link an die andere Person schicken |
| „Zu viele Versuche in kurzer Zeit“ / „Für heute sind keine weiteren Versuche möglich“ | Schutz-Grenzen: 20 E-Mail-Anfragen pro Tag, 30 angenommene Einladungen pro Tag, 60 Benachrichtigungs-Aufträge pro Stunde; dazu eigene Grenzen von Supabase für Anmeldungen | Warten (bis zu einem Tag). Getränke werden trotzdem geteilt und erscheinen im Feed – nur die Benachrichtigung fällt weg |
| „Heute wurden schon sehr viele Getränke geteilt. Weitere Einträge bleiben vorerst nur auf diesem Gerät.“ | Schutz-Grenze: höchstens 200 geteilte Getränke pro Person in 24 Stunden | Nichts tun – die Einträge sind im eigenen Verlauf gespeichert, nur nicht geteilt. Nach 24 Stunden wird Neues wieder geteilt |
| „Benachrichtigungen sind schon auf fünf Geräten eingerichtet“ | Höchstens 5 Benachrichtigungs-Abos pro Konto | Auf einem alten Gerät abmelden |
| Android: keine Benachrichtigungen | ntfy nicht in Drink With Me ausgewählt, Benachrichtigungen für Drink With Me oder ntfy verboten, Akku-Optimierung, VAPID-Secrets fehlen, oder ein eigener ntfy-Server läuft nicht auf Port 443 | Schritt 9 komplett; Android-Einstellungen → Apps → Drink With Me / ntfy → Benachrichtigungen an; Deploy-Lauf auf Warnung „VAPID-Secrets fehlen“ prüfen (4.2); eigenen ntfy-Server über https auf Port 443 erreichbar machen oder `ntfy.sh` nehmen |
| Web-App: „Benachrichtigungen sind auf dem Server noch nicht eingerichtet.“ | Die GitHub-Variable `VAPID_PUBLIC_KEY` fehlte beim Bauen oder ist kein gültiger Schlüssel | 5.3 prüfen, dann die Web-App neu veröffentlichen (wie bei „Server noch nicht eingerichtet“ oben) |
| iPhone: keine Benachrichtigungen | Nicht über den Home-Bildschirm geöffnet, iOS älter als 16.4, Mitteilungen verboten oder Fokus aktiv | Schritt 10; iPhone-Einstellungen → Mitteilungen → Drink With Me → Mitteilungen erlauben |
| Benachrichtigungen fehlen bei allen | `VAPID_PUBLIC_KEY` ist nicht überall gleich (Supabase-Secret, GitHub-Variable, `supabase.properties`) oder `VAPID_SUBJECT` beginnt nicht mit `mailto:` | Werte vergleichen; nach einer Korrektur Web-App neu veröffentlichen, neue APK bauen und Benachrichtigungen in den Apps aus- und wieder einschalten |
| Benachrichtigungen kommen ohne ntfy erst viel später | So gewollt: Abfrage etwa alle 15 Minuten; Android verzögert im Stromsparmodus | ntfy einrichten (Schritt 9) |
| Apps: „Keine Verbindung zum Server“ / „Der Server ist gerade nicht erreichbar“, obwohl Internet da ist | Supabase-Projekt **pausiert** (nach etwa 7 Tagen ohne Nutzung) | Supabase-Dashboard → Projekt → **Restore project**; ein paar Minuten warten |
| „Der Server ist noch nicht vollständig eingerichtet“ | Edge Function antwortet `server_misconfigured` – meist fehlen VAPID-Secrets | Schritt 4.2, dann „Supabase Deploy“ erneut starten |
| „Supabase Deploy“: Knopf **Run workflow** fehlt | Der Workflow liegt noch nicht auf `main` | Schritt 7.2: Pull Request zusammenführen |
| „Supabase Deploy“ bricht bei „Einstellungen prüfen“ ab | Secret oder Variable fehlt, oder `SUPABASE_PROJECT_REF` ist der Projektname statt der 20-stelligen Referenz | Meldung lesen, Schritt 5.2/5.3 korrigieren |
| „Supabase Deploy“ bricht bei „Mit dem Projekt verbinden“ ab | Datenbank-Passwort falsch | Supabase → **Project Settings → Database → Reset database password**, neues Passwort ins Secret `SUPABASE_DB_PASSWORD`. Sonst die Fehlermeldung kopieren und Claude schicken (ohne Passwörter!) |
| Android: „App nicht installiert“ beim Update | Die alte App ist mit einem anderen Schlüssel signiert (z. B. selbst in Android Studio gebaut) | Alte App deinstallieren (lokaler Verlauf geht dabei verloren), dann neu installieren |
| Android: „App wurde nicht installiert, da das Paket in Konflikt mit einem bestehenden Paket steht“ beim **ersten** Installieren | Auf dem Gerät ist schon eine „Drink With Me“ mit anderer Signatur: selbst in Android Studio gebaut, aus einem anderen Projekt mit demselben Paketnamen oder eine frühe Testversion (vor dem festen Test-Schlüssel) – evtl. im Privaten Bereich oder Arbeitsprofil | Alte „Drink With Me“ deinstallieren (überall nachsehen), dann neu installieren |
| Android: „Die App wurde nicht installiert, da das Paket offenbar ungültig ist“ | Kaputter Download – **oder** eine installierte „Drink With Me“ mit höherer Versionsnummer (z. B. selbst gebaut) | Neu herunterladen (Größe etwa 13 MB, Prüfsumme in den Release-Hinweisen); hilft das nicht: alte App deinstallieren |
| Android: „Beim Parsen des Pakets ist ein Problem aufgetreten“ | Download unvollständig, oder Android älter als 8.0 | Alle alten `DrinkWithMe*.apk` in „Downloads“ löschen, über WLAN neu herunterladen; Android-Version prüfen |
| Android: Play Protect meldet „blockiert“ oder „kennt diese App noch nicht“, danach passiert nichts | Der Hinweis wurde mit „OK“ bzw. „Verstanden“ geschlossen – das bricht die Installation ab | Erneut öffnen, dann **Weitere Details → Trotzdem installieren** (oder **App scannen**) |
| Android: Erlaubnis für unbekannte Apps fehlt, „Installieren“ erscheint nicht | Die Erlaubnis wurde einer anderen App gegeben (Chrome vs. Dateien vs. WhatsApp) oder abgelehnt | Einstellungen → Apps → Spezieller App-Zugriff → **Unbekannte Apps installieren** → die App, mit der du die Datei öffnest → **Dieser Quelle vertrauen** |
| Android: Schalter „Dieser Quelle vertrauen“ lässt sich nicht einschalten | **Erweiterter Schutz** (Android 16+) ist an, verwaltetes Gerät/Arbeitsprofil, Kinderschutz (Family Link) oder ein laufender Anruf mit einer unbekannten Nummer | In den Einstellungen nach „Erweiterter Schutz“ suchen, vorübergehend ausschalten und danach die Erlaubnis **neu** erteilen (sie wird beim Einschalten entzogen); Anruf beenden. Für Entwickler: `adb install DrinkWithMe.apk` funktioniert auch mit Erweitertem Schutz |
| Android: nur „App wurde nicht installiert“ ohne Grund | Verschiedenes (Play Protect, Konflikt, …) | Für Entwickler: am Computer `adb install -r DrinkWithMe.apk` zeigt den genauen Fehlercode |
| Download-Link zeigt „404“ | Alter Link `…/releases/latest/download/app-debug.apk` | Den Link aus 8.1 nehmen (`…/releases/download/testversion/DrinkWithMe.apk`) |
| F-Droid: „Ungültiges Repository“ beim Hinzufügen der Paketquelle, oder Fehler beim Aktualisieren | Direkt nach einer neuen Testversion liefert GitHub die Dateien bis zu 5 Minuten zwischengespeichert aus – kurz passen Index und Signatur nicht zusammen. Oder die Paketquelle ist noch nicht eingerichtet (5.5) | 5 Minuten warten, erneut versuchen. Prüfen: `https://raw.githubusercontent.com/q0ochen/DrinkWithMe/fdroid-repo/fdroid/repo/index-v2.json` muss sich im Browser öffnen |
| F-Droid: „Download fehlgeschlagen“ bei einem Update | F-Droid kennt noch eine ältere Liste; die Paketquelle hält nur die aktuelle und die vorige Testversion | In F-Droid die Paketquellen aktualisieren (Meine Apps → ⋮ → Auf Aktualisierungen prüfen), dann erneut |
| Fire TV: Downloader fehlt unter „Apps unbekannter Herkunft“ | Fehler im Fire-TV-Update (Herbst 2026) oder Vega-OS-Gerät | System aktualisieren (11.2); bei Vega OS geht es nicht |
| Vorlesen bleibt stumm | Keine deutsche Stimme installiert | Die Hilfe **„Vorlesen einrichten“** erscheint in den Einstellungen und auf dem Ereignis-Bildschirm – je nach Fall mit **Deutsche Stimme installieren** oder **SherpaTTS bei F-Droid**. Danach einfach zurück in die App – die Sprachausgabe startet dann neu |
| Mikrofon-Knopf „Sprechen“ fehlt | Keine Spracherkennungs-App auf dem Gerät | Normal auf manchen Geräten; Eintragen per Tipp geht immer |
| Anmeldung klappt plötzlich bei niemandem mehr (nach langer Pause) | Google löscht OAuth-Clients, die lange nicht benutzt wurden | In Google Cloud nachsehen (gelöschte Clients lassen sich eine Zeit lang wiederherstellen), sonst neuen Client anlegen (2.3) und in Supabase eintragen (3.1) |

---

## 14. Anhang

### Kosten und Grenzen

| Dienst | Kosten | Grenzen, die ihr kennen solltet |
|---|---|---|
| Supabase Free | 0 € | Pausiert nach etwa einer Woche ohne Nutzung; Speicher- und Aufruf-Kontingente reichen für eine Freundesgruppe locker |
| Google Cloud (nur Anmeldung) | 0 €, kein Rechnungskonto | Kein Logo hochladen (sonst Prüfung); lange ungenutzte Clients löscht Google |
| GitHub (öffentliches Repository) | 0 € | Artifacts werden nach einiger Zeit gelöscht – für dauerhafte Downloads Releases nutzen |
| GitHub Pages | 0 € | – |
| ntfy (`ntfy.sh`) | 0 € | – |

### Was ist geheim, was nicht?

- **Öffentlich** (darf ins Repository, in die Apps, an Claude): Projekt-URL, Projekt-Referenz, Publishable/anon-Key,
  `VAPID_PUBLIC_KEY`, Google Client-ID, Adresse der Web-App.
- **Geheim** (nur Passwort-Manager, Supabase bzw. GitHub-Secrets): Datenbank-Passwort, Access Token,
  `VAPID_PRIVATE_KEY`, Google Client-Secret, der „secret“/„service_role“-Key von Supabase.
- Der Schutz der Daten hängt **nicht** an geheimen App-Schlüsseln, sondern an den Zugriffsregeln der Datenbank
  (Row Level Security): Jede Person sieht nur, was für sie bestimmt ist.

### Konto auf Wunsch löschen (für den Betreiber)

Wer die App nicht mehr hat und per E-Mail um Löschung bittet: Supabase-Dashboard → **Authentication → Users** →
nach der E-Mail-Adresse suchen → **…** → **Delete user**. Profil, Freundschaften, eigene Anfragen,
Einladungs-Links, geteilte Getränke, Benachrichtigungs-Abos und Zähler werden automatisch mitgelöscht – genau wie
bei „Konto löschen“ in der App. (Anfragen **anderer** an diese Person bleiben beim jeweiligen Absender, nur ohne
Verbindung zum gelöschten Konto, und verfallen nach 30 Tagen.)

### Weiterführend

- [`../supabase/README.md`](../supabase/README.md) – Backend für Entwickler (lokal testen, Schnittstellen)
- [`../web/README.md`](../web/README.md) – Web-App für Entwickler
- [`ARCHITEKTUR.md`](ARCHITEKTUR.md) – verbindlicher technischer Vertrag
- [`DATENSCHUTZ_ENTWURF.md`](DATENSCHUTZ_ENTWURF.md) – Entwurf der Datenschutzerklärung
- [`F-DROID.md`](F-DROID.md) – Weg in den F-Droid-Katalog
