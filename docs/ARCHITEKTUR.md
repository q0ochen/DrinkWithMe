# Drink With Me – Architektur & Schnittstellenvertrag (Version 2)

Verbindlicher Vertrag für Kotlin-Kern, Android-App, Web-App und Supabase-Backend. **Namen, Tabellen, Spalten,
Funktionen und Pfade sind exakt so zu verwenden.** Ersetzt die frühere Trinkrunden/Firebase-Architektur.
Anforderungen: [`ANFORDERUNGEN.md`](ANFORDERUNGEN.md).

## 0. Leitplanken (nicht verhandelbar)

| Regel | Folge |
|---|---|
| **F-Droid-tauglich** | Keine proprietären Bibliotheken: kein Firebase, keine `com.google.android.gms`/Play-Services, kein `googleid`, kein `credentials-play-services-auth`, kein `kotlinx-coroutines-play-services`, kein Google-Services-Gradle-Plugin, keine Analytics/Crash-Reporter. Nur freie Abhängigkeiten aus Maven Central / Google Maven (AndroidX). |
| **Kostenlos, ohne Kreditkarte** | Backend = **Supabase Free**. Keine bezahlten Dienste. |
| **Minimale Berechtigungen** | Android: nur `INTERNET`, `POST_NOTIFICATIONS` (zur Laufzeit, erst wenn sinnvoll) und was WorkManager selbst mitbringt. **Kein** `RECORD_AUDIO`, keine Kontakte, kein Standort. |
| **Lokal zuerst** | Bar und **vollständige Trinkhistorie liegen nur auf dem Gerät**. Der Server kennt nur Profil, Freundschaften, Push-Abos und **kurzlebige** geteilte Ereignisse (30 Tage). |
| **Google-Konto nur für Vernetzung** | Ohne Anmeldung funktionieren Bar, Eintragen, Verlauf, Statistik, Sprache und Vorlesen vollständig. |
| **Zeitpunkt immer „jetzt“** | Getränke werden ausschließlich mit der aktuellen Uhrzeit erfasst. |
| **Gesundheit ≥ Soziales** | Statistik/Verlauf ist ein Hauptbereich, nicht versteckt. Keine Belohnungen für Alkohol. |
| **Selbst erstellte Grafiken** | Alle Icons eigene SVGs unter CC0 (keine Grafiken aus dem Internet). |
| **Lizenz** | Code und Grafiken CC0 1.0. Keine ODbL-Daten (Open Food Facts) einbetten. |

Plattformen: Android-Handy, -Tablet, Android TV/Fire TV (Fire OS 7/8) – eine APK. iPhone/Browser: Web-App (PWA).
Echte iOS-App erst nach der Testphase.

## 1. Kotlin-Kern (`android/core`, reines Kotlin/JVM, Paket `com.drinkwithme.core`)

Bisherige Trinkrunden-Typen (`InviteCode`, `DrinkGroup`, `GroupMember`, Gruppen-Use-Cases, `PublishingDrinkRepository`
für Gruppen) **entfallen** und werden ersetzt.

### 1.1 Bar (`bar`)

```kotlin
enum class DrinkCategory { WATER, COFFEE, TEA, JUICE, SOFT_DRINK, ENERGY_DRINK, MILK, BEER, WINE, SPARKLING_WINE, SPIRIT, COCKTAIL, OTHER }

/** Schlüssel eines selbst gezeichneten Icons; identisch in Android (R.drawable.drink_<key>) und Web (icons/<key>.svg). */
enum class DrinkIcon(val key: String) {
    WATER("water"), SPARKLING_WATER("sparkling_water"), COFFEE("coffee"), TEA("tea"),
    JUICE("juice"), APPLE_SPRITZER("apple_spritzer"), COLA("cola"), LEMONADE("lemonade"),
    ICED_TEA("iced_tea"), ENERGY_DRINK("energy_drink"), MILK("milk"), MALT_BEER("malt_beer"),
    BEER("beer"), WHEAT_BEER("wheat_beer"), RADLER("radler"), RED_WINE("red_wine"), WHITE_WINE("white_wine"),
    SPARKLING_WINE("sparkling_wine"), SHOT("shot"), LONGDRINK("longdrink"), COCKTAIL("cocktail"), OTHER("other"),
}

enum class ValueSource { DEFAULT, USER }

data class BarItem(
    val id: String,
    val name: String,                 // 1–40 Zeichen
    val icon: DrinkIcon,
    val category: DrinkCategory,
    val servingMilliliters: Int,      // 1–2000
    val alcoholPercent: Double,       // Vol.-%, 0–80
    val sugarGramsPer100Ml: Double,   // 0–100
    val aliases: List<String> = emptyList(),   // für die Spracherkennung, z. B. „Schorle“
    val valueSource: ValueSource = ValueSource.DEFAULT,
    val sortOrder: Int = 0,
)
```

