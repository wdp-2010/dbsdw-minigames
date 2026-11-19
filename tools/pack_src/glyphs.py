"""Font glyphs: HUD icons, the hit splash overlay and the chest-menu background.

Run: python3 tools/pack_src/glyphs.py <resourcepack dir>
Code points are the contract with messages.yml (see the plan's glyph table). White glyphs are
coloured by the text colour; full-colour icons are used with <white>.
"""

from __future__ import annotations

import json
import math
import random
import sys
from pathlib import Path

from palette import BLUE, CYAN, GOLD, GREEN, MAGENTA, ORANGE, OUTLINE, RED, WHITE, YELLOW
from pixel import Canvas, blob, darken, ellipse, erode, intersect, lighten, mask, polygon, rect, shift, subtract, union

# --------------------------------------------------------------- HUD icons (9 x 9)
S9 = (9, 9)


def drop() -> Canvas:
    c = Canvas(9)
    m = union(polygon(mask(S9), [(4, 0), (6.5, 4), (1.5, 4)]), ellipse(mask(S9), (1, 2.5, 7, 8)))
    c.fill(m, WHITE)
    c.fill(subtract(m, shift(m, -1, -1)), (205, 205, 205))
    c.px(3, 4, (255, 255, 255))
    c.outline(m, OUTLINE)
    return c


def grid(art: str, colour) -> Canvas:
    """9 x 9 icon from ASCII art: o outline, w white, c colour, d dark colour, l light colour."""
    rows = [r.strip() for r in art.strip("\n").split("\n")]
    pal = {"o": OUTLINE, "w": WHITE, "c": colour, "d": darken(colour, 0.3), "l": lighten(colour, 0.4)}
    c = Canvas(9)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in pal:
                c.px(x, y, pal[ch])
    return c


ICONS = {
    "rapid_fire": (ORANGE, """
        ..ooooo..
        .occcwco.
        occcwwcdo
        occwwccdo
        ocwwwwwdo
        occcwwcdo
        ocdcwcddo
        .odwddddo
        ..ooooo..
    """),
    "triple_shot": (MAGENTA, """
        ..ooooo..
        .occccco.
        occcwwcdo
        occcwwcdo
        ocwwccwwo
        ocwwddwwo
        ocddddddo
        .oddddddo
        ..ooooo..
    """),
    "speed": (CYAN, """
        ..ooooo..
        .occccco.
        ocwwcwwdo
        occwwcwwo
        occcwwcwo
        occwwcwwo
        ocwwdwwdo
        .oddddddo
        ..ooooo..
    """),
    "repair": (GREEN, """
        ..ooooo..
        .occccco.
        occcwccdo
        occcwccdo
        ocwwwwwdo
        occcwccdo
        ocdcwcddo
        .oddddddo
        ..ooooo..
    """),
}


def shield() -> Canvas:
    c = Canvas(9)
    m = polygon(mask(S9), [(4, 0), (8, 1), (7.5, 5), (4, 8.5), (0.5, 5), (0, 1)])
    c.fill(m, GOLD)
    c.fill(subtract(m, shift(m, -1, -1)), darken(GOLD, 0.3))
    c.fill(rect(mask(S9), (4, 1, 4, 7)), WHITE)
    return c


def hit_marker() -> Canvas:
    c = Canvas(9)
    m = mask(S9)
    for i in range(9):
        if i not in (3, 4, 5):
            m.putpixel((i, i), 255)
            m.putpixel((8 - i, i), 255)
    c.fill(m, WHITE)
    return c


def bubble() -> Canvas:
    c = Canvas(9)
    ring = subtract(ellipse(mask(S9), (0, 0, 8, 8)), ellipse(mask(S9), (1.5, 1.5, 6.5, 6.5)))
    c.fill(ellipse(mask(S9), (1, 1, 7, 7)), CYAN, 90)
    c.fill(ring, CYAN)
    c.px(3, 2, WHITE).px(2, 3, WHITE)
    return c


def team_splat() -> Canvas:
    c = Canvas(9)
    rng = random.Random(9)
    m = union(blob(rng, 9, 4, 4.5, 3.2, bumps=4, wobble=0.5), ellipse(mask(S9), (7, 0, 8.5, 1.5)))
    c.fill(m, WHITE)
    c.fill(subtract(m, shift(m, -1, -1)), (200, 200, 200))
    return c


# --------------------------------------------------------------- hit splash overlay (256 x 144)

def overlay() -> Canvas:
    """Paint on the goggles: splats and drips around the screen edges, the centre stays clear."""
    w, h = 256, 144
    c = Canvas(w, h)
    rng = random.Random(77)
    m = mask((w, h))
    top = (256 - h) // 2  # blobs are drawn on a 256 x 256 square, then the middle band is kept
    spots = [(-6, 10, 34), (30, -8, 26), (250, 6, 30), (262, 70, 30), (220, 150, 34), (10, 140, 32),
             (128, -14, 18), (120, 160, 16), (-10, 80, 20), (190, -10, 20), (70, 150, 20)]
    for x, y, r in spots:
        r = r * 0.85
        b = blob(rng, 256, x, y + top, r, bumps=7, wobble=0.6).crop((0, top, w, top + h))
        m = union(m, b)
        for _ in range(5):
            a = rng.uniform(0, math.tau)
            d = r + rng.uniform(4, 16)
            rr = rng.uniform(1.5, 4)
            px, py = x + d * math.cos(a), y + d * math.sin(a)
            ellipse(m, (px - rr, py - rr, px + rr, py + rr))
    # drips running down from the top splats
    for x0 in (14, 40, 238, 118, 196):
        length = rng.randint(14, 34)
        rect(m, (x0, 0, x0 + 3, length), radius=1)
        ellipse(m, (x0 - 1, length - 2, x0 + 4, length + 3))
    c.fill(m, WHITE, 170)
    c.fill(subtract(m, erode(m, 2)), (225, 225, 225), 205)
    c.fill(subtract(erode(m, 4), shift(erode(m, 4), 3, 3)), WHITE, 215)
    return c


