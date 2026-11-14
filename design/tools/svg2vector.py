#!/usr/bin/env python3
# SPDX-License-Identifier: CC0-1.0
"""svg2vector – wandelt die Drink-With-Me-Icons (SVG) in Android-VectorDrawable-XML um.

Unterstützte SVG-Teilmenge (alles andere bricht mit einer verständlichen Fehlermeldung ab, damit SVG und
Android-Grafik nie unbemerkt voneinander abweichen):

* Elemente: ``svg`` (mit ``viewBox``), ``g``, ``path``, ``circle``, ``ellipse``, ``rect`` (auch mit ``rx``/``ry``),
  ``line``, ``polygon``, ``polyline``; ``title``/``desc``/``metadata`` werden ignoriert.
* Attribute (direkt oder in ``style="…"``, vererbt über ``g``): ``fill``, ``fill-opacity``, ``fill-rule``,
  ``stroke``, ``stroke-opacity``, ``stroke-width``, ``stroke-linecap``, ``stroke-linejoin``,
  ``stroke-miterlimit``, ``opacity`` (wird auf die einzelnen Formen verteilt).
* ``transform`` mit ``translate``, ``scale`` und ``rotate`` (wird zu ``<group>``).

Aufruf (aus dem Repository-Wurzelverzeichnis):

    python3 design/tools/svg2vector.py convert design/icons/water.svg /tmp/water.xml
    python3 design/tools/svg2vector.py sync     # alle Android-Drawables neu erzeugen
    python3 design/tools/svg2vector.py check    # prüfen, ob alles aktuell und gültig ist (Exit-Code 1 sonst)

Nur Python-Standardbibliothek (3.10+).
"""
from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field, replace
from pathlib import Path
from typing import Callable, Iterable, Mapping, Protocol, Sequence, Union

from svgpath import PathDataError, format_number, normalize_path_data

SVG_NS = "http://www.w3.org/2000/svg"
ANDROID_NS = "http://schemas.android.com/apk/res/android"

#: Schlüssel aus ``DrinkIcon`` (docs/ARCHITEKTUR.md, Abschnitt 1.1) – je ein SVG ``design/icons/<key>.svg``.
DRINK_ICON_KEYS: tuple[str, ...] = (
    "water", "sparkling_water", "coffee", "tea", "juice", "apple_spritzer", "cola", "lemonade",
    "iced_tea", "energy_drink", "milk", "malt_beer", "beer", "wheat_beer", "radler", "red_wine", "white_wine",
    "sparkling_wine", "shot", "longdrink", "cocktail", "other",
)


class ConversionError(ValueError):
    """Das SVG nutzt etwas, das ein VectorDrawable nicht (oder nicht gleich) darstellen kann."""


# --- Farben -----------------------------------------------------------------------------------------

_NAMED_COLORS = {
    "black": (0, 0, 0), "white": (255, 255, 255), "red": (255, 0, 0), "green": (0, 128, 0),
    "blue": (0, 0, 255), "yellow": (255, 255, 0), "gray": (128, 128, 128), "grey": (128, 128, 128),
}
_HEX_RE = re.compile(r"#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})")
_RGB_RE = re.compile(r"rgb\(\s*([\d.]+%?)\s*,\s*([\d.]+%?)\s*,\s*([\d.]+%?)\s*\)")

Rgb = tuple[int, int, int]


def parse_color(value: str) -> Rgb | None:
    """Liest eine SVG-Farbe; ``None`` bedeutet „keine Farbe“ (``none``/``transparent``)."""
    text = value.strip().lower()
    if text in ("none", "transparent"):
        return None
    hex_match = _HEX_RE.fullmatch(text)
    if hex_match:
        digits = hex_match.group(1)
        if len(digits) == 3:
            digits = "".join(ch * 2 for ch in digits)
        return int(digits[0:2], 16), int(digits[2:4], 16), int(digits[4:6], 16)
    rgb_match = _RGB_RE.fullmatch(text)
    if rgb_match:
        return tuple(_rgb_channel(part) for part in rgb_match.groups())  # type: ignore[return-value]
    if text in _NAMED_COLORS:
        return _NAMED_COLORS[text]
    if text.startswith("url("):
        raise ConversionError(f"Verläufe/Muster ({value}) sind nicht erlaubt – bitte flächige Farben verwenden")
    raise ConversionError(f"Unbekannte Farbe {value!r}")


