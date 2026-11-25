"""Molenwaard: a bomb map (paintball:bomb only) in a wooded river valley.

Not symmetric, like a bomb map should be: the attackers start in a woodcutters' camp in the west
(spawn 0), the defenders at the forester's lodge in the east (spawn 1). A creek runs north to south
through the middle and can only be crossed at three places:

- north: a stony ford, then the long open meadow to site A;
- middle: a covered timber bridge, then the farmyard with paths to both sites;
- south: a narrow footbridge into a long drying barn that comes out on site B.

Site A is the yard in front of the watermill (north-east), site B the timber yard under the log
gantry (south-east). Both are much closer to the defenders' spawn than to the attackers'.
The arena flags 2 and 3 are the sites (see BombSites in the paintball plugin).
"""
from __future__ import annotations

import math

import analyze
import parts as P
from mcbuild import Canvas, Grad, Pal, fbm, h3
from woudfront import poly_dist, rect_dist

WORLD = "world"
SX, SY, SZ = 124, 40, 92
G = 8
ORIGIN = (300, -63, 600)   # south of Moerasdorp (z 400..499)
SEED = 77
MODES = ["paintball:bomb"]

CANVAS = Canvas(SX, SY, SZ, SEED)

# --------------------------------------------------------------------------- block-out

CREEK_Z = (5, 86)               # the creek ends in rounded pools inside the map
FORD_Z = (14, 19)
BRIDGE_Z = (43, 49)             # covered bridge, walls on z 43 and z 49
FOOT_Z = (71, 73)               # footbridge deck
MILL = (80, 5, 92, 13)          # site A's watermill
RACE = [(60, 9.5), (77.5, 9.5), (77.5, 4.5)]   # mill race from the creek to the wheel
YARD_A = (74, 16, 98, 29)
BARN = (66, 65, 77, 77)         # drying barn: west door from the footbridge, east door onto B
YARD_B = (78, 60, 101, 86)
OFFICE = (95, 61, 100, 66)
SHED = (88, 79, 98, 85)
FARM = (74, 37, 86, 53)         # farmhouse in the middle
LODGE = (104, 28, 117, 36)      # defenders' lodge
CAMP = (3, 34, 22, 58)          # attackers' camp
WOODSHED = (105, 57, 115, 63)
CT_YARD = (104, 39, 118, 56)    # defenders' spawn yard

SITE_A = (86.5, G + 1, 22.5)
SITE_B = (88.5, G + 1, 71.5)

PATHS = [
    # attackers: north lane to the ford and the meadow, middle to the bridge, south to the footbridge
    [(14, 42), (20, 31), (31, 22), (44, 18), (52, 16.5), (64, 16.5), (74, 19), (82, 21)],
    [(16, 46), (30, 47), (42, 45), (50, 46)],
    [(14, 50), (20, 61), (31, 70), (44, 72), (52, 72)],
    # middle: from the bridge round the farm to both sites
    [(66, 46), (70, 38), (76, 32), (82, 27)],
    [(66, 46), (70, 55), (74, 60), (82, 66)],
    # defenders: from the lodge yard to both sites and to the farm's east door
    [(108, 42), (102, 34), (96, 28), (90, 25)],
    [(108, 50), (103, 58), (98, 64), (92, 69)],
    [(104, 46), (94, 47), (87, 49)],
]

SPAWNS = {
    0: [(8.5, G + 1, 40.5), (8.5, G + 1, 44.5), (8.5, G + 1, 48.5), (8.5, G + 1, 52.5),
        (13.5, G + 1, 42.5), (13.5, G + 1, 50.5)],
    1: [(113.5, G + 1, 41.5), (113.5, G + 1, 45.5), (113.5, G + 1, 49.5), (113.5, G + 1, 53.5),
        (108.5, G + 1, 43.5), (108.5, G + 1, 51.5)],
}
FLAGS = {2: SITE_A, 3: SITE_B}
YAW = {0: -90.0, 1: 90.0, 2: 0.0, 3: 0.0}
POWERUPS = []
CLOSEUPS = {"siteA": (66, 0, 104, 32), "siteB": (62, 58, 104, 90), "mid": (46, 34, 92, 58),
            "camp": (0, 30, 26, 62)}

BUILT = [MILL, YARD_A, BARN, YARD_B, FARM, LODGE, CAMP, CT_YARD]


# --------------------------------------------------------------------------- helpers


def creek_x(z):
    return 58 + 2.5 * math.sin((z - 46) / 13)


def creek_d(x, z):
    """Distance to the creek's centre line, with round ends."""
    zz = min(max(z, CREEK_Z[0]), CREEK_Z[1])
    return math.hypot(x - creek_x(zz), z - zz)


def race_d(x, z):
    return poly_dist(x, z, RACE)


def on_path(x, z, w=1.4):
    return any(poly_dist(x, z, p) < w + 0.6 * h3(x, 1, z, 3) for p in PATHS)


def height(x, z):
    n = (fbm(x, z, 24, SEED) - 0.5) * 5
    flat = 0.0
    for r in BUILT:
        flat = max(flat, 1 - min(1, rect_dist(x, z, r) / 6))
    flat = max(flat, 1 - min(1, creek_d(x, z) / 11))
    flat = max(flat, 1 - min(1, min(poly_dist(x, z, p) for p in PATHS) / 4))
    edge = min(x, z, SX - 1 - x, SZ - 1 - z)
    rise = max(0, 6 - edge) * 0.8
    return G + round(n * (1 - flat) + rise)


def put_on_ground(c, x, z, state, dy=1):
    c.set(x, c.top_solid(x, z) + dy, z, state)


FONT = {
    "A": [".#.", "#.#", "###", "#.#", "#.#"],
    "B": ["##.", "#.#", "##.", "#.#", "##."],
}


def letter(c: Canvas, ch, x, y, z, along="x", step=1):
    """A big painted letter on a wall: a dark board with the letter in yellow, flush in the wall.
    (x, y, z) is the board's bottom-left corner seen from outside; the board runs `along` x or z
    in direction `step`, 5 wide and 7 high."""
    rows = FONT[ch]
    for i in range(5):
        for j in range(7):
            px, pz = (x + i * step, z) if along == "x" else (x, z + i * step)
            lit = 1 <= i <= 3 and 1 <= j <= 5 and rows[5 - j][i - 1] == "#"
            c.set(px, y + j, pz, "yellow_terracotta" if lit else "dark_oak_planks")


