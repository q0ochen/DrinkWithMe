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
├── firestore.indexes.json   keine zusammengesetzten Indizes; eine Feld-Ausnahme: Collection-Group-Index für
│                            drinks.authorId (für deleteAccount) – die App kommt mit den automatischen Indizes aus
├── package.json             Firebase CLI in fester Version (für Emulatoren und Deploy)
└── functions/               Cloud Functions: TypeScript, Node 22, 2nd gen, Region europe-west3
    ├── src/
    │   ├── index.ts         Registriert die Functions (setGlobalOptions, onCall, onDocumentCreated/-Deleted)
    │   ├── container.ts     Composition Root: verdrahtet die konkreten Klassen
    │   ├── config.ts        Region, Instanz-Obergrenze, Trigger-Pfade, Laufzeit-Obergrenze
    │   ├── domain/          Reine Logik ohne Firebase: Einladungscodes, Namen, Mitgliedschaft,
    │   │                    Schutz vor Code-Raten, Mengenformat, Kategorie-Emojis, Push-Nachricht, GroupDto, Fehler
    │   ├── groups/          GroupService + FirestoreGroupService (Transaktionen),
    │   │                    GroupCleanup + FirestoreGroupCleanup (Getränke aufgelöster Runden)
    │   ├── accounts/        AccountService + FirestoreAccountService (Konto löschen), Auth-Adapter
    │   ├── notifications/   DrinkNotifier + Ports, FCM-Sender, Emulator-Sender, ungültige Tokens
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
| `joinGroup` | Callable | `{ code }` → `{ group: GroupDto }` | `unauthenticated`, `invalid-argument` (Format), `not-found` (unbekannter Code), `failed-precondition` (Runde voll), `resource-exhausted` (zu viele falsche Codes) |
| `leaveGroup` | Callable | `{ groupId }` → `{}` | `unauthenticated`, `invalid-argument` (kaputte ID) |
| `deleteAccount` | Callable (540 s) | `{}` → `{}` | `unauthenticated` – idempotent, darf nach einem Fehler wiederholt werden |
| `onGroupDrinkCreated` | Firestore-Trigger | `groups/{groupId}/drinks/{docId}` angelegt → Daten-Nachricht an jedes andere Mitglied | – |
| `onGroupDeleted` | Firestore-Trigger (Wiederholung, 540 s) | `groups/{groupId}` gelöscht → alle Getränke der Runde löschen (`recursiveDelete`) | – |

Die Fehlermeldungen sind deutsch und entsprechen den Texten im Kotlin-Kern.
Einladungscodes haben **8 Zeichen** und werden in einer Transaktion auf `inviteCodes/{code}` reserviert
(bis zu 10 Versuche; gelingt das nie, kommt `aborted`). Beitreten und Verlassen sind idempotent.
Verlässt das letzte Mitglied die Runde, löscht die Transaktion Runde und Code; die Getränke räumt danach der
Trigger `onGroupDeleted` auf (wird bei Fehlern wiederholt, sodass nichts liegen bleibt). Verlässt der Besitzer
die Runde, übernimmt das am längsten dabei gewesene Mitglied.

**Schutz vor Code-Raten:** `joinGroup` zählt pro Nutzer unbekannte Codes in `joinAttempts/{uid}`
(`windowStart`, `failures`; nur für die Functions zugänglich). Ab **10 Fehlversuchen innerhalb einer Stunde**
(festes Fenster ab dem ersten Fehlversuch) wird jeder weitere Versuch mit `resource-exhausted` abgelehnt –
noch bevor der Code nachgeschlagen wird, also auch ein richtiger Code. Formatfehler zählen nicht, ein
erfolgreicher Beitritt setzt den Zähler nicht zurück (sonst ließe er sich mit dem Code einer eigenen Runde
zurücksetzen). Zusammen mit 31⁸ ≈ 8,5 · 10¹¹ möglichen Codes ist Raten damit aussichtslos.
**App Check** wird bewusst nicht verwendet: Die App wird als APK direkt verteilt (nicht über Google Play),
die Play-Integrity-Prüfung würde solche Installationen ablehnen.

**Konto löschen (`deleteAccount`):** verlässt alle Runden (gleiche Logik wie `leaveGroup`), löscht danach
per Collection-Group-Abfrage `drinks where authorId == uid` **alle** eigenen geteilten Getränke – auch in
Runden, die man längst verlassen hat –, dann `users/{uid}` samt `tokens`, `joinAttempts/{uid}` und zuletzt
den Firebase-Auth-Nutzer (ein bereits gelöschter Nutzer ist kein Fehler).