# --------------------------------------------------------------- chest-menu background (176 x 168)

# Slot frames (top-left corners) of a 3-row chest screen: chest rows, inventory rows, hotbar.
SLOT_ROWS = (17, 35, 53, 84, 102, 120, 142)


def menu_background() -> Canvas:
    """The whole 3-row chest screen: a navy top with paint splashes for the menu, a light lower
    part where the client's dark "Inventory" label stays readable. Slot interiors are transparent,
    so the vanilla slots and the items in them show through whatever order the client draws in."""
    w, h = 176, 168
    c = Canvas(w, h)
    navy, light = (34, 38, 70), (206, 211, 228)
    panel = rect(mask((w, h)), (0, 0, w - 1, h - 1), radius=4)
    top = intersect(panel, rect(mask((w, h)), (0, 0, w - 1, 71)))
    c.fill(panel, light)
    c.fill(top, navy)
    c.fill(rect(mask((w, h)), (1, 72, w - 2, 72)), darken(light, 0.25))
    header = rect(mask((w, h)), (3, 3, w - 4, 14), radius=2)
    c.fill(header, (48, 54, 98))
    rng = random.Random(5)
    for colour, x in ((RED, 128), (BLUE, 146), (YELLOW, 162)):
        b = intersect(blob(rng, w, x, 6, 6.5, bumps=5, wobble=0.5).crop((0, 0, w, h)), panel)
        c.fill(b, colour)
        c.fill(subtract(b, shift(b, -1, -1)), darken(colour, 0.3))
    for x0, length, colour in ((132, 20, RED), (151, 14, BLUE), (164, 24, YELLOW)):
        c.fill(union(rect(mask((w, h)), (x0, 10, x0 + 2, length), radius=1),
                     ellipse(mask((w, h)), (x0 - 1, length - 2, x0 + 3, length + 2))), colour)
    # a few paint drops on the light part
    for colour, (x, y, r) in ((RED, (166, 160, 3)), (BLUE, (160, 163, 2)), (YELLOW, (10, 162, 2))):
        c.fill(intersect(ellipse(mask((w, h)), (x - r, y - r, x + r, y + r)), panel), colour)
    for y in SLOT_ROWS:
        dark = y < 72
        frame_c = (22, 24, 46) if dark else darken(light, 0.45)
        rim_c = lighten(navy, 0.25) if dark else WHITE
        for col in range(9):
            x = 7 + 18 * col
            c.fill(rect(mask((w, h)), (x, y, x + 17, y + 17)), frame_c)
            c.fill(rect(mask((w, h)), (x + 1, y + 17, x + 17, y + 17)), rim_c)
            c.fill(rect(mask((w, h)), (x + 17, y + 1, x + 17, y + 17)), rim_c)
            c.img.paste((0, 0, 0, 0), (x + 1, y + 1, x + 17, y + 17))
    c.fill(subtract(panel, erode(panel, 1)), OUTLINE)
    return c


# --------------------------------------------------------------- fonts

HUD = [  # (char, file, ascent, height)
    ("", "drop", 8, 9), ("", "shield", 8, 9), ("", "rapid_fire", 8, 9),
    ("", "triple_shot", 8, 9), ("", "speed", 8, 9), ("", "repair", 8, 9),
    ("", "hit_marker", 8, 9), ("", "protected", 8, 9), ("", "team", 8, 9),
]
OVERLAY_ASCENT, OVERLAY_HEIGHT = 45, 90
MENU_ASCENT, MENU_HEIGHT = 13, 168


def bitmap(file: str, ascent: int, height: int, char: str) -> dict:
    return {"type": "bitmap", "file": f"dbsdw:font/{file}.png", "ascent": ascent, "height": height,
            "chars": [char]}


def main(out: Path) -> None:
    base = out / "assets" / "dbsdw"
    tex = base / "textures" / "font"
    drop().save(tex / "drop.png")
    shield().save(tex / "shield.png")
    for name, (colour, art) in ICONS.items():
        grid(art, colour).save(tex / f"{name}.png")
    hit_marker().save(tex / "hit_marker.png")
    bubble().save(tex / "protected.png")
    team_splat().save(tex / "team.png")
    overlay().save(tex / "overlay.png")
    menu_background().save(tex / "menu.png")

    def write(name: str, providers: list[dict]) -> None:
        path = base / "font" / f"{name}.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps({"providers": providers}, indent=2) + "\n", encoding="utf-8")

    # The space keeps its normal width, so messages can put spaces between icons in this font.
    write("hud", [bitmap(f, a, h, ch) for ch, f, a, h in HUD] + [{"type": "space", "advances": {" ": 4}}])
    write("overlay", [bitmap("overlay", OVERLAY_ASCENT, OVERLAY_HEIGHT, "")])
    write("menu", [bitmap("menu", MENU_ASCENT, MENU_HEIGHT, ""),
                   {"type": "space", "advances": {"": -8, "": -169}}])


if __name__ == "__main__":
    main(Path(sys.argv[1]))
