# SPDX-License-Identifier: CC0-1.0
"""Tests über die echten Icons im Repository: vollständig, einheitlicher Stil, Android-Dateien aktuell."""
import unittest
import xml.etree.ElementTree as ET

from contact_sheet import ContactSheet, vector_to_svg
from svg2vector import APP_ICON_COMPOSITE, DRINK_ICON_KEYS, GENERATED_MARKER, Project, SvgToVectorConverter
from vector_checks import VectorDrawableValidator, check_project, kotlin_icon_keys

SVG = "{http://www.w3.org/2000/svg}"
OUTLINE = "#263238"


class RepositoryIconsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.project = Project.from_repository()

    def drink_svg(self, key: str) -> str:
        return (self.project.icons_dir / f"{key}.svg").read_text(encoding="utf-8")

    def test_ein_svg_je_drinkicon_schluessel(self):
        missing = [key for key in DRINK_ICON_KEYS if not (self.project.icons_dir / f"{key}.svg").exists()]
        self.assertEqual(missing, [])
        self.assertEqual(len(DRINK_ICON_KEYS), 22)
        self.assertEqual(len(set(DRINK_ICON_KEYS)), 22)

    def test_schluessel_wie_im_kotlin_kern(self):
        keys = kotlin_icon_keys(self.project.kotlin_icon_file)
        if keys is None:
            self.skipTest("DrinkIcon.kt noch nicht vorhanden")
        self.assertEqual(keys, list(DRINK_ICON_KEYS))

    def test_einheitlicher_stil(self):
        for key in DRINK_ICON_KEYS:
            with self.subTest(key=key):
                text = self.drink_svg(key)
                root = ET.fromstring(text)
                self.assertEqual(root.get("viewBox"), "0 0 48 48")
                self.assertTrue((root.findtext(f"{SVG}title") or "").strip(), "Titel (Barrierefreiheit im Web) fehlt")
                self.assertIn("CC0", text)
                self.assertIn(OUTLINE, text, "gemeinsame Umrissfarbe fehlt")
                self.assertNotIn("<text", text)
                SvgToVectorConverter().convert(text)  # nur unterstützte SVG-Teilmenge

    def test_keine_doppelten_icons(self):
        contents = {self.drink_svg(key).split("</title>", 1)[1] for key in DRINK_ICON_KEYS}
        self.assertEqual(len(contents), len(DRINK_ICON_KEYS))

    def test_android_dateien_aktuell_und_gueltig(self):
        errors, warnings = check_project(self.project, DRINK_ICON_KEYS, self.project.kotlin_icon_file)
        self.assertEqual(errors, [])
        unexpected = [w for w in warnings if "ic_notification" not in w and "notification.svg" not in w]
        self.assertEqual(unexpected, [])

    def test_jede_erzeugte_datei_ist_gueltig_und_gekennzeichnet(self):
        validator = VectorDrawableValidator()
        for spec in self.project.specs:
            target = self.project.res_dir / spec.target
            text = target.read_text(encoding="utf-8")
            if spec.source == "notification.svg" and not self.project.is_generated(text):
                continue  # noch die alte, nicht erzeugte Datei des App-Moduls
            with self.subTest(target=spec.target):
                self.assertIn(GENERATED_MARKER, text)
                self.assertEqual(validator.validate(text).errors, [])

    def test_benachrichtigungssymbol_ist_einfarbig_weiss(self):
        xml_text = self.project.render(next(s for s in self.project.specs if s.source == "notification.svg"))
        colors = {value for element in ET.fromstring(xml_text).iter("path")
                  for name, value in element.attrib.items() if name.endswith("Color")}
        self.assertEqual(colors, {"#FFFFFF"})

    def test_zusammengesetztes_app_symbol(self):
        text = (self.project.icons_dir / APP_ICON_COMPOSITE).read_text(encoding="utf-8")
        self.assertEqual(text, self.project.render_app_icon())
        self.assertEqual(ET.fromstring(text).get("viewBox"), "0 0 108 108")

    def test_kontaktbogen(self):
        html = ContactSheet(self.project).build()
        for key in DRINK_ICON_KEYS:
            self.assertIn(f"<span>{key}</span>", html)
        self.assertIn('id="appicon"', html)
        self.assertIn('id="banner"', html)

    def test_rueckuebersetzung_behaelt_alle_pfade(self):
        for spec in self.project.specs[:len(DRINK_ICON_KEYS)]:
            with self.subTest(target=spec.target):
                xml_text = (self.project.res_dir / spec.target).read_text(encoding="utf-8")
                svg_root = ET.fromstring(vector_to_svg(xml_text))
                self.assertEqual(len(list(svg_root.iter(f"{SVG}path"))), len(list(ET.fromstring(xml_text).iter("path"))))


if __name__ == "__main__":
    unittest.main()
