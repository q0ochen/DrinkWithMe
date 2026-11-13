# Drink With Me in F-Droid veröffentlichen

[F-Droid](https://f-droid.org) ist ein App-Katalog nur für **freie Software**. F-Droid baut jede App selbst aus
dem öffentlichen Quellcode, prüft sie und verteilt sie kostenlos. Für Drink With Me ist das der geplante Weg auf
Android-Handys, -Tablets und -Fernseher (siehe [`ANFORDERUNGEN.md`](ANFORDERUNGEN.md)).

Dieses Dokument sagt, **was schon erfüllt ist**, **was noch fehlt** und **womit zu rechnen ist**.

> **Unsichere Punkte sind mit [unsicher] markiert.** Stand der Recherche: 10. November 2026. Die Seiten von
> f-droid.org, izzyondroid.org und codeberg.org konnten bei der Recherche nicht direkt abgerufen werden. Die
> F-Droid-Regeln stammen aus den Quelltexten der Dokumentation auf GitLab (`fdroid-website`, `fdroidserver`) und aus
> Suchergebnissen. Vor dem Einreichen bitte die verlinkten Originalseiten lesen.
>
> **Schon jetzt in der F-Droid-App:** über unsere **eigene F-Droid-Paketquelle** (Abschnitt 11.3, Anleitung in der
> [README](../README.md#in-f-droid-eigene-paketquelle)). Außerdem: Testversion per Link oder mit **Obtainium**
> ([Abschnitt 11](#11-bis-zur-aufnahme-andere-wege)). **IzzyOnDroid** kommt wegen seiner KI-Richtlinie praktisch
> nicht in Frage.

---

## 1. Kurz zusammengefasst

| Thema | Stand |
|---|---|
| Freie Lizenz (CC0 1.0) | **erfüllt:** CC0 ist von der FSF als freie Lizenz anerkannt (nicht von der OSI) und steht in der Liste erlaubter Lizenzen von `fdroidserver` (`CC0-1.0`) |
| Keine proprietären Abhängigkeiten (kein Firebase, keine Google Play Services) | **erfüllt** |
| Öffentlicher Quellcode, Fehler-Tracker | erfüllt, sofern „Issues“ im GitHub-Repository eingeschaltet sind |
| Mindestens 3 Monate aktive Entwicklung | steht seit 16. Oktober 2026 in der Inclusion Policy. **Es gibt keinen Startknopf:** Die Prüfer zählen ab der Erstellung des öffentlichen Repositorys (9. November 2026) und schauen, ob seitdem sichtbar weiterentwickelt wurde. Eine eigene Paketquelle, ein Release oder eine Anmeldung zählen nicht → Antrag frühestens **ab 9. Februar 2027**, besser Mitte/Ende Februar |
| Regel zu KI-erstelltem Code | F-Droid: **keine** (Stand 10.11.2026); ob eine kommt, ist offen [unsicher]; vor dem Einreichen neu prüfen (Abschnitt 11.4) |
| Anti-Features | voraussichtlich **NonFreeNet**, möglicherweise zusätzlich **TetheredNet** |
| Store-Texte und Bilder (Fastlane) | **teilweise:** Titel, Kurz- und Langbeschreibung (`de-DE`, `en-US`), Icon und TV-Banner unter `fastlane/metadata/android/`; Bildschirmfotos und Änderungshinweise fehlen noch |
| Eigener Release-Schlüssel und Release-Workflow | **fehlt noch** |
| Abhängigkeits-Block von Google (`dependenciesInfo`) | **abgeschaltet** |
| Versionsnummern und Tags | **teilweise:** Testversionen zählen automatisch hoch (`1000 + Laufnummer`); für Veröffentlichungen Schema und Tag unten |
| Einreichen bei F-Droid (Merge Request) | nach den Punkten oben |
| Realistische Aufnahme | **Frühjahr bis Sommer 2027** [unsicher – die Warteschlange ist lang: im November 2026 warteten 141 Anträge auf Prüfung, der älteste seit Mai 2026] |

---

## 2. Aufnahmeregeln, die uns betreffen

Aus der [Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy/) und der Checkliste für neue Apps:

1. **Freie Lizenz für den gesamten Code** und eine Lizenzdatei im Repository – `LICENSE` (CC0 1.0) ist vorhanden.
2. **Nur freie Abhängigkeiten aus vertrauenswürdigen Quellen** (Maven Central, Google Maven für AndroidX).
   Erfüllt: AndroidX/Jetpack Compose, OkHttp, UnifiedPush Connector (mit Tink), kotlinx-coroutines – alle Apache 2.0.
   Kein Firebase, kein `com.google.android.gms`, kein Google-Services-Plugin, keine Analyse- oder Absturz-Dienste.
   Das ist in [`ARCHITEKTUR.md`](ARCHITEKTUR.md), Abschnitt 0, festgeschrieben – bitte so lassen.
3. **Keine fertigen Binärdateien im Quellcode**, die beim Bauen benutzt werden. [unsicher] Zu prüfen sind
   `android/app/debug.keystore` (nur für Test-Builds) und `android/gradle/wrapper/gradle-wrapper.jar`. Notfalls
   lässt F-Droid sie beim Bauen löschen (`scandelete` in den Metadaten) – der Release-Build braucht beide nicht.
4. **Aktive Entwicklung seit mindestens 3 Monaten** (Ausnahmen z. B. für Autoren, die schon andere Apps in F-Droid
   pflegen). Das Repository gibt es seit dem 9. November 2026.
5. **Der Autor ist einverstanden** – bei uns selbstverständlich, weil wir selbst einreichen.
6. **Store-Texte im Repository** (Fastlane- oder Triple-T-Struktur), mit Kurz- und Langbeschreibung und einer
   englischen Fassung (`en-US`) [unsicher, ob `en-US` Pflicht ist – die Checkliste für neue Apps nennt sie].
7. **Releases als Git-Tags**, damit F-Droid neue Versionen automatisch findet.
8. **Netzwerkdienste werden gekennzeichnet** („Anti-Features“, nächster Abschnitt).

Die App ist ohne Server voll benutzbar (Bar, Verlauf, Statistik, Sprache, Vorlesen) – das ist für F-Droid
ein Pluspunkt.

---

## 3. Anti-Features: was F-Droid kennzeichnen wird

Anti-Features sind Hinweise im F-Droid-Katalog, keine Ablehnung. Die Entscheidung treffen die Prüfer.

| Anti-Feature | Bedeutung | Bei uns |
|---|---|---|
| **NonFreeNet** | Die App nutzt einen **nicht freien Netzwerkdienst** | **Voraussichtlich ja:** Für Freunde ist die Anmeldung mit einem **Google-Konto** nötig (Google-Anmeldung ist ein proprietärer Dienst). |
| **TetheredNet** | Die App nutzt einen freien Dienst, dessen **Server man nicht wählen kann** (seit Juli 2024 von NonFreeNet getrennt) | **Möglicherweise:** Supabase ist freie Software, aber die Server-Adresse steht fest in der App (`supabase.properties`) und lässt sich in der App nicht ändern. |
| Tracking, Ads, NonFreeDep, NonFreeAdd | Tracking, Werbung, unfreie Abhängigkeiten oder Zusatzangebote | **Nein** |

Ein Vorschlag für die Begründung in den Metadaten (siehe Abschnitt 7):

- *NonFreeNet:* „Freunde und Benachrichtigungen erfordern eine Anmeldung mit einem Google-Konto. Bar, Verlauf,
  Statistik, Sprache und Vorlesen funktionieren ohne Konto und ohne Netz.“
- *TetheredNet:* „Die Vernetzung nutzt einen fest eingestellten Server des Projekts (Supabase, freie Software).“

Wer TetheredNet vermeiden will, müsste die Server-Adresse in der App einstellbar machen – das ist derzeit nicht
geplant.

---

## 4. Store-Texte und Bilder (Fastlane-Struktur)

F-Droid liest Beschreibung, Bilder und Änderungshinweise aus dem Repository. Vorschlag: im Hauptverzeichnis
`fastlane/metadata/android/` [unsicher: Weil die App im Unterordner `android/app` liegt, beim Einreichen prüfen,
ob F-Droid den Ordner im Hauptverzeichnis findet; sonst unter `android/app/` anlegen]:

```
fastlane/metadata/android/
├── de-DE/
│   ├── title.txt                 Drink With Me
│   ├── short_description.txt     Kurzbeschreibung, höchstens 80 Zeichen
│   ├── full_description.txt      Langbeschreibung (höchstens 4000 Zeichen, einfaches HTML erlaubt)
│   ├── changelogs/
│   │   └── <versionCode>.txt     Was ist neu – pro Version, höchstens 500 Zeichen
│   └── images/
│       ├── icon.png              512 × 512 (aus design/icons/app_icon.svg erzeugen)
│       ├── featureGraphic.png    1024 × 500 (optional)
│       ├── tvBanner.png          Fernseh-Banner (aus design/icons/tv_banner.svg)
│       ├── phoneScreenshots/     1.png, 2.png, … (Bar, Verlauf, Freunde, Ereignis)
│       ├── tenInchScreenshots/   Tablet (optional)
│       └── tvScreenshots/        Fernseher (optional)
└── en-US/
    ├── title.txt
    ├── short_description.txt     englisch, mit dem Hinweis, dass die App derzeit nur Deutsch spricht
    ├── full_description.txt
    └── changelogs/<versionCode>.txt
```

- Alle Bilder müssen **selbst erstellt** sein (wie die Icons, CC0). Bildschirmfotos der eigenen App sind in
  Ordnung; Bildschirmfotos aus der Web-App lassen sich mit `SCREENSHOT_DIR=… npm run test:e2e -- screenshots`
  erzeugen (siehe [`web/README.md`](../web/README.md)), für Android braucht es Fotos vom Gerät.
- In die Beschreibung gehören: Gesundheit vor Sozialem, „lokal zuerst“, wofür das Google-Konto nötig ist,
  ntfy für sofortige Benachrichtigungen, Fernseher-Unterstützung, Gesundheitshinweis, Link zur Datenschutzerklärung.

---

## 5. Reproduzierbare Builds mit eigenem Schlüssel

### 5.1 Worum geht es?

Normalerweise baut F-Droid die App und **signiert sie mit einem eigenen F-Droid-Schlüssel**. Die Folge: Eine
APK aus GitHub und eine aus F-Droid haben verschiedene Signaturen – man kann nicht von der einen auf die andere
aktualisieren, ohne zu deinstallieren (und dabei den lokalen Verlauf zu verlieren).

Bei **reproduzierbaren Builds** baut F-Droid die App ebenfalls selbst, vergleicht das Ergebnis aber Byte für Byte
mit **unserer** signierten APK. Stimmen beide überein, verteilt F-Droid **unsere** APK mit **unserer** Signatur.

### 5.2 Warum wir das wollen

- **Eine Signatur überall:** GitHub-Releases und F-Droid sind untereinander update-fähig.
- **Android-Entwicklerprüfung 2027** (Abschnitt 8): Mit eigenem Schlüssel muss nur **ein** Schlüssel registriert
  werden; bei F-Droid-Signatur kommt ein zweiter Schritt nach der ersten Veröffentlichung dazu.
- **Vertrauen:** F-Droid bestätigt öffentlich, dass die verteilte APK genau aus dem veröffentlichten Quellcode
  entsteht.

Reproduzierbare Builds sind bei F-Droid **keine Pflicht**, gelten aber als gute Praxis.

### 5.3 Was dafür nötig ist

1. **Neuer, geheimer Release-Schlüssel.** Der jetzige `android/app/debug.keystore` ist öffentlich (er liegt im
   Repository) und darf für Veröffentlichungen **nicht** verwendet werden. Den neuen Schlüssel einmal erzeugen
   (z. B. in Android Studio: *Build → Generate Signed App Bundle or APK → Create new*; dafür braucht es einen
   Computer), **doppelt sichern** (Passwort-Manager und offline) und für GitHub als Secrets hinterlegen
   (Schlüsseldatei base64-kodiert plus Passwörter). **Geht der Schlüssel verloren, kann niemand mehr Updates
   veröffentlichen**, und er lässt sich auch bei Google nicht mehr registrieren.
2. **Release-Workflow** (noch zu bauen, z. B. von Claude): bei einem Tag `v…` die Release-APK bauen, mit dem
   geheimen Schlüssel signieren und als `DrinkWithMe-<version>.apk` an ein GitHub-Release hängen.
3. **Deterministischer Build** [unsicher – muss ausprobiert werden]: feste Versionen von JDK, Gradle, Android
   Gradle Plugin und Build-Tools (sind im Repository festgelegt). Der Abhängigkeits-Block von Google ist in
   `android/app/build.gradle.kts` **bereits abgeschaltet**:

   ```kotlin
   android {
       dependenciesInfo {
           includeInApk = false     // F-Droid lehnt den verschlüsselten „Dependency metadata“-Block ab
           includeInBundle = false
       }
   }
   ```

   Offen ist, zweimal aus demselben Commit zu bauen und zu vergleichen (z. B. mit `diffoscope` oder F-Droids
   `apksigcopier`/`fdroid build` in der CI). Dabei besonders prüfen [unsicher]: die Datei
   `META-INF/version-control-info.textproto`, die neuere Android-Gradle-Plugin-Versionen in Release-Builds
   einbetten (Commit-Angabe, `vcsInfo`), und das Baseline-Profil aus `profileinstaller` – beides sind bekannte
   Stolpersteine für reproduzierbare Builds.
4. **Öffentliche Server-Werte im Repository:** F-Droid baut ohne unsere GitHub-Secrets. Damit die F-Droid-Version
   Freunde und Benachrichtigungen kann, muss `android/app/supabase.properties` mit den echten (öffentlichen)
   Werten **eingecheckt** sein. Das ist unbedenklich – geheim ist darin nichts.

> **Achtung, Wechsel für Testerinnen und Tester:** Wer die Test-APK (öffentlicher Debug-Schlüssel) installiert hat,
> muss für die erste Release-Version **einmal deinstallieren** – dabei gehen Bar und Verlauf verloren, weil die
> Android-App noch keine Export-Funktion hat (die Web-App hat schon „Sicherung speichern/einlesen“ als JSON-Datei).
> Vor dem Umstieg ankündigen oder vorher eine Export-/Import-Funktion in der Android-App einbauen – am besten im
> selben Dateiformat wie die Web-App.

---

## 6. Versionen und Tags

| Was | Regel | Beispiel |
|---|---|---|
| `versionCode` (in `android/app/build.gradle.kts`) | ganze Zahl, bei **jeder** Veröffentlichung höher, nie wiederverwenden. Testversionen aus der CI haben `1000 + Laufnummer` (automatisch, `-PciBuildNumber`); Veröffentlichungen **Major × 1.000.000 + Minor × 1.000 + Patch**, fest eingecheckt – damit liegen sie immer über den Testversionen | `2.0.0` → `2000000` |
| `versionName` | dreiteilig, passend zum Tag | `2.0.0` (gesetzt) |
| Git-Tag | `v` + `versionName`, auf dem Commit, aus dem gebaut wird | `v2.0.0` |
| Änderungshinweis | `fastlane/metadata/android/de-DE/changelogs/<versionCode>.txt` (und `en-US`) | `changelogs/2000000.txt` |

- Die automatische Testversion nutzt den festen Tag `testversion` (`.github/workflows/android.yml`). Er passt nicht
  zum Muster `^v[0-9.]+$`, deshalb hält F-Droid ihn nicht für eine Veröffentlichung. Eigene Test-Tags bitte nie
  `v…` nennen.
- F-Droid prüft regelmäßig die Tags (`UpdateCheckMode: Tags`) und übernimmt neue Versionen automatisch
  (`AutoUpdateMode: Version`).
- Die Web-App führt ihre Version in `web/package.json` (jetzt `2.0.0`); sinnvoll ist, beide gleich zu halten.

---

## 7. Einreichen: der Merge Request bei `fdroiddata`

F-Droid verwaltet alle Apps im Repository [gitlab.com/fdroid/fdroiddata](https://gitlab.com/fdroid/fdroiddata).
Der schnellste Weg ist ein eigener **Merge Request** (die Alternative – ein Wunsch-Ticket im „Request for
Packaging“-Tracker – dauert meist deutlich länger).

1. Kostenloses Konto bei **GitLab** anlegen.
2. `fdroiddata` **forken** (eigene Kopie anlegen).
3. Eine Datei `metadata/com.drinkwithme.app.yml` anlegen. Gerüst [unsicher in Details – mit `fdroid lint` und
   `fdroid rewritemeta` prüfen]:

   ```yaml
   Categories:
     - Sports & Health          # [unsicher] gültige Namen stehen in fdroiddata/config/categories.yml
   License: CC0-1.0
   AuthorName: '[Name]'
   WebSite: https://q0ochen.github.io/DrinkWithMe/
   SourceCode: https://github.com/q0ochen/DrinkWithMe
   IssueTracker: https://github.com/q0ochen/DrinkWithMe/issues

   AutoName: Drink With Me

   RepoType: git
   Repo: https://github.com/q0ochen/DrinkWithMe.git
   Binaries: https://github.com/q0ochen/DrinkWithMe/releases/download/v%v/DrinkWithMe-%v.apk

   Builds:
     - versionName: 2.0.0
       versionCode: 2000000
       commit: v2.0.0
       subdir: android/app
       gradle:
         - yes

   AllowedAPKSigningKeys: <SHA-256 des eigenen Release-Zertifikats, klein, ohne Doppelpunkte>

   AntiFeatures:
     NonFreeNet:
       de-DE: Freunde und Benachrichtigungen erfordern eine Anmeldung mit einem Google-Konto.
       en-US: Friends and notifications require signing in with a Google account.

   AutoUpdateMode: Version
   UpdateCheckMode: Tags ^v[0-9.]+$
   CurrentVersion: 2.0.0
   CurrentVersionCode: 2000000
   ```

4. Merge Request mit dem Titel **„New app: Drink With Me“** öffnen, die Vorlage (Checkliste) ausfüllen und
   warten, bis die automatischen Prüfungen (Pipeline) grün sind.
5. Auf Rückfragen der Prüfer antworten (Anti-Features, Binärdateien, Reproduzierbarkeit …).
6. Nach dem Zusammenführen baut der F-Droid-Server die App; sie erscheint mit einer der nächsten
   Katalog-Aktualisierungen (meist innerhalb einiger Tage).

---

## 8. Android-Entwicklerprüfung ab 2027

Google führt eine Pflicht ein, dass Apps auf **zertifizierten Android-Geräten** (Android 7 und neuer) von einem
**registrierten Entwickler** stammen müssen – auch außerhalb des Play Stores.

**Stand November 2026** (laut [developer.android.com/developer-verification](https://developer.android.com/developer-verification)
und dem [FAQ](https://developer.android.com/developer-verification/guides/faq)):

- Seit **30. September 2026** gilt das nur in **Brasilien, Indonesien, Singapur und Thailand** und nur für
  Installationen aus teilnehmenden Stores (u. a. Google Play, Galaxy Store, Xiaomi GetApps). F-Droid gehört nicht
  dazu; direkte APK-Installationen sind bisher nicht betroffen.
- **2027** will Google das **weltweit** für alle Apps auf zertifizierten Geräten einführen. Ein Datum für
  Deutschland ist nicht genannt [unsicher].
- Nicht registrierte Apps lassen sich dann nur noch über den **„erweiterten Ablauf“** (einmalig: Entwickleroptionen
  einschalten, bestätigen, dass einen niemand dazu drängt, Neustart, **24 Stunden warten**, mit Fingerabdruck/PIN
  bestätigen; danach für 7 Tage oder dauerhaft) oder per **ADB** installieren.
- Der Fire TV ist kein von Google zertifiziertes Gerät; die Prüfung dürfte dort nicht greifen [unsicher].

**Möglichkeiten für Drink With Me:**

| Weg | Kosten | Voraussetzungen | Passt? |
|---|---|---|---|
| **Volles Konto** in der Android Developer Console | **25 US-Dollar** einmalig | Ausweis-Prüfung | Widerspricht „kein Geld ausgeben“ |
| **Konto für begrenzte Verteilung** („Limited distribution“) | kostenlos | Google-Konto mit 2-Faktor-Anmeldung, Google-Zahlungsprofil (Name und Anschrift), **kein** Ausweis | nur für einen kleinen Kreis: Google nennt **20** Geräte (FAQ) bzw. **50** Geräte (Anleitung) [unsicher], die einzeln per QR-Code/Link freigegeben werden – für die Testgruppe denkbar, **nicht** für die öffentliche F-Droid-Version |
| **Registrierung für Open-Source-Kataloge** | [unsicher] | Konto in der Android Developer Console oder Play Console; Paketname `com.drinkwithme.app` und Schlüssel-Fingerabdruck registrieren; wenn F-Droid mit eigenem Schlüssel signiert, zusätzlich dessen Fingerabdruck nach der ersten Veröffentlichung | Mit eigenem Schlüssel (Abschnitt 5) am einfachsten. Ob das mit einem kostenlosen Konto möglich ist, ist offen [unsicher] |
| **Nichts registrieren** | kostenlos | Nutzerinnen und Nutzer schalten einmal den „erweiterten Ablauf“ ein | funktioniert, ist aber eine Hürde – gerade für die Zielgruppe „extrem einfach“ |

F-Droid lehnt die Pflicht ab ([offener Brief, Februar 2026](https://f-droid.org/en/2026/02/24/open-letter-opposing-developer-verification.html))
und hat im September 2026 „F-Droid 2.0“ veröffentlicht. Wie F-Droid 2027 damit umgeht (z. B. Registrierung im
Auftrag der Entwickler über Googles neue Schnittstellen), ist offen [unsicher].

**Was wir jetzt schon tun können:**

- Paketnamen `com.drinkwithme.app` beibehalten.
- Den eigenen Release-Schlüssel (Abschnitt 5) **sicher aufbewahren** – ohne ihn keine Registrierung.
- Anfang 2027 neu bewerten und **entscheiden**: kostenloses Konto + Registrierung, falls möglich – sonst
  erweiterter Ablauf. Diese Entscheidung steht noch aus.

---

## 9. Zeitplan (Vorschlag)

| Wann | Was |
|---|---|
| ab sofort | Testversion per Link, mit Obtainium oder über die eigene F-Droid-Paketquelle verteilen (Abschnitt 11) |
| bis Dezember 2026 | Testphase mit Test-APKs; Fehler beheben |
| Dezember 2026 – Januar 2027 | Release-Schlüssel erzeugen, Release-Workflow, Reproduzierbarkeit prüfen (`dependenciesInfo` ist schon abgeschaltet); Fastlane-Texte und Bildschirmfotos; `versionCode` erhöhen und Tag setzen; ggf. Export/Import des Verlaufs in der Android-App |
| sofort | Arbeitszweig in `main` zusammenführen (normaler Merge, **kein** Squash, damit Historie und Daten sichtbar bleiben); danach regelmäßig auf `main` weiterentwickeln und Versionen taggen – „aktiv entwickelt“ muss sichtbar sein |
| ab Mitte Februar 2027 | 3 Monate erreicht → Merge Request bei `fdroiddata`, Vorlage **vollständig** ausfüllen, Beschreibung und Antworten **selbst** schreiben (Prüfer reagieren im Herbst 2026 deutlich ablehnend auf KI-geschriebene MR-Texte) |
| Frühjahr – Sommer 2027 | Prüfung durch Freiwillige; Rückfragen zügig beantworten (inaktive Anträge werden geschlossen). Mithelfen beim Testen anderer Anträge ist ausdrücklich erwünscht und beschleunigt die Warteschlange [unsicher: Dauer] |
| 2027 | Android-Entwicklerprüfung: Entscheidung (Abschnitt 8) |

---

## 10. Checkliste

- [ ] „Issues“ im GitHub-Repository eingeschaltet
- [ ] `android/app/supabase.properties` mit den echten öffentlichen Werten eingecheckt
- [ ] Eigener Release-Schlüssel erzeugt, doppelt gesichert, als GitHub-Secrets hinterlegt
- [ ] Release-Workflow: Tag `v…` → signierte APK `DrinkWithMe-<version>.apk` im GitHub-Release
- [x] `dependenciesInfo { includeInApk = false; includeInBundle = false }`
- [ ] Reproduzierbarkeit geprüft (zwei Builds aus demselben Commit sind identisch)
- [x] `versionName` dreiteilig (`2.0.0`)
- [ ] `versionCode` erhöht, Tag `v2.0.0` gesetzt
- [x] Fastlane-Texte (`de-DE`, `en-US`), Icon, TV-Banner
- [ ] Fastlane: Bildschirmfotos, Änderungshinweise
- [ ] Datenschutzerklärung unter fester Adresse (siehe [`DATENSCHUTZ_ENTWURF.md`](DATENSCHUTZ_ENTWURF.md))
- [ ] Umstieg der Testerinnen und Tester angekündigt (Deinstallation, Datenverlust) oder Export in der Android-App gebaut
- [ ] Arbeitszweig in `main` zusammengeführt (ohne Squash), danach regelmäßig weiterentwickelt und getaggt
- [ ] 3 Monate aktive Entwicklung erreicht (ab 9. Februar 2027; zählt automatisch ab Erstellung des Repositorys)
- [ ] Merge Request „New app: Drink With Me“ bei `fdroiddata`

---

## 11. Bis zur Aufnahme: andere Wege

Frage des Hauptentwicklers (10.11.2026): Lässt sich die App schon jetzt in F-Droid oder einem F-Droid-kompatiblen
Katalog anbieten? Ergebnis der Recherche:

| Weg | Ab wann | Kosten | Bewertung |
|---|---|---|---|
| **Testversion per Link** | sofort | 0 € | läuft schon; Updates von Hand |
| **Obtainium** + Testversion | sofort | 0 € | **Empfehlung für die Testphase:** meldet neue Testversionen, keine Projektänderung nötig |
| **Eigene F-Droid-Paketquelle** auf GitHub | **eingerichtet** (12.11.2026), aktiv sobald das Secret `FDROID_REPO_KEYSTORE` gesetzt ist | 0 € | für alle, die die App in der F-Droid-App haben wollen (11.3) |
| **IzzyOnDroid** | – | 0 € | **praktisch ausgeschlossen** wegen der KI-Richtlinie (11.2) |
| **Offizielles F-Droid** | Antrag ab etwa 9.2.2027, Aufnahme etwa Februar bis Mai 2027 | 0 € | das Ziel (Abschnitte 1–10); Restrisiko KI-Regel (11.4) |

### 11.1 Obtainium (jetzt)

[Obtainium](https://github.com/ImranR98/Obtainium) (GPL-3.0, erhältlich als APK bei GitHub, in F-Droid und bei
IzzyOnDroid) installiert Apps direkt aus GitHub-Releases und meldet neue Versionen.

- Die Testversion hat einen **festen Tag** (`testversion`) und ist als **Vorabversion** markiert. Obtainium liest
  die Version aus dem Tag-Namen und erkennt neue Builds deshalb nur mit drei Einstellungen: **Vorabversionen einbeziehen**
  (`includePrereleases`), **Veröffentlichungsdatum als Version verwenden** (`releaseDateAsVersion`, dazu
  `versionDetection` aus) und **Letzten Upload eines Release-Anhangs als Veröffentlichungsdatum verwenden**
  (`useLatestAssetDateAsReleaseDate`). Die letzte ist nötig, weil `android.yml` das Release nur bearbeitet und die
  APK mit `--clobber` ersetzt; das Datum des Releases selbst ändert sich dabei nicht.
- Der Ein-Tipp-Link in der [README](../README.md#android-testversion) und in den Release-Hinweisen setzt alle drei.
  Format geprüft gegen den Obtainium-Quellcode (`lib/pages/home.dart`, `lib/models/app.dart`,
  `lib/providers/app_json_migration.dart`, `lib/app_sources/github.dart`, Stand 10.11.2026) und gegen die
  Weiterleitungsseite `apps.obtainium.imranr.dev/redirect`.
- **Beim Umstieg auf den Release-Schlüssel** (Abschnitt 5) muss der Obtainium-Eintrag gelöscht und die App neu
  installiert werden. Für versionierte Releases (`v2.0.0` …) reicht danach ein normaler Eintrag ohne die drei
  Einstellungen.

### 11.2 IzzyOnDroid (nicht empfohlen)

IzzyOnDroid ist ein unabhängiges, F-Droid-kompatibles Verzeichnis. Es kennt **keine** 3-Monats-Regel und ist in
den Clients **Droid-ify** und **Neo Store** voreingestellt, in der offiziellen F-Droid-App 2.0 aber **nicht**.

- **KI-Richtlinie (Hauptgrund):** „We are strongly opposed to apps which are fully or in part created by generative
  AI tools.“ und „Vibe-coded apps will be rejected.“ Fehlende Offenheit kann nachträglich zur Ablehnung führen.
  Das Antragsformular (`.forgejo/issue_template/app-inclusion-request.yaml`) hat einen **Abschnitt zur KI-Nutzung**
  (Umfang, Werkzeuge, Verantwortung) [unsicher, ob Pflichtfeld]. Drink With Me ist zum größten Teil mit
  Claude Code geschrieben (fast alle Commits). Bei ehrlicher Angabe ist eine Ablehnung sehr wahrscheinlich;
  andere Projekte mit Claude-Code-Anteil reichen aus diesem Grund gar nicht erst ein. Der Wortlaut stammt aus
  Suchauszügen und Drittkopien der Seite [AppInclusionPolicy](https://izzyondroid.org/docs/general/AppInclusionPolicy/)
  (dort selbst nicht abrufbar) – vor einer Entscheidung bitte einmal direkt lesen.
- **Technisch** würde IzzyOnDroid die heutige Testversion ebenfalls ablehnen: Debug-Build (`debuggable`) mit
  öffentlichem Debug-Zertifikat, wandernder Vorabversions-Tag, Testversionen statt regulärer Releases. Nötig wären
  Release-Schlüssel, reguläre Releases je Version mit steigendem `versionCode` und Fastlane-Texte – also dieselben
  Vorarbeiten wie für F-Droid.
- Antrag (falls doch): Issue „App Inclusion Request“ auf
  [codeberg.org/IzzyOnDroid/repodata](https://codeberg.org/IzzyOnDroid/repodata) (kostenloses Codeberg-Konto).

### 11.3 Eigene F-Droid-Paketquelle (eingerichtet)

Seit 12.11.2026 baut der Workflow „Android“ bei jedem Push auf `main` bzw. den Entwicklungszweig (und bei einem
manuellen Start) eine eigene F-Droid-Paketquelle. Wer die F-Droid-App nutzt, fügt sie einmal hinzu und
installiert und aktualisiert Drink With Me dann wie jede andere F-Droid-App. Anleitung mit Link und QR-Code:
[README](../README.md#in-f-droid-eigene-paketquelle).

| Was | Wie |
|---|---|
| Adresse | `https://raw.githubusercontent.com/q0ochen/DrinkWithMe/fdroid-repo/fdroid/repo` |
| Fingerabdruck (SHA-256) | `d358dcffd3b82c09460943df4513e5f7951f2c0c6a6fd7ebab8e0741becd7523` |
| Link zum Hinzufügen | `https://fdroid.link/#<Adresse>?fingerprint=<Fingerabdruck>` (F-Droid 2.0 fängt nur `fdroid.link` und `fdroidrepos://` ab, keine normalen https-Links) |
| Gebaut von | Job „Eigene F-Droid-Paketquelle aktualisieren“ in `.github/workflows/android.yml` mit `fdroid/build-repo.sh` und `fdroidserver` 2.4.5 (`fdroid update`) |
| Inhalt | signierter Index (`entry.jar`, `index-v2.json`, für alte Clients `index-v1.jar`), die **aktuelle und die vorige** Testversion (`archive_older: 2`), Texte und Bilder aus `fastlane/metadata/android/`, Angaben aus `fdroid/metadata/com.drinkwithme.app.yml` (u. a. Anti-Feature NonFreeNet) und die Anti-Feature-Bezeichnungen aus `fdroid/config/` (F-Droid 2.0 zeigt nur Anti-Features, die die Paketquelle definiert) |
| Ort | Zweig `fdroid-repo` – bei jedem Build mit **einem einzigen** Commit ersetzt, damit alte APKs das Repository nicht wachsen lassen (rund 26 MB; jeder vollständige Klon des Repositorys lädt sie mit) |
| Reihenfolge | Veröffentlicht wird erst nach dem Installationstest im Emulator, und nie von einem älteren Lauf: Liegt schon eine neuere Testversion in der Paketquelle (bzw. im Release „testversion“), wird übersprungen. Wird `android.yml` umbenannt, beginnt die Laufnummer neu – dann den Versatz 1000 in `build.gradle.kts` erhöhen (der Workflow warnt) |
| Sicherheit | Der geheime Schlüssel ist nur im Schritt „Paketquelle bauen“ sichtbar, das Schreib-Token nur beim Veröffentlichen; `fdroidserver` und alle Abhängigkeiten werden mit festen Prüfsummen installiert (`fdroid/requirements.txt`) |
| Signatur des Index | eigener geheimer Schlüssel im Secret `FDROID_REPO_KEYSTORE` (EINRICHTUNG 5.5) – **nicht** `android/app/debug.keystore`. Der Job prüft vor und nach dem Bauen, dass Schlüssel und Index zum Fingerabdruck passen; fehlt das Secret, wird der Job übersprungen |
| Signatur der APK | unverändert der feste Test-Schlüssel: Die App aus F-Droid, aus dem Download-Link und aus Obtainium ist austauschbar |
| Updates | F-Droid bietet ein Update nur bei höherem `versionCode` an. Deshalb haben Testversionen aus der CI `versionCode` **1000 + Laufnummer** und `versionName` `2.0.0-test.<Laufnummer>` (`-PciBuildNumber` in `android/app/build.gradle.kts`) |

Grenzen und Hinweise:

- Das ist **nicht** das offizielle F-Droid: F-Droid baut hier nichts selbst und prüft nichts. Die Paketquelle
  verteilt genau die APK, die GitHub Actions gebaut und getestet hat.
- Warum nicht GitHub Pages? Pages ist durch die Web-App belegt (ein Repository hat nur eine Pages-Seite, jedes
  Deploy ersetzt sie). `raw.githubusercontent.com` ist der übliche Weg für solche Paketquellen (so arbeitet auch
  `fdroid nightly`). `fdroid nightly` selbst passt nicht, weil es die APK neu signiert – alle Tester müssten neu
  installieren.
- `fdroid update` warnt, dass die APK „debuggable“ ist – das ist bei Testversionen so gewollt.
- `raw.githubusercontent.com` hält Dateien bis zu 5 Minuten im Zwischenspeicher. Direkt nach einer neuen
  Testversion können `entry.jar` und `index-v2.json` kurz aus verschiedenen Builds stammen; F-Droid meldet dann
  „Ungültiges Repository“. Abhilfe: einige Minuten warten (steht auch in README und EINRICHTUNG 13).
- F-Droid 2.0 aktualisiert die Paketquellen standardmäßig nur alle paar Stunden (und nur im WLAN). Deshalb bleibt
  die vorige APK erhalten; ist eine Liste noch älter, hilft „Auf Aktualisierungen prüfen“.
- Den Schlüssel **nie verlieren oder austauschen**: Ein neuer Schlüssel heißt, dass alle die Paketquelle neu
  hinzufügen müssen.
- Nach der Aufnahme ins offizielle F-Droid kann die eigene Paketquelle eingestellt werden; wer mag, behält sie für
  Testversionen.

### 11.4 Offizielles F-Droid und KI

Die [Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy/) enthält (Stand 10.11.2026) **keine** Regel zu
KI-erstelltem Code. Diskutiert wird an zwei Stellen, beide ohne Beschluss:

- Admin-Ticket [#699](https://gitlab.com/fdroid/admin/-/issues/699) („Interim Policy on Use of Generative AI“):
  eine eher erlaubende Übergangsregel nach dem Vorbild von Debian – ausdrücklich **nur** für Beiträge zu F-Droid
  selbst, nicht für die Aufnahme von Apps.
- Admin-Ticket [#641](https://gitlab.com/fdroid/admin/-/issues/641) („Tag apps that use AI or LLMs with an
  anti-feature“): Apps, die KI oder LLMs **nutzen**, mit einem Anti-Feature kennzeichnen. Drink With Me nutzt keine
  KI-Dienste; ob der Vorschlag auch Apps erfassen würde, die mit KI **geschrieben** wurden, ist offen [unsicher].
- Dazu eine Diskussion im [F-Droid-Forum](https://forum.f-droid.org/t/does-f-droid-have-a-formal-policy-on-libre-ai/33279).

Stand 13.11.2026: #641 ist weiter offen und aktiv; dort wird auch vorgeschlagen, mit KI geschriebene Apps
abzulehnen. Bei Drink With Me ist die KI-Herkunft offensichtlich (fast alle Commits stammen von „Claude“). Mit
Rückfragen ist zu rechnen; wer einreicht, sollte den Code erklären und dafür einstehen können.

Eine strenge Regel wie bei IzzyOnDroid würde auch Drink With Me treffen; danach sieht es bei F-Droid derzeit nicht
aus [unsicher]. Deshalb: vor dem Antrag im Februar 2027 die Policy neu lesen und im Merge Request offen angeben, wie
die App entstanden ist.

## Quellen

- F-Droid: [Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy/),
  [Anti-Features](https://f-droid.org/docs/Anti-Features/),
  [Submitting to F-Droid – Quick Start Guide](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/),
  [Reproducible Builds](https://f-droid.org/docs/Reproducible_Builds/),
  [All About Descriptions, Graphics, and Screenshots](https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/),
  [Neues Anti-Feature TetheredNet (Juli 2024)](https://f-droid.org/en/2024/07/25/twif.html),
  Quelltexte: [fdroid-website `_docs`](https://gitlab.com/fdroid/fdroid-website/-/tree/master/_docs)
  (u. a. `Inclusion_Policy.md`, `Setup_an_F-Droid_App_Repo.md`, `Publishing_Nightly_Builds.md`),
  [fdroidserver](https://gitlab.com/fdroid/fdroidserver),
  [voreingestellte Paketquellen des F-Droid-Clients](https://gitlab.com/fdroid/fdroidclient/-/blob/master/app/src/main/assets/default_repos.json),
  [Forum: Does F-Droid have a formal policy on libre „AI“?](https://forum.f-droid.org/t/does-f-droid-have-a-formal-policy-on-libre-ai/33279),
  Admin-Tickets [#641](https://gitlab.com/fdroid/admin/-/issues/641) und [#699](https://gitlab.com/fdroid/admin/-/issues/699),
  3-Monats-Regel: [Commit a9c6e1c5](https://gitlab.com/fdroid/fdroid-website/-/commit/a9c6e1c591bfa4a36d4da0ce9ad15d1b7463c244)
  und [Diskussion !1643](https://gitlab.com/fdroid/fdroid-website/-/merge_requests/1643); wie Prüfer zählen (Alter des
  Repositorys): [fdroiddata !56510](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/56510)
- IzzyOnDroid: [App Inclusion Policy](https://izzyondroid.org/docs/general/AppInclusionPolicy/),
  [Wiki (archiviert)](https://gitlab.com/IzzyOnDroid/repo/-/wikis/home),
  [Anträge auf Codeberg](https://codeberg.org/IzzyOnDroid/repodata);
  Beispiele für die Anwendung der KI-Richtlinie:
  [aard2-android #217](https://github.com/itkach/aard2-android/issues/217),
  [perritodeck #6](https://github.com/perritodeck/perritodeck/pull/6),
  [kikidame (README)](https://github.com/t-seki/kikidame#readme)
- Obtainium: [Quellcode](https://github.com/ImranR98/Obtainium) (Deep-Links in `lib/pages/home.dart`,
  App-Format in `lib/models/app.dart`, GitHub-Quelle in `lib/app_sources/github.dart`),
  [Weiterleitungsseite](https://github.com/ImranR98/apps.obtainium.imranr.dev/blob/main/src/pages/redirect.astro)
- Google: [Android developer verification](https://developer.android.com/developer-verification),
  [FAQ](https://developer.android.com/developer-verification/guides/faq),
  [Limited distribution](https://developer.android.com/developer-verification/guides/limited-distribution),
  [Open-Source-Registrierung](https://developer.android.com/developer-verification/guides/open-source-app-registration),
  [Blog, Juni 2026](https://developer.android.com/blog/posts/android-developer-verification-building-a-safer-ecosystem-together)
