"""Flat item sprites: paintball, splats, power-ups, lobby items and pack.png.

Run: python3 tools/pack_src/textures.py <resourcepack dir>
Sprites that are tinted in game (paintball, splats) are drawn in greys: the client multiplies them
with the team colour from the item's custom_model_data.
"""

from __future__ import annotations

import math
import random
import sys
from pathlib import Path

from palette import BLUE, CYAN, GOLD, GREEN, MAGENTA, ORANGE, OUTLINE, RED, WHITE, YELLOW
from pixel import (Canvas, blob, darken, dilate, ellipse, erode, intersect, lighten, mask, polygon,
                   rect, shift, subtract, union)

GREY_BASE = (228, 228, 228)
GREY_SHADE = (178, 178, 178)

# Alpha per splat fade stage (0 = fresh). Matches SplatFade in the Paintball plugin.
FADE_ALPHA = (255, 175, 105, 45)


def paintball() -> Canvas:
    c = Canvas(16)
    ball = ellipse(mask((16, 16)), (3, 3, 12, 12))
    c.shaded(ball, GREY_BASE, light=0.6, shade=0.25, outline=OUTLINE)
    c.px(6, 5, WHITE).px(5, 6, WHITE).px(6, 6, WHITE)
    return c


def splat_shape(shape: int) -> tuple:
    """Mask of splat shape 0..2 plus its inner highlight, fixed seeds per shape.

    A wobbly core, arms that taper outwards and end in a round drop, and a few loose drops.
    """
    rng = random.Random(1000 + shape)
    size = 32
    c = 15.5
    core = blob(rng, size, c, c, 7.2 + shape * 0.5, bumps=5, wobble=0.45)
    parts = [core]
    arms = 6 + shape
    base_angle = rng.uniform(0, math.tau)
    for i in range(arms):
        a = base_angle + math.tau * i / arms + rng.uniform(-0.35, 0.35)
        length = rng.uniform(9.5, 13.5)
        w0 = rng.uniform(2.2, 3.4)
        tip_r = rng.uniform(1.1, 2.0)
        tx, ty = c + length * math.cos(a), c + length * math.sin(a)
        nx, ny = -math.sin(a), math.cos(a)
        parts.append(polygon(mask((size, size)), [
            (c + nx * w0, c + ny * w0), (c - nx * w0, c - ny * w0),
            (tx - nx * tip_r * 0.6, ty - ny * tip_r * 0.6), (tx + nx * tip_r * 0.6, ty + ny * tip_r * 0.6)]))
        parts.append(ellipse(mask((size, size)), (tx - tip_r, ty - tip_r, tx + tip_r, ty + tip_r)))
    for _ in range(3 + shape):
        a = rng.uniform(0, math.tau)
        d = rng.uniform(12.5, 14.8)
        r = rng.uniform(0.6, 1.3)
        x, y = c + d * math.cos(a), c + d * math.sin(a)
        parts.append(ellipse(mask((size, size)), (x - r, y - r, x + r, y + r)))
    full = union(*parts)
    shine = ellipse(mask((size, size)), (c - 5, c - 5, c - 1, c - 2))
    shine = intersect(shine, erode(core, 2))
    return full, shine


def splat(shape: int, stage: int) -> Canvas:
    full, shine = splat_shape(shape)
    a = FADE_ALPHA[stage]
    c = Canvas(32)
    c.fill(full, GREY_BASE, a)
    c.fill(subtract(full, shift(full, -1, -1)), GREY_SHADE, a)
    c.fill(shine, WHITE, a)
    return c


def badge(colour, icon_mask, icon_colour=WHITE) -> Canvas:
    """A round glossy badge in the power-up colour with a white icon and dark outlines."""
    c = Canvas(32)
    disc = ellipse(mask((32, 32)), (2, 2, 29, 29))
    c.shaded(disc, colour, light=0.35, shade=0.35, outline=OUTLINE)
    ring = subtract(disc, erode(disc, 2))
    c.fill(ring, darken(colour, 0.25))
    gloss = intersect(ellipse(mask((32, 32)), (7, 5, 22, 13)), erode(disc, 3))
    c.fill(gloss, lighten(colour, 0.35))
    c.shaded(icon_mask, icon_colour, light=0.0, shade=0.12, outline=OUTLINE)
    return c


def icon_bolt() -> object:
    return polygon(mask((32, 32)), [(18, 6), (9, 18), (15, 18), (12, 26), (23, 13), (17, 13), (20, 6)])


