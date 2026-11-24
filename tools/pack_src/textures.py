"""Flat item sprites: paintball, splats, paint drips, power-ups, lobby items and pack.png.

Run: python3 tools/pack_src/textures.py <resourcepack dir>
Sprites that are tinted in game (paintball, splats) are drawn in greys: the client multiplies them
with the team colour from the item's custom_model_data.
"""

from __future__ import annotations

import math
import random
import sys

from PIL import ImageChops
from pathlib import Path

from palette import (BLUE, BROWN, CYAN, GOLD, GREEN, GUNMETAL, GUNMETAL_DARK, KHAKI, KHAKI_DARK, MAGENTA, OLIVE, OLIVE_DARK,
                     OLIVE_LIGHT, ORANGE, OUTLINE, RED, SAND, WHITE, YELLOW)
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


DRIP_VARIANTS = 3


def drip_body_shape(variant: int) -> tuple:
    """Mask of the trail a running drop leaves, top (at the splat) to bottom (at the drop).

    Real paint runs are fed from the splat, so the trail is widest at the top and thins towards
    the drop; it wanders a little with the wall's roughness and has a lump here and there where
    the drop stalled. The plugin stretches it lengthwise to the length of the run.
    """
    rng = random.Random(2000 + variant)
    size = 32
    m = mask((size, size))
    phase = rng.uniform(0, math.tau)
    waves = rng.uniform(0.7, 1.3)
    drift = rng.uniform(-1.0, 1.0)
    lumps = [(rng.uniform(6, 26), rng.uniform(0.8, 1.4)) for _ in range(1 + variant % 2)]
    for y in range(size):
        t = y / (size - 1)
        cx = 15.5 + 1.4 * math.sin(phase + t * math.tau * waves) * (0.3 + 0.7 * t) + drift * t
        half = 4.6 - 2.2 * t
        for ly, extra in lumps:
            half += extra * math.exp(-((y - ly) / 1.6) ** 2)
        for x in range(size):
            if abs(x + 0.5 - cx) <= half:
                m.putpixel((x, y), 255)
    return m


def drip_body(variant: int, stage: int) -> Canvas:
    """The trail, lit on its left edge and shaded on its right; the first rows thin out so it
    starts softly under the splat."""
    a = FADE_ALPHA[stage]
    m = drip_body_shape(variant)
    c = Canvas(32)
    c.fill(m, GREY_BASE, a)
    c.fill(subtract(m, shift(m, -1, 0)), GREY_SHADE, a)
    c.fill(subtract(m, shift(m, 1, 0)), WHITE, a)
    for y in range(3):
        for x in range(32):
            if m.getpixel((x, y)):
                r, g, b, _ = c.img.getpixel((x, y))
                c.img.putpixel((x, y), (r, g, b, round(a * (0.35 + 0.22 * y))))
    return c


def drip_head_shape(variant: int) -> tuple:
    """The drop at the end of a run, point up: a thin neck from the trail that swells into a heavy,
    slightly lopsided bulb (gravity pulls the paint into the bottom), with a highlight."""
    rng = random.Random(3000 + variant)
    size = 32
    bw = rng.uniform(5.6, 6.8)          # half width of the bulb
    bh = rng.uniform(6.2, 7.4)          # half height of the bulb
    lean = rng.uniform(-1.0, 1.0)
    cx, cy = 15.5 + lean, 31 - bh - 0.5
    m = mask((size, size))
    for y in range(size):
        for x in range(size):
            dx, dy = (x + 0.5 - cx) / bw, (y + 0.5 - cy) / bh
            # a bulb a little flatter on top than at the bottom
            if dy < 0:
                dx *= 1 + 0.25 * -dy
            if dx * dx + dy * dy <= 1:
                m.putpixel((x, y), 255)
    neck_bottom = cy - bh * 0.4
    for y in range(0, int(neck_bottom) + 1):
        t = y / max(1.0, neck_bottom)
        ncx = 15.5 + lean * t
        half = 2.4 + (bw * 0.75 - 2.4) * t ** 2.2
        for x in range(size):
            if abs(x + 0.5 - ncx) <= half:
                m.putpixel((x, y), 255)
    shine = intersect(ellipse(mask((size, size)), (cx - bw * 0.62, cy - bh * 0.55, cx - bw * 0.12, cy - bh * 0.05)),
                      erode(m, 2))
    return m, shine


def drip_head(variant: int, stage: int) -> Canvas:
    m, shine = drip_head_shape(variant)
    a = FADE_ALPHA[stage]
    c = Canvas(32)
    c.fill(m, GREY_BASE, a)
    c.fill(subtract(m, shift(m, -1, -1)), GREY_SHADE, a)
    c.fill(shine, WHITE, a)
    return c


