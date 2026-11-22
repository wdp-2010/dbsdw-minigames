"""Woudfront: woodsball in a forest. Two log forts, a trench line, a creek with a bridge and two
fords in the middle, a ruined farmhouse and a hunters' cabin on the flanks.

Built on the west half (team 0) and turned 180 degrees for team 1. Three lanes:
north = open meadow with the cabin (long range), centre = trench -> bridge (mid range),
south = dense forest with the ruin (close range).
"""
from __future__ import annotations

import math

import parts as P
from mcbuild import Canvas, Grad, Pal, fbm, h3

WORLD = "world"
SX, SY, SZ = 150, 40, 100
G = 8                      # ground surface (top block) in local y
ORIGIN = (300, -63, -50)   # world position of local (0, 0, 0); y -63 is the flat world's grass
HALF = SX // 2
SEED = 41

CANVAS = Canvas(SX, SY, SZ, SEED)

FORT = (6, 32, 28, 67)          # palisade rectangle x1, z1, x2, z2
GATES_E = ((38, 41), (58, 61))  # z ranges of the east gates
GATE_N = (15, 17)               # x range of the north and south gates
BRIDGE_Z = (47, 52)
FORDS = ((13, 17), (82, 86))
RUIN = (52, 59, 64, 69)
CABIN = (47, 6, 54, 12)
TRENCH = [(41, 24), (38, 31), (43, 38), (38, 45), (43, 52), (38, 59), (43, 66), (40, 74)]
PATHS = [
    [(29, 39.5), (36, 36), (44, 33), (52, 38), (60, 45), (67, 49.5)],
    [(29, 59.5), (37, 64), (44, 72), (52, 78), (62, 83), (71, 84)],
    [(16, 31), (21, 22), (32, 17), (44, 18), (58, 16), (71, 15)],
    [(16, 68), (20, 78), (30, 86), (44, 84)],
]

SPAWNS = {0: [(8.5, G + 1, 36.5), (8.5, G + 1, 43.5), (8.5, G + 1, 56.5), (8.5, G + 1, 63.5),
              (13.5, G + 1, 38.5), (13.5, G + 1, 61.5)]}
SPAWNS[1] = [(SX - p[0], p[1], SZ - p[2]) for p in SPAWNS[0]]
FLAGS = {0: (11.5, G + 2, 50.5)}
FLAGS[1] = (SX - FLAGS[0][0], FLAGS[0][1], SZ - FLAGS[0][2])
YAW = {0: -90.0, 1: 90.0}
POWERUPS = [(45.5, G + 1, 20.5), (60.5, G + 1, 30.5), (104.5, G + 1, 79.5), (89.5, G + 1, 69.5)]
CLOSEUPS = {"fort": (0, 24, 40, 76), "mid": (40, 30, 110, 70), "ruin": (46, 54, 72, 76)}


# --------------------------------------------------------------------------- helpers


def creek_x(z):
    return HALF - 0.5 + 3.0 * math.sin((z - (SZ - 1) / 2) / 9.0)


def seg_dist(px, pz, a, b):
    ax, az = a
    bx, bz = b
    dx, dz = bx - ax, bz - az
    t = max(0.0, min(1.0, ((px - ax) * dx + (pz - az) * dz) / (dx * dx + dz * dz or 1)))
    return math.hypot(px - ax - t * dx, pz - az - t * dz)


def poly_dist(px, pz, pts):
    return min(seg_dist(px, pz, pts[i], pts[i + 1]) for i in range(len(pts) - 1))


def rect_dist(px, pz, r):
    x1, z1, x2, z2 = r
    dx = max(x1 - px, 0, px - x2)
    dz = max(z1 - pz, 0, pz - z2)
    return math.hypot(dx, dz)


def in_ford(z):
    return any(a <= z <= b for a, b in FORDS)


# --------------------------------------------------------------------------- terrain


def height(x, z):
    n = (fbm(x, z, 26, SEED) - 0.5) * 6
    flat = 0.0
    for r, k in ((FORT, 7), (RUIN, 5), (CABIN, 4)):
        flat = max(flat, 1 - min(1, rect_dist(x, z, r) / k))
    flat = max(flat, 1 - min(1, poly_dist(x, z, TRENCH) / 6))
    flat = max(flat, 1 - min(1, abs(x - creek_x(z)) / 12))
    edge = min(x, z, SZ - 1 - z)
    rise = max(0, 6 - edge) * 0.8
    return G + round(n * (1 - flat) + rise)