- `DefaultBarCatalog`: Vorschlagsliste (eigene, CC0-Werte; Zucker g/100 ml · Vol.-% · Standardportion):
  Wasser 0·0·250 · Mineralwasser 0·0·250 · Kaffee 0·0·200 · Tee 0·0·250 · Cola 10,6·0·330 · Cola zero 0·0·330 ·
  Limonade 8·0·330 · Eistee 7,5·0·500 · Energydrink 11·0·250 · Orangensaft 9·0·200 · Apfelsaft 10·0·200 ·
  Apfelschorle 5,5·0·500 · Malzbier 9·0·330 · Milch 4,8·0·200 (Zucker = Milchzucker, als Schätzwert markiert) ·
  Pils 0,3·4,9·500 · Weizenbier 0,3·5,4·500 · Alkoholfreies Bier 2,5·0,4·500 · Radler 5·2,5·500 ·
  Rotwein trocken 0,3·12·125 · Weißwein trocken 0,3·11,5·125 · Sekt trocken 2,5·11·100 · Schnaps 0·40·20 ·
  Longdrink (Spirituose + Cola) 8,7·5,3·240 · Cocktail (Schätzwert) 8·10·200.
- Beim ersten Start wählt der Nutzer aus dem Katalog seine Bar (Vorauswahl: Wasser, Kaffee, Tee, Apfelschorle, Cola,
  Pils, Rotwein). Alles ist änderbar; eigene Getränke sind möglich.
- Ports: `BarRepository { fun items(): List<BarItem>; fun save(item); fun delete(id); fun reorder(ids) }`.

### 1.2 Trinkhistorie (`log`) – nur lokal

```kotlin
/** Ein getrunkenes Getränk. Werte werden beim Eintragen aus der Bar kopiert (Snapshot), damit spätere Änderungen
 *  an der Bar die Historie nicht rückwirkend verfälschen. */
data class DrinkEntry(
    val id: String,                    // UUID
    val barItemId: String?,
    val name: String,
    val icon: DrinkIcon,
    val category: DrinkCategory,
    val volumeMilliliters: Int,
    val alcoholPercent: Double,
    val sugarGramsPer100Ml: Double,
    val consumedAt: Instant,           // immer „jetzt“
    val shareWithFriends: Boolean,
)
```

- `LogDrinkFromBarUseCase(barItem, volumeOverride? = null, shareOverride? = null)`: erzeugt `DrinkEntry` mit Uhrzeit
  „jetzt“; `shareWithFriends` ergibt sich aus der `SharingPolicy` (1.5). `UndoLastEntryUseCase` (innerhalb von 10 s
  ohne Rückfrage), `DeleteEntryUseCase`, `HistoryRepository { entries(from, to); save; delete }`.

### 1.3 Gesundheit (`health`)

- `alcoholGrams = ml × Vol.-% / 100 × 0,8`; `sugarGrams = ml × Zucker / 100`. Gerechnet wird beim Anzeigen.
- `isAlcoholic`: Vol.-% ≥ 1,2. `isSugary`: ≥ 5 g/100 ml; `isVerySugary`: ≥ 8 g/100 ml (orientiert an den Stufen der
  britischen Zuckerabgabe).