def badge(colour, icon_mask, icon_colour=WHITE) -> Canvas:
    """A round army patch: olive drab cloth, a rim in the power-up colour, khaki stitching and the
    icon in white. The coloured rim keeps each power-up recognisable from a distance."""
    c = Canvas(32)
    disc = ellipse(mask((32, 32)), (2, 2, 29, 29))
    c.shaded(disc, OLIVE, light=0.25, shade=0.3, outline=OUTLINE)
    rim = subtract(disc, erode(disc, 3))
    c.fill(rim, colour)
    c.fill(subtract(rim, shift(rim, -1, -1)), darken(colour, 0.3))
    # stitching: a dashed khaki ring just inside the rim
    stitch = subtract(erode(disc, 4), erode(disc, 5))
    for y in range(32):
        for x in range(32):
            if stitch.getpixel((x, y)) and (x + y) % 3 == 0:
                c.px(x, y, SAND)
    c.shaded(icon_mask, icon_colour, light=0.0, shade=0.15, outline=OUTLINE)
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
    body_c, black, tank_c = OLIVE, GUNMETAL_DARK, KHAKI
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
    """A game controller in army olive with bright buttons: every game, not just paintball."""
    c = Canvas(32)
    s = (32, 32)
    # Body: a wide rounded bar with two handles hanging down.
    bar = rect(mask(s), (3, 9, 28, 20), radius=4)
    left = ellipse(mask(s), (2, 12, 13, 27))
    right = ellipse(mask(s), (18, 12, 29, 27))
    body = union(bar, left, right)
    c.outline(body, OUTLINE)
    c.shaded(body, OLIVE, light=0.45, shade=0.35)
    # Shoulder buttons along the top edge.
    for x0 in (5, 21):
        bump = rect(mask(s), (x0, 7, x0 + 5, 8), radius=0)
        c.fill(bump, GUNMETAL_DARK)
    # D-pad on the left.
    dpad = union(rect(mask(s), (6, 13, 12, 15)), rect(mask(s), (8, 11, 10, 17)))
    c.fill(dpad, OUTLINE)
    c.fill(rect(mask(s), (8, 11, 10, 11)), GUNMETAL).fill(rect(mask(s), (6, 13, 6, 15)), GUNMETAL)
    # Four buttons on the right, in the paint colours.
    for (x, y), colour in (((22, 11), YELLOW), ((19, 14), BLUE), ((25, 14), RED), ((22, 17), GREEN)):
        b = ellipse(mask(s), (x - 0.5, y - 0.5, x + 2.5, y + 2.5))
        c.fill(dilate(b, 1, diagonal=False), OLIVE_DARK)
        c.shaded(b, colour, light=0.5, shade=0.3)
    # Two small grey sticks in the middle.
    for x0 in (13, 17):
        c.fill(rect(mask(s), (x0, 19, x0 + 1, 20)), GUNMETAL_DARK)
    return c


def lobby_stats() -> Canvas:
    """A medal on a striped ribbon (army honours instead of a sports cup)."""
    c = Canvas(32)
    s = (32, 32)
    ribbon = polygon(mask(s), [(9, 1), (23, 1), (23, 12), (16, 16), (9, 12)])
    disc = ellipse(mask(s), (7, 12, 25, 30))
    c.outline(union(ribbon, disc), OUTLINE)
    c.fill(ribbon, OLIVE)
    for x0 in (11, 19):
        c.fill(intersect(rect(mask(s), (x0, 0, x0 + 1, 15)), ribbon), RED)
    c.fill(intersect(rect(mask(s), (15, 0, 16, 15)), ribbon), SAND)
    c.shaded(disc, GOLD, light=0.55, shade=0.35)
    c.fill(subtract(erode(disc, 2), erode(disc, 3)), darken(GOLD, 0.3))
    star = polygon(mask(s), [(16, 15), (17.6, 19), (22, 19.2), (18.6, 22), (19.8, 26.4), (16, 23.8),
                             (12.2, 26.4), (13.4, 22), (10, 19.2), (14.4, 19)])
    c.shaded(star, lighten(GOLD, 0.5), light=0.0, shade=0.2)
    c.px(11, 17, WHITE).px(12, 16, WHITE)
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


def camo(c: Canvas, area, rng: random.Random, size: int, blobs: int = 14) -> None:
    """Woodland camo blobs (dark olive, brown, light olive) inside `area`."""
    for i in range(blobs):
        colour = (OLIVE_DARK, BROWN, OLIVE_LIGHT)[i % 3]
        x, y = rng.uniform(0, size), rng.uniform(0, size)
        b = intersect(blob(rng, size, x, y, rng.uniform(size * 0.07, size * 0.14), bumps=5, wobble=0.6), area)
        c.fill(b, colour)


