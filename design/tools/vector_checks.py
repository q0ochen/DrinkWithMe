# SPDX-License-Identifier: CC0-1.0
"""Prüfungen für die erzeugten Android-Grafiken.

* :class:`VectorDrawableValidator` – nur Elemente/Attribute, die Android-VectorDrawable kennt, mit gültigen Werten.
* :func:`outline_points` – Umrisspunkte eines VectorDrawables inkl. Gruppen-Transformationen (für Bereichsprüfungen).
* :func:`check_project` – alles zusammen für ``svg2vector.py check`` (CI-tauglich).

Nur Python-Standardbibliothek.
"""
from __future__ import annotations

import math
import re
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path
from typing import Protocol, Sequence

from svgpath import PathDataError, parse_path_data, sample_points

ANDROID_NS = "http://schemas.android.com/apk/res/android"
_A = "{" + ANDROID_NS + "}"

_ALLOWED_ATTRIBUTES: dict[str, set[str]] = {
    "vector": {"width", "height", "viewportWidth", "viewportHeight", "alpha", "tint", "tintMode", "autoMirrored",
               "name"},
    "group": {"name", "rotation", "pivotX", "pivotY", "scaleX", "scaleY", "translateX", "translateY"},
    "path": {"name", "pathData", "fillColor", "strokeColor", "strokeWidth", "strokeAlpha", "fillAlpha",
             "strokeLineCap", "strokeLineJoin", "strokeMiterLimit", "fillType", "trimPathStart", "trimPathEnd",
             "trimPathOffset"},
    "clip-path": {"name", "pathData"},
}
_REQUIRED_ATTRIBUTES = {
    "vector": {"width", "height", "viewportWidth", "viewportHeight"},
    "path": {"pathData"},
    "clip-path": {"pathData"},
}
_ALLOWED_CHILDREN = {"vector": {"group", "path", "clip-path"}, "group": {"group", "path", "clip-path"},
                     "path": set(), "clip-path": set()}

_COLOR_RE = re.compile(r"#(?:[0-9A-Fa-f]{3,4}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})|@(?:android:)?color/[A-Za-z0-9_]+")
_DIMENSION_RE = re.compile(r"\d+(?:\.\d+)?dp")
_FLOAT_RE = re.compile(r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")
_ENUMS = {
    "strokeLineCap": {"butt", "round", "square"},
    "strokeLineJoin": {"miter", "round", "bevel"},
    "fillType": {"nonZero", "evenOdd"},
    "tintMode": {"src_over", "src_in", "src_atop", "multiply", "screen", "add"},
    "autoMirrored": {"true", "false"},
}
_COLOR_ATTRIBUTES = {"fillColor", "strokeColor", "tint"}
_DIMENSION_ATTRIBUTES = {"width", "height"}
_UNIT_INTERVAL_ATTRIBUTES = {"alpha", "fillAlpha", "strokeAlpha", "trimPathStart", "trimPathEnd", "trimPathOffset"}
_POSITIVE_ATTRIBUTES = {"viewportWidth", "viewportHeight"}

#: Android-Lint („VectorPath“) warnt ab dieser Länge von pathData.
LINT_MAX_PATH_LENGTH = 800


@dataclass
class ValidationReport:
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)

    @property
    def ok(self) -> bool:
        return not self.errors