def terrain(c: Canvas):
    grass = "grass_block[snowy=false]"
    path_pal = Pal(("dirt_path", 5), ("coarse_dirt", 2), ("gravel", 1), seed=3)
    bank = Pal(("gravel", 3), ("mud", 2), ("clay", 1), ("coarse_dirt", 2), seed=4, scale=2)
    bed = Pal(("gravel", 3), ("sand", 1), ("clay", 1), ("mud", 1), seed=5, scale=2)
    forest_floor = Pal((grass, 6), ("podzol[snowy=false]", 2), ("coarse_dirt", 1), ("moss_block", 1), seed=6, scale=3)
    for x in range(HALF):
        for z in range(SZ):
            h = height(x, z)
            cd = abs(x - creek_x(z))
            top = grass
            if cd < 3.2:
                deep = cd < 1.8 and not in_ford(z)
                floor = G - 3 if deep else G - 2
                c.column(x, z, 0, floor - 1, "stone")
                c.set(x, floor, z, bed)
                for y in range(floor + 1, G):
                    c.set(x, y, z, "water")
                if in_ford(z) and h3(x, 0, z, 9) < 0.45:
                    c.set(x, G - 1, z, Pal("mossy_cobblestone", "cobblestone", "stone", seed=10))
                continue
            if cd < 5.5:
                h = G - 1 if cd < 4.4 else G
                top = bank
            elif poly_dist(x, z, PATHS[0] + []) < 1.6 or any(poly_dist(x, z, p) < 1.4 + 0.6 * h3(x, 1, z, 3) for p in PATHS):
                top = path_pal
            elif z > 72 or fbm(x, z, 14, SEED + 9) > 0.6:
                top = forest_floor
            c.column(x, z, 0, h - 4, "stone")
            c.column(x, z, h - 3, h - 1, "dirt")
            c.set(x, h, z, top)


def trench(c: Canvas):
    """Shallow zig-zag trench: one block deep, plank revetment, duckboards, a sandbag lip."""
    floor_pal = Pal(("coarse_dirt", 3), ("mud", 1), ("podzol[snowy=false]", 1), seed=12)
    cells = set()
    for x in range(30, 50):
        for z in range(20, 80):
            if poly_dist(x, z, TRENCH) < 1.5:
                cells.add((x, z))
    for (x, z) in cells:
        h = c.top_solid(x, z)
        for y in range(h, h + 3):
            c.set(x, y, z, "air")
        c.set(x, h - 1, z, floor_pal)
        if h3(x, 0, z, 13) < 0.5:
            c.set(x, h, z, "spruce_trapdoor[facing=north,half=bottom,open=false]")
    for (x, z) in cells:
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if (nx, nz) in cells:
                continue
            h = c.top_solid(nx, nz)
            if h < 0:
                continue
            post = (nx + nz) % 4 == 0
            c.set(nx, h, nz, "spruce_log[axis=y]" if post else Pal("spruce_planks", ("stripped_spruce_log[axis=y]", 1), seed=14))
            if dx == 1:  # the enemy side gets a sandbag lip, half height: hidden when sneaking
                c.set(nx, h + 1, nz, P.SANDBAG_TOP)


def creek_crossings(c: Canvas):
    z1, z2 = BRIDGE_Z
    for x in range(66, HALF):
        rise = 1 if x < 69 else 2
        for z in range(z1, z2 + 1):
            c.set(x, G + rise - 1, z, Pal(("spruce_planks", 4), ("stripped_spruce_log[axis=x]", 1), seed=15))
            for y in range(G + rise, G + rise + 3):
                c.set(x, y, z, "air")
        if x == 68:
            for z in range(z1, z2 + 1):
                c.set(x, G + 1, z, "spruce_stairs[facing=east,half=bottom,shape=straight]")
        for z in (z1 - 1, z2 + 1):
            c.set(x, G + rise, z, "spruce_log[axis=y]" if x % 3 == 0 else "spruce_fence")
            c.set(x, G + rise - 1, z, "spruce_log[axis=x]")
            if x % 3 == 0:
                c.set(x, G + rise + 1, z, "lantern[hanging=false]" if x == 72 else "air")
        if x % 3 == 0:
            for z in (z1 - 1, z2 + 1):
                c.column(x, z, G - 4, G + rise - 2, "stripped_spruce_log[axis=y]")


