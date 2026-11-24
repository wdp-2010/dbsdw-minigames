"""Basiskamp: the minigame server's lobby, a woodland camp in the style of Woudfront.

The player arrives on the parade ground in the middle (world 0, 0) facing north, where the
practice range runs into the trees. Around the parade ground:

  north      practice range (log palisade lanes, firing shed, earth berm), two leaderboard plinths
  west       paintball HQ: a palisade compound with a command hall, watchtower and the game NPC
  east       friends' camp (private rooms): campfire circle, tents and a cabin
  south      the games square under a timber arch, one stand per game with its NPC:
             Valvloer (a tile floor with holes over a hay pit, under a gantry), Schaken (a
             giant chess board with pieces mid-game), Verstoppertje (a timber yard full of
             hiding places)
  further    a creek with a bridge, the camp gate between two towers, barracks (NW), a supply
             depot with a water tower (NE), a motor pool (SE), forest and hills all round

Ground is at world y -59 (local G = 4), like the arenas (-55) it sits above the flat world.
World = local - 56 in x and z.
"""
from __future__ import annotations

import json
import math

import parts as P
from duinbasis import jeep
from mcbuild import Canvas, Grad, Pal, fbm, h3, is_full
from woudfront import poly_dist, rect_dist

LOBBY = True
WORLD = "world"
SX, SY, SZ = 112, 34, 112
G = 4
ORIGIN = (-56, -63, -56)   # local (56, *, 56) is world (0, *, 0)
C = 56
SEED = 91

CANVAS = Canvas(SX, SY, SZ, SEED)

PLAZA_R = 9.5
RANGE = (48, 17, 64, 47)          # world x -8..8, z -39..-9 (Paintball practice zone)
TARGETS = [(51, 35), (56, 30), (60, 34), (53, 24), (59, 21)]   # dummy blocks (Paintball config)
HQ = (12, 45, 34, 67)
HQ_GATE = (54, 58)                # z range of the east gate
NPC = (38.5, G + 1, 56.5, -90.0)  # in front of the HQ gate, facing the parade ground
FRIENDS = (90, 56)
# the games square: one stand per game, each with its NPC on the north side facing the plaza
VALVLOER = (14, 72, 30, 87)
CHESS = (34, 70, 51, 87)          # 16 x 16 board inside a log border
HIDE = (63, 72, 90, 87)
GAME_NPCS = {                     # game id -> (x, y, z, yaw); yaw 180 faces north, -135 north-east
    # in the lane between the Valvloer floor and the chess board, looking out over the plaza
    # (north of the floor it stood against the HQ palisade)
    "valvloer": (32.5, G + 1, 75.5, -125.0),
    "chess": (42.5, G + 1, 67.5, 180.0),
    "verstoppertje": (72.5, G + 1, 69.5, 180.0),
}
ARCH_Z = 66                       # timber arch over the road into the games square
BARRACKS = (10, 9, 42, 38)
DEPOT = (70, 9, 103, 38)
MOTOR = (72, 101, 98, 106)
GATE_Z = 106
ROAD = (54, 58)
BOARDS = [(45, 47), (67, 47)]     # leaderboard plinths (centre blocks)
PATHS = [
    [(46, 56), (36, 56)],                       # parade ground -> HQ gate
    [(66, 56), (82, 56)],                       # -> friends' camp
    [(50, 63), (42, 66)],                       # -> chess
    [(48, 61), (40, 66), (32, 71)],             # -> valvloer
    [(49, 50), (44, 44), (34, 39), (28, 30)],   # -> barracks
    [(63, 50), (68, 44), (78, 39), (86, 30)],   # -> depot
    [(64, 62), (74, 68), (90, 70), (92, 92)],   # -> creek path east
    [(60, 100), (72, 98), (84, 98)],            # -> motor pool
]
SPAWN = (56.5, G + 1, 56.5, 180.0)

# --------------------------------------------------------------------------- materials

GRASS = "grass_block[snowy=false]"
PATH = Pal(("dirt_path", 5), ("coarse_dirt", 2), ("gravel", 1), seed=3)
ROADP = Pal(("dirt_path", 4), ("gravel", 2), ("coarse_dirt", 2), ("packed_mud", 1), seed=4, scale=2)
FOREST = Pal((GRASS, 6), ("podzol[snowy=false]", 2), ("coarse_dirt", 1), ("moss_block", 1), seed=6, scale=3)
BANK = Pal(("gravel", 3), ("mud", 2), ("clay", 1), ("coarse_dirt", 2), seed=7, scale=2)
BED = Pal(("gravel", 3), ("sand", 1), ("clay", 1), ("mud", 1), seed=8, scale=2)
LOGS = Pal(("spruce_log[axis=y]", 8), ("stripped_spruce_log[axis=y]", 1), ("dark_oak_log[axis=y]", 1), seed=20)
STONE = Pal(("stone_bricks", 5), ("cracked_stone_bricks", 1), ("mossy_stone_bricks", 2), ("andesite", 1), seed=21)
PARADE = Pal(("packed_mud", 3), ("coarse_dirt", 2), ("gravel", 2), ("dirt_path", 2), ("mud_bricks", 1), seed=22, scale=2)


# --------------------------------------------------------------------------- helpers


def creek_z(x):
    return 96.5 + 2.6 * math.sin(x / 9.0) + 1.2 * math.sin(x / 4.3 + 1.0)


def plaza_d(x, z):
    return math.hypot(x - C, z - C)


def built_rects():
    out = [RANGE, HQ, BARRACKS, DEPOT, MOTOR, (78, 46, 102, 67), VALVLOER, CHESS, HIDE]
    for (x, _, z, _) in GAME_NPCS.values():
        out.append((int(x) - 2, int(z) - 2, int(x) + 2, int(z) + 2))
    out.append((44, 100, 68, 110))
    return out


def height(x, z):
    edge = min(x, z, SX - 1 - x, SZ - 1 - z)
    rise = max(0, 10 - edge) * 0.9
    n = (fbm(x, z, 18, SEED) - 0.5) * 5
    flat = 0.0
    for r in built_rects():
        flat = max(flat, 1 - min(1, rect_dist(x, z, r) / 5))
    flat = max(flat, 1 - min(1, max(0, plaza_d(x, z) - PLAZA_R) / 6))
    flat = max(flat, 1 - min(1, abs(z - creek_z(x)) / 9))
    flat = max(flat, 1 - min(1, min(poly_dist(x, z, p) for p in PATHS) / 4))
    if ROAD[0] - 3 <= x <= ROAD[1] + 3 and z > 60:
        flat = 1.0
        rise *= 0.0 if z < GATE_Z + 1 else 1.0
    return G + max(0, round(n * (1 - flat))) + round(rise)


def on_path(x, z):
    return any(poly_dist(x, z, p) < 1.3 + 0.7 * h3(x, 1, z, 3) for p in PATHS)


def creek_dist(x, z):
    if x < 5 or x > SX - 6:
        return 99.0
    return abs(z - creek_z(x))


# --------------------------------------------------------------------------- terrain


def terrain(c: Canvas):
    for x in range(SX):
        for z in range(SZ):
            h = height(x, z)
            cd = creek_dist(x, z)
            if cd < 2.4:
                floor = G - 3 if cd < 1.4 else G - 2
                c.column(x, z, 0, floor - 1, "stone")
                c.set(x, floor, z, BED)
                for y in range(floor + 1, G):
                    c.set(x, y, z, "water")
                continue
            top = GRASS
            if cd < 4.6:
                h = G - 1 if cd < 3.4 else G
                top = BANK
            elif plaza_d(x, z) <= PLAZA_R:
                h = G
                top = PARADE
            elif ROAD[0] <= x <= ROAD[1] and z > C:
                h = G
                top = ROADP
            elif on_path(x, z):
                top = PATH
            elif fbm(x, z, 14, SEED + 9) > 0.58 or min(x, z, SX - 1 - x, SZ - 1 - z) < 9:
                top = FOREST
            c.column(x, z, 0, h - 4, "stone")
            c.column(x, z, max(0, h - 3), h - 1, "dirt")
            c.set(x, h, z, top)
    # culverts where the creek runs into the hills (keeps the water in)
    for x in (4, SX - 5):
        zc = round(creek_z(x))
        for z in range(zc - 3, zc + 4):
            for y in range(G - 3, G + 3):
                if abs(z - zc) == 3 or y == G + 2 or y < G - 3 + 1:
                    c.set(x, y, z, Pal("mossy_cobblestone", "cobblestone", "mossy_stone_bricks", seed=9))
        for z in range(zc - 3, zc + 4):
            c.set(x + (1 if x < C else -1), G + 2, z, "cobblestone_slab[type=bottom]" if h3(x, 0, z, 9) < .5 else "air")


