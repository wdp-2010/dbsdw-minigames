"""Houtkamp: the Verstoppertje arena. A lumber camp in a forest clearing, full of the blocks the
hiders can turn into (Verstoppertje's `disguises`): barrels, hay bales, spruce and oak logs,
crafting tables, composters, moss blocks and pumpkins.

  middle   the camp fire square where the hiders start
  west     the sawmill: an open timber hall with workbenches, log racks and barrels
  north    the log yard: stacks, stumps and a timber cart
  east     a creek with a footbridge, a pumpkin garden and a cabin
  south    the stable corner with hay, the forester's hut (seekers wait there) and a cabin
  around   forest, moss mounds and stumps, then a barrier at the edge

Asymmetric on purpose (hide and seek, not two teams fighting): no rotation.
"""
from __future__ import annotations

import math

import analyze
import parts as P
from basiskamp import hall
from mcbuild import Canvas, Pal, fbm, h3, parse
from woudfront import poly_dist, rect_dist

WORLD = "world"
SX, SY, SZ = 84, 36, 84
G = 8
ORIGIN = (500, -63, 40)     # south of Zaagtoren (z -50..21)
SEED = 63
MODES = ["verstoppertje:klassiek"]

CANVAS = Canvas(SX, SY, SZ, SEED)

SQUARE = (42, 42)                       # camp fire square centre
SAWMILL = (12, 30, 30, 46)              # open hall x1, z1, x2, z2
YARD = (34, 8, 60, 24)                  # log yard
GARDEN = (64, 16, 76, 30)               # pumpkin garden
STABLE = (30, 60, 46, 74)               # hay corner
HUT = (12, 58, 19, 65)                  # forester's hut: seekers wait here
CABINS = [(66, 58, 73, 65), (40, 30, 46, 35)]
PATHS = [
    [(42, 42), (31, 39)],               # -> sawmill
    [(42, 42), (46, 24)],               # -> yard
    [(42, 42), (58, 41), (66, 40)],     # -> bridge -> garden/cabin
    [(42, 42), (38, 60)],               # -> stable
    [(38, 60), (22, 62)],               # -> hut
    [(66, 40), (70, 30), (70, 56)],     # east path along the creek
]
DISGUISES = ["barrel", "hay_block", "spruce_log", "oak_log", "crafting_table", "composter", "moss_block", "pumpkin"]

SPAWNS = {0: [(42.5 + 3.5 * math.cos(a), G + 1, 42.5 + 3.5 * math.sin(a)) for a in
              (k * math.pi / 4 for k in range(8))],
          1: [(14.5, G + 1, 60.5), (16.5, G + 1, 62.5), (14.5, G + 1, 63.5)]}
SPAWNS[0] = [(round(x - 0.5) + 0.5, y, round(z - 0.5) + 0.5) for x, y, z in SPAWNS[0]]
YAW = {0: 0.0, 1: -90.0}
CLOSEUPS = {"sawmill": (8, 26, 34, 50), "middle": (30, 28, 56, 52), "south": (8, 54, 50, 78)}


CREEK_Z = (9, 73)            # the creek runs between two ponds, never to the edge of the map


def creek_x(z):
    return 61 + 2.5 * math.sin(z / 8.0)


def creek_d(x, z):
    """Distance to the water's middle line: the creek between the ponds, or a pond's centre."""
    z1, z2 = CREEK_Z
    along = abs(x - creek_x(z)) if z1 <= z <= z2 else 99
    pond = min(math.dist((x, z), (creek_x(z1), z1)), math.dist((x, z), (creek_x(z2), z2))) - 1.3
    return min(along, pond)


def built():
    return [SAWMILL, YARD, GARDEN, STABLE, HUT] + CABINS


# --------------------------------------------------------------------------- terrain


def height(x, z):
    n = (fbm(x, z, 22, SEED) - 0.5) * 5
    flat = 1 - min(1, math.dist((x, z), SQUARE) / 10)
    for r in built():
        flat = max(flat, 1 - min(1, rect_dist(x, z, r) / 4))
    flat = max(flat, 1 - min(1, creek_d(x, z) / 6))
    edge = min(x, z, SX - 1 - x, SZ - 1 - z)
    rise = max(0, 7 - edge) * 0.6
    return G + round(n * (1 - flat) + rise)