def log_stack(c: Canvas, x, y, z, length=5, axis="x", kind="spruce", rows=(3, 2, 1)):
    """Stacked logs lying along `axis`: three wide at the bottom, then two, then one."""
    for row, w in enumerate(rows):
        for j in range(w):
            for i in range(length):
                off = j + (row + 1) // 2 if row else j
                px, pz = (x + i, z + off) if axis == "x" else (x + off, z + i)
                c.set(px, y + row, pz, f"{kind}_log[axis={axis}]")


def sacks(c: Canvas, x, z, w, d, seed):
    pal = Pal(("white_wool", 3), ("light_gray_wool", 2), ("brown_wool", 1), seed=seed)
    for i in range(w):
        for k in range(d):
            y = c.top_solid(x + i, z + k) + 1
            c.set(x + i, y, z + k, pal)
            if h3(x + i, 0, z + k, seed) < 0.45:
                c.set(x + i, y + 1, z + k, "white_wool" if h3(x, 1, z, seed) < 0.5 else "light_gray_wool")


def stone_wall(c: Canvas, x1, z1, x2, z2, height=1.5, gaps=(), seed=0):
    """A dry-stone field wall following the ground, with a slab cap (shoot over standing)."""
    stone = Pal(("cobblestone", 3), ("mossy_cobblestone", 2), ("stone", 1), ("andesite", 1), seed=seed)
    cap = Pal(("cobblestone_slab[type=bottom]", 2), ("mossy_cobblestone_slab[type=bottom]", 1), seed=seed + 1)
    n = max(abs(x2 - x1), abs(z2 - z1), 1)
    for i in range(n + 1):
        t = i / n
        x, z = round(x1 + (x2 - x1) * t), round(z1 + (z2 - z1) * t)
        if any(a <= (x if x1 != x2 else z) <= b for a, b in gaps):
            continue
        y = c.top_solid(x, z) + 1
        for k in range(int(height)):
            c.set(x, y + k, z, stone)
        if height - int(height) >= 0.5:
            c.set(x, y + int(height), z, cap)


def clear(c: Canvas, x1, z1, x2, z2, y1=G + 1, y2=G + 18):
    c.box(x1, y1, z1, x2, y2, z2, "air")


# --------------------------------------------------------------------------- terrain


def terrain(c: Canvas):
    grass = "grass_block[snowy=false]"
    path_pal = Pal(("dirt_path", 5), ("coarse_dirt", 2), ("gravel", 1), seed=3)
    bank = Pal(("gravel", 3), ("mud", 2), ("clay", 1), ("coarse_dirt", 2), seed=4, scale=2)
    bed = Pal(("gravel", 3), ("sand", 1), ("clay", 1), ("mud", 1), seed=5, scale=2)
    forest_floor = Pal((grass, 6), ("podzol[snowy=false]", 2), ("coarse_dirt", 1), ("moss_block", 1), seed=6, scale=3)
    ford = Pal(("mossy_cobblestone", 2), ("cobblestone", 2), ("stone", 1), ("andesite", 1), seed=10)
    for x in range(SX):
        for z in range(SZ):
            h = height(x, z)
            cd = creek_d(x, z)
            rd = race_d(x, z)
            top = grass
            in_ford = FORD_Z[0] <= z <= FORD_Z[1]
            if cd < 3.2 or rd < 1.0:
                floor = G - 3 if cd < 1.8 and rd >= 1.0 else G - 2
                c.column(x, z, 0, floor - 1, "stone")
                c.set(x, floor, z, bed)
                for y in range(floor + 1, G):
                    c.set(x, y, z, "water")
                if in_ford and cd < 3.2 and (z in (16, 17) or h3(x, 0, z, 9) < 0.55):
                    # stepping stones level with the banks: the ford is walkable, two abreast
                    c.column(x, z, floor + 1, G - 1, "stone")
                    c.set(x, G - 1, z, ford)
                continue
            if cd < 5.5 or rd < 2.2:
                h = G - 1 if (cd < 4.4 or rd < 1.6) else G
                top = bank
            elif on_path(x, z):
                top = path_pal
            elif fbm(x, z, 14, SEED + 9) > 0.58 or (x < 50 and z > 60):
                top = forest_floor
            c.column(x, z, 0, h - 4, "stone")
            c.column(x, z, h - 3, h - 1, "dirt")
            c.set(x, h, z, top)


# --------------------------------------------------------------------------- crossings


