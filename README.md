# Drink With Me 🥂

Teile mit Freund:innen, was du gerade trinkst – als native App für **iOS (Swift/SwiftUI)**
und **Android (Kotlin/Jetpack Compose)**. Auf Android gibt es zusätzlich **Trinkrunden**: Freundesgruppen,
in denen ihr live seht, was die anderen trinken – mit Push-Nachricht.

## Funktionen

- Getränk erfassen: Name, Kategorie (Wasser, Kaffee, Tee, Saft, Softdrink, Bier, Wein, Cocktail, Sonstiges),
  Menge in ml und optionale Notiz. Der Zeitpunkt ist nicht wählbar – es wird immer der aktuelle
  Zeitpunkt („jetzt“) gespeichert. Das stellt bereits die Fachlogik sicher (`DrinkDraft` hat kein Zeitfeld).
- Verlauf aller Getränke, neueste zuerst, mit Löschen
- **Teilen** eines einzelnen Getränks über das System-Share-Sheet (WhatsApp, Instagram, Signal, Mail …):
  > 🍷 Ich trinke gerade Rotwein (150 ml) um 14:30 Uhr. „Mit Freunden“ Trink mit mir! #DrinkWithMe
- **Tag teilen**: Zusammenfassung aller heutigen Getränke inklusive Gesamtmenge
- Lokale Speicherung als JSON-Datei auf dem Gerät

### Trinkrunden (Android)

- **Anmelden mit Google** (Firebase Authentication über den Android Credential Manager)
- **Runde erstellen** (Name 1–30 Zeichen) → 6-stelliger **Einladungscode** zum Teilen oder Kopieren;
  **beitreten** per Code; man kann in mehreren Runden sein (bis zu 50 Mitglieder pro Runde); **verlassen**
  jederzeit – verlässt das letzte Mitglied die Runde, wird sie samt Getränken gelöscht
- Neue Getränke werden **automatisch in allen eigenen Runden geteilt** – außer mit dem Schalter
  **„Privat (nicht mit Freunden teilen)“**. Löschen entfernt ein Getränk auch wieder aus den Runden.
- Reiter **„Freunde“**: Live-Feed einer gewählten Runde (neueste 100 Getränke)
- **Push-Nachricht** an alle anderen Mitglieder, z. B. „Anna trinkt gerade – ☕️ Cappuccino (200 ml) · Stammtisch“
- Navigation unten: **Meine Getränke** · **Freunde** · **Runden** (inkl. Konto und Abmelden)
- Ohne eingerichteten Server (keine `android/app/google-services.json`) läuft die App wie bisher **nur lokal**
  und zeigt bei den Trinkrunden „Server noch nicht eingerichtet“.
- **iOS bleibt vorerst lokal** – ohne Anmeldung und ohne Trinkrunden.

## Dokumentation

| Dokument | Inhalt |
|---|---|
| [`docs/FIREBASE_EINRICHTUNG.md`](docs/FIREBASE_EINRICHTUNG.md) | **Schritt-für-Schritt-Anleitung** (für Nicht-Programmierer): Firebase-Projekt, Blaze-Tarif und Budget, Google-Anmeldung, Datenbank in Frankfurt, `google-services.json`, Dienstkonto, GitHub-Secret, Deploy, Test, Fehlerbehebung |
| [`docs/TRINKRUNDEN_ARCHITEKTUR.md`](docs/TRINKRUNDEN_ARCHITEKTUR.md) | Verbindlicher Vertrag: Datenmodell, Sicherheitsregeln, Cloud Functions, Region, App-Verhalten |
| [`docs/DATENSCHUTZ_ENTWURF.md`](docs/DATENSCHUTZ_ENTWURF.md) | **Entwurf** einer Datenschutzerklärung (mit Platzhaltern, keine Rechtsberatung) |
| [`firebase/README.md`](firebase/README.md) | Backend für Entwickler: Aufbau, lokale Tests mit Emulatoren, Versionen |

## Projektstruktur