- **Alkoholfreier Tag** = kein Getränk mit `isAlcoholic`; Gramm werden trotzdem vollständig summiert.
- Bezugswerte (`HealthReferences`), nur für gesunde Erwachsene; Bänder ausblendbar, **kein Grün**:
  - Alkohol pro Woche (Mo–So) nach DGE 2024: unter 27 g „risikoarm“, 27–81 g „moderates Risiko“, über 81 g „riskant“.
    Pflichtsatz: „Es gibt keine unbedenkliche Menge Alkohol. Weniger ist besser.“
  - Zucker pro Tag: 50 g „Obergrenze für freien Zucker in der gesamten Ernährung (bei 2000 kcal)“ (durchgezogen),
    25 g „WHO: unter 25 g bringt zusätzlichen Nutzen“ (gestrichelt). Hinweis: „Getränke sind nur ein Teil davon.“
- Formulierungen: keine Diagnose, keine Prävention/Überwachung versprechen, keine „Erlaubnis-Sprache“ („noch 10 g übrig“),
  kein „gesund“ bei Alkohol. Serien/Lob nur für alkohol- bzw. zuckerfreie Tage und Rückgänge.
  Info-Text: „Diese App ist ein persönliches Getränketagebuch. Sie stellt keine Diagnose und ersetzt keine ärztliche
  Beratung. Kinder, Jugendliche, Schwangere und Stillende sollten keinen Alkohol trinken.“

### 1.4 Statistik (`stats`)

`StatisticsCalculator(clock: Clock, zone: ZoneId)` – reine Funktionen über `List<DrinkEntry>`:
- `dailySugar(days: Int)`: Liste (Datum, Gramm) – Standard 14 Tage.
- `weeklyAlcohol(weeks: Int)`: Liste (Wochenbeginn Montag, Gramm, alkoholfreie Tage) – Standard 8 Wochen.
- `today()`: Zucker g, Alkohol g, Anzahl zuckerhaltiger/alkoholischer Getränke, Gesamtmenge ml.
- `monthComparison()`: Zucker und Alkohol der letzten 30 Tage vs. der 30 Tage davor (Prozent, `null` ohne Vergleichsbasis).
- `movingAverage4Weeks()`: Alkohol g/Woche und Zucker g/Tag.
- `alcoholFreeStreak()`: zusammenhängende alkoholfreie Tage bis heute.

### 1.5 Teilen-Regel (`sharing`)

- `SharingPolicy`: Ein Eintrag wird mit Freunden geteilt, wenn angemeldet **und** das Getränk nicht als privat markiert
  ist **und** (nicht alkoholisch **oder** der Nutzer hat dem Teilen alkoholischer Getränke einmalig zugestimmt).
  Beim ersten alkoholischen Getränk fragt die App einmal: „Alkoholische Getränke mit Freunden teilen?“ (Ja/Nein,
  später in den Einstellungen änderbar). Mengen in Gramm, Zucker und Statistiken werden **nie** geteilt.
- Geteilt werden nur: Name, Icon, Kategorie, Menge in ml, Zeitpunkt.

### 1.6 Sprache (`voice`) und Vorlesen (`speech`)

- `VoiceCommandMatcher.match(candidates: List<String>, bar: List<BarItem>): VoiceMatch` mit
  `sealed interface VoiceMatch { Drink(item); Ambiguous(first, second); Command(cmd); NoMatch }` und
  `enum class VoiceCommand { UNDO, READ_ALOUD, TODAY }` („rückgängig“/„nein“, „vorlesen“, „wie viel heute“).
  Normalisierung: Kleinschreibung, ä→ae, ö→oe, ü→ue, ß→ss, Füllwörter entfernen („ich trinke/nehme/hatte“,
  „ein/eine/einen“, „glas/flasche/tasse“, „bitte“), Zahlwörter. Abgleich gegen Name **und** Aliase mit
  Kölner Phonetik + Jaro-Winkler; eindeutiger Treffer ab Ähnlichkeit 0,88 und Abstand ≥ 0,05 zum Zweiten.