def _rgb_channel(part: str) -> int:
    number = float(part.rstrip("%"))
    if part.endswith("%"):
        number = number * 255 / 100
    return max(0, min(255, round(number)))


def android_color(rgb: Rgb, alpha: float = 1.0) -> str:
    """``#RRGGBB`` oder – bei Transparenz – ``#AARRGGBB``."""
    channel_alpha = max(0, min(255, round(alpha * 255)))
    rgb_hex = "".join(f"{channel:02X}" for channel in rgb)
    return f"#{rgb_hex}" if channel_alpha == 255 else f"#{channel_alpha:02X}{rgb_hex}"


# --- Stil (Präsentationsattribute) ------------------------------------------------------------------

_STYLE_PROPERTIES = {
    "fill", "fill-opacity", "fill-rule", "stroke", "stroke-opacity", "stroke-width", "stroke-linecap",
    "stroke-linejoin", "stroke-miterlimit", "opacity",
}
#: Eigenschaften, die VectorDrawable nicht kennt – lieber abbrechen als still anders aussehen.
_UNSUPPORTED_PROPERTIES = {
    "stroke-dasharray", "stroke-dashoffset", "filter", "mask", "clip-path", "marker", "marker-start",
    "marker-mid", "marker-end", "paint-order", "vector-effect", "mix-blend-mode", "font", "font-family",
}
_LINECAPS = {"butt", "round", "square"}
_LINEJOINS = {"miter", "round", "bevel"}
_FILL_RULES = {"nonzero", "evenodd"}


@dataclass(frozen=True)
class Style:
    """Aufgelöster (geerbter) Stil einer Form. Standardwerte wie in SVG."""

    fill: str = "black"
    fill_opacity: float = 1.0
    fill_rule: str = "nonzero"
    stroke: str = "none"
    stroke_opacity: float = 1.0
    stroke_width: float = 1.0
    stroke_linecap: str = "butt"
    stroke_linejoin: str = "miter"
    stroke_miterlimit: float = 4.0
    opacity: float = 1.0  # Produkt der Deckkraft aller Vorfahren und der Form selbst


def parse_length(value: str, what: str) -> float:
    """Zahl ohne Einheit oder mit ``px``; Prozentwerte und andere Einheiten sind nicht erlaubt."""
    text = value.strip()
    if text.endswith("px"):
        text = text[:-2]
    try:
        return float(text)
    except ValueError as error:
        raise ConversionError(f"{what}: Zahl erwartet, gefunden {value!r}") from error


def _parse_opacity(value: str, what: str) -> float:
    return max(0.0, min(1.0, parse_length(value, what)))


def _declared_properties(element: ET.Element) -> dict[str, str]:
    """Präsentationsattribute und ``style``-Deklarationen (``style`` hat Vorrang)."""
    declared = {name: value for name, value in element.attrib.items() if not name.startswith("{")}
    for declaration in declared.pop("style", "").split(";"):
        if ":" in declaration:
            name, value = declaration.split(":", 1)
            declared[name.strip()] = value.strip()
    return declared


class StyleResolver:
    """Berechnet den Stil eines Elements aus dem Stil der Eltern und den eigenen Angaben."""

    def resolve(self, parent: Style, element: ET.Element) -> Style:
        declared = _declared_properties(element)
        for name in declared:
            if name in _UNSUPPORTED_PROPERTIES:
                raise ConversionError(f"<{_local(element.tag)}>: {name} wird von VectorDrawable nicht unterstützt")
        props = {name: value for name, value in declared.items() if name in _STYLE_PROPERTIES}
        style = parent
        if "fill" in props:
            style = replace(style, fill=props["fill"])
        if "stroke" in props:
            style = replace(style, stroke=props["stroke"])
        if "fill-opacity" in props:
            style = replace(style, fill_opacity=_parse_opacity(props["fill-opacity"], "fill-opacity"))
        if "stroke-opacity" in props:
            style = replace(style, stroke_opacity=_parse_opacity(props["stroke-opacity"], "stroke-opacity"))
        if "stroke-width" in props:
            style = replace(style, stroke_width=parse_length(props["stroke-width"], "stroke-width"))
        if "stroke-miterlimit" in props:
            style = replace(style, stroke_miterlimit=parse_length(props["stroke-miterlimit"], "stroke-miterlimit"))
        if "fill-rule" in props:
            style = replace(style, fill_rule=_choice(props["fill-rule"], _FILL_RULES, "fill-rule"))
        if "stroke-linecap" in props:
            style = replace(style, stroke_linecap=_choice(props["stroke-linecap"], _LINECAPS, "stroke-linecap"))
        if "stroke-linejoin" in props:
            style = replace(style, stroke_linejoin=_choice(props["stroke-linejoin"], _LINEJOINS, "stroke-linejoin"))
        if "opacity" in props:
            style = replace(style, opacity=parent.opacity * _parse_opacity(props["opacity"], "opacity"))
        return style


