"""3D models (gun, flag, splat plane), sprite models and every item definition.

Run: python3 tools/pack_src/models.py <resourcepack dir>
Item definitions follow the asset table in docs/superpowers/plans/2026-11-18-plan-6-resource-pack.md.
Tints read custom_model_data colors[0]; the splat reads floats[0] (shape) and floats[1] (fade stage).
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

from PIL import Image

from boxmodel import Box, build
from palette import GOLD, OUTLINE, STEEL, WHITE, WOOD, YELLOW
from pixel import darken, lighten

TINT_GREY = (236, 236, 236)
DEFAULT_TINT = -1  # white: an untinted item shows the plain grey model

SPRITES = ["paintball", "powerup_rapid_fire", "powerup_triple_shot", "powerup_speed",
           "powerup_shield", "powerup_repair", "lobby_games", "lobby_stats", "lobby_survival"]
TINTED_SPRITES = {"paintball"}


def write_json(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def save_png(path: Path, img: Image.Image) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, format="PNG", optimize=False, compress_level=9)


# ---------------------------------------------------------------- gun
# Modelled on real speedball markers: long thin barrel, slim body, a big rounded loader (hopper)
# on a feed neck, a front foregrip, raked grip with trigger guard, and the air tank bottom-line,
# pointing back under the grip. The barrel points north (-Z); 16 units = one block.

BODY = (226, 230, 238)
BLACK = (44, 46, 58)
BARREL_BACK = (66, 70, 84)
TANK = (72, 78, 92)


def soft(colour):
    """Outline a little darker than the part itself: cartoon edges without the boxy black frame."""
    return darken(colour, 0.55)


def part(frm, to, colour, **kw) -> Box:
    kw.setdefault("outline", soft(TINT_GREY if kw.get("tint") else colour))
    return Box(frm, to, colour, **kw)


def rod(axis: str, centre: tuple[float, float], r: float, start: float, end: float, colour, **kw) -> list[Box]:
    """A round-ish rod: two crossed boxes give an octagon-like cross-section."""
    a, b = centre
    k = 0.62

    def box(ra: float, rb: float, s: float, e: float) -> Box:
        if axis == "z":
            return part((a - ra, b - rb, s), (a + ra, b + rb, e), colour, **kw)
        if axis == "y":
            return part((a - ra, s, b - rb), (a + ra, e, b + rb), colour, **kw)
        return part((s, a - ra, b - rb), (e, a + ra, b + rb), colour, **kw)

    # the second box is a hair shorter so the two end caps never share a plane
    return [box(r, r * k, start, end), box(r * k, r, start + 0.01, end - 0.01)]


def _barrel_ports(img: Image.Image, d: str) -> None:
    w, h = img.size
    if d in ("east", "west", "up") and w > 30:
        for x in range(4, min(w - 4, 26), 5):
            for y in range(h // 2 - 1, h // 2 + 1):
                img.putpixel((x, y), (18, 18, 24, 255))
                img.putpixel((x + 1, y), (18, 18, 24, 255))


def _muzzle(img: Image.Image, d: str) -> None:
    if d == "north":
        w, h = img.size
        for x in range(w // 2 - 1, w // 2 + 1):
            for y in range(h // 2 - 1, h // 2 + 1):
                img.putpixel((x, y), (16, 16, 22, 255))


def _grip_texture(img: Image.Image, d: str) -> None:
    w, h = img.size
    if d in ("east", "west"):
        for y in range(3, h - 3, 3):
            for x in range(2 + (y // 3) % 2, w - 2, 2):
                img.putpixel((x, y), lighten(BLACK, 0.22) + (255,))


def _tank_band(img: Image.Image, d: str) -> None:
    w, h = img.size
    if d in ("east", "west", "up", "down"):
        across = d in ("up", "down")
        length = h if across else w
        for i in range(length):
            if 0.35 * length < i < 0.5 * length:
                if across:
                    for x in range(w):
                        img.putpixel((x, i), YELLOW + (255,))
                else:
                    for y in range(h):
                        img.putpixel((i, y), YELLOW + (255,))


def _hopper_window(img: Image.Image, d: str) -> None:
    """Smoked window stripe along the loader sides, with a white shine on top."""
    w, h = img.size
    if d in ("east", "west"):
        for x in range(3, w - 3):
            for y in range(int(h * 0.42), int(h * 0.58)):
                img.putpixel((x, y), (52, 56, 70, 255))
        for x in range(4, w // 2):
            img.putpixel((x, 3), (255, 255, 255, 255))


def rounded(frm, to, inset: float, colour, **kw) -> list[Box]:
    """A box with its four vertical edges cut back by `inset`, seen from above a rounded rectangle."""
    (x0, y0, z0), (x1, y1, z1) = frm, to
    return [part((x0, y0, z0 + inset), (x1, y1, z1 - inset), colour, **kw),
            part((x0 + inset, y0 + 0.01, z0), (x1 - inset, y1 - 0.01, z1), colour, **kw)]


def gun_boxes() -> list[Box]:
    tint = dict(tint=True)
    hop = dict(tint=True, gloss=False, outline=darken(TINT_GREY, 0.3))
    return [
        # barrel: thin and long, a thicker threaded back part, team-coloured muzzle
        *rod("z", (8, 9.6), 0.75, -14.5, 0, BLACK, decorate=_barrel_ports),
        *rod("z", (8, 9.6), 1.05, -2.5, 0.4, BARREL_BACK),
        *rod("z", (8, 9.6), 0.95, -15.5, -14.2, TINT_GREY, decorate=_muzzle, **tint),
        # body: slim, polished, crowned top, black frame rail, team inlay, bolt cap at the back
        part((7, 8, 0), (9, 11.2, 9.5), BODY),
        part((7.25, 11.2, 1), (8.75, 11.8, 8.5), BODY, gloss=False),
        part((7.15, 7.4, 0.5), (8.85, 8, 9), BLACK, gloss=False),
        part((6.95, 9.3, 1.5), (9.05, 10.2, 7.5), TINT_GREY, faces=("east", "west"), gloss=False, **tint),
        *rod("z", (8, 9.6), 0.6, 9.5, 10.6, STEEL, gloss=False),
        # feed neck and collar
        *rod("y", (8, 4), 0.65, 11.8, 13.6, BLACK, gloss=False),
        part((7.1, 13.2, 3.1), (8.9, 13.8, 4.9), STEEL, gloss=False),
        # loader (hopper): an egg in the team colour (low nose, tall back), built from rounded layers,
        # with a smoked window and a black lid
        *rounded((6.6, 13.8, 0.5), (9.4, 14.4, 7.5), 0.6, TINT_GREY, **hop),
        *rounded((6, 14.4, -1.5), (10, 15.2, 9), 1, TINT_GREY, **hop),
        *rounded((5.5, 15.2, -2.5), (10.5, 18.2, 9.8), 1.2, TINT_GREY, decorate=_hopper_window, **hop),
        *rounded((6, 18.2, -1.5), (10, 19.2, 9.5), 1, TINT_GREY, **hop),
        *rounded((6.8, 19.2, 0), (9.2, 19.8, 8.5), 0.6, TINT_GREY, **hop),
        *rounded((6.3, 15.6, -3.2), (9.7, 17.6, -2.5), 0.8, TINT_GREY, **hop),
        *rounded((6.3, 15.6, 9.8), (9.7, 18.8, 10.4), 0.8, TINT_GREY, **hop),
        part((7.2, 19.8, 1.5), (8.8, 20.2, 6), BLACK, gloss=False),
        # foregrip (front, vertical), trigger guard and trigger
        *rod("y", (8, 1.15), 0.75, 3.5, 7.4, BLACK, decorate=_grip_texture),
        *rod("y", (8, 1.15), 0.55, 3, 3.5, STEEL, gloss=False),
        part((7.65, 4.6, 2.6), (8.35, 7.4, 3.2), BODY, gloss=False),
        part((7.65, 4.2, 2.6), (8.35, 4.8, 6.6), BODY, gloss=False),
        part((7.75, 5.2, 4.2), (8.25, 7.4, 4.7), STEEL, gloss=False),
        # raked grip
        part((7.05, 1.2, 6.3), (8.95, 7.4, 8.8), BLACK, decorate=_grip_texture,
             rotation={"angle": -22.5, "axis": "x", "origin": [8, 7.4, 7.5]}),
        # hose from the foregrip to the tank adapter
        part((7.85, 2.6, 1.1), (8.15, 3.0, 8), BLACK, gloss=False, outline=None),
        # air tank, bottom-line under the grip, pointing back, with a yellow band
        *rod("z", (8, 1.7), 0.6, 7.5, 10, STEEL, gloss=False),
        *rod("z", (8, 1.7), 1.6, 10, 21, TANK, decorate=_tank_band),
        *rod("z", (8, 1.7), 1.1, 21, 21.8, TANK, gloss=False),
    ]


GUN_DISPLAY = {
    # Values found by comparing variants in the real client (armor stands for third person).
    "firstperson_righthand": {"rotation": [4, -6, 0], "translation": [5, -1, -6], "scale": [0.55, 0.55, 0.55]},
    "firstperson_lefthand": {"rotation": [4, 6, 0], "translation": [5, -1, -6], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_righthand": {"rotation": [90, 0, 0], "translation": [0, 0, -5], "scale": [0.6, 0.6, 0.6]},
    "thirdperson_lefthand": {"rotation": [90, 0, 0], "translation": [0, 0, -5], "scale": [0.6, 0.6, 0.6]},
    "gui": {"rotation": [0, 90, -30], "translation": [0, 0, 0], "scale": [0.68, 0.68, 0.68]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.3, 0.3, 0.3]},
    "fixed": {"rotation": [0, 90, 0], "translation": [-1.5, 0, 0], "scale": [0.8, 0.8, 0.8]},
    "head": {"rotation": [0, 0, 0], "translation": [0, 10, 0], "scale": [0.6, 0.6, 0.6]},
}


# ---------------------------------------------------------------- flag

def _cloth(img: Image.Image, d: str) -> None:
    """Vertical light and dark bands suggest a cloth waving in the wind."""
    w, h = img.size
    if d not in ("north", "south"):
        return
    for x in range(w):
        band = (x // 6) % 3
        if band == 1:
            for y in range(h):
                r, g, b, a = img.getpixel((x, y))
                img.putpixel((x, y), (min(255, r + 14), min(255, g + 14), min(255, b + 14), a))
        elif band == 2:
            for y in range(h):
                r, g, b, a = img.getpixel((x, y))
                img.putpixel((x, y), (r - 30, g - 30, b - 30, a))
    # Swallowtail notch at the free end of the cloth (east, +X). The client shows the south face's
    # texture with east on the right and the north face's with east on the left.
    tip_right = d == "south"
    for y in range(h):
        depth = max(0, 8 - abs(y - h // 2) * 2)
        xs = range(w - depth, w) if tip_right else range(0, depth)
        for x in xs:
            img.putpixel((x, y), (0, 0, 0, 0))
        if depth:
            img.putpixel((w - depth - 1, y) if tip_right else (depth, y), OUTLINE + (255,))


def _emblem(img: Image.Image, d: str) -> None:
    """A white paint splat with a dark rim, on transparent background."""
    w, h = img.size
    img.paste((0, 0, 0, 0), (0, 0, w, h))
    from textures import splat_shape  # same splat as the decals, shape 1
    full, _ = splat_shape(1)
    m = full.resize((w, h), resample=Image.NEAREST)
    from pixel import dilate, subtract
    rim = subtract(dilate(m, 1), m)
    img.paste((255, 255, 255, 255), (0, 0), m)
    img.paste(OUTLINE + (255,), (0, 0), rim)


def flag_boxes() -> list[Box]:
    return [
        Box((7.25, 0, 7.25), (8.75, 29.5, 8.75), WOOD),
        Box((6.75, 29.5, 6.75), (9.25, 32, 9.25), GOLD),
        Box((8.75, 17.5, 7.6), (21.75, 28.5, 8.4), TINT_GREY, tint=True, decorate=_cloth, gloss=False,
            outline=None, shade=False),
        # emblem planes just outside both cloth faces
        Box((11, 19, 7.5), (19, 27, 7.5), WHITE, faces=("north",), decorate=_emblem, outline=None,
            gloss=False, shade=False),
        Box((11, 19, 8.5), (19, 27, 8.5), WHITE, faces=("south",), decorate=_emblem, outline=None,
            gloss=False, shade=False),
    ]


FLAG_DISPLAY = {
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    "head": {"rotation": [0, 0, 0], "translation": [-4, 6, 4], "scale": [0.8, 0.8, 0.8]},
    "gui": {"rotation": [0, 0, 0], "translation": [-4.5, -8, 0], "scale": [0.62, 0.62, 0.62]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, -2, 0], "scale": [0.5, 0.5, 0.5]},
    "firstperson_righthand": {"rotation": [0, 90, 0], "translation": [0, -2, 0], "scale": [0.5, 0.5, 0.5]},
}


# ---------------------------------------------------------------- item definitions

def model_ref(model: str, tinted: bool) -> dict:
    ref: dict = {"type": "minecraft:model", "model": model}
    if tinted:
        ref["tints"] = [{"type": "minecraft:custom_model_data", "index": 0, "default": DEFAULT_TINT}]
    return ref


def splat_definition() -> dict:
    def stage_dispatch(shape: int) -> dict:
        return {
            "type": "minecraft:range_dispatch",
            "property": "minecraft:custom_model_data",
            "index": 1,
            "entries": [{"threshold": s, "model": model_ref(f"dbsdw:item/splat_{shape}_{s}", True)}
                        for s in (1, 2, 3)],
            "fallback": model_ref(f"dbsdw:item/splat_{shape}_0", True),
        }

    return {"model": {
        "type": "minecraft:range_dispatch",
        "property": "minecraft:custom_model_data",
        "index": 0,
        "entries": [{"threshold": s, "model": stage_dispatch(s)} for s in (1, 2)],
        "fallback": stage_dispatch(0),
    }}


def main(out: Path) -> None:
    assets = out / "assets" / "dbsdw"
    models, items, tex = assets / "models" / "item", assets / "items", assets / "textures" / "item"

    for name, boxes, display, size in (("gun", gun_boxes(), GUN_DISPLAY, 256),
                                       ("flag", flag_boxes(), FLAG_DISPLAY, 256)):
        model, texture = build(boxes, f"dbsdw:item/{name}", size)
        model["display"] = display
        model["gui_light"] = "front"
        write_json(models / f"{name}.json", model)
        save_png(tex / f"{name}.png", texture)
        write_json(items / f"{name}.json", {"model": model_ref(f"dbsdw:item/{name}", True)})

    # Splat: a flat square centred on the model origin, facing up; brightness comes from the display.
    write_json(models / "splat_base.json", {
        "textures": {"particle": "#splat"},
        "elements": [{"from": [0, 8, 0], "to": [16, 8, 16], "shade": False,
                      "faces": {"up": {"uv": [0, 0, 16, 16], "texture": "#splat", "tintindex": 0}}}],
    })
    for shape in range(3):
        for stage in range(4):
            write_json(models / f"splat_{shape}_{stage}.json", {
                "parent": "dbsdw:item/splat_base",
                "textures": {"splat": f"dbsdw:item/splat_{shape}_{stage}"}})
    write_json(items / "splat.json", splat_definition())

    for name in SPRITES:
        write_json(models / f"{name}.json", {"parent": "minecraft:item/generated",
                                             "textures": {"layer0": f"dbsdw:item/{name}"}})
        write_json(items / f"{name}.json", {"model": model_ref(f"dbsdw:item/{name}", name in TINTED_SPRITES)})


if __name__ == "__main__":
    main(Path(sys.argv[1]))