- `SpeechTexts`: erzeugt **Vorlese-Sätze** getrennt von Anzeige-Texten: ganze deutsche Sätze, Zahlen und Einheiten
  ausgeschrieben („zweihundertfünfzig Milliliter“, „siebenundzwanzig Gramm“), keine Emojis/Abkürzungen. Beispiele:
  „Anna trinkt eine Apfelschorle.“ · „Apfelschorle eingetragen.“ · „Heute aus Getränken: fünfunddreißig Gramm Zucker.“

### 1.7 Vernetzung (`social`) – Ports, Implementierung im App-Modul

```kotlin
data class UserProfile(val id: String, val displayName: String, val avatarUrl: String?)
data class Friend(val profile: UserProfile, val since: Instant)
data class FriendRequest(val id: String, val other: UserProfile, val direction: Direction, val createdAt: Instant) { enum class Direction { INCOMING, OUTGOING } }
data class FriendDrinkEvent(val id: String, val author: UserProfile, val name: String, val icon: DrinkIcon,
                            val category: DrinkCategory, val volumeMilliliters: Int, val consumedAt: Instant)

interface SessionProvider { val currentUser: Flow<UserProfile?> }
interface FriendsService {
    suspend fun friends(): List<Friend>
    suspend fun requests(): List<FriendRequest>
    suspend fun sendRequestByEmail(email: String)      // Antwort immer neutral (kein Hinweis, ob die Adresse existiert)
    suspend fun respond(requestId: String, accept: Boolean)
    suspend fun cancel(requestId: String)
    suspend fun createInviteLink(): String              // vollständige https-URL
    suspend fun acceptInvite(token: String): UserProfile
    suspend fun removeFriend(userId: String)
}
interface FeedService { suspend fun recentEvents(since: Instant?, limit: Int = 100): List<FriendDrinkEvent> }
interface EventPublisher { suspend fun publish(entry: DrinkEntry); suspend fun unpublish(entryId: String) }
```

- Ein Decorator um das `HistoryRepository` (wie bisher `PublishingDrinkRepository`, jetzt für Freunde) teilt nach
  `SharingPolicy` im Hintergrund; lokales Speichern wartet nie auf das Netz; Fehler blockieren nichts.

## 2. Supabase-Backend (`supabase/`)

Supabase Free (ohne Karte), Region **Frankfurt (eu-central-1)**. Verzeichnis nach Supabase-CLI-Standard:
`supabase/config.toml`, `supabase/migrations/*.sql`, `supabase/functions/<name>/index.ts`, `supabase/tests/*.sql` (pgTAP).

### 2.1 Tabellen (Schema `public`, RLS auf **jeder** Tabelle aktiv)

```sql
profiles(id uuid PK → auth.users ON DELETE CASCADE, display_name text 1..50, avatar_url text null ≤2048,
         created_at timestamptz default now())
  -- per Trigger auf auth.users angelegt (Name/Bild aus raw_user_meta_data: full_name|name, avatar_url|picture).
  -- SELECT: eigene Zeile, Freunde, und Personen mit offener Anfrage zwischen beiden. UPDATE: nur eigene
  --   (display_name, avatar_url). KEIN E-Mail-Feld (E-Mails bleiben in auth.users).
friendships(user_a uuid, user_b uuid, created_at, PK(user_a,user_b), CHECK(user_a < user_b), beide FK → profiles CASCADE)
  -- SELECT: wenn auth.uid() in (user_a,user_b). Schreiben nur über RPCs.
friend_requests(id uuid PK, from_user uuid, to_user uuid, created_at, UNIQUE(from_user,to_user), CHECK(from_user<>to_user), FK CASCADE)
  -- SELECT: Absender oder Empfänger. DELETE: Absender (zurückziehen). Anlegen/Annehmen nur über RPCs.
invite_links(token text PK (≥ 128 bit, base64url), owner uuid FK CASCADE, created_at, expires_at (14 Tage))
  -- Kein Client-Zugriff außer über RPCs. Pro Nutzer höchstens ein aktiver Link (neuer Link ersetzt den alten).
drink_events(id uuid PK (= lokale DrinkEntry.id), author_id uuid default auth.uid() FK CASCADE,
             name text 1..40, icon text ~ '^[a-z_]{1,30}$', category text (DrinkCategory.name),
             volume_ml int 1..2000, consumed_at timestamptz, created_at timestamptz default now(), notified_at timestamptz null)
  -- INSERT: author_id = auth.uid() AND consumed_at zwischen now()-24h und now()+5min (in WITH CHECK).
  -- SELECT: Autor oder Freund des Autors. DELETE: Autor. Kein UPDATE. Löschung nach 30 Tagen (pg_cron, täglich).
push_subscriptions(id uuid PK, user_id uuid default auth.uid() FK CASCADE, endpoint text UNIQUE (https, ≤2048),
                   p256dh text, auth text, platform text ('unifiedpush'|'web'), created_at, updated_at)
  -- Nur der Besitzer: SELECT/INSERT/DELETE (max. 5 pro Nutzer, per Trigger). Versand liest per Service-Rolle.
rate_limits(user_id uuid, action text, window_start timestamptz, count int, PK(user_id, action))
  -- Kein Client-Zugriff.
```