def icon_triple() -> object:
    m = mask((32, 32))
    for cx, cy in ((10, 20), (16, 12), (22, 20)):
        ellipse(m, (cx - 3.5, cy - 3.5, cx + 3.5, cy + 3.5))
    return m


def icon_speed() -> object:
    m = mask((32, 32))
    for x0 in (8, 16):
        polygon(m, [(x0, 9), (x0 + 4, 9), (x0 + 10, 16), (x0 + 4, 23), (x0, 23), (x0 + 6, 16)])
    return m


def icon_shield() -> object:
    return polygon(mask((32, 32)), [(16, 6), (24, 9), (23, 18), (16, 26), (9, 18), (8, 9)])


def icon_plus() -> object:
    m = mask((32, 32))
    rect(m, (13, 7, 18, 24), radius=1)
    rect(m, (7, 13, 24, 18), radius=1)
    return m


POWERUPS = {
    "rapid_fire": (ORANGE, icon_bolt),
    "triple_shot": (MAGENTA, icon_triple),
    "speed": (CYAN, icon_speed),
    "shield": (GOLD, icon_shield),
    "repair": (GREEN, icon_plus),
}


def powerup(name: str) -> Canvas:
    colour, icon = POWERUPS[name]
    c = badge(colour, icon())
    if name == "shield":
        # A gold stripe down the middle of the shield.
        inner = intersect(erode(icon_shield(), 2), rect(mask((32, 32)), (15, 0, 16, 31)))
        c.fill(inner, GOLD)
    return c


def gun_side(c: Canvas, ox: int, oy: int, hopper=BLUE) -> None:
    """Side view of the paintball marker for 2D icons, nose to the right (matches the 3D model)."""
    s = (32, 32)
    body_c, black, tank_c = (226, 230, 238), (44, 46, 58), (72, 78, 92)
    barrel = rect(mask(s), (ox + 17, oy + 13, ox + 29, oy + 14))
    muzzle = rect(mask(s), (ox + 29, oy + 12, ox + 30, oy + 15))
    body = rect(mask(s), (ox + 7, oy + 11, ox + 17, oy + 16), radius=1)
    hopper_m = union(ellipse(mask(s), (ox + 4, oy + 2, ox + 17, oy + 10)),
                     ellipse(mask(s), (ox + 3, oy + 3, ox + 12, oy + 10)))
    neck = rect(mask(s), (ox + 9, oy + 9, ox + 11, oy + 11))
    grip = polygon(mask(s), [(ox + 8, oy + 16), (ox + 11, oy + 16), (ox + 9, oy + 22), (ox + 6, oy + 22)])
    fore = rect(mask(s), (ox + 15, oy + 16, ox + 16, oy + 20))
    guard = subtract(rect(mask(s), (ox + 11, oy + 16, ox + 15, oy + 19), radius=1),
                     rect(mask(s), (ox + 12, oy + 16, ox + 14, oy + 18)))
    tank = rect(mask(s), (ox + 0, oy + 22, ox + 9, oy + 25), radius=1)
    whole = union(barrel, muzzle, body, hopper_m, neck, grip, fore, guard, tank)
    c.outline(whole, OUTLINE)
    c.fill(barrel, black)
    c.fill(muzzle, hopper)
    c.shaded(tank, tank_c, light=0.3)
    c.fill(rect(mask(s), (ox + 4, oy + 22, ox + 5, oy + 25)), YELLOW)
    c.shaded(body, body_c, light=0.4, shade=0.25)
    c.fill(rect(mask(s), (ox + 9, oy + 13, ox + 15, oy + 13)), hopper)
    c.fill(union(grip, fore, guard, neck), black)
    c.shaded(hopper_m, hopper, light=0.45, shade=0.3)
    c.fill(rect(mask(s), (ox + 5, oy + 6, ox + 15, oy + 6)), darken(hopper, 0.45))
    c.fill(rect(mask(s), (ox + 7, oy + 3, ox + 9, oy + 3)), WHITE)


def lobby_games() -> Canvas:
    c = Canvas(32)
    rng = random.Random(7)
    b = blob(rng, 32, 22, 21, 9, bumps=5, wobble=0.5)
    c.fill(b, BLUE)
    c.fill(subtract(b, shift(b, -1, -1)), darken(BLUE, 0.3))
    gun_side(c, 0, 2, hopper=RED)
    return c


