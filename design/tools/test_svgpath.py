# SPDX-License-Identifier: CC0-1.0
"""Tests für das Einlesen, Ausgeben und Abtasten von Pfaddaten."""
import math
import unittest

from svgpath import (PathCommand, PathDataError, arc_points, format_number, normalize_path_data, parse_path_data,
                     sample_points)


class ParsePathDataTest(unittest.TestCase):
    def test_einfacher_pfad(self):
        self.assertEqual(parse_path_data("M1 2 L3 4 Z"),
                         [PathCommand("M", (1.0, 2.0)), PathCommand("L", (3.0, 4.0)), PathCommand("Z", ())])

    def test_kompakte_bogen_flags_ohne_trenner(self):
        commands = parse_path_data("M0 0 a1 1 0 011 1")
        self.assertEqual(commands[1], PathCommand("a", (1.0, 1.0, 0.0, 0.0, 1.0, 1.0, 1.0)))

    def test_negative_radien_werden_betragsmaessig_verwendet(self):
        self.assertEqual(parse_path_data("M0 0 A-2 -3 0 0 1 4 4")[1].args[:2], (2.0, 3.0))

    def test_implizites_lineto_nach_moveto(self):
        self.assertEqual([c.letter for c in parse_path_data("M1 2 3 4 5 6")], ["M", "L", "L"])
        self.assertEqual([c.letter for c in parse_path_data("m1 2 3 4")], ["m", "l"])

    def test_wiederholte_argumente(self):
        commands = parse_path_data("M0 0 C1 1 2 2 3 3 4 4 5 5 6 6")
        self.assertEqual([c.letter for c in commands], ["M", "C", "C"])

    def test_zahlenformate(self):
        commands = parse_path_data("M.5.5L-1e1,2h+3")
        self.assertEqual(commands, [PathCommand("M", (0.5, 0.5)), PathCommand("L", (-10.0, 2.0)),
                                    PathCommand("h", (3.0,))])

    def test_fehler(self):
        for bad in ("", "L1 2", "M1", "M1 2 X3", "M0 0 A1 1 0 2 1 3 3", "M0 0 Z 1 2", "M1,,"):
            with self.subTest(bad=bad), self.assertRaises(PathDataError):
                parse_path_data(bad)


class FormatTest(unittest.TestCase):
    def test_format_number(self):
        self.assertEqual(format_number(1.0), "1")
        self.assertEqual(format_number(0.12345), "0.123")
        self.assertEqual(format_number(-0.0001), "0")
        self.assertEqual(format_number(12.5), "12.5")
        self.assertEqual(format_number(-3.25), "-3.25")

    def test_normalisierung_ist_stabil(self):
        d = "M12 9H36L33.22 39.6Q33 42 30.6 42H17.4a2 2 0 011-1z"
        once = normalize_path_data(d)
        self.assertEqual(once, "M12,9 H36 L33.22,39.6 Q33,42,30.6,42 H17.4 a2,2,0,0,1,1,-1 z")
        self.assertEqual(normalize_path_data(once), once)


class SampleTest(unittest.TestCase):
    def test_kreis_aus_zwei_boegen(self):
        commands = parse_path_data("M14 24 A10 10 0 1 1 34 24 A10 10 0 1 1 14 24 Z")
        for x, y in list(sample_points(commands))[1:-1]:
            self.assertAlmostEqual(math.hypot(x - 24, y - 24), 10, places=6)

    def test_bogen_richtung(self):
        # sweep=0 von links nach rechts verläuft in SVG-Koordinaten (y nach unten) über den unteren Rand
        points = arc_points((13, 17), 11, 11, 0, False, False, (35, 17), 8)
        self.assertGreater(max(y for _, y in points), 27.9)

    def test_relative_befehle_und_schliessen(self):
        points = list(sample_points(parse_path_data("M0 0 h10 v10 H0 z")))
        self.assertEqual(points, [(0, 0), (10, 0), (10, 10), (0, 10), (0, 0)])

    def test_glatte_kurve_spiegelt_kontrollpunkt(self):
        points = list(sample_points(parse_path_data("M0 0 C0 10 10 10 10 0 S20 -10 20 0"), steps=4))
        self.assertEqual(points[-1], (20, 0))
        self.assertLess(points[-3][1], 0)  # zweites Segment wölbt sich nach oben

    def test_quadratisch_und_t(self):
        points = list(sample_points(parse_path_data("M0 0 Q5 10 10 0 T20 0"), steps=2))
        self.assertEqual(points[1], (5.0, 5.0))
        self.assertEqual(points[3], (15.0, -5.0))


if __name__ == "__main__":
    unittest.main()
