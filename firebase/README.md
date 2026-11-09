# Firebase-Backend der Trinkrunden

Sicherheitsregeln und Cloud Functions für die **Trinkrunden** von Drink With Me.
Verbindlicher Vertrag (Feldnamen, Pfade, Funktionsnamen, Region): [`docs/TRINKRUNDEN_ARCHITEKTUR.md`](../docs/TRINKRUNDEN_ARCHITEKTUR.md).
Die Einrichtung des echten Firebase-Projekts beschreibt [`docs/FIREBASE_EINRICHTUNG.md`](../docs/FIREBASE_EINRICHTUNG.md).

## Aufbau

```
firebase/
├── firebase.json            Regeln, Indizes, Functions (Quelle "functions", Build vor dem Deploy), Emulatoren
├── .firebaserc              Standardprojekt "demo-drinkwithme" (nur Platzhalter für die Emulatoren)
├── firestore.rules          Sicherheitsregeln
├── firestore.indexes.json   leer – beide Abfragen der App kommen mit automatischen Einzelfeld-Indizes aus
├── package.json             Firebase CLI in fester Version (für Emulatoren und Deploy)
└── functions/               Cloud Functions: TypeScript, Node 22, 2nd gen, Region europe-west3
    ├── src/
    │   ├── index.ts         Registriert die Functions (setGlobalOptions, onCall, onDocumentCreated)
    │   ├── container.ts     Composition Root: verdrahtet die konkreten Klassen
    │   ├── config.ts        Region, Instanz-Obergrenze, Trigger-Pfad
    │   ├── domain/          Reine Logik ohne Firebase: Einladungscodes, Namen, Mitgliedschaft,
    │   │                    Mengenformat, Kategorie-Emojis, Push-Text, GroupDto, Fehler
    │   ├── groups/          GroupService (Schnittstelle) + FirestoreGroupService (Transaktionen)
    │   ├── notifications/   DrinkNotifier + Ports, FCM-Sender, Emulator-Sender
    │   ├── firestore/       Firestore-Adapter (Tokens, Runden, Profile, Sammlungsnamen)
    │   └── handlers/        Callable-Adapter: Anmeldung prüfen, Fehler in HttpsError übersetzen
    └── test/
        ├── unit/            Unit-Tests ohne Emulatoren (npm test)
        └── emulator/        Tests gegen Auth-, Firestore- und Functions-Emulator (npm run test:emulator)
```

### Functions

| Name | Art | Eingabe → Antwort | Fehler |
|---|---|---|---|
| `createGroup` | Callable | `{ name }` → `{ group: GroupDto }` | `unauthenticated`, `invalid-argument` |
| `joinGroup` | Callable | `{ code }` → `{ group: GroupDto }` | `unauthenticated`, `invalid-argument`, `not-found`, `resource-exhausted` |
| `leaveGroup` | Callable | `{ groupId }` → `{}` | `unauthenticated`, `invalid-argument` (kaputte ID) |
| `onGroupDrinkCreated` | Firestore-Trigger | `groups/{groupId}/drinks/{drinkId}` angelegt → Push an alle anderen Mitglieder | – |

Die Fehlermeldungen sind deutsch und entsprechen den Texten im Kotlin-Kern.
Einladungscodes werden in einer Transaktion auf `inviteCodes/{code}` reserviert (bis zu 10 Versuche;
gelingt das nie, kommt `aborted`). Beitreten und Verlassen sind idempotent. Verlässt das letzte Mitglied
die Runde, werden Runde, Code und alle Getränke gelöscht; verlässt der Besitzer die Runde, übernimmt
das am längsten dabei gewesene Mitglied.

### SOLID im Backend

- **Single Responsibility:** Regeln (`domain/membership.ts`), Formatierung (`domain/volumeFormatter.ts`,
  `domain/drinkNotification.ts`), Speicherung (`FirestoreGroupService`) und Transport (`handlers/callables.ts`)
  sind getrennt.
- **Open/Closed & Liskov:** Neue Versandwege entstehen als weiterer `NotificationSender`
  (heute `FcmNotificationSender` und `EmulatorOutboxSender`).
- **Interface Segregation:** kleine Ports – `DeviceTokenStore`, `GroupLookup`, `NotificationSender`,
  `ProfileReader`, `InviteCodeGenerator`, `Logger`.
- **Dependency Inversion:** Nur `container.ts` kennt die konkreten Klassen; Zufall (`crypto.randomInt`),
  Firestore und Messaging werden injiziert. Dadurch ist z. B. der `DrinkNotifier` (Empfänger, Aufräumen
  ungültiger Tokens) mit Fakes testbar.