# --------------------------------------------------------------------------- parade ground


def bench(c, x, y, z, axis="x", length=3):
    for i in range(length):
        px, pz = (x + i, z) if axis == "x" else (x, z + i)
        c.set(px, y, pz, f"stripped_spruce_log[axis={axis}]")


def plaza(c: Canvas):
    # emblem in the floor: concentric rings, the spawn in the middle
    for x in range(C - 5, C + 6):
        for z in range(C - 5, C + 6):
            d = math.hypot(x - C + 0.0, z - C + 0.0)
            if d <= 1.0:
                c.set(x, G, z, "chiseled_stone_bricks")
            elif d <= 2.2:
                c.set(x, G, z, "polished_andesite")
            elif 3.4 <= d <= 4.4:
                c.set(x, G, z, STONE)
    # cardinal arrows: stone brick paths out of the ring
    for k in range(5, 10):
        for (dx, dz) in ((0, -1), (0, 1), (-1, 0), (1, 0)):
            c.set(C + dx * k, G, C + dz * k, Pal(("stone_bricks", 3), ("mossy_stone_bricks", 1), seed=23))
    # a log curb around the parade ground, broken where paths leave
    for a in range(0, 360, 2):
        x = round(C + (PLAZA_R + 0.6) * math.cos(math.radians(a)))
        z = round(C + (PLAZA_R + 0.6) * math.sin(math.radians(a)))
        if on_path(x, z) or abs(x - C) <= 2 or abs(z - C) <= 2:
            continue
        c.set(x, G + 1, z, "spruce_slab[type=bottom]")
    # lamp posts on the diagonals and log benches between them
    for dx, dz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        P.lamp_post(c, C + dx * 7, G + 1, C + dz * 7, height=3)
    bench(c, C - 2, G + 1, C + 8, "x", 5)
    bench(c, C - 8, G + 1, C + 3, "z", 3)
    bench(c, C + 8, G + 1, C + 3, "z", 3)
    # flowers in tubs (barrel + plant) at the curb ends
    for x, z in ((C - 3, C - 9), (C + 3, C - 9)):
        c.set(x, G + 1, z, "spruce_planks")
        c.set(x, G + 2, z, "potted_fern")


def boards(c: Canvas):
    """Plinths for the two leaderboards: a stone block with a log frame and lanterns."""
    for bx, bz in BOARDS:
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                c.set(bx + dx, G, bz + dz, "polished_andesite")
                c.set(bx + dx, G + 1, bz + dz, "stone_bricks" if dx == dz == 0 else
                      ("stone_brick_slab[type=bottom]"))
        c.set(bx, G + 1, bz, "chiseled_stone_bricks")
        for dx, dz in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
            c.column(bx + dx, bz + dz, G + 1, G + 2, "spruce_log[axis=y]")
            c.set(bx + dx, G + 3, bz + dz, "lantern[hanging=false]")


# --------------------------------------------------------------------------- practice range


def practice_range(c: Canvas):
    x1, z1, x2, z2 = RANGE
    lane = Pal(("coarse_dirt", 4), ("packed_mud", 2), ("dirt_path", 1), ("gravel", 1), seed=30, scale=2)
    for x in range(x1 + 1, x2):
        for z in range(z1, z2 + 1):
            c.set(x, G, z, lane)
            for y in range(G + 1, G + 9):
                c.set(x, y, z, "air")
    # palisade sides (3 high, every fourth log one higher) with a plank walkway look
    for z in range(z1, z2 + 1):
        for x in (x1, x2):
            top = 3 + (1 if z % 4 == 0 else 0)
            c.set(x, G, z, "dirt")
            for y in range(G + 1, G + top + 1):
                c.set(x, y, z, LOGS)
    # back berm: earth bank with hay targets and a high log wall
    for z in range(z1, z1 + 3):
        for x in range(x1 + 1, x2):
            hgt = 3 - (z - z1)
            for y in range(G + 1, G + hgt + 1):
                c.set(x, y, z, Pal(("coarse_dirt", 3), ("rooted_dirt", 1), ("moss_block", 1), seed=31))
            if z == z1 + 2 and x % 3 == 0:
                c.set(x, G + 1, z, "hay_block[axis=y]")
    for x in range(x1, x2 + 1):
        for y in range(G + 1, G + 7):
            c.set(x, y, z1 - 1, LOGS)
        c.set(x, G + 7, z1 - 1, "spruce_slab[type=bottom]" if x % 2 else "spruce_planks")
    # distance markers on the side walls: a lantern every 8 blocks
    for z in range(z2 - 8, z1 + 3, -8):
        for x in (x1, x2):
            c.set(x, G + 4 + (1 if z % 4 == 0 else 0), z, "lantern[hanging=false]")
    # firing shed at the south end: log posts, slab roof with overhang, a rail to lean on
    s1, s2 = z2 - 4, z2
    for x in (x1, x2):
        for z in (s1, s2):
            c.column(x, z, G + 1, G + 4, "spruce_log[axis=y]")
    for x in range(x1 - 1, x2 + 2):
        for z in range(s1 - 1, s2 + 2):
            edge = x in (x1 - 1, x2 + 1) or z in (s1 - 1, s2 + 1)
            c.set(x, G + 5, z, "dark_oak_slab[type=bottom]" if edge else "dark_oak_planks")
    for x in range(x1, x2 + 1):
        c.set(x, G + 4, s1, "spruce_log[axis=x]")
        c.set(x, G + 4, s2, "spruce_log[axis=x]")
        c.set(x, G, s2, "spruce_planks")
        c.set(x, G, s2 - 1, "spruce_planks")
        c.set(x, G, s2 - 2, "spruce_planks")
    for x in range(x1 + 1, x2):
        if x % 4 != 0:
            c.set(x, G + 1, s1, "spruce_trapdoor[facing=south,half=top,open=false]")
        c.set(x, G + 3, s1 - 1, "air")
    for x in range(x1 + 2, x2 - 1, 4):
        c.set(x, G + 4, s1 + 2, "lantern[hanging=true]")
    # target posts behind each dummy (a log stub, a hay bale) so the dummies read as targets
    for tx, tz in TARGETS:
        c.set(tx, G, tz, "packed_mud")
        c.set(tx, G, tz - 1, "spruce_log[axis=x]")
    # entrance: open to the parade ground, sandbags beside the opening
    for x in (x1 - 1, x2 + 1):
        P.sandbags(c, x, z2 - 6, x, z2 - 1, G + 1, 1.5)


# --------------------------------------------------------------------------- paintball HQ


