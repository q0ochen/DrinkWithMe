# SPDX-License-Identifier: CC0-1.0
"""Tests für den SVG→VectorDrawable-Umwandler."""
import contextlib
import io
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

from svg2vector import (ConversionError, GroupTransform, OutputSpec, Project, SvgToVectorConverter, VectorGroup,
                        VectorPath, android_color, compose_svg, convert_svg, main, parse_color, parse_transform)
from vector_checks import VectorDrawableValidator

A = "{http://schemas.android.com/apk/res/android}"


def svg(body: str, view_box: str = "0 0 48 48") -> str:
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{view_box}">{body}</svg>'


def convert(body: str, **kwargs):
    return SvgToVectorConverter().convert(svg(body), **kwargs)


def only_path(body: str) -> VectorPath:
    document = convert(body)
    assert len(document.children) == 1, document.children
    node = document.children[0]
    assert isinstance(node, VectorPath), node
    return node


class ColorTest(unittest.TestCase):
    def test_formate(self):
        self.assertEqual(parse_color("#abc"), (170, 187, 204))
        self.assertEqual(parse_color("#A0B1C2"), (160, 177, 194))
        self.assertEqual(parse_color("rgb(10, 20, 30)"), (10, 20, 30))
        self.assertEqual(parse_color("rgb(100%,0%,50%)"), (255, 0, 128))
        self.assertEqual(parse_color("white"), (255, 255, 255))
        self.assertIsNone(parse_color("none"))

    def test_nicht_unterstuetzt(self):
        for bad in ("url(#verlauf)", "hsl(0,0%,0%)", "currentColor", "#12"):
            with self.subTest(bad=bad), self.assertRaises(ConversionError):
                parse_color(bad)

    def test_android_farbe(self):
        self.assertEqual(android_color((38, 50, 56)), "#263238")
        self.assertEqual(android_color((255, 255, 255), 0.5), "#80FFFFFF")
        self.assertEqual(android_color((0, 0, 0), 0), "#00000000")


class StyleTest(unittest.TestCase):
    def test_standardfuellung_ist_schwarz(self):
        path = only_path('<rect width="10" height="10"/>')
        self.assertEqual(path.fill_color, "#000000")
        self.assertIsNone(path.stroke_color)

    def test_vererbung_ueber_gruppen(self):
        path = only_path('<g fill="#ff0000" stroke="#00ff00" stroke-width="3" stroke-linecap="round">'
                         '<g><circle cx="5" cy="5" r="2"/></g></g>')
        self.assertEqual((path.fill_color, path.stroke_color, path.stroke_width, path.stroke_line_cap),
                         ("#FF0000", "#00FF00", 3.0, "round"))

    def test_deckkraft_wird_multipliziert_und_in_farbe_gefaltet(self):
        path = only_path('<g opacity="0.5"><rect width="4" height="4" fill="#fff" fill-opacity="0.5"/></g>')
        self.assertEqual(path.fill_color, "#40FFFFFF")

    def test_style_attribut_hat_vorrang(self):
        path = only_path('<rect width="4" height="4" fill="#000" style="fill: #0000ff; stroke:#fff"/>')
        self.assertEqual((path.fill_color, path.stroke_color), ("#0000FF", "#FFFFFF"))

    def test_fuellregel_und_linien(self):
        path = only_path('<path d="M0 0 H9 V9 Z" fill-rule="evenodd" stroke="#000" stroke-linejoin="bevel"/>')
        self.assertEqual((path.fill_type, path.stroke_line_join, path.stroke_miter_limit), ("evenOdd", "bevel", None))

    def test_gehrungsgrenze_nur_bei_miter(self):
        path = only_path('<path d="M0 0 L9 9" fill="none" stroke="#000" stroke-miterlimit="10"/>')
        self.assertEqual(path.stroke_miter_limit, 10.0)

    def test_unsichtbares_wird_weggelassen(self):
        document = convert('<rect width="4" height="4" fill="none"/><rect width="2" height="2" fill="#000" '
                           'fill-opacity="0"/><circle r="3" fill="#123456"/>')
        self.assertEqual(len(document.children), 1)

    def test_nur_unsichtbares_ist_ein_fehler(self):
        with self.assertRaises(ConversionError):
            convert('<rect width="4" height="4" fill="none"/>')

    def test_deckkraft_bei_fuellung_und_kontur_ist_ein_fehler(self):
        with self.assertRaises(ConversionError):
            convert('<rect width="4" height="4" fill="#000" stroke="#fff" opacity="0.5"/>')

    def test_nicht_unterstuetzte_eigenschaften(self):
        for attribute in ('stroke-dasharray="2 2"', 'filter="url(#f)"', 'style="mask: url(#m)"'):
            with self.subTest(attribute=attribute), self.assertRaises(ConversionError):
                convert(f'<path d="M0 0 L9 9" stroke="#000" {attribute}/>')

    def test_ungueltige_werte(self):
        for attribute in ('stroke-linecap="pointy"', 'fill-rule="odd"', 'stroke-width="2em"'):
            with self.subTest(attribute=attribute), self.assertRaises(ConversionError):
                convert(f'<path d="M0 0 L9 9" stroke="#000" {attribute}/>')


