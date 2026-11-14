# Grafiken von Drink With Me

Alle Grafiken der App sind **selbst gezeichnet** (von Hand geschriebenes SVG, keine Vorlagen, keine Icon-Sammlungen,
keine Marken oder Logos) und stehen unter **CC0 1.0** – siehe [`icons/LICENSE`](icons/LICENSE).
Die SVGs sind die einzige Quelle: Die Android-Grafiken werden daraus **erzeugt**, damit beide nie auseinanderlaufen.

```
design/
├── icons/
│   ├── <key>.svg                 22 Getränke-Icons, ein SVG je DrinkIcon-Schlüssel (viewBox 0 0 48 48)
│   ├── app_icon_background.svg   App-Symbol, Hintergrundebene (108 × 108)
│   ├── app_icon_foreground.svg   App-Symbol, Vordergrundebene (Inhalt in der Schutzzone)
│   ├── app_icon.svg              beide Ebenen zusammen (erzeugt) – für Web-App/PWA und Store-Einträge
│   ├── tv_banner.svg             Banner für Android TV / Fire TV (320 × 180, Schrift als Linien)
│   ├── notification.svg          Benachrichtigungssymbol (einfarbig weiß, 24 × 24)
│   └── LICENSE                   CC0 1.0 und Hinweis „eigene Werke“
└── tools/
    ├── svg2vector.py             SVG → Android-VectorDrawable (convert, sync, check)
    ├── svgpath.py                Pfaddaten einlesen, einheitlich ausgeben, abtasten
    ├── vector_checks.py          Prüfungen: erlaubte Attribute, Ränder, Schutzzone, Aktualität
    ├── contact_sheet.py          Kontaktbogen (HTML) zur Sichtprüfung
    └── test_*.py                 Tests (unittest)
```

Alle Werkzeuge brauchen nur **Python 3.10+ ohne Zusatzpakete**.

## Wo werden die Grafiken verwendet?

| Grafik | Android | Web-App |
|---|---|---|
| `icons/<key>.svg` | `R.drawable.drink_<key>` (`res/drawable/drink_<key>.xml`) | `icons/<key>.svg` |
| App-Symbol | `@mipmap/ic_launcher`, `android:roundIcon="@mipmap/ic_launcher_round"` (adaptiv, ab API 26) | `app_icon.svg` (auch als „maskable“ geeignet) |
| TV-Banner | `android:banner="@drawable/tv_banner"` (160 × 90 dp = 320 × 180 px bei xhdpi) | – |
| Benachrichtigung | `@drawable/ic_notification` (siehe unten) | – |

Der Schlüssel `<key>` ist `DrinkIcon.key` aus dem Kotlin-Kern (`docs/ARCHITEKTUR.md`, Abschnitt 1.1).

## Ein Icon ändern