def hall(c: Canvas, x1, z1, x2, z2, h=G, door_x=None):
    """Log hall: stripped corner posts, horizontal log walls, a gable roof along x with overhang."""
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            c.set(x, h, z, "spruce_planks")
            for y in range(h + 1, h + 12):
                c.set(x, y, z, "air")
    for y in range(h + 1, h + 5):
        for x in range(x1, x2 + 1):
            for z in (z1, z2):
                c.set(x, y, z, "spruce_log[axis=x]" if y > h + 1 else "cobblestone")
        for z in range(z1, z2 + 1):
            for x in (x1, x2):
                c.set(x, y, z, "spruce_log[axis=z]" if y > h + 1 else "cobblestone")
    for (x, z) in ((x1, z1), (x1, z2), (x2, z1), (x2, z2)):
        c.column(x, z, h + 1, h + 4, "stripped_dark_oak_log[axis=y]")
    # windows with shutters lying flat against the wall
    for x in range(x1 + 2, x2 - 1, 3):
        for z, f in ((z1, "north"), (z2, "south")):
            c.set(x, h + 3, z, "glass_pane")
            c.set(x, h + 2, z + (-1 if f == "north" else 1), f"spruce_trapdoor[facing={f},half=top,open=false]")
    zm = (z1 + z2) // 2
    # big door on the east side
    dx = x2 if door_x is None else door_x
    for z in (zm - 1, zm, zm + 1):
        for y in (h + 1, h + 2, h + 3):
            c.set(dx, y, z, "air")
    for z in (zm - 2, zm + 2):
        c.column(dx + 1, z, h + 1, h + 4, "spruce_log[axis=y]")
    for z in range(zm - 2, zm + 3):
        c.set(dx + 1, h + 4, z, "spruce_log[axis=z]")
    c.set(dx + 2, h + 3, zm - 2, "lantern[hanging=false]") if False else None
    # gable roof along x
    half = (z2 - z1) // 2 + 1
    for i in range(half + 1):
        y = h + 5 + i
        za, zb = z1 - 1 + i, z2 + 1 - i
        for x in range(x1 - 1, x2 + 2):
            if za > zb:
                continue
            if za == zb:
                c.set(x, y, za, "dark_oak_slab[type=bottom]")
                continue
            c.set(x, y, za, "dark_oak_stairs[facing=south,half=bottom,shape=straight]")
            c.set(x, y, zb, "dark_oak_stairs[facing=north,half=bottom,shape=straight]")
            if za + 1 < zb:
                c.set(x, y - 1, za + 1, "dark_oak_planks") if x in (x1 - 1, x2 + 1) else None
        for z in range(za + 1, zb):
            for x in (x1, x2):
                c.set(x, y, z, "spruce_planks")
    # ridge beams poking out at both gables
    c.set(x1 - 2, h + 5 + half - 1, zm, "spruce_log[axis=x]")
    c.set(x2 + 2, h + 5 + half - 1, zm, "spruce_log[axis=x]")
    # chimney
    cx, cz = x1 + 2, z1 + 1
    c.column(cx, cz, h + 1, h + 5 + half + 1, Pal("cobblestone", "mossy_cobblestone", "stone_bricks", seed=40))
    c.set(cx, h + 5 + half + 2, cz, "campfire[lit=true,signal_fire=false,facing=north,waterlogged=false]")
    return zm


def hq(c: Canvas):
    x1, z1, x2, z2 = HQ
    floor = Pal(("coarse_dirt", 3), (GRASS, 2), ("dirt_path", 2), ("gravel", 1), seed=41, scale=2)
    for x in range(x1 + 1, x2):
        for z in range(z1 + 1, z2):
            c.set(x, G, z, floor)
            for y in range(G + 1, G + 4):
                c.set(x, y, z, "air")
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            if not (x in (x1, x2) or z in (z1, z2)):
                continue
            gate = x == x2 and HQ_GATE[0] <= z <= HQ_GATE[1]
            corner = x in (x1, x2) and z in (z1, z2)
            top = 6 if corner else 4 + (1 if h3(x, 0, z, 42) > 0.55 else 0)
            for y in range(G + 1, G + top + 1):
                if gate and y <= G + 4:
                    continue
                c.set(x, y, z, "dark_oak_log[axis=y]" if corner else LOGS)
            if not corner and not gate:
                c.set(x, G + top + 1, z, "spruce_fence" if False else None)
    # gate frame: posts sticking out, a lintel, team banners on both posts
    a, b = HQ_GATE
    for z in (a - 1, b + 1):
        c.column(x2 + 1, z, G + 1, G + 7, "spruce_log[axis=y]")
    for z in range(a - 1, b + 2):
        c.set(x2 + 1, G + 7, z, "spruce_log[axis=z]")
        c.set(x2, G + 5, z, "spruce_planks")
        c.set(x2, G + 6, z, "spruce_planks")
    c.set(x2 + 2, G + 5, a - 1, "red_wall_banner[facing=east]")
    c.set(x2 + 2, G + 5, b + 1, "blue_wall_banner[facing=east]")
    c.set(x2 + 2, G + 3, a - 1, "lantern[hanging=false]") if False else None
    for z in (a - 1, b + 1):
        c.set(x2 + 2, G + 1, z, "barrel[facing=up,open=false]")
        c.set(x2 + 2, G + 2, z, "lantern[hanging=false]")
    # walkway behind the gate wall
    for z in range(z1 + 1, z2):
        if not HQ_GATE[0] <= z <= HQ_GATE[1]:   # open over the gate (owner's in-game edit)
            c.set(x2 - 1, G + 3, z, "spruce_planks")
    for lz in (z1 + 2, z2 - 2):
        c.column(x2 - 1, lz, G + 1, G + 2, "spruce_planks")
        c.column(x2 - 2, lz, G + 1, G + 3, "ladder[facing=west]")
    # the command hall, door facing the gate
    zm = hall(c, x1 + 3, z1 + 5, x1 + 14, z2 - 5)
    c.set(x1 + 6, G + 1, zm, "cartography_table")
    c.set(x1 + 7, G + 1, zm, "cartography_table")
    c.set(x1 + 6, G + 1, zm + 1, "lectern[facing=east,has_book=false,powered=false]")
    P.crate_stack(c, x1 + 4, G + 1, z1 + 6, w=1, d=2, h=2, seed=43)
    c.set(x1 + 12, G + 1, z1 + 6, "smithing_table")
    # watchtower in the north-west corner, tents and supplies along the south wall
    P.watchtower(c, x1 + 1, G + 1, z1 + 1, height=7, ladder_side="east")
    c.set(x1 + 3, G + 15, z1 + 2, "spruce_fence")
    c.column(x1 + 3, z1 + 2, G + 12, G + 16, "spruce_fence")
    c.set(x1 + 3, G + 17, z1 + 2, "red_banner[rotation=4]")
    P.tent(c, x1 + 15, G + 1, z2 - 3, length=5, facing="x", seed=44)
    P.camo_net(c, x1 + 15, z1 + 2, x1 + 20, z1 + 7, G + 4, seed=45)
    P.crate_stack(c, x1 + 16, G + 1, z1 + 3, w=2, d=2, h=2, seed=46)
    for z in range(z1 + 9, z1 + 12):
        c.set(x2 - 3, G + 1, z, P.SANDBAG)
    c.set(x2 - 3, G + 2, z1 + 10, P.SANDBAG_TOP)
    # flag podium with a pole: red and blue banners (the two teams)
    fx, fz = x1 + 19, zm
    P.flag_podium(c, fx, G + 1, fz)
    c.column(fx, fz, G + 1, G + 7, "spruce_fence")
    c.set(fx + 1, G + 7, fz, "red_wall_banner[facing=east]")
    c.set(fx - 1, G + 7, fz, "blue_wall_banner[facing=west]")
    c.set(fx, G + 8, fz, "waxed_lightning_rod[facing=up,powered=false,waterlogged=false]")
    # NPC stand outside the gate: a small plank platform
    npc_stand(c, int(NPC[0]), int(NPC[2]), "z")


# --------------------------------------------------------------------------- friends' camp