### 2.2 RPCs (`SECURITY DEFINER`, `search_path = ''`, nur für `authenticated`)

| Funktion | Verhalten |
|---|---|
| `send_friend_request(email text) → void` | Sucht `auth.users` nach `lower(email)` (nur bestätigte E-Mails). Ist es man selbst, schon befreundet oder unbekannt: still nichts tun. Gibt es eine Gegenanfrage: sofort Freundschaft. Sonst Anfrage anlegen. **Immer** gleiche, neutrale Antwort. Limit: 20 Aufrufe pro Nutzer und Tag → Fehler `rate_limited`. |
| `respond_friend_request(request_id uuid, accept boolean) → void` | Nur Empfänger. Annehmen: Freundschaft anlegen, Anfrage löschen. Ablehnen: löschen. |
| `create_invite_link() → text` | Neues Token (alter Link verfällt), gültig 14 Tage; gibt nur das Token zurück. |
| `accept_invite(token text) → profiles` | Gültig und nicht eigener Link → Freundschaft sofort (beide haben zugestimmt: Ersteller durch Teilen, Empfänger durch Antippen). Gibt Profil des Einladenden zurück. Limit: 30 pro Tag. |
| `remove_friend(friend_id uuid) → void` | Löscht die Freundschaft (idempotent). |

### 2.3 Edge Functions (Deno/TypeScript, kostenlos im Free-Tarif)

| Funktion | Verhalten |
|---|---|
| `notify-friends` (POST `{ eventId }`, Nutzer-JWT) | Prüft JWT; lädt das Ereignis (Autor = Aufrufer, `created_at` < 10 min, `notified_at` null); setzt `notified_at`; lädt Freunde und deren `push_subscriptions` (Service-Rolle); sendet **WebPush (RFC 8291, aes128gcm) mit VAPID** an jedes Abo; Payload JSON `{ type: "drink", eventId, authorId, recipientId, title, body }` (Titel „Anna trinkt gerade“, Text „Apfelschorle (500 ml)“), TTL 1 Tag; Antwort 404/410 → Abo löschen. Limit: 60 Aufrufe pro Nutzer und Stunde. |
| `delete-account` (POST, Nutzer-JWT) | Löscht den Auth-Nutzer per Admin-API; alles andere folgt über `ON DELETE CASCADE`. Idempotent. |

Geheimnisse (nur in Supabase gesetzt, nie im Repo): `VAPID_PRIVATE_KEY`, `VAPID_SUBJECT` (mailto:…).
Öffentlich (im App-Build, unkritisch): Projekt-URL, **Publishable/Anon-Key**, `VAPID_PUBLIC_KEY`.

### 2.4 Anmeldung (Supabase Auth)

