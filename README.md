# Drink With Me

**Dein Getränketagebuch – für dich und mit deinen Freunden.** Drink With Me zeigt dir anschaulich, wie viel
Alkohol und Zucker du über Getränke zu dir nimmst, und hilft dir, weniger davon zu trinken. Wenn du möchtest,
sehen deine Freunde, was du gerade trinkst – und du bekommst eine Nachricht, wenn sie etwas trinken.

Gesundheit ist mindestens so wichtig wie der soziale Teil: Der Verlauf ist ein Hauptbereich, es gibt keine
Belohnungen für Alkohol, und deine Statistik verlässt nie dein Gerät.

## Plattformen

| Gerät | So bekommst du Drink With Me |
|---|---|
| **Android-Handy und -Tablet** (ab Android 8) | Android-App (APK aus GitHub, später F-Droid) |
| **Android TV / Fire TV** (Fire OS 7/8) | dieselbe APK – Bar, Verlauf und Vorlesen; Anmeldung auf dem Fernseher ist nicht eigens gebaut |
| **iPhone** (ab iOS 16.4) und jeder Browser | Web-App: [q0ochen.github.io/DrinkWithMe](https://q0ochen.github.io/DrinkWithMe/) – „Zum Home-Bildschirm“ für Benachrichtigungen (die Home-Bildschirm-App hat einen eigenen Speicher; Bar und Verlauf nimmst du per Sicherungsdatei mit) |

Eine echte iPhone-App ist erst nach der Testphase geplant.

## Android-Testversion

Die aktuelle Testversion lässt sich **ohne Anmeldung** herunterladen. Sie wird nach jedem erfolgreichen Build auf
`main` (bzw. dem Entwicklungszweig) automatisch ersetzt:
**https://github.com/q0ochen/DrinkWithMe/releases/download/testversion/DrinkWithMe.apk**

0. **Schon einmal „Drink With Me“ installiert**, z. B. selbst in Android Studio gebaut oder eine Version vom
   9. November? Dann erst **deinstallieren**: Einstellungen → Apps → Drink With Me → Deinstallieren. Sonst meldet
   Android „… da das Paket in Konflikt mit einem bestehenden Paket steht“.
1. Den Link **in Chrome** öffnen (nicht im Browser von WhatsApp, Telegram oder Gmail). Fragt Chrome, ob die Datei
   schädlich sein könnte: **Trotzdem herunterladen**. Dann **Öffnen** tippen.
2. Android fragt nach der Erlaubnis, Apps aus dieser Quelle zu installieren: **zulassen** (auf Pixel-Handys
   „Dieser Quelle vertrauen“ bzw. „Nur dieses Mal“ / „Immer zulassen“). Dann **Installieren** tippen.
3. Meldet sich **Play Protect** („App blockiert“, „unbekannte App“): **nicht** auf „OK“ tippen, sondern
   **Weitere Details → Trotzdem installieren** (oder **App scannen**).
4. **Fire TV** (Fire OS 7/8): in der App „Downloader“ den Link eingeben. Vorher in den Entwickleroptionen
   „Apps unbekannter Herkunft“ für Downloader erlauben.

Klappt es nicht, bitte den **genauen Fehlertext** (am besten einen Screenshot) schicken – die häufigsten Meldungen
und was hilft, stehen in [`docs/EINRICHTUNG.md`](docs/EINRICHTUNG.md#13-wenn-etwas-nicht-klappt).

**Automatisch aktuell bleiben (optional):** Die freie App [Obtainium](https://github.com/ImranR98/Obtainium)
installiert die Testversion direkt aus GitHub und meldet jede neue Testversion.

1. Obtainium installieren, z. B. die APK von [github.com/ImranR98/Obtainium/releases](https://github.com/ImranR98/Obtainium/releases).
2. Auf dem Android-Gerät diesen Link öffnen und den Import bestätigen:
   **[Drink With Me in Obtainium hinzufügen](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.drinkwithme.app%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fq0ochen%2FDrinkWithMe%22%2C%22author%22%3A%22q0ochen%22%2C%22name%22%3A%22Drink%20With%20Me%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Atrue%2C%5C%22versionDetection%5C%22%3Afalse%2C%5C%22releaseDateAsVersion%5C%22%3Atrue%2C%5C%22useLatestAssetDateAsReleaseDate%5C%22%3Atrue%7D%22%2C%22overrideSource%22%3Anull%7D)**.
3. In Obtainium „Drink With Me“ antippen und **Installieren** wählen. Ist die App schon installiert, steht dort
   **Aktualisieren** – das antippen.

Der Link setzt die drei Einstellungen, die für die Testversion nötig sind: „Vorabversionen einbeziehen“,
„Veröffentlichungsdatum als Version verwenden“ und „Letzten Upload eines Release-Anhangs als
Veröffentlichungsdatum verwenden“. Die Testversion hat immer denselben Namen und dieselbe Versionsnummer,
deshalb erkennt Obtainium neue Builds nur am Datum. Wer die App von Hand in Obtainium hinzufügt
(`https://github.com/q0ochen/DrinkWithMe`), schaltet diese drei Einstellungen selbst ein.

Wann die App in F-Droid kommt, steht in [`docs/F-DROID.md`](docs/F-DROID.md).

## Funktionen

- **Bar:** Du stellst dir deine eigene Bar aus über 20 Getränken zusammen (Wasser, Kaffee, Apfelschorle, Cola,
  Pils, Rotwein …) oder legst eigene an. **Ein Tipp = jetzt getrunken** – der Zeitpunkt ist immer „jetzt“.
  Danach 10 Sekunden lang ein großer „Rückgängig“-Knopf. Alle Getränke-Icons sind selbst gezeichnet.
- **Verlauf:** Zucker pro Tag (mit Linien bei 50 g und 25 g), Alkohol pro Woche (mit den Bezugswerten der DGE
  2024, ausblendbar), alkoholfreie Tage in Folge, Vergleich mit dem Vormonat, Liste aller Einträge. Alle Werte
  gibt es zusätzlich als Text. **„Als Bild teilen“** erzeugt ein PNG deiner Statistik.
- **Freunde:** Anmeldung mit Google (nur dafür nötig), Freunde per **Einladungs-Link** oder **Google-E-Mail-Adresse**
  hinzufügen (ohne dass jemand erfährt, ob es zu einer Adresse ein Konto gibt), Feed mit den Getränken deiner
  Freunde, **Benachrichtigung**, wenn ein Freund etwas trinkt. Einzelne Getränke kannst du **privat eintragen**;
  ohne Internet holt die App das Teilen später nach.
- **Sicherung (Web-App):** Bar und Verlauf als Datei speichern und wieder einlesen – z. B. vom Safari-Tab in die
  App auf dem Home-Bildschirm oder auf ein anderes Gerät. Beim Einlesen wird nichts gelöscht und nichts geteilt.
- **Sprache und Vorlesen:** Getränke per Sprache eintragen („Apfelschorle“, „rückgängig“, „wie viel heute“),
  Ereignisse laut vorlesen lassen („Anna trinkt eine Apfelschorle.“) – mit großem Knopf, gedacht auch für
  Menschen, die schlecht sehen.
- **Barrierefreiheit:** große Bedienflächen, Screenreader-Beschriftungen, Schrift bis 200 %, guter Kontrast,
  Bedienung mit Fernbedienung oder Tastatur.
- **Benachrichtigungen ohne Google-Dienste:** auf Android über UnifiedPush (empfohlen: die freie App **ntfy**),
  sonst prüft die App etwa alle 15 Minuten; in der Web-App über Web Push.

## Datenschutz in einem Absatz

Bar, vollständiger Verlauf und Statistik liegen **nur auf deinem Gerät**. Ohne Anmeldung verlässt nichts das
Gerät. Meldest du dich mit Google an, speichert der Server (Supabase, Rechenzentrum in Frankfurt) nur dein Profil
(Name und Bild-Adresse aus Google), deine Freundschaften und Anfragen (von einer eingegebenen E-Mail-Adresse nur
eine gekürzte Form und eine nicht umkehrbare Kennung, höchstens 30 Tage), Einladungs-Links, Benachrichtigungs-Abos
und die Getränke, die du teilst – und die nur mit **Name, Icon, Art, Menge in ml und Zeitpunkt**, nach **30 Tagen**
automatisch gelöscht. Gramm Alkohol, Zucker und Statistiken werden nie geteilt; alkoholische Getränke erst nach
deiner ausdrücklichen Zustimmung. Benachrichtigungen sind Ende-zu-Ende verschlüsselt. Eine Sicherungsdatei
entsteht nur, wenn du sie selbst speicherst, und bleibt bei dir. Keine Werbung, kein Tracking, keine
Analyse-Dienste. Dein Konto löschst du direkt in der App. Details:
[`docs/DATENSCHUTZ_ENTWURF.md`](docs/DATENSCHUTZ_ENTWURF.md).

## Dokumentation

| Dokument | Für wen | Inhalt |
|---|---|---|
| [`docs/EINRICHTUNG.md`](docs/EINRICHTUNG.md) | **alle** (ohne Programmierkenntnisse) | Schritt für Schritt: Supabase, Google-Anmeldung, Schlüssel, GitHub, App installieren, ntfy, iPhone, Fire TV, Testplan, Fehlerbehebung |
| [`docs/ANFORDERUNGEN.md`](docs/ANFORDERUNGEN.md) | alle | Was die App können soll und was entschieden ist |
| [`docs/ARCHITEKTUR.md`](docs/ARCHITEKTUR.md) | Entwickler | Verbindlicher Vertrag: Kern, Datenbank, Edge Functions, Apps, CI |
| [`docs/DATENSCHUTZ_ENTWURF.md`](docs/DATENSCHUTZ_ENTWURF.md) | Betreiber | **Entwurf** einer Datenschutzerklärung (Platzhalter, keine Rechtsberatung) |
| [`docs/F-DROID.md`](docs/F-DROID.md) | Betreiber | Was für die Aufnahme in F-Droid nötig ist, Zeitplan, Android-Entwicklerprüfung 2027 |
| [`supabase/README.md`](supabase/README.md) | Entwickler | Backend: Tabellen, RPCs, Edge Functions, lokal testen |
| [`web/README.md`](web/README.md) | Entwickler | Web-App: Aufbau, Befehle, Konfiguration, Routing, Web Push, Sicherung, Teilen ohne Netz |
| [`design/README.md`](design/README.md) | alle | Selbst gezeichnete Icons und Werkzeuge |

## Projektstruktur

```
android/
├── core/        Reines Kotlin/JVM (Paket com.drinkwithme.core): Bar, Trinkhistorie, Gesundheit, Statistik,
│                Teilen-Regel, Spracherkennung (Abgleich), Vorlese-Texte, Ports der Vernetzung + Tests
└── app/         Android-App (Jetpack Compose): Bar · Verlauf · Freunde · Einstellungen, Supabase per OkHttp,
                 Anmeldung per PKCE im Browser, UnifiedPush, WorkManager-Abruf, Vorlesen (TextToSpeech),
                 Spracheingabe (RecognizerIntent), Tablet/TV-Layout + Tests
                 supabase.properties = öffentliche Server-Werte (Vorlage: supabase.properties.example)
supabase/
├── migrations/  Datenbank: Tabellen, Row Level Security, Trigger, RPCs, tägliches Aufräumen (pg_cron)
├── tests/       pgTAP-Tests (Zugriffsrechte-Matrix, RPCs, Limits, Konto löschen)
├── functions/   Edge Functions (Deno): notify-friends (eigenes Web Push nach RFC 8291/8292),
│                delete-account, gemeinsamer Code und Tests
└── scripts/     VAPID-Schlüsselpaar erzeugen
web/             Web-App/PWA (Vite, TypeScript, Preact, supabase-js, IndexedDB, Service Worker):
                 Port des Kotlin-Kerns mit Golden-Tests, Supabase-Adapter, Oberfläche, Playwright-Tests
design/          Selbst gezeichnete SVG-Icons (CC0) und Werkzeuge (SVG → Android-VectorDrawable, Prüfungen)
ios/             Alte, rein lokale iPhone-App (Swift/SwiftUI) – wird in dieser Phase NICHT weiterentwickelt;
                 iPhone-Nutzer nehmen die Web-App
docs/            Einrichtung, Anforderungen, Architektur, Datenschutz-Entwurf, F-Droid
.github/workflows/  CI (siehe unten)
```

Android-App und Web-App haben **dieselben Bereiche, Texte und Regeln**: Die Web-App enthält einen Port des
Kotlin-Kerns nach TypeScript, und Golden-Tests prüfen, dass beide dieselben Ergebnisse liefern.

## So funktioniert die CI

Jeder Push und jeder Pull Request startet automatisch die passenden Prüfungen (GitHub Actions, kostenlos für
öffentliche Repositorys):

| Workflow | Wann | Was |
|---|---|---|
| **Android** (`android.yml`) | Änderungen an `android/` oder `design/` | Icons prüfen (SVG ↔ Android), Tests der Grafik-Werkzeuge, Tests des Kerns und der App, Debug-APK bauen, Signatur prüfen → Artifact **`DrinkWithMe-android-apk`**; bei einem Push auf `main` oder den Entwicklungszweig zusätzlich die öffentliche **Testversion** (Release `testversion`, siehe oben) ersetzen |
| **Supabase** (`supabase.yml`) | Änderungen an `supabase/` | Lokales Supabase in Docker starten, alle Migrationen auf leerer Datenbank, pgTAP-Tests; Edge Functions: Formatierung, Lint, Typprüfung, Deno-Tests (u. a. gegen die Beispielwerte aus RFC 8291) |
| **Web-App** (`web.yml`) | Änderungen an `web/` oder `design/icons/`; auch manuell (Run workflow) | Typprüfung, Lint, Unit-Tests (inkl. Golden-Tests gegen den Kotlin-Kern), Build, Playwright-Tests → Artifact **`DrinkWithMe-web`**; bei einem Push auf `main` oder einem manuellen Start auf `main` (Actions → „Web-App“ → Run workflow) zusätzlich **Veröffentlichung auf GitHub Pages** |
| **Supabase Deploy** (`supabase-deploy.yml`) | **nur manuell** (Actions → „Supabase Deploy“ → Run workflow) | Migrationen und Edge Functions im echten Supabase-Projekt bereitstellen; braucht die Secrets `SUPABASE_ACCESS_TOKEN`, `SUPABASE_DB_PASSWORD` und die Variable `SUPABASE_PROJECT_REF` |
| **iOS** (`ios.yml`) | Änderungen an `ios/` | Alte iOS-App: Tests und unsignierte IPA (nicht weiterentwickelt) |

Alle Debug-APKs sind mit dem festen, öffentlichen Test-Schlüssel `android/app/debug.keystore` signiert und lassen
sich deshalb als Update übereinander installieren. Für Veröffentlichungen (F-Droid) braucht es einen eigenen,
geheimen Schlüssel – siehe [`docs/F-DROID.md`](docs/F-DROID.md).

## Selbst bauen (für Entwickler)

Wer nicht programmiert, braucht das nicht: Die CI baut alles, siehe [`docs/EINRICHTUNG.md`](docs/EINRICHTUNG.md).

```bash
# Android (JDK 17, Android SDK 35)
cd android
./gradlew :core:test :app:testDebugUnitTest   # Tests
./gradlew :app:assembleDebug                  # APK unter app/build/outputs/apk/debug/

# Web-App (Node.js 22)
cd web
npm ci && npx playwright install chromium     # einmalig
npm run check                                 # Typprüfung, Lint, Tests, Build, Playwright

# Supabase (Supabase CLI, Docker, Deno 2)
supabase start && supabase test db
cd supabase/functions && deno test
```

Einzelheiten: [`supabase/README.md`](supabase/README.md), [`web/README.md`](web/README.md),
[`design/README.md`](design/README.md).

## SOLID-Prinzipien im Code

| Prinzip | Umsetzung (Beispiele aus dem Code) |
|---|---|
| **S** – Single Responsibility | `StatisticsCalculator` rechnet nur, `SpeechTexts` baut nur Vorlese-Sätze (getrennt von Anzeige-Texten), `JsonFileHistoryRepository` speichert nur, `ServerConfig.parse` prüft nur die Server-Werte, `PushMessageHandler` entscheidet nur, ob eine Push-Nachricht angezeigt wird. Die Edge Function `notify-friends` ist aufgeteilt in HTTP (`handler.ts`), Ablauf (`service.ts`), Datenbank (`supabase_adapters.ts`) und Verschlüsselung (`_shared/webpush/`). |
| **O** – Open/Closed | Das Teilen mit Freunden kam als **Decorator** dazu: `SharingHistoryRepository` legt sich um jedes `HistoryRepository`, speichert sofort lokal und teilt im Hintergrund – `LogDrinkFromBarUseCase` und `DeleteEntryUseCase` blieben unverändert (in Kotlin und im TypeScript-Port gleich). Ein anderer Push-Weg wäre eine neue `PushSender`-Umsetzung, ohne `NotifyFriendsService` zu ändern. |
| **L** – Liskov Substitution | `InMemoryHistoryRepository`, `JsonFileHistoryRepository` (Android), `IndexedDbHistoryRepository` (Web) und `SharingHistoryRepository` sind überall austauschbar, wo ein `HistoryRepository` erwartet wird; Tests nutzen die In-Memory-Variante. Ebenso `UnconfiguredAccountController` (ohne Server) und `SupabaseAccountController`. |
| **I** – Interface Segregation | Lesen und Schreiben sind getrennt: `HistoryReader`/`HistoryWriter`, `BarReader`/`BarWriter`. Die Vernetzung besteht aus kleinen Ports: `SessionProvider`, `FriendsService`, `FeedService`, `EventPublisher`; die Teilen-Entscheidung ist die Ein-Methoden-Schnittstelle `ShareDecider`. In `notify-friends`: `DrinkEventClaimer`, `FriendshipReader`, `ProfileReader`, `SubscriptionStore`, `RateLimiter`. |
| **D** – Dependency Inversion | Der Kotlin-Kern definiert nur Ports und kennt weder Android noch Supabase. Die Adapter (`SupabaseSession`, `SupabaseEventPublisher`, `SupabaseFriendsService`, `SupabaseFeedService`, `JsonFileHistoryRepository` …) liegen im App-Modul. Verdrahtet wird ausschließlich in den Kompositionswurzeln: `AppContainer` (Android), `createAppServices` in `web/src/app/container.ts` (Web) und `notify-friends/index.ts` (Edge Function). Uhrzeit (`java.time.Clock`, `systemClock`) und ID-Erzeugung (`IdGenerator`) sind injiziert und damit testbar. |

## Lizenz

**CC0 1.0 Universal** (gemeinfrei) – siehe [`LICENSE`](LICENSE). Das gilt für den Code und für alle Grafiken:
Die Icons, das App-Symbol und das TV-Banner sind **selbst gezeichnet** (keine Vorlagen, keine Icon-Sammlungen),
siehe [`design/icons/LICENSE`](design/icons/LICENSE). Die verwendeten Bibliotheken (u. a. AndroidX, OkHttp,
UnifiedPush Connector, Preact, supabase-js, idb) stehen unter ihren eigenen freien Lizenzen (Apache 2.0, MIT, ISC).