def terrain(c: Canvas):
    grass = "grass_block[snowy=false]"
    path = Pal(("dirt_path", 5), ("coarse_dirt", 2), ("gravel", 1), seed=3)
    sawdust = Pal(("coarse_dirt", 3), ("rooted_dirt", 2), ("dirt_path", 1), seed=4, scale=2)
    forest = Pal((grass, 6), ("podzol[snowy=false]", 2), ("coarse_dirt", 1), seed=5, scale=3)
    bank = Pal(("gravel", 3), ("mud", 2), ("clay", 1), ("coarse_dirt", 2), seed=6, scale=2)
    for x in range(SX):
        for z in range(SZ):
            cd = creek_d(x, z)
            h = height(x, z)
            if cd < 2.2:
                floor = G - 2
                c.column(x, z, 0, floor - 1, "stone")
                c.set(x, floor, z, Pal(("gravel", 3), ("sand", 1), ("clay", 1), seed=7))
                c.set(x, G - 1, z, "water")
                if h3(x, 0, z, 8) < 0.12:
                    c.set(x, G - 1, z, Pal("mossy_cobblestone", "cobblestone", seed=9))
                continue
            top = forest
            if cd < 4:
                h = G - 1 if cd < 3 else G
                top = bank
            elif any(poly_dist(x, z, p) < 1.3 + 0.5 * h3(x, 1, z, 10) for p in PATHS):
                top = path
            elif rect_dist(x, z, SAWMILL) < 3 or rect_dist(x, z, YARD) < 2 or math.dist((x, z), SQUARE) < 7:
                top = sawdust
            elif h3(x, 2, z, 11) < 0.6:
                top = grass
            c.column(x, z, 0, h - 4, "stone")
            c.column(x, z, h - 3, h - 1, "dirt")
            c.set(x, h, z, top)


def on_ground(c: Canvas, x, z):
    return c.top_solid(x, z) + 1


# --------------------------------------------------------------------------- the camp


def square(c: Canvas):
    """Fire pit in a ring of logs to sit on; barrels and a crafting table by the cook's spot."""
    cx, cz = SQUARE
    y = G + 1
    c.set(cx, G, cz, "cobblestone")
    c.set(cx, y, cz, "campfire[lit=true,signal_fire=false,facing=north,waterlogged=false]")
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
        c.set(cx + dx, G, cz + dz, Pal("cobblestone", "mossy_cobblestone", "stone", seed=12))
    # seat logs, each 3 long, on four sides
    for (sx, sz, axis) in ((cx - 1, cz - 5, "x"), (cx - 1, cz + 5, "x"), (cx - 5, cz - 1, "z"), (cx + 5, cz - 1, "z")):
        for i in range(3):
            px, pz = (sx + i, sz) if axis == "x" else (sx, sz + i)
            c.set(px, y, pz, f"oak_log[axis={axis}]")
    for (x, z) in ((cx + 6, cz + 4), (cx + 7, cz + 4), (cx + 6, cz + 5)):
        c.set(x, y, z, "barrel[facing=up,open=false]")
    c.set(cx - 6, y, cz + 5, "crafting_table")
    c.set(cx - 7, y, cz - 5, "composter[level=3]")
    P.lamp_post(c, cx + 6, y, cz - 6)