def _choice(value: str, allowed: set[str], what: str) -> str:
    text = value.strip()
    if text not in allowed:
        raise ConversionError(f"{what}={value!r} nicht unterstützt (erlaubt: {', '.join(sorted(allowed))})")
    return text


# --- Transformationen -------------------------------------------------------------------------------


@dataclass(frozen=True)
class GroupTransform:
    """Eine VectorDrawable-``<group>``-Transformation (Android rechnet: Pivot, Skalierung, Drehung, Verschiebung)."""

    translate_x: float = 0.0
    translate_y: float = 0.0
    scale_x: float = 1.0
    scale_y: float = 1.0
    rotation: float = 0.0
    pivot_x: float = 0.0
    pivot_y: float = 0.0


_TRANSFORM_RE = re.compile(r"\s*([a-zA-Z]+)\s*\(([^)]*)\)\s*,?")


def parse_transform(value: str) -> list[GroupTransform]:
    """Zerlegt ``transform``; die erste Angabe wird die äußerste Gruppe (wie in SVG)."""
    transforms: list[GroupTransform] = []
    position = 0
    text = value.strip()
    while position < len(text):
        match = _TRANSFORM_RE.match(text, position)
        if not match:
            raise ConversionError(f"transform={value!r} nicht lesbar")
        position = match.end()
        name = match.group(1)
        numbers = [float(n) for n in re.split(r"[\s,]+", match.group(2).strip()) if n]
        transforms.append(_group_transform(name, numbers, value))
    return transforms


def _group_transform(name: str, numbers: list[float], source: str) -> GroupTransform:
    if name == "translate" and len(numbers) in (1, 2):
        return GroupTransform(translate_x=numbers[0], translate_y=numbers[1] if len(numbers) == 2 else 0.0)
    if name == "scale" and len(numbers) in (1, 2):
        return GroupTransform(scale_x=numbers[0], scale_y=numbers[1] if len(numbers) == 2 else numbers[0])
    if name == "rotate" and len(numbers) in (1, 3):
        pivot = (numbers[1], numbers[2]) if len(numbers) == 3 else (0.0, 0.0)
        return GroupTransform(rotation=numbers[0], pivot_x=pivot[0], pivot_y=pivot[1])
    raise ConversionError(f"transform {name}({', '.join(map(str, numbers))}) in {source!r} nicht unterstützt "
                          "(erlaubt: translate, scale, rotate)")


# --- Formen -> Pfaddaten ----------------------------------------------------------------------------


class ShapeConverter(Protocol):
    """Wandelt ein Form-Element in Pfaddaten um."""

    #: ``False`` für Formen ohne Fläche (``line``): ``fill`` wird dort ignoriert, wie in SVG.
    fillable: bool

    def path_data(self, element: ET.Element) -> str:
        ...


def _number(element: ET.Element, name: str, default: float | None = None) -> float:
    value = element.get(name)
    if value is None:
        if default is None:
            raise ConversionError(f"<{_local(element.tag)}> braucht das Attribut {name}")
        return default
    return parse_length(value, f"<{_local(element.tag)}> {name}")


def _n(value: float) -> str:
    return format_number(value)


def ellipse_path(cx: float, cy: float, rx: float, ry: float) -> str:
    """Ellipse als zwei Halbbögen im Uhrzeigersinn (Android versteht Bögen in ``pathData``)."""
    if rx <= 0 or ry <= 0:
        raise ConversionError("Kreis/Ellipse braucht positive Radien")
    return (f"M{_n(cx - rx)},{_n(cy)} A{_n(rx)},{_n(ry)},0,1,1,{_n(cx + rx)},{_n(cy)} "
            f"A{_n(rx)},{_n(ry)},0,1,1,{_n(cx - rx)},{_n(cy)} Z")


