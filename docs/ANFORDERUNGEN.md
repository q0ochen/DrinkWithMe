# Anforderungen (Stand: 10.11.2026)

Gesammelt aus der Sprachnachricht des Hauptentwicklers und den Nachrichten des Projektinhabers.
Dieses Dokument ist die Grundlage für den Umbau; offene Entscheidungen stehen am Ende.

## 1. Vernetzung

- Anmeldung mit dem **Google-Konto**. Ein Google-Konto ist die **einzige feste Voraussetzung** – und nur für
  die Vernetzung nötig.
- Andere Nutzer, die die App ebenfalls verwenden und angemeldet sind, können **als Freunde hinzugefügt** werden.
- Die bisherigen **Trinkrunden (Gruppen mit Einladungscode) entfallen.**
- Freunde bekommen eine **Benachrichtigung**, wenn man etwas trinkt.

## 2. „Bar“ – eigene Getränke verwalten

- Der App-Name bleibt **„Drink With Me“**. **„Bar“** heißt nur der Bereich, in dem man seine Getränke verwaltet.
- Jeder erstellt sich seine **eigene Bar** und fügt Getränke hinzu (z. B. Wasser, Apfelschorle, …).
- Getränke werden mit einem **Icon bzw. einer Grafik** dargestellt, die das Getränk zeigt.
- Die Grafiken werden **selbst erstellt** – keine Grafiken aus dem Internet –, damit sie frei verwendet werden dürfen.
- Getrunken wird aus der Bar heraus (Getränk antippen = „jetzt getrunken“; Zeitpunkt immer „jetzt“).

## 3. Gesundheit (mindestens gleichwertig zum sozialen Teil)

- Die **Trinkhistorie wird lokal auf dem Gerät** gespeichert (persönliche Statistik).
- **Anschaulicher grafischer Verlauf** des Getränkekonsums.
- Schwerpunkt: **alkoholhaltige** und **besonders zuckerhaltige** Getränke erfassen, um einen Anreiz zur
  Reduzierung zu schaffen (gesündere Ernährung).
- Statistik als **Bild teilen** (Screenshot oder aus der App erzeugte Bilddatei) – optional, Hauptzweck ist die
  eigene Übersicht.

## 4. Bedienung & Barrierefreiheit

- **Extrem einfach**, auch **per Sprachsteuerung** bedienbar.
- **Vorlesen** von Ereignissen für Menschen, die schlecht sehen: Tippen auf die Push-Nachricht öffnet die App,
  ein weiterer Tipp liest das Ereignis laut vor (z. B. „Anna trinkt eine Apfelschorle“).

## 5. Datenschutz & Einstieg

- Die App soll einfach sein; Nutzer dürfen nicht durch **übermäßige Datenfreigaben/Berechtigungen** von der
  Installation abgehalten werden.

## 6. Plattformen & Vertrieb

- Start auf **Android**, möglichst auch auf **Tablets** und **Smart-TVs** (Frage: Amazon Fire TV Stick 4K Max).
- **iOS** ist langfristig geplant – erst **nach der Testphase**.
- Veröffentlichung auf **F-Droid** (App-Store für freie Software).
- **Auf gar keinen Fall Geld ausgeben** (keine kostenpflichtigen Dienste oder Tarife).

## 7. Vorbild

- „Beer With Me“ dient als **technisches Vorbild** (Vernetzung über Getränke). Name, Grafiken und Gestaltung
  werden **nicht** übernommen; der Gesundheitsaspekt ist bei uns mindestens gleichwertig.

## Entschieden

- App-Name bleibt „Drink With Me“; „Bar“ ist nur der Bereich zur Getränkeverwaltung (10.11.2026).

- **Server: Supabase** (kostenloser Tarif ohne Kreditkarte); Firebase entfällt (10.11.2026).
- **Benachrichtigungen:** UnifiedPush mit der kostenlosen App **ntfy** (optional, für sofortige Nachrichten);
  ohne ntfy prüft die App etwa alle 15 Minuten und beim Öffnen (10.11.2026).
- **Freunde hinzufügen:** per **Einladungs-Link** oder per **Google-E-Mail-Adresse** mit Bestätigung (10.11.2026).
- **iPhone in der Testphase:** kostenlose **Web-App** (PWA) mit Anmeldung, Freunden und Push (10.11.2026).
- Alkoholische Getränke werden erst nach einmaliger Zustimmung mit Freunden geteilt; Mengen, Zucker und
  Statistiken werden nie geteilt (Datenschutz, Gesundheitsdaten).

Technische Umsetzung: siehe [`ARCHITEKTUR.md`](ARCHITEKTUR.md).

## Offene Entscheidungen

- Hosting der Web-App (z. B. GitHub Pages – setzt ein öffentliches Repository voraus, das für F-Droid ohnehin
  nötig ist – oder Netlify/Cloudflare Pages).