def covered_bridge(c: Canvas):
    """Timber bridge with plank walls, windows and a gable roof: the middle crossing."""
    z1, z2 = BRIDGE_Z
    x1, x2 = 50, 66
    deck = Pal(("spruce_planks", 4), ("stripped_spruce_log[axis=x]", 1), seed=15)
    wall = Pal(("spruce_planks", 5), ("stripped_spruce_log[axis=y]", 1), seed=16)
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            c.set(x, G, z, deck)
            for y in range(G + 1, G + 9):
                c.set(x, y, z, "air")
        # walls: posts every 4, windows in between at shoulder height
        for z in (z1, z2):
            post = (x - x1) % 4 == 0
            for y in range(G + 1, G + 5):
                window = not post and y in (G + 2, G + 3) and 1 <= (x - x1) % 4 <= 3 and x1 + 2 < x < x2 - 2
                if window:
                    continue
                c.set(x, y, z, "spruce_log[axis=y]" if post else wall)
            c.set(x, G, z, "spruce_log[axis=x]")
        # piers under the deck in the water
        if (x - x1) % 4 == 0:
            for z in (z1, z2):
                c.column(x, z, G - 4, G - 1, "stripped_spruce_log[axis=y]")
    # roof: stairs rising from both walls, ridge along x
    for i in range(4):
        y = G + 5 + i
        za, zb = z1 - 1 + i, z2 + 1 - i
        for x in range(x1 - 1, x2 + 2):
            c.set(x, y, za, "dark_oak_stairs[facing=south,half=bottom,shape=straight]")
            c.set(x, y, zb, "dark_oak_stairs[facing=north,half=bottom,shape=straight]")
            for z in range(za + 1, zb):
                if i == 3:
                    c.set(x, y, z, "dark_oak_slab[type=bottom]")
        for x in (x1, x2):
            for z in range(za + 1, zb):
                if i < 3:
                    c.set(x, y, z, "spruce_planks")
    for x in range(x1, x2 + 1):
        for z in (z1, z2):
            c.set(x, G + 5, z, "spruce_planks")
    # gable openings stay open below the roof; tie beams inside, lanterns hanging from them
    for x in range(x1 + 2, x2 - 1, 4):
        for z in range(z1 + 1, z2):
            c.set(x, G + 5, z, "spruce_log[axis=z]")
        c.set(x, G + 4, (z1 + z2) // 2, "lantern[hanging=true]")
    # a few crates inside to hide behind, never blocking the lane
    P.crate_stack(c, 56, G + 1, z1 + 1, w=2, d=1, h=1, seed=17)
    P.crate_stack(c, 61, G + 1, z2 - 1, w=1, d=1, h=2, seed=18)
    # stone abutments at both ends
    for x in (x1 - 1, x2 + 1):
        for z in range(z1 - 1, z2 + 2):
            if z in (z1 - 1, z2 + 1):
                c.column(x, z, G - 1, G + 1, Pal("stone_bricks", "mossy_stone_bricks", "cobblestone", seed=19))


def footbridge(c: Canvas):
    z1, z2 = FOOT_Z
    cx = creek_x(72)
    xa, xb = int(cx - 6), int(cx + 6)
    for x in range(xa, xb + 1):
        for z in range(z1, z2 + 1):
            c.set(x, G, z, Pal(("spruce_planks", 3), ("spruce_slab[type=top]", 1), seed=20))
            for y in range(G + 1, G + 4):
                c.set(x, y, z, "air")
        for z in (z1 - 1, z2 + 1):
            c.set(x, G, z, "spruce_log[axis=x]")
            c.set(x, G + 1, z, "spruce_log[axis=y]" if (x - xa) % 3 == 0 else "spruce_fence")
        if (x - xa) % 3 == 0:
            for z in (z1 - 1, z2 + 1):
                c.column(x, z, G - 4, G - 1, "stripped_spruce_log[axis=y]")


# --------------------------------------------------------------------------- site A: the mill


def waterwheel(c: Canvas, cx, cy, cz, r=4):
    """An undershot wheel in the y-z plane at x = cx: rim, spokes, paddles and a hub into the mill."""
    rim = Pal(("stripped_spruce_log[axis=x]", 1), seed=30)
    for a in range(0, 360, 6):
        t = math.radians(a)
        y, z = round(cy + math.sin(t) * r), round(cz + math.cos(t) * r)
        c.set(cx, y, z, "stripped_spruce_wood[axis=x]")
    for a in range(0, 360, 45):
        t = math.radians(a)
        for k in range(1, r):
            y, z = round(cy + math.sin(t) * k), round(cz + math.cos(t) * k)
            c.set(cx, y, z, "spruce_fence" if k < r - 1 else "spruce_planks")
        # paddles stick out past the rim
        y, z = round(cy + math.sin(t) * (r + 1)), round(cz + math.cos(t) * (r + 1))
        c.set(cx, y, z, "spruce_planks")
    c.set(cx, cy, cz, rim)
    c.set(cx + 1, cy, cz, "spruce_log[axis=x]")


def mill(c: Canvas):
    x1, z1, x2, z2 = MILL
    clear(c, x1 - 1, z1 - 1, x2 + 1, z2 + 1)
    base = Grad([Pal(("mossy_cobblestone", 3), ("cobblestone", 3), ("mossy_stone_bricks", 1), seed=31),
                 Pal(("cobblestone", 3), ("stone_bricks", 2), ("andesite", 1), seed=32)], G + 1, G + 4, seed=33)
    timber = Pal(("spruce_planks", 5), ("stripped_spruce_log[axis=y]", 1), seed=34)
    floor = Pal(("spruce_planks", 3), ("cobblestone", 1), seed=35)
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            c.set(x, G, z, floor)
            c.column(x, z, G - 3, G - 1, "cobblestone")
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            if x not in (x1, x2) and z not in (z1, z2):
                continue
            corner = x in (x1, x2) and z in (z1, z2)
            for y in range(G + 1, G + 9):
                if corner:
                    c.set(x, y, z, "stripped_dark_oak_log[axis=y]" if y > G + 4 else "stone_bricks")
                elif y <= G + 4:
                    c.set(x, y, z, base)
                else:
                    post = (x - x1) % 4 == 0 if z in (z1, z2) else (z - z1) % 4 == 0
                    c.set(x, y, z, "dark_oak_log[axis=y]" if post else timber)
    # jetty: the upper storey's floor beam sticks out a block on the yard side
    for x in range(x1, x2 + 1):
        c.set(x, G + 5, z2 + 1, "dark_oak_log[axis=x]")
    # upper floor inside
    for x in range(x1 + 1, x2):
        for z in range(z1 + 1, z2):
            c.set(x, G + 5, z, Pal(("spruce_planks", 4), ("dark_oak_planks", 1), seed=36))
    # doors: wide yard door (south), side door east, windows
    for x in (83, 84, 85):
        for y in (G + 1, G + 2, G + 3):
            c.set(x, y, z2, "air")
    for z in (8, 9):
        for y in (G + 1, G + 2, G + 3):
            c.set(x2, y, z, "air")
    c.column(82, z2 + 1, G + 1, G + 4, "spruce_log[axis=y]")
    c.column(86, z2 + 1, G + 1, G + 4, "spruce_log[axis=y]")
    for x in range(82, 87):
        c.set(x, G + 4, z2 + 1, "spruce_log[axis=x]")
    for wx in (x1 + 2, x2 - 2):
        for y in (G + 2, G + 3):
            c.set(wx, y, z1, "air")
        c.set(wx, G + 1, z1 - 1, "stone_brick_stairs[facing=south,half=top,shape=straight]")
    for (wx, wz) in ((x1, 8), (x1, 10)):
        c.set(wx, G + 7, wz, "glass_pane")
    for wx in (x1 + 2, x1 + 6):
        c.set(wx, G + 7, z2, "glass_pane")
        c.set(wx, G + 6, z2 + 1, "spruce_trapdoor[facing=south,half=top,open=false]")
    # big painted A over the yard door's right side
    letter(c, "A", 87, G + 1, z2, along="x", step=1)
    # steep gable roof along x with overhang
    half = (z2 - z1) // 2 + 1
    for i in range(half + 1):
        y = G + 9 + i
        za, zb = z1 - 1 + i, z2 + 1 - i
        for x in range(x1 - 1, x2 + 2):
            if za > zb:
                continue
            if za == zb:
                c.set(x, y, za, "dark_oak_slab[type=bottom]")
                continue
            c.set(x, y, za, "dark_oak_stairs[facing=south,half=bottom,shape=straight]")
            c.set(x, y, zb, "dark_oak_stairs[facing=north,half=bottom,shape=straight]")
        for z in range(za + 1, zb):
            for x in (x1, x2):
                c.set(x, y, z, timber)
    c.set(x1 - 2, G + 9 + half - 1, (z1 + z2) // 2, "spruce_log[axis=x]")
    c.set(x2 + 2, G + 9 + half - 1, (z1 + z2) // 2, "spruce_log[axis=x]")
    # inside: millstones, sacks, a ladder to the loft
    c.disc(87, G + 1, 9, 1.5, "smooth_stone")
    c.disc(87, G + 2, 9, 1.5, "smooth_stone_slab[type=bottom]")
    c.set(87, G + 2, 9, "grindstone[face=floor,facing=north]")
    sacks(c, x1 + 1, z1 + 1, 2, 2, seed=37)
    P.crate_stack(c, x2 - 2, G + 1, z1 + 1, w=1, d=2, h=2, seed=38)
    for y in range(G + 1, G + 6):
        c.set(x1 + 1, y, z2 - 2, "ladder[facing=east]")
    c.set(x1 + 1, G + 5, z2 - 2, "air")
    c.set(x1 + 1, G + 5, z2 - 2, "ladder[facing=east]")
    c.set(x1 + 4, G + 8, 9, "lantern[hanging=true]")
    # the wheel in the race on the west side
    waterwheel(c, x1 - 2, G + 1, 7, r=3)
    c.set(x1 - 1, G + 1, 7, "spruce_log[axis=x]")


def yard_a(c: Canvas):
    x1, z1, x2, z2 = YARD_A
    cobbles = Pal(("cobblestone", 3), ("gravel", 2), ("coarse_dirt", 2), ("andesite", 1), ("mossy_cobblestone", 1),
                  seed=40, scale=2)
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            if rect_dist(x, z, (x1 + 2, z1, x2 - 2, z2 - 2)) < 2.2:
                c.set(x, G, z, cobbles)
                for y in range(G + 1, G + 10):
                    c.set(x, y, z, "air")
    sx, _, sz = (int(v) for v in SITE_A)
    # the site: a hay wagon, sacks, a leaning millstone, crates
    for x in range(sx - 4, sx - 1):
        for z in (sz - 1, sz):
            c.set(x, G + 1, z, "spruce_slab[type=top]")
            c.set(x, G + 2, z, "hay_block[axis=x]")
    c.set(sx - 4, G + 3, sz - 1, "hay_block[axis=x]")
    for (wx, wz) in ((sx - 4, sz - 2), (sx - 2, sz - 2), (sx - 4, sz + 1), (sx - 2, sz + 1)):
        c.set(wx, G + 1, wz, "dark_oak_trapdoor[facing=" + ("north" if wz < sz else "south") + ",half=bottom,open=true]")
    c.set(sx - 5, G + 1, sz, "spruce_fence_gate[facing=east,open=false,in_wall=false]")
    for (dy, dz) in ((0, -1), (0, 0), (0, 1), (1, -1), (1, 0), (1, 1), (2, 0)):
        c.set(sx + 3, G + 1 + dy, sz + dz, "smooth_stone")
    c.set(sx + 3, G + 2, sz, "polished_andesite")
    sacks(c, sx + 1, sz + 3, 3, 1, seed=41)
    P.crate_stack(c, sx - 1, G + 1, sz - 5, w=2, d=1, h=2, seed=42)
    c.set(sx + 5, G + 1, sz - 3, "barrel[facing=up,open=false]")
    c.set(sx + 5, G + 1, sz - 2, "barrel[facing=up,open=false]")
    c.set(sx + 5, G + 2, sz - 3, "barrel[facing=north,open=false]")
    # the yard's west side, toward the meadow: a stone wall with a gate gap, and a trough
    stone_wall(c, x1 + 1, z1 + 2, x1 + 1, z2 - 3, height=1.5, gaps=((19, 21),), seed=43)
    for z in range(z2 - 6, z2 - 3):
        c.set(x1 + 3, G + 1, z, "cauldron")
    # south side: a woodpile and a lamp toward the farm path
    log_stack(c, sx - 3, G + 1, z2 - 2, length=5, axis="x", kind="oak", rows=(2, 1))
    P.lamp_post(c, sx + 6, G + 1, z2 - 3)
    # a well toward the defenders' side
    wx, wz = x2 - 4, z1 + 4
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if dx or dz:
                c.set(wx + dx, G + 1, wz + dz, "stone_bricks")
    c.set(wx, G, wz, "water")
    c.set(wx, G - 1, wz, "stone")
    for (dx, dz) in ((-1, -1), (1, 1)):
        c.column(wx + dx, wz + dz, G + 2, G + 3, "spruce_fence")
    c.set(wx - 1, G + 4, wz - 1, "spruce_planks")
    c.set(wx + 1, G + 4, wz + 1, "spruce_planks")
    c.set(wx, G + 4, wz, "spruce_slab[type=bottom]")
    c.set(wx - 1, G + 4, wz, "spruce_slab[type=bottom]")
    c.set(wx + 1, G + 4, wz, "spruce_slab[type=bottom]")
    c.set(wx, G + 3, wz, "iron_chain[axis=y]")
    c.set(wx, G + 4, wz - 1, "spruce_slab[type=bottom]")
    c.set(wx, G + 4, wz + 1, "spruce_slab[type=bottom]")


def meadow(c: Canvas):
    """The north lane between the ford and site A: open, with a few walls and hay stacks."""
    stone_wall(c, 64, 23, 73, 23, height=1.5, gaps=((68, 69),), seed=44)
    for (x, z) in ((66, 12), (67, 12), (66, 13), (70, 20)):
        put_on_ground(c, x, z, "hay_block[axis=y]")
    P.boulder(c, 47, c.top_solid(47, 12) + 1, 12, r=2.0, seed=45)
    P.boulder(c, 39, c.top_solid(39, 25) + 1, 25, r=1.8, seed=46)
    P.fallen_log(c, 30, c.top_solid(30, 15) + 1, 15, length=6, axis="x", seed=47)
    P.fallen_log(c, 49, c.top_solid(49, 24) + 1, 24, length=5, axis="z", seed=48)
    # hedge rows along the meadow's south side (cover from the middle)
    for x in range(36, 50):
        if h3(x, 0, 30, 49) < 0.8:
            P.bush(c, x, c.top_solid(x, 30) + 1, 30, r=1.1, seed=x)


# --------------------------------------------------------------------------- site B: timber yard


def barn(c: Canvas):
    """The long drying barn on the south crossing: slatted walls, plank stacks inside, doors at both
    ends. The attackers go through it onto site B; the defenders hold its east door."""
    x1, z1, x2, z2 = BARN
    clear(c, x1, z1, x2, z2)
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            c.set(x, G, z, Pal(("coarse_dirt", 2), ("spruce_planks", 2), ("gravel", 1), seed=50))
    slat = Pal(("spruce_planks", 3), ("dark_oak_planks", 1), seed=51)
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            if x not in (x1, x2) and z not in (z1, z2):
                continue
            corner = x in (x1, x2) and z in (z1, z2)
            for y in range(G + 1, G + 6):
                if corner or (z in (z1, z2) and (x - x1) % 4 == 0) or (x in (x1, x2) and (z - z1) % 4 == 0):
                    c.set(x, y, z, "spruce_log[axis=y]")
                elif y in (G + 3, G + 4) and z in (z1, z2) and (x + y) % 2 == 0:
                    c.set(x, y, z, "spruce_fence")    # drying slats: see through, shoot through
                else:
                    c.set(x, y, z, slat)
    # doors: west from the footbridge, east onto B
    for z in (FOOT_Z[0], FOOT_Z[0] + 1, FOOT_Z[0] + 2):
        for y in (G + 1, G + 2, G + 3):
            c.set(x1, y, z, "air")
    for z in (69, 70, 71):
        for y in (G + 1, G + 2, G + 3):
            c.set(x2, y, z, "air")
    # roof along x
    half = (z2 - z1) // 2 + 1
    for i in range(half + 1):
        y = G + 6 + i
        za, zb = z1 - 1 + i, z2 + 1 - i
        for x in range(x1 - 1, x2 + 2):
            if za > zb:
                continue
            if za == zb:
                c.set(x, y, za, "spruce_slab[type=bottom]")
                continue
            c.set(x, y, za, "spruce_stairs[facing=south,half=bottom,shape=straight]")
            c.set(x, y, zb, "spruce_stairs[facing=north,half=bottom,shape=straight]")
        for z in range(za + 1, zb):
            for x in (x1, x2):
                c.set(x, y, z, slat)
    # inside: plank stacks on bearers, a zig-zag through them
    for (px, pz, w, d) in ((x1 + 2, z1 + 1, 3, 2), (x1 + 6, z1 + 5, 3, 3), (x1 + 2, z2 - 3, 3, 2),
                           (x1 + 8, z2 - 2, 2, 1)):
        for i in range(w):
            for k in range(d):
                c.set(px + i, G + 1, pz + k, "stripped_spruce_log[axis=x]")
                c.set(px + i, G + 2, pz + k, "spruce_planks")
                if h3(px + i, 0, pz + k, 52) < 0.6:
                    c.set(px + i, G + 3, pz + k, "spruce_slab[type=bottom]")
    c.set(x1 + 5, G + 5, (z1 + z2) // 2, "lantern[hanging=true]")
    for x in range(x1 + 1, x2):
        c.set(x, G + 6, (z1 + z2) // 2, "spruce_log[axis=x]")


def gantry(c: Canvas, x1, x2, z, top):
    """Log gantry crane over the timber yard: A-frame legs at both ends, a beam, a log bundle on chains."""
    for x in (x1, x2):
        for dz in (-2, 2):
            prev = None
            for y in range(G + 1, top):
                k = (y - G - 1) / max(1, top - G - 2)
                zz = z + round(dz * (1 - k))
                c.set(x, y, zz, "spruce_log[axis=y]")
                if prev is not None and prev != zz:
                    c.set(x, y, prev, "spruce_log[axis=y]")   # a full step, never a corner joint
                prev = zz
        c.set(x, G + 4, z - 1, "spruce_log[axis=z]")
        c.set(x, G + 4, z + 1, "spruce_log[axis=z]")
        c.set(x, G + 4, z, "spruce_log[axis=z]")
    for x in range(x1, x2 + 1):
        c.set(x, top, z, "dark_oak_log[axis=x]")
    # trolley and hanging load
    hx = (x1 + x2) // 2 + 2
    c.set(hx, top - 1, z, "iron_chain[axis=y]")
    c.set(hx, top - 2, z, "iron_chain[axis=y]")
    c.set(hx, top - 3, z, "iron_chain[axis=y]")
    for dx in (-2, -1, 0, 1, 2):
        c.set(hx + dx, top - 4, z, "oak_log[axis=x]")
        c.set(hx + dx, top - 4, z + 1, "oak_log[axis=x]")
    c.set(x1 + 1, top + 1, z, "lantern[hanging=false]")


def yard_b(c: Canvas):
    x1, z1, x2, z2 = YARD_B
    floor = Pal(("coarse_dirt", 3), ("gravel", 2), ("dirt_path", 1), ("podzol[snowy=false]", 1), seed=60, scale=2)
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            c.set(x, G, z, floor)
            for y in range(G + 1, G + 14):
                c.set(x, y, z, "air")
    sx, _, sz = (int(v) for v in SITE_B)
    # log stacks in rows: the site sits between them
    log_stack(c, sx - 7, G + 1, sz - 6, length=6, axis="x", kind="spruce")
    log_stack(c, sx + 2, G + 1, sz + 3, length=6, axis="x", kind="oak")
    log_stack(c, sx - 8, G + 1, sz + 4, length=4, axis="x", kind="birch", rows=(2, 1))
    log_stack(c, sx + 4, G + 1, sz - 8, length=5, axis="z", kind="spruce", rows=(2, 2))
    # plank piles and a saw horse on the site
    for i in range(3):
        c.set(sx - 3 + i, G + 1, sz + 2, "stripped_spruce_log[axis=x]")
        c.set(sx - 3 + i, G + 2, sz + 2, "spruce_slab[type=bottom]")
    c.set(sx + 2, G + 1, sz - 2, "spruce_planks")
    c.set(sx + 2, G + 2, sz - 2, "spruce_planks")
    c.set(sx + 3, G + 1, sz - 2, "spruce_planks")
    P.crate_stack(c, sx - 3, G + 1, sz - 3, w=1, d=1, h=2, seed=61)
    # the gantry spans the site along x
    gantry(c, sx - 9, sx + 9, sz - 1, G + 10)
    # saw shed (open sides, roof on posts) at the south edge
    a, b_, c2, d = SHED
    for x in range(a, c2 + 1):
        for z in range(b_, d + 1):
            c.set(x, G, z, Pal(("spruce_planks", 2), ("coarse_dirt", 1), seed=62))
    for (px, pz) in ((a, b_), (a, d), (c2, b_), (c2, d), ((a + c2) // 2, b_), ((a + c2) // 2, d)):
        c.column(px, pz, G + 1, G + 4, "spruce_log[axis=y]")
    for x in range(a - 1, c2 + 2):
        for z in range(b_ - 1, d + 2):
            c.set(x, G + 5, z, "spruce_slab[type=bottom]" if x in (a - 1, c2 + 1) or z in (b_ - 1, d + 1)
                  else "spruce_planks")
    for x in range(a, c2 + 1):
        c.set(x, G + 4, b_, "spruce_log[axis=x]")
        c.set(x, G + 4, d, "spruce_log[axis=x]")
    for x in range(a + 2, a + 7):
        c.set(x, G + 1, b_ + 3, "stripped_spruce_log[axis=x]")
    c.set(a + 4, G + 2, b_ + 3, "stonecutter[facing=north]")
    for z in range(b_ + 1, d):
        c.set(c2 - 1, G + 1, z, "spruce_planks")
        if z % 2 == 0:
            c.set(c2 - 1, G + 2, z, "spruce_slab[type=bottom]")
    # back wall of the shed (toward the map edge)
    for x in range(a, c2 + 1):
        for y in range(G + 1, G + 4):
            c.set(x, y, d, "spruce_planks" if (x - a) % 5 else "spruce_log[axis=y]")
    # yard office with the painted B facing the site
    ox1, oz1, ox2, oz2 = OFFICE
    for x in range(ox1, ox2 + 1):
        for z in range(oz1, oz2 + 1):
            c.set(x, G, z, "spruce_planks")
            edge = x in (ox1, ox2) or z in (oz1, oz2)
            for y in range(G + 1, G + 8):
                if edge:
                    c.set(x, y, z, "spruce_log[axis=y]" if (x in (ox1, ox2) and z in (oz1, oz2)) else
                          Pal(("spruce_planks", 3), ("stripped_spruce_log[axis=y]", 1), seed=63))
                else:
                    c.set(x, y, z, "air")
    letter(c, "B", ox1, G + 1, oz1 + 1, along="z", step=1)   # read from the west: z grows to the right
    for x in (ox1 + 2, ox1 + 3):     # door on the north side, toward the defenders' path
        for y in (G + 1, G + 2):
            c.set(x, y, oz1, "air")
    c.set(ox2, G + 3, oz1 + 2, "glass_pane")
    for x in range(ox1 - 1, ox2 + 2):
        for z in range(oz1 - 1, oz2 + 2):
            c.set(x, G + 8, z, "dark_oak_slab[type=bottom]" if x in (ox1 - 1, ox2 + 1) or z in (oz1 - 1, oz2 + 1)
                  else "dark_oak_planks")
    # a low plank fence on the yard's north side, two gaps (from the middle and from the lodge)
    for x in range(x1, x2 + 1):
        if 80 <= x <= 83 or 91 <= x <= 94 or x >= ox1 - 1:
            continue
        c.set(x, G + 1, z1, "spruce_fence")


# --------------------------------------------------------------------------- middle


def farm(c: Canvas):
    """Farmhouse in the middle: stone ground floor, timber gable, doors west (toward the bridge,
    offset so the bridge does not look straight through) and east (toward the lodge)."""
    x1, z1, x2, z2 = FARM
    clear(c, x1 - 1, z1 - 1, x2 + 1, z2 + 1)
    stone = Grad([Pal(("cobblestone", 3), ("mossy_cobblestone", 2), ("stone", 1), seed=70),
                  Pal(("stone_bricks", 3), ("cobblestone", 2), ("andesite", 1), seed=71)], G + 1, G + 4, seed=72)
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            c.set(x, G, z, Pal(("spruce_planks", 3), ("cobblestone", 1), seed=73))
            if x in (x1, x2) or z in (z1, z2):
                corner = x in (x1, x2) and z in (z1, z2)
                for y in range(G + 1, G + 5):
                    c.set(x, y, z, "stripped_oak_log[axis=y]" if corner else stone)
    # inner wall splits the house in two rooms, with a doorway
    for z in range(z1 + 1, z2):
        for y in range(G + 1, G + 5):
            c.set(80, y, z, "spruce_planks")
    for z in (45, 46):
        for y in (G + 1, G + 2):
            c.set(80, y, z, "air")
    # doors: west at z 40..41, east at z 50..51 (not in a line with the bridge or each other)
    for y in (G + 1, G + 2, G + 3):
        for z in (40, 41):
            c.set(x1, y, z, "air")
        for z in (50, 51):
            c.set(x2, y, z, "air")
    # windows with sills
    for (wx, wz, f) in ((x1, 46, "west"), (x1, 50, "west"), (x2, 40, "east"), (x2, 45, "east")):
        c.set(wx, G + 2, wz, "air")
        c.set(wx, G + 3, wz, "air")
    for wx in (x1 + 3, x2 - 3):
        for wz in (z1, z2):
            c.set(wx, G + 2, wz, "air")
            c.set(wx, G + 3, wz, "air")
    # gable roof along z
    half = (x2 - x1) // 2 + 1
    for i in range(half + 1):
        y = G + 5 + i
        xa, xb = x1 - 1 + i, x2 + 1 - i
        for z in range(z1 - 1, z2 + 2):
            if xa > xb:
                continue
            if xa == xb:
                c.set(xa, y, z, "spruce_slab[type=bottom]")
                continue
            c.set(xa, y, z, "spruce_stairs[facing=east,half=bottom,shape=straight]")
            c.set(xb, y, z, "spruce_stairs[facing=west,half=bottom,shape=straight]")
        for x in range(xa + 1, xb):
            for z in (z1, z2):
                c.set(x, y, z, Pal(("oak_planks", 3), ("stripped_oak_log[axis=y]", 1), seed=74))
    c.column(x1 + 2, z2 - 1, G + 1, G + 5 + half + 1, Pal("bricks", "cobblestone", "stone_bricks", seed=75))
    # inside: table, barrels, a hay corner
    c.set(77, G + 1, 47, "crafting_table")
    c.set(76, G + 1, 51, "barrel[facing=up,open=false]")
    c.set(77, G + 1, 51, "barrel[facing=up,open=false]")
    c.set(83, G + 1, 39, "hay_block[axis=x]")
    c.set(84, G + 1, 39, "hay_block[axis=x]")
    c.set(83, G + 1, 40, "hay_block[axis=z]")
    c.set(78, G + 4, 45, "lantern[hanging=true]")
    c.set(83, G + 4, 46, "lantern[hanging=true]")
    # outside: a fenced kitchen garden (south) and a cart (north)
    for x in range(x1, x2 + 1):
        if x in (79, 80):
            continue
        c.set(x, G + 1, z2 + 4, "oak_fence")
    for z in range(z2 + 1, z2 + 4):
        c.set(x1, G + 1, z, "oak_fence")
        c.set(x2, G + 1, z, "oak_fence")
    for x in range(x1 + 1, x2):
        for z in range(z2 + 1, z2 + 4):
            if (x + z) % 3:
                c.set(x, G, z, "rooted_dirt")
                if h3(x, 0, z, 76) < 0.5:
                    c.set(x, G + 1, z, "fern")
    for x in range(x1 + 2, x1 + 5):
        c.set(x, G + 1, z1 - 3, "spruce_slab[type=top]")
        c.set(x, G + 2, z1 - 3, "barrel[facing=east,open=false]" if x == x1 + 3 else "spruce_trapdoor[facing=north,half=bottom,open=false]")
    sacks(c, x2 - 3, z1 - 3, 2, 1, seed=77)


def orchard(c: Canvas):
    """Fruit trees in rows between the farm and site A (the short way up from the middle)."""
    for x in range(70, 96, 6):
        for z in (31, 35):
            if rect_dist(x, z, FARM) < 4 or on_path(x, z, 2.2):
                continue
            P.oak(c, x, c.top_solid(x, z) + 1, z, height=3, r=1.9, seed=x * 3 + z)
    P.sandbags(c, 89, 33, 93, 33, None, 1.5)


# --------------------------------------------------------------------------- spawns


def camp(c: Canvas):
    """The attackers' woodcutters' camp: tents behind the spawn line, a log pile, a lean-to."""
    x1, z1, x2, z2 = CAMP
    floor = Pal(("coarse_dirt", 3), ("grass_block[snowy=false]", 2), ("dirt_path", 2), ("podzol[snowy=false]", 1),
                seed=80, scale=2)
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            if rect_dist(x, z, (x1 + 2, z1 + 2, x2 - 2, z2 - 2)) < 2.5:
                c.set(x, G, z, floor)
                for y in range(G + 1, G + 10):
                    c.set(x, y, z, "air")
    P.tent(c, 3, G + 1, 37, length=5, facing="x", cloth="brown_wool", seed=81)
    P.tent(c, 3, G + 1, 55, length=5, facing="x", cloth="green_wool", seed=82)
    log_stack(c, 3, G + 1, 45, length=4, axis="z", kind="spruce", rows=(2, 1))
    c.set(11, G + 1, 46, "campfire[lit=true,signal_fire=false,facing=north,waterlogged=false]")
    for (x, z, f) in ((10, 46, "east"), (12, 46, "west")):
        c.set(x, G + 1, z, f"spruce_stairs[facing={f},half=bottom,shape=straight]")
    # cover in front of the spawn line: log stacks and a chopping block
    log_stack(c, 17, G + 1, 40, length=3, axis="z", kind="oak", rows=(2, 1))
    log_stack(c, 17, G + 1, 51, length=3, axis="z", kind="oak", rows=(2, 1))
    P.crate_stack(c, 18, G + 1, 46, w=1, d=2, h=1, seed=83)
    c.set(15, G + 1, 47, "oak_log[axis=y]")
    P.lamp_post(c, 6, G + 1, 46)


def lodge(c: Canvas):
    """The defenders' forester's lodge north of their spawn yard, a woodshed south of it."""
    from basiskamp import hall
    x1, z1, x2, z2 = LODGE
    hall(c, x1, z1, x2, z2, h=G, door_x=None)
    for x in (110, 111):            # a second door onto the spawn yard
        for y in (G + 1, G + 2, G + 3):
            c.set(x, y, z2, "air")
    floor = Pal(("coarse_dirt", 2), ("gravel", 1), ("dirt_path", 2), ("grass_block[snowy=false]", 2), seed=90, scale=2)
    for x in range(102, 120):
        for z in range(38, 58):
            if rect_dist(x, z, CT_YARD) < 1.5:
                c.set(x, G, z, floor)
                for y in range(G + 1, G + 10):
                    c.set(x, y, z, "air")
    # woodshed: roof on posts with stacked logs
    for (px, pz) in ((106, 58), (106, 62), (114, 58), (114, 62)):
        c.column(px, pz, G + 1, G + 3, "spruce_log[axis=y]")
    for x in range(105, 116):
        for z in range(57, 64):
            c.set(x, G + 4, z, "spruce_slab[type=bottom]")
    log_stack(c, 107, G + 1, 59, length=7, axis="x", kind="spruce", rows=(3, 2))
    # cover between the spawn and the site paths
    P.crate_stack(c, 105, G + 1, 41, w=1, d=1, h=2, seed=91)
    P.crate_stack(c, 105, G + 1, 52, w=1, d=1, h=2, seed=92)
    P.lamp_post(c, 116, G + 1, 47)
    c.set(117, G + 1, 44, "barrel[facing=up,open=false]")


# --------------------------------------------------------------------------- nature


def woods(c: Canvas):
    def blocked(x, z, pad):
        for r in BUILT + [SHED, OFFICE, WOODSHED]:
            if rect_dist(x, z, r) < 3.5 + pad:
                return True
        if creek_d(x, z) < 6.5 + pad or race_d(x, z) < 3.5 + pad:
            return True
        if any(poly_dist(x, z, p) < 2.8 + pad for p in PATHS):
            return True
        if BRIDGE_Z[0] - 3 <= z <= BRIDGE_Z[1] + 3 and 44 <= x <= 72:
            return True
        return False

    step = 5
    for gx in range(0, SX, step):
        for gz in range(0, SZ, step):
            x = gx + int(h3(gx, 5, gz, 60) * step)
            z = gz + int(h3(gz, 6, gx, 61) * step)
            if x >= SX or z >= SZ:
                continue
            border = x < 5 or z < 5 or z > SZ - 6 or x > SX - 6
            if not border and blocked(x, z, 0):
                continue
            if border:
                dens = 0.95
            elif x < 50 and z > 58:
                dens = 0.8                       # dense south forest on the attackers' side
            elif x < 50 and z < 30:
                dens = 0.22                      # the open north meadow
            elif x > 64:
                dens = 0.35
            else:
                dens = 0.3 + 0.45 * fbm(x, z, 20, SEED + 2)
            r = h3(x, 7, z, 62)
            if r > dens:
                if not border and r < dens + 0.25 and not blocked(x, z, 0):
                    P.bush(c, x, c.top_solid(x, z) + 1, z, r=1.2 + h3(x, 8, z, 63), seed=x * 7 + z)
                continue
            y = c.top_solid(x, z) + 1
            kind = h3(x, 9, z, 64)
            if border and kind < 0.5 or kind < 0.2:
                P.spruce(c, x, y, z, height=8 + int(h3(x, 1, z, 65) * 5), seed=x + z)
            elif kind < 0.4:
                P.birch(c, x, y, z, height=5 + int(h3(x, 2, z, 66) * 3), seed=x + z)
            else:
                P.oak(c, x, y, z, height=4 + int(h3(x, 3, z, 67) * 3), r=2.3 + h3(x, 4, z, 68), seed=x + z)
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if h3(x + dx, 0, z + dz, 69) < 0.3 and c.empty(x + dx, y, z + dz):
                    c.set(x + dx, y, z + dz, "fern")
    # south forest: boulders and fallen trunks
    for (x, z, r) in ((26, 80, 2.4), (40, 64, 1.8), (22, 66, 2.0), (36, 84, 2.2)):
        if not blocked(x, z, -2):
            P.boulder(c, x, c.top_solid(x, z) + 1, z, r=r, seed=x)
    for (x, z, a) in ((28, 76, "x"), (42, 79, "z"), (34, 60, "x")):
        if not blocked(x, z, -1):
            P.fallen_log(c, x, c.top_solid(x, z) + 1, z, length=5, axis=a, seed=x)
    # middle west: a charcoal stack and a hunters' hide by the centre path
    P.boulder(c, 35, c.top_solid(35, 40) + 1, 40, r=1.7, seed=96)
    P.boulder(c, 38, c.top_solid(38, 53) + 1, 53, r=1.9, seed=97)
    P.sandbags(c, 46, 41, 46, 43, None, 1.5)
    P.sandbags(c, 46, 49, 46, 51, None, 1.5)
    # defenders' side of the creek: banks with reeds and a boulder at each crossing
    P.boulder(c, 70, c.top_solid(70, 13) + 1, 13, r=1.6, seed=98)
    P.boulder(c, 69, c.top_solid(69, 80) + 1, 80, r=1.8, seed=99)


def ground_cover(c: Canvas):
    deco = Pal(("short_grass", 30), ("fern", 4), ("dandelion", 1), ("oxeye_daisy", 1), ("azure_bluet", 1), seed=80)
    for x in range(1, SX - 1):
        for z in range(1, SZ - 1):
            y = c.top(x, z)
            s = c.get(x, y, z)
            if s.startswith("grass_block") and h3(x, 12, z, 81) < 0.35:
                c.set(x, y + 1, z, deco)
            elif s.startswith("podzol") and h3(x, 12, z, 82) < 0.2:
                c.set(x, y + 1, z, "fern")
            elif s.startswith("mud") and h3(x, 12, z, 83) < 0.3:
                c.set(x, y + 1, z, "short_grass")


def border(c: Canvas):
    """Barrier in every non-full block of the edge columns (also under tree canopies)."""
    from mcbuild import is_full
    for x in range(SX):
        for z in range(SZ):
            if x in (0, SX - 1) or z in (0, SZ - 1):
                for y in range(1, SY):
                    if not is_full(c.b.get((x, y, z))):
                        c.set(x, y, z, "barrier")


def build():
    c = CANVAS
    c.b.clear()
    terrain(c)
    covered_bridge(c)
    footbridge(c)
    meadow(c)
    camp(c)
    lodge(c)
    farm(c)
    orchard(c)
    yard_a(c)
    mill(c)
    yard_b(c)
    barn(c)
    woods(c)
    ground_cover(c)
    c.drop_floating()
    border(c)
    c.fix_connections()


def report():
    """The arena checks with the bomb sites named, plus the run lengths from each spawn."""
    lines, area = analyze.report(CANVAS, SPAWNS, FLAGS)
    lines = [ln.replace("team 2 flag", "site A").replace("team 3 flag", "site B")
             .replace("team 0", "attackers (spawn 0)").replace("team 1", "defenders (spawn 1)") for ln in lines]
    lines += bomb_distances(CANVAS, SPAWNS, FLAGS, area)
    return lines


def bomb_distances(c, spawns, sites, area):
    """Walking distance (in blocks, along the floor) from each spawn to each site."""
    from collections import deque
    cells = set(area)
    out = []
    for t, label in ((0, "attackers"), (1, "defenders")):
        sx, sy, sz = (int(v) for v in spawns[t][0])
        start = next(((sx, sy - d, sz) for d in range(4) if (sx, sy - d, sz) in cells), None)
        if start is None:
            out.append(f"{label}: spawn not on the floor")
            continue
        dist = {start: 0}
        q = deque([start])
        while q:
            p = q.popleft()
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                for dy in (0, 1, -1, -2, -3):
                    n = (p[0] + dx, p[1] + dy, p[2] + dz)
                    if n in cells and n not in dist:
                        dist[n] = dist[p] + 1
                        q.append(n)
                        break
        for k, name in ((2, "A"), (3, "B")):
            fx, fy, fz = (int(v) for v in sites[k])
            best = min((d for (x, y, z), d in dist.items() if abs(x - fx) <= 1 and abs(z - fz) <= 1), default=None)
            out.append(f"{label} to site {name}: {best if best is not None else 'NOT REACHABLE'} blocks")
    return out