def fort(c: Canvas):
    x1, z1, x2, z2 = FORT
    logs = Pal(("spruce_log[axis=y]", 8), ("stripped_spruce_log[axis=y]", 1), ("dark_oak_log[axis=y]", 1), seed=20)
    floor = Pal(("coarse_dirt", 3), ("grass_block[snowy=false]", 2), ("dirt_path", 2), ("gravel", 1), seed=21, scale=2)
    for x in range(x1 + 1, x2):
        for z in range(z1 + 1, z2):
            c.set(x, G, z, floor)
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            on_wall = x in (x1, x2) or z in (z1, z2)
            if not on_wall:
                continue
            gate = (x == x2 and any(a <= z <= b for a, b in GATES_E)) or \
                   (z in (z1, z2) and GATE_N[0] <= x <= GATE_N[1])
            top = 4 + (1 if h3(x, 0, z, 22) > 0.55 else 0)
            corner = x in (x1, x2) and z in (z1, z2)
            if corner:
                top = 6
            for y in range(G + 1, G + top + 1):
                if gate and y <= G + 3:
                    continue
                c.set(x, y, z, "dark_oak_log[axis=y]" if corner else logs)
    # walkway behind the east wall, over both gates
    wx = x2 - 1
    for z in range(z1 + 1, z2):
        c.set(wx, G + 3, z, "spruce_planks")
        if z % 5 == 0 and not any(a - 1 <= z <= b + 1 for a, b in GATES_E):
            c.column(wx, z, G + 1, G + 2, "spruce_fence")
    for lz in (45, 54):
        c.column(wx, lz, G + 1, G + 2, "spruce_planks")
        c.column(wx - 1, lz, G + 1, G + 3, "ladder[facing=west]")
    # gate frames: log posts sticking out, a lintel and lanterns
    for a, b in GATES_E:
        for z in (a - 1, b + 1):
            c.column(x2 + 1, z, G + 1, G + 5, "spruce_log[axis=y]")
        for z in range(a - 1, b + 2):
            c.set(x2 + 1, G + 5, z, "spruce_log[axis=z]")
        c.set(x2 + 2, G + 4, a - 1, "lantern[hanging=false]")
    # flag podium, tents, supplies, tower
    fx, fy, fz = int(FLAGS[0][0]), int(FLAGS[0][1]), int(FLAGS[0][2])
    P.flag_podium(c, fx, fy, fz)
    P.tent(c, 16, G + 1, 41, length=6, facing="x", seed=1)
    P.tent(c, 16, G + 1, 59, length=6, facing="x", seed=2)
    P.camo_net(c, 19, 46, 25, 54, G + 4, seed=3)
    P.crate_stack(c, 20, G + 1, 47, w=2, d=2, h=2, seed=4)
    P.crate_stack(c, 23, G + 1, 52, w=2, d=1, h=1, seed=5)
    P.crate_stack(c, 21, G + 1, 51, w=1, d=1, h=1, seed=6)
    for z in (48, 49, 50, 51):
        c.set(25, G + 1, z, P.SANDBAG)
    c.set(25, G + 2, 49, P.SANDBAG_TOP)
    P.watchtower(c, 22, G + 1, 34, height=7, ladder_side="west")
    P.lamp_post(c, 12, G + 1, 46)
    for x, z in ((8, 49), (8, 50), (8, 51)):
        c.set(x, G + 1, z, "hay_block[axis=z]")
    c.set(9, G + 1, 40, "barrel[facing=up,open=false]")
    c.set(9, G + 1, 60, "barrel[facing=up,open=false]")
    # sandbag nests just outside the gates
    P.sandbags(c, x2 + 4, 36, x2 + 4, 43, None, 1.5)
    P.sandbags(c, x2 + 4, 56, x2 + 4, 63, None, 1.5)


