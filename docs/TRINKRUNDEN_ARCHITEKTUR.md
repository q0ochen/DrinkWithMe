# Trinkrunden – Architektur & Schnittstellenvertrag

Verbindlicher Vertrag zwischen Android-App, Firebase-Backend (Regeln + Cloud Functions)
und Kotlin-Kern. **Feldnamen, Pfade, Regionen und Funktionsnamen sind exakt so zu verwenden.**

## Produktentscheidungen

| Thema | Entscheidung |
|---|---|
| Verbinden | **Trinkrunden** mit 6-stelligem Einladungscode; man kann in mehreren Runden sein |
| Anmeldung | **Google-Konto** (Firebase Auth, Android Credential Manager) |
| Sichtbarkeit | Getränke werden **automatisch** in allen eigenen Runden geteilt; Schalter **„Privat“** pro Getränk |
| Benachrichtigung | **Push** an alle anderen Rundenmitglieder, sobald jemand ein geteiltes Getränk einträgt |
| Plattform | Zunächst **Android**. iOS bleibt vorerst lokal (ohne Trinkrunden) |
| Backend | Google Firebase: Auth, Cloud Firestore, Cloud Functions (2nd gen), Cloud Messaging; Tarif **Blaze** (wegen Functions) |
| Region | Firestore-Datenbank **`europe-west3`** (Frankfurt), alle Functions **`europe-west3`** |

## Kotlin-Kern (`android/core`, reines Kotlin – fertig, getestet)

- `domain/Drink.kt`: `Drink.visibility` und `DrinkDraft.visibility` vom Typ `DrinkVisibility { FRIENDS, PRIVATE }`, Standard `FRIENDS`.
- `social/SocialModels.kt`: `UserProfile(id, displayName, photoUrl?)`, `GroupMember(userId, displayName)`,
  `DrinkGroup(id, name, inviteCode: InviteCode, ownerId, members: List<GroupMember>)`,
  `FeedEntry(drinkId, groupId, author: GroupMember, name, category: DrinkCategory, volumeInMilliliters, note?, consumedAt: Instant)`.
- `social/InviteCode.kt`: `InviteCode.parse(raw): InviteCode?` – 6 Zeichen aus `ABCDEFGHJKMNPQRSTUVWXYZ23456789`, normalisiert (Großschreibung, ohne Leerzeichen/Bindestriche).
- `social/SocialPorts.kt` (Schnittstellen, Implementierung im App-Modul):
  - `CurrentUserProvider { val currentUser: Flow<UserProfile?> }`
  - `GroupReader { fun observeMyGroups(): Flow<List<DrinkGroup>> }`
  - `GroupMembershipService { suspend createGroup(name): DrinkGroup; suspend joinGroup(code: InviteCode): DrinkGroup; suspend leaveGroup(groupId) }` – `joinGroup` wirft `GroupNotFoundException` bei unbekanntem Code.
  - `FeedReader { fun observeFeed(groupId, limit): Flow<List<FeedEntry>> }`
  - `DrinkPublisher { suspend publish(drink); suspend unpublish(drinkId) }` – ohne Anmeldung/Runde: nichts tun.
- `social/SocialExceptions.kt`: `SocialValidationException.{EmptyGroupName, GroupNameTooLong, InvalidInviteCode}`, `GroupNotFoundException` – Meldungstexte sind deutsch und werden direkt angezeigt.
- `social/usecase/*`: `CreateGroupUseCase` (Name 1–30 Zeichen), `JoinGroupUseCase(rawCode)`, `LeaveGroupUseCase`, `ObserveMyGroupsUseCase`, `ObserveFeedUseCase(groupId)` (Limit 100) – jeweils als Interface + `Default…`-Implementierung, `suspend operator fun invoke`.
- `social/PublishingDrinkRepository(local, publisher, scope, onPublishError)`: Decorator um das lokale `DrinkRepository`. `save` speichert lokal und teilt `FRIENDS`-Getränke im Hintergrund; `delete` löscht lokal und ruft `unpublish`.