class VectorDrawableValidator:
    """Prüft VectorDrawable-XML gegen die von Android unterstützten Elemente und Attribute."""

    def validate(self, xml_text: str) -> ValidationReport:
        report = ValidationReport()
        try:
            root = ET.fromstring(xml_text)
        except ET.ParseError as error:
            report.errors.append(f"kein wohlgeformtes XML: {error}")
            return report
        if root.tag != "vector":
            report.errors.append(f"Wurzelelement muss <vector> sein, nicht <{root.tag}>")
            return report
        if f'xmlns:android="{ANDROID_NS}"' not in xml_text:
            report.errors.append("xmlns:android fehlt oder ist falsch")
        self._element(root, report)
        return report

    def _element(self, element: ET.Element, report: ValidationReport) -> None:
        tag = element.tag
        allowed = _ALLOWED_ATTRIBUTES.get(tag)
        if allowed is None:
            report.errors.append(f"<{tag}> ist in VectorDrawable nicht erlaubt")
            return
        names = set()
        for qualified, value in element.attrib.items():
            if not qualified.startswith(_A):
                report.errors.append(f"<{tag}>: Attribut {qualified!r} ohne android:-Namensraum")
                continue
            name = qualified[len(_A):]
            names.add(name)
            if name not in allowed:
                report.errors.append(f"<{tag}>: android:{name} wird nicht unterstützt")
                continue
            problem = self._value_problem(name, value)
            if problem:
                report.errors.append(f"<{tag}> android:{name}={value!r}: {problem}")
            if name == "pathData" and len(value) > LINT_MAX_PATH_LENGTH:
                report.warnings.append(f"<{tag}>: pathData mit {len(value)} Zeichen (Lint „VectorPath“ ab "
                                       f"{LINT_MAX_PATH_LENGTH})")
        missing = _REQUIRED_ATTRIBUTES.get(tag, set()) - names
        if missing:
            report.errors.append(f"<{tag}>: Pflichtattribut(e) fehlen: {', '.join(sorted(missing))}")
        if tag == "path" and not ({"fillColor", "strokeColor"} & names):
            report.warnings.append("<path> ohne fillColor und strokeColor ist unsichtbar")
        for child in element:
            if child.tag not in _ALLOWED_CHILDREN[tag]:
                report.errors.append(f"<{child.tag}> ist in <{tag}> nicht erlaubt")
                continue
            self._element(child, report)

    @staticmethod
    def _value_problem(name: str, value: str) -> str | None:
        if name == "pathData":
            try:
                parse_path_data(value)
            except PathDataError as error:
                return str(error)
            return None
        if name in _COLOR_ATTRIBUTES:
            return None if _COLOR_RE.fullmatch(value) else "keine gültige Farbe"
        if name in _DIMENSION_ATTRIBUTES:
            return None if _DIMENSION_RE.fullmatch(value) else "Größe in dp erwartet, z. B. 48dp"
        if name in _ENUMS:
            return None if value in _ENUMS[name] else f"erlaubt: {', '.join(sorted(_ENUMS[name]))}"
        if name == "name":
            return None
        if not _FLOAT_RE.fullmatch(value):
            return "Zahl erwartet"
        number = float(value)
        if name in _UNIT_INTERVAL_ATTRIBUTES and not 0 <= number <= 1:
            return "Wert zwischen 0 und 1 erwartet"
        if name in _POSITIVE_ATTRIBUTES and number <= 0:
            return "positiver Wert erwartet"
        if name == "strokeWidth" and number < 0:
            return "darf nicht negativ sein"
        return None


# --- Geometrie --------------------------------------------------------------------------------------

Matrix = tuple[float, float, float, float, float, float]  # x' = a·x + c·y + e, y' = b·x + d·y + f
IDENTITY: Matrix = (1.0, 0.0, 0.0, 1.0, 0.0, 0.0)


def multiply(m: Matrix, n: Matrix) -> Matrix:
    """``m · n`` (erst ``n``, dann ``m`` anwenden)."""
    a, b, c, d, e, f = m
    a2, b2, c2, d2, e2, f2 = n
    return (a * a2 + c * b2, b * a2 + d * b2, a * c2 + c * d2, b * c2 + d * d2,
            a * e2 + c * f2 + e, b * e2 + d * f2 + f)


def group_matrix(element: ET.Element) -> Matrix:
    """Matrix einer ``<group>`` in Android-Reihenfolge: T(translate + pivot) · R · S · T(−pivot)."""
    def number(name: str, default: float) -> float:
        return float(element.get(_A + name, default))

    px, py = number("pivotX", 0.0), number("pivotY", 0.0)
    angle = math.radians(number("rotation", 0.0))
    cos_a, sin_a = math.cos(angle), math.sin(angle)
    m = (1.0, 0.0, 0.0, 1.0, -px, -py)
    m = multiply((number("scaleX", 1.0), 0.0, 0.0, number("scaleY", 1.0), 0.0, 0.0), m)
    m = multiply((cos_a, sin_a, -sin_a, cos_a, 0.0, 0.0), m)
    return multiply((1.0, 0.0, 0.0, 1.0, number("translateX", 0.0) + px, number("translateY", 0.0) + py), m)