def pack_icon() -> Canvas:
    c = Canvas(128)
    s = (128, 128)
    bg = rect(mask(s), (2, 2, 125, 125), radius=18)
    c.fill(bg, OLIVE)
    camo(c, bg, random.Random(11), 128)
    rng = random.Random(42)
    for colour, (x, y, r) in ((BLUE, (44, 46, 30)), (RED, (84, 82, 30)), (YELLOW, (92, 34, 12))):
        b = intersect(blob(rng, 128, x, y, r, bumps=7, wobble=0.6), erode(bg, 3))
        c.fill(b, colour)
        c.fill(subtract(b, shift(b, -2, -2)), darken(colour, 0.3))
    c.outline(bg, OUTLINE, thick=2)
    gun = Canvas(32)
    gun_side(gun, 0, 3, hopper=RED)
    big = gun.img.resize((96, 96), resample=0)
    c.img.alpha_composite(big, (16, 22))
    return c


def scope_overlay() -> Canvas:
    """Camera overlay while aiming. The client stretches it over the whole screen, so the round view
    is drawn as an ellipse 9/16 as wide as it is tall: round again on a 16:9 screen."""
    w = h = 256
    c = Canvas(w, h)
    cx, cy, ry = 127.5, 127.5, 118
    rx = ry * 9 / 16
    view = ellipse(mask((w, h)), (cx - rx, cy - ry, cx + rx, cy + ry))
    c.fill(ImageChops.invert(view), (12, 14, 8), 235)
    rim = subtract(view, ellipse(mask((w, h)), (cx - rx + 2, cy - ry + 3, cx + rx - 2, cy + ry - 3)))
    c.fill(rim, OLIVE_DARK)
    # thin crosshair with a gap in the middle, and mil-dots on the horizontal line
    line = (20, 22, 16)
    for y in range(int(cy - ry), int(cy + ry) + 1):
        if abs(y - cy) > 7 and view.getpixel((128, y)):
            c.px(127, y, line, 200).px(128, y, line, 200)
    for x in range(int(cx - rx), int(cx + rx) + 1):
        if abs(x - cx) > 4 and view.getpixel((x, 128)):
            c.px(x, 127, line, 200).px(x, 128, line, 200)
    for k in (-3, -2, -1, 1, 2, 3):
        x = round(cx + k * 10)
        c.px(x, 126, line, 200).px(x, 129, line, 200)
    c.px(127, 127, RED).px(128, 127, RED).px(127, 128, RED).px(128, 128, RED)
    return c


def splash_overlay(colour, fade: bool) -> Canvas:
    """Paint on the goggles, worn as a camera overlay: the client stretches it over the whole screen,
    so the splats reach the edges on any screen. Same art as the old title glyph, in team colour."""
    from glyphs import overlay
    art = overlay()
    c = Canvas(art.img.width, art.img.height)
    for y in range(art.img.height):
        for x in range(art.img.width):
            r, g, b, a = art.img.getpixel((x, y))
            if a:
                k = r / 255
                c.px(x, y, tuple(round(v * k) for v in colour), round(a * (0.45 if fade else 1)))
    return c


def main(out: Path) -> None:
    tex = out / "assets" / "dbsdw" / "textures" / "item"
    paintball().save(tex / "paintball.png")
    for shape in range(3):
        for stage in range(4):
            splat(shape, stage).save(tex / f"splat_{shape}_{stage}.png")
    for variant in range(DRIP_VARIANTS):
        for stage in range(4):
            drip_body(variant, stage).save(tex / f"drip_body_{variant}_{stage}.png")
            drip_head(variant, stage).save(tex / f"drip_head_{variant}_{stage}.png")
    for name in POWERUPS:
        powerup(name).save(tex / f"powerup_{name}.png")
    lobby_games().save(tex / "lobby_games.png")
    lobby_stats().save(tex / "lobby_stats.png")
    lobby_survival().save(tex / "lobby_survival.png")
    pack_icon().save(out / "pack.png")
    scope_overlay().save(out / "assets" / "dbsdw" / "textures" / "misc" / "scope.png")
    for name, colour in (("red", RED), ("blue", BLUE)):
        for fade in (False, True):
            splash_overlay(colour, fade).save(
                out / "assets" / "dbsdw" / "textures" / "misc" / f"splash_{name}{'_fade' if fade else ''}.png")


if __name__ == "__main__":
    main(Path(sys.argv[1]))
