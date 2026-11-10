# Drink With Me in F-Droid veröffentlichen

[F-Droid](https://f-droid.org) ist ein App-Katalog nur für **freie Software**. F-Droid baut jede App selbst aus
dem öffentlichen Quellcode, prüft sie und verteilt sie kostenlos. Für Drink With Me ist das der geplante Weg auf
Android-Handys, -Tablets und -Fernseher (siehe [`ANFORDERUNGEN.md`](ANFORDERUNGEN.md)).

Dieses Dokument sagt, **was schon erfüllt ist**, **was noch fehlt** und **womit zu rechnen ist**.

> **Unsichere Punkte sind mit [unsicher] markiert.** Stand der Recherche: November 2026. Die Seiten von f-droid.org
> konnten bei der Recherche nicht direkt abgerufen werden; die F-Droid-Regeln stammen aus Suchergebnissen und
> bekannter Dokumentation. Vor dem Einreichen bitte die verlinkten Originalseiten lesen.

---

## 1. Kurz zusammengefasst

| Thema | Stand |
|---|---|
| Freie Lizenz (CC0 1.0) | erfüllt [unsicher: CC0 ist von der FSF als freie Lizenz anerkannt, nicht von der OSI – F-Droid nimmt sie nach unserem Kenntnisstand an] |
| Keine proprietären Abhängigkeiten (kein Firebase, keine Google Play Services) | **erfüllt** |
| Öffentlicher Quellcode, Fehler-Tracker | erfüllt, sofern „Issues“ im GitHub-Repository eingeschaltet sind |
| Mindestens 3 Monate aktive Entwicklung | Repository seit 9. November 2026 → frühestens **Februar 2027** |
| Anti-Features | voraussichtlich **NonFreeNet**, möglicherweise zusätzlich **TetheredNet** |
| Store-Texte und Bilder (Fastlane) | **fehlt noch** |
| Eigener Release-Schlüssel und Release-Workflow | **fehlt noch** |
| Abhängigkeits-Block von Google (`dependenciesInfo`) | **abgeschaltet** |
| Versionsnummern und Tags | **teilweise:** `versionName` ist `2.0.0`; `versionCode` erhöhen und Tag setzen fehlen noch (Schema unten) |
| Einreichen bei F-Droid (Merge Request) | nach den Punkten oben |
| Realistische Aufnahme | **etwa Februar bis Mai 2027** [unsicher – hängt von freiwilligen Prüfern ab] |

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
| `versionCode` (in `android/app/build.gradle.kts`) | ganze Zahl, bei **jeder** Veröffentlichung um mindestens 1 höher, nie wiederverwenden | jetzt `2` → erste F-Droid-Version z. B. `3` |
| `versionName` | dreiteilig, passend zum Tag | `2.0.0` (gesetzt) |
| Git-Tag | `v` + `versionName`, auf dem Commit, aus dem gebaut wird | `v2.0.0` |
| Änderungshinweis | `fastlane/metadata/android/de-DE/changelogs/<versionCode>.txt` (und `en-US`) | `changelogs/3.txt` |

- Tags für Testversionen bitte **anders** benennen (z. B. `test-1`, siehe [`EINRICHTUNG.md`](EINRICHTUNG.md),
  Schritt 8.1), damit F-Droid sie nicht für Veröffentlichungen hält.
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
       versionCode: 3
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
   CurrentVersionCode: 3
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
| bis Dezember 2026 | Testphase mit Test-APKs; Fehler beheben |
| Dezember 2026 – Januar 2027 | Release-Schlüssel erzeugen, Release-Workflow, Reproduzierbarkeit prüfen (`dependenciesInfo` ist schon abgeschaltet); Fastlane-Texte und Bildschirmfotos; `versionCode` erhöhen und Tag setzen; ggf. Export/Import des Verlaufs in der Android-App |
| ab Mitte Februar 2027 | 3 Monate aktive Entwicklung erreicht → Merge Request bei `fdroiddata` |
| Februar – Mai 2027 | Prüfung durch Freiwillige, Rückfragen beantworten [unsicher: Dauer] |
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
- [ ] Fastlane-Texte (`de-DE`, `en-US`), Icon, Bildschirmfotos, Änderungshinweise
- [ ] Datenschutzerklärung unter fester Adresse (siehe [`DATENSCHUTZ_ENTWURF.md`](DATENSCHUTZ_ENTWURF.md))
- [ ] Umstieg der Testerinnen und Tester angekündigt (Deinstallation, Datenverlust) oder Export in der Android-App gebaut
- [ ] 3 Monate aktive Entwicklung erreicht (ab etwa 9. Februar 2027)
- [ ] Merge Request „New app: Drink With Me“ bei `fdroiddata`

## Quellen

- F-Droid: [Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy/),
  [Anti-Features](https://f-droid.org/docs/Anti-Features/),
  [Submitting to F-Droid – Quick Start Guide](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/),
  [Reproducible Builds](https://f-droid.org/docs/Reproducible_Builds/),
  [All About Descriptions, Graphics, and Screenshots](https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/),
  [Neues Anti-Feature TetheredNet (Juli 2024)](https://f-droid.org/en/2024/07/25/twif.html)
- Google: [Android developer verification](https://developer.android.com/developer-verification),
  [FAQ](https://developer.android.com/developer-verification/guides/faq),
  [Limited distribution](https://developer.android.com/developer-verification/guides/limited-distribution),
  [Open-Source-Registrierung](https://developer.android.com/developer-verification/guides/open-source-app-registration),
  [Blog, Juni 2026](https://developer.android.com/blog/posts/android-developer-verification-building-a-safer-ecosystem-together)
