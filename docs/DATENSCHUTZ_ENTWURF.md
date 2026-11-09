# Datenschutzerklärung für „Drink With Me“ – ENTWURF

> **Bitte zuerst lesen – das ist ein Entwurf, keine Rechtsberatung.**
>
> - Dieser Text wurde als **Arbeitsgrundlage** erstellt. Er ist **nicht** von einer Juristin oder einem
>   Juristen geprüft und ersetzt keine Rechtsberatung.
> - Alles in **[eckigen Klammern]** ist ein **Platzhalter** und muss ausgefüllt oder entschieden werden.
> - Stellen mit **„[Prüfen: …]“** sind Punkte, die vor einer Veröffentlichung fachkundig geprüft werden sollten.
>   Am Ende steht eine [Checkliste](#checkliste-vor-der-veröffentlichung-nicht-teil-der-erklärung).
> - Der Text beschreibt den **technischen Stand der Android-App mit Trinkrunden** (siehe
>   [`TRINKRUNDEN_ARCHITEKTUR.md`](TRINKRUNDEN_ARCHITEKTUR.md)). Ändert sich die App, muss er angepasst werden.

---

**Stand:** [TT.MM.JJJJ]

## 1. Wer ist verantwortlich?

Verantwortlich für die Verarbeitung personenbezogener Daten in der App „Drink With Me“ im Sinne der
Datenschutz-Grundverordnung (DSGVO) ist:

[Vor- und Nachname]
[Straße und Hausnummer]
[PLZ und Ort]
E-Mail: [kontakt@beispiel.de]

Bei Fragen zum Datenschutz, für Auskünfte und Löschwünsche schreib uns einfach an [kontakt@beispiel.de].

[Prüfen: Wird die App nur im privaten Freundeskreis genutzt, kann unter Umständen die sogenannte
„Haushaltsausnahme“ (Art. 2 Abs. 2 lit. c DSGVO) greifen. Sobald die App öffentlich angeboten wird, z. B. im
Google Play Store, gilt die DSGVO in jedem Fall.]

## 2. Das Wichtigste in Kürze

- Du kannst die App **ohne Anmeldung** nutzen. Dann bleiben alle deine Getränke **nur auf deinem Gerät**.
- Nur wenn du dich **mit deinem Google-Konto anmeldest** und einer **Trinkrunde** beitrittst, werden Daten
  auf einem Server gespeichert: dein Name, die Runden, in denen du bist, und die Getränke, die du teilst.
- Deine Getränke sehen **nur die Mitglieder deiner Runden** – nicht die Öffentlichkeit.
- Getränke mit dem Schalter **„Privat (nicht mit Freunden teilen)“** verlassen dein Gerät nie.
- Als Server nutzen wir **Google Firebase**. Die Datenbank und die Server-Funktionen laufen in
  **Frankfurt am Main** (Google-Cloud-Region `europe-west3`).
- **Keine Werbung, kein Tracking, keine Analyse-Tools**, kein Verkauf von Daten.
- Du kannst geteilte Getränke jederzeit löschen, Runden verlassen und die Löschung deines Kontos per E-Mail
  verlangen.

## 3. Welche Daten verarbeitet die App?

### 3.1 Nutzung ohne Anmeldung – alles bleibt auf deinem Gerät

Wenn du Getränke einträgst, speichert die App diese **lokal auf deinem Gerät** (Name des Getränks, Kategorie,
Menge, optionale Notiz, Zeitpunkt, Einstellung „Privat“). Diese Daten werden **nicht** an uns oder an Server
übertragen.

- **Teilen über andere Apps:** Wenn du ein Getränk oder deinen Tag über die Teilen-Funktion deines Handys
  teilst (z. B. per WhatsApp, Signal, E-Mail), entscheidest du selbst, an wen der Text geht. Die Übertragung
  übernimmt dann die von dir gewählte App; dafür gelten deren Datenschutzbestimmungen.
- **Datensicherung von Android:** Hast du in deinem Android-Gerät die automatische Sicherung aktiviert, kann
  Android die App-Daten in deinem eigenen Google-Konto sichern. Das ist eine Funktion von Android bzw. Google,
  die du in den Android-Einstellungen steuerst.
- **iPhone-App:** Die iPhone-Version von Drink With Me arbeitet derzeit **ausschließlich lokal**. Sie hat keine
  Anmeldung und keine Trinkrunden und überträgt keine Daten an Server.

### 3.2 Anmeldung mit Google (nur für Trinkrunden, Android)

Für die Trinkrunden meldest du dich mit deinem **Google-Konto** an. Dabei verarbeiten wir bzw. unser
Dienstleister Google (siehe Abschnitt 6):

| Daten | Wozu | Wer sieht sie |
|---|---|---|
| **Firebase-Nutzer-ID** (eine zufällige Kennung) | um dein Konto, deine Runden und deine Getränke zuzuordnen | technisch; Mitglieder deiner Runden sehen sie nicht direkt |
| **Name** aus deinem Google-Konto (Anzeigename) | damit deine Freunde sehen, wer etwas trinkt | Mitglieder deiner Runden |
| **E-Mail-Adresse** deines Google-Kontos | wird vom Anmeldedienst (Firebase Authentication) gespeichert, um dein Konto zu verwalten | nur wir als Betreiber in der Firebase-Konsole; **nicht** deine Freunde |
| **Adresse (URL) deines Google-Profilbilds**, falls vorhanden | für eine mögliche Anzeige deines Bildes (wird derzeit nicht angezeigt) | derzeit niemand außer uns |
| Zeitpunkt der Kontoerstellung und der letzten Anmeldung | Kontoverwaltung | nur wir als Betreiber |
| IP-Adresse und Geräte-/Browserkennung (User-Agent) beim Anmelden | Sicherheit und Missbrauchsschutz durch Google; laut Google werden protokollierte IP-Adressen einige Wochen aufbewahrt | Google |

Hat dein Google-Konto keinen Namen, verwendet die App ersatzweise den Teil deiner E-Mail-Adresse **vor dem
„@“** als Anzeigenamen. Dieser ist dann für die Mitglieder deiner Runden sichtbar.

Dein Passwort erhalten wir **nicht** – die Anmeldung läuft vollständig über Google.

### 3.3 Trinkrunden

Wenn du eine Runde erstellst oder ihr beitrittst, speichern wir:

- Name der Runde, den **6-stelligen Einladungscode**, die Person, die die Runde verwaltet (Besitzer), den
  Erstellungszeitpunkt,
- die Liste der Mitglieder (Nutzer-ID und Anzeigename).

Alle Mitglieder einer Runde sehen den Namen der Runde, den Einladungscode und die Namen der anderen Mitglieder.
Wer den Einladungscode kennt, kann der Runde beitreten – gib ihn also nur an Personen weiter, die deine
Getränke sehen dürfen. Eine Runde hat höchstens 50 Mitglieder.

### 3.4 Geteilte Getränke

Bist du angemeldet und Mitglied mindestens einer Runde, wird jedes neu eingetragene Getränk **automatisch in
allen deinen Runden geteilt** – außer du schaltest **„Privat (nicht mit Freunden teilen)“** ein. Dabei werden
gespeichert:

- Name des Getränks, Kategorie (z. B. Wasser, Kaffee, Bier, Wein), Menge in ml, deine **optionale Notiz**,
- der Zeitpunkt, zu dem du das Getränk eingetragen hast, und der Zeitpunkt der Speicherung auf dem Server,
- deine Nutzer-ID und dein Anzeigename.

Sichtbarkeit:

- Die Getränke sehen **alle aktuellen Mitglieder der jeweiligen Runde** – auch Personen, die der Runde
  **später beitreten** (die App zeigt jeweils die neuesten 100 Einträge einer Runde).
- Getränke, die du **vor** der Anmeldung oder **vor** dem Beitritt zu einer Runde eingetragen hast, werden
  **nicht nachträglich** geteilt.
- Damit die Anzeige schnell ist und auch offline funktioniert, speichert die App die Daten deiner Runden
  (einschließlich der Getränke deiner Freunde) zusätzlich in einem **Zwischenspeicher auf deinem Gerät**.

> **Hinweis zu deiner Notiz:** Schreib in die Notiz nur, was alle Mitglieder deiner Runden lesen dürfen.

[Prüfen: Angaben zum Konsum alkoholischer Getränke können Rückschlüsse auf die Gesundheit zulassen und damit
unter Umständen als besondere Kategorie personenbezogener Daten (Art. 9 DSGVO) gelten. Klären, ob dafür eine
ausdrückliche Einwilligung (Art. 9 Abs. 2 lit. a DSGVO) eingeholt werden muss, z. B. durch einen
Einwilligungsdialog vor dem ersten Teilen.]

### 3.5 Push-Benachrichtigungen

Damit du benachrichtigt wirst, wenn jemand aus deinen Runden etwas trinkt, nutzen wir
**Firebase Cloud Messaging** (FCM) von Google:

- Bei der Anmeldung speichert die App das **Push-Token** deines Geräts (eine Kennung, über die Nachrichten
  zugestellt werden), die Plattform („android“) und den Zeitpunkt der letzten Aktualisierung in deinem Konto.
- Trägt ein anderes Mitglied ein geteiltes Getränk ein, schickt unser Server über FCM eine Nachricht an die
  Geräte der übrigen Mitglieder, z. B.: „Anna trinkt gerade – ☕ Cappuccino (200 ml) · Stammtisch“. Dafür
  verarbeitet Google den Inhalt der Nachricht und die Push-Tokens.
- Ab Android 13 fragt die App nach deiner **Erlaubnis für Benachrichtigungen**. Du kannst sie jederzeit in den
  Android-Einstellungen wieder entziehen.
- Beim **Abmelden** löscht die App das Push-Token. Ungültig gewordene Tokens löscht der Server automatisch.

### 3.6 Server-Protokolle

Unsere Server-Funktionen (Firebase Cloud Functions) schreiben technische Protokolle, z. B. Fehlermeldungen,
Zeitpunkte und in einzelnen Fällen Nutzer- oder Runden-IDs. Die Protokolle dienen nur dem Betrieb und der
Fehlersuche und werden bei Google Cloud Logging gespeichert. [Prüfen: Standard-Aufbewahrung im
„_Default“-Log-Bucket von Google Cloud Logging: 30 Tage.]

### 3.7 Was wir nicht tun

- **Keine Werbung**, keine Werbe-IDs.
- **Keine Analyse- oder Tracking-Dienste** (z. B. kein Google Analytics, kein Crash-Reporting-Dienst).
- **Keine Standortdaten**, kein Zugriff auf Kontakte, Kamera oder Fotos.
- **Kein Verkauf** und keine Weitergabe deiner Daten an Dritte – außer an Google als technischen Dienstleister
  (Abschnitt 6).

## 4. Zwecke und Rechtsgrundlagen

| Verarbeitung | Zweck | Rechtsgrundlage [Prüfen] |
|---|---|---|
| Lokale Speicherung der Getränke | Grundfunktion der App | findet nur auf deinem Gerät statt; keine Übermittlung an uns |
| Anmeldung, Konto, Runden, geteilte Getränke | Bereitstellung der Trinkrunden, die du aktiv nutzt | Art. 6 Abs. 1 lit. b DSGVO (Nutzung des von dir gewünschten Dienstes) [ggf. zusätzlich Art. 9 Abs. 2 lit. a DSGVO, siehe 3.4] |
| Push-Benachrichtigungen | Benachrichtigung über Getränke deiner Freunde | Art. 6 Abs. 1 lit. a DSGVO (deine Einwilligung über die Benachrichtigungs-Erlaubnis) bzw. lit. b |
| Sicherheitsprotokolle bei der Anmeldung, Server-Protokolle | Sicherheit, Missbrauchsschutz, Fehlersuche | Art. 6 Abs. 1 lit. f DSGVO (berechtigtes Interesse an einem sicheren und funktionierenden Dienst) |

## 5. Wie lange werden die Daten gespeichert?

- **Lokale Getränke:** bis du sie in der App löschst oder die App deinstallierst.
- **Geteilte Getränke:** bis du das Getränk löschst, die Runde aufgelöst wird oder dein Konto gelöscht wird.
  [Optional entscheiden: Sollen geteilte Getränke nach einer festen Frist automatisch gelöscht werden,
  z. B. nach [X] Tagen? Das ist derzeit nicht umgesetzt.]
- **Runden:** solange mindestens ein Mitglied in der Runde ist. Verlässt das letzte Mitglied die Runde, werden
  die Runde, ihr Einladungscode und alle darin geteilten Getränke automatisch gelöscht.
- **Konto und Profil (Name, Profilbild-URL):** bis zur Löschung deines Kontos.
- **Push-Tokens:** bis zum Abmelden, bis sie ungültig werden oder bis zur Löschung deines Kontos.
- **Protokolle:** siehe Abschnitt 3.6.

## 6. Wer verarbeitet die Daten? (Auftragsverarbeiter und Speicherort)

Wir nutzen die Plattform **Google Firebase**. Google verarbeitet die Daten in unserem Auftrag als
**Auftragsverarbeiter** (Art. 28 DSGVO).

- **Anbieter:** [Prüfen: Vertragspartner laut Firebase-/Google-Cloud-Bedingungen, für Kunden im EWR in der
  Regel Google Ireland Limited bzw. Google Cloud EMEA Limited, Gordon House, Barrow Street, Dublin 4, Irland.]
- **Genutzte Dienste:** Firebase Authentication (Anmeldung), Cloud Firestore (Datenbank), Cloud Functions for
  Firebase (Server-Funktionen), Firebase Cloud Messaging (Push-Benachrichtigungen), Google Cloud Logging
  (Protokolle).
- **Speicherort:** Die **Datenbank (Cloud Firestore)** und die **Server-Funktionen** laufen in der
  Google-Cloud-Region **`europe-west3` (Frankfurt am Main, Deutschland)**.
- **Globale Dienste:** Die **Anmeldung** (Firebase Authentication) und der **Versand von Push-Nachrichten**
  (Firebase Cloud Messaging) sind weltweit betriebene Google-Dienste. Dabei können Daten auch außerhalb der EU,
  insbesondere in den **USA**, verarbeitet werden. Google LLC ist nach dem **EU-US Data Privacy Framework**
  zertifiziert; zusätzlich hat Google **EU-Standardvertragsklauseln** abgeschlossen.
  [Prüfen: aktuellen Stand der Zertifizierung und der Vertragsgrundlagen.]
- **Vertragsgrundlage:** Firebase Data Processing and Security Terms bzw. Cloud Data Processing Addendum.
  [Prüfen: In der Firebase-Konsole unter „Projekteinstellungen“ die Datenverarbeitungsbedingungen akzeptieren
  bzw. deren Geltung bestätigen.]
- Weitere Informationen von Google: <https://firebase.google.com/support/privacy>

## 7. Deine Daten löschen

Du hast es weitgehend selbst in der Hand:

| Was du tun willst | So geht's |
|---|---|
| Ein geteiltes Getränk entfernen | Das Getränk in „Meine Getränke“ **löschen** – es wird auch aus allen Runden entfernt, in denen du gerade Mitglied bist. |
| Eine Runde verlassen | Im Reiter „Runden“ die Runde **verlassen**. **Achtung:** Deine bisher dort geteilten Getränke bleiben für die übrigen Mitglieder sichtbar. Willst du sie entfernen, **lösche sie vor dem Verlassen**. |
| Keine Push-Nachrichten mehr | In der App **abmelden** (das Push-Token wird gelöscht) oder in den Android-Einstellungen die Benachrichtigungen ausschalten. |
| Alle lokalen Daten löschen | Die App deinstallieren oder in den Android-Einstellungen die App-Daten löschen. |
| **Dein Konto komplett löschen** | Schreib eine E-Mail an **[kontakt@beispiel.de]** mit der E-Mail-Adresse deines Google-Kontos. Wir löschen dann dein Konto, dein Profil, deine Push-Tokens, deine Mitgliedschaften und deine geteilten Getränke, in der Regel innerhalb von [30] Tagen, und bestätigen dir die Löschung. Eine Löschfunktion direkt in der App gibt es derzeit noch nicht. |

## 8. Berechtigungen der App (Android)

- **Internet:** für Anmeldung, Trinkrunden und Push-Nachrichten.
- **Benachrichtigungen** (ab Android 13 mit Abfrage): für Push-Nachrichten über die Getränke deiner Freunde.

Die App fordert keine weiteren Berechtigungen an (z. B. keinen Standort, keine Kontakte, keine Kamera).

## 9. Deine Rechte

Du hast nach der DSGVO das Recht auf:

- **Auskunft** über die zu dir gespeicherten Daten (Art. 15),
- **Berichtigung** unrichtiger Daten (Art. 16),
- **Löschung** (Art. 17),
- **Einschränkung** der Verarbeitung (Art. 18),
- **Datenübertragbarkeit** (Art. 20),
- **Widerspruch** gegen Verarbeitungen auf Grundlage berechtigter Interessen (Art. 21),
- **Widerruf** einer erteilten Einwilligung mit Wirkung für die Zukunft (Art. 7 Abs. 3), z. B. indem du die
  Benachrichtigungen ausschaltest.

Schreib dafür einfach an **[kontakt@beispiel.de]**.

Außerdem kannst du dich bei einer **Datenschutz-Aufsichtsbehörde** beschweren, z. B. bei der Behörde deines
Wohnorts oder der für uns zuständigen Behörde: [Name und Anschrift der zuständigen Landesdatenschutzbehörde].

## 10. Sicherheit

- Alle Verbindungen zwischen App und Server sind **verschlüsselt** (HTTPS/TLS).
- **Sicherheitsregeln** auf dem Server sorgen dafür, dass Runden und Getränke **nur von Mitgliedern der
  jeweiligen Runde** gelesen werden können. Dein Profil und deine Push-Tokens kannst nur du selbst lesen und
  ändern. Runden werden ausschließlich von unseren Server-Funktionen angelegt und geändert.
- Einladungscodes werden zufällig erzeugt.

## 11. Mindestalter

Die App richtet sich an Personen ab **[16 / 18] Jahren**. [Prüfen: Die App erfasst auch alkoholische
Getränke; Mindestalter festlegen und ggf. in der App bzw. im Store-Eintrag angeben.]

## 12. Änderungen dieser Datenschutzerklärung

Wenn sich die App oder die Rechtslage ändert, passen wir diese Erklärung an. Es gilt die jeweils aktuelle
Fassung, die [in der App / unter: URL] abrufbar ist.

---

## Checkliste vor der Veröffentlichung (nicht Teil der Erklärung)

- [ ] Alle Platzhalter in [eckigen Klammern] ausgefüllt, Stand-Datum gesetzt.
- [ ] Text fachkundig prüfen lassen (Datenschutz-Beratung, Verbraucherzentrale o. Ä.).
- [ ] Frage zu Art. 9 DSGVO (Alkoholkonsum als Gesundheitsdatum?) geklärt; ggf. Einwilligungsdialog in die App
      einbauen.
- [ ] Mindestalter entschieden.
- [ ] In der Firebase-Konsole die Datenverarbeitungsbedingungen (Auftragsverarbeitung) akzeptiert.
- [ ] Geprüft, dass **Google Analytics im Firebase-Projekt ausgeschaltet** ist (siehe
      [`FIREBASE_EINRICHTUNG.md`](FIREBASE_EINRICHTUNG.md), Schritt 1).
- [ ] Ablauf für Löschanfragen per E-Mail festgelegt (Anleitung: [`FIREBASE_EINRICHTUNG.md`](FIREBASE_EINRICHTUNG.md),
      Anhang „Konto auf Wunsch löschen“).
- [ ] Erklärung in der App oder unter einer festen Adresse erreichbar gemacht.
- [ ] Bei einer Veröffentlichung im Google Play Store: Formular „Datensicherheit“ (Data safety) passend zu
      diesem Text ausfüllen.
- [ ] Wenn sich die App ändert (z. B. Trinkrunden für iOS, Anzeige von Profilbildern, neue Dienste): Text anpassen.
