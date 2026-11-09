# Firebase einrichten – Schritt für Schritt

Diese Anleitung richtet den Server für die **Trinkrunden** ein. Danach könnt ihr in der Android-App sehen,
was eure Freunde trinken, und bekommt eine Push-Nachricht, sobald jemand aus eurer Runde etwas einträgt.

Sie ist für Menschen geschrieben, die **nicht programmieren**. Ihr müsst nichts installieren: Alles passiert
im Browser (Firebase-Konsole, Google-Cloud-Konsole, GitHub). Den eigentlichen „Upload“ des Servers erledigt
GitHub Actions für euch.

> **Wichtig vorab für iPhone-Nutzer:** Die iPhone-App bleibt vorerst **nur lokal** – ohne Trinkrunden.
> Du kannst den Server trotzdem komplett einrichten (das ist reine Browser-Arbeit). **Testen** können die
> Trinkrunden aber nur Android-Handys, und zwar am besten **zwei** (eins erstellt die Runde, das andere tritt bei).

---

## Inhalt

0. [Bevor es losgeht](#0-bevor-es-losgeht)
1. [Firebase-Projekt erstellen](#1-firebase-projekt-erstellen)
2. [Auf den Blaze-Tarif umstellen und eine Budgetwarnung setzen](#2-auf-den-blaze-tarif-umstellen-und-eine-budgetwarnung-setzen)
3. [Anmeldung mit Google einschalten](#3-anmeldung-mit-google-einschalten)
4. [Datenbank anlegen – in Frankfurt!](#4-datenbank-anlegen--in-frankfurt)
5. [Android-App anmelden und google-services.json herunterladen](#5-android-app-anmelden-und-google-servicesjson-herunterladen)
6. [google-services.json ins Repository bringen](#6-google-servicesjson-ins-repository-bringen)
7. [Dienstkonto („Roboter-Zugang“) für GitHub anlegen](#7-dienstkonto-roboter-zugang-für-github-anlegen)
8. [Secret und Variable in GitHub eintragen](#8-secret-und-variable-in-github-eintragen)
9. [Server bereitstellen: Workflow „Firebase Deploy“ starten](#9-server-bereitstellen-workflow-firebase-deploy-starten)
10. [App installieren und ausprobieren](#10-app-installieren-und-ausprobieren)
11. [Wenn etwas nicht klappt](#11-wenn-etwas-nicht-klappt)
12. [Anhang: Kosten, Sicherheit, Konto löschen](#12-anhang)

---

## 0. Bevor es losgeht

### Was ihr braucht

- [ ] Ein **Google-Konto**. Am besten ein privates `@gmail.com`-Konto (Firmen- oder Schulkonten sperren oft
      genau die Dinge, die wir in Schritt 7 brauchen).
- [ ] Eine **Kreditkarte** (oder eine andere Zahlungsart, die Google akzeptiert) – nötig für den Blaze-Tarif.
      Bei einer kleinen Freundesgruppe kostet das normalerweise **nichts**, mehr dazu in Schritt 2.
- [ ] **Besitzer-Rechte** (oder Admin) am GitHub-Repository `Drink-With-Me` – nur dann darf man Secrets anlegen.
- [ ] Etwa **eine Stunde Zeit**.
- [ ] Zum Testen: **zwei Android-Handys** mit Google-Konto (z. B. deins und das eines Freundes).

### Ehrlich gesagt: Ein Computer macht es viel leichter

Die Firebase-Konsole und die Google-Cloud-Konsole sind für große Bildschirme gebaut. Grundsätzlich gehen sie
auch im Handy-Browser (in Safari über „aA“ → **„Desktop-Website anfordern“**, in Chrome über das Menü
→ **„Desktopwebsite“**), aber es ist fummelig.

| Schritt | Am Handy möglich? |
|---|---|
| 1–5 (Firebase-Konsole) | ja, aber unbequem |
| 6 (Datei ins Repository) | ja – am einfachsten: Datei an Claude im Chat schicken |
| **7–8 (Schlüssel erstellen und in GitHub einfügen)** | **besser am Computer** – man muss den Inhalt einer Datei komplett kopieren |
| 9 (Workflow starten) | ja, problemlos |
| 10 (App installieren) | nur auf Android |

Ein geliehener Laptop, ein Familien-PC oder ein Chromebook reicht völlig – installieren müsst ihr nichts,
ein Browser genügt.

### Spickzettel – diese Werte braucht ihr unterwegs

| Was | Wert |
|---|---|
| Paketname der Android-App | `com.drinkwithme.app` |
| SHA-1-Fingerabdruck | `F9:FE:D7:35:CC:E7:E0:6C:CE:04:67:D0:E4:52:0E:52:8F:3C:34:0A` |
| SHA-256-Fingerabdruck | `BE:67:02:0F:5D:85:35:B0:17:3B:3B:F3:CB:70:4F:65:87:CB:56:E2:C4:1F:B0:EB:79:59:41:15:13:B1:1C:85` |
| Standort der Datenbank | `europe-west3` (Frankfurt) |
| Branch im Repository | `claude/drink-with-me-app-s7ifqu` |
| Ablageort der Konfigurationsdatei | `android/app/google-services.json` |
| GitHub-Secret | `FIREBASE_SERVICE_ACCOUNT` |
| GitHub-Variable | `FIREBASE_PROJECT_ID` |
| Workflow zum Bereitstellen | „Firebase Deploy“ (`.github/workflows/firebase-deploy.yml`) |

Die beiden Fingerabdrücke gehören zum festen Test-Schlüssel der App (`android/app/debug.keystore`). Mit ihm
sind alle APKs aus GitHub Actions signiert. Sie sind **kein Geheimnis**.

> **Hinweis zu den Menünamen:** Google ändert die Konsolen ab und zu. Wir nennen die englischen Bezeichnungen
> und, wo es eine gibt, die deutsche in Klammern. Wenn etwas anders heißt: In der Firebase-Konsole links das
> Menü aufklappen oder oben die Suche benutzen – die Produkte heißen überall gleich („Authentication“,
> „Firestore“, „Functions“).

---

## 1. Firebase-Projekt erstellen

1. Öffne <https://console.firebase.google.com> und melde dich mit deinem Google-Konto an.
2. Klicke auf **„Create a project“** („Projekt erstellen“; manchmal auch „Add project“ / „Projekt hinzufügen“).
3. **Projektname:** z. B. `Drink With Me`. Darunter schlägt Firebase eine **Projekt-ID** vor,
   z. B. `drink-with-me-1a2b3`.
   **Schreib dir die Projekt-ID auf** – du brauchst sie in Schritt 7 und 8. Sie lässt sich später nicht ändern.
4. Bedingungen bestätigen → **„Continue“** („Weiter“).
5. Fragt die Konsole nach **KI-Unterstützung / Gemini in Firebase**: brauchen wir nicht, kann aus bleiben.
6. **Google Analytics:** Schalter **ausschalten** – die App verwendet keine Statistik und kein Tracking.
7. **„Create project“** („Projekt erstellen“) → kurz warten → **„Continue“**.

Du landest auf der **Projektübersicht** (Project Overview).

> **Tipp für zwei Leute:** Damit auch dein Freund alles sieht, kannst du ihn einladen: Zahnrad oben links
> → **„Project settings“** („Projekteinstellungen“) → Reiter **„Users and permissions“**
> („Nutzer und Berechtigungen“) → **„Add member“** („Mitglied hinzufügen“) → seine E-Mail-Adresse,
> Rolle **„Owner“** („Inhaber“).

---

## 2. Auf den Blaze-Tarif umstellen und eine Budgetwarnung setzen

### Warum überhaupt ein Tarif mit Kreditkarte?

Die Trinkrunden brauchen kleine Server-Programme („Cloud Functions“): Runde erstellen, beitreten, verlassen
und die Push-Nachricht verschicken. Cloud Functions gibt es **nur im Blaze-Tarif** („pay as you go“ –
bezahlt wird nur, was verbraucht wird). Dafür verlangt Google eine Zahlungsmethode.

### Was kostet das wirklich?

- Auch im Blaze-Tarif gibt es **kostenlose Freimengen**. Für die Datenbank sind das z. B. pro Tag
  50.000 Lesevorgänge, 20.000 Schreibvorgänge und 1 GB Speicher; die Server-Funktionen haben ebenfalls ein
  großzügiges monatliches Freikontingent. Push-Nachrichten und die Google-Anmeldung kosten nichts.
  Aktuelle Zahlen: <https://firebase.google.com/pricing>
- Eine **kleine Freundesgruppe bleibt normalerweise komplett innerhalb der Freimengen**.
- Was trotzdem anfallen kann, sind **Cent-Beträge**, z. B. für den Speicherplatz der Programmpakete der
  Functions. Der Deploy-Workflow richtet deshalb automatisch eine Aufräumregel ein, die alte Pakete löscht.
- Zur Sicherheit begrenzt der Code die Functions auf höchstens 10 gleichzeitige Instanzen.
- **Ehrlicher Hinweis:** Ein Budget ist eine **Warnung per E-Mail, keine Kostenbremse**. Google stoppt bei
  Erreichen des Betrags nichts automatisch. Wie man im Notfall alles abschaltet, steht im
  [Anhang](#notbremse-alles-kostenpflichtige-abschalten).

### So geht's

1. In der Firebase-Konsole unten links steht dein aktueller Tarif **„Spark“**. Klicke daneben auf
   **„Upgrade“** und wähle **„Blaze“**.
   (Alternativ: Zahnrad → **„Usage and billing“** („Nutzung und Abrechnung“) → **„Details & settings“**
   → **„Modify plan“** („Tarif ändern“).)
2. Wähle ein **Cloud-Rechnungskonto** (Cloud Billing account) oder lege ein neues an: Land, Name,
   Zahlungsart (Kreditkarte) eingeben.
   - Bietet Google dabei ein **kostenloses Startguthaben** (Free Trial, z. B. 300 $ für 90 Tage) an, ist das
     in Ordnung. **Aber:** Wenn die Testphase endet, hält Google alles an, bis das Rechnungskonto in ein
     normales („bezahltes“) Konto umgewandelt wird. Das geht in der Google-Cloud-Konsole unter **„Billing“**
     („Abrechnung“) mit einem Klick. Sonst funktionieren die Trinkrunden danach nicht mehr.
3. Die Konsole bietet an, eine **Budgetwarnung** festzulegen („budget alert“). Trage **1 €** ein
   (oder einen Betrag, ab dem ihr Bescheid wissen wollt).
4. Bestätigen (**„Link“** / „Verknüpfen“, „Continue“ / „Weiter“). Unten links steht jetzt **„Blaze“**.

**Falls keine Budgetwarnung angeboten wurde**, leg sie selbst an:

1. Öffne <https://console.cloud.google.com/billing> und wähle dein Rechnungskonto.
2. Links **„Budgets & alerts“** („Budgets und Benachrichtigungen“) → **„Create budget“** („Budget erstellen“).
3. Name z. B. `Drink With Me`, bei *Projects* dein Firebase-Projekt, bei *Services* **„All services“**.
4. Betrag: **„Specified amount“** → `1` (Euro).
5. Schwellenwerte so lassen (z. B. 50 %, 90 %, 100 %), E-Mail-Benachrichtigung an die Abrechnungs-Admins
   anhaken → **„Finish“**.

---

## 3. Anmeldung mit Google einschalten

> **Reihenfolge ist wichtig:** Erst diesen Schritt, **dann** in Schritt 5 die Datei `google-services.json`
> herunterladen. Erst wenn die Google-Anmeldung eingeschaltet ist, steht in der Datei alles, was die App für
> die Anmeldung braucht.

1. Linkes Menü: **„Security“** → **„Authentication“** (in älteren Versionen unter „Build“ / „Entwickeln“).
2. Falls angezeigt: **„Get started“** („Jetzt starten“).
3. Reiter **„Sign-in method“** („Anmeldemethode“) → in der Liste der Anbieter **„Google“** wählen.
4. Schalter **„Enable“** („Aktivieren“) einschalten.
5. **„Public-facing name for project“** („Öffentlicher Name“): z. B. `Drink With Me`.
6. **„Support email for project“** („Support-E-Mail-Adresse“): deine E-Mail-Adresse auswählen.
   **Pflichtfeld** – ohne Support-E-Mail klappt die Google-Anmeldung nicht.
7. **„Save“** („Speichern“).

---

## 4. Datenbank anlegen – in Frankfurt!

> **Achtung, das ist der wichtigste Schritt:** Der **Standort der Datenbank lässt sich später nicht mehr ändern.**
> Er muss **`europe-west3` (Frankfurt)** sein – dort laufen auch die Server-Funktionen. Mit einem anderen
> Standort schlägt das Bereitstellen in Schritt 9 fehl.
>
> **Und:** Diesen Schritt **unbedingt vor Schritt 9** erledigen. Findet der Deploy-Workflow keine Datenbank,
> legt er selbst eine an – und zwar in den **USA** (`nam5`). Dann müsste man sie löschen und neu anlegen.

1. Linkes Menü: **„Databases & Storage“** → **„Firestore“** (früher „Firestore Database“ unter „Build“).
2. **„Create database“** („Datenbank erstellen“).
3. Fragt die Konsole nach der **Edition**: **„Standard edition“** wählen (nicht „Enterprise“).
4. **Database ID:** so lassen, wie vorgeschlagen: **`(default)`**.
5. **Location** („Standort“): **`europe-west3 (Frankfurt)`** auswählen.
   Nicht „eur3 (Europe)“ und nicht „nam5 (United States)“ – genau **europe-west3**.
6. Sicherheitsregeln: **„Start in production mode“** („Im Produktionsmodus starten“).
   Damit ist die Datenbank erst einmal komplett verschlossen. Die richtigen Regeln spielt Schritt 9 ein.
7. **„Create“** („Erstellen“). Nach kurzer Zeit siehst du eine leere Datenbank.

**Kontrolle:** Öffne (Projekt-ID einsetzen)
`https://console.cloud.google.com/firestore/databases?project=DEINE-PROJEKT-ID`.
In der Liste steht die Datenbank `(default)`; in der Spalte **„Location“** („Standort“) muss
**`europe-west3`** stehen.

---

## 5. Android-App anmelden und google-services.json herunterladen

### 5.1 App hinzufügen

1. Zurück zur **Projektübersicht** (ganz oben links „Project Overview“).
2. **„Add app“** („App hinzufügen“) → das **Android-Symbol** wählen.
3. **„Android package name“** („Android-Paketname“): genau
   ```
   com.drinkwithme.app
   ```
   (Kann später nicht geändert werden – bitte genau so, ohne Leerzeichen.)
4. **„App nickname“** (optional): z. B. `Drink With Me Android`.
5. **„Debug signing certificate SHA-1“** („SHA-1 des Signaturzertifikats für die Fehlerbehebung“):
   ```
   F9:FE:D7:35:CC:E7:E0:6C:CE:04:67:D0:E4:52:0E:52:8F:3C:34:0A
   ```
6. **„Register app“** („App registrieren“).
7. Die Konsole bietet jetzt `google-services.json` zum Download an. Du kannst sie hier schon laden – wir laden
   sie in 5.3 aber **noch einmal**, nachdem auch der SHA-256-Fingerabdruck eingetragen ist.
8. Die weiteren Seiten („Add Firebase SDK“ usw.) einfach mit **„Next“** („Weiter“) überspringen und mit
   **„Continue to console“** („Weiter zur Konsole“) abschließen. Den Code dafür gibt es bereits.

### 5.2 SHA-256-Fingerabdruck ergänzen

1. Zahnrad oben links → **„Project settings“** („Projekteinstellungen“) → Reiter **„General“** („Allgemein“).
2. Ganz unten bei **„Your apps“** („Meine Apps“) die Android-App `com.drinkwithme.app` auswählen.
3. Bei **„SHA certificate fingerprints“** („SHA-Zertifikat-Fingerabdrücke“) steht schon der SHA-1-Wert.
   Klicke auf **„Add fingerprint“** („Fingerabdruck hinzufügen“) und füge ein:
   ```
   BE:67:02:0F:5D:85:35:B0:17:3B:3B:F3:CB:70:4F:65:87:CB:56:E2:C4:1F:B0:EB:79:59:41:15:13:B1:1C:85
   ```
4. **„Save“** („Speichern“). Jetzt stehen dort **beide** Werte (SHA-1 und SHA-256).

### 5.3 Aktuelle google-services.json herunterladen

1. Auf derselben Seite (bei der Android-App) auf **„google-services.json“** klicken – die Datei wird
   heruntergeladen.
2. **Dateiname prüfen:** Er muss genau `google-services.json` lauten. Hast du sie mehrfach geladen, heißt sie
   evtl. `google-services (1).json` – dann umbenennen oder die alte Datei löschen.

> **Ist das geheim?** Nein. Laut Firebase enthält diese Datei eindeutige, aber **nicht geheime** Kennungen
> eures Projekts. Geschützt werden die Daten durch die Anmeldung und die Sicherheitsregeln. Das Repository
> ist außerdem privat. Trotzdem gilt: nicht öffentlich herumschicken, wenn es nicht nötig ist.

---

## 6. google-services.json ins Repository bringen

Die Datei muss im Repository unter **`android/app/google-services.json`** liegen, im Branch
**`claude/drink-with-me-app-s7ifqu`**. Erst dann baut GitHub eine App, die mit eurem Server spricht.
Ohne die Datei läuft die App wie bisher nur lokal und zeigt bei den Trinkrunden
„Server noch nicht eingerichtet“.

### Weg A – am einfachsten: an Claude schicken

Schick Claude im Chat die Datei (als Anhang) oder ihren kompletten Inhalt und schreib dazu:
„Bitte lege das als `android/app/google-services.json` in den Branch.“ Claude legt sie an der richtigen
Stelle ab.

### Weg B – selbst über die GitHub-Webseite hochladen

1. Öffne das Repository auf <https://github.com> (am Handy evtl. „Desktop-Website anfordern“).
2. Oben links über der Dateiliste auf den Branch-Schalter klicken (zeigt meist `main`) und
   **`claude/drink-with-me-app-s7ifqu`** auswählen.
3. In den Ordner **`android`** und dann **`app`** wechseln.
4. **„Add file“** → **„Upload files“** → die Datei `google-services.json` auswählen oder hineinziehen.
5. Unten **„Commit directly to the `claude/drink-with-me-app-s7ifqu` branch“** auswählen
   (nicht „Create a new branch“) → **„Commit changes“**.
6. Kontrolle: Im Ordner `android/app` liegt jetzt `google-services.json` (genau so geschrieben).

**Danach** baut GitHub automatisch eine neue App-Version. Das dauert ein paar Minuten:
Reiter **„Actions“** → Workflow **„Android“** → der oberste Lauf muss einen **grünen Haken** bekommen.
Installieren werdet ihr sie in Schritt 10.

---

## 7. Dienstkonto („Roboter-Zugang“) für GitHub anlegen

GitHub soll später Sicherheitsregeln und Server-Funktionen in euer Projekt hochladen. Dafür braucht GitHub
einen eigenen Zugang – ein **Dienstkonto** (Service Account) mit einem **Schlüssel** (eine JSON-Datei).

> **Der Schlüssel ist geheim!** Wer ihn hat, kann euer Firebase-Projekt verändern und Kosten verursachen.
> - **Nicht** ins Repository hochladen.
> - **Nicht** an Claude oder sonst jemanden im Chat schicken.
> - Nur in Schritt 8 bei GitHub als **Secret** einfügen und die Datei danach löschen.

### 7.1 Dienstkonto erstellen

1. Öffne (Projekt-ID einsetzen):
   `https://console.cloud.google.com/iam-admin/serviceaccounts?project=DEINE-PROJEKT-ID`
   Das ist die Google-Cloud-Konsole – sie gehört zum selben Projekt wie Firebase. Oben in der Leiste muss
   euer Projekt ausgewählt sein.
2. **„+ Create service account“** („+ Dienstkonto erstellen“).
3. **Name:** `github-deploy`. Beschreibung z. B. „Deploy aus GitHub Actions“.
   → **„Create and continue“** („Erstellen und fortfahren“).
4. Im Abschnitt **„Permissions“** („Berechtigungen“, manchmal „Grant this service account access to project“)
   die folgenden **fünf Rollen** hinzufügen. Jeweils ins Feld **„Select a role“** („Rolle auswählen“) den
   Namen tippen, auswählen und mit **„+ Add another role“** („Weitere Rolle hinzufügen“) die nächste anlegen:

   | Rolle (englisch) | technischer Name | Wofür |
   |---|---|---|
   | **Firebase Admin** | `roles/firebase.admin` | Functions anlegen und aktualisieren, Sicherheitsregeln und Indizes der Datenbank einspielen |
   | **Service Account User** (Dienstkontonutzer) | `roles/iam.serviceAccountUser` | Die Functions laufen unter einem Google-eigenen Konto – GitHub muss sie „in dessen Namen“ anlegen dürfen |
   | **Service Usage Admin** | `roles/serviceusage.serviceUsageAdmin` | Beim ersten Mal schaltet der Workflow die nötigen Google-Dienste selbst ein (Cloud Functions, Cloud Build, Artifact Registry, Cloud Run, Eventarc, Pub/Sub) |
   | **Project IAM Admin** (Projekt-IAM-Administrator) | `roles/resourcemanager.projectIamAdmin` | Beim ersten Mal gibt der Workflow Googles internen Konten die Rechte, die der Datenbank-Trigger für Push-Nachrichten braucht |
   | **Artifact Registry Administrator** | `roles/artifactregistry.admin` | Richtet die Aufräumregel ein, die alte Programmpakete löscht (spart Cent-Beträge) |

   > Rollen wie „Cloud Functions Admin“, „Firebase Rules Admin“ oder „Cloud Datastore Index Admin“, die man
   > anderswo liest, sind in **Firebase Admin** schon enthalten.

5. **„Continue“** („Weiter“) → den letzten, optionalen Abschnitt überspringen → **„Done“** („Fertig“).

**Rollen vergessen oder Abschnitt nicht angezeigt?** Dann über die IAM-Seite nachtragen:
`https://console.cloud.google.com/iam-admin/iam?project=DEINE-PROJEKT-ID` → **„Grant access“**
(„Zugriff gewähren“) → bei *New principals* die Adresse
`github-deploy@DEINE-PROJEKT-ID.iam.gserviceaccount.com` eintragen → Rollen auswählen → **„Save“**.

### 7.2 Schlüssel (JSON) erstellen

1. In der Liste der Dienstkonten auf **`github-deploy@…`** klicken.
2. Reiter **„Keys“** („Schlüssel“) → **„Add key“** („Schlüssel hinzufügen“)
   → **„Create new key“** („Neuen Schlüssel erstellen“).
3. Typ **„JSON“** → **„Create“** („Erstellen“). Die Datei wird heruntergeladen
   (Name ähnlich wie `drink-with-me-1a2b3-0123456789ab.json`). Das ist die **einzige** Kopie –
   Google kann sie nicht erneut anzeigen. Geht sie verloren, einfach einen neuen Schlüssel erstellen.

> **Meldung „Service account key creation is disabled“?** Dann gehört das Projekt zu einer Organisation
> (Firma, Schule, Google Workspace), die Schlüssel verbietet. Bei privaten `@gmail.com`-Konten gibt es keine
> Organisation und diese Sperre normalerweise nicht. Lösung: Projekt mit einem privaten Google-Konto anlegen
> oder den Admin der Organisation fragen.

---

## 8. Secret und Variable in GitHub eintragen

Das geht nur mit Besitzer-/Admin-Rechten am Repository.

### 8.1 Secret `FIREBASE_SERVICE_ACCOUNT` (der Schlüssel)

1. Die JSON-Datei aus Schritt 7.2 mit einem **Texteditor** öffnen (Windows: Editor/Notepad,
   Mac: TextEdit, Chromebook: Text). Sie beginnt mit `{` und endet mit `}`.
2. **Alles markieren und kopieren** (Strg+A, Strg+C bzw. ⌘A, ⌘C) – von der ersten `{` bis zur letzten `}`.
3. Auf GitHub im Repository: **„Settings“** → links **„Secrets and variables“** → **„Actions“**.
4. Reiter **„Secrets“** → **„New repository secret“**.
5. **Name:** genau `FIREBASE_SERVICE_ACCOUNT`
6. **Secret:** den kopierten Inhalt einfügen – unverändert, ohne etwas hinzuzufügen oder wegzulassen.
7. **„Add secret“**.
8. Die heruntergeladene Schlüsseldatei danach **löschen** (auch aus dem Papierkorb) – GitHub hat sie jetzt
   sicher gespeichert. Den Inhalt eines Secrets kann man später nicht mehr ansehen, nur ersetzen.

### 8.2 Variable `FIREBASE_PROJECT_ID` (die Projekt-ID)

1. Gleiche Seite, Reiter **„Variables“** → **„New repository variable“**.
2. **Name:** genau `FIREBASE_PROJECT_ID`
3. **Value:** die **Projekt-ID** aus Schritt 1, z. B. `drink-with-me-1a2b3`
   (klein geschrieben, mit Bindestrichen – **nicht** der Projektname „Drink With Me“).
   Nachschauen kannst du sie in der Firebase-Konsole unter Zahnrad → „Project settings“ → „Project ID“.
4. **„Add variable“**.

---

## 9. Server bereitstellen: Workflow „Firebase Deploy“ starten

Jetzt lädt GitHub die Sicherheitsregeln und die Server-Funktionen in euer Projekt.

### 9.1 Ist der Workflow zu sehen?

1. Auf GitHub im Repository den Reiter **„Actions“** öffnen.
2. Links in der Liste muss **„Firebase Deploy“** stehen.

**Er fehlt?** GitHub zeigt manuell startbare Workflows erst an, wenn sie auch im Haupt-Branch **`main`**
liegen. Die Trinkrunden liegen aber noch im Branch `claude/drink-with-me-app-s7ifqu`. Dann:

- **Entweder** Claude bitten: „Bitte erstelle einen Pull Request von `claude/drink-with-me-app-s7ifqu` nach
  `main`.“ Danach musst du nur noch auf GitHub **„Merge pull request“** → **„Confirm merge“** drücken.
- **Oder** selbst: Reiter **„Pull requests“** → **„New pull request“** → *base:* `main`,
  *compare:* `claude/drink-with-me-app-s7ifqu` → **„Create pull request“** → **„Merge pull request“**
  → **„Confirm merge“**.
- **Wichtig:** Danach **nicht** auf „Delete branch“ klicken – Claude arbeitet in diesem Branch weiter.

### 9.2 Workflow starten

1. **„Actions“** → links **„Firebase Deploy“** anklicken.
2. Rechts auf **„Run workflow“** klicken.
3. Bei **„Use workflow from“** den Branch **`claude/drink-with-me-app-s7ifqu`** wählen (dort ist immer der
   neueste Stand; direkt nach einem Merge geht auch `main`).
4. Grünen Knopf **„Run workflow“** drücken.
5. Nach ein paar Sekunden erscheint ein neuer Lauf. Der **erste** Deploy dauert etwa **5–15 Minuten**, weil
   Google im Hintergrund noch Dienste einschaltet. Spätere Deploys sind schneller.

### 9.3 Hat es geklappt?

- Der Lauf hat einen **grünen Haken**.
- Firebase-Konsole → **„Hosting & Serverless“** → **„Functions“** (früher unter „Build“): Dort stehen vier
  Funktionen in der Region `europe-west3`: `createGroup`, `joinGroup`, `leaveGroup` und `onGroupDrinkCreated`.
- Firebase-Konsole → **Firestore** → Reiter **„Rules“** („Regeln“): Dort stehen jetzt lange Regeln statt
  der kurzen Standardregel.

**Roter Lauf?** Draufklicken → auf den roten Schritt klicken → die Fehlermeldung steht meist ganz unten.
Häufige Ursachen und Lösungen stehen in [Abschnitt 11](#11-wenn-etwas-nicht-klappt). Besonders beim
**allerersten** Mal hilft oft: **5–10 Minuten warten und den Workflow noch einmal starten** (oben rechts
„Re-run jobs“) – Google braucht manchmal etwas, bis neue Berechtigungen überall angekommen sind.

> Den Deploy müsst ihr nur wiederholen, wenn sich etwas im Ordner `firebase/` ändert (Claude sagt dann
> Bescheid). Für neue App-Versionen ist er nicht nötig.

---

## 10. App installieren und ausprobieren

### 10.1 APK herunterladen und installieren (Android)

1. Am Android-Handy in Chrome GitHub öffnen und anmelden (das Repository ist privat).
2. **„Actions“** → links **„Android“** → den obersten Lauf mit **grünem Haken** öffnen. Er muss **nach**
   dem Hochladen von `google-services.json` (Schritt 6) gestartet sein.
3. Ganz unten bei **„Artifacts“** auf **`DrinkWithMe-android-apk`** tippen. Es wird eine ZIP-Datei geladen.
4. In der App **„Dateien“** („Files“) → **Downloads** → die ZIP-Datei antippen → **„Entpacken“**.
5. Die Datei `app-debug.apk` antippen. Android fragt, ob Chrome bzw. „Dateien“ Apps installieren darf →
   **erlauben** → **„Installieren“**. Warnt Google Play Protect vor einer unbekannten App:
   **„Trotzdem installieren“**.
6. War die App schon installiert, wird sie einfach aktualisiert – eure gespeicherten Getränke bleiben erhalten.

**Für Freunde ohne GitHub-Zugang:** Schick ihnen einfach die Datei `app-debug.apk` (z. B. per Messenger oder
Google Drive). Sie müssen dann nur Schritt 5 und 6 machen.

### 10.2 Ausprobieren – mit zwei Handys (A und B)

| # | Wer | Was tun | Was sollte passieren |
|---|---|---|---|
| 1 | A und B | App öffnen → unten **„Runden“** → **„Mit Google anmelden“** → Konto wählen | Oben steht „Angemeldet als …“ |
| 2 | A und B | Android fragt nach **Benachrichtigungen** | **„Zulassen“** – sonst kommen keine Push-Nachrichten |
| 3 | A | Bei „Runden“ **„Erstellen“** → Name, z. B. `Stammtisch` → **„Erstellen“** | Meldung „Runde „Stammtisch“ erstellt …“ mit einem 6-stelligen Code |
| 4 | A | **„Code teilen“** (z. B. per WhatsApp) oder **„Code kopieren“** | B bekommt den Code |
| 5 | B | Bei „Runden“ **„Beitreten“** → Code eingeben → **„Beitreten“** | „Willkommen in der Runde „Stammtisch“!“ – beide sehen 2 Mitglieder |
| 6 | B | Unten **„Meine Getränke“** → **„Getränk“** → z. B. Cappuccino → Schalter „Privat“ **aus** lassen → **„Speichern“** | – |
| 7 | A | – | Push-Nachricht „*B* trinkt gerade“ mit Getränk, Menge und Rundenname; im Reiter **„Freunde“** erscheint das Getränk |
| 8 | A | Selbst ein Getränk eintragen | B bekommt die Push-Nachricht (man selbst bekommt keine für eigene Getränke) |
| 9 | B | Ein Getränk mit Schalter **„Privat (nicht mit Freunden teilen)“** eintragen | Keine Push-Nachricht, nicht im Feed von A |
| 10 | B | Ein geteiltes Getränk in „Meine Getränke“ löschen | Es verschwindet auch bei A im Reiter „Freunde“ |

Wenn das alles klappt: **Herzlichen Glückwunsch – die Trinkrunden laufen!**

Gut zu wissen:
- Geteilt werden nur Getränke, die man **einträgt, während man angemeldet und in einer Runde ist**. Ältere
  Getränke werden nicht nachträglich geteilt.
- Ein Getränk wird automatisch in **allen** eigenen Runden geteilt (außer es ist „Privat“).
- Ohne Internet wird das Getränk trotzdem lokal gespeichert.

---

## 11. Wenn etwas nicht klappt

### Deploy (Schritt 9) schlägt fehl

| Meldung (Ausschnitt) | Ursache | Lösung |
|---|---|---|
| `Secret FIREBASE_SERVICE_ACCOUNT fehlt` / `Variable FIREBASE_PROJECT_ID fehlt` | Schritt 8 fehlt oder Name falsch geschrieben | Namen genau prüfen (Großbuchstaben, Unterstriche) |
| `FIREBASE_PROJECT_ID ungültig` | Projektname statt Projekt-ID eingetragen | Projekt-ID eintragen, z. B. `drink-with-me-1a2b3` |
| `Failed to get Firebase project` / `does not have permission` | Projekt-ID falsch, oder der Schlüssel gehört zu einem anderen Projekt | Projekt-ID prüfen; Schlüssel im richtigen Projekt neu erstellen (Schritt 7) |
| `must be on the Blaze (pay-as-you-go) plan` | Tarif ist noch „Spark“ | Schritt 2 |
| `iam.serviceAccounts.ActAs` / `Service Account User` | Rolle **Service Account User** fehlt | Rolle nachtragen (Schritt 7.1, „Rollen vergessen?“) |
| `serviceusage.services.use` / `Permissions denied enabling …` | Rolle **Service Usage Admin** fehlt | Rolle nachtragen, Workflow neu starten |
| `We failed to modify the IAM policy for the project` | Rolle **Project IAM Admin** fehlt | Rolle nachtragen, Workflow neu starten |
| `Permission denied while using the Eventarc Service Agent` | Beim ersten Mal sind Googles Berechtigungen noch nicht überall angekommen | **5–10 Minuten warten**, Workflow neu starten |
| `A firestore trigger location must match the firestore database region` | Datenbank steht nicht in `europe-west3` | Siehe unten „Datenbank im falschen Standort“ |
| `missing permission on the build service account` | Googles Build-Konto hat keine Rechte (kommt v. a. bei Firmen-/Schulkonten vor) | IAM-Seite → Konto `PROJEKTNUMMER-compute@developer.gserviceaccount.com` → Rolle **Cloud Build Service Account** (`roles/cloudbuild.builds.builder`) hinzufügen. Die Projektnummer steht in den Projekteinstellungen. |
| Warnung `could not set up cleanup policy` (Lauf trotzdem grün) | Rolle **Artifact Registry Administrator** fehlt | Nicht schlimm; Rolle nachtragen, damit alte Pakete gelöscht werden |

**Datenbank im falschen Standort:** Solange noch keine wichtigen Daten drin sind, ist das schnell behoben:
`https://console.cloud.google.com/firestore/databases?project=DEINE-PROJEKT-ID` öffnen → Datenbank
`(default)` anklicken → **„Delete database“** („Datenbank löschen“) → bestätigen. Ein paar Minuten warten,
Schritt 4 mit `europe-west3` wiederholen und dann den Workflow erneut starten.

### Anmeldung in der App klappt nicht

| Was du siehst | Lösung |
|---|---|
| „Server noch nicht eingerichtet – siehe docs/FIREBASE_EINRICHTUNG.md“ | Die installierte App wurde **ohne** `google-services.json` gebaut. Prüfen, ob die Datei genau unter `android/app/google-services.json` im Branch liegt, auf den neuen grünen „Android“-Lauf warten und **diese** APK installieren (Schritt 10.1). |
| „Die Google-Anmeldung ist noch nicht eingerichtet (default_web_client_id fehlt)“ | `google-services.json` wurde **vor** Schritt 3 heruntergeladen. Google-Anmeldung einschalten, Datei neu herunterladen (Schritt 5.3), ersetzen (Schritt 6), neue APK installieren. |
| „Die Google-Anmeldung ist fehlgeschlagen“ | Meist fehlt der **SHA-1-Fingerabdruck** oder er ist falsch, der Paketname stimmt nicht, oder die Google-Anmeldung ist nicht aktiviert bzw. ohne **Support-E-Mail** gespeichert. Schritt 3 und 5.2 genau prüfen, 5 Minuten warten, erneut versuchen. Hilft das nicht: `google-services.json` neu herunterladen und ersetzen. |
| „Auf diesem Gerät ist kein Google-Konto verfügbar …“ | Android-Einstellungen → Konten → Google-Konto hinzufügen. |
| Anmeldung bricht sofort ab, Handy ohne Google-Dienste (z. B. neuere Huawei-Geräte) | Die Anmeldung braucht die Google-Play-Dienste – auf solchen Geräten geht es leider nicht. |
| Google zeigt „Zugriff blockiert“ / „App wird getestet“ (selten) | Google-Cloud-Konsole → **„Google Auth Platform“** (früher „OAuth-Zustimmungsbildschirm“) → **„Audience“** („Zielgruppe“): Status auf **„In production“** stellen oder die Freunde als Testnutzer eintragen. |

### Runden und Feed

| Was du siehst | Lösung |
|---|---|
| „Die Server-Funktion wurde nicht gefunden. Sind die Cloud Functions bereitgestellt?“ | Schritt 9 ausführen bzw. prüfen, ob er grün war. |
| „Keine Leserechte für …“ / Runden oder Feed laden nicht | Die Sicherheitsregeln sind noch nicht eingespielt → Schritt 9. |
| „Zu diesem Code gibt es keine Runde.“ | Code vertippt? Er hat 6 Zeichen; Groß-/Kleinschreibung und Leerzeichen sind egal. Oder die Runde wurde aufgelöst. |
| „Diese Runde ist voll …“ | Eine Runde hat höchstens 50 Mitglieder. |
| Im Reiter „Freunde“ erscheint nichts | Oben die richtige Runde auswählen. Wurde das Getränk als „Privat“ gespeichert? War der Freund beim Eintragen schon in der Runde? |

### Push-Nachrichten kommen nicht an

Der Reihe nach prüfen:

1. **Beide angemeldet** und **in derselben Runde**? Nach „Abmelden“ bekommt ein Handy absichtlich keine Pushs mehr.
2. **Für eigene Getränke** gibt es keine Push-Nachricht – nur die anderen bekommen eine. **Private** Getränke
   lösen nie eine aus.
3. **Benachrichtigungen erlaubt?** Android-Einstellungen → Apps → **Drink With Me** → **Benachrichtigungen**
   → einschalten, auch die Kategorie **„Getränke deiner Freunde“**.
4. **Akku-Optimierung:** Android-Einstellungen → Apps → Drink With Me → **Akku** → **„Nicht eingeschränkt“**
   („Uneingeschränkt“). Bei Xiaomi, Huawei, Oppo, OnePlus u. a. zusätzlich **Autostart** erlauben.
   Herstellerspezifische Tipps: <https://dontkillmyapp.com>
5. **Nicht stören** / Fokusmodus am Handy aus?
6. Einmal **abmelden und wieder anmelden** – dann wird das Push-Token neu registriert.
7. **Server-Protokolle ansehen:** Firebase-Konsole → „Hosting & Serverless“ → **„Functions“** → Reiter
   **„Logs“** („Protokolle“) → Einträge von `onGroupDrinkCreated` kurz nach dem Eintragen.
8. Firebase-Konsole → Zahnrad → Projekteinstellungen → Reiter **„Cloud Messaging“**: Bei
   **„Firebase Cloud Messaging API (V1)“** sollte **„Enabled“** („Aktiviert“) stehen.

---

## 12. Anhang

### Kosten im Blick behalten

- Firebase-Konsole → Zahnrad → **„Usage and billing“** („Nutzung und Abrechnung“): Verbrauch der Datenbank,
  Functions usw.
- Google-Cloud-Konsole → **„Billing“** („Abrechnung“) → **„Reports“** („Berichte“): Kosten in Euro.
- Die Budget-E-Mails aus Schritt 2 kommen an die E-Mail-Adresse des Rechnungskontos.

### Notbremse: Alles Kostenpflichtige abschalten

Google-Cloud-Konsole → **„Billing“** → **„Account management“** („Kontoverwaltung“) → beim Projekt über das
Drei-Punkte-Menü **„Disable billing“** („Abrechnung deaktivieren“). Dann entstehen keine Kosten mehr –
aber die Trinkrunden funktionieren auch nicht mehr, bis die Abrechnung wieder aktiviert ist. Die App läuft
lokal weiter.

### Sicherheit des Dienstkonto-Schlüssels

- **Project IAM Admin** wird nur für den **ersten** Deploy gebraucht. Wer vorsichtig sein will, entfernt
  diese Rolle danach wieder (IAM-Seite → `github-deploy@…` → Stift-Symbol → Rolle löschen → „Save“).
  Kommen später neue Arten von Server-Funktionen dazu, meldet der Deploy das mit
  „We failed to modify the IAM policy“ – dann die Rolle kurz wieder hinzufügen.
- **Schlüssel ausgetauscht werden muss** (z. B. weil die Datei irgendwo herumlag)? Google-Cloud-Konsole →
  Dienstkonten → `github-deploy@…` → „Keys“ → alten Schlüssel löschen (Papierkorb-Symbol) → neuen erstellen
  (Schritt 7.2) → in GitHub beim Secret auf **„Update“** und den neuen Inhalt einfügen (Schritt 8.1).

### Konto auf Wunsch löschen (für die Betreiber)

Die Datenschutzerklärung ([`DATENSCHUTZ_ENTWURF.md`](DATENSCHUTZ_ENTWURF.md)) verspricht, ein Konto auf
Anfrage per E-Mail zu löschen. Eine Löschfunktion in der App gibt es (noch) nicht. So geht es von Hand:

1. Am einfachsten bittet ihr die Person, vorher in der App **ihre geteilten Getränke zu löschen**, **alle
   Runden zu verlassen** und sich **abzumelden**.
2. Firebase-Konsole → **Authentication** → Reiter **„Users“** („Nutzer“) → nach der E-Mail-Adresse suchen →
   **User UID** notieren → Drei-Punkte-Menü → **„Delete account“** („Konto löschen“).
3. Firebase-Konsole → **Firestore** → Sammlung **`users`** → Dokument mit dieser UID → Drei-Punkte-Menü →
   **Dokument löschen** (inklusive der Untersammlung `tokens`).
4. Hat die Person Schritt 1 nicht gemacht: In der Sammlung **`groups`** jede Runde öffnen, in deren Feld
   `memberIds` die UID steht. Dort in der Untersammlung `drinks` alle Dokumente mit dieser `authorId`
   löschen, die UID aus `memberIds` und den Eintrag aus `members` entfernen. Steht die UID bei `ownerId`,
   dort die UID eines anderen Mitglieds eintragen. War sie das letzte Mitglied, die ganze Runde löschen
   und ebenso das passende Dokument in `inviteCodes`.
5. Der Person kurz bestätigen, dass alles gelöscht ist. Die lokalen Getränke auf ihrem Handy verschwinden,
   wenn sie die App deinstalliert.

### Was liegt wo?

| Was | Wo |
|---|---|
| Architektur und Datenmodell (verbindlich) | [`docs/TRINKRUNDEN_ARCHITEKTUR.md`](TRINKRUNDEN_ARCHITEKTUR.md) |
| Server-Code (Regeln, Functions, Tests) | [`firebase/`](../firebase/README.md) |
| Datenschutzerklärung (Entwurf) | [`docs/DATENSCHUTZ_ENTWURF.md`](DATENSCHUTZ_ENTWURF.md) |
| Bauen der Android-App | `.github/workflows/android.yml` – Download: Actions → „Android“ → `DrinkWithMe-android-apk` |
| Tests des Servers bei jedem Push | `.github/workflows/firebase.yml` („Firebase“) |
| Server bereitstellen (manuell) | `.github/workflows/firebase-deploy.yml` („Firebase Deploy“) |

### Kleines Wörterbuch

- **Firebase:** Baukasten von Google für App-Server: Anmeldung, Datenbank, Server-Funktionen, Push.
- **Firestore:** die Datenbank, in der Runden und geteilte Getränke liegen.
- **Cloud Functions:** kleine Server-Programme (Runde erstellen, beitreten, verlassen, Push verschicken).
- **Blaze:** Firebase-Tarif „zahle nur, was du verbrauchst“, mit kostenlosen Freimengen.
- **Dienstkonto / Service Account:** ein Zugang für Programme statt für Menschen – hier für GitHub.
- **Secret:** ein geheimer Wert, den GitHub verschlüsselt speichert und nur an Workflows weitergibt.
- **Workflow / GitHub Actions:** automatische Abläufe auf GitHub, z. B. App bauen oder Server bereitstellen.
- **APK:** die Installationsdatei einer Android-App.
- **SHA-1 / SHA-256:** „Fingerabdrücke“ des Schlüssels, mit dem die App signiert ist. Damit erkennt Google,
  dass die Anmeldung wirklich aus unserer App kommt.