class PathShape:
    fillable = True

    def path_data(self, element: ET.Element) -> str:
        return element.get("d", "")


class CircleShape:
    fillable = True

    def path_data(self, element: ET.Element) -> str:
        radius = _number(element, "r")
        return ellipse_path(_number(element, "cx", 0.0), _number(element, "cy", 0.0), radius, radius)


class EllipseShape:
    fillable = True

    def path_data(self, element: ET.Element) -> str:
        return ellipse_path(_number(element, "cx", 0.0), _number(element, "cy", 0.0),
                            _number(element, "rx"), _number(element, "ry"))


class RectShape:
    fillable = True

    def path_data(self, element: ET.Element) -> str:
        x, y = _number(element, "x", 0.0), _number(element, "y", 0.0)
        width, height = _number(element, "width"), _number(element, "height")
        if width <= 0 or height <= 0:
            raise ConversionError("<rect> braucht positive Breite und Höhe")
        rx_attr, ry_attr = element.get("rx"), element.get("ry")
        rx = parse_length(rx_attr, "rx") if rx_attr is not None else None
        ry = parse_length(ry_attr, "ry") if ry_attr is not None else None
        rx = rx if rx is not None else (ry or 0.0)
        ry = ry if ry is not None else rx
        rx, ry = min(rx, width / 2), min(ry, height / 2)
        right, bottom = x + width, y + height
        if rx <= 0 or ry <= 0:
            return f"M{_n(x)},{_n(y)} H{_n(right)} V{_n(bottom)} H{_n(x)} Z"
        arc = f"A{_n(rx)},{_n(ry)},0,0,1,"
        return (f"M{_n(x + rx)},{_n(y)} H{_n(right - rx)} {arc}{_n(right)},{_n(y + ry)} "
                f"V{_n(bottom - ry)} {arc}{_n(right - rx)},{_n(bottom)} H{_n(x + rx)} "
                f"{arc}{_n(x)},{_n(bottom - ry)} V{_n(y + ry)} {arc}{_n(x + rx)},{_n(y)} Z")


class LineShape:
    fillable = False

    def path_data(self, element: ET.Element) -> str:
        return (f"M{_n(_number(element, 'x1', 0.0))},{_n(_number(element, 'y1', 0.0))} "
                f"L{_n(_number(element, 'x2', 0.0))},{_n(_number(element, 'y2', 0.0))}")


class PolyShape:
    fillable = True

    def __init__(self, closed: bool) -> None:
        self._closed = closed

    def path_data(self, element: ET.Element) -> str:
        numbers = [float(n) for n in re.split(r"[\s,]+", element.get("points", "").strip()) if n]
        if len(numbers) < 4 or len(numbers) % 2:
            raise ConversionError(f"<{_local(element.tag)}> braucht mindestens zwei Punkte (x,y)")
        pairs = [f"{_n(numbers[i])},{_n(numbers[i + 1])}" for i in range(0, len(numbers), 2)]
        return "M" + " L".join(pairs) + (" Z" if self._closed else "")


DEFAULT_SHAPES: Mapping[str, ShapeConverter] = {
    "path": PathShape(),
    "circle": CircleShape(),
    "ellipse": EllipseShape(),
    "rect": RectShape(),
    "line": LineShape(),
    "polygon": PolyShape(closed=True),
    "polyline": PolyShape(closed=False),
}

_IGNORED_ELEMENTS = {"title", "desc", "metadata"}


# --- VectorDrawable-Modell --------------------------------------------------------------------------


@dataclass(frozen=True)
class VectorPath:
    path_data: str
    fill_color: str | None = None
    fill_type: str | None = None  # "evenOdd" oder None (= nonZero)
    stroke_color: str | None = None
    stroke_width: float | None = None
    stroke_line_cap: str | None = None
    stroke_line_join: str | None = None
    stroke_miter_limit: float | None = None


@dataclass(frozen=True)
class VectorGroup:
    transform: GroupTransform
    children: tuple["VectorNode", ...]


VectorNode = Union[VectorPath, VectorGroup]


@dataclass(frozen=True)
class VectorDocument:
    width_dp: float
    height_dp: float
    viewport_width: float
    viewport_height: float
    children: tuple[VectorNode, ...]
    comment: str | None = None


# --- SVG -> Modell ----------------------------------------------------------------------------------


