#!/usr/bin/env python3
# SPDX-License-Identifier: CC0-1.0
"""Erzeugt einen Kontaktbogen (HTML) aller Icons zur Sichtprüfung im Browser.

Gezeigt werden die SVG-Quellen auf hellem und dunklem Grund (48 px und 96 px), in Graustufen, die aus den
Android-VectorDrawables zurückübersetzten Grafiken direkt neben den SVGs (so fallen Fehler des Umwandlers
sofort auf), das App-Symbol mit verschiedenen Launcher-Masken samt Schutzzone und das TV-Banner.

    python3 design/tools/contact_sheet.py /tmp/icons.html
    # dann die Datei im Browser öffnen

Nur Python-Standardbibliothek.
"""
from __future__ import annotations

import argparse
import base64
import html
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Sequence

from svg2vector import DRINK_ICON_KEYS, Project

_A = "{http://schemas.android.com/apk/res/android}"


def _android_paint(color: str | None, alpha_attr: str | None) -> tuple[str, float]:
    """``#AARRGGBB``/``#RRGGBB`` → (``#RRGGBB``, Deckkraft)."""
    if not color:
        return "none", 1.0
    digits = color.lstrip("#")
    if len(digits) == 8:
        opacity = int(digits[:2], 16) / 255
        digits = digits[2:]
    else:
        opacity = 1.0
    if alpha_attr is not None:
        opacity *= float(alpha_attr)
    return f"#{digits}", opacity


def vector_to_svg(xml_text: str) -> str:
    """Übersetzt ein (von svg2vector erzeugtes) VectorDrawable zurück in SVG – nur für die Vorschau."""
    root = ET.fromstring(xml_text)
    width = root.get(_A + "viewportWidth")
    height = root.get(_A + "viewportHeight")
    body = "".join(_vector_children(root))
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {height}">{body}</svg>'


def _vector_children(element: ET.Element) -> list[str]:
    parts = []
    for child in element:
        if child.tag == "group":
            def number(name: str, default: float) -> float:
                return float(child.get(_A + name, default))

            px, py = number("pivotX", 0), number("pivotY", 0)
            transform = (f"translate({number('translateX', 0) + px} {number('translateY', 0) + py}) "
                         f"rotate({number('rotation', 0)}) scale({number('scaleX', 1)} {number('scaleY', 1)}) "
                         f"translate({-px} {-py})")
            parts.append(f'<g transform="{transform}">{"".join(_vector_children(child))}</g>')
        elif child.tag == "path":
            fill, fill_opacity = _android_paint(child.get(_A + "fillColor"), child.get(_A + "fillAlpha"))
            stroke, stroke_opacity = _android_paint(child.get(_A + "strokeColor"), child.get(_A + "strokeAlpha"))
            attrs = {
                "d": child.get(_A + "pathData", ""),
                "fill": fill,
                "fill-opacity": f"{fill_opacity:.3f}",
                "fill-rule": "evenodd" if child.get(_A + "fillType") == "evenOdd" else "nonzero",
                "stroke": stroke,
                "stroke-opacity": f"{stroke_opacity:.3f}",
                "stroke-width": child.get(_A + "strokeWidth", "0"),
                "stroke-linecap": child.get(_A + "strokeLineCap", "butt"),
                "stroke-linejoin": child.get(_A + "strokeLineJoin", "miter"),
                "stroke-miterlimit": child.get(_A + "strokeMiterLimit", "4"),
            }
            parts.append("<path " + " ".join(f'{k}="{html.escape(v)}"' for k, v in attrs.items()) + "/>")
    return parts


def _data_uri(svg_text: str) -> str:
    return "data:image/svg+xml;base64," + base64.b64encode(svg_text.encode("utf-8")).decode("ascii")


def _img(uri: str, size: int, extra_class: str = "") -> str:
    return f'<img class="{extra_class}" src="{uri}" width="{size}" height="{size}" alt="">'


_STYLE = """
body { margin: 0; font: 13px/1.3 sans-serif; background: #F3F4F6; color: #1F2933; }
section { padding: 16px; display: inline-block; }
h2 { margin: 0 0 10px; font-size: 15px; }
.grid { display: grid; grid-template-columns: repeat(11, 104px); gap: 8px; }
.cell { display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 6px 0; border-radius: 8px; }
.cell span { font-size: 10px; }
.light { background: #FFFFFF; color: #1F2933; }
.dark { background: #121212; color: #E5E7EB; }
.mid { background: #9AA5B1; color: #111; }
.grey { filter: grayscale(100%); }
.pair { display: flex; gap: 2px; }
.row { display: flex; gap: 16px; align-items: flex-start; flex-wrap: wrap; }
.mask { display: flex; flex-direction: column; align-items: center; gap: 4px; }
"""