- **Nur** der Google-Anbieter ist aktiv; E-Mail/Passwort und anonyme Anmeldung **aus** (wichtig für den E-Mail-Abgleich).
- Google-OAuth-Client Typ **Web-Anwendung**, Redirect `https://<projekt>.supabase.co/auth/v1/callback`,
  Scopes nur `openid email profile`, Zustimmungsbildschirm „In Produktion“.
- Erlaubte Redirect-URLs in Supabase: `drinkwithme://auth-callback` (Android) und `<WEB_APP_URL>/auth/callback` (Web).
- Android: **PKCE im Browser** (Custom Tab, Fallback `ACTION_VIEW`, niemals WebView) über `GET /auth/v1/authorize?
  provider=google&redirect_to=drinkwithme://auth-callback&code_challenge=…&code_challenge_method=s256` → Rückkehr mit
  `code` → `POST /auth/v1/token?grant_type=pkce` → Sitzung; Erneuern mit `grant_type=refresh_token`; Refresh-Token
  verschlüsselt mit Android-Keystore gespeichert.

## 3. Android-App (`android/app`)

- Abhängigkeiten nur frei: AndroidX (Compose, Lifecycle, Activity, Browser, WorkManager), OkHttp, UnifiedPush-Connector.
  Supabase-REST/Auth direkt per HTTPS (kein `compose-auth`). Kotlin-/AGP-Versionen dürfen angehoben werden, wenn eine
  Bibliothek es verlangt (CI ist maßgeblich).
- Konfiguration über `android/app/supabase.properties` (nicht geheim, kann eingecheckt werden):
  `supabaseUrl`, `supabaseAnonKey`, `vapidPublicKey`, `webAppUrl`. Fehlt die Datei → Vernetzung zeigt „Server noch nicht
  eingerichtet“, alles andere funktioniert.
- Bereiche (untere Navigation; auf Tablet/TV als Seitenleiste): **Bar** · **Verlauf** (Statistik & Historie) ·
  **Freunde** · **Einstellungen**.
- **Bar**: großes Raster mit Icon + Name, **ein Tipp = getrunken** (Bestätigung per kurzer Ansage + großer
  „Rückgängig“-Knopf 10 s), langes Drücken/Menü = bearbeiten. Mikrofon-Knopf „Sprechen“ nur, wenn eine Spracherkennung
  vorhanden ist (`RecognizerIntent.ACTION_RECOGNIZE_SPEECH`, `de-DE`, 5 Ergebnisse, Bar-Namen als Biasing ab API 33,
  **ohne** `EXTRA_PREFER_OFFLINE`), Abgleich mit `VoiceCommandMatcher`.
- **Verlauf**: Diagramme (Compose Canvas, selbst gezeichnet): Zucker pro Tag mit 50-g/25-g-Linien, Alkohol pro Woche
  mit DGE-Bändern (ausblendbar), Kennzahlen (heute, alkoholfreie Tage, Vergleich Vormonat), Liste der Einträge.
  Alles zusätzlich als Text (Screenreader/TV). **„Als Bild teilen“** (PNG über FileProvider, Vorschau vor dem Teilen).
- **Freunde**: Feed der Freunde, Freundesliste, Anfragen (annehmen/ablehnen), „Freund einladen“ (Link teilen) und
  „Per E-Mail hinzufügen“; Einladungs-Links öffnen die App über `drinkwithme://einladung/<token>` (die Web-Seite
  `<WEB_APP_URL>/einladung/<token>` bietet „In der App öffnen“ an).
- **Ereignis-Bildschirm**: Tipp auf eine Benachrichtigung öffnet ihn (pro Ereignis eigener PendingIntent-RequestCode);
  großer Satz (≥ 28 sp) + bildschirmbreiter Knopf **„Vorlesen“** (≥ 96 dp). Optionale Benachrichtigungs-Aktion
  „Vorlesen“ öffnet denselben Bildschirm mit automatischem Vorlesen.
- **Vorlesen**: Android `TextToSpeech` (`Locale.GERMANY`, Rückfall `Locale.GERMAN`); fehlt eine deutsche Stimme →
  Hilfe „Vorlesen einrichten“ (z. B. SherpaTTS aus F-Droid). Nicht automatisch sprechen, wenn ein Screenreader aktiv ist.