```
ios/
├── project.yml                  XcodeGen-Spezifikation der App
├── DrinkWithMe/                 SwiftUI-App (Präsentation + Composition Root)
│   ├── App/                     DrinkWithMeApp, AppContainer
│   └── Presentation/            List- und AddDrink-Views + ViewModels
└── DrinkWithMeCore/             Swift Package: Domain, Use Cases, Sharing, Daten + Tests
android/
├── core/                        Reines Kotlin/JVM-Modul: Domain, Use Cases, Sharing + Tests
│   └── …/core/social/           Trinkrunden-Kern: Modelle, Einladungscode, Ports, Use Cases,
│                                PublishingDrinkRepository (ohne Android/Firebase)
└── app/                         Android-App: Compose-UI, ViewModels, JSON-Repository, Share-Intent
    ├── …/app/social/firebase/   Firebase-Adapter der Ports (Auth, Firestore, Functions, Credential Manager)
    ├── …/app/push/              Push: Kanal „drinks“, Token-Registrierung, Anzeige im Vordergrund
    ├── …/app/ui/                Meine Getränke · Freunde · Runden
    └── google-services.json     (nicht im Repo, bis Firebase eingerichtet ist – siehe Anleitung)
firebase/                        Backend der Trinkrunden
├── firebase.json, .firebaserc   Konfiguration (Regeln, Indizes, Functions, Emulatoren)
├── firestore.rules              Sicherheitsregeln
├── firestore.indexes.json
└── functions/                   Cloud Functions: TypeScript, Node 22, 2nd gen, Region europe-west3 + Tests
docs/                            Anleitung, Architektur, Datenschutz-Entwurf
.github/workflows/
├── android.yml                  Android: Tests + Debug-APK (Artifact „DrinkWithMe-android-apk“)
├── ios.yml                      iOS: Tests + unsignierte IPA (Artifact „DrinkWithMe-ios-unsigned“)
├── firebase.yml                 Backend: Build, Lint, Unit- und Emulator-Tests bei jedem Push
└── firebase-deploy.yml          Backend bereitstellen – nur manuell (Actions → „Firebase Deploy“ → Run workflow)
```

Beide Plattformen haben **dieselbe Architektur und dieselben Namen**:
`Drink`, `DrinkDraft`, `DrinkCategory`, `DrinkReader`/`DrinkWriter` bzw. `DrinkReading`/`DrinkWriting`,
`LogDrink`-, `FetchDrinks`-, `DeleteDrink`- und `ShareDrinks`-Use-Cases und den `GermanShareFormatter`.
Die Trinkrunden (`com.drinkwithme.core.social`) gibt es bisher nur im Kotlin-Kern.

## SOLID-Prinzipien im Code

| Prinzip | Umsetzung |
|---|---|
| **S** – Single Responsibility | Jede Klasse hat genau eine Aufgabe: `DefaultDrinkValidator` prüft, `DefaultLogDrinkUseCase` erfasst, `GermanShareFormatter` formatiert, `JSONFileDrinkRepository`/`JsonFileDrinkRepository` speichert, Views zeigen nur an. Bei den Trinkrunden ebenso: `GroupDrinkMapper` übersetzt zwischen Getränken/Feed-Einträgen und Firestore-Dokumenten, `FunctionsErrorTranslator` übersetzt Server-Fehler in deutsche Meldungen, `FirestoreDrinkPublisher` teilt nur. |
| **O** – Open/Closed | Neue Share-Stile (z. B. englisch oder nur Emojis) entstehen als neue `DrinkMessageFormatting`/`DrinkMessageFormatter`-Implementierung, neue Speicherorte als neues Repository – ohne bestehenden Code zu ändern. So kamen auch die Trinkrunden dazu: `PublishingDrinkRepository` ist ein **Decorator** um das lokale `DrinkRepository` (speichert lokal und teilt `FRIENDS`-Getränke im Hintergrund) – `LogDrink`- und `DeleteDrink`-Use-Case blieben unverändert. |
| **L** – Liskov Substitution | `InMemoryDrinkRepository`, das JSON-Repository und `PublishingDrinkRepository` sind überall austauschbar; Tests und Previews nutzen die In-Memory-Variante, die App die Datei-Variante – mit Server zusätzlich eingepackt in den Decorator. |
| **I** – Interface Segregation | Lesen (`DrinkReading`/`DrinkReader`) und Schreiben (`DrinkWriting`/`DrinkWriter`) sind getrennt; Einzel- und Tages-Formatter sind getrennte Protokolle. `FetchDrinks` kennt nur das Lese-Interface, `DeleteDrink` nur das Schreib-Interface. Die Trinkrunden-Ports sind ebenso klein: `CurrentUserProvider`, `GroupReader`, `GroupMembershipService`, `FeedReader`, `DrinkPublisher`. |
| **D** – Dependency Inversion | ViewModels und Use Cases hängen nur von Protokollen/Interfaces ab. Konkrete Klassen werden ausschließlich im Composition Root (`AppContainer`) verdrahtet. Uhrzeit (`DateProviding` / `java.time.Clock`) und ID-Erzeugung sind ebenfalls injiziert und damit testbar. Der Kotlin-Kern definiert die **Ports** der Trinkrunden und kennt Firebase nicht; die **Firebase-Adapter** (`FirestoreGroupReader`, `FirestoreFeedReader`, `FirestoreDrinkPublisher`, `FirebaseGroupMembershipService` …) liegen im App-Modul und werden in `FirebaseSocialModuleFactory`/`AppContainer` verdrahtet. Im Backend übernimmt `firebase/functions/src/container.ts` diese Rolle. |