class ContactSheet:
    """Baut das HTML; Quelle der Grafiken ist ein :class:`Project` (austauschbar für Tests)."""

    def __init__(self, project: Project, keys: Sequence[str] = DRINK_ICON_KEYS) -> None:
        self._project = project
        self._keys = keys

    def _svg_uri(self, name: str) -> str:
        return _data_uri((self._project.icons_dir / name).read_text(encoding="utf-8"))

    def _android_uri(self, target: str) -> str:
        return _data_uri(vector_to_svg((self._project.res_dir / target).read_text(encoding="utf-8")))

    def _icon_grid(self, section_id: str, title: str, background: str, grey: bool = False) -> str:
        cells = []
        for key in self._keys:
            uri = self._svg_uri(f"{key}.svg")
            klass = "grey" if grey else ""
            cells.append(f'<div class="cell {background}">{_img(uri, 96, klass)}{_img(uri, 48, klass)}'
                         f'<span>{html.escape(key)}</span></div>')
        return f'<section id="{section_id}" class="{background}"><h2>{title}</h2><div class="grid">{"".join(cells)}</div></section>'

    def _android_grid(self) -> str:
        cells = []
        for key in self._keys:
            svg_uri = self._svg_uri(f"{key}.svg")
            android_uri = self._android_uri(f"drawable/drink_{key}.xml")
            cells.append(f'<div class="cell light"><div class="pair">{_img(svg_uri, 48)}{_img(android_uri, 48)}</div>'
                         f'<div class="pair dark">{_img(svg_uri, 48)}{_img(android_uri, 48)}</div>'
                         f'<span>{html.escape(key)}</span></div>')
        return ('<section id="android" class="light"><h2>Links SVG-Quelle, rechts erzeugtes Android-VectorDrawable '
                f'(zurückübersetzt)</h2><div class="grid">{"".join(cells)}</div></section>')

    def _app_icon(self) -> str:
        background = self._svg_uri("app_icon_background.svg")
        foreground = self._svg_uri("app_icon_foreground.svg")
        android_bg = self._android_uri("drawable/ic_launcher_background.xml")
        android_fg = self._android_uri("drawable/ic_launcher_foreground.xml")
        masks = {
            "Kreis": '<circle cx="54" cy="54" r="36"/>',
            "Squircle": '<path d="M54 18 C84 18 90 24 90 54 C90 84 84 90 54 90 C24 90 18 84 18 54 C18 24 24 18 54 18 Z"/>',
            "abgerundet": '<rect x="18" y="18" width="72" height="72" rx="14"/>',
            "Tropfen": '<path d="M54 18 H90 V54 A36 36 0 0 1 54 90 A36 36 0 0 1 18 54 A36 36 0 0 1 54 18 Z"/>',
        }
        items = []
        for index, (name, shape) in enumerate(masks.items()):
            for label, bg, fg in (("SVG", background, foreground), ("Android", android_bg, android_fg)):
                clip = f"m{index}{label}"
                svg = (f'<svg width="120" height="120" viewBox="18 18 72 72"><defs><clipPath id="{clip}">{shape}'
                       f'</clipPath></defs><g clip-path="url(#{clip})"><image href="{bg}" width="108" height="108"/>'
                       f'<image href="{fg}" width="108" height="108"/></g></svg>')
                items.append(f'<div class="mask">{svg}<span>{name} · {label}</span></div>')
        small = (f'<div class="mask"><svg width="48" height="48" viewBox="18 18 72 72"><defs><clipPath id="s48">'
                 f'{masks["Kreis"]}</clipPath></defs><g clip-path="url(#s48)"><image href="{background}" width="108" '
                 f'height="108"/><image href="{foreground}" width="108" height="108"/></g></svg><span>48 px</span></div>')
        zone = (f'<div class="mask"><svg width="216" height="216" viewBox="0 0 108 108"><image href="{background}" '
                f'width="108" height="108"/><image href="{foreground}" width="108" height="108"/>'
                '<rect x="18" y="18" width="72" height="72" fill="none" stroke="#FF00AA" stroke-width="0.6" '
                'stroke-dasharray="2 2"/><circle cx="54" cy="54" r="33" fill="none" stroke="#FFEE00" '
                'stroke-width="0.6"/></svg><span>108 dp, gelb: Schutzzone Ø 66, pink: sichtbar 72</span></div>')
        return (f'<section id="appicon" class="mid"><h2>App-Symbol (adaptiv)</h2><div class="row">{zone}'
                f'{"".join(items)}{small}</div></section>')

    def _banner(self) -> str:
        svg = self._svg_uri("tv_banner.svg")
        android = self._android_uri("drawable/tv_banner.xml")
        return (f'<section id="banner" class="dark"><h2>TV-Banner 320 × 180 (oben SVG, unten Android)</h2>'
                f'<div class="row"><img src="{svg}" width="320" height="180" alt=""><img src="{svg}" width="160" '
                f'height="90" alt=""></div><div class="row" style="margin-top:8px"><img src="{android}" width="320" '
                f'height="180" alt=""><img class="grey" src="{android}" width="320" height="180" alt=""></div></section>')

    def build(self) -> str:
        sections = [
            self._icon_grid("light", "Heller Grund (96 px und 48 px)", "light"),
            self._icon_grid("dark", "Dunkler Grund (96 px und 48 px)", "dark"),
            self._icon_grid("grey-light", "Graustufen, heller Grund", "light", grey=True),
            self._icon_grid("grey-dark", "Graustufen, dunkler Grund", "dark", grey=True),
            self._android_grid(),
            self._app_icon(),
            self._banner(),
        ]
        body = "<br>".join(sections)
        return (f'<!doctype html><html lang="de"><head><meta charset="utf-8"><title>Drink With Me – Icons</title>'
                f'<style>{_STYLE}</style></head><body>{body}</body></html>')


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Kontaktbogen aller Icons als HTML erzeugen.")
    parser.add_argument("output", help="Zieldatei, z. B. /tmp/icons.html")
    args = parser.parse_args(argv)
    Path(args.output).write_text(ContactSheet(Project.from_repository()).build(), encoding="utf-8")
    print(f"Kontaktbogen: {args.output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