1. `design/icons/<key>.svg` in einem Texteditor (oder z. B. Inkscape, dann „Normales SVG“ speichern) bearbeiten.
   Die [Stilregeln](#stilregeln) und die [erlaubte SVG-Teilmenge](#erlaubte-svg-teilmenge) beachten.
2. Android-Grafiken neu erzeugen (im Wurzelverzeichnis des Repositorys):
   ```sh
   python3 design/tools/svg2vector.py sync
   ```
3. Prüfen (dasselbe kann die CI ausführen; Exit-Code 1 bei Fehlern):
   ```sh
   python3 design/tools/svg2vector.py check
   python3 -m unittest discover -s design/tools -p 'test_*.py'
   ```
4. Anschauen: Kontaktbogen erzeugen und im Browser öffnen. Er zeigt jedes Icon auf hellem und dunklem Grund
   (48 px und 96 px), in Graustufen, das erzeugte Android-Drawable direkt neben dem SVG, das App-Symbol mit
   verschiedenen Launcher-Masken samt Schutzzone und das TV-Banner.
   ```sh
   python3 design/tools/contact_sheet.py /tmp/drink-icons.html
   ```
5. SVG **und** erzeugte Dateien gemeinsam einchecken. Erzeugte Dateien nie von Hand bearbeiten – sie tragen den
   Hinweis „Erzeugt aus … mit design/tools/svg2vector.py“.

## Ein neues Getränke-Icon hinzufügen

1. Neuen Eintrag in `enum class DrinkIcon` (`android/core/.../bar/DrinkIcon.kt`) **und** in `DRINK_ICON_KEYS`
   (`design/tools/svg2vector.py`) – gleiche Reihenfolge, Schlüssel nur `a–z` und `_`. `check` meldet Abweichungen.
2. `design/icons/<key>.svg` zeichnen (am einfachsten ein ähnliches Icon kopieren und umbauen).
3. `sync`, `check`, Kontaktbogen ansehen (siehe oben). Die Web-App übernimmt die SVGs aus `design/icons/`.

## Stilregeln

- **Raster:** viewBox `0 0 48 48`, Inhalt mit mindestens 1 Einheit Abstand zum Rand (prüft `check`),
  Gefäße stehen unten bei y ≈ 42–43.
- **Umriss:** `#263238`, Breite **2**, `stroke-linejoin="round"`; kleine Details 1,2–1,8. Der Umriss wird zuletzt
  gezeichnet und deckt die Kanten der Flüssigkeit ab.
- **Aufbau eines Glases:** 1. Glasfläche `#E4F2F9`, 2. Flüssigkeit (flächig, gleiche Kontur wie das Glas, oben
  gerade oder leicht gewellt), 3. Details (Blasen, Eis, Glanz), 4. Umriss, 5. Beiwerk davor (Scheibe am Rand,
  Apfel, Ähre, Schirmchen).
- **Dunkle Flüssigkeiten** (Cola, Malzbier) mit sichtbarer heller Glaswand (≈ 1,5 Einheiten Abstand zum Umriss),
  damit die Form auch auf dunklem Grund erkennbar bleibt.
- **Glanzlichter:** weiße Linie (`#FFFFFF`, Deckkraft 0,5–0,9, Breite 2, runde Enden), meist links.
  **Dampf** heißer Getränke: `#90A4AE`.
- **Unterscheidung über die Form**, nicht nur über die Farbe (jedes Icon muss in Graustufen und bei 48 dp
  erkennbar sein): jedes Getränk hat ein eigenes Gefäß oder ein eigenes Merkmal (Tabelle unten).
- **Kein Text** in Getränke-Icons, keine Marken-Anmutung (z. B. keine Kontur-Flaschen, keine Schleifen auf Etiketten).
- **Keine Verläufe, Filter, Schatten** – flächige Farben, Transparenz nur über `fill-opacity`/`stroke-opacity`.

| Schlüssel | Gefäß | Merkmal |
|---|---|---|
| `water` | Becher | Wasser mit Welle |
| `sparkling_water` | Becher | Blasen im Glas und darüber |
| `coffee` | Henkelbecher | Kaffee von oben, Dampf |
| `tea` | Tasse mit Untertasse | Teebeutel-Etikett, Dampf |
| `juice` | niedriges, breites Glas | Orangenscheibe am Rand |
| `apple_spritzer` | hohes Glas | Blasen, Apfel davor |
| `cola` | Flasche | Trinkhalm, dunkel mit Blasen |
| `lemonade` | Dose | Zitrone mit Blatt |
| `iced_tea` | konisches Glas | Eiswürfel, Teebeutel-Etikett |
| `energy_drink` | schlanke Dose | Blitz |
| `milk` | Milchkarton | Tropfen auf blauem Band |
| `malt_beer` | Tulpenglas mit Stiel | dunkel, heller Schaum, Ähre daneben |
| `beer` | Seidel mit Henkel | Schaumkrone mit Tropfen, Facetten |
| `wheat_beer` | Weizenglas (geschwungen) | hohe Schaumkrone |
| `radler` | Becher mit Wulst | Schaum, Zitronenscheibe |
| `red_wine` | bauchiges Weinglas | dunkelroter Wein |
| `white_wine` | schlankes Weinglas, langer Stiel | heller Wein |
| `sparkling_wine` | Sektflöte | Perlen, Funkeln |
| `shot` | kleines Schnapsglas | dicker Boden |
| `longdrink` | hohes, gerades Glas | Eis, Limettenschnitz, Trinkhalm |
| `cocktail` | Cocktailschale (V-Form) | Schirmchen |
| `other` | Becher mit Kuppeldeckel | Trinkhalm |

### Farben

| Zweck | Farbe |
|---|---|
| Umriss | `#263238` |
| Glas | `#E4F2F9` |
| Glanz, Schaum, Blasen | `#FFFFFF` |
| Metall (Dosen) | `#CFD8DC` |
| Dampf | `#90A4AE` |
| Wasser / Mineralwasser | `#4FB4E8` / `#7CCBEF` |
| Orange (Saft, Scheibe) | `#FFA21F`, `#F57C00`, `#FFC069` |
| Apfelschorle, Apfel, Blatt | `#F2BE3C`, `#E5483D`, `#7CB342` |
| Kaffee (Becher, Kaffee) | `#EC7F57`, `#F6A684`, `#6B4434` |
| Tee, Etikett, Porzellan | `#D9822B`, `#FFCB47`, `#FFF7EA`, Band `#5BA4D9` |
| Cola, Trinkhalm | `#5A3322`, `#FF8A65` |
| Limonade (Dose, Zitrone) | `#FFF2B8`, `#FFD43B` |
| Energydrink, Blitz | `#7C5CD6`, `#FFE14D` |
| Milch (Karton, Band) | `#E3EEF7`, `#4A9FE0` |
| Malzbier, Schaum, Ähre | `#5B3518`, `#F2DFC0`, `#E9B949` |
| Bier / Weizen / Radler | `#F6B92B` / `#EFA33A` / `#FFDF73`, Zitrone `#F9C80E`, `#FFF3A3` |
| Rotwein / Weißwein / Sekt | `#9E1C3F` / `#F1D77C` / `#F7D774`, Funkeln `#FFC23D` |
| Schnaps | `#E8A33D` |
| Longdrink, Limette | `#8A4B2A`, `#7CB342`, `#D4E9A8` |
| Cocktail, Schirmchen | `#EC4F86`, `#33B5F0` |
| Sonstiges, Trinkhalm | `#9C7BD6`, `#5BC0A8` |
| App-Symbol / Banner | Grund `#12707F`, Kreis `#1A8191`, Schrift `#FFFFFF` |

## Erlaubte SVG-Teilmenge

`svg2vector.py` versteht genau das, was ein Android-VectorDrawable gleich darstellen kann, und bricht sonst mit
einer Fehlermeldung ab (lieber ein Fehler als eine still abweichende Android-Grafik):

- Elemente: `svg` (mit `viewBox`), `g`, `path`, `circle`, `ellipse`, `rect` (auch `rx`/`ry`), `line`, `polygon`,
  `polyline`. `title`, `desc`, `metadata` und Kommentare werden ignoriert.
- Attribute (direkt oder in `style="…"`, über `g` vererbt): `fill`, `fill-opacity`, `fill-rule`, `stroke`,
  `stroke-opacity`, `stroke-width`, `stroke-linecap`, `stroke-linejoin`, `stroke-miterlimit`, `opacity`.
  Farben als `#rgb`, `#rrggbb`, `rgb(…)` oder einfache Namen (`white`, `black` …).
- `transform` mit `translate`, `scale` und `rotate` (wird zu `<group>`); **nicht** `matrix`/`skewX`/`skewY`.
- `opacity` wird auf die einzelnen Formen verteilt; bei Formen mit Füllung **und** Kontur bitte
  `fill-opacity`/`stroke-opacity` verwenden (sonst Fehler).
- **Nicht erlaubt:** `text` (Schrift als Pfade/Linien zeichnen), Verläufe, Muster, Filter, Masken, `clip-path`,
  `use`, Bilder, CSS (`<style>`, `class`-Regeln), gestrichelte Linien.

Die erzeugten Pfaddaten sind vereinheitlicht (jeder Befehl ausgeschrieben, Zahlen durch Kommas getrennt,
höchstens drei Nachkommastellen); Kreise, Ellipsen und abgerundete Rechtecke werden zu Bögen.
`check` prüft außerdem, dass jede erzeugte Datei nur von VectorDrawable unterstützte Elemente und Attribute mit
gültigen Werten enthält, dass Getränke-Icons nicht über den Rand ragen und der Vordergrund des App-Symbols in der
Schutzzone liegt.

## App-Symbol, TV-Banner, Benachrichtigung

- **App-Symbol (adaptiv):** zwei Ebenen mit 108 × 108 dp. Sichtbar ist je nach Launcher-Maske (Kreis, Squircle,
  abgerundetes Quadrat …) nur die Mitte; der **Vordergrund muss im Kreis mit 66 dp Durchmesser um die Mitte
  (54/54) liegen** – `check` misst das nach. Motiv: zwei anstoßende Gläser (Wasser und Saft), bewusst ohne
  Alkohol. `app_icon.svg` (beide Ebenen übereinander) wird von `sync` erzeugt.
- **TV-Banner:** 320 × 180, Schrift „Drink With Me“ als selbst gezeichnete Linien (VectorDrawable kann keinen Text).
  Wichtiges im mittleren Bereich lassen, Launcher runden die Ecken ab.
- **Benachrichtigung:** `notification.svg` ist einfarbig weiß auf transparent, wie Android es verlangt. Die
  bisherige `res/drawable/ic_notification.xml` wurde nicht mit diesem Werkzeug erzeugt; `sync` überschreibt
  solche fremden Dateien **nie** (`check` warnt nur). Zum Übernehmen die alte Datei löschen und `sync` ausführen.