def sawmill(c: Canvas):
    """Open timber hall: log posts, a gable roof, no walls. Workbenches and racks inside."""
    x1, z1, x2, z2 = SAWMILL
    h = G
    floor = Pal(("spruce_planks", 3), ("coarse_dirt", 1), ("stripped_spruce_wood[axis=y]", 1), seed=13)
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            c.set(x, h, z, floor)
            for y in range(h + 1, h + 11):
                c.set(x, y, z, "air")
    for x in range(x1, x2 + 1, 6):
        for z in (z1, z2):
            c.column(x, z, h - 1, h + 5, "dark_oak_log[axis=y]")
    for x in range(x1, x2 + 1):
        for z in (z1, z2):
            c.set(x, h + 5, z, "spruce_log[axis=x]")
    for z in range(z1, z2 + 1):
        for x in (x1, x2):
            if (z - z1) % 8 == 0:
                c.column(x, z, h - 1, h + 5, "dark_oak_log[axis=y]")
            c.set(x, h + 5, z, "spruce_log[axis=z]")
    # gable roof along x, dark oak with an overhang
    half = (z2 - z1) // 2 + 1
    for i in range(half + 1):
        y = h + 6 + i
        za, zb = z1 - 1 + i, z2 + 1 - i
        if za > zb:
            break
        for x in range(x1 - 1, x2 + 2):
            if za == zb:
                c.set(x, y, za, "dark_oak_slab[type=bottom]")
                continue
            c.set(x, y, za, "dark_oak_stairs[facing=south,half=bottom,shape=straight]")
            c.set(x, y, zb, "dark_oak_stairs[facing=north,half=bottom,shape=straight]")
        for z in range(za + 1, zb):
            for x in (x1, x2):
                c.set(x, y, z, "spruce_planks")
    # the saw line down the middle: log on rollers between two stonecutters
    zm = (z1 + z2) // 2
    for x in range(x1 + 3, x2 - 2):
        c.set(x, h + 1, zm, "stripped_spruce_wood[axis=y]")
    for x in (x1 + 6, x1 + 11):
        c.set(x, h + 2, zm, "stonecutter[facing=north]")
    for x in range(x1 + 4, x1 + 10):
        if x not in (x1 + 6,):
            c.set(x, h + 2, zm, "oak_log[axis=x]")
    # workbenches along the north side, barrels and composters (sawdust) along the south
    for i, x in enumerate(range(x1 + 2, x2 - 1, 3)):
        c.set(x, h + 1, z1 + 2, "crafting_table")
        c.set(x + 1, h + 1, z1 + 2, "barrel[facing=up,open=false]" if i % 2 else "spruce_log[axis=y]")
        c.set(x, h + 1, z2 - 2, "composter[level=5]" if i % 2 else "barrel[facing=north,open=false]")
        c.set(x + 1, h + 1, z2 - 2, "oak_log[axis=y]")
    # log racks: upright logs leaning in rows by the west wall (perfect for log hiders)
    for z in range(z1 + 4, z2 - 3, 2):
        c.set(x1 + 1, h + 1, z, "spruce_log[axis=y]")
        c.set(x1 + 1, h + 2, z, "spruce_log[axis=y]")
        c.set(x1 + 2, h + 1, z, "oak_log[axis=y]")
    # carpenters' benches outside the open east end
    for z in (z1 + 3, z1 + 6, z2 - 6, z2 - 3):
        c.set(x2 + 2, on_ground(c, x2 + 2, z), z, "crafting_table")
    for x in range(x1 + 3, x2 - 2, 4):
        c.set(x, h + 5, zm, "lantern[hanging=false]")
        c.set(x, h + 4, zm, "lantern[hanging=true]")
        c.set(x, h + 5, zm, "spruce_log[axis=x]")
    for x in range(x1, x2 + 1):
        c.set(x, h + 5, zm, "spruce_log[axis=x]")


def log_yard(c: Canvas):
    x1, z1, x2, z2 = YARD
    # long stacks (horizontal), in a pyramid
    for (sx, sz, n) in ((x1 + 1, z1 + 1, 9), (x1 + 13, z1 + 1, 8)):
        y = on_ground(c, sx, sz)
        for row in range(3):
            for i in range(row, n - row):
                for w in range(3 - row):
                    c.set(sx + i, y + row, sz + w, "spruce_log[axis=x]" if (i + w) % 3 else "oak_log[axis=x]")
    # stumps and short upright logs in loose rows: the yard's hiding field
    for x in range(x1 + 1, x2, 2):
        for z in range(z1 + 6, z2 + 1, 3):
            if h3(x, 0, z, 14) < 0.55:
                kind = "oak_log[axis=y]" if h3(x, 1, z, 15) < 0.5 else "spruce_log[axis=y]"
                y = on_ground(c, x, z)
                c.set(x, y, z, kind)
                if h3(x, 2, z, 16) < 0.25:
                    c.set(x, y + 1, z, kind)
    # timber cart: a plank bed on log wheels with a load of logs
    cx, cz = x2 - 6, z1 + 2
    y = on_ground(c, cx, cz)
    for i in range(5):
        for w in range(3):
            c.set(cx + i, y + 1, cz + w, "spruce_slab[type=top]")
    for (dx, dz) in ((0, -1), (4, -1), (0, 3), (4, 3)):
        c.set(cx + dx, y, cz + dz, "stripped_dark_oak_wood[axis=y]")
    for i in range(5):
        c.set(cx + i, y + 2, cz + 1, "oak_log[axis=x]")
    c.set(cx - 1, y + 1, cz + 1, "spruce_fence")
    c.set(cx - 2, y + 1, cz + 1, "spruce_fence")
    for (x, z) in ((x2 - 2, z2 - 1), (x2 - 1, z2 - 1), (x2 - 2, z2 - 2)):
        c.set(x, on_ground(c, x, z), z, "barrel[facing=up,open=false]")