@dataclass(frozen=True)
class OutlinePoint:
    x: float
    y: float
    reach: float  # halbe sichtbare Strichbreite um den Punkt (0 bei reinen Flächen)


def outline_points(xml_text: str, steps: int = 16) -> list[OutlinePoint]:
    """Alle Umrisspunkte der sichtbaren Pfade in Viewport-Koordinaten."""
    root = ET.fromstring(xml_text)
    points: list[OutlinePoint] = []
    _collect(root, IDENTITY, points, steps)
    return points


def _collect(element: ET.Element, matrix: Matrix, points: list[OutlinePoint], steps: int) -> None:
    for child in element:
        if child.tag == "group":
            _collect(child, multiply(matrix, group_matrix(child)), points, steps)
        elif child.tag == "path":
            stroked = child.get(_A + "strokeColor") is not None
            scale = math.sqrt(abs(matrix[0] * matrix[3] - matrix[1] * matrix[2]))
            reach = float(child.get(_A + "strokeWidth", 0)) / 2 * scale if stroked else 0.0
            for x, y in sample_points(parse_path_data(child.get(_A + "pathData", "")), steps):
                a, b, c, d, e, f = matrix
                points.append(OutlinePoint(a * x + c * y + e, b * x + d * y + f, reach))


@dataclass(frozen=True)
class Bounds:
    left: float
    top: float
    right: float
    bottom: float


def bounds(points: Sequence[OutlinePoint]) -> Bounds:
    return Bounds(min(p.x - p.reach for p in points), min(p.y - p.reach for p in points),
                  max(p.x + p.reach for p in points), max(p.y + p.reach for p in points))


def max_distance(points: Sequence[OutlinePoint], cx: float, cy: float) -> float:
    """Größter Abstand eines (gezeichneten) Punktes vom Mittelpunkt."""
    return max(math.hypot(p.x - cx, p.y - cy) + p.reach for p in points)


# --- Projektprüfung ---------------------------------------------------------------------------------


class _Spec(Protocol):
    source: str
    target: str


class ProjectLike(Protocol):
    icons_dir: Path
    res_dir: Path
    specs: Sequence[_Spec]

    def render(self, spec: _Spec) -> str:
        ...

    def render_app_icon(self) -> str:
        ...

    def is_generated(self, text: str) -> bool:
        ...


APP_ICON_COMPOSITE = "app_icon.svg"


#: Mindestabstand der Getränke-Icons zum Rand der 48er-viewBox (inklusive halber Strichbreite).
ICON_MARGIN = 1.0

#: Vordergrund des adaptiven Symbols: Inhalt innerhalb des Kreises mit 66 dp Durchmesser (Mitte 54/54).
LAUNCHER_SAFE_RADIUS = 33.0
LAUNCHER_CENTER = 54.0


def kotlin_icon_keys(kotlin_file: Path) -> list[str] | None:
    """Liest die Schlüssel aus ``enum class DrinkIcon`` (``None``, wenn die Datei fehlt)."""
    if not kotlin_file.exists():
        return None
    text = kotlin_file.read_text(encoding="utf-8")
    match = re.search(r"enum class DrinkIcon[^{]*\{(.*?);", text, re.S)
    body = match.group(1) if match else ""
    return re.findall(r"[A-Z_]+\(\s*\"([a-z_]+)\"\s*\)", body)


def _check_adaptive_icon(path: Path, res_dir: Path, errors: list[str]) -> None:
    if not path.exists():
        errors.append(f"{path.name} fehlt in {path.parent.name}")
        return
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as error:
        errors.append(f"{path.name}: kein wohlgeformtes XML: {error}")
        return
    if root.tag != "adaptive-icon":
        errors.append(f"{path.name}: Wurzelelement muss <adaptive-icon> sein")
        return
    layers = {child.tag: child.get(_A + "drawable", "") for child in root}
    for layer in ("background", "foreground"):
        reference = layers.get(layer)
        if not reference or not reference.startswith("@drawable/"):
            errors.append(f"{path.name}: <{layer} android:drawable=\"@drawable/…\"> fehlt")
            continue
        if not (res_dir / "drawable" / f"{reference.split('/', 1)[1]}.xml").exists():
            errors.append(f"{path.name}: {reference} existiert nicht")