class ShapeTest(unittest.TestCase):
    def test_kreis(self):
        self.assertEqual(only_path('<circle cx="24" cy="20" r="4"/>').path_data,
                         "M20,20 A4,4,0,1,1,28,20 A4,4,0,1,1,20,20 Z")

    def test_ellipse(self):
        self.assertEqual(only_path('<ellipse cx="10" cy="10" rx="5" ry="2"/>').path_data,
                         "M5,10 A5,2,0,1,1,15,10 A5,2,0,1,1,5,10 Z")

    def test_rechteck(self):
        self.assertEqual(only_path('<rect x="1" y="2" width="3" height="4"/>').path_data, "M1,2 H4 V6 H1 Z")

    def test_abgerundetes_rechteck_mit_begrenzten_radien(self):
        self.assertEqual(only_path('<rect width="10" height="4" rx="3"/>').path_data,
                         "M3,0 H7 A3,2,0,0,1,10,2 V2 A3,2,0,0,1,7,4 H3 A3,2,0,0,1,0,2 V2 A3,2,0,0,1,3,0 Z")

    def test_linie_ignoriert_fuellung(self):
        path = only_path('<line x1="1" y1="2" x2="3" y2="4" stroke="#000"/>')
        self.assertEqual((path.path_data, path.fill_color), ("M1,2 L3,4", None))

    def test_linie_ohne_kontur_ist_unsichtbar(self):
        with self.assertRaises(ConversionError):
            convert('<line x1="1" y1="2" x2="3" y2="4"/>')

    def test_polygon_und_polyline(self):
        self.assertEqual(only_path('<polygon points="0,0 4,0 4,4"/>').path_data, "M0,0 L4,0 L4,4 Z")
        self.assertEqual(only_path('<polyline points="0 0 4 0 4 4" fill="none" stroke="#000"/>').path_data,
                         "M0,0 L4,0 L4,4")

    def test_fehlerhafte_formen(self):
        for body in ('<circle cx="1" cy="1"/>', '<circle r="0"/>', '<rect width="0" height="3"/>',
                     '<polygon points="1,2"/>', '<polygon points="1,2 3"/>', '<path d="L1 2"/>'):
            with self.subTest(body=body), self.assertRaises(ConversionError):
                convert(body)

    def test_pfad_wird_normalisiert(self):
        self.assertEqual(only_path('<path d="M1 1a1 1 0 011 1z"/>').path_data, "M1,1 a1,1,0,0,1,1,1 z")


class TransformTest(unittest.TestCase):
    def test_einzelne_transformationen(self):
        self.assertEqual(parse_transform("translate(3)"), [GroupTransform(translate_x=3)])
        self.assertEqual(parse_transform("scale(2)"), [GroupTransform(scale_x=2, scale_y=2)])
        self.assertEqual(parse_transform("rotate(30 4 5)"), [GroupTransform(rotation=30, pivot_x=4, pivot_y=5)])

    def test_liste_wird_zu_verschachtelten_gruppen(self):
        document = convert('<g transform="translate(1 2) rotate(30)"><rect width="1" height="1"/></g>')
        outer = document.children[0]
        self.assertIsInstance(outer, VectorGroup)
        self.assertEqual(outer.transform, GroupTransform(translate_x=1, translate_y=2))
        inner = outer.children[0]
        self.assertEqual(inner.transform, GroupTransform(rotation=30))
        self.assertIsInstance(inner.children[0], VectorPath)

    def test_transformation_an_einer_form(self):
        document = convert('<ellipse cx="5" cy="5" rx="3" ry="1" transform="rotate(45 5 5)"/>')
        self.assertEqual(document.children[0].transform, GroupTransform(rotation=45, pivot_x=5, pivot_y=5))

    def test_nicht_unterstuetzte_transformationen(self):
        for value in ("matrix(1 0 0 1 0 0)", "skewX(10)", "rotate(1 2)", "translate(1 2 3)", "wackeln(1)"):
            with self.subTest(value=value), self.assertRaises(ConversionError):
                parse_transform(value)