def lobby_stats() -> Canvas:
    c = Canvas(32)
    s = (32, 32)
    cup = union(polygon(mask(s), [(8, 4), (24, 4), (22, 15), (19, 18), (13, 18), (10, 15)]),
                ellipse(mask(s), (8, 2, 24, 7)))
    handles = union(subtract(ellipse(mask(s), (3, 5, 12, 14)), ellipse(mask(s), (5, 7, 10, 12))),
                    subtract(ellipse(mask(s), (20, 5, 29, 14)), ellipse(mask(s), (22, 7, 27, 12))))
    stem = rect(mask(s), (14, 18, 18, 23))
    base = rect(mask(s), (9, 23, 23, 28), radius=1)
    whole = union(cup, handles, stem, base)
    c.outline(whole, OUTLINE)
    c.shaded(handles, darken(GOLD, 0.1))
    c.shaded(stem, darken(GOLD, 0.15))
    c.shaded(cup, GOLD, light=0.55, shade=0.3)
    c.shaded(base, (90, 60, 40), light=0.3)
    c.fill(rect(mask(s), (13, 24, 19, 26)), lighten(GOLD, 0.2))
    c.fill(rect(mask(s), (11, 6, 12, 12)), WHITE)
    # A star on the cup.
    star = polygon(mask(s), [(16, 7), (17, 10), (20, 10), (17.5, 12), (18.5, 15), (16, 13),
                             (13.5, 15), (14.5, 12), (12, 10), (15, 10)])
    c.fill(star, WHITE)
    return c


def lobby_survival() -> Canvas:
    """A grass block seen from the corner, with a curved green 'go back' arrow."""
    c = Canvas(32)
    s = (32, 32)
    top = polygon(mask(s), [(14, 3), (25, 8), (14, 13), (3, 8)])
    left = polygon(mask(s), [(3, 8), (14, 13), (14, 27), (3, 22)])
    right = polygon(mask(s), [(14, 13), (25, 8), (25, 22), (14, 27)])
    whole = union(top, left, right)
    c.outline(whole, OUTLINE)
    c.fill(top, (110, 200, 70))
    c.fill(left, (150, 100, 60))
    c.fill(right, (120, 80, 48))
    grass_l = intersect(left, rect(mask(s), (0, 0, 31, 15)))
    grass_r = intersect(right, rect(mask(s), (0, 0, 31, 15)))
    c.fill(grass_l, (90, 170, 55))
    c.fill(grass_r, (75, 145, 45))
    rng = random.Random(3)
    for _ in range(14):
        x, y = rng.randint(4, 24), rng.randint(15, 25)
        if left.getpixel((x, y)) or right.getpixel((x, y)):
            c.px(x, y, (95, 62, 38))
    # Arrow pointing back (left), bottom right corner.
    arrow = union(polygon(mask(s), [(16, 24), (22, 19), (22, 22), (29, 22), (29, 26), (22, 26), (22, 29)]))
    c.shaded(arrow, GREEN, light=0.4, outline=OUTLINE)
    return c


def pack_icon() -> Canvas:
    c = Canvas(128)
    s = (128, 128)
    bg = rect(mask(s), (2, 2, 125, 125), radius=18)
    c.fill(bg, (32, 36, 64))
    rng = random.Random(42)
    for colour, (x, y, r) in ((RED, (44, 46, 30)), (BLUE, (84, 82, 30)), (YELLOW, (92, 34, 12))):
        b = intersect(blob(rng, 128, x, y, r, bumps=7, wobble=0.6), erode(bg, 3))
        c.fill(b, colour)
        c.fill(subtract(b, shift(b, -2, -2)), darken(colour, 0.3))
    c.outline(bg, OUTLINE, thick=2)
    gun = Canvas(32)
    gun_side(gun, 0, 3)
    big = gun.img.resize((96, 96), resample=0)
    c.img.alpha_composite(big, (16, 22))
    return c


def main(out: Path) -> None:
    tex = out / "assets" / "dbsdw" / "textures" / "item"
    paintball().save(tex / "paintball.png")
    for shape in range(3):
        for stage in range(4):
            splat(shape, stage).save(tex / f"splat_{shape}_{stage}.png")
    for name in POWERUPS:
        powerup(name).save(tex / f"powerup_{name}.png")
    lobby_games().save(tex / "lobby_games.png")
    lobby_stats().save(tex / "lobby_stats.png")
    lobby_survival().save(tex / "lobby_survival.png")
    pack_icon().save(out / "pack.png")


if __name__ == "__main__":
    main(Path(sys.argv[1]))