def check_project(project: ProjectLike, expected_keys: Sequence[str],
                  kotlin_file: Path | None = None) -> tuple[list[str], list[str]]:
    """Prüft Aktualität, Gültigkeit und Maße aller erzeugten Grafiken. Liefert (Fehler, Warnungen)."""
    errors: list[str] = []
    warnings: list[str] = []
    validator = VectorDrawableValidator()
    for spec in project.specs:
        source = project.icons_dir / spec.source
        target = project.res_dir / spec.target
        if not source.exists():
            errors.append(f"Quelle fehlt: design/icons/{spec.source}")
            continue
        try:
            expected = project.render(spec)
        except ValueError as error:
            errors.append(str(error))
            continue
        if not target.exists():
            errors.append(f"res/{spec.target} fehlt")
            continue
        actual = target.read_text(encoding="utf-8")
        if not project.is_generated(actual):
            warnings.append(f"res/{spec.target} ist nicht aus design/icons/{spec.source} erzeugt – zum Übernehmen "
                            "Datei löschen und „sync“ ausführen")
            continue
        if actual != expected:
            errors.append(f"res/{spec.target} ist veraltet (nicht aus design/icons/{spec.source} erzeugt)")
        report = validator.validate(actual)
        errors += [f"res/{spec.target}: {message}" for message in report.errors]
        warnings += [f"res/{spec.target}: {message}" for message in report.warnings]
        if report.ok:
            errors += _geometry_problems(spec, actual)
    composite = project.icons_dir / APP_ICON_COMPOSITE
    if not composite.exists() or composite.read_text(encoding="utf-8") != project.render_app_icon():
        errors.append(f"design/icons/{APP_ICON_COMPOSITE} fehlt oder ist veraltet")
    known_sources = {spec.source for spec in project.specs} | {APP_ICON_COMPOSITE}
    for svg in sorted(project.icons_dir.glob("*.svg")):
        if svg.name not in known_sources:
            warnings.append(f"design/icons/{svg.name} wird nicht nach Android übernommen")
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        _check_adaptive_icon(project.res_dir / "mipmap-anydpi-v26" / name, project.res_dir, errors)
    if kotlin_file is not None:
        kotlin_keys = kotlin_icon_keys(kotlin_file)
        if kotlin_keys is not None and list(kotlin_keys) != list(expected_keys):
            errors.append(f"DrinkIcon-Schlüssel in {kotlin_file.name} weichen ab: {kotlin_keys} ≠ {list(expected_keys)}")
    return errors, warnings


def _geometry_problems(spec: _Spec, xml_text: str) -> list[str]:
    points = outline_points(xml_text)
    root = ET.fromstring(xml_text)
    width = float(root.get(_A + "viewportWidth", 0))
    height = float(root.get(_A + "viewportHeight", 0))
    problems = []
    if spec.target.startswith("drawable/drink_"):
        if (width, height) != (48, 48):
            problems.append(f"res/{spec.target}: viewBox muss 0 0 48 48 sein")
        box = bounds(points)
        if (box.left < ICON_MARGIN or box.top < ICON_MARGIN or box.right > width - ICON_MARGIN
                or box.bottom > height - ICON_MARGIN):
            problems.append(f"res/{spec.target}: Zeichnung zu nah am oder über dem Rand ({_fmt(box)}, "
                            f"mindestens {ICON_MARGIN:g} Abstand)")
    elif spec.target == "drawable/ic_launcher_foreground.xml":
        reach = max_distance(points, LAUNCHER_CENTER, LAUNCHER_CENTER)
        if reach > LAUNCHER_SAFE_RADIUS:
            problems.append(f"res/{spec.target}: Vordergrund verlässt die Schutzzone (Radius {reach:.1f} > "
                            f"{LAUNCHER_SAFE_RADIUS:g} dp)")
    elif spec.target == "drawable/tv_banner.xml":
        if (width, height) != (320, 180):
            problems.append(f"res/{spec.target}: viewBox muss 0 0 320 180 sein")
    return problems


def _fmt(box: Bounds) -> str:
    return f"{box.left:.1f}, {box.top:.1f} – {box.right:.1f}, {box.bottom:.1f}"