class DocumentTest(unittest.TestCase):
    def test_viewbox_pflicht_und_gueltig(self):
        for root in ('<svg xmlns="http://www.w3.org/2000/svg"><rect width="1" height="1"/></svg>',
                     '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 0 5"><rect width="1" height="1"/></svg>',
                     '<html/>', 'kein xml'):
            with self.subTest(root=root), self.assertRaises(ConversionError):
                SvgToVectorConverter().convert(root)

    def test_verschobene_viewbox_wird_ausgeglichen(self):
        document = SvgToVectorConverter().convert(svg('<rect width="1" height="1"/>', "10 20 48 48"))
        self.assertEqual(document.children[0].transform, GroupTransform(translate_x=-10, translate_y=-20))

    def test_groesse_in_dp(self):
        document = convert('<rect width="1" height="1"/>', width_dp=24, height_dp=24)
        self.assertEqual((document.width_dp, document.viewport_width), (24, 48))

    def test_nicht_unterstuetzte_elemente(self):
        for body in ('<text x="1" y="1">Hallo</text>', '<linearGradient id="v"/>', '<use href="#a"/>',
                     '<image href="a.png" width="1" height="1"/>', '<style>rect{fill:red}</style>'):
            with self.subTest(body=body), self.assertRaises(ConversionError):
                convert(body + '<rect width="1" height="1"/>')

    def test_titel_beschreibung_und_kommentare_werden_ignoriert(self):
        document = convert('<title>T</title><desc>D</desc><!-- Kommentar --><rect width="1" height="1"/>')
        self.assertEqual(len(document.children), 1)


class WriterTest(unittest.TestCase):
    def test_ausgabe_ist_gueltiges_vectordrawable(self):
        xml_text = convert_svg(svg(
            '<g transform="translate(1 1) scale(0.5) rotate(10 24 24)" stroke="#263238" stroke-width="2" '
            'stroke-linejoin="round" stroke-linecap="round"><circle cx="24" cy="24" r="8" fill="#E4F2F9"/>'
            '<line x1="0" y1="0" x2="4" y2="4" stroke-opacity="0.5"/></g>'
            '<path d="M0 0 H9 V9 Z" fill="#fff" fill-opacity="0.4" fill-rule="evenodd"/>'), 48, 48, "Test -- mit Strichen")
        report = VectorDrawableValidator().validate(xml_text)
        self.assertEqual(report.errors, [])
        root = ET.fromstring(xml_text)
        self.assertEqual(root.tag, "vector")
        self.assertEqual(root.get(A + "width"), "48dp")
        group = root.find("group")
        self.assertEqual((group.get(A + "translateX"), group.find("group").get(A + "scaleX")), ("1", "0.5"))
        self.assertIn("<!-- Test – mit Strichen -->", xml_text)
        self.assertIn('android:fillType="evenOdd"', xml_text)
        self.assertIn('android:strokeColor="#80263238"', xml_text)

    def test_neutrale_transformation_ohne_gruppe(self):
        document = convert('<g transform="translate(0 0) scale(1)"><rect width="1" height="1"/></g>')
        self.assertIsInstance(document.children[0], VectorPath)

    def test_gruppe_ohne_attribute_wird_sauber_geschrieben(self):
        from svg2vector import VectorDocument, VectorXmlWriter
        document = VectorDocument(1, 1, 1, 1, (VectorGroup(GroupTransform(), (VectorPath("M0,0 L1,1", "#000000"),)),))
        xml_text = VectorXmlWriter().write(document)
        self.assertIn("    <group>\n", xml_text)
        ET.fromstring(xml_text)

    def test_ausgabe_ist_deterministisch(self):
        body = '<rect width="10" height="10" rx="2" fill="#123456"/>'
        self.assertEqual(convert_svg(svg(body)), convert_svg(svg(body)))