def friends(c: Canvas):
    fx, fz = FRIENDS
    ring = Pal(("coarse_dirt", 3), ("dirt_path", 2), ("gravel", 1), seed=50)
    c.disc(fx, G, fz, 7.5, ring)
    for x in range(fx - 8, fx + 9):
        for z in range(fz - 8, fz + 9):
            for y in range(G + 1, G + 4):
                c.set(x, y, z, "air")
    c.disc(fx, G, fz, 1.6, Pal("cobblestone", "mossy_cobblestone", seed=51))
    c.set(fx, G, fz, "cobblestone")
    c.set(fx, G + 1, fz, "campfire[lit=true,signal_fire=false,facing=north,waterlogged=false]")
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        c.set(fx + dx, G + 1, fz + dz, "cobblestone_slab[type=bottom]")
    # log benches in a ring (four arcs, open towards the path in the west)
    for (bx, bz, axis) in ((fx - 2, fz - 4, "x"), (fx - 2, fz + 4, "x"), (fx + 4, fz - 1, "z")):
        bench(c, bx, G + 1, bz, axis, 5 if axis == "x" else 3)
    for (x, z) in ((fx - 4, fz - 3), (fx - 4, fz + 3), (fx + 3, fz - 3), (fx + 3, fz + 3)):
        c.set(x, G + 1, z, "stripped_spruce_log[axis=y]")
    # string lights: posts with fences between them and hanging lanterns
    posts = [(fx - 6, fz - 6), (fx + 6, fz - 6), (fx + 6, fz + 6), (fx - 6, fz + 6)]
    for (x, z) in posts:
        c.column(x, z, G + 1, G + 4, "spruce_log[axis=y]")
    for i in range(4):
        (ax, az), (bx, bz) = posts[i], posts[(i + 1) % 4]
        if i == 3:
            continue   # west side stays open
        n = max(abs(bx - ax), abs(bz - az))
        for k in range(1, n):
            x = ax + (bx - ax) * k // n
            z = az + (bz - az) * k // n
            c.set(x, G + 4, z, "spruce_fence")
            if k % 3 == 0:
                c.set(x, G + 3, z, "lantern[hanging=true]")
    # tents and a cabin around the fire
    P.tent(c, fx - 4, G + 1, fz - 10, length=5, facing="x", cloth="brown_wool", seed=52)
    P.tent(c, fx - 4, G + 1, fz + 10, length=5, facing="x", cloth="green_wool", seed=53)
    P.tent(c, fx + 10, G + 1, fz - 4, length=5, facing="z", cloth="green_wool", seed=54)
    hall(c, fx + 4, fz + 6, fx + 11, fz + 11, door_x=None)
    P.crate_stack(c, fx + 9, G + 1, fz - 8, w=1, d=2, h=1, seed=55)
    c.set(fx - 6, G + 1, fz + 2, "barrel[facing=up,open=false]")
    c.set(fx - 6, G + 1, fz - 2, "barrel[facing=up,open=false]")
    # woodpile
    for z in range(fz - 2, fz + 3):
        c.set(fx + 8, G + 1, z, "oak_log[axis=z]")
        if z % 2 == 0:
            c.set(fx + 8, G + 2, z, "oak_log[axis=z]")


# --------------------------------------------------------------------------- games square


def npc_stand(c: Canvas, x, z, axis="x"):
    """A plank platform for a game NPC with a lantern post on either side (along `axis`)."""
    for px in range(x - 1, x + 2):
        for pz in range(z - 1, z + 2):
            c.set(px, G, pz, "spruce_planks")
            for y in range(G + 1, G + 4):
                c.set(px, y, pz, "air")
    for d in (-2, 2):
        px, pz = (x + d, z) if axis == "x" else (x, z + d)
        c.set(px, G + 1, pz, "spruce_fence")
        c.set(px, G + 2, pz, "lantern[hanging=false]")


def curb(c: Canvas, x1, z1, x2, z2, y=G):
    """Log curb around a rectangle (logs lie along the edge), dark posts in the corners."""
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            if x in (x1, x2) and z in (z1, z2):
                c.set(x, y, z, "stripped_dark_oak_log[axis=y]")
            elif z in (z1, z2):
                c.set(x, y, z, "spruce_log[axis=x]")
            elif x in (x1, x2):
                c.set(x, y, z, "spruce_log[axis=z]")


def clear_above(c: Canvas, x1, z1, x2, z2, h=10):
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            for y in range(G + 1, G + h):
                c.set(x, y, z, "air")


def gantry(c: Canvas, x1, z1, x2, z2, top):
    """Timber frame: corner posts, beams round the top with knee braces, a middle beam with lanterns."""
    for (x, z) in ((x1, z1), (x1, z2), (x2, z1), (x2, z2)):
        c.column(x, z, G + 1, top, "stripped_dark_oak_log[axis=y]")
    for x in range(x1 + 1, x2):
        c.set(x, top, z1, "spruce_log[axis=x]")
        c.set(x, top, z2, "spruce_log[axis=x]")
    for z in range(z1 + 1, z2):
        c.set(x1, top, z, "spruce_log[axis=z]")
        c.set(x2, top, z, "spruce_log[axis=z]")
    # knee braces: upside-down stairs under the beams next to each post
    for (x, z) in ((x1, z1), (x1, z2), (x2, z1), (x2, z2)):
        sx = 1 if x == x1 else -1
        sz = 1 if z == z1 else -1
        c.set(x + sx, top - 1, z, f"spruce_stairs[facing={'west' if sx == 1 else 'east'},half=top,shape=straight]")
        c.set(x, top - 1, z + sz, f"spruce_stairs[facing={'north' if sz == 1 else 'south'},half=top,shape=straight]")
    zm = (z1 + z2) // 2
    for x in range(x1 + 1, x2):
        c.set(x, top + 1, zm, "spruce_log[axis=x]")
    for x in (x1, x2):
        c.set(x, top + 1, zm, "stripped_dark_oak_log[axis=y]")
    for x in range(x1 + 3, x2 - 1, 4):
        c.set(x, top, zm, "iron_chain[axis=y]")
        c.set(x, top - 1, zm, "lantern[hanging=true]")
    # plank cap along the top beams
    for x in range(x1, x2 + 1):
        for z in (z1, z2):
            c.set(x, top + 1, z, "spruce_slab[type=bottom]")
    for z in range(z1 + 1, z2):
        if z != zm:
            for x in (x1, x2):
                c.set(x, top + 1, z, "spruce_slab[type=bottom]")


VALVLOER_TILES = ["birch_planks", "stripped_birch_wood", "oak_planks", "stripped_oak_wood",
                  "jungle_planks", "stripped_jungle_wood", "mangrove_planks", "stripped_mangrove_wood"]


def valvloer(c: Canvas):
    """A patchwork floor of the game's own blocks, with holes where a player ran, over a hay pit."""
    x1, z1, x2, z2 = VALVLOER
    clear_above(c, x1, z1, x2, z2)
    curb(c, x1, z1, x2, z2)
    trail = [(16, 85), (19, 81), (23, 82), (26, 78), (25, 75)]
    for x in range(x1 + 1, x2):
        for z in range(z1 + 1, z2):
            i, k = (x - x1 - 1) // 2, (z - z1 - 1) // 2
            tile = VALVLOER_TILES[int(h3(i, 1, k, 130) * len(VALVLOER_TILES))]
            hole = poly_dist(x, z, trail) < 0.75 or h3(x, 2, z, 131) < 0.05
            c.set(x, G - 1, z, "hay_block[axis=y]")
            c.set(x, G, z, "air" if hole else tile)
    gantry(c, x1, z1, x2, z2, G + 6)


CHESS_LIGHT = Pal(("pale_oak_planks", 3), ("birch_planks", 1), seed=140)
CHESS_DARK = Pal(("dark_oak_planks", 3), ("stripped_dark_oak_wood", 1), seed=141)
WHITE = Pal(("calcite", 3), ("smooth_quartz", 1), seed=142)
BLACK = Pal(("polished_blackstone", 3), ("blackstone", 1), seed=143)


def chess_piece(c: Canvas, i, k, kind, white):
    """A 2 x 2 piece on square (file i, rank k): height and cap tell the kind."""
    x0, z0 = CHESS[0] + 1 + 2 * i, CHESS[1] + 1 + 2 * k
    m = WHITE if white else BLACK
    cap = "chiseled_quartz_block" if white else "chiseled_polished_blackstone"
    slab = "smooth_quartz_slab[type=bottom]" if white else "polished_blackstone_slab[type=bottom]"
    body = {"pawn": 1, "rook": 2, "knight": 2, "bishop": 2, "queen": 3, "king": 3}[kind]
    cells = [(x0, z0), (x0 + 1, z0), (x0, z0 + 1), (x0 + 1, z0 + 1)]
    for (x, z) in cells:
        for y in range(G + 1, G + 1 + body):
            c.set(x, y, z, m)
    top = G + 1 + body
    if kind == "pawn" or kind == "bishop":
        for (x, z) in cells:
            c.set(x, top, z, slab)
    elif kind == "rook":
        for (x, z) in cells:
            c.set(x, top - 1, z, cap)
            c.set(x, top, z, slab)
    elif kind == "knight":   # the head leans towards the other side
        d = -1 if white else 1
        hz = z0 if white else z0 + 1
        for x in (x0, x0 + 1):
            c.set(x, top, hz, m)
            c.set(x, top + 1, hz, slab)
            c.set(x, top, hz - d, f"{'smooth_quartz' if white else 'polished_blackstone'}_stairs"
                  f"[facing={'south' if white else 'north'},half=bottom,shape=straight]")
    elif kind == "queen":
        for (x, z) in cells:
            c.set(x, top - 1, z, cap)
            c.set(x, top, z, slab)
    elif kind == "king":
        for (x, z) in cells:
            c.set(x, top - 1, z, cap)
            c.set(x, top, z, m)
            c.set(x, top + 1, z, slab)


