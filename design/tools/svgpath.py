# SPDX-License-Identifier: CC0-1.0
"""Pfaddaten für SVG (Attribut ``d``) und Android-VectorDrawable (``android:pathData``).

Beide Formate verwenden dieselbe Pfad-Grammatik. Dieses Modul

* liest Pfaddaten streng ein (auch kompakte Schreibweisen wie ``a1 1 0 011 1`` oder ``1.5.5``),
* gibt sie in einer einheitlichen, für Android sicher lesbaren Form wieder aus
  (jeder Befehl explizit, Zahlen durch Kommas getrennt, höchstens drei Nachkommastellen) und
* tastet Umrisse als Punktfolge ab (für Prüfungen wie die Schutzzone des App-Symbols).

Nur Python-Standardbibliothek.
"""
from __future__ import annotations

import math
import re
from dataclasses import dataclass
from typing import Iterator, Sequence

#: Anzahl der Zahlen pro Befehl (Großbuchstabe = absolut, Kleinbuchstabe = relativ).
ARG_COUNTS: dict[str, int] = {"M": 2, "L": 2, "H": 1, "V": 1, "C": 6, "S": 4, "Q": 4, "T": 2, "A": 7, "Z": 0}

_NUMBER_RE = re.compile(r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")
_SEPARATORS = " \t\r\n,"

Point = tuple[float, float]


class PathDataError(ValueError):
    """Die Pfaddaten sind syntaktisch fehlerhaft."""


@dataclass(frozen=True)
class PathCommand:
    """Ein einzelner Pfadbefehl, z. B. ``L`` mit den Argumenten ``(10.0, 20.0)``."""

    letter: str
    args: tuple[float, ...]

    @property
    def is_relative(self) -> bool:
        return self.letter.islower()


class _Scanner:
    """Liest Zahlen, Bögen-Flags und Befehlsbuchstaben aus einer Pfadzeichenkette."""

    def __init__(self, text: str) -> None:
        self._text = text
        self._pos = 0

    def skip_separators(self) -> None:
        while self._pos < len(self._text) and self._text[self._pos] in _SEPARATORS:
            self._pos += 1

    def at_end(self) -> bool:
        self.skip_separators()
        return self._pos >= len(self._text)

    def peek(self) -> str:
        self.skip_separators()
        return self._text[self._pos] if self._pos < len(self._text) else ""

    def read_letter(self) -> str:
        letter = self.peek()
        self._pos += 1
        return letter

    def read_number(self) -> float:
        self.skip_separators()
        match = _NUMBER_RE.match(self._text, self._pos)
        if not match:
            raise PathDataError(f"Zahl erwartet an Position {self._pos}: {self._context()!r}")
        self._pos = match.end()
        return float(match.group())

    def read_flag(self) -> float:
        self.skip_separators()
        char = self._text[self._pos] if self._pos < len(self._text) else ""
        if char not in ("0", "1"):
            raise PathDataError(f"Bogen-Flag 0 oder 1 erwartet an Position {self._pos}: {self._context()!r}")
        self._pos += 1
        return float(char)

    def _context(self) -> str:
        return self._text[max(0, self._pos - 10): self._pos + 10]


def _read_args(scanner: _Scanner, letter: str) -> tuple[float, ...]:
    upper = letter.upper()
    if upper == "A":
        rx, ry, rotation = (scanner.read_number() for _ in range(3))
        large_arc, sweep = scanner.read_flag(), scanner.read_flag()
        x, y = scanner.read_number(), scanner.read_number()
        return (abs(rx), abs(ry), rotation, large_arc, sweep, x, y)
    return tuple(scanner.read_number() for _ in range(ARG_COUNTS[upper]))


def _implicit_follow_up(letter: str) -> str | None:
    """Befehl, der bei weiteren Zahlen ohne neuen Buchstaben gilt (nach M/m implizit L/l)."""
    if letter in "Zz":
        return None
    if letter == "M":
        return "L"
    if letter == "m":
        return "l"
    return letter


def parse_path_data(text: str) -> list[PathCommand]:
    """Liest Pfaddaten ein; wirft :class:`PathDataError` bei jedem Syntaxfehler."""
    scanner = _Scanner(text)
    commands: list[PathCommand] = []
    current: str | None = None
    while not scanner.at_end():
        char = scanner.peek()
        if char.isalpha():
            letter = scanner.read_letter()
            if letter.upper() not in ARG_COUNTS:
                raise PathDataError(f"Unbekannter Pfadbefehl {letter!r}")
            if not commands and letter not in "Mm":
                raise PathDataError("Pfaddaten müssen mit M oder m beginnen")
            if letter in "Zz":
                commands.append(PathCommand(letter, ()))
                current = None
                continue
            commands.append(PathCommand(letter, _read_args(scanner, letter)))
            current = _implicit_follow_up(letter)
        else:
            if current is None:
                raise PathDataError(f"Pfadbefehl erwartet statt {char!r}")
            commands.append(PathCommand(current, _read_args(scanner, current)))
    if not commands:
        raise PathDataError("Leere Pfaddaten")
    return commands


def format_number(value: float) -> str:
    """Kurze Dezimaldarstellung mit höchstens drei Nachkommastellen (``-0`` wird zu ``0``)."""
    rounded = round(value, 3)
    if rounded == 0:
        return "0"
    return f"{rounded:.3f}".rstrip("0").rstrip(".")


def format_path_data(commands: Sequence[PathCommand]) -> str:
    """Gibt Befehle einheitlich aus, z. B. ``M12,9 L36,9 Z``."""
    return " ".join(cmd.letter + ",".join(format_number(arg) for arg in cmd.args) for cmd in commands)


def normalize_path_data(text: str) -> str:
    """Liest Pfaddaten ein und gibt sie in einheitlicher Form zurück."""
    return format_path_data(parse_path_data(text))


# --- Abtasten -------------------------------------------------------------------------------------


def _lerp_cubic(p0: Point, p1: Point, p2: Point, p3: Point, t: float) -> Point:
    u = 1 - t
    return (
        u**3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t**3 * p3[0],
        u**3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t**3 * p3[1],
    )


def _lerp_quad(p0: Point, p1: Point, p2: Point, t: float) -> Point:
    u = 1 - t
    return (
        u * u * p0[0] + 2 * u * t * p1[0] + t * t * p2[0],
        u * u * p0[1] + 2 * u * t * p1[1] + t * t * p2[1],
    )


def _vector_angle(ux: float, uy: float, vx: float, vy: float) -> float:
    return math.atan2(ux * vy - uy * vx, ux * vx + uy * vy)


def arc_points(start: Point, rx: float, ry: float, rotation_deg: float, large_arc: bool, sweep: bool,
               end: Point, steps: int) -> list[Point]:
    """Punkte auf einem elliptischen Bogen (SVG-Endpunkt-Parametrisierung, SVG 1.1 Anhang F.6)."""
    (x1, y1), (x2, y2) = start, end
    if rx == 0 or ry == 0 or start == end:
        return [end]
    phi = math.radians(rotation_deg)
    cos_phi, sin_phi = math.cos(phi), math.sin(phi)
    dx, dy = (x1 - x2) / 2, (y1 - y2) / 2
    x1p = cos_phi * dx + sin_phi * dy
    y1p = -sin_phi * dx + cos_phi * dy
    rx, ry = abs(rx), abs(ry)
    radii_check = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
    if radii_check > 1:
        scale = math.sqrt(radii_check)
        rx, ry = rx * scale, ry * scale
    numerator = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
    denominator = rx * rx * y1p * y1p + ry * ry * x1p * x1p
    coefficient = math.sqrt(max(0.0, numerator / denominator)) if denominator else 0.0
    if large_arc == sweep:
        coefficient = -coefficient
    cxp = coefficient * rx * y1p / ry
    cyp = -coefficient * ry * x1p / rx
    cx = cos_phi * cxp - sin_phi * cyp + (x1 + x2) / 2
    cy = sin_phi * cxp + cos_phi * cyp + (y1 + y2) / 2
    ux, uy = (x1p - cxp) / rx, (y1p - cyp) / ry
    vx, vy = (-x1p - cxp) / rx, (-y1p - cyp) / ry
    theta = _vector_angle(1, 0, ux, uy)
    delta = _vector_angle(ux, uy, vx, vy)
    if not sweep and delta > 0:
        delta -= 2 * math.pi
    elif sweep and delta < 0:
        delta += 2 * math.pi
    points = []
    for i in range(1, steps + 1):
        angle = theta + delta * i / steps
        ex, ey = rx * math.cos(angle), ry * math.sin(angle)
        points.append((cx + cos_phi * ex - sin_phi * ey, cy + sin_phi * ex + cos_phi * ey))
    return points


def sample_points(commands: Sequence[PathCommand], steps: int = 16) -> Iterator[Point]:
    """Liefert absolute Punkte entlang des Pfads (Endpunkte und Zwischenpunkte von Kurven)."""
    x = y = 0.0
    start_x = start_y = 0.0
    last_control: Point | None = None
    last_upper = ""
    for cmd in commands:
        upper = cmd.letter.upper()
        rel = cmd.is_relative
        a = cmd.args
        ox, oy = (x, y) if rel else (0.0, 0.0)
        control: Point | None = None
        if upper == "M":
            x, y = ox + a[0], oy + a[1]
            start_x, start_y = x, y
            yield (x, y)
        elif upper == "L":
            x, y = ox + a[0], oy + a[1]
            yield (x, y)
        elif upper == "H":
            x = (x if rel else 0.0) + a[0]
            yield (x, y)
        elif upper == "V":
            y = (y if rel else 0.0) + a[0]
            yield (x, y)
        elif upper in ("C", "S"):
            if upper == "C":
                p1 = (ox + a[0], oy + a[1])
                p2, p3 = (ox + a[2], oy + a[3]), (ox + a[4], oy + a[5])
            else:
                p1 = (2 * x - last_control[0], 2 * y - last_control[1]) if last_upper in ("C", "S") and last_control else (x, y)
                p2, p3 = (ox + a[0], oy + a[1]), (ox + a[2], oy + a[3])
            p0 = (x, y)
            for i in range(1, steps + 1):
                yield _lerp_cubic(p0, p1, p2, p3, i / steps)
            control = p2
            x, y = p3
        elif upper in ("Q", "T"):
            if upper == "Q":
                p1, p2 = (ox + a[0], oy + a[1]), (ox + a[2], oy + a[3])
            else:
                p1 = (2 * x - last_control[0], 2 * y - last_control[1]) if last_upper in ("Q", "T") and last_control else (x, y)
                p2 = (ox + a[0], oy + a[1])
            p0 = (x, y)
            for i in range(1, steps + 1):
                yield _lerp_quad(p0, p1, p2, i / steps)
            control = p1
            x, y = p2
        elif upper == "A":
            end = (ox + a[5], oy + a[6])
            yield from arc_points((x, y), a[0], a[1], a[2], bool(a[3]), bool(a[4]), end, steps)
            x, y = end
        elif upper == "Z":
            x, y = start_x, start_y
            yield (x, y)
        last_control = control
        last_upper = upper
