"""Props of the bomb mode and the shop: the paint bomb, the two grenades, the defuse kit (3D box
models) and the sprite icons of the vest, the boots and the two cartridges.

Run: python3 tools/pack_src/props.py <resourcepack dir>
Same look as the gun: army gear in olive drab, khaki and gunmetal with soft cartoon outlines; the
paint stays bright. The 3D props sit on the bottom of their block (y = 0) so a planted bomb shown by
an item display lifted half a block stands on the ground.
"""

from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image

from boxmodel import Box, build
from models import model_ref, part, rod, save_png, write_json
from palette import (CYAN, GOLD, GREEN, GUNMETAL, GUNMETAL_DARK, KHAKI, KHAKI_DARK, MAGENTA, OLIVE, OLIVE_DARK,
                     OLIVE_LIGHT, ORANGE, OUTLINE, RED, SAND, STEEL, WHITE, YELLOW)
from pixel import Canvas, darken, ellipse, lighten, mask, polygon, rect, subtract

SCREEN = (0x1E, 0x3A, 0x22)
DIGIT = (0x7C, 0xFF, 0x6A)


# ---------------------------------------------------------------- face details

def _strap(img: Image.Image, d: str) -> None:
    """Stencilled warning stripes along the bomb crate sides."""
    w, h = img.size
    if d in ("north", "south") and w > 12:
        for x in range(2, w - 2):
            for y in range(h - 5, h - 2):
                if ((x + y) // 3) % 2 == 0:
                    img.putpixel((x, y), YELLOW + (255,))


def _screen(img: Image.Image, d: str) -> None:
    """A green timer read-out with 88:88 on the top face of the timer box."""
    if d != "up":
        return
    w, h = img.size
    for x in range(2, w - 2):
        for y in range(2, h - 2):
            img.putpixel((x, y), SCREEN + (255,))
    # little seven-segment-ish digits: four columns of bars
    cols = [3 + i * max(2, (w - 6) // 4) for i in range(4)]
    for i, cx in enumerate(cols):
        for y in (3, h // 2, h - 4):
            for x in range(cx, min(cx + 2, w - 3)):
                if 0 <= y < h:
                    img.putpixel((x, y), DIGIT + (255,))
        if i == 1 and cx + 3 < w - 2:
            img.putpixel((cx + 3, h // 2 - 1), DIGIT + (255,))
            img.putpixel((cx + 3, h // 2 + 1), DIGIT + (255,))


def _keypad(img: Image.Image, d: str) -> None:
    """Rows of light keys on the front of the timer box."""
    if d != "south":
        return
    w, h = img.size
    for x in range(2, w - 2, 3):
        for y in range(2, h - 2, 3):
            img.putpixel((x, y), lighten(GUNMETAL, 0.5) + (255,))


def _label(colour):
    """A paint canister's label: a white band with a colour splash."""
    def draw(img: Image.Image, d: str) -> None:
        w, h = img.size
        if d in ("up", "down", "east", "west") and w > 6:
            across = w > h
            n = w if across else h
            for i in range(int(n * 0.3), int(n * 0.7)):
                for j in range(1, (h if across else w) - 1):
                    xy = (i, j) if across else (j, i)
                    img.putpixel(xy, SAND + (255,))
            mid = n // 2
            for i in range(mid - 1, mid + 2):
                for j in range(2, (h if across else w) - 2):
                    xy = (i, j) if across else (j, i)
                    img.putpixel(xy, colour + (255,))
    return draw


def _band(colour):
    def draw(img: Image.Image, d: str) -> None:
        w, h = img.size
        if d in ("north", "south", "east", "west"):
            for x in range(w):
                for y in range(int(h * 0.4), int(h * 0.62)):
                    img.putpixel((x, y), colour + (255,))
    return draw


def _cross(img: Image.Image, d: str) -> None:
    """A white cross-and-wrench patch on the front and top of the defuse kit."""
    w, h = img.size
    if d not in ("south", "up") or w < 10:
        return
    cx, cy = w // 2, h // 2
    for i in range(-3, 4):
        for t in (-1, 0):
            img.putpixel((cx + i, cy + t), WHITE + (255,))
            img.putpixel((cx + t, cy + i), WHITE + (255,))


# ---------------------------------------------------------------- 3D props

def bomb_boxes() -> list[Box]:
    """The paint bomb: an olive crate strapped with khaki bands, three paint canisters on top, a timer
    box with a keypad and a green read-out, a red light, an aerial and coloured wires."""
    return [
        part((2, 0, 4), (14, 0.75, 12), GUNMETAL_DARK, gloss=False),
        part((2.5, 0.75, 4.5), (13.5, 4, 11.5), OLIVE, decorate=_strap),
        part((5, 0.5, 4.25), (6, 4.25, 11.75), KHAKI_DARK, gloss=False),
        part((10, 0.5, 4.25), (11, 4.25, 11.75), KHAKI_DARK, gloss=False),
        # paint canisters lying along z on the left of the top
        *rod("z", (4.0, 5.25), 1.25, 5, 11, MAGENTA, decorate=_label(MAGENTA)),
        *rod("z", (6.75, 5.25), 1.25, 5, 11, ORANGE, decorate=_label(ORANGE)),
        *rod("z", (4.0, 5.25), 0.5, 4.5, 5, STEEL, gloss=False),
        *rod("z", (6.75, 5.25), 0.5, 4.5, 5, STEEL, gloss=False),
        # timer box
        part((8.5, 4, 5.5), (13, 6.5, 10.5), GUNMETAL, decorate=_screen),
        part((8.75, 4.25, 10.5), (12.75, 6.25, 10.75), GUNMETAL_DARK, decorate=_keypad, gloss=False),
        # red light and aerial
        part((12, 6.5, 6), (12.75, 7.25, 6.75), RED, gloss=False),
        part((9.25, 6.5, 6), (9.75, 10.5, 6.5), GUNMETAL_DARK, gloss=False, outline=None),
        part((9, 10.5, 5.75), (10, 11.25, 6.75), RED, gloss=False),
        # wires from the canisters to the timer
        part((7.75, 4.25, 7.0), (8.5, 4.75, 7.5), RED, gloss=False, outline=None),
        part((7.75, 4.25, 8.5), (8.5, 4.75, 9.0), CYAN, gloss=False, outline=None),
        part((7.75, 4.25, 9.75), (8.5, 4.75, 10.25), YELLOW, gloss=False, outline=None),
    ]


def paint_grenade_boxes() -> list[Box]:
    """A round paint grenade: olive shell with a bright paint band, gunmetal fuse, khaki lever, steel pin."""
    return [
        part((5, 0, 5.5), (11, 6, 10.5), OLIVE, decorate=_band(RED)),
        part((5.5, 0, 5), (10.5, 6, 11), OLIVE, shrink=(0, 0.01, 0), decorate=_band(RED)),
        part((5.5, -0.01, 5.5), (10.5, 6.25, 10.5), OLIVE_LIGHT, shrink=(0, 0.02, 0), gloss=False),
        part((7, 6, 7), (9, 7.5, 9), GUNMETAL),
        part((8.75, 5, 7.5), (9.75, 7.25, 8.5), KHAKI, gloss=False),
        part((6, 6.75, 7.75), (7, 7.75, 8.25), STEEL, gloss=False),
        part((5.25, 7.75, 7.75), (6.25, 8.5, 8.25), STEEL, gloss=False),
    ]


def smoke_grenade_boxes() -> list[Box]:
    """A smoke can: steel-grey tube with a white band, holes in the gunmetal cap, khaki lever."""
    return [
        *rod("y", (8, 8), 2.25, 0, 7.5, STEEL, decorate=_band(WHITE)),
        *rod("y", (8, 8), 1.75, 7.5, 8.5, GUNMETAL),
        part((10, 4.5, 7.5), (11, 8.25, 8.5), KHAKI, gloss=False),
        part((6.5, 8.5, 7.75), (7.5, 9.25, 8.25), STEEL, gloss=False),
    ]


def defuse_kit_boxes() -> list[Box]:
    """A yellow tool case with a white cross, gunmetal latches and a carrying handle."""
    return [
        part((3, 0, 5), (13, 4.5, 11), GOLD, decorate=_cross),
        part((2.75, 4.25, 4.75), (13.25, 4.75, 11.25), darken(GOLD, 0.25), gloss=False),
        part((4.5, 2.5, 10.9), (5.5, 4, 11.25), GUNMETAL_DARK, gloss=False),
        part((10.5, 2.5, 10.9), (11.5, 4, 11.25), GUNMETAL_DARK, gloss=False),
        part((6, 4.75, 7.5), (6.75, 6.5, 8.5), GUNMETAL, gloss=False),
        part((9.25, 4.75, 7.5), (10, 6.5, 8.5), GUNMETAL, gloss=False),
        part((6, 6.5, 7.5), (10, 7.25, 8.5), GUNMETAL, gloss=False),
    ]


def display(gui_scale: float, hand_scale: float) -> dict:
    return {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 3, 0], "scale": [gui_scale] * 3},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
        "head": {"rotation": [0, 0, 0], "translation": [0, 13, 0], "scale": [0.6, 0.6, 0.6]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 1], "scale": [hand_scale * 0.6] * 3},
        "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 1], "scale": [hand_scale * 0.6] * 3},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 6, -2], "scale": [hand_scale] * 3},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 6, -2], "scale": [hand_scale] * 3},
    }


PROPS = {
    "bomb": (bomb_boxes, display(0.85, 0.6), 256),
    "paint_grenade": (paint_grenade_boxes, display(1.1, 0.7), 128),
    "smoke_grenade": (smoke_grenade_boxes, display(1.0, 0.7), 128),
    "defuse_kit": (defuse_kit_boxes, display(0.9, 0.6), 128),
}


# ---------------------------------------------------------------- sprites (32 x 32)

def vest() -> Canvas:
    """An olive paint vest with khaki pockets and a gold shield patch."""
    c = Canvas(32)
    body = polygon(mask((32, 32)), [(8, 5), (12, 5), (14, 9), (18, 9), (20, 5), (24, 5), (27, 10), (26, 28),
                                    (6, 28), (5, 10)])
    c.shaded(body, OLIVE, light=0.3, shade=0.3, outline=OUTLINE)
    c.fill(rect(mask((32, 32)), (15, 9, 16, 27)), OLIVE_DARK)
    for x0 in (8, 18):
        pocket = rect(mask((32, 32)), (x0, 18, x0 + 5, 24), radius=1)
        c.shaded(pocket, KHAKI, light=0.25, shade=0.25, outline=darken(KHAKI, 0.5))
    patch = polygon(mask((32, 32)), [(21, 10), (25, 11), (24.5, 14), (21, 16), (17.5, 14), (17, 11)])
    c.shaded(patch, GOLD, light=0.3, shade=0.2, outline=OUTLINE)
    return c


def boots() -> Canvas:
    """A cyan-striped army boot with a white speed streak."""
    c = Canvas(32)
    boot = polygon(mask((32, 32)), [(10, 5), (19, 5), (19, 18), (27, 21), (28, 26), (8, 26), (9, 16)])
    c.shaded(boot, KHAKI_DARK, light=0.25, shade=0.3, outline=OUTLINE)
    sole = rect(mask((32, 32)), (8, 25, 28, 28), radius=1)
    c.shaded(sole, (0x3A, 0x30, 0x24), light=0.15, shade=0.2, outline=OUTLINE)
    c.fill(rect(mask((32, 32)), (10, 9, 18, 11)), CYAN)
    for y in (13, 16):
        c.fill(rect(mask((32, 32)), (12, y, 17, y)), SAND)
    for i, y in enumerate((8, 13, 18)):
        c.fill(rect(mask((32, 32)), (1 + i, y, 7, y + 1)), WHITE)
    return c


def cartridge(colour, icon: str) -> Canvas:
    """A paint cartridge: a steel tube with a coloured paint window and a power-up mark."""
    c = Canvas(32)
    tube = rect(mask((32, 32)), (10, 4, 22, 28), radius=3)
    c.shaded(tube, STEEL, light=0.35, shade=0.3, outline=OUTLINE)
    cap = rect(mask((32, 32)), (11, 2, 21, 6), radius=1)
    c.shaded(cap, GUNMETAL, light=0.3, shade=0.2, outline=OUTLINE)
    window = rect(mask((32, 32)), (12, 9, 20, 25), radius=2)
    c.shaded(window, colour, light=0.35, shade=0.3)
    mark = mask((32, 32))
    if icon == "bolt":
        polygon(mark, [(17, 11), (13, 18), (16, 18), (14, 23), (19, 16), (16, 16), (18, 11)])
    else:
        for cy in (12, 17, 22):
            ellipse(mark, (14.5, cy - 1.5, 17.5, cy + 1.5))
    c.shaded(subtract(mark, mask((32, 32))), WHITE, light=0.0, shade=0.15, outline=darken(colour, 0.5))
    return c


SPRITES = {
    "shop_vest": vest,
    "shop_boots": boots,
    "shop_rapid_cartridge": lambda: cartridge(ORANGE, "bolt"),
    "shop_triple_cartridge": lambda: cartridge(MAGENTA, "triple"),
}


def main(out: Path) -> None:
    assets = out / "assets" / "dbsdw"
    models, items, tex = assets / "models" / "item", assets / "items", assets / "textures" / "item"
    for name, (boxes, disp, size) in PROPS.items():
        model, texture = build(boxes(), f"dbsdw:item/{name}", size)
        model["display"] = disp
        model["gui_light"] = "side"
        write_json(models / f"{name}.json", model)
        save_png(tex / f"{name}.png", texture)
        write_json(items / f"{name}.json", {"model": model_ref(f"dbsdw:item/{name}", False)})
    for name, draw in SPRITES.items():
        draw().save(tex / f"{name}.png")
        write_json(models / f"{name}.json", {"parent": "minecraft:item/generated",
                                             "textures": {"layer0": f"dbsdw:item/{name}"}})
        write_json(items / f"{name}.json", {"model": model_ref(f"dbsdw:item/{name}", False)})


if __name__ == "__main__":
    main(Path(sys.argv[1]))