def chess(c: Canvas):
    """A giant 16 x 16 board (2 x 2 squares) with pieces in the middle of a game."""
    x1, z1, x2, z2 = CHESS
    clear_above(c, x1, z1, x2, z2)
    curb(c, x1, z1, x2, z2)
    for x in range(x1 + 1, x2):
        for z in range(z1 + 1, z2):
            i, k = (x - x1 - 1) // 2, (z - z1 - 1) // 2
            c.set(x, G, z, CHESS_LIGHT if (i + k) % 2 == 0 else CHESS_DARK)
    for (x, z) in ((x1, z1), (x1, z2), (x2, z1), (x2, z2)):
        c.column(x, z, G + 1, G + 2, "stripped_dark_oak_log[axis=y]")
        c.set(x, G + 3, z, "lantern[hanging=false]")
    # white plays from the south (rank 7), black from the north (rank 0)
    # an open middle game; no two pieces on touching squares, so each one reads on its own
    white = [("king", 6, 7), ("rook", 4, 7), ("pawn", 0, 6), ("pawn", 2, 6), ("knight", 5, 5),
             ("bishop", 0, 3), ("queen", 3, 4), ("pawn", 7, 5)]
    black = [("king", 2, 0), ("rook", 4, 0), ("pawn", 6, 1), ("pawn", 0, 1), ("knight", 6, 3),
             ("bishop", 2, 2), ("pawn", 4, 2)]
    for kind, i, k in white:
        chess_piece(c, i, k, kind, True)
    for kind, i, k in black:
        chess_piece(c, i, k, kind, False)
    # taken pieces wait beside the board: a pawn and a knight on each side
    for (x, z, white_side) in ((x2 + 2, z2 - 3, True), (x2 + 2, z1 + 2, False)):
        m = WHITE if white_side else BLACK
        c.set(x, G, z, "spruce_planks")
        c.set(x, G + 1, z, m)
        c.set(x, G + 2, z, "smooth_quartz_slab[type=bottom]" if white_side else "polished_blackstone_slab[type=bottom]")


YARD = Pal(("coarse_dirt", 3), ("dirt_path", 2), ("packed_mud", 1), (GRASS, 2), ("gravel", 1), seed=150, scale=2)


def log_pile(c: Canvas, x, z, length=5, axis="x", kind="spruce"):
    """Stacked logs: three wide at the bottom, two, then one."""
    for row, w in enumerate((3, 2, 1)):
        for j in range(w):
            for i in range(length):
                px, pz = (x + i, z + j + row * 0.5) if axis == "x" else (x + j + row * 0.5, z + i)
                c.set(int(px), G + 1 + row, int(pz), f"{kind}_log[axis={axis}]")


def hide_yard(c: Canvas):
    """Verstoppertje: a timber yard full of things to hide among (and a few that are hiders)."""
    x1, z1, x2, z2 = HIDE
    clear_above(c, x1, z1, x2, z2)
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            c.set(x, G, z, YARD)
    # rail fence along the east and south sides, open to the plaza and the road
    for x in range(x1 + 4, x2 + 1):
        c.set(x, G + 1, z2, "spruce_fence")
    for z in range(z1, z2 + 1):
        c.set(x2, G + 1, z, "spruce_fence")
    for (x, z) in ((x2, z1), (x2, z2), (x1 + 4, z2)):
        c.column(x, z, G + 1, G + 2, "spruce_log[axis=y]")
    # an open-fronted shed along the south fence: log back wall, posts, slab roof with overhang
    sx1, sx2, sz1, sz2 = x1 + 12, x1 + 22, z2 - 5, z2 - 1
    for x in range(sx1, sx2 + 1):
        for y in range(G + 1, G + 4):
            c.set(x, y, sz2, "spruce_log[axis=x]" if y > G + 1 else "cobblestone")
    for z in range(sz1, sz2 + 1):
        for x in (sx1, sx2):
            for y in range(G + 1, G + 4):
                c.set(x, y, z, "spruce_log[axis=z]" if y > G + 1 else "cobblestone")
    for x in (sx1, sx2):
        c.column(x, sz1, G + 1, G + 3, "stripped_dark_oak_log[axis=y]")
    for x in range(sx1 + 1, sx2):
        c.set(x, G + 1, sz1, "air")
        c.set(x, G + 2, sz1, "air")
        c.set(x, G + 3, sz1, "spruce_log[axis=x]")
    for x in range(sx1 - 1, sx2 + 2):
        for z in range(sz1 - 1, sz2 + 2):
            edge = x in (sx1 - 1, sx2 + 1) or z in (sz1 - 1, sz2 + 1)
            c.set(x, G + 4, z, "dark_oak_slab[type=bottom]" if edge else "dark_oak_planks")
    for x in range(sx1 + 2, sx2 - 1, 4):
        c.set(x, G + 3, sz1 + 2, "lantern[hanging=true]")
    # shed contents: barrels, crates, a workbench
    for x in range(sx1 + 1, sx1 + 4):
        c.set(x, G + 1, sz2 - 1, "barrel[facing=up,open=false]")
    c.set(sx1 + 1, G + 2, sz2 - 1, "barrel[facing=up,open=false]")
    P.crate_stack(c, sx2 - 3, G + 1, sz2 - 2, w=2, d=1, h=2, seed=151)
    c.set(sx1 + 5, G + 1, sz2 - 1, "crafting_table")
    c.set(sx1 + 6, G + 1, sz2 - 1, "smithing_table")
    # log piles, hay, crates and barrels around the yard
    log_pile(c, x1 + 2, z1 + 3, length=6, axis="x")
    log_pile(c, x1 + 2, z1 + 9, length=5, axis="x", kind="oak")
    log_pile(c, x2 - 5, z1 + 2, length=6, axis="z", kind="birch")
    for (x, z) in ((x1 + 11, z1 + 3), (x1 + 12, z1 + 3), (x1 + 11, z1 + 4)):
        c.set(x, G + 1, z, "hay_block[axis=y]")
    c.set(x1 + 11, G + 2, z1 + 3, "hay_block[axis=x]")
    P.crate_stack(c, x1 + 16, G + 1, z1 + 2, w=2, d=2, h=2, seed=152)
    P.crate_stack(c, x1 + 18, G + 1, z1 + 8, w=1, d=2, h=1, seed=153)
    P.bush(c, x1 + 8, G + 1, z1 + 8, r=1.3, seed=154)
    P.bush(c, x2 - 3, G + 1, z2 - 3, r=1.2, seed=155)
    P.oak(c, x1 + 8, G + 1, z2 - 4, height=5, r=2.3, seed=156)
    # the hiders: odd blocks standing on their own in the open
    for (x, z, s) in ((x1 + 7, z1 + 5, "barrel[facing=up,open=false]"), (x1 + 14, z1 + 7, "hay_block[axis=y]"),
                      (x1 + 21, z1 + 5, "spruce_planks"), (x1 + 5, z2 - 3, "barrel[facing=up,open=false]"),
                      (x1 + 3, z2 - 1, "potted_fern")):
        if s == "potted_fern":
            c.set(x, G + 1, z, "spruce_planks")
            c.set(x, G + 2, z, s)
        else:
            c.set(x, G + 1, z, s)


def arch(c: Canvas):
    """Timber arch over the road where the games square starts."""
    z = ARCH_Z
    a, b = ROAD[0] - 2, ROAD[1] + 2
    for x in (a, b):
        c.column(x, z, G + 1, G + 6, "dark_oak_log[axis=y]")
        c.set(x, G, z, "cobblestone")
    for x in range(a - 1, b + 2):
        c.set(x, G + 7, z, "dark_oak_log[axis=x]")
        c.set(x, G + 8, z, "spruce_slab[type=bottom]" if x % 2 else "spruce_planks")
    c.set(a + 1, G + 6, z, "spruce_stairs[facing=west,half=top,shape=straight]")
    c.set(b - 1, G + 6, z, "spruce_stairs[facing=east,half=top,shape=straight]")
    for x in (a + 2, b - 2):
        c.set(x, G + 6, z, "lantern[hanging=true]")
    for x in (a, b):
        for dz in (-1, 1):
            c.set(x, G + 1, z + dz, "barrel[facing=up,open=false]")