def _local(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


class SvgToVectorConverter:
    """Übersetzt ein SVG-Dokument in ein :class:`VectorDocument`."""

    def __init__(self, shapes: Mapping[str, ShapeConverter] = DEFAULT_SHAPES,
                 style_resolver: StyleResolver | None = None) -> None:
        self._shapes = shapes
        self._styles = style_resolver or StyleResolver()

    def convert(self, svg_text: str, width_dp: float | None = None, height_dp: float | None = None,
                comment: str | None = None) -> VectorDocument:
        try:
            root = ET.fromstring(svg_text)
        except ET.ParseError as error:
            raise ConversionError(f"SVG ist kein wohlgeformtes XML: {error}") from error
        if _local(root.tag) != "svg":
            raise ConversionError("Wurzelelement muss <svg> sein")
        min_x, min_y, viewport_w, viewport_h = self._view_box(root)
        style = self._styles.resolve(Style(), root)
        children = tuple(self._children(root, style))
        if min_x or min_y:
            children = (VectorGroup(GroupTransform(translate_x=-min_x, translate_y=-min_y), children),)
        if not children:
            raise ConversionError("SVG enthält keine sichtbare Form")
        return VectorDocument(
            width_dp=width_dp if width_dp is not None else viewport_w,
            height_dp=height_dp if height_dp is not None else viewport_h,
            viewport_width=viewport_w, viewport_height=viewport_h, children=children, comment=comment,
        )

    @staticmethod
    def _view_box(root: ET.Element) -> tuple[float, float, float, float]:
        view_box = root.get("viewBox")
        if not view_box:
            raise ConversionError("<svg> braucht eine viewBox, z. B. viewBox=\"0 0 48 48\"")
        numbers = [float(n) for n in re.split(r"[\s,]+", view_box.strip()) if n]
        if len(numbers) != 4 or numbers[2] <= 0 or numbers[3] <= 0:
            raise ConversionError(f"viewBox={view_box!r} ungültig")
        return numbers[0], numbers[1], numbers[2], numbers[3]

    def _children(self, parent: ET.Element, parent_style: Style) -> Iterable[VectorNode]:
        for element in parent:
            if not isinstance(element.tag, str):
                continue  # Kommentare, Verarbeitungsanweisungen
            tag = _local(element.tag)
            if tag in _IGNORED_ELEMENTS:
                continue
            style = self._styles.resolve(parent_style, element)
            if tag == "g":
                nodes: tuple[VectorNode, ...] = tuple(self._children(element, style))
            elif tag in self._shapes:
                path = self._path(self._shapes[tag], element, style)
                nodes = (path,) if path else ()
            else:
                raise ConversionError(f"<{tag}> wird nicht unterstützt (Text bitte als Pfade zeichnen; "
                                      "keine Verläufe, Filter, Masken, Bilder oder <use>)")
            transform = element.get("transform")
            if transform and nodes:
                for group_transform in reversed(parse_transform(transform)):
                    if group_transform != GroupTransform():  # neutrale Transformation braucht keine Gruppe
                        nodes = (VectorGroup(group_transform, nodes),)
            yield from nodes

    def _path(self, shape: ShapeConverter, element: ET.Element, style: Style) -> VectorPath | None:
        try:
            path_data = normalize_path_data(shape.path_data(element))
        except PathDataError as error:
            raise ConversionError(f"<{_local(element.tag)}>: {error}") from error
        fill_rgb = parse_color(style.fill) if shape.fillable else None
        stroke_rgb = parse_color(style.stroke) if style.stroke_width > 0 else None
        fill_alpha = style.fill_opacity * style.opacity
        stroke_alpha = style.stroke_opacity * style.opacity
        if fill_rgb is not None and stroke_rgb is not None and style.opacity < 1:
            raise ConversionError(f"<{_local(element.tag)}>: opacity bei Füllung UND Kontur ist in VectorDrawable "
                                  "nicht gleich darstellbar – bitte fill-opacity/stroke-opacity verwenden")
        fill_color = android_color(fill_rgb, fill_alpha) if fill_rgb is not None and fill_alpha > 0 else None
        stroke_color = android_color(stroke_rgb, stroke_alpha) if stroke_rgb is not None and stroke_alpha > 0 else None
        if fill_color is None and stroke_color is None:
            return None
        has_stroke = stroke_color is not None
        return VectorPath(
            path_data=path_data,
            fill_color=fill_color,
            fill_type="evenOdd" if fill_color and style.fill_rule == "evenodd" else None,
            stroke_color=stroke_color,
            stroke_width=style.stroke_width if has_stroke else None,
            stroke_line_cap=style.stroke_linecap if has_stroke and style.stroke_linecap != "butt" else None,
            stroke_line_join=style.stroke_linejoin if has_stroke and style.stroke_linejoin != "miter" else None,
            stroke_miter_limit=(style.stroke_miterlimit if has_stroke and style.stroke_linejoin == "miter"
                                and style.stroke_miterlimit != 4.0 else None),
        )


# --- Modell -> XML ----------------------------------------------------------------------------------


class VectorXmlWriter:
    """Schreibt ein :class:`VectorDocument` als Android-VectorDrawable (stabil formatiert, diff-freundlich)."""

    _INDENT = "    "

    def write(self, document: VectorDocument) -> str:
        lines = ['<?xml version="1.0" encoding="utf-8"?>']
        if document.comment:
            lines.append(f"<!-- {document.comment.replace('--', '–')} -->")
        attrs = [
            ("android:width", f"{format_number(document.width_dp)}dp"),
            ("android:height", f"{format_number(document.height_dp)}dp"),
            ("android:viewportWidth", format_number(document.viewport_width)),
            ("android:viewportHeight", format_number(document.viewport_height)),
        ]
        lines += self._tag_lines("", f'vector xmlns:android="{ANDROID_NS}"', attrs, ">")
        for child in document.children:
            lines += self._node(child, 1)
        lines.append("</vector>")
        return "\n".join(lines) + "\n"

    def _node(self, node: VectorNode, depth: int) -> list[str]:
        indent = self._INDENT * depth
        if isinstance(node, VectorGroup):
            lines = self._tag_lines(indent, "group", self._group_attributes(node.transform), ">")
            for child in node.children:
                lines += self._node(child, depth + 1)
            lines.append(f"{indent}</group>")
            return lines
        return self._tag_lines(indent, "path", self._path_attributes(node), " />")

    def _tag_lines(self, indent: str, tag: str, attrs: Sequence[tuple[str, str]], closing: str) -> list[str]:
        """Öffnendes Tag, jedes Attribut auf eigener Zeile (wie in Android Studio üblich)."""
        if not attrs:
            return [f"{indent}<{tag}{closing}"]
        lines = [f"{indent}<{tag}"] + [f'{indent}{self._INDENT}{name}="{_escape(value)}"' for name, value in attrs]
        lines[-1] += closing
        return lines

    @staticmethod
    def _group_attributes(transform: GroupTransform) -> list[tuple[str, str]]:
        attrs = []
        if transform.rotation:
            attrs.append(("android:rotation", format_number(transform.rotation)))
        if transform.pivot_x:
            attrs.append(("android:pivotX", format_number(transform.pivot_x)))
        if transform.pivot_y:
            attrs.append(("android:pivotY", format_number(transform.pivot_y)))
        if transform.scale_x != 1:
            attrs.append(("android:scaleX", format_number(transform.scale_x)))
        if transform.scale_y != 1:
            attrs.append(("android:scaleY", format_number(transform.scale_y)))
        if transform.translate_x:
            attrs.append(("android:translateX", format_number(transform.translate_x)))
        if transform.translate_y:
            attrs.append(("android:translateY", format_number(transform.translate_y)))
        return attrs

    @staticmethod
    def _path_attributes(path: VectorPath) -> list[tuple[str, str]]:
        attrs = [("android:pathData", path.path_data)]
        if path.fill_color:
            attrs.append(("android:fillColor", path.fill_color))
        if path.fill_type:
            attrs.append(("android:fillType", path.fill_type))
        if path.stroke_color:
            attrs.append(("android:strokeColor", path.stroke_color))
            attrs.append(("android:strokeWidth", format_number(path.stroke_width or 0)))
        if path.stroke_line_cap:
            attrs.append(("android:strokeLineCap", path.stroke_line_cap))
        if path.stroke_line_join:
            attrs.append(("android:strokeLineJoin", path.stroke_line_join))
        if path.stroke_miter_limit is not None:
            attrs.append(("android:strokeMiterLimit", format_number(path.stroke_miter_limit)))
        return attrs


def _escape(value: str) -> str:
    return value.replace("&", "&amp;").replace('"', "&quot;").replace("<", "&lt;").replace(">", "&gt;")


def convert_svg(svg_text: str, width_dp: float | None = None, height_dp: float | None = None,
                comment: str | None = None) -> str:
    """Bequemer Einstieg: SVG-Text rein, VectorDrawable-XML raus."""
    return VectorXmlWriter().write(SvgToVectorConverter().convert(svg_text, width_dp, height_dp, comment))


# --- Welche SVGs werden zu welchen Android-Dateien? -------------------------------------------------

#: Kennzeichen erzeugter Dateien. Dateien ohne dieses Kennzeichen werden nie überschrieben.
GENERATED_MARKER = "mit design/tools/svg2vector.py"


@dataclass(frozen=True)
class OutputSpec:
    """Eine erzeugte Android-Datei: Quelle relativ zu ``design/icons``, Ziel relativ zu ``res``."""

    source: str
    target: str
    width_dp: float
    height_dp: float


def default_output_specs(keys: Sequence[str] = DRINK_ICON_KEYS) -> list[OutputSpec]:
    specs = [OutputSpec(f"{key}.svg", f"drawable/drink_{key}.xml", 48, 48) for key in keys]
    specs += [
        OutputSpec("app_icon_foreground.svg", "drawable/ic_launcher_foreground.xml", 108, 108),
        OutputSpec("app_icon_background.svg", "drawable/ic_launcher_background.xml", 108, 108),
        # 320 × 180 px bei xhdpi (Leanback-Banner) = 160 × 90 dp
        OutputSpec("tv_banner.svg", "drawable/tv_banner.xml", 160, 90),
        # Wird erst übernommen, wenn die bisherige (nicht erzeugte) Datei entfernt wurde – siehe design/README.md.
        OutputSpec("notification.svg", "drawable/ic_notification.xml", 24, 24),
    ]
    return specs


#: Zusammengesetztes App-Symbol (Hintergrund + Vordergrund) für Web-App/PWA und Store-Einträge.
APP_ICON_LAYERS = ("app_icon_background.svg", "app_icon_foreground.svg")
APP_ICON_COMPOSITE = "app_icon.svg"


def is_generated(text: str) -> bool:
    """``True``, wenn die Datei von diesem Werkzeug erzeugt wurde (und damit überschrieben werden darf)."""
    return GENERATED_MARKER in text


def compose_svg(layers: Sequence[str], title: str, comment: str) -> str:
    """Legt SVG-Ebenen mit gleicher viewBox übereinander (erste Ebene unten)."""
    ET.register_namespace("", SVG_NS)
    roots = [ET.fromstring(text) for text in layers]
    view_box = roots[0].get("viewBox")
    if any(root.get("viewBox") != view_box for root in roots):
        raise ConversionError("Alle Ebenen brauchen dieselbe viewBox")
    size = (view_box or "0 0 0 0").split()
    composite = ET.Element(f"{{{SVG_NS}}}svg", {"viewBox": view_box or "", "width": size[2], "height": size[3]})
    ET.SubElement(composite, f"{{{SVG_NS}}}title").text = title
    composite.append(ET.Comment(f" {comment} "))
    for root in roots:
        layer = ET.SubElement(composite, f"{{{SVG_NS}}}g")
        for name, value in root.attrib.items():
            if name not in ("viewBox", "width", "height") and not name.startswith("{"):
                layer.set(name, value)
        layer.extend(child for child in root if _local(child.tag) not in _IGNORED_ELEMENTS)
    ET.indent(composite, space="  ")
    return ET.tostring(composite, encoding="unicode") + "\n"


@dataclass
class Project:
    """Pfade des Repositorys; in Tests austauschbar."""

    icons_dir: Path
    res_dir: Path
    specs: list[OutputSpec] = field(default_factory=default_output_specs)
    kotlin_icon_file: Path | None = None
    keys: Sequence[str] = DRINK_ICON_KEYS
    app_icon_layers: Sequence[str] = APP_ICON_LAYERS

    @classmethod
    def from_repository(cls, root: Path | None = None) -> "Project":
        base = root or Path(__file__).resolve().parents[2]
        return cls(
            icons_dir=base / "design" / "icons",
            res_dir=base / "android" / "app" / "src" / "main" / "res",
            kotlin_icon_file=base / "android" / "core" / "src" / "main" / "kotlin" / "com" / "drinkwithme" / "core"
            / "bar" / "DrinkIcon.kt",
        )

    def render(self, spec: OutputSpec) -> str:
        svg_text = (self.icons_dir / spec.source).read_text(encoding="utf-8")
        comment = (f"Erzeugt aus design/icons/{spec.source} {GENERATED_MARKER} – nicht von Hand "
                   "bearbeiten. Eigenes Werk, Lizenz CC0 1.0 (design/icons/LICENSE).")
        try:
            return convert_svg(svg_text, spec.width_dp, spec.height_dp, comment)
        except ConversionError as error:
            raise ConversionError(f"{spec.source}: {error}") from error

    @staticmethod
    def is_generated(text: str) -> bool:
        return is_generated(text)

    def render_app_icon(self) -> str:
        layers = [(self.icons_dir / name).read_text(encoding="utf-8") for name in self.app_icon_layers]
        return compose_svg(layers, "Drink With Me – App-Symbol",
                           f"Erzeugt aus {' + '.join(self.app_icon_layers)} {GENERATED_MARKER} – nicht von Hand "
                           "bearbeiten. Eigenes Werk, CC0 1.0")


# --- Kommandozeile ----------------------------------------------------------------------------------


def _cmd_convert(args: argparse.Namespace) -> int:
    svg_text = Path(args.input).read_text(encoding="utf-8")
    xml_text = convert_svg(svg_text, args.width_dp, args.height_dp,
                           comment=f"Erzeugt aus {Path(args.input).name} {GENERATED_MARKER}")
    Path(args.output).write_text(xml_text, encoding="utf-8")
    print(f"{args.input} -> {args.output}")
    return 0


def _write_if_changed(target: Path, content: str) -> str:
    """Schreibt nur bei Änderungen; fremde (nicht erzeugte) Dateien bleiben unangetastet."""
    if target.exists():
        current = target.read_text(encoding="utf-8")
        if not is_generated(current):
            return "fremd"
        if current == content:
            return "gleich"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")
    return "neu"


def _cmd_sync(args: argparse.Namespace, project: Project) -> int:
    for spec in project.specs:
        state = _write_if_changed(project.res_dir / spec.target, project.render(spec))
        hint = "  (nicht erzeugte Datei – übersprungen)" if state == "fremd" else ""
        print(f"{state:6}  {spec.source} -> res/{spec.target}{hint}")
    state = _write_if_changed(project.icons_dir / APP_ICON_COMPOSITE, project.render_app_icon())
    print(f"{state:6}  {' + '.join(project.app_icon_layers)} -> design/icons/{APP_ICON_COMPOSITE}")
    return 0


def _cmd_check(args: argparse.Namespace, project: Project) -> int:
    from vector_checks import check_project  # erst hier, damit „convert“ ohne Prüfmodul läuft

    errors, warnings = check_project(project, project.keys, project.kotlin_icon_file)
    for warning in warnings:
        print(f"Warnung: {warning}")
    for error in errors:
        print(f"FEHLER: {error}")
    if errors:
        print(f"{len(errors)} Fehler. Neu erzeugen mit: python3 design/tools/svg2vector.py sync")
        return 1
    print(f"OK: {len(project.specs)} Grafiken geprüft.")
    return 0


def main(argv: Sequence[str] | None = None, project_factory: Callable[[], Project] = Project.from_repository) -> int:
    parser = argparse.ArgumentParser(description="Wandelt Drink-With-Me-SVGs in Android-VectorDrawables um.")
    sub = parser.add_subparsers(dest="command", required=True)
    convert = sub.add_parser("convert", help="eine SVG-Datei umwandeln")
    convert.add_argument("input")
    convert.add_argument("output")
    convert.add_argument("--width-dp", type=float, default=None, help="Breite in dp (Standard: viewBox-Breite)")
    convert.add_argument("--height-dp", type=float, default=None, help="Höhe in dp (Standard: viewBox-Höhe)")
    sub.add_parser("sync", help="alle Android-Drawables (und design/icons/app_icon.svg) neu erzeugen")
    sub.add_parser("check", help="prüfen, ob alle Grafiken aktuell und gültig sind (Exit-Code 1 sonst)")
    args = parser.parse_args(argv)
    try:
        if args.command == "convert":
            return _cmd_convert(args)
        project = project_factory()
        return _cmd_sync(args, project) if args.command == "sync" else _cmd_check(args, project)
    except ConversionError as error:
        print(f"FEHLER: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
