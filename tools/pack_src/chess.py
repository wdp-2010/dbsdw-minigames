"""Chess pieces: 12 turned-wood 3D models (6 Staunton pieces x white and black) and their item definitions.

Run: python3 tools/pack_src/chess.py <resourcepack dir>

Shapes follow the Staunton pattern every club set uses: a wide base with a bead, a tapering body, a
collar, then the head (pawn ball, bishop mitre with its slit, queen coronet with a ball, king crown with
a cross, rook turret with battlements). Round parts are turned: each height step is an octagon (a square
box plus the same box turned 45 degrees), stacked along the piece's real profile. The knight is a horse
head in side profile, extruded, narrower at the head than at the chest.

Colours: white is pale boxwood, black is dark walnut, both with a light wood grain and a green felt pad.
The base is at y = 0, centred on x = z = 8; the knight looks north (-Z). The plugin stands each piece on
its square and turns each side to face the other.
"""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path

from PIL import Image

from boxmodel import Box, build
from palette import OLIVE_DARK
from pixel import darken, lighten

BOXWOOD = (0xF2, 0xE2, 0xC0)
WALNUT = (0x5E, 0x3C, 0x24)
FELT = OLIVE_DARK
EYE = (0x1C, 0x14, 0x0C)

PIECES = ("pawn", "knight", "bishop", "rook", "queen", "king")
# The turned box sits a hair inside the square one at top and bottom, so their flat faces never share
# a plane (that would flicker).
EPS = 0.02