def games_square(c: Canvas):
    valvloer(c)
    chess(c)
    hide_yard(c)
    arch(c)
    for game, (x, _, z, _) in GAME_NPCS.items():
        npc_stand(c, int(x), int(z), "z" if game == "valvloer" else "x")

# --------------------------------------------------------------------------- creek, gate, south


def bridge(c: Canvas):
    x1, x2 = ROAD[0] - 1, ROAD[1] + 1
    zs = [creek_z(x) for x in range(x1, x2 + 1)]
    za, zb = int(min(zs) - 5), int(max(zs) + 5)
    for z in range(za, zb + 1):
        mid = min(abs(z - za), abs(zb - z))
        rise = 1 if mid >= 2 else 0
        for x in range(x1, x2 + 1):
            c.set(x, G + rise, z, Pal(("spruce_planks", 4), ("stripped_spruce_log[axis=z]", 1), seed=60))
            for y in range(G + rise + 1, G + rise + 4):
                c.set(x, y, z, "air")
            if rise == 0:
                c.column(x, z, 1, G - 1, "dirt")
        if rise and mid == 2:
            for x in range(x1 + 1, x2):
                c.set(x, G + 1, z, f"spruce_stairs[facing={'south' if z < (za + zb) / 2 else 'north'},half=bottom,shape=straight]")
        for x in (x1 - 1, x2 + 1):
            c.set(x, G + rise, z, "spruce_log[axis=z]")
            c.set(x, G + rise + 1, z, "spruce_log[axis=y]" if z % 3 == 0 else "spruce_fence")
            if z % 3 == 0:
                c.column(x, z, 1, G + rise - 1, "stripped_spruce_log[axis=y]")
                if z == za + 3 or z == zb - 3:
                    c.set(x, G + rise + 2, z, "lantern[hanging=false]")


def gate(c: Canvas):
    z = GATE_Z
    for x in range(SX):
        if ROAD[0] - 1 <= x <= ROAD[1] + 1:
            continue
        h = c.top_solid(x, z)
        if h > G + 6:
            continue
        top = 4 + (1 if h3(x, 0, z, 70) > 0.55 else 0)
        for y in range(h + 1, max(h + 1, G + top + 1)):
            c.set(x, y, z, LOGS)
    # gate posts and lintel, closed by a timber double gate (the camp has no way out)
    for x in (ROAD[0] - 2, ROAD[1] + 2):
        c.column(x, z, G + 1, G + 7, "dark_oak_log[axis=y]")
    for x in range(ROAD[0] - 2, ROAD[1] + 3):
        c.set(x, G + 7, z, "dark_oak_log[axis=x]")
        c.set(x, G + 8, z, "spruce_slab[type=bottom]" if x % 2 else "spruce_planks")
    for x in range(ROAD[0] - 1, ROAD[1] + 2):
        for y in range(G + 1, G + 5):
            c.set(x, y, z, "spruce_planks" if (x + y) % 3 else "stripped_spruce_log[axis=y]")
        c.set(x, G + 5, z, "spruce_fence")
        c.set(x, G + 6, z, "air")
    c.set(ROAD[0] - 2, G + 6, z - 1, "red_wall_banner[facing=north]")
    c.set(ROAD[1] + 2, G + 6, z - 1, "blue_wall_banner[facing=north]")
    P.watchtower(c, ROAD[0] - 7, G + 1, z - 2, height=7, ladder_side="east")
    P.watchtower(c, ROAD[1] + 4, G + 1, z - 2, height=7, ladder_side="west")
    P.sandbags(c, ROAD[0] - 4, z - 5, ROAD[0] - 1, z - 5, None, 1.5)
    P.sandbags(c, ROAD[1] + 1, z - 5, ROAD[1] + 4, z - 5, None, 1.5)


def motor_pool(c: Canvas):
    x1, z1, x2, z2 = MOTOR
    pad = Pal(("gravel", 3), ("andesite", 1), ("coarse_dirt", 2), ("packed_mud", 1), seed=80, scale=2)
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            c.set(x, G, z, pad)
            for y in range(G + 1, G + 6):
                c.set(x, y, z, "air")
    for i, x in enumerate(range(x1 + 2, x2 - 4, 7)):
        jeep(c, x, G + 1, z1 + 1, axis="z", wreck=False)
    P.camo_net(c, x1 + 1, z1, x1 + 14, z2, G + 5, seed=81)
    P.crate_stack(c, x2 - 3, G + 1, z1 + 1, w=2, d=2, h=2, seed=82)
    for z in range(z1 + 1, z2):
        c.set(x2, G + 1, z, "barrel[facing=up,open=false]" if z % 2 else "barrel[facing=north,open=false]")


# --------------------------------------------------------------------------- barracks and depot


def barracks(c: Canvas):
    x1, z1, x2, z2 = BARRACKS
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            if c.get(x, G, z).startswith("grass") and h3(x, 2, z, 90) < 0.3:
                c.set(x, G, z, Pal("coarse_dirt", "dirt_path", seed=91))
    for row, z in enumerate((z1 + 4, z1 + 12)):
        for k, x in enumerate(range(x1 + 2, x2 - 8, 8)):
            P.tent(c, x, G + 1, z, length=5, facing="x", cloth="green_wool" if (k + row) % 3 else "brown_wool",
                   seed=100 + k + row * 10)
    # mess cabin and a fire pit with logs
    hall(c, x1 + 3, z2 - 9, x1 + 13, z2 - 3)
    fx, fz = x1 + 21, z2 - 6
    c.set(fx, G + 1, fz, "campfire[lit=true,signal_fire=false,facing=north,waterlogged=false]")
    bench(c, fx - 1, G + 1, fz - 2, "x", 3)
    bench(c, fx - 1, G + 1, fz + 2, "x", 3)
    # laundry line between two posts
    for x in (x2 - 8, x2 - 2):
        c.column(x, z2 - 1, G + 1, G + 3, "spruce_log[axis=y]")
    for x in range(x2 - 7, x2 - 2):
        c.set(x, G + 3, z2 - 1, "spruce_fence")
        if x % 2:
            c.set(x, G + 2, z2 - 1, Pal("white_banner[rotation=0]", "light_gray_banner[rotation=0]", seed=x))
    P.lamp_post(c, x1 + 18, G + 1, z1 + 9, height=3)
    P.lamp_post(c, x1 + 2, G + 1, z1 + 9, height=3)


def water_tower(c: Canvas, x, z):
    """Landmark: four log legs with braces, a plank tank with iron bands, a cone roof."""
    y0 = G + 1
    for dx in (0, 5):
        for dz in (0, 5):
            c.column(x + dx, z + dz, y0, y0 + 9, "stripped_spruce_log[axis=y]")
    for lvl in (3, 7):
        for i in range(1, 5):
            for (px, pz) in ((x + i, z), (x + i, z + 5), (x, z + i), (x + 5, z + i)):
                c.set(px, y0 + lvl, pz, "spruce_fence")
    for i in range(1, 10):
        c.set(x + 1, y0 + i - 1, z, "ladder[facing=north]") if False else None
    cx, cz = x + 2.5, z + 2.5
    for y in range(y0 + 10, y0 + 16):
        for px in range(x - 1, x + 7):
            for pz in range(z - 1, z + 7):
                d = math.hypot(px - cx, pz - cz)
                if d <= 3.6:
                    band = y in (y0 + 11, y0 + 14)
                    c.set(px, y, pz, ("dark_oak_planks" if not band else "polished_blackstone")
                          if d > 2.6 else "air")
    for y in range(y0 + 16, y0 + 20):
        r = 4.2 - (y - y0 - 16) * 1.2
        for px in range(x - 2, x + 8):
            for pz in range(z - 2, z + 8):
                if math.hypot(px - cx, pz - cz) <= r:
                    c.set(px, y, pz, "dark_oak_slab[type=bottom]" if math.hypot(px - cx, pz - cz) > r - 1 else "dark_oak_planks")
    c.set(x + 3, y0 + 20, z + 3, "waxed_lightning_rod[facing=up,powered=false,waterlogged=false]")
    for y in range(y0, y0 + 10):
        c.set(x + 2, y, z + 6, "ladder[facing=south]")
        c.set(x + 2, y, z + 5, "spruce_planks")


