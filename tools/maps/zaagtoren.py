"""Zaagtoren: the Valvloer arena. An open timber tower in a forest clearing, an old sawmill's
drying loft: four round plank floors stacked seven blocks apart inside a ring of big log
columns, a crown roof on top, a mill pond underneath.

Valvloer (the game) drops every floor block someone stands on, so only the four floors may use the
floor woods (FLOOR_WOODS, the plugin's floor.blocks): mangrove at the bottom, then jungle, oak, birch
on top. The rim, columns and everything else are spruce and dark oak. An invisible barrier ring
just inside the rim keeps players on the floors; whoever drops below the lowest floor is out.

Two teams, spawns on the top floor, west (team 0) and east (team 1). Not rotated: the tower is
round and the same for both teams anyway.
"""
from __future__ import annotations

import math

import parts as P
from mcbuild import Canvas, Pal, fbm, h3, is_solid, parse

WORLD = "world"
SX, SY, SZ = 72, 58, 72
G = 8                       # ground surface in local y (world y -55)
ORIGIN = (500, -63, -50)    # east of Woudfront (x 300..449)
SEED = 77
MODES = ["valvloer:teams"]

CANVAS = Canvas(SX, SY, SZ, SEED)

CX = CZ = 36.0              # tower centre (between blocks 35 and 36)
LAYERS = [14, 21, 28, 35]   # floor y, bottom to top
FLOOR_R = 15.5              # floor: distance <= this
WALL_R = 16.9               # barrier ring: FLOOR_R < distance <= WALL_R, above each floor
RIM_R = 17.6                # rim beam under the barrier ring
COLUMN_R = 19.5
TOP = 46                    # columns end here; the crown roof sits on them

# floor woods by layer (bottom to top); the plugin's floor.blocks must list all of them
FLOOR_WOODS = [("mangrove_planks", "stripped_mangrove_wood[axis=y]"),
               ("jungle_planks", "stripped_jungle_wood[axis=y]"),
               ("oak_planks", "stripped_oak_wood[axis=y]"),
               ("birch_planks", "stripped_birch_wood[axis=y]")]
FLOOR_NAMES = {parse(s)[0] for pair in FLOOR_WOODS for s in pair}

_top = LAYERS[-1] + 1
SPAWNS = {0: [(CX - 9.5, _top, CZ - 4.5), (CX - 9.5, _top, CZ + 4.5), (CX - 11.5, _top, CZ + 0.5),
              (CX - 5.5, _top, CZ - 8.5), (CX - 5.5, _top, CZ + 8.5), (CX - 4.5, _top, CZ + 0.5)]}
SPAWNS[1] = [(2 * CX - p[0], p[1], 2 * CZ - p[2]) for p in SPAWNS[0]]
YAW = {0: -90.0, 1: 90.0}
CLOSEUPS = {"tower": (10, 10, 62, 62)}


def dist(x, z):
    return math.hypot(x + 0.5 - CX, z + 0.5 - CZ)


def angle(x, z):
    return math.atan2(z + 0.5 - CZ, x + 0.5 - CX)


# --------------------------------------------------------------------------- terrain


def height(x, z):
    d = dist(x, z)
    n = (fbm(x, z, 18, SEED) - 0.5) * 5
    flat = max(0.0, 1 - max(0.0, d - 23) / 8)
    edge = min(x, z, SX - 1 - x, SZ - 1 - z)
    rise = max(0, 6 - edge) * 0.7
    return G + round(n * (1 - flat) + rise)


def terrain(c: Canvas):
    grass = "grass_block[snowy=false]"
    yard = Pal(("coarse_dirt", 4), ("dirt_path", 2), ("gravel", 1), (grass, 2), seed=3, scale=2)
    forest = Pal((grass, 6), ("podzol[snowy=false]", 2), ("coarse_dirt", 1), ("moss_block", 1), seed=4, scale=3)
    bank = Pal(("gravel", 3), ("mud", 2), ("clay", 1), ("mossy_cobblestone", 1), seed=5, scale=2)
    for x in range(SX):
        for z in range(SZ):
            d = dist(x, z)
            h = height(x, z)
            if d < 12.5:                      # mill pond under the tower
                floor = G - 3 if d < 10 else G - 2
                c.column(x, z, 0, floor - 1, "stone")
                c.set(x, floor, z, Pal(("gravel", 3), ("clay", 1), ("sand", 1), seed=6))
                for y in range(floor + 1, G + 1):
                    c.set(x, y, z, "water")
                continue
            c.column(x, z, 0, h - 4, "stone")
            c.column(x, z, h - 3, h - 1, "dirt")
            if d < 14:
                top = bank
            elif d < 25:
                top = yard
            else:
                top = forest
            c.set(x, h, z, top)