## Firestore-Datenmodell

```
users/{uid}
  displayName: string (1–50)       – vom Client bei der Anmeldung geschrieben (set/merge)
  photoUrl:    string | null
  updatedAt:   timestamp (serverTimestamp)

users/{uid}/tokens/{fcmToken}        – Dokument-ID = FCM-Token
  platform:  "android"
  updatedAt: timestamp (serverTimestamp)

groups/{groupId}                     – NUR von Cloud Functions geschrieben
  name:       string (1–30)
  inviteCode: string (6 Zeichen)
  ownerId:    string
  memberIds:  string[]               – max. 50
  members:    map uid -> { displayName: string }
  createdAt:  timestamp

groups/{groupId}/drinks/{drinkId}    – vom Client erstellt; Dokument-ID = lokale Drink-ID
  drinkId:             string  (== Dokument-ID)
  authorId:            string  (== request.auth.uid)
  authorName:          string  (1–50)
  name:                string  (1–50)
  category:            string  (DrinkCategory.name, z. B. "BEER")
  volumeInMilliliters: int     (1–5000)
  note:                string (≤ 280) | null
  consumedAt:          timestamp (zwischen request.time − 24 h und request.time + 5 min)
  createdAt:           timestamp (== request.time, also serverTimestamp)

inviteCodes/{code}                   – NUR Cloud Functions; kein Client-Zugriff
  groupId: string
```

Kategorien: `WATER, COFFEE, TEA, JUICE, SOFT_DRINK, BEER, WINE, COCKTAIL, OTHER`.

## Sicherheitsregeln (`firebase/firestore.rules`)

- `users/{uid}` und `users/{uid}/tokens/{token}`: lesen/schreiben **nur** der Nutzer selbst; Felder validiert.
- `groups/{groupId}`: lesen nur Mitglieder (`request.auth.uid in resource.data.memberIds`; muss auch für die
  Abfrage `where("memberIds", "array-contains", uid)` funktionieren); **keine** Client-Schreibrechte.
- `groups/{groupId}/drinks/{drinkId}`: lesen nur Mitglieder; erstellen nur Mitglieder mit eigener `authorId`
  und gültigen Feldern (exakt die obigen Felder, `hasOnly` + `hasAll`); **kein** Update;
  löschen durch den Autor **oder** wenn das Dokument nicht existiert (`resource == null`), damit
  `unpublish` per Batch über alle Runden löschen kann.
- `inviteCodes/**`: kein Client-Zugriff.
- Alles andere: verboten.

## Cloud Functions (`firebase/functions`, TypeScript, Node 22, 2nd gen, Region `europe-west3`)

Aufrufbare Funktionen (`onCall`, Anmeldung erforderlich, sonst `unauthenticated`):

| Name | Eingabe | Antwort | Fehler |
|---|---|---|---|
| `createGroup` | `{ name: string }` | `{ group: GroupDto }` | `invalid-argument` (Name leer/> 30) |
| `joinGroup` | `{ code: string }` | `{ group: GroupDto }` | `invalid-argument` (Format), `not-found` (unbekannter Code), `resource-exhausted` (50 Mitglieder) |
| `leaveGroup` | `{ groupId: string }` | `{}` | idempotent; verlässt das letzte Mitglied die Runde, werden Runde, Code und Getränke gelöscht; verlässt der Besitzer, wird `ownerId` an ein verbleibendes Mitglied übertragen |

`GroupDto = { id, name, inviteCode, ownerId, members: [{ userId, displayName }] }`.
Anzeigename eines Mitglieds: `users/{uid}.displayName`, sonst `request.auth.token.name`, sonst `"Unbekannt"`.
Codes werden serverseitig eindeutig erzeugt (Transaktion auf `inviteCodes/{code}`).
`joinGroup` ist idempotent (bereits Mitglied → Runde zurückgeben).