class ComposeTest(unittest.TestCase):
    def test_ebenen_werden_uebereinandergelegt(self):
        composite = compose_svg([svg('<title>unten</title><rect width="9" height="9" fill="#000"/>', "0 0 9 9"),
                                 svg('<circle cx="4" cy="4" r="2" fill="#fff"/>', "0 0 9 9")], "Titel", "Kommentar")
        root = ET.fromstring(composite)
        self.assertEqual(root.get("viewBox"), "0 0 9 9")
        self.assertEqual(len(root.findall("{http://www.w3.org/2000/svg}g")), 2)
        self.assertNotIn("unten", composite)
        self.assertNotIn("ns0:", composite)
        self.assertEqual(len(SvgToVectorConverter().convert(composite).children), 2)

    def test_unterschiedliche_viewbox_ist_ein_fehler(self):
        with self.assertRaises(ConversionError):
            compose_svg([svg('<rect width="1" height="1"/>', "0 0 9 9"), svg('<rect width="1" height="1"/>')], "T", "K")


class ProjectTest(unittest.TestCase):
    """sync/check auf einem kleinen Beispielprojekt in einem temporären Ordner."""

    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        base = Path(self._tmp.name)
        self.icons = base / "icons"
        self.res = base / "res"
        self.icons.mkdir()
        (self.res / "mipmap-anydpi-v26").mkdir(parents=True)
        layer = '<rect width="108" height="108" fill="#12707F"/>'
        (self.icons / "a.svg").write_text(svg('<circle cx="24" cy="24" r="10" fill="#4FB4E8"/>'))
        (self.icons / "fg.svg").write_text(svg('<circle cx="54" cy="54" r="20" fill="#fff"/>', "0 0 108 108"))
        (self.icons / "bg.svg").write_text(svg(layer, "0 0 108 108"))
        for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
            (self.res / "mipmap-anydpi-v26" / name).write_text(
                '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">'
                '<background android:drawable="@drawable/ic_launcher_background"/>'
                '<foreground android:drawable="@drawable/ic_launcher_foreground"/></adaptive-icon>')
        self.project = Project(icons_dir=self.icons, res_dir=self.res, keys=("a",), app_icon_layers=("bg.svg", "fg.svg"), specs=[
            OutputSpec("a.svg", "drawable/drink_a.xml", 48, 48),
            OutputSpec("fg.svg", "drawable/ic_launcher_foreground.xml", 108, 108),
            OutputSpec("bg.svg", "drawable/ic_launcher_background.xml", 108, 108),
        ])

    def tearDown(self):
        self._tmp.cleanup()

    def run_main(self, *argv: str) -> tuple[int, str]:
        out = io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(out):
            code = main(list(argv), project_factory=lambda: self.project)
        return code, out.getvalue()

    def test_sync_dann_check(self):
        code, output = self.run_main("sync")
        self.assertEqual(code, 0)
        self.assertIn("neu", output)
        self.assertTrue((self.res / "drawable" / "drink_a.xml").exists())
        self.assertTrue((self.icons / "app_icon.svg").exists())
        self.assertIn("gleich", self.run_main("sync")[1])
        code, output = self.run_main("check")
        self.assertEqual(code, 0, output)

    def test_veraltete_datei_wird_erkannt(self):
        self.run_main("sync")
        (self.icons / "a.svg").write_text(svg('<circle cx="24" cy="24" r="12" fill="#4FB4E8"/>'))
        code, output = self.run_main("check")
        self.assertEqual(code, 1)
        self.assertIn("veraltet", output)

    def test_fremde_datei_wird_nicht_ueberschrieben(self):
        target = self.res / "drawable" / "drink_a.xml"
        target.parent.mkdir(parents=True)
        target.write_text("<vector/>")
        code, output = self.run_main("sync")
        self.assertIn("übersprungen", output)
        self.assertEqual(target.read_text(), "<vector/>")
        code, output = self.run_main("check")
        self.assertEqual(code, 0, output)
        self.assertIn("Warnung", output)

    def test_umwandlungsfehler_ergibt_exit_code_2(self):
        (self.icons / "a.svg").write_text(svg('<text>Hallo</text>'))
        code, output = self.run_main("sync")
        self.assertEqual(code, 2)
        self.assertIn("a.svg", output)

    def test_convert_einzeldatei(self):
        target = Path(self._tmp.name) / "out.xml"
        code, _ = self.run_main("convert", str(self.icons / "a.svg"), str(target), "--width-dp", "24")
        self.assertEqual(code, 0)
        self.assertIn('android:width="24dp"', target.read_text())


if __name__ == "__main__":
    unittest.main()
