# Golden-Daten aus dem Kotlin-Kern

`GoldenExport.kt` lässt den echten Kotlin-Kern (`android/core`) für viele feste, pseudozufällige Eingaben rechnen –
Zahlwörter, Vorlese-Sätze, Artikel, Normalisierung, Kölner Phonetik, Jaro-Winkler, Sprachabgleich gegen drei Bars,
Statistik über 40 Verläufe (Zeitzone Berlin, auch über Zeitumstellungen) und Einladungs-Links – und schreibt alles nach
`web/src/core/golden/kotlin-golden.json`. Der Test `kotlinGenerated.golden.test.ts` verlangt, dass der TypeScript-Port
für dieselben Eingaben **exakt** dieselben Ausgaben liefert (auch Kommazahlen bitgenau).

Neu erzeugen, wenn sich der Kotlin-Kern ändert (JDK 17+ und Gradle 8 nötig, `android/` bleibt unverändert):

```bash
GRADLE=/pfad/zu/gradle web/tools/kotlin-golden/export.sh
cd web && npm test
```

Zusätzlich vergleicht `kotlinSourceDrift.test.ts` bei jedem `npm test` die Tabellen (Katalog, Icons, Kategorien,
Bezugswerte, Pflichttexte, Füllwörter, Artikel-Endungen, Schwellen) direkt mit dem Kotlin-Quelltext.