## iOS bauen (Swift)

Voraussetzungen: macOS mit Xcode 15 oder neuer, iOS 16+.

```bash
brew install xcodegen
cd ios
xcodegen generate          # erzeugt DrinkWithMe.xcodeproj
open DrinkWithMe.xcodeproj # Schema „DrinkWithMe“ auf Simulator/iPhone starten
```

Tests der Geschäftslogik:

```bash
cd ios/DrinkWithMeCore
swift test
```

Ohne Mac: GitHub Actions baut bei jedem Push eine unsignierte IPA (Actions → „iOS“ → Artifact
`DrinkWithMe-ios-unsigned`), die vor der Installation noch mit einer Apple-ID signiert werden muss.

## Android bauen (Kotlin)

Voraussetzungen: Android Studio (Ladybug oder neuer) bzw. Android SDK 35 und JDK 17+.

```bash
cd android
./gradlew :core:test               # Tests der Geschäftslogik (inkl. Trinkrunden-Kern)
./gradlew :app:testDebugUnitTest   # Tests der App-Logik (Mapper, Formatierer, ViewModels)
./gradlew :app:assembleDebug       # APK unter app/build/outputs/apk/debug/
./gradlew :app:installDebug        # auf verbundenes Gerät/Emulator installieren
```

Alternativ den Ordner `android/` in Android Studio öffnen und „Run“ drücken.

Ohne eigenen Rechner: GitHub Actions baut bei jedem Push eine signierte Debug-APK
(Actions → „Android“ → Artifact `DrinkWithMe-android-apk`). Alle APKs sind mit dem festen Schlüssel
`android/app/debug.keystore` signiert und lassen sich deshalb als Update installieren.

**Trinkrunden aktivieren:** `android/app/google-services.json` aus der Firebase-Konsole ablegen – ist sie
vorhanden, wird das google-services-Plugin automatisch angewendet. Die vollständige Einrichtung (inklusive
Server) beschreibt [`docs/FIREBASE_EINRICHTUNG.md`](docs/FIREBASE_EINRICHTUNG.md).

## Firebase-Backend (Trinkrunden)

- **Tests:** laufen bei jedem Push automatisch (`.github/workflows/firebase.yml`); lokal siehe
  [`firebase/README.md`](firebase/README.md).
- **Bereitstellen:** manuell über GitHub Actions → „Firebase Deploy“ → „Run workflow“
  (`.github/workflows/firebase-deploy.yml`). Benötigt das Repository-Secret `FIREBASE_SERVICE_ACCOUNT`
  (JSON-Schlüssel eines Dienstkontos) und die Repository-Variable `FIREBASE_PROJECT_ID`.
- **Region:** Firestore-Datenbank und alle Functions in `europe-west3` (Frankfurt).

## Ideen für später

- Trinkrunden auch für iOS (Firebase-Adapter für die gleichen Ports)
- Konto direkt in der App löschen
- Foto zum Getränk, Standort („Wo trinkst du?“)
- Statistiken (Wasser-Ziel pro Tag, Koffein-Tracker)