def garden(c: Canvas):
    x1, z1, x2, z2 = GARDEN
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            edge = x in (x1, x2) or z in (z1, z2)
            if edge:
                if not (z == (z1 + z2) // 2 and x == x1):   # gate gap on the west side
                    c.set(x, G + 1, z, "spruce_fence")
                continue
            if (z - z1) % 3 == 0:
                c.set(x, G, z, "water") if (x - x1) % 4 == 2 else c.set(x, G, z, "farmland[moisture=7]")
            else:
                c.set(x, G, z, "farmland[moisture=7]")
                if h3(x, 3, z, 17) < 0.35:
                    c.set(x, G + 1, z, "pumpkin")
    for (x, z) in ((x1 - 1, z1 + 2), (x1 - 1, z1 + 3), (x2 + 1, z2 - 2)):
        c.set(x, on_ground(c, x, z), z, "composter[level=7]")
    for (x, z) in ((x2 + 1, z1 + 1), (x2 + 2, z1 + 1), (x1 - 2, z2 - 1)):
        c.set(x, on_ground(c, x, z), z, "pumpkin")
    c.set(x1 - 1, on_ground(c, x1 - 1, z2), z2, "hay_block[axis=y]")


def stable(c: Canvas):
    """A lean-to shelter with hay, a fenced paddock with bales, troughs (composters) and a cart."""
    x1, z1, x2, z2 = STABLE
    # lean-to along the south side
    for x in range(x1, x1 + 10):
        for z in (z2 - 4, z2):
            if (x - x1) % 3 == 0:
                c.column(x, z, G + 1, G + (4 if z == z2 - 4 else 3), "spruce_log[axis=y]")
        for z in range(z2 - 5, z2 + 2):
            y = G + 5 - (z - (z2 - 5)) // 3
            c.set(x, y, z, "spruce_slab[type=bottom]" if (z - z2) % 3 else "spruce_slab[type=top]")
        for z in range(z2 - 3, z2):
            c.set(x, G, z, "coarse_dirt")
            if h3(x, 4, z, 18) < 0.55:
                c.set(x, G + 1, z, "hay_block[axis=y]")
                if h3(x, 5, z, 19) < 0.3:
                    c.set(x, G + 2, z, "hay_block[axis=y]")
    # paddock fence with bales scattered inside
    px1, pz1, px2, pz2 = x1 + 1, z1, x2, z2 - 6
    for x in range(px1, px2 + 1):
        for z in range(pz1, pz2 + 1):
            if x in (px1, px2) or z in (pz1, pz2):
                if not (x == px1 + 3 and z == pz1):
                    c.set(x, on_ground(c, x, z), z, "spruce_fence")
            elif h3(x, 6, z, 20) < 0.12:
                c.set(x, on_ground(c, x, z), z, "hay_block[axis=y]")
    for z in (pz1 + 2, pz1 + 3, pz1 + 5):
        c.set(px2 - 1, on_ground(c, px2 - 1, z), z, "composter[level=8]")
    for x in (px1 + 2, px1 + 6):
        c.set(x, on_ground(c, x, pz2 + 1), pz2 + 1, "composter[level=4]")
    c.set(x2 + 1, on_ground(c, x2 + 1, z2 - 2), z2 - 2, "barrel[facing=up,open=false]")
    c.set(x2 + 1, on_ground(c, x2 + 1, z2 - 1), z2 - 1, "barrel[facing=up,open=false]")


def cabins(c: Canvas):
    for i, (x1, z1, x2, z2) in enumerate(CABINS + [HUT]):
        door = None if x1 < 60 else x1   # east doors, the creek cabin opens to the west
        hall(c, x1, z1, x2, z2, h=G, door_x=door)
        zm = (z1 + z2) // 2
        c.set(x1 + 1, G + 1, z1 + 1, "barrel[facing=up,open=false]")
        c.set(x1 + 1, G + 1, z2 - 1, "crafting_table")
        c.set(x2 - 1, G + 1, z1 + 1, "composter[level=2]" if i % 2 else "pumpkin")
        if i < len(CABINS):
            c.set(x2 - 1, G + 1, z2 - 1, "hay_block[axis=y]")
        # woodpile against the back wall, outside
        wx = x1 - 1 if door is None else x2 + 1
        for z in range(z1 + 1, z2):
            if z != zm:
                c.set(wx, G + 1, z, "oak_log[axis=z]")
                if (z - z1) % 2:
                    c.set(wx, G + 2, z, "oak_log[axis=z]")
    # the hut is where the seekers wait: a bench along the wall and a lantern
    x1, z1, x2, z2 = HUT
    c.set(x2 - 1, G + 3, z1 + 1, "lantern[hanging=false]")
    c.set(x2 + 2, on_ground(c, x2 + 2, z1), z1, "crafting_table")
    c.set(x2 + 2, on_ground(c, x2 + 2, z1 + 1), z1 + 1, "composter[level=1]")


def bridge(c: Canvas):
    zb = 40
    xs = [x for x in range(SX) if abs(x - creek_x(zb)) < 4.5]
    for x in xs:
        for z in (zb - 1, zb, zb + 1):
            c.set(x, G, z, Pal(("spruce_planks", 3), ("stripped_spruce_wood[axis=y]", 1), seed=21))
            c.set(x, G + 1, z, "air")
        for z in (zb - 2, zb + 2):
            c.set(x, G + 1, z, "spruce_fence")
            c.set(x, G, z, "spruce_log[axis=x]")
    for z in (zb - 2, zb + 2):
        for x in (xs[0], xs[-1]):
            c.set(x, G + 1, z, "spruce_log[axis=y]")
    # a little dock with barrels downstream
    dz = 52
    dx = int(creek_x(dz)) - 3
    for x in range(dx, dx + 3):
        for z in (dz, dz + 1):
            c.set(x, G, z, "spruce_planks")
    c.set(dx, G + 1, dz, "barrel[facing=up,open=false]")
    c.set(dx, G + 1, dz + 1, "barrel[facing=east,open=false]")


def forest(c: Canvas):
    def blocked(x, z):
        if any(rect_dist(x, z, r) < 4 for r in built()):
            return True
        if math.dist((x, z), SQUARE) < 10 or creek_d(x, z) < 5:
            return True
        return any(poly_dist(x, z, p) < 3 for p in PATHS)

    step = 5
    for gx in range(0, SX, step):
        for gz in range(0, SZ, step):
            x = gx + int(h3(gx, 5, gz, 60) * step)
            z = gz + int(h3(gz, 6, gx, 61) * step)
            if not (0 <= x < SX and 0 <= z < SZ):
                continue
            border = min(x, z, SX - 1 - x, SZ - 1 - z) < 6
            if not border and blocked(x, z):
                continue
            dens = 0.95 if border else 0.5
            r = h3(x, 7, z, 62)
            y = on_ground(c, x, z)
            if r > dens:
                if not border and r < dens + 0.3:
                    # forest floor hiding spots: a moss mound, a stump or a bush
                    k = h3(x, 8, z, 63)
                    if k < 0.4:
                        c.set(x, y, z, "moss_block")
                        for dx, dz in ((1, 0), (0, 1)):
                            if h3(x + dx, 9, z + dz, 64) < 0.5:
                                c.set(x + dx, on_ground(c, x + dx, z + dz), z + dz, "moss_block")
                    elif k < 0.7:
                        c.set(x, y, z, "oak_log[axis=y]")
                    else:
                        P.bush(c, x, y, z, r=1.2, seed=x * 7 + z)
                continue
            kind = h3(x, 9, z, 65)
            if kind < 0.45:
                P.spruce(c, x, y, z, height=8 + int(h3(x, 1, z, 66) * 5), seed=x + z)
            elif kind < 0.7:
                P.birch(c, x, y, z, height=5 + int(h3(x, 2, z, 67) * 3), seed=x + z)
            else:
                P.oak(c, x, y, z, height=4 + int(h3(x, 3, z, 68) * 3), r=2.3 + h3(x, 4, z, 69), seed=x + z)
            # moss around the trunk
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if h3(x + dx, 0, z + dz, 70) < 0.25 and c.empty(x + dx, y, z + dz):
                    c.set(x + dx, y - 1, z + dz, "moss_block")
                    c.set(x + dx, y, z + dz, "moss_carpet")


def ground_cover(c: Canvas):
    deco = Pal(("short_grass", 30), ("fern", 4), ("dandelion", 1), ("oxeye_daisy", 1), ("poppy", 1), seed=80)
    for x in range(1, SX - 1):
        for z in range(1, SZ - 1):
            y = c.top(x, z)
            s = c.get(x, y, z)
            if s.startswith("grass_block") and h3(x, 12, z, 81) < 0.3:
                c.set(x, y + 1, z, deco)
            elif s.startswith("podzol") and h3(x, 12, z, 82) < 0.2:
                c.set(x, y + 1, z, "fern")


def border(c: Canvas):
    """Barrier in every non-full block of the edge columns, up to the top."""
    for x in range(SX):
        for z in range(SZ):
            if x in (0, SX - 1) or z in (0, SZ - 1):
                for y in range(1, SY):
                    s = c.b.get((x, y, z))
                    if s is None or not s.startswith(("stone", "dirt", "grass_block", "podzol", "coarse_dirt",
                                                       "gravel", "mud", "clay", "sand", "rooted_dirt")):
                        c.set(x, y, z, "barrier")


def build():
    c = CANVAS
    c.b.clear()
    terrain(c)
    square(c)
    sawmill(c)
    log_yard(c)
    garden(c)
    stable(c)
    cabins(c)
    bridge(c)
    forest(c)
    ground_cover(c)
    c.drop_floating()
    border(c)
    c.fix_connections()


# --------------------------------------------------------------------------- checks


def report():
    """Verstoppertje checks: the seekers can reach where the hiders start, nobody can walk off the
    map, and each disguise block is common enough to hide as."""
    c = CANVAS
    starts = [(int(p[0]), int(p[1]), int(p[2])) for lst in SPAWNS.values() for p in lst]
    area = analyze.walkable(c, starts)
    cols = {(x, z) for x, _, z in area}
    lines = [f"walkable floor: {len(area)} blocks"]
    ok = True
    for t, lst in SPAWNS.items():
        on = all(any((int(p[0]), int(p[1]) - d, int(p[2])) in area for d in range(3)) for p in lst)
        lines.append(f"team {t} spawns on walkable floor: {'yes' if on else 'NO'}")
        ok &= on
    hiders = analyze.walkable(c, [starts[0]])
    seekers = analyze.walkable(c, [(int(p[0]), int(p[1]), int(p[2])) for p in SPAWNS[1]])
    joined = bool(hiders & seekers)
    lines.append(f"seekers can walk to the hiders: {'yes' if joined else 'NO'}")
    ok &= joined
    edge = [p for p in area if p[0] in (0, SX - 1) or p[2] in (0, SZ - 1)]
    lines.append(f"floor at the edge: {len(edge)} (must be 0)")
    ok &= not edge
    # a disguise counts where a hider could copy it: a prop block standing next to walkable floor
    counts = {name: 0 for name in DISGUISES}
    for (x, y, z), s in c.b.items():
        name = parse(s)[0]
        if name in counts and any((x + dx, z + dz) in cols for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            counts[name] += 1
    for name, n in counts.items():
        good = n >= 10
        lines.append(f"  {name}: {n} {'' if good else '(too few, aim 10+)'}")
        ok &= good
    lines.append("VERSTOPPERTJE OK" if ok else "VERSTOPPERTJE CHECKS FAILED")
    return lines