def _grain(seed: int):
    """Wood grain: faint darker and lighter streaks across each face."""
    def draw(img: Image.Image, d: str) -> None:
        w, h = img.size
        base = img.getpixel((w // 2, h // 2))[:3]
        dark = darken(base, 0.07)
        light = lighten(base, 0.06)
        for x in range(w):
            r = math.sin(x * 1.7 + seed * 3.1) + math.sin(x * 0.53 + seed)
            if r > 1.2:
                for y in range(h):
                    img.putpixel((x, y), dark + (255,))
            elif r < -1.5:
                for y in range(h):
                    img.putpixel((x, y), light + (255,))
    return draw


TAN_22_5 = math.tan(math.radians(22.5))


def turned(y0: float, y1: float, r: float, colour, seed: int = 0) -> list[Box]:
    """One regular octagonal step, r from the centre to a flat side, from y0 to y1.

    A regular octagon is exactly the union of four bars r long and r*tan(22.5) wide: one along x, one
    along z, and the same two turned 45 degrees. Each bar's top and bottom sit a hair apart so the
    overlapping flat faces never flicker.
    """
    kw = dict(outline=None, gloss=False, decorate=_grain(seed))
    w = r * TAN_22_5
    boxes = []
    for i, (turn, along_x) in enumerate(((None, True), (None, False), (45, True), (45, False))):
        rot = None if turn is None else {"angle": turn, "axis": "y", "origin": [8, (y0 + y1) / 2, 8]}
        hx, hz = (r, w) if along_x else (w, r)
        boxes.append(Box((8 - hx, y0 + i * EPS / 2, 8 - hz), (8 + hx, y1 - i * EPS / 2, 8 + hz), colour,
                         rotation=rot, **kw))
    return boxes


def profile(steps: list[tuple], colour) -> list[Box]:
    """(y0, y1, radius) steps; a radius pair (r0, r1) is a taper cut into ~1-unit steps."""
    boxes: list[Box] = []
    for i, (y0, y1, r) in enumerate(steps):
        if isinstance(r, tuple):
            n = max(1, round(y1 - y0))
            for k in range(n):
                a = y0 + (y1 - y0) * k / n
                b = y0 + (y1 - y0) * (k + 1) / n
                rr = r[0] + (r[1] - r[0]) * (k + 0.5) / n
                boxes += turned(a, b, rr, colour, seed=i * 7 + k)
        else:
            boxes += turned(y0, y1, r, colour, seed=i * 7)
    return boxes


def ball(cy: float, radius: float, slices: int, y_from: float | None = None) -> list[tuple]:
    """Steps for a sphere centred at cy (from y_from up, cut off where it sits on a neck)."""
    out = []
    bottom = cy - radius if y_from is None else y_from
    top = cy + radius
    for k in range(slices):
        a = bottom + (top - bottom) * k / slices
        b = bottom + (top - bottom) * (k + 1) / slices
        mid = (a + b) / 2
        out.append((a, b, max(0.4, math.sqrt(max(0.0, radius * radius - (mid - cy) ** 2)))))
    return out


def felt(r: float) -> list[Box]:
    boxes = turned(0, 0.6, r, FELT)
    for b in boxes:
        b.decorate = None
    return boxes


def base(r: float) -> list[tuple]:
    """Foot with a bead and a cove, from the felt to y = 4.4."""
    return [(0.6, 2.0, r), (2.0, 2.7, r - 0.5), (2.7, 3.5, r - 1.2), (3.5, 4.4, r - 1.9)]


def pawn(c) -> list[Box]:
    steps = [(0.6, 2.0, 5.4), (2.0, 2.6, 4.9), (2.6, 3.3, 4.2),
             (3.3, 8.5, (3.6, 2.1)),
             (8.5, 9.2, 3.1), (9.2, 9.8, 2.0)]
    steps += ball(12.3, 2.8, 6, y_from=9.8)
    return felt(5.2) + profile(steps, c)


def _ring(points_y0: float, points_y1: float, x0: float, x1: float, inner: float, outer: float, c, seed: int):
    """Eight small blocks around a rim: four square to the sides, four turned to the corners."""
    kw = dict(outline=None, gloss=False, decorate=_grain(seed))
    boxes = []
    mid = (points_y0 + points_y1) / 2
    for turn in (None, {"angle": 45, "axis": "y", "origin": [8, mid, 8]}):
        boxes.append(Box((x0, points_y0, 8 - outer), (x1, points_y1, 8 - inner), c, rotation=turn, **kw))
        boxes.append(Box((x0, points_y0, 8 + inner), (x1, points_y1, 8 + outer), c, rotation=turn, **kw))
        boxes.append(Box((8 - outer, points_y0, x0), (8 - inner, points_y1, x1), c, rotation=turn, **kw))
        boxes.append(Box((8 + inner, points_y0, x0), (8 + outer, points_y1, x1), c, rotation=turn, **kw))
    return boxes


def rook(c) -> list[Box]:
    steps = base(6.0) + [(4.4, 12.5, (4.1, 3.6)), (12.5, 13.2, 4.3), (13.2, 13.8, 3.9), (13.8, 17.5, 4.7)]
    return felt(5.8) + profile(steps, c) + _ring(17.5, 20.0, 7.0, 9.0, 3.3, 4.7, c, 5)


def _slit(img: Image.Image, d: str) -> None:
    """The bishop's mitre cut: a dark diagonal."""
    w, h = img.size
    for i in range(-1, max(w, h)):
        x = w // 2 - h // 2 + i
        y = h - 1 - i
        for dx in (0, 1):
            if 0 <= x + dx < w and 0 <= y < h:
                img.putpixel((x + dx, y), EYE + (255,))


def bishop(c) -> list[Box]:
    steps = base(5.8) + [(4.4, 12.0, (3.8, 2.0)), (12.0, 12.7, 3.2), (12.7, 13.4, 3.7), (13.4, 14.0, 2.4),
                         (14.0, 15.0, 2.8), (15.0, 18.8, 3.2), (18.8, 19.5, 2.9),
                         (19.5, 20.5, 2.3), (20.5, 21.4, 1.5), (21.4, 22.0, 0.8)]
    steps += ball(23.0, 1.2, 3, y_from=22.0)
    boxes = felt(5.6) + profile(steps, c)
    # The slit: a thin dark diagonal plate just in front of and behind the mitre's flat faces.
    half = 3.2 * TAN_22_5
    for z, face in ((8 - 3.22, "north"), (8 + 3.22, "south")):
        boxes.append(Box((8 - half, 15.2, z), (8 + half, 18.8, z), c, faces=(face,), outline=None, gloss=False,
                         decorate=_slit_on_wood(c)))
    return boxes


def _slit_on_wood(c):
    def draw(img: Image.Image, d: str) -> None:
        w, h = img.size
        for x in range(w):
            for y in range(h):
                img.putpixel((x, y), (0, 0, 0, 0))
        _slit(img, d)
    return draw


def queen(c) -> list[Box]:
    steps = base(6.0) + [(4.4, 14.0, (4.0, 2.2)), (14.0, 14.8, 3.4), (14.8, 15.6, 4.0), (15.6, 16.4, 2.8),
                         (16.4, 20.5, (2.7, 4.0)), (20.5, 21.3, 4.2), (21.3, 22.3, 2.7), (22.3, 23.0, 1.9)]
    steps += ball(24.2, 1.4, 3, y_from=23.0)
    return felt(5.8) + profile(steps, c) + _ring(21.3, 22.8, 7.4, 8.6, 2.9, 4.1, c, 9)


def king(c) -> list[Box]:
    steps = base(6.2) + [(4.4, 15.0, (4.2, 2.4)), (15.0, 15.8, 3.6), (15.8, 16.6, 4.2), (16.6, 17.4, 3.0),
                         (17.4, 22.0, (2.8, 4.2)), (22.0, 23.0, 4.4), (23.0, 23.6, 3.2)]
    boxes = felt(6.0) + profile(steps, c)
    kw = dict(outline=None, gloss=False, decorate=_grain(11))
    boxes.append(Box((7.2, 23.6, 7.2), (8.8, 30.0, 8.8), c, **kw))
    boxes.append(Box((5.4, 26.4, 7.25), (10.6, 28.0, 8.75), c, **kw))
    return boxes


# Knight head in side view, looking left (north, -Z). Rows from the top (y = 21) down to the collar
# (y = 6); columns are z = 2 … 13. '#' is wood, 'm' the darker mane down the back.
KNIGHT = [
    ".....#..#...",
    ".....####m..",
    "....#####mm.",
    "...######mm.",
    "..#######mm.",
    ".########mm.",
    "#########mm.",
    "#########mmm",
    ".##..####mmm",
    ".....####mmm",
    "....#####mmm",
    "...######mmm",
    "...#######mm",
    "..########mm",
    "..#########m",
    "..##########",
]
KNIGHT_TOP = 21.0
KNIGHT_Z0 = 2.0


def knight_half_width(row: int) -> float:
    """Half the head's thickness: narrow at the ears and muzzle, wider down the neck to the chest."""
    return 1.8 + 1.7 * row / (len(KNIGHT) - 1)


def knight(c) -> list[Box]:
    steps = base(5.8) + [(4.4, 5.2, 4.2)]
    boxes = felt(5.6) + profile(steps, c)
    mane = darken(c, 0.22)
    for row, line in enumerate(KNIGHT):
        y1 = KNIGHT_TOP - row
        y0 = y1 - 1
        hw = knight_half_width(row)
        z = 0
        while z < len(line):
            ch = line[z]
            if ch == ".":
                z += 1
                continue
            end = z
            while end < len(line) and line[end] == ch:
                end += 1
            colour = mane if ch == "m" else c
            w = hw - (0.3 if ch == "m" else 0)
            boxes.append(Box((8 - w, y0, KNIGHT_Z0 + z), (8 + w, y1, KNIGHT_Z0 + end), colour,
                             outline=None, gloss=False, decorate=_grain(row)))
            z = end
    # Eyes (both cheeks) and the mouth line: dark plates just outside the wood.
    hw = knight_half_width(4) + 0.02
    for x, face in ((8 - hw, "west"), (8 + hw, "east")):
        boxes.append(Box((x, 17.2, 5.0), (x, 18.2, 6.0), EYE, faces=(face,), outline=None, gloss=False, shade=False))
    return boxes


SHAPES = {"pawn": pawn, "knight": knight, "bishop": bishop, "rook": rook, "queen": queen, "king": king}

DISPLAY = {
    "gui": {"rotation": [20, 225, 0], "translation": [0, -2.5, 0], "scale": [0.4, 0.4, 0.4]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.3, 0.3, 0.3]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, -4, 0], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.3, 0.3, 0.3]},
    "firstperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.3, 0.3, 0.3]},
}


def write_json(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main(out: Path) -> None:
    assets = out / "assets" / "dbsdw"
    for colour, wood in (("white", BOXWOOD), ("black", WALNUT)):
        for piece in PIECES:
            name = f"chess_{colour}_{piece}"
            model = texture = None
            for size in (128, 256, 512):
                try:
                    model, texture = build(SHAPES[piece](wood), f"dbsdw:item/{name}", size)
                    break
                except ValueError:
                    continue
            if model is None:
                raise SystemExit(f"{name}: too many faces for a 512 texture")
            model["display"] = DISPLAY
            model["gui_light"] = "front"
            write_json(assets / "models" / "item" / f"{name}.json", model)
            path = assets / "textures" / "item" / f"{name}.png"
            path.parent.mkdir(parents=True, exist_ok=True)
            texture.save(path, format="PNG", optimize=False, compress_level=9)
            write_json(assets / "items" / f"{name}.json",
                       {"model": {"type": "minecraft:model", "model": f"dbsdw:item/{name}"}})


if __name__ == "__main__":
    main(Path(sys.argv[1]))