Trigger `onGroupDrinkCreated` (`onDocumentCreated("groups/{groupId}/drinks/{drinkId}")`):
- Empfänger: `memberIds` ohne `authorId`; Tokens aus `users/{uid}/tokens`.
- `sendEachForMulticast` mit
  - `notification.title`: `"{authorName} trinkt gerade"`,
  - `notification.body`: `"{emoji} {name} ({Menge}) · {Rundenname}"` – Menge wie `VolumeFormatter` („250 ml“, „1,5 l“),
  - `android.notification.channelId = "drinks"`, `android.notification.tag = drinkId`
    (gleiche Getränke aus mehreren gemeinsamen Runden ersetzen sich statt doppelt zu erscheinen),
  - `data = { type: "drink", groupId, drinkId }`.
- Ungültige Tokens (`messaging/registration-token-not-registered`, `messaging/invalid-registration-token`) werden gelöscht.

## Android-App (`android/app`)

- Firebase ist **optional konfiguriert**: Das Gradle-Plugin `com.google.gms.google-services` wird nur angewendet,
  wenn `android/app/google-services.json` existiert. Ohne Datei baut und läuft die App wie bisher (nur lokal);
  die Trinkrunden-Bereiche zeigen dann einen Hinweis „Server noch nicht eingerichtet“.
  Laufzeitprüfung: `FirebaseApp.getApps(context).isNotEmpty()`.
- Google-Anmeldung über Credential Manager (`GetGoogleIdOption`) mit der Web-Client-ID aus der von google-services
  erzeugten Ressource `default_web_client_id` (zur Laufzeit per `resources.getIdentifier` nachschlagen, da sie ohne
  Konfiguration nicht existiert) → `GoogleAuthProvider.getCredential(idToken, null)` → Firebase Auth.
- Functions-Client: `Firebase.functions("europe-west3")`.
- Navigation unten: **Meine Getränke** · **Freunde** (Feed einer gewählten Runde, live) · **Runden** (erstellen, beitreten,
  Code teilen/kopieren, verlassen; Konto: angemeldet als …, Abmelden).
- Eintragen: zusätzlicher Schalter **„Privat (nicht mit Freunden teilen)“**.
- Push: `FirebaseMessagingService` (Token registrieren, Vordergrund-Nachrichten selbst anzeigen),
  Kanal `"drinks"`, Laufzeit-Berechtigung `POST_NOTIFICATIONS` (Android 13+), Token beim Abmelden löschen.
- Signatur: fester Debug-Schlüssel `android/app/debug.keystore` – in Firebase als SHA-1/SHA-256 hinterlegen:
  - SHA-1: `F9:FE:D7:35:CC:E7:E0:6C:CE:04:67:D0:E4:52:0E:52:8F:3C:34:0A`
  - SHA-256: `BE:67:02:0F:5D:85:35:B0:17:3B:3B:F3:CB:70:4F:65:87:CB:56:E2:C4:1F:B0:EB:79:59:41:15:13:B1:1C:85`

## Verzeichnisse & CI

```
firebase/
  firebase.json            – firestore (rules, indexes, location-unabhängig), functions (source "functions"), emulators
  .firebaserc              – default: "demo-drinkwithme" (Platzhalter; Deploy nutzt --project aus GitHub-Variable)
  firestore.rules
  firestore.indexes.json
  functions/               – package.json (+ package-lock.json), tsconfig.json, src/, test/
.github/workflows/firebase.yml         – Build + Unit-Tests + Emulator-Tests (Regeln & Functions) bei jedem Push
.github/workflows/firebase-deploy.yml  – manuell: Regeln + Functions deployen
                                         (Secret FIREBASE_SERVICE_ACCOUNT, Variable FIREBASE_PROJECT_ID)
docs/FIREBASE_EINRICHTUNG.md           – Schritt-für-Schritt-Anleitung (deutsch, für Nicht-Programmierer)
```