# --------------------------------------------------------------------------- tower


def floor_block(i, x, z):
    planks, stripped = FLOOR_WOODS[i]
    d = dist(x, z)
    if i == 0:      # rings
        a = int(d / 2) % 2 == 0
    elif i == 1:    # diagonal boards
        a = ((x + z) // 3) % 2 == 0
    elif i == 2:    # 3x3 checker
        a = ((x // 3) + (z // 3)) % 2 == 0
    else:           # sixteen spokes, a compass on the top floor
        a = int((angle(x, z) + math.pi) / (2 * math.pi) * 16) % 2 == 0
    if h3(x, i, z, 90) < 0.08:
        a = not a
    return planks if a else stripped


def holes(i):
    """A few 2x2 gaps in the lower three floors, turned per floor so they never line up."""
    if i == len(LAYERS) - 1:
        return set()
    out = set()
    for k in range(5):
        a = (k / 5) * 2 * math.pi + i * 1.3
        r = 7 + 5 * h3(i, k, 0, 91)
        hx = int(CX + r * math.cos(a))
        hz = int(CZ + r * math.sin(a))
        out |= {(hx, hz), (hx + 1, hz), (hx, hz + 1), (hx + 1, hz + 1)}
    return out


def floors(c: Canvas):
    for i, y in enumerate(LAYERS):
        gaps = holes(i)
        for x in range(SX):
            for z in range(SZ):
                if dist(x, z) <= FLOOR_R and (x, z) not in gaps:
                    c.set(x, y, z, floor_block(i, x, z))


def rim_and_wall(c: Canvas):
    rim = Pal(("spruce_wood[axis=y]", 3), ("stripped_spruce_wood[axis=y]", 1), seed=20)
    for x in range(SX):
        for z in range(SZ):
            d = dist(x, z)
            if FLOOR_R < d <= RIM_R:
                for y in LAYERS:
                    c.set(x, y, z, rim)
                    # brackets under the outer edge of the rim, every few blocks
                    if d > RIM_R - 0.8 and int((angle(x, z) + math.pi) * 9) % 3 == 0:
                        c.set(x, y - 1, z, "dark_oak_planks")
            if FLOOR_R < d <= WALL_R:
                for i, y in enumerate(LAYERS):
                    y_end = LAYERS[i + 1] - 1 if i + 1 < len(LAYERS) else SY - 1
                    for yy in range(y + 1, y_end + 1):
                        c.set(x, yy, z, "barrier")
            # hanging lanterns under the rim, outside the barrier ring
            if WALL_R < d <= RIM_R and int((angle(x, z) + math.pi) * 12) % 8 == 0 and h3(x, 0, z, 21) < 0.5:
                for y in LAYERS:
                    if c.get(x, y - 1, z) == "air":
                        c.set(x, y - 1, z, "lantern[hanging=true]")


def columns(c: Canvas):
    logs = Pal(("dark_oak_log[axis=y]", 5), ("spruce_log[axis=y]", 1), seed=22)
    for k in range(8):
        a = (k + 0.5) * math.pi / 4
        px = int(round(CX + COLUMN_R * math.cos(a) - 1))
        pz = int(round(CZ + COLUMN_R * math.sin(a) - 1))
        foot = min(height(px + dx, pz + dz) for dx in (0, 1) for dz in (0, 1))
        for dx in (0, 1):
            for dz in (0, 1):
                c.column(px + dx, pz + dz, foot - 2, TOP, logs)
        # stone footing, wider than the column
        for dx in (-1, 0, 1, 2):
            for dz in (-1, 0, 1, 2):
                c.column(px + dx, pz + dz, foot - 2, foot + 1,
                         Pal(("cobblestone", 3), ("mossy_cobblestone", 2), ("stone_bricks", 1), seed=23))
        # beams from the column to the rim at every floor, with a knee brace below
        for y in LAYERS + [TOP - 2]:
            for t in range(0, 8):
                r = COLUMN_R - 1 - t * 0.5
                bx = int(CX + r * math.cos(a) - 0.5)
                bz = int(CZ + r * math.sin(a) - 0.5)
                if dist(bx, bz) > WALL_R:
                    c.set(bx, y, bz, "dark_oak_log[axis=y]" if t == 0 else "stripped_dark_oak_wood[axis=y]")
            ox = int(CX + (COLUMN_R - 2.5) * math.cos(a) - 0.5)
            oz = int(CZ + (COLUMN_R - 2.5) * math.sin(a) - 0.5)
            if dist(ox, oz) > WALL_R:
                c.set(ox, y - 1, oz, "dark_oak_fence")
                c.set(ox, y - 2, oz, "dark_oak_fence")
                c.set(ox, y - 3, oz, "lantern[hanging=true]")


def crown(c: Canvas):
    """A narrow ring roof (an eave) over the columns, and a hoist beam across the top with a bundle
    of logs hanging from it: the tower's silhouette from the forest."""
    outer = COLUMN_R + 1.5
    for x in range(SX):
        for z in range(SZ):
            d = dist(x, z)
            if COLUMN_R - 1.5 <= d <= COLUMN_R + 0.5:
                c.set(x, TOP, z, "stripped_spruce_wood[axis=y]")
            if not (RIM_R < d <= outer):
                continue
            y = TOP + 1 + int(outer - d)
            dx, dz = CX - (x + 0.5), CZ - (z + 0.5)
            facing = ("east" if dx > 0 else "west") if abs(dx) > abs(dz) else ("south" if dz > 0 else "north")
            c.set(x, y, z, Pal((f"dark_oak_stairs[facing={facing},half=bottom,shape=straight]", 5),
                               (f"spruce_stairs[facing={facing},half=bottom,shape=straight]", 1), seed=24))
            for yy in range(TOP + 1, y):
                c.set(x, yy, z, "dark_oak_planks")
    # hoist: two A-frame posts on the crown, a beam across, a chained bundle of logs over the middle
    beam_y = TOP + 8
    z0 = int(CZ)
    for side in (-1, 1):
        px = int(CX + side * (RIM_R + 0.5)) - (1 if side < 0 else 0)
        base = TOP + 1
        for k in range(beam_y - base):
            c.set(px, base + k, z0 - 1, "spruce_log[axis=y]")
            c.set(px, base + k, z0 + 1, "spruce_log[axis=y]")
        c.set(px, beam_y - 1, z0, "spruce_log[axis=z]")
    for x in range(int(CX - RIM_R - 1), int(CX + RIM_R + 1)):
        c.set(x, beam_y, z0, "dark_oak_log[axis=x]")
        c.set(x, beam_y, z0 - 1, "dark_oak_slab[type=bottom]")
        c.set(x, beam_y, z0 + 1, "dark_oak_slab[type=bottom]")
    bundle_y = LAYERS[-1] + 8
    for y in range(bundle_y + 2, beam_y):
        c.set(int(CX) - 2, y, z0, "iron_chain[axis=y]")
        c.set(int(CX) + 1, y, z0, "iron_chain[axis=y]")
    for x in range(int(CX) - 3, int(CX) + 3):
        for dy, dz in ((0, -1), (0, 0), (0, 1), (1, 0)):   # three logs below, one on top
            c.set(x, bundle_y + dy, z0 + dz, "spruce_log[axis=x]")


# --------------------------------------------------------------------------- yard and forest


def yard(c: Canvas):
    # log piles: horizontal stacks, three high, ends trimmed in a slope
    for (x, z, axis, n) in ((6, 10, "x", 7), (58, 8, "z", 6), (8, 56, "z", 7), (52, 60, "x", 8)):
        base = c.top_solid(x, z) + 1
        for row in range(3):
            for i in range(row, n - row):
                for w in range(3 - row):
                    px, pz = (x + i, z + w) if axis == "x" else (x + w, z + i)
                    c.set(px, base + row, pz, f"spruce_log[axis={axis}]")
    # the old saw bench: stonecutters under a lean-to roof
    sx, sz = 59, 32
    base = c.top_solid(sx, sz) + 1
    for i in range(5):
        c.set(sx, base, sz + i, "stripped_spruce_wood[axis=y]")
        if i in (1, 3):
            c.set(sx, base + 1, sz + i, "stonecutter[facing=west]")
    for (px, pz) in ((sx - 1, sz - 1), (sx - 1, sz + 5), (sx + 2, sz - 1), (sx + 2, sz + 5)):
        c.column(px, pz, base, base + 3, "spruce_log[axis=y]")
    for i in range(-1, 6):
        c.set(sx - 1, base + 4, sz + i, "spruce_slab[type=bottom]")
        c.set(sx, base + 4, sz + i, "spruce_slab[type=bottom]")
        c.set(sx + 1, base + 4, sz + i, "spruce_slab[type=top]")
        c.set(sx + 2, base + 4, sz + i, "spruce_slab[type=top]")
    for (x, z) in ((12, 34), (13, 35), (12, 36)):
        c.set(x, c.top_solid(x, z) + 1, z, "hay_block[axis=y]")
    P.crate_stack(c, 30, c.top_solid(30, 60) + 1, 60, w=2, d=1, h=2, seed=25)
    P.crate_stack(c, 42, c.top_solid(42, 11) + 1, 11, w=1, d=2, h=1, seed=26)
    for (x, z) in ((20, 50), (50, 20)):
        P.lamp_post(c, x, c.top_solid(x, z) + 1, z)


def forest(c: Canvas):
    step = 5
    for gx in range(0, SX, step):
        for gz in range(0, SZ, step):
            x = gx + int(h3(gx, 5, gz, 60) * step)
            z = gz + int(h3(gz, 6, gx, 61) * step)
            if not (0 <= x < SX and 0 <= z < SZ):
                continue
            d = dist(x, z)
            if d < 25:
                continue
            border = min(x, z, SX - 1 - x, SZ - 1 - z) < 5
            dens = 0.95 if border else 0.6
            if h3(x, 7, z, 62) > dens:
                continue
            if any(abs(x - px) < 6 and abs(z - pz) < 6 for px, pz in ((6, 10), (58, 8), (8, 56), (52, 60), (59, 32))):
                continue
            y = c.top_solid(x, z) + 1
            kind = h3(x, 9, z, 64)
            if kind < 0.45:
                P.spruce(c, x, y, z, height=9 + int(h3(x, 1, z, 65) * 6), seed=x + z)
            elif kind < 0.7:
                P.birch(c, x, y, z, height=6 + int(h3(x, 2, z, 66) * 3), seed=x + z)
            else:
                P.oak(c, x, y, z, height=5 + int(h3(x, 3, z, 67) * 3), r=2.4 + h3(x, 4, z, 68), seed=x + z)


def ground_cover(c: Canvas):
    deco = Pal(("short_grass", 30), ("fern", 6), ("dandelion", 1), ("oxeye_daisy", 1), seed=80)
    for x in range(SX):
        for z in range(SZ):
            y = c.top(x, z)
            s = c.get(x, y, z)
            if s.startswith("grass_block") and h3(x, 12, z, 81) < 0.35:
                c.set(x, y + 1, z, deco)
            elif s.startswith("podzol") and h3(x, 12, z, 82) < 0.2:
                c.set(x, y + 1, z, "fern")


def build():
    c = CANVAS
    c.b.clear()
    terrain(c)
    floors(c)
    rim_and_wall(c)
    columns(c)
    crown(c)
    yard(c)
    forest(c)
    ground_cover(c)
    c.drop_floating()
    c.fix_connections()


# --------------------------------------------------------------------------- checks


def report():
    """Valvloer checks: floor sizes, floor woods only in the floors, spawns on the top floor, and
    the barrier ring holds even when every floor block is gone."""
    c = CANVAS
    lines = []
    ok = True
    for i, y in enumerate(LAYERS):
        n = sum(1 for x in range(SX) for z in range(SZ) if parse(c.get(x, y, z))[0] in FLOOR_NAMES)
        lines.append(f"floor {i + 1} (y {y}, {FLOOR_WOODS[i][0].split('_')[0]}): {n} blocks")
    stray = [k for k, s in c.b.items() if parse(s)[0] in FLOOR_NAMES and (k[1] not in LAYERS or dist(k[0], k[2]) > FLOOR_R)]
    lines.append(f"floor woods outside the floors: {len(stray)} (must be 0)")
    ok &= not stray
    for t, lst in SPAWNS.items():
        on = all(parse(c.get(int(p[0]), int(p[1]) - 1, int(p[2])))[0] in FLOOR_NAMES for p in lst)
        lines.append(f"team {t} spawns on the top floor: {'yes' if on else 'NO'}")
        ok &= on

    # Flood the space a player could reach if every floor block had dropped: it must stay inside
    # the barrier ring until it falls below the lowest floor (where the player is out).
    def open_(x, y, z):
        s = c.b.get((x, y, z))
        return s is None or not is_solid(s) or parse(s)[0] in FLOOR_NAMES

    start = [(int(p[0]), int(p[1]), int(p[2])) for lst in SPAWNS.values() for p in lst]
    seen = set(start)
    todo = list(start)
    widest = 0.0
    leak = False
    while todo:
        x, y, z = todo.pop()
        widest = max(widest, dist(x, z))
        if x in (0, SX - 1) or z in (0, SZ - 1):
            leak = True
            break
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (x + dx, y + dy, z + dz)
            if n in seen or n[1] < LAYERS[0] or n[1] >= SY or not open_(*n):
                continue
            seen.add(n)
            todo.append(n)
    sealed = not leak and widest <= FLOOR_R + 0.8
    lines.append(f"barrier ring holds with all floors gone: {'yes' if sealed else 'NO'} "
                 f"(widest reach {widest:.1f}, floor radius {FLOOR_R})")
    ok &= sealed
    lines.append("VALVLOER OK" if ok else "VALVLOER CHECKS FAILED")
    return lines