def depot(c: Canvas):
    x1, z1, x2, z2 = DEPOT
    yard = Pal(("gravel", 3), ("coarse_dirt", 2), ("packed_mud", 1), ("andesite", 1), seed=110, scale=2)
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            c.set(x, G, z, yard)
    P.container(c, x1 + 2, G + 1, z1 + 3, length=8, axis="x", color="green", seed=111)
    P.container(c, x1 + 2, G + 4, z1 + 3, length=8, axis="x", color="brown", seed=112)
    P.container(c, x1 + 2, G + 1, z1 + 8, length=8, axis="x", color="gray", seed=113)
    P.container(c, x1 + 13, G + 1, z1 + 3, length=7, axis="x", color="green", doors=False, seed=114)
    P.container(c, x1 + 16, G + 1, z1 + 12, length=7, axis="z", color="brown", seed=115)
    P.camo_net(c, x1 + 2, z1 + 15, x1 + 12, z1 + 23, G + 4, seed=116)
    P.crate_stack(c, x1 + 3, G + 1, z1 + 17, w=3, d=2, h=2, seed=117)
    P.crate_stack(c, x1 + 8, G + 1, z1 + 19, w=2, d=2, h=1, seed=118)
    jeep(c, x1 + 4, G + 1, z2 - 6, axis="x")
    water_tower(c, x2 - 9, z1 + 3)
    for z in range(z2 - 12, z2 - 2):
        c.set(x2 - 1, G + 1, z, "hay_block[axis=z]" if z % 3 else "barrel[facing=up,open=false]")
    P.watchtower(c, x2 - 5, G + 1, z2 - 4, height=6, ladder_side="west")


# --------------------------------------------------------------------------- nature


def woods(c: Canvas):
    def blocked(x, z, pad):
        if any(rect_dist(x, z, r) < 3 + pad for r in built_rects()):
            return True
        if plaza_d(x, z) < PLAZA_R + 9 + pad or creek_dist(x, z) < 5 + pad:
            return True
        if any(poly_dist(x, z, p) < 3 + pad for p in PATHS):
            return True
        if ROAD[0] - 4 - pad <= x <= ROAD[1] + 4 + pad and z > C:
            return True
        if abs(x - FRIENDS[0]) < 12 and abs(z - FRIENDS[1]) < 12:
            return True
        return False

    step = 4
    for gx in range(0, SX, step):
        for gz in range(0, SZ, step):
            x = gx + int(h3(gx, 5, gz, 60) * step)
            z = gz + int(h3(gz, 6, gx, 61) * step)
            if x >= SX - 1 or z >= SZ - 1 or x < 1 or z < 1:
                continue
            edge = min(x, z, SX - 1 - x, SZ - 1 - z)
            border = edge < 9
            if blocked(x, z, 0) and not (border and not blocked(x, z, -3)):
                continue
            dens = 0.92 if border else 0.12 + 0.45 * fbm(x, z, 20, SEED + 2)
            r = h3(x, 7, z, 62)
            y = c.top_solid(x, z) + 1
            if r > dens:
                if r < dens + 0.3:
                    P.bush(c, x, y, z, r=1.1 + h3(x, 8, z, 63), seed=x * 7 + z)
                continue
            kind = h3(x, 9, z, 64)
            if border and kind < 0.55 or kind < 0.15:
                P.spruce(c, x, y, z, height=8 + int(h3(x, 1, z, 65) * 6), seed=x + z)
            elif kind < 0.4:
                P.birch(c, x, y, z, height=5 + int(h3(x, 2, z, 66) * 3), seed=x + z)
            else:
                P.oak(c, x, y, z, height=4 + int(h3(x, 3, z, 67) * 3), r=2.3 + h3(x, 4, z, 68), seed=x + z)
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if h3(x + dx, 0, z + dz, 69) < 0.3 and c.empty(x + dx, y, z + dz):
                    c.set(x + dx, y, z + dz, "fern")
    for (x, z, r) in ((20, 50, 2.0), (8, 80, 2.4), (100, 82, 2.2), (46, 6, 1.8), (66, 92, 1.6), (30, 104, 2.0)):
        if not blocked(x, z, -2):
            P.boulder(c, x, c.top_solid(x, z) + 1, z, r=r, seed=x)
    for (x, z, a) in ((24, 86, "x"), (88, 86, "x"), (6, 44, "z")):
        if not blocked(x, z, -2):
            P.fallen_log(c, x, c.top_solid(x, z) + 1, z, length=5, axis=a, seed=x)


def ground_cover(c: Canvas):
    deco = Pal(("short_grass", 30), ("fern", 4), ("dandelion", 1), ("oxeye_daisy", 1), ("azure_bluet", 1),
               ("poppy", 1), seed=120)
    for x in range(1, SX - 1):
        for z in range(1, SZ - 1):
            y = c.top(x, z)
            s = c.get(x, y, z)
            if s.startswith("grass_block") and h3(x, 12, z, 121) < 0.32:
                c.set(x, y + 1, z, deco)
            elif s.startswith("podzol") and h3(x, 12, z, 122) < 0.2:
                c.set(x, y + 1, z, "fern")
    # reeds along the creek
    for x in range(6, SX - 6):
        for z in range(70, SZ):
            if 2.4 <= creek_dist(x, z) < 4.6 and h3(x, 13, z, 123) < 0.25:
                y = c.top(x, z)
                if c.get(x, y, z) in ("air",) or not is_full(c.get(x, y, z)):
                    continue
                c.set(x, y + 1, z, Pal("short_grass", "fern", "tall_grass[half=lower]", seed=124))


def tall_grass_fix(c: Canvas):
    for (x, y, z), s in list(c.b.items()):
        if s.startswith("tall_grass[half=lower]"):
            if c.empty(x, y + 1, z):
                c.set(x, y + 1, z, "tall_grass[half=upper]")
            else:
                c.set(x, y, z, "short_grass")


def border(c: Canvas):
    for x in range(SX):
        for z in range(SZ):
            if x in (0, SX - 1) or z in (0, SZ - 1):
                for y in range(1, SY):      # every air block, also under tree canopies
                    if c.empty(x, y, z) or not is_full(c.get(x, y, z)):
                        c.set(x, y, z, "barrier")


def clear_spawn(c: Canvas):
    """Nothing in the way where people stand: spawn, NPC, leaderboards."""
    for (x, y, z) in [SPAWN[:3], NPC[:3]] + [p[:3] for p in GAME_NPCS.values()]:
        for dy in range(0, 3):
            c.set(int(x), int(y) + dy, int(z), "air")


# --------------------------------------------------------------------------- entities


def w(x, y, z):
    ox, oy, oz = ORIGIN
    return x + ox, y + oy, z + oz


def text(x, y, z, title, sub=None, scale=2.0, color="#D9C78F"):
    t = [{"text": title, "color": color, "bold": True}]
    if sub:
        t.append({"text": "\n" + sub, "color": "#BFBFBF", "bold": False})
    comp = json.dumps({"text": "", "extra": t}, separators=(",", ":"))
    wx, wy, wz = w(x, y, z)
    return (f"summon minecraft:text_display {wx} {wy} {wz} {{Tags:[\"dbsdw_lobby\"],billboard:\"vertical\","
            f"background:-1442840576,text:{comp},"
            f"transformation:{{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],"
            f"translation:[0f,0f,0f],scale:[{scale}f,{scale}f,{scale}f]}}}}")


