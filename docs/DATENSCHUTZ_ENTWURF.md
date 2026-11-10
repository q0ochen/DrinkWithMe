# Datenschutzerklärung für „Drink With Me“ – ENTWURF

> **Bitte zuerst lesen – das ist ein Entwurf, keine Rechtsberatung.**
>
> - Dieser Text ist eine **Arbeitsgrundlage**. Er ist **nicht** von einer Juristin oder einem Juristen geprüft
>   und ersetzt keine Rechtsberatung.
> - Alles in **[eckigen Klammern]** ist ein **Platzhalter** und muss ausgefüllt oder entschieden werden.
> - Stellen mit **„[Prüfen: …]“** sollten vor einer Veröffentlichung fachkundig geprüft werden. Am Ende steht
>   eine [Checkliste](#checkliste-vor-der-veröffentlichung-nicht-teil-der-erklärung).
> - Der Text beschreibt den technischen Stand nach dem Umbau (Architektur Version 2: Supabase, UnifiedPush,
>   Web-App; siehe [`ARCHITEKTUR.md`](ARCHITEKTUR.md)). Ändert sich die App, muss er angepasst werden.

---

**Stand:** [TT.MM.JJJJ]

## 1. Wer ist verantwortlich?

Verantwortlich für die Verarbeitung personenbezogener Daten in „Drink With Me“ (Android-App und Web-App) im Sinne
der Datenschutz-Grundverordnung (DSGVO) ist:

[Vor- und Nachname]
[Straße und Hausnummer]
[PLZ und Ort]
E-Mail: [kontakt@beispiel.de]

Bei Fragen zum Datenschutz, für Auskünfte und Löschwünsche schreib uns einfach an [kontakt@beispiel.de].

[Prüfen: Wird die App nur im privaten Freundeskreis genutzt, kann unter Umständen die „Haushaltsausnahme“
(Art. 2 Abs. 2 lit. c DSGVO) greifen. Sobald die App öffentlich angeboten wird – z. B. über F-Droid oder die
öffentliche Web-App –, ist davon auszugehen, dass die DSGVO vollständig gilt.]

## 2. Das Wichtigste in Kürze

- **Ohne Anmeldung bleibt alles auf deinem Gerät.** Bar, vollständiger Verlauf, Statistik und Einstellungen
  werden nie an uns übertragen – auch nicht, wenn du angemeldet bist.
- **Mit Google-Anmeldung** (nur nötig für Freunde) speichern wir auf einem Server in **Frankfurt am Main**:
  dein Profil (Name und Adresse deines Profilbilds aus Google), deine E-Mail-Adresse (nur im Anmeldesystem),
  deine Freundschaften und Freundschaftsanfragen, Einladungs-Links, Benachrichtigungs-Abos, Zähler gegen
  Missbrauch und die **Getränke, die du teilst**.
- **Geteilt werden nur:** Name des Getränks, Icon, Art (z. B. „Bier“), Menge in ml und Zeitpunkt. **Nie** Gramm
  Alkohol, Zucker oder deine Statistik.
- **Alkoholische Getränke** teilt die App **erst nach deiner ausdrücklichen Zustimmung**; du kannst sie jederzeit
  zurücknehmen.
- Geteilte Getränke sehen **nur deine Freunde** – Menschen, mit denen du dich ausdrücklich vernetzt hast.
  Sie werden nach **30 Tagen automatisch gelöscht**.
- **Benachrichtigungen** sind Ende-zu-Ende verschlüsselt; die Zustelldienste können ihren Inhalt nicht lesen.
- **Keine Werbung, kein Tracking, keine Analyse- oder Absturz-Dienste**, kein Verkauf von Daten.
- Du kannst dein **Konto direkt in der App löschen**; dann werden alle Server-Daten zu deinem Konto gelöscht.

## 3. Daten, die nur auf deinem Gerät liegen

### 3.1 Bar, Verlauf, Statistik und Einstellungen

Die App speichert **lokal auf deinem Gerät** (Android: im privaten App-Speicher; Web-App: in der Datenbank deines
Browsers, „IndexedDB“):

- deine **Bar**: Getränke mit Name, Icon, Art, Standardmenge, Vol.-% Alkohol und Zucker pro 100 ml,
- deinen **vollständigen Verlauf**: jedes eingetragene Getränk mit Zeitpunkt, Menge und den Werten zum Zeitpunkt
  des Eintragens,
- daraus berechnete **Statistiken** (Gramm Alkohol pro Woche, Gramm Zucker pro Tag, alkoholfreie Tage …) – sie
  werden beim Anzeigen berechnet und nicht übertragen,
- deine **Einstellungen**, z. B. ob alkoholische Getränke geteilt werden dürfen, Ansagen, Vorlesen.

Diese Daten werden **nicht** an uns oder an Server übertragen. Sie verschwinden, wenn du sie in der App löschst,
die App deinstallierst bzw. in der Web-App die Website-Daten löschst (auf dem iPhone: das Symbol vom
Home-Bildschirm entfernst).

### 3.2 Technische Daten auf deinem Gerät

Bist du angemeldet, speichert die App außerdem lokal:

- deine **Anmeldesitzung** (Zugangs- und Erneuerungs-Token). Android verschlüsselt das Erneuerungs-Token mit
  einem Schlüssel aus dem Android-Keystore; die Web-App speichert die Sitzung in IndexedDB,
- deine Nutzer-ID, deinen Anzeigenamen und deine E-Mail-Adresse (zur Anzeige „Angemeldet als …“),
- technische Merker: wann du zuletzt neue Getränke deiner Freunde gesehen hast, zu welchen Ereignissen schon eine
  Benachrichtigung angezeigt wurde, noch nicht übertragene Teilen-/Löschaufträge (nur IDs) und – in der
  Web-App – die Nutzer-ID für die Prüfung eingehender Benachrichtigungen.

### 3.3 Teilen über andere Apps

Teilst du deine Statistik **als Bild**, entscheidest du selbst, an wen und über welche App (z. B. Messenger,
E-Mail). Die Übertragung übernimmt dann die gewählte App; dafür gelten deren Datenschutzbestimmungen.

### 3.4 Spracheingabe und Vorlesen

- **Spracheingabe:** Die App nimmt **selbst keinen Ton auf** und hat keine Mikrofon-Berechtigung. Tippst du auf
  „Sprechen“, übernimmt die **Spracherkennung deines Geräts bzw. Browsers** (z. B. von Google oder Apple) und gibt
  der App nur den erkannten Text zurück. Ob diese Erkennung die Aufnahme auf eigenen Servern verarbeitet,
  bestimmt ihr Anbieter; es gelten dessen Datenschutzbestimmungen.
- **Vorlesen:** nutzt die Sprachausgabe deines Geräts (Android) bzw. Browsers (Web-App). Die Web-App bevorzugt
  Stimmen, die auf dem Gerät laufen; gibt es keine, kann der Browser eine Online-Stimme seines Herstellers
  verwenden, die den vorgelesenen Text an dessen Server schickt.

### 3.5 Datensicherung von Android

Hast du auf deinem Android-Gerät die automatische Datensicherung eingeschaltet, kann Android Bar und Verlauf in
**deiner eigenen** Sicherung (z. B. bei Google) mitsichern. Das ist eine Funktion von Android bzw. des
Geräteherstellers, die du in den Android-Einstellungen steuerst. Deine Anmeldung wird dabei bewusst **nicht**
gesichert.

## 4. Daten auf dem Server – nur wenn du dich anmeldest

Für Freunde und Benachrichtigungen nutzen wir **Supabase** (siehe Abschnitt 7). Die Datenbank liegt im
Rechenzentrum **Frankfurt am Main** (Region `eu-central-1`). Jede Tabelle ist durch Zugriffsregeln
(„Row Level Security“) geschützt: Jede Person kann nur lesen, was für sie bestimmt ist.

### 4.1 Anmeldung mit Google

Du meldest dich über **Google** an (Verfahren „OAuth“, nur die Berechtigungen `openid`, `email`, `profile`).
Die Anmeldung läuft immer im Browser – dein Google-Passwort erhalten wir **nie**. Im Anmeldesystem von Supabase
(„Supabase Auth“) werden gespeichert:

| Daten | Wozu | Wer sieht sie |
|---|---|---|
| zufällige **Nutzer-ID** | Zuordnung von Profil, Freundschaften und geteilten Getränken | technisch; Freunde sehen sie nicht direkt |
| **E-Mail-Adresse** deines Google-Kontos und ob Google sie bestätigt hat | Kontoverwaltung; damit dich Freunde per E-Mail-Adresse finden können (Abschnitt 4.3) | nur wir als Betreiber – **nicht** deine Freunde |
| Daten aus deinem Google-Profil: **Name**, **Adresse (URL) deines Profilbilds**, deine Google-Kennung | Anzeigename; Zuordnung zum Google-Konto | Name: Freunde und Personen mit offener Anfrage (4.2) |
| Zeitpunkte der Registrierung und letzten Anmeldung, aktive Sitzungen | Kontoverwaltung, Sicherheit | nur wir |
| Sicherheitsprotokolle der Anmeldung (Zeitpunkt, Art des Ereignisses, ggf. **IP-Adresse** und Browser-/Gerätekennung) | Sicherheit, Missbrauchsschutz, Fehlersuche | nur wir [Prüfen: siehe Abschnitt 4.8] |

Google erfährt dabei, dass du dich bei „Drink With Me“ anmeldest. Für dein Google-Konto ist Google selbst
verantwortlich. In deinem Google-Konto kannst du die Verbindung jederzeit trennen
([myaccount.google.com/connections](https://myaccount.google.com/connections)).

### 4.2 Profil

Bei der ersten Anmeldung legt der Server ein **Profil** an: **Anzeigename** (aus deinem Google-Namen) und die
**Adresse deines Google-Profilbilds**. Das Profil enthält **keine** E-Mail-Adresse.

Sichtbar ist dein Profil für dich, für deine **Freunde** und für Personen, mit denen eine **offene
Freundschaftsanfrage** besteht (in beiden Richtungen). Die Apps zeigen derzeit **kein** Profilbild an (sie laden
also auch nichts von Googles Bild-Servern), sondern nur den Namen bzw. Anfangsbuchstaben.

### 4.3 Freundschaften und Freundschaftsanfragen

- **Freundschaften:** wer mit wem befreundet ist und seit wann. Sichtbar nur für die beiden Beteiligten.
- **Anfragen per E-Mail-Adresse:** Gibst du die Google-E-Mail-Adresse einer Person ein, sucht der Server nach
  einem Konto mit genau dieser, von Google bestätigten Adresse. Die Antwort ist **immer gleich** – man erfährt
  also nicht, ob es die Adresse gibt. Gespeichert werden Absender, Empfänger und Zeitpunkt. Die angefragte Person
  sieht deinen **Anzeigenamen**, **nicht** deine E-Mail-Adresse. Die E-Mail-Adresse, die du eingibst, wird nicht
  gespeichert.
- Gibt es schon eine Anfrage in die Gegenrichtung, seid ihr sofort befreundet.

### 4.4 Einladungs-Links

Erstellst du einen Einladungs-Link, speichert der Server ein zufälliges, nicht erratbares Kennwort (Token),
dich als Ersteller sowie Erstellungs- und Ablaufzeitpunkt. Der Link ist **14 Tage** gültig; ein neuer Link ersetzt
den alten. Wer den Link hat und ihn nach der Anmeldung **ausdrücklich annimmt**, ist sofort mit dir befreundet –
teile ihn also nur mit Personen, die deine geteilten Getränke sehen dürfen.

### 4.5 Geteilte Getränke

Bist du angemeldet, teilt die App ein neu eingetragenes Getränk mit deinen Freunden, **außer**

- es ist alkoholisch und du hast dem Teilen alkoholischer Getränke **nicht** zugestimmt (Abschnitt 4.6), oder
- du hast es als **privat** eingetragen (Android: Getränk lange drücken → „Privat eintragen (nicht teilen)“).
  [Prüfen: In der Web-App gibt es diese Möglichkeit pro Getränk derzeit nicht.]

Gespeichert werden **nur**:

- eine zufällige ID des Eintrags, deine Nutzer-ID,
- **Name** des Getränks (auch ein selbst gewählter Name – den sehen deine Freunde), **Icon**, **Art**
  (z. B. Wasser, Kaffee, Bier, Wein), **Menge in ml**,
- der **Zeitpunkt**, zu dem du es getrunken hast, und technische Zeitpunkte (gespeichert, benachrichtigt).

**Nicht** übertragen werden Vol.-% Alkohol, Zucker, Gramm, Statistiken und Getränke, die du **vor** der Anmeldung
eingetragen hast.

Sichtbar sind geteilte Getränke für dich und **deine Freunde** – auch für Personen, mit denen du dich später
befreundest, solange das Getränk noch gespeichert ist. Der Server löscht geteilte Getränke **30 Tage** nach dem
Trinkzeitpunkt automatisch (tägliches Aufräumen). Löschst du einen Eintrag im Verlauf oder tippst du auf
„Rückgängig“, entfernt die App ihn auch vom Server. [Prüfen: Die Android-App holt das ohne Internetverbindung
später nach; die Web-App derzeit nicht – dann bleibt der Eintrag bis zur automatischen Löschung.]

### 4.6 Alkohol: Teilen nur mit ausdrücklicher Zustimmung

Angaben darüber, ob und wie viel jemand Alkohol trinkt, können Rückschlüsse auf die Gesundheit zulassen und damit
**Gesundheitsdaten** im Sinne von Art. 9 DSGVO sein. Deshalb:

- Gramm, Statistiken und Bezugswerte bleiben **immer** auf deinem Gerät.
- Beim ersten alkoholischen Getränk fragt die App einmal: **„Alkoholische Getränke mit Freunden teilen?“** und
  erklärt, was geteilt wird. Ohne dein „Ja“ werden alkoholische Getränke **nicht** geteilt.
- Du kannst die Zustimmung jederzeit in den **Einstellungen** zurücknehmen. Das wirkt für die Zukunft; bereits
  geteilte alkoholische Getränke bleiben sichtbar, bis du sie löschst oder sie nach 30 Tagen automatisch gelöscht
  werden. [Prüfen: Sollen sie beim Widerruf sofort entfernt werden? Derzeit nicht umgesetzt.]
- Die Zustimmung wird **auf deinem Gerät** gespeichert (je Gerät bzw. Browser getrennt).

[Prüfen: Reicht der Dialog als „ausdrückliche Einwilligung“ nach Art. 9 Abs. 2 lit. a DSGVO (informiert,
bestimmt, eindeutig)? Wie wird die Einwilligung nachgewiesen (Art. 7 Abs. 1), wenn sie nur auf dem Gerät liegt?
Sind auch nicht-alkoholische Getränke (z. B. zuckerhaltige) im Zusammenhang einer Gesundheits-App als
Gesundheitsdaten zu behandeln?]

### 4.7 Benachrichtigungen

Damit du benachrichtigt wirst, wenn ein Freund etwas trinkt, speichert der Server ein **Benachrichtigungs-Abo**
pro Gerät (höchstens 5 pro Konto): die Zustelladresse („Endpoint“, eine Internetadresse des Zustelldienstes),
zwei öffentliche Schlüssel zum Verschlüsseln, die Plattform („unifiedpush“ oder „web“) und Zeitpunkte.

Teilst du ein Getränk, schickt unser Server an die Abos deiner Freunde eine Nachricht mit z. B. dem Titel
„Anna trinkt gerade“ und dem Text „Apfelschorle (500 ml)“ sowie technischen Kennungen (Ereignis, Absender,
Empfänger). Die Nachricht ist nach dem Web-Push-Standard **Ende-zu-Ende verschlüsselt** (RFC 8291): Die
Zustelldienste sehen nur verschlüsselte Daten, die Zustelladresse, Zeitpunkt und Größe. Die App zeigt eine
Nachricht nur an, wenn sie für die gerade angemeldete Person bestimmt ist.

Zustelldienste:

- **Android:** der Dienst, den du selbst ausgewählt hast (empfohlen: die App **ntfy** mit dem Server `ntfy.sh`;
  andere UnifiedPush-Dienste sind möglich). Ohne solchen Dienst fragt die App etwa alle 15 Minuten selbst beim
  Server nach – dann gibt es kein Abo.
- **Web-App:** der Push-Dienst deines Browsers – z. B. **Apple** (Safari/iPhone), **Google** (Chrome),
  **Mozilla** (Firefox) oder **Microsoft** (Edge).

Beim Abmelden, beim Ausschalten der Benachrichtigungen und wenn ein Zustelldienst meldet, dass ein Abo nicht mehr
gilt, wird das Abo gelöscht. Hat dein Gerät beim Abmelden kein Internet, kann das Abo auf dem Server bleiben, bis
der Zustelldienst es für ungültig erklärt oder du dein Konto löschst; die App zeigt dann trotzdem keine Nachrichten
für das abgemeldete Konto an.

### 4.8 Schutz vor Missbrauch und Server-Protokolle

- **Zähler gegen Missbrauch:** Pro Konto zählt der Server, wie oft bestimmte Aktionen in einem Zeitfenster
  genutzt wurden (z. B. 20 Anfragen per E-Mail pro Tag, 30 angenommene Einladungen pro Tag, 60 Benachrichtigungs-
  Aufträge pro Stunde). Gespeichert werden Nutzer-ID, Aktion, Beginn des Zeitfensters und Anzahl. Veraltete Zähler
  löscht der Server nach spätestens etwa zwei Tagen.
- **Protokolle:** Supabase protokolliert technisch jeden Aufruf (u. a. Zeitpunkt, aufgerufene Adresse,
  Ergebnis, **IP-Adresse** und Browser-/App-Kennung). Diese Protokolle dienen Betrieb, Sicherheit und Fehlersuche.
  [Prüfen: Aufbewahrungsdauer der Supabase-Protokolle im kostenlosen Tarif (nach unserem Kenntnisstand kurz,
  etwa ein Tag). Die Anmelde-Protokolle („Auth Audit Logs“) schreibt Supabase standardmäßig zusätzlich in die
  Projekt-Datenbank, wo sie ohne eigenes Aufräumen bestehen bleiben – auch nach einer Kontolöschung. Empfehlung:
  in Supabase unter Authentication → Audit Logs das Schreiben in die Datenbank abschalten (siehe
  `EINRICHTUNG.md`, Schritt 3.3).]
- Unsere Server-Funktionen protokollieren keine vollständigen Zustelladressen oder Schlüssel.

## 5. Web-App und Downloads (GitHub)

Die Web-App wird über **GitHub Pages** ausgeliefert, die Android-Testversionen über **GitHub** (Releases bzw.
Actions). Beim Aufruf verarbeitet GitHub technisch notwendige Daten, insbesondere deine **IP-Adresse**, und
protokolliert Zugriffe aus Sicherheitsgründen. Die Web-App selbst lädt keine Schriften, Bilder oder Skripte von
fremden Servern nach und verbindet sich nur mit unserem Supabase-Server und – für Benachrichtigungen – mit dem
Push-Dienst deines Browsers. Cookies zu Werbe- oder Analysezwecken gibt es nicht.

[Prüfen: Rolle von GitHub (Auftragsverarbeiter oder eigener Verantwortlicher), Speicherdauer der
Zugriffsprotokolle laut GitHub-Datenschutzerklärung.]

## 6. Zwecke und Rechtsgrundlagen

| Verarbeitung | Zweck | Rechtsgrundlage [Prüfen] |
|---|---|---|
| Lokale Speicherung von Bar, Verlauf, Statistik | Grundfunktion der App | findet nur auf deinem Gerät statt; keine Übermittlung an uns |
| Anmeldung, Profil, Freundschaften, Anfragen, Einladungs-Links | Bereitstellung der Vernetzung, die du aktiv nutzt | Art. 6 Abs. 1 lit. b DSGVO |
| Teilen nicht-alkoholischer Getränke | Freunde sehen, was du trinkst | Art. 6 Abs. 1 lit. b DSGVO [Prüfen: ggf. Art. 9, siehe 4.6] |
| Teilen alkoholischer Getränke | wie oben | **ausdrückliche Einwilligung**, Art. 9 Abs. 2 lit. a i. V. m. Art. 6 Abs. 1 lit. a DSGVO; Widerruf jederzeit in den Einstellungen |
| Benachrichtigungen | Hinweis auf Getränke deiner Freunde | Art. 6 Abs. 1 lit. a DSGVO (Einschalten bzw. Erlaubnis des Betriebssystems) bzw. lit. b |
| Zähler gegen Missbrauch, Sicherheits- und Server-Protokolle, Hosting-Protokolle | Sicherheit, Missbrauchsschutz, Fehlersuche | Art. 6 Abs. 1 lit. f DSGVO (berechtigtes Interesse an einem sicheren Dienst) |

## 7. Wer verarbeitet die Daten? (Empfänger, Auftragsverarbeiter, Speicherort)

| Dienst | Wofür | Speicherort / Hinweise |
|---|---|---|
| **Supabase** – [Prüfen: Vertragspartner und Anschrift laut Data Processing Addendum, z. B. Supabase, Inc.] | Datenbank, Anmeldesystem, Server-Funktionen (Edge Functions), Protokolle | Datenbank in **Frankfurt am Main** (AWS-Region `eu-central-1`). [Prüfen: Edge Functions können in der Region ausgeführt werden, die dem Aufruf am nächsten liegt; Unterauftragnehmer (u. a. Amazon Web Services); Zugriffe aus Drittländern, z. B. durch Support; Grundlage der Übermittlung (EU-Standardvertragsklauseln / EU-US Data Privacy Framework). Auftragsverarbeitungsvertrag (DPA) im Supabase-Dashboard abschließen.] |
| **Google** – für Nutzer im EWR in der Regel Google Ireland Limited, Gordon House, Barrow Street, Dublin 4, Irland [Prüfen] | Anmeldung mit dem Google-Konto | Google verarbeitet die Anmeldung als Anbieter deines Google-Kontos. [Prüfen: Rolle (eigener Verantwortlicher), Drittlandübermittlung.] Wir speichern bei Google keine Nutzerdaten; das Google-Cloud-Projekt dient nur der Registrierung der Anmeldung. |
| **Zustelldienste für Benachrichtigungen**: ntfy (`ntfy.sh`) oder ein anderer von dir gewählter UnifiedPush-Dienst; Apple, Google, Mozilla oder Microsoft (je nach Browser) | Transport der **verschlüsselten** Benachrichtigungen | Sie sehen keine Inhalte, nur Zustelladresse, Zeitpunkt und Größe. [Prüfen: Betreiber und Standort von ntfy.sh; Einordnung der Zustelldienste.] |
| **GitHub, Inc.** (USA) | Auslieferung der Web-App (GitHub Pages) und der Android-Testversionen | IP-Adressen in Zugriffsprotokollen. [Prüfen: Rolle, Drittlandübermittlung, Data Privacy Framework.] |

Darüber hinaus geben wir keine Daten weiter und verkaufen keine Daten.

## 8. Wie lange werden die Daten gespeichert?

| Daten | Speicherdauer |
|---|---|
| Bar, Verlauf, Statistik, Einstellungen (Gerät) | bis du sie löschst, die App deinstallierst bzw. die Website-Daten löschst |
| Konto im Anmeldesystem (E-Mail, Google-Daten), Profil | bis zur Löschung deines Kontos |
| Freundschaften | bis einer von euch die Freundschaft beendet oder sein Konto löscht |
| Freundschaftsanfragen | bis sie angenommen, abgelehnt oder zurückgezogen werden oder ein Konto gelöscht wird [Prüfen: automatische Frist für unbeantwortete Anfragen? Derzeit keine] |
| Einladungs-Links | 14 Tage (abgelaufene löscht der Server täglich); ein neuer Link ersetzt den alten sofort |
| Geteilte Getränke | höchstens **30 Tage** nach dem Trinkzeitpunkt; früher, wenn du sie löschst oder dein Konto löschst |
| Benachrichtigungs-Abos | bis zum Abmelden, Ausschalten, bis der Zustelldienst sie für ungültig erklärt oder bis zur Kontolöschung |
| Zähler gegen Missbrauch | höchstens etwa 2 Tage |
| Server-Protokolle | [Prüfen: Dauer laut Supabase; Anmelde-Protokolle in der Datenbank siehe 4.8] |
| Hosting-Protokolle (GitHub) | [Prüfen: laut GitHub] |

## 9. Deine Daten löschen

| Was du tun willst | So geht's |
|---|---|
| Ein geteiltes Getränk entfernen | Im **Verlauf** löschen oder direkt nach dem Eintragen **Rückgängig** – es wird auch vom Server entfernt (siehe 4.5). Eine schon zugestellte Benachrichtigung kann die App nicht zurückholen. |
| Eine Freundschaft beenden | **Freunde → Entfernen**. Ihr seht danach gegenseitig keine geteilten Getränke mehr. |
| Keine Benachrichtigungen mehr | In den **Einstellungen** ausschalten, **abmelden** oder die Erlaubnis im Betriebssystem entziehen. |
| Alkoholische Getränke nicht mehr teilen | **Einstellungen → Alkoholische Getränke mit Freunden teilen** ausschalten. |
| Alle lokalen Daten löschen | Android: App deinstallieren oder App-Daten löschen. Web-App: Website-Daten im Browser löschen bzw. das Symbol vom Home-Bildschirm entfernen. |
| **Dein Konto komplett löschen** | **Direkt in der App: Einstellungen → Konto → „Konto löschen“** → bestätigen. Gelöscht werden sofort: dein Konto im Anmeldesystem (mit E-Mail-Adresse), dein Profil, deine Freundschaften und Anfragen, deine Einladungs-Links, **alle** deine geteilten Getränke, deine Benachrichtigungs-Abos und Zähler. Danach wirst du abgemeldet. Bar und Verlauf **auf deinem Gerät bleiben** erhalten. |
| Konto löschen ohne App | E-Mail an **[kontakt@beispiel.de]** mit der E-Mail-Adresse deines Google-Kontos. Wir löschen dieselben Daten wie die Funktion in der App, in der Regel innerhalb von [30] Tagen, und bestätigen dir die Löschung. |

Nicht sofort mitgelöscht werden technische Protokolle (Abschnitt 4.8 und 5); sie verfallen nach den dort
genannten Fristen. Die Verbindung zu deinem Google-Konto trennst du zusätzlich in deinem Google-Konto
(Abschnitt 4.1).

## 10. Berechtigungen

**Android-App:**

- **Internet:** für Anmeldung, Freunde und Benachrichtigungen (ohne Anmeldung nicht benutzt).
- **Benachrichtigungen** (ab Android 13 mit Abfrage, erst wenn du dich anmeldest oder sie einschaltest).
- Technische Berechtigungen, die die mitgelieferten freien Bibliotheken für Hintergrundaufgaben und die
  Zustellung über UnifiedPush brauchen (ohne Abfrage) [Prüfen: Liste aus der fertigen APK übernehmen].

**Keine** Berechtigungen für Mikrofon, Standort, Kontakte, Kamera oder Fotos.

**Web-App:** Benachrichtigungen nur, wenn du sie in den Einstellungen einschaltest und dein Browser fragt.

## 11. Deine Rechte

Du hast nach der DSGVO das Recht auf **Auskunft** (Art. 15), **Berichtigung** (Art. 16), **Löschung** (Art. 17),
**Einschränkung** der Verarbeitung (Art. 18), **Datenübertragbarkeit** (Art. 20), **Widerspruch** gegen
Verarbeitungen auf Grundlage berechtigter Interessen (Art. 21) und auf **Widerruf** einer Einwilligung mit
Wirkung für die Zukunft (Art. 7 Abs. 3) – z. B. indem du das Teilen alkoholischer Getränke oder die
Benachrichtigungen ausschaltest.

Schreib dafür einfach an **[kontakt@beispiel.de]**.

Außerdem kannst du dich bei einer **Datenschutz-Aufsichtsbehörde** beschweren, z. B. bei der Behörde deines
Wohnorts oder der für uns zuständigen Behörde: [Name und Anschrift der zuständigen Landesdatenschutzbehörde].

## 12. Sicherheit

- Alle Verbindungen sind **verschlüsselt** (HTTPS/TLS); Benachrichtigungen zusätzlich Ende-zu-Ende.
- Zugriffsregeln auf **jeder** Tabelle der Datenbank; Änderungen an Freundschaften, Anfragen und Einladungen nur
  über geprüfte Server-Funktionen.
- Einladungs-Links sind zufällig und praktisch nicht zu erraten (192 Bit).
- Ein geteiltes Getränk kann niemand ändern; löschen kann es nur, wer es geteilt hat (und der Server beim
  Aufräumen oder bei der Kontolöschung).
- Die Anmeldung läuft im Browser mit dem PKCE-Verfahren; die App sieht dein Google-Passwort nie.

## 13. Mindestalter und Gesundheitshinweis

Die App richtet sich an Personen ab **[16 / 18] Jahren**. Sie ist ein persönliches Getränketagebuch, stellt
keine Diagnose und ersetzt keine ärztliche Beratung. Kinder, Jugendliche, Schwangere und Stillende sollten keinen
Alkohol trinken. [Prüfen: Mindestalter festlegen und in der App bzw. im Store-Eintrag angeben.]

## 14. Änderungen dieser Datenschutzerklärung

Wenn sich die App oder die Rechtslage ändert, passen wir diese Erklärung an. Es gilt die jeweils aktuelle
Fassung, die [in der App / unter: URL] abrufbar ist.

---

## Checkliste vor der Veröffentlichung (nicht Teil der Erklärung)

- [ ] Alle Platzhalter in [eckigen Klammern] ausgefüllt, Stand-Datum gesetzt.
- [ ] Text fachkundig prüfen lassen (Datenschutz-Beratung, Verbraucherzentrale o. Ä.).
- [ ] **Art. 9 DSGVO** geklärt: Reicht der Zustimmungsdialog für alkoholische Getränke? Nachweis der Einwilligung?
      Sollen beim Widerruf bereits geteilte alkoholische Getränke sofort gelöscht werden?
- [ ] Mindestalter entschieden.
- [ ] **Supabase:** Auftragsverarbeitungsvertrag (DPA) abgeschlossen; Unterauftragnehmer und Drittlandübermittlung
      geprüft; Region Frankfurt bestätigt (Project Settings → General).
- [ ] **Supabase:** Schreiben der Anmelde-Protokolle in die Datenbank abgeschaltet oder eine Löschfrist festgelegt
      (`EINRICHTUNG.md`, Schritt 3.3); Aufbewahrung der übrigen Protokolle geprüft.
- [ ] Google-Zustimmungsbildschirm: nur `openid`, `email`, `profile`; Link zu dieser Datenschutzerklärung im
      Bereich „Branding“ eingetragen (sobald sie unter einer festen Adresse steht).
- [ ] Rolle von GitHub (Pages, Releases) und Betreiber von ntfy.sh geprüft.
- [ ] Erklärung in der App bzw. unter einer festen Adresse erreichbar gemacht (z. B. als Seite der Web-App).
- [ ] Ablauf für Löschanfragen per E-Mail festgelegt (Anleitung: `EINRICHTUNG.md`, Anhang „Konto auf Wunsch
      löschen“).
- [ ] Offene technische Punkte entschieden: „Privat eintragen“ auch in der Web-App; Nachholen von Löschungen in
      der Web-App ohne Internet; Frist für unbeantwortete Freundschaftsanfragen.
- [ ] Für F-Droid: Hinweise zu den Anti-Features (Abhängigkeit von Google-Anmeldung und eigenem Server) in der
      Beschreibung (siehe [`F-DROID.md`](F-DROID.md)).
- [ ] Wenn sich die App ändert (z. B. Anzeige von Profilbildern, iPhone-App, neue Dienste): Text anpassen.