- **Push**: UnifiedPush-Connector; Einrichtungs-Assistent empfiehlt **ntfy** (F-Droid); Registrierung mit
  `VAPID_PUBLIC_KEY`, Abo in `push_subscriptions`; Nachrichten mit `decrypted == false` verwerfen; anzeigen nur, wenn
  `recipientId` dem angemeldeten Nutzer entspricht. **Ohne** Distributor: WorkManager-Abruf alle 15 min + beim Öffnen
  (neue `drink_events` seit zuletzt gesehen, keine Doppelten).
- **Konto löschen** (Einstellungen): Edge Function `delete-account`, dann lokal abmelden; lokale Historie bleibt.
- **Barrierefreiheit**: Semantik/contentDescription überall, Ziele ≥ 48 dp, Schriftgröße bis 200 %, Kontrast ≥ 4,5:1,
  keine Information nur über Farbe, D-Pad-Fokus mit sichtbarem Rahmen.
- **Tablet/TV**: `BoxWithConstraints` ab 600 dp zweispaltig; Manifest: `LEANBACK_LAUNCHER`, Banner 320×180,
  `android.hardware.touchscreen` und `android.software.leanback` `required=false`; auf TV keine Texteingabe nötig
  (Eintragen per OK aus der Bar), kein Verlass auf Benachrichtigungen.
- App-Symbol: selbst gezeichnet (adaptive icon), CC0.

## 4. Web-App (`web/`) für iPhone und Browser

- Statische PWA (Vite + TypeScript, `@supabase/supabase-js`, kleine UI-Bibliothek erlaubt, z. B. Preact), kostenlos
  hostbar (GitHub Pages bei öffentlichem Repo, sonst Netlify/Cloudflare Pages ohne Karte). Basis-URL konfigurierbar.
- Gleiche Bereiche wie Android (Bar, Verlauf, Freunde, Einstellungen), gleiche Texte und Regeln; Bar und Historie
  **lokal** in IndexedDB; Icons aus `design/icons/*.svg`.
- Google-Anmeldung über Supabase (`signInWithOAuth`, PKCE, Redirect `<WEB_APP_URL>/auth/callback`).
- Web Push (VAPID) über Service Worker; auf dem iPhone ab iOS 16.4 nach „Zum Home-Bildschirm“; Service Worker zeigt
  nur Nachrichten mit passender `recipientId` an. Abo mit `platform = 'web'`.
- Vorlesen über `speechSynthesis` (de-DE), Spracheingabe optional über `SpeechRecognition`, falls vorhanden.
- Seite `/einladung/<token>`: „In der App öffnen“ (Android) oder direkt im Browser annehmen.

## 5. Icons (`design/icons/`)

- Selbst gezeichnete, einfache, flächige SVGs (viewBox 0 0 48 48), einheitlicher Stil, gut erkennbar bei 48 dp und
  in Graustufen (Form statt nur Farbe). Ein SVG je `DrinkIcon.key` plus App-Symbol und TV-Banner.
- Android: daraus erzeugte Vector Drawables `android/app/src/main/res/drawable/drink_<key>.xml`.
- Lizenz CC0 (siehe `design/icons/LICENSE` bzw. Hinweis in README).

## 6. CI

- `android.yml`: Kern-Tests, App-Tests, Debug-APK, Signaturprüfung (wie bisher).
- `supabase.yml`: `supabase start` (Docker auf GitHub-Runnern), Migrationen, pgTAP-Tests (`supabase test db`),
  Deno-Tests der Edge Functions.
- `web.yml`: Build + Tests der Web-App, Artefakt.
- `supabase-deploy.yml` (manuell): `supabase link` + `db push` + `functions deploy` mit Secrets
  `SUPABASE_ACCESS_TOKEN`, `SUPABASE_DB_PASSWORD` und Variable `SUPABASE_PROJECT_REF`.
- Die Firebase-Workflows und `firebase/` entfallen.