def entity_cmds():
    ox, oy, oz = ORIGIN
    box = f"x={ox},y={oy - 1},z={oz},dx={SX - 1},dy={SY},dz={SZ - 1}"
    cmds = [
        "kill @e[type=minecraft:text_display,tag=dbsdw_range]",
        "kill @e[type=minecraft:text_display,tag=dbsdw_lobby]",
        # GameNpc cancels every damage event, /kill included; Health 0 removes the old NPC
        "execute as @e[type=minecraft:mannequin,tag=dbsdw_lobby] run data merge entity @s {Health:0f}",
        "kill @e[type=minecraft:item," + box + "]",
    ]
    for mob in ("sheep", "cow", "pig", "chicken", "horse", "zombie_horse", "rabbit"):
        cmds.append(f"kill @e[type=minecraft:{mob},{box}]")
    x1, z1, x2, z2 = RANGE
    cmds += [
        text((x1 + x2) / 2 + 0.5, G + 6.3, z2 + 1.5, "OEFENTERREIN", "Loop naar binnen voor een geweer", 2.0),
        text(HQ[2] + 1.5, G + 8.4, (HQ_GATE[0] + HQ_GATE[1]) / 2 + 0.5, "PAINTBALL", "Commandopost", 2.4),
        text(FRIENDS[0] + 0.5, G + 5.5, FRIENDS[1] + 0.5, "SPEEL MET VRIENDEN",
             "/room of het item in je hotbar", 1.8),
    ]
    cmds.append(text(C + 0.5, G + 9.5, GATE_Z - 0.5, "BASISKAMP", None, 2.6))
    cmds.append(text(C + 0.5, G + 10.2, ARCH_Z + 0.5, "SPELLEN", "Valvloer · Schaken · Verstoppertje", 2.2))
    # way markers at the curb gaps of the parade ground
    cmds.append(text(C - 10 + 0.5, G + 3.0, C + 0.5, "PAINTBALL", None, 1.2))
    cmds.append(text(C + 10 + 0.5, G + 3.0, C + 0.5, "VRIENDEN", None, 1.2))
    cmds.append(text(C + 0.5, G + 3.0, C + 10 + 0.5, "SPELLEN", None, 1.2))
    for game, (x, z, title, sub) in {
        "valvloer": ((VALVLOER[0] + VALVLOER[2]) / 2 + 0.5, VALVLOER[1] + 0.5, "VALVLOER",
                     "De vloer valt weg onder je voeten"),
        "chess": ((CHESS[0] + CHESS[2]) / 2 + 0.5, CHESS[1] + 0.5, "SCHAKEN", "Schaak op een reuzenbord"),
        "verstoppertje": ((HIDE[0] + HIDE[2]) / 2 + 0.5, HIDE[1] + 0.5, "VERSTOPPERTJE",
                          "Verstop je als een blok"),
    }.items():
        cmds.append(text(x, G + 6.5 if game != "valvloer" else G + 9.0, z, title, sub, 1.8))
    cmds.append(npc_cmd("paintball", "Paintball", NPC, PAINTBALL_GEAR))
    for game, name in (("valvloer", "Valvloer"), ("chess", "Schaken"), ("verstoppertje", "Verstoppertje")):
        cmds.append(npc_cmd(game, name, GAME_NPCS[game], GEAR[game]))
    return cmds


def _leather(slot, color):
    return f'{slot}:{{id:"minecraft:leather_{slot_item(slot)}",count:1,components:{{"minecraft:dyed_color":{color}}}}}'


def slot_item(slot):
    return {"head": "helmet", "chest": "chestplate", "legs": "leggings", "feet": "boots"}[slot]


def outfit(head, chest, legs, feet):
    return ",".join(_leather(s, col) for s, col in (("head", head), ("chest", chest), ("legs", legs), ("feet", feet))
                    if col is not None)


PAINTBALL_GEAR = ('mainhand:{id:"minecraft:crossbow",count:1,components:{'
                  '"minecraft:charged_projectiles":[{id:"minecraft:arrow",count:1}],'
                  '"minecraft:item_model":"dbsdw:gun","minecraft:custom_model_data":{colors:[16726832]}}},'
                  + outfit(5462314, 5462314, 4211248, 3355443))
GEAR = {
    # a plank from the floor in hand, overalls in the floor's warm wood colours
    "valvloer": 'mainhand:{id:"minecraft:mangrove_planks",count:1},' + outfit(None, 12884526, 9131330, 3355443),
    # a chess clock and a black and white suit
    "chess": 'mainhand:{id:"minecraft:clock",count:1},' + outfit(None, 1908001, 15790320, 1908001),
    # a barrel over the head (the disguise) and a spyglass for the seeker
    "verstoppertje": ('head:{id:"minecraft:barrel",count:1},mainhand:{id:"minecraft:spyglass",count:1},'
                      + outfit(None, 6192150, 4211248, 3355443)),
}


def npc_cmd(game, name, pos, gear):
    """A game NPC: a Mannequin with the dbsdw:game_npc tag GameNpc listens for."""
    x, y, z = w(*pos[:3])
    return ("summon minecraft:mannequin {} {} {} {{Tags:[\"dbsdw_lobby\"],Rotation:[{}f,0f],"
            "CustomName:{{text:\"{}\",color:\"#D9C78F\",bold:true}},CustomNameVisible:1b,"
            "Invulnerable:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b,immovable:1b,"
            "description:{{text:\"Klik om te spelen\",color:\"gray\"}},"
            "equipment:{{{}}},"
            "BukkitValues:{{\"dbsdw:game_npc\":\"{}\"}}}}").format(x, y, z, pos[3], name, gear, game)


# world positions the plugins need (written to lobby.txt by build.py)
def config_notes():
    sx, sy, sz = w(*SPAWN[:3])
    x1, z1, x2, z2 = RANGE
    a, b = w(x1, G, z1), w(x2, G + 8, z2)
    lines = [f"lobby spawn: {sx} {sy} {sz} yaw {SPAWN[3]}",
             f"practice corner-1: [{a[0]}, {a[1]}, {a[2]}]  corner-2: [{b[0]}, {b[1]}, {b[2]}]",
             "practice targets:"]
    for tx, tz in TARGETS:
        p = w(tx + 0.5, G + 1, tz + 0.5)
        lines.append(f"  - [{p[0]}, {p[1]}, {p[2]}, 0]")
    lines.append("leaderboards:")
    for bx, bz in BOARDS:
        p = w(bx + 0.5, G + 3, bz + 0.5)
        lines.append(f"  {p}")
    p = w(*NPC[:3])
    lines.append(f"paintball NPC: {p} yaw {NPC[3]}")
    for game, pos in GAME_NPCS.items():
        lines.append(f"{game} NPC: {w(*pos[:3])} yaw {pos[3]}")
    return lines


# checks: these must be reachable on foot from the spawn
POINTS = {
    "practice range": (56, G + 1, 30),
    "npc": (int(NPC[0]), G + 1, int(NPC[2])),
    "hq hall": (HQ[0] + 8, G + 1, 56),
    "friends' camp": (FRIENDS[0] - 3, G + 1, FRIENDS[1]),
    "valvloer npc": (int(GAME_NPCS["valvloer"][0]), G + 1, int(GAME_NPCS["valvloer"][2])),
    "valvloer floor": (20, G + 1, 76),
    "chess npc": (int(GAME_NPCS["chess"][0]), G + 1, int(GAME_NPCS["chess"][2])),
    "chess board": (42, G + 1, 80),
    "verstoppertje npc": (int(GAME_NPCS["verstoppertje"][0]), G + 1, int(GAME_NPCS["verstoppertje"][2])),
    "verstoppertje shed": (80, G + 1, 84),
    "bridge": (56, G + 2, 97),
    "barracks": (26, G + 1, 25),
    "depot": (86, G + 1, 30),
}


def build():
    c = CANVAS
    c.b.clear()
    terrain(c)
    plaza(c)
    boards(c)
    practice_range(c)
    hq(c)
    friends(c)
    games_square(c)
    bridge(c)
    barracks(c)
    depot(c)
    motor_pool(c)
    woods(c)
    gate(c)
    ground_cover(c)
    tall_grass_fix(c)
    clear_spawn(c)
    c.drop_floating()
    border(c)
    c.fix_connections()

CLOSEUPS = {"centre": (36, 14, 76, 70), "hq": (8, 40, 44, 72), "games": (10, 60, 94, 92), "valvloer": (10, 62, 34, 90), "chess": (30, 60, 60, 90), "hide": (58, 62, 94, 90),
            "friends": (72, 40, 108, 72), "north": (6, 4, 108, 44)}