def ruin(c: Canvas):
    """Burnt-out stone farmhouse, two storeys, one gable left standing."""
    x1, z1, x2, z2 = RUIN
    wall = Grad([Pal(("mossy_cobblestone", 3), ("cobblestone", 2), ("mossy_stone_bricks", 1), seed=30),
                 Pal(("cobblestone", 3), ("stone_bricks", 2), ("andesite", 1), ("mossy_cobblestone", 1), seed=31),
                 Pal(("stone_bricks", 3), ("cracked_stone_bricks", 2), ("cobblestone", 1), ("andesite", 1), seed=32)],
                G + 1, G + 9, seed=33)
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            c.set(x, G, z, Pal("cobblestone", "gravel", "coarse_dirt", ("stone_bricks", 2), seed=34))
            for y in range(G + 1, G + 12):
                c.set(x, y, z, "air")
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            edge = x in (x1, x2) or z in (z1, z2)
            if not edge:
                continue
            # broken top edge: high on the west gable, crumbling toward the east
            wall_top = G + 8 - int((x - x1) * 0.45) - int(h3(x, 2, z, 35) * 3)
            if x == x1:
                wall_top = G + 8 + min(z - z1, z2 - z) // 1 - 1  # gable
                wall_top = min(wall_top, G + 11)
            corner = x in (x1, x2) and z in (z1, z2)
            for y in range(G + 1, wall_top + 1):
                c.set(x, y, z, "stripped_oak_log[axis=y]" if corner and y < G + 9 else wall)
    # openings: doors (2 wide), windows (inset by leaving the frame), a blown-out hole
    for z in (z1 + 3, z1 + 4):
        for y in (G + 1, G + 2, G + 3):
            c.set(x2, y, z, "air")
            c.set(x1, y, z + 1, "air")
    for x in (x1 + 4, x1 + 5):
        for y in (G + 1, G + 2, G + 3):
            c.set(x, y, z1, "air")
            c.set(x + 2, y, z2, "air")
    for (wx, wz, axis) in ((x1 + 2, z1, "x"), (x1 + 8, z1, "x"), (x1 + 2, z2, "x"), (x1 + 7, z2, "x")):
        for y in (G + 2, G + 3, G + 6, G + 7):
            c.set(wx, y, wz, "air")
    for y in (G + 2, G + 3, G + 6):
        c.set(x1, y, z1 + 2, "air")
        c.set(x1, y, z2 - 2, "air")
    # window sills (depth): stairs upside down under each window, outside
    for (wx, wz, f, dz) in ((x1 + 2, z1 - 1, "north", 0), (x1 + 8, z1 - 1, "north", 0),
                            (x1 + 2, z2 + 1, "south", 0), (x1 + 7, z2 + 1, "south", 0)):
        c.set(wx, G + 1, wz, f"stone_brick_stairs[facing={'south' if f == 'north' else 'north'},half=top,shape=straight]")
    # upper floor, partly collapsed
    for x in range(x1 + 1, x2):
        for z in range(z1 + 1, z2):
            if x > x1 + 6 and h3(x, 3, z, 36) < 0.6:
                continue
            c.set(x, G + 5, z, Pal(("spruce_planks", 4), ("dark_oak_planks", 1), seed=37))
    for x in range(x1 + 1, x2, 3):
        for z in range(z1 + 1, z2):
            if c.get(x, G + 5, z) != "air":
                c.set(x, G + 5, z, "dark_oak_log[axis=z]")
    # stairs up to the first floor along the north wall
    for i in range(4):
        c.set(x1 + 2 + i, G + 1 + i, z1 + 1, "cobblestone_stairs[facing=east,half=bottom,shape=straight]")
        c.set(x1 + 2 + i, G + 5, z1 + 1, "air")
    c.set(x1 + 6, G + 5, z1 + 1, "air")
    # chimney on the gable
    c.column(x1 - 1, (z1 + z2) // 2, G + 1, G + 12, Pal("bricks", "stone_bricks", "cobblestone", seed=38))
    # charred roof beams left on the west end
    for z in range(z1, z2 + 1):
        r = G + 8 + min(z - z1, z2 - z)
        for x in range(x1, x1 + 3):
            if r <= G + 11:
                c.set(x, r, z, "dark_oak_stairs[facing=" + ("south" if z < (z1 + z2) / 2 else "north") + ",half=bottom,shape=straight]")
    # rubble
    rub = Pal(("cobblestone", 3), ("mossy_cobblestone", 2), ("gravel", 1), seed=39)
    for (rx, rz) in ((x2 + 2, z1 + 1), (x2 + 1, z2), (x1 + 8, z1 - 2), (x2 + 2, z2 - 2)):
        c.blob(rx, G + 0.6, rz, 1.6, 0.9, 1.3, rub, rough=0.5, seed=rx)
    for (sx_, sz_) in ((x2 + 3, z1 + 4), (x1 + 9, z2 + 2)):
        c.set(sx_, G + 1, sz_, "cobblestone_slab[type=bottom]")
    # inside: a broken cart and crates
    P.crate_stack(c, x1 + 7, G + 1, z1 + 4, w=2, d=1, h=1, seed=40)
    c.set(x1 + 3, G + 1, z2 - 2, "hay_block[axis=x]")
    c.set(x1 + 4, G + 1, z2 - 2, "hay_block[axis=x]")


def cabin(c: Canvas):
    x1, z1, x2, z2 = CABIN
    h = G
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            c.set(x, h, z, "spruce_planks")
            for y in range(h + 1, h + 9):
                c.set(x, y, z, "air")
    logs = Pal(("spruce_log[axis=x]", 1), seed=50)
    for y in range(h + 1, h + 4):
        for x in range(x1, x2 + 1):
            for z in (z1, z2):
                c.set(x, y, z, "spruce_log[axis=x]")
        for z in range(z1, z2 + 1):
            for x in (x1, x2):
                c.set(x, y, z, "spruce_log[axis=z]")
    for (x, z) in ((x1, z1), (x1, z2), (x2, z1), (x2, z2)):
        c.column(x, z, h + 1, h + 3, "stripped_spruce_log[axis=y]")
    # doors on east and west, windows on north and south
    zm = (z1 + z2) // 2
    for y in (h + 1, h + 2):
        c.set(x1, y, zm, "air")
        c.set(x2, y, zm, "air")
    for x in (x1 + 2, x2 - 2):
        c.set(x, h + 2, z1, "air")
        c.set(x, h + 2, z2, "air")
        c.set(x, h + 1, z1 - 1, "spruce_trapdoor[facing=north,half=top,open=false]")
        c.set(x, h + 1, z2 + 1, "spruce_trapdoor[facing=south,half=top,open=false]")
    # gable roof with overhang (stairs) running along x
    for i in range(0, (z2 - z1) // 2 + 2):
        y = h + 4 + i
        for x in range(x1 - 1, x2 + 2):
            za, zb = z1 - 1 + i, z2 + 1 - i
            if za > zb:
                continue
            if za == zb:
                c.set(x, y, za, "dark_oak_slab[type=bottom]")
                continue
            c.set(x, y, za, "dark_oak_stairs[facing=south,half=bottom,shape=straight]")
            c.set(x, y, zb, "dark_oak_stairs[facing=north,half=bottom,shape=straight]")
        for z in range(z1 + i, z2 - i + 1):
            for x in (x1, x2):
                if z1 - 1 + i < z < z2 + 1 - i:
                    c.set(x, y, z, "spruce_planks")
    c.set(x1 + 1, h + 1, z1 + 1, "barrel[facing=up,open=false]")
    c.set(x2 - 1, h + 1, z2 - 1, "crafting_table")
    c.set(x1 + 1, h + 1, z2 - 1, "composter[level=0]")
    # woodpile and chopping block
    for z in range(z1 + 1, z2):
        c.set(x2 + 1, h + 1, z, "oak_log[axis=x]")
    c.set(x1 - 2, h + 1, z2 + 2, "oak_log[axis=y]")


def north_meadow(c: Canvas):
    for (x, z, a) in ((34, 22, "x"), (60, 23, "z"), (28, 12, "z")):
        P.fallen_log(c, x, c.top_solid(x, z) + 1, z, length=6, axis=a, seed=x)
    for (x, z) in ((40, 9), (41, 9), (40, 10), (63, 9), (62, 26), (31, 27)):
        c.set(x, c.top_solid(x, z) + 1, z, "hay_block[axis=y]")
    for (x, z, r) in ((36, 13, 2.2), (57, 25, 1.8), (66, 8, 2.4)):
        P.boulder(c, x, c.top_solid(x, z) + 1, z, r=r, seed=x)
    # broken fence line
    for x in range(30, 66):
        z = 26
        if h3(x, 4, z, 51) < 0.65 and not any(poly_dist(x, z, p) < 2.5 for p in PATHS):
            c.set(x, c.top_solid(x, z) + 1, z, "spruce_fence")


def woods(c: Canvas):
    def blocked(x, z, pad):
        if rect_dist(x, z, FORT) < 3 + pad or rect_dist(x, z, RUIN) < 6 + pad or rect_dist(x, z, CABIN) < 3 + pad:
            return True
        if poly_dist(x, z, TRENCH) < 4 + pad or any(poly_dist(x, z, p) < 2.5 + pad for p in PATHS):
            return True
        if abs(x - creek_x(z)) < 6.5 + pad or (BRIDGE_Z[0] - 4 <= z <= BRIDGE_Z[1] + 4 and x > 60):
            return True
        if 28 < x < 34 and 30 < z < 70:      # keep the field in front of the fort open
            return True
        return False

    step = 5
    for gx in range(0, HALF, step):
        for gz in range(0, SZ, step):
            x = gx + int(h3(gx, 5, gz, 60) * step)
            z = gz + int(h3(gz, 6, gx, 61) * step)
            if x >= HALF - 7 or z >= SZ:
                continue
            border = x < 5 or z < 5 or z > SZ - 6
            if not border and blocked(x, z, 0):
                continue
            if border:
                dens = 0.95
            elif z > 72:
                dens = 0.8
            elif z < 27 and x > 30:
                dens = 0.12
            else:
                dens = 0.25 + 0.55 * fbm(x, z, 20, SEED + 2)
            r = h3(x, 7, z, 62)
            if r > dens:
                if not border and r < dens + 0.25 and not blocked(x, z, 0):
                    P.bush(c, x, c.top_solid(x, z) + 1, z, r=1.2 + h3(x, 8, z, 63), seed=x * 7 + z)
                continue
            y = c.top_solid(x, z) + 1
            kind = h3(x, 9, z, 64)
            if border and kind < 0.5 or kind < 0.15:
                P.spruce(c, x, y, z, height=8 + int(h3(x, 1, z, 65) * 5), seed=x + z)
            elif kind < 0.4:
                P.birch(c, x, y, z, height=5 + int(h3(x, 2, z, 66) * 3), seed=x + z)
            else:
                P.oak(c, x, y, z, height=4 + int(h3(x, 3, z, 67) * 3), r=2.3 + h3(x, 4, z, 68), seed=x + z)
            # roots / ferns around the trunk
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if h3(x + dx, 0, z + dz, 69) < 0.3 and c.empty(x + dx, y, z + dz):
                    c.set(x + dx, y, z + dz, "fern")
    # south forest: boulders and a broken stone wall
    for (x, z, r) in ((30, 92, 2.6), (48, 90, 2.0), (22, 80, 1.8), (58, 92, 2.2), (35, 78, 1.6)):
        if not blocked(x, z, -2):
            P.boulder(c, x, c.top_solid(x, z) + 1, z, r=r, seed=x)
    for x in range(28, 46):
        z = 76 + (x // 6) % 2
        if h3(x, 10, z, 70) < 0.7 and not any(poly_dist(x, z, p) < 2 for p in PATHS):
            y = c.top_solid(x, z) + 1
            c.set(x, y, z, Pal("mossy_cobblestone", "cobblestone", "mossy_stone_bricks", seed=71))
            if h3(x, 11, z, 72) < 0.5:
                c.set(x, y + 1, z, Pal("mossy_cobblestone_wall", "cobblestone_wall", seed=73))


def ground_cover(c: Canvas):
    deco = Pal(("short_grass", 30), ("fern", 4), ("dandelion", 1), ("oxeye_daisy", 1), ("azure_bluet", 1), seed=80)
    for x in range(1, HALF):
        for z in range(1, SZ - 1):
            y = c.top(x, z)
            s = c.get(x, y, z)
            if s.startswith("grass_block") and h3(x, 12, z, 81) < 0.35:
                c.set(x, y + 1, z, deco)
            elif s.startswith("podzol") and h3(x, 12, z, 82) < 0.2:
                c.set(x, y + 1, z, "fern")


def border(c: Canvas):
    for x in range(SX):
        for z in range(SZ):
            if x in (0, SX - 1) or z in (0, SZ - 1):
                h = c.top_solid(x, z)
                for y in range(max(h + 1, 1), SY):
                    c.set(x, y, z, "barrier")


def build():
    c = CANVAS
    c.b.clear()
    terrain(c)
    trench(c)
    creek_crossings(c)
    fort(c)
    ruin(c)
    cabin(c)
    north_meadow(c)
    woods(c)
    ground_cover(c)
    c.rotate_west_half()
    c.drop_floating()
    border(c)
    c.fix_connections()
