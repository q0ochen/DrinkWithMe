# SPDX-License-Identifier: CC0-1.0
"""Tests für die Prüfungen der erzeugten VectorDrawables."""
import math
import tempfile
import unittest
from pathlib import Path

from vector_checks import (LINT_MAX_PATH_LENGTH, OutlinePoint, VectorDrawableValidator, bounds, check_project,
                           kotlin_icon_keys, max_distance, outline_points)

NS = 'xmlns:android="http://schemas.android.com/apk/res/android"'


def vector(body: str, size: str = 'android:width="48dp" android:height="48dp" android:viewportWidth="48" '
                                  'android:viewportHeight="48"') -> str:
    return f"<vector {NS} {size}>{body}</vector>"


def path(attributes: str = 'android:fillColor="#000000"', data: str = "M0,0 H10 V10 Z") -> str:
    return f'<path android:pathData="{data}" {attributes}/>'


class ValidatorTest(unittest.TestCase):
    def setUp(self):
        self.validator = VectorDrawableValidator()

    def assertInvalid(self, xml_text: str, fragment: str):
        report = self.validator.validate(xml_text)
        self.assertFalse(report.ok, xml_text)
        self.assertTrue(any(fragment in error for error in report.errors), report.errors)

    def test_gueltiges_minimum(self):
        report = self.validator.validate(vector(path() + '<group android:rotation="10" android:pivotX="24">'
                                                + path('android:strokeColor="#80FFFFFF" android:strokeWidth="2" '
                                                       'android:strokeLineCap="round" android:fillType="evenOdd"')
                                                + "</group>"))
        self.assertEqual((report.errors, report.warnings), ([], []))

    def test_fehler(self):
        cases = {
            "nicht wohlgeformt": ("<vector", "wohlgeformt"),
            "falsche Wurzel": (f"<shape {NS}/>", "<vector>"),
            "fremdes Element": (vector("<text/>"), "<text>"),
            "unbekanntes Attribut": (vector(path('android:fillColor="#000" android:blur="2"')), "android:blur"),
            "ohne Namensraum": (vector(path('fill="#000"')), "ohne android:"),
            "Farbe": (vector(path('android:fillColor="#12"')), "Farbe"),
            "Farbe benannt": (vector(path('android:fillColor="red"')), "Farbe"),
            "Größe": (vector(path(), 'android:width="48px" android:height="48dp" android:viewportWidth="48" '
                                    'android:viewportHeight="48"'), "dp"),
            "Aufzählung": (vector(path('android:strokeColor="#000" android:strokeLineCap="spitz"')), "erlaubt"),
            "Pfaddaten": (vector(path(data="Q1")), "pathData"),
            "Deckkraft": (vector(path('android:fillColor="#000" android:fillAlpha="1.5"')), "zwischen 0 und 1"),
            "Zahl": (vector(path('android:strokeColor="#000" android:strokeWidth="dick"')), "Zahl"),
            "Pflicht": (vector("<path android:fillColor=\"#000\"/>"), "Pflichtattribut"),
            "Viewport": (vector(path(), 'android:width="1dp" android:height="1dp" android:viewportWidth="0" '
                                       'android:viewportHeight="1"'), "positiver"),
            "Kind im Pfad": (vector('<path android:pathData="M0,0 L1,1" android:strokeColor="#000"><path/></path>'),
                             "nicht erlaubt"),
            "Namensraum fehlt": ('<vector width="1"/>', "xmlns:android"),
        }
        for name, (xml_text, fragment) in cases.items():
            with self.subTest(name):
                self.assertInvalid(xml_text, fragment)

    def test_warnungen(self):
        long_data = "M0,0 " + " ".join("L1,1" for _ in range(LINT_MAX_PATH_LENGTH // 4))
        report = self.validator.validate(vector(path(data=long_data) + '<path android:pathData="M0,0 L1,1"/>'))
        self.assertTrue(report.ok)
        self.assertEqual(len(report.warnings), 2)


class GeometryTest(unittest.TestCase):
    def test_umriss_mit_strichbreite(self):
        points = outline_points(vector(path('android:strokeColor="#000" android:strokeWidth="2"')))
        box = bounds(points)
        self.assertEqual((box.left, box.top, box.right, box.bottom), (-1, -1, 11, 11))

    def test_gruppen_transformation_wie_android(self):
        # Drehung um 90° um den Pivot (10/10), danach verschoben um (5/0)
        xml_text = vector('<group android:rotation="90" android:pivotX="10" android:pivotY="10" '
                          'android:translateX="5">' + path(data="M20,10 L20,10") + "</group>")
        x, y = outline_points(xml_text)[0].x, outline_points(xml_text)[0].y
        self.assertAlmostEqual(x, 15)
        self.assertAlmostEqual(y, 20)

    def test_skalierung_vergroessert_strich(self):
        xml_text = vector('<group android:scaleX="2" android:scaleY="2">'
                          + path('android:strokeColor="#000" android:strokeWidth="2"', "M5,5 L5,5") + "</group>")
        point = outline_points(xml_text)[0]
        self.assertEqual((point.x, point.y, point.reach), (10, 10, 2))

    def test_maximaler_abstand(self):
        self.assertAlmostEqual(max_distance([OutlinePoint(3, 4, 1), OutlinePoint(0, 1, 0)], 0, 0), 6)
        self.assertTrue(math.isclose(max_distance([OutlinePoint(54, 21, 0)], 54, 54), 33))


class KotlinKeysTest(unittest.TestCase):
    def test_schluessel_aus_enum(self):
        with tempfile.TemporaryDirectory() as tmp:
            file = Path(tmp) / "DrinkIcon.kt"
            file.write_text('enum class DrinkIcon(val key: String) {\n    WATER("water"), COFFEE("coffee"),\n'
                            '    OTHER("other"),\n    ;\n    companion object { fun x() = OTHER("x") }\n}\n')
            self.assertEqual(kotlin_icon_keys(file), ["water", "coffee", "other"])
            self.assertIsNone(kotlin_icon_keys(Path(tmp) / "fehlt.kt"))


class _FakeSpec:
    def __init__(self, source, target):
        self.source, self.target = source, target


class _FakeProject:
    """Minimaler Projekt-Doppelgänger: liefert festen Inhalt, als wäre er aus der Quelle erzeugt."""

    def __init__(self, base: Path, contents: dict):
        self.icons_dir = base / "icons"
        self.res_dir = base / "res"
        self.icons_dir.mkdir()
        (self.res_dir / "drawable").mkdir(parents=True)
        (self.res_dir / "mipmap-anydpi-v26").mkdir()
        self.specs = []
        self._contents = {}
        for target, xml_text in contents.items():
            source = Path(target).stem + ".svg"
            (self.icons_dir / source).write_text("<svg/>")
            (self.res_dir / target).write_text(xml_text)
            spec = _FakeSpec(source, target)
            self.specs.append(spec)
            self._contents[target] = xml_text
        (self.icons_dir / "app_icon.svg").write_text("APP")

    def render(self, spec):
        return self._contents[spec.target]

    def render_app_icon(self):
        return "APP"

    @staticmethod
    def is_generated(text):
        return "erzeugt" in text

    def write_adaptive_icons(self, foreground="ic_launcher_foreground"):
        for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
            (self.res_dir / "mipmap-anydpi-v26" / name).write_text(
                f'<adaptive-icon {NS}><background android:drawable="@drawable/ic_launcher_background"/>'
                f'<foreground android:drawable="@drawable/{foreground}"/></adaptive-icon>')


def generated(body: str, viewport: int = 48) -> str:
    return ("<!-- erzeugt -->" + vector(body, f'android:width="{viewport}dp" android:height="{viewport}dp" '
                                              f'android:viewportWidth="{viewport}" android:viewportHeight="{viewport}"'))


class CheckProjectTest(unittest.TestCase):
    def make(self, contents: dict) -> _FakeProject:
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        return _FakeProject(Path(self._tmp.name), contents)

    def base_contents(self, icon_body: str = path(), foreground: str = path(data="M40,40 H60 V60 Z")) -> dict:
        return {
            "drawable/drink_a.xml": generated(icon_body),
            "drawable/ic_launcher_foreground.xml": generated(foreground, 108),
            "drawable/ic_launcher_background.xml": generated(path(data="M0,0 H108 V108 H0 Z"), 108),
        }

    def test_alles_in_ordnung(self):
        project = self.make(self.base_contents(icon_body=path(data="M1,1 H47 V47 H1 Z")))
        project.write_adaptive_icons()
        errors, warnings = check_project(project, ["a"])
        self.assertEqual((errors, warnings), ([], []))

    def test_icon_ragt_ueber_den_rand(self):
        project = self.make(self.base_contents(icon_body=path(data="M40,40 H50 V50 Z")))
        project.write_adaptive_icons()
        errors, _ = check_project(project, ["a"])
        self.assertTrue(any("zu nah am oder über dem Rand" in e for e in errors), errors)

    def test_vordergrund_ausserhalb_der_schutzzone(self):
        project = self.make(self.base_contents(foreground=path(data="M18,18 H90 V90 Z")))
        project.write_adaptive_icons()
        errors, _ = check_project(project, ["a"])
        self.assertTrue(any("Schutzzone" in e for e in errors), errors)

    def test_adaptives_symbol_fehlt_oder_verweist_ins_leere(self):
        project = self.make(self.base_contents())
        errors, _ = check_project(project, ["a"])
        self.assertTrue(any("ic_launcher.xml fehlt" in e for e in errors), errors)
        project.write_adaptive_icons(foreground="gibt_es_nicht")
        errors, _ = check_project(project, ["a"])
        self.assertTrue(any("@drawable/gibt_es_nicht existiert nicht" in e for e in errors), errors)

    def test_veraltet_und_ungueltig(self):
        project = self.make(self.base_contents())
        project.write_adaptive_icons()
        (project.res_dir / "drawable" / "drink_a.xml").write_text("<!-- erzeugt --><vector/>")
        errors, _ = check_project(project, ["a"])
        self.assertTrue(any("veraltet" in e for e in errors), errors)
        self.assertTrue(any("xmlns:android" in e for e in errors), errors)

    def test_fremde_datei_nur_warnung(self):
        project = self.make(self.base_contents())
        project.write_adaptive_icons()
        (project.res_dir / "drawable" / "drink_a.xml").write_text(vector(path()))
        errors, warnings = check_project(project, ["a"])
        self.assertEqual(errors, [])
        self.assertTrue(any("nicht aus design/icons/drink_a.svg erzeugt" in w for w in warnings), warnings)

    def test_kotlin_schluessel_weichen_ab(self):
        project = self.make(self.base_contents())
        project.write_adaptive_icons()
        kotlin = Path(self._tmp.name) / "DrinkIcon.kt"
        kotlin.write_text('enum class DrinkIcon(val key: String) { A("a"), B("b"), ; }')
        errors, _ = check_project(project, ["a"], kotlin)
        self.assertTrue(any("weichen ab" in e for e in errors), errors)

    def test_zusammengesetztes_app_symbol_veraltet(self):
        project = self.make(self.base_contents())
        project.write_adaptive_icons()
        (project.icons_dir / "app_icon.svg").write_text("ALT")
        errors, _ = check_project(project, ["a"])
        self.assertTrue(any("app_icon.svg" in e for e in errors), errors)


if __name__ == "__main__":
    unittest.main()