**Grenze beim Teilen:** Die Regel für neue Getränke liest pro Runde genau ein Runden-Dokument (`get()`).
Firestore erlaubt höchstens 20 solcher Zugriffe pro Batch – ein Batch darf ein Getränk also in höchstens
**20 Runden** gleichzeitig anlegen (bei mehr Runden in mehrere Batches aufteilen). Löschen hat diese Grenze nicht.

**Push im Emulator:** Cloud Messaging lässt sich nicht emulieren. Läuft die Funktion im Emulator
(`FUNCTIONS_EMULATOR=true`), schreibt sie die Nachricht in die Sammlung `emulatorOutbox`; Tokens mit dem
Präfix `unregistered-` gelten dort als abgemeldet. In Produktion wird immer FCM verwendet.

## Lokal testen

Voraussetzungen: **Node.js 22** und für die Emulatoren **Java 21** oder neuer.

```bash
cd firebase/functions
npm ci
npm run build        # TypeScript → lib/
npm run lint         # ESLint (typgestützt)
npm run typecheck    # TypeScript inkl. Tests
npm test             # Unit-Tests, ohne Emulatoren

cd ..                # firebase/
npm ci               # Firebase CLI in fester Version
npx firebase emulators:exec --project demo-drinkwithme --only auth,firestore,functions \
  "npm --prefix functions run test:emulator"
```

Kurzform für den letzten Schritt: `npm run emulators:test` (in `firebase/`).
Emulatoren zum Ausprobieren starten: `npm run emulators` (Oberfläche unter http://127.0.0.1:4000).
Vorher immer `npm run build` in `functions/` ausführen – der Functions-Emulator lädt `lib/`.

Die Emulator-Tests prüfen:

- `firestore.rules.test.ts` – jede Regel mit erlaubten und verbotenen Fällen (`@firebase/rules-unit-testing`),
- `groupService.test.ts` – Code-Kollisionen, gleichzeitige Beitritte, Mitgliederlimit, Aufräumen,
- `callables.test.ts` – `createGroup`, `joinGroup`, `leaveGroup` über den Functions-Emulator, wie die App sie aufruft,
- `drinkTrigger.test.ts` – `onGroupDrinkCreated` Ende-zu-Ende (Nachricht und Aufräumen ungültiger Tokens).

`test/unit/kotlinCore.contract.test.ts` gleicht Kategorien/Emojis, Code-Alphabet, Namenslänge und
Fehlermeldungen direkt mit den Kotlin-Dateien in `android/core` ab.

## GitHub Actions

- **`.github/workflows/firebase.yml`** („Firebase“) – bei jedem Push/Pull Request, der `firebase/**` (oder die
  gespiegelten Kotlin-Dateien) ändert, und manuell: installieren, bauen, Lint + Typprüfung, Unit-Tests,
  danach alle Emulator-Tests. Stellt nichts bereit.
- **`.github/workflows/firebase-deploy.yml`** („Firebase Deploy“) – nur manuell (Actions → Firebase Deploy →
  Run workflow): prüft zuerst, ob das Secret `FIREBASE_SERVICE_ACCOUNT` und die Variable `FIREBASE_PROJECT_ID`
  gesetzt sind, baut die Functions und führt dann
  `firebase deploy --only firestore,functions --project <FIREBASE_PROJECT_ID> --non-interactive --force` aus.

Wichtig für den Deploy:

- Die Firestore-Datenbank muss in **`europe-west3`** angelegt sein. Ein Firestore-Trigger muss in derselben
  Region laufen wie die Datenbank – sonst bricht `firebase deploy` mit
  „A firestore trigger location must match the firestore database region.“ ab.
- `--force` löscht im Projekt Functions und Indizes, die hier nicht (mehr) definiert sind.
- Beim allerersten Deploy aktiviert Firebase noch Dienste (Cloud Build, Artifact Registry, Eventarc, Cloud Run).
  Schlägt er mit einem Berechtigungsfehler des Eventarc-Dienstagenten fehl, den Workflow nach ein paar
  Minuten einfach erneut starten.

## Versionen

| Paket | Version | Hinweis |
|---|---|---|
| `firebase-functions` | 7.4.0 | 2nd-gen-API (`firebase-functions/v2/...`) |
| `firebase-admin` | 14.5.0 | benötigt Node ≥ 22 |
| `firebase-tools` | 15.35.0 | Emulatoren benötigen Java ≥ 21 |
| `@firebase/rules-unit-testing` | 5.0.2 | 6.x verlangt Node ≥ 24.12, daher die 5er-Reihe mit `firebase` 12.19.0 |
| `typescript` | 6.0.3 | `typescript-eslint` 8.72 unterstützt TypeScript < 6.1 |
| `vitest` | 5.1.0 | Testrunner für Unit- und Emulator-Tests |
