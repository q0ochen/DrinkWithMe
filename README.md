# Drink With Me 🥂

Teile mit Freund:innen, was du gerade trinkst – als native App für **iOS (Swift/SwiftUI)**
und **Android (Kotlin/Jetpack Compose)**.

## Funktionen

- Getränk erfassen: Name, Kategorie (Wasser, Kaffee, Tee, Saft, Softdrink, Bier, Wein, Cocktail, Sonstiges),
  Menge in ml und optionale Notiz (iOS zusätzlich mit Zeitpunkt)
- Verlauf aller Getränke, neueste zuerst, mit Löschen
- **Teilen** eines einzelnen Getränks über das System-Share-Sheet (WhatsApp, Instagram, Signal, Mail …):
  > 🍷 Ich trinke gerade Rotwein (150 ml) um 14:30 Uhr. „Mit Freunden“ Trink mit mir! #DrinkWithMe
- **Tag teilen**: Zusammenfassung aller heutigen Getränke inklusive Gesamtmenge
- Lokale Speicherung als JSON-Datei auf dem Gerät

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
└── app/                         Android-App: Compose-UI, ViewModels, JSON-Repository, Share-Intent
```

Beide Plattformen haben **dieselbe Architektur und dieselben Namen**:
`Drink`, `DrinkDraft`, `DrinkCategory`, `DrinkReader`/`DrinkWriter` bzw. `DrinkReading`/`DrinkWriting`,
`LogDrink`-, `FetchDrinks`-, `DeleteDrink`- und `ShareDrinks`-Use-Cases und den `GermanShareFormatter`.

## SOLID-Prinzipien im Code

| Prinzip | Umsetzung |
|---|---|
| **S** – Single Responsibility | Jede Klasse hat genau eine Aufgabe: `DefaultDrinkValidator` prüft, `DefaultLogDrinkUseCase` erfasst, `GermanShareFormatter` formatiert, `JSONFileDrinkRepository`/`JsonFileDrinkRepository` speichert, Views zeigen nur an. |
| **O** – Open/Closed | Neue Share-Stile (z. B. englisch oder nur Emojis) entstehen als neue `DrinkMessageFormatting`/`DrinkMessageFormatter`-Implementierung, neue Speicherorte (z. B. Cloud) als neues Repository – ohne bestehenden Code zu ändern. |
| **L** – Liskov Substitution | `InMemoryDrinkRepository` und das JSON-Repository sind überall austauschbar; Tests und Previews nutzen die In-Memory-Variante, die App die Datei-Variante. |
| **I** – Interface Segregation | Lesen (`DrinkReading`/`DrinkReader`) und Schreiben (`DrinkWriting`/`DrinkWriter`) sind getrennt; Einzel- und Tages-Formatter sind getrennte Protokolle. `FetchDrinks` kennt nur das Lese-Interface, `DeleteDrink` nur das Schreib-Interface. |
| **D** – Dependency Inversion | ViewModels und Use Cases hängen nur von Protokollen/Interfaces ab. Konkrete Klassen werden ausschließlich im Composition Root (`AppContainer`) verdrahtet. Uhrzeit (`DateProviding` / `java.time.Clock`) und ID-Erzeugung sind ebenfalls injiziert und damit testbar. |

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

## Android bauen (Kotlin)

Voraussetzungen: Android Studio (Ladybug oder neuer) bzw. Android SDK 35 und JDK 17+.

```bash
cd android
./gradlew :core:test          # Tests der Geschäftslogik
./gradlew :app:assembleDebug  # APK unter app/build/outputs/apk/debug/
./gradlew :app:installDebug   # auf verbundenes Gerät/Emulator installieren
```

Alternativ den Ordner `android/` in Android Studio öffnen und „Run“ drücken.

## Ideen für später

- Freunde & Feed über ein Backend: neues `DrinkRepository` (z. B. Firebase) + neuer Share-Kanal,
  ohne Domain und UI zu ändern
- Foto zum Getränk, Standort („Wo trinkst du?“)
- Statistiken (Wasser-Ziel pro Tag, Koffein-Tracker)