**Push:** Reine Daten-Nachrichten (kein `notification`-Block), je Empfänger ein `sendEachForMulticast` an
höchstens seine **5 neuesten** Tokens (`orderBy("updatedAt", "desc").limit(5)`), `android.priority = "high"`,
`data = { type, groupId, drinkId, docId, recipientId, title, body }`. Die App zeigt eine Nachricht nur, wenn
der angemeldete Nutzer `recipientId` ist. Gelöscht werden Tokens mit
`messaging/registration-token-not-registered`, `messaging/invalid-registration-token` und
`messaging/invalid-argument` – Letzteres nur, wenn die Meldung das Registrierungs-Token nennt
(ein Fehler in der Nachricht selbst löscht also nie alle Tokens).

### SOLID im Backend

- **Single Responsibility:** Regeln (`domain/membership.ts`), Formatierung (`domain/volumeFormatter.ts`,
  `domain/drinkNotification.ts`), Speicherung (`FirestoreGroupService`) und Transport (`handlers/callables.ts`)
  sind getrennt.
- **Open/Closed & Liskov:** Neue Versandwege entstehen als weiterer `NotificationSender`
  (heute `FcmNotificationSender` und `EmulatorOutboxSender`).
- **Interface Segregation:** kleine Ports – `DeviceTokenStore`, `GroupLookup`, `NotificationSender`,
  `ProfileReader`, `InviteCodeGenerator`, `GroupCleanup`, `AuthUserDeleter`, `Logger`.
- **Dependency Inversion:** Nur `container.ts` kennt die konkreten Klassen; Zufall (`crypto.randomInt`),
  Uhr (`Date.now`), Firestore, Auth und Messaging werden injiziert. Dadurch sind z. B. der `DrinkNotifier`
  (Empfänger, Aufräumen ungültiger Tokens) mit Fakes und der Schutz vor Code-Raten mit einer steuerbaren Uhr testbar.

**Regeln:** Getränke liegen unter `groups/{groupId}/drinks/{authorId}_{drinkId}`; die Regeln verlangen
`docId == request.auth.uid + "_" + drinkId`, sodass kein Mitglied die ID eines fremden Getränks „besetzen“
kann. Token-Dokument-IDs müssen wie ein FCM-Token aussehen (20–1024 Zeichen aus `A–Z a–z 0–9 _ : -`). Das
Muster `^[A-Za-z0-9_:-]{20,1024}$` aus dem Vertrag wird dabei als `size()`-Prüfung plus
`matches('^[A-Za-z0-9_:-]+$')` geschrieben, weil die Regex-Engine der Regeln (RE2) höchstens `{…,1000}` erlaubt.

**Grenze beim Teilen:** Die Regel für neue Getränke liest pro Runde genau ein Runden-Dokument (`get()`).
Firestore erlaubt höchstens 20 solcher Zugriffe pro Batch – ein Batch darf ein Getränk also in höchstens
**20 Runden** gleichzeitig anlegen (bei mehr Runden in mehrere Batches aufteilen). Löschen hat diese Grenze nicht.

**Push im Emulator:** Cloud Messaging lässt sich nicht emulieren. Läuft die Funktion im Emulator
(`FUNCTIONS_EMULATOR=true`), schreibt sie jede Nachricht (eine je Empfänger) in die Sammlung `emulatorOutbox`;
Tokens mit dem Präfix `unregistered-` gelten dort als abgemeldet, Tokens mit `malformed-` als kaputt
(`invalid-argument` mit Token-Meldung). In Produktion wird immer FCM verwendet.

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
  u. a. Token-IDs, vertragsgemäße Getränke-IDs, „Besetzen“ fremder IDs, kein Zugriff auf `joinAttempts`,
- `groupService.test.ts` – Code-Kollisionen, gleichzeitige Beitritte, Mitgliederlimit, Schutz vor Code-Raten mit
  steuerbarer Uhr, `leaveAllGroups`, Aufräumen aufgelöster Runden, seitenweises Löschen beim Konto-Löschen,
- `callables.test.ts` – `createGroup`, `joinGroup`, `leaveGroup`, `deleteAccount` über den Functions-Emulator,
  wie die App sie aufruft (inkl. Sperre nach 10 falschen Codes und Löschen des Auth-Nutzers),
- `drinkTrigger.test.ts` – `onGroupDrinkCreated` (je Empfänger eine Daten-Nachricht, höchstens 5 Tokens,
  Aufräumen ungültiger Tokens) und `onGroupDeleted` Ende-zu-Ende.

`test/unit/kotlinCore.contract.test.ts` gleicht Kategorien/Emojis, Code-Alphabet und -Länge, Namenslänge und
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
- Der Collection-Group-Index für `drinks.authorId` wird beim Deploy mit `--only firestore` angelegt und ist nach
  einigen Minuten fertig; bis dahin scheitert `deleteAccount` (die App kann es einfach erneut versuchen).
- `deleteAccount` löscht den Auth-Nutzer mit dem Laufzeit-Dienstkonto der Functions
  (`PROJEKTNUMMER-compute@developer.gserviceaccount.com`); es braucht die Berechtigung
  `firebaseauth.users.delete` (in der Standardrolle „Editor“ enthalten).
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
