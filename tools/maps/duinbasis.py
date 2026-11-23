"""Duinbasis: a desert outpost. Two walled compounds with an HQ bunker, and between them a
container yard under a gantry crane (left lane), an adobe village street (centre), a dry wadi
with a wrecked truck (right lane) and a plaza with a well and market stalls in the middle.

Built on the west half (team 0) and turned 180 degrees, so each team's left lane starts in the
container yard and ends in the enemy's wadi.
"""
from __future__ import annotations

import math

import parts as P
from mcbuild import Canvas, Grad, Pal, fbm, h3, vnoise
from woudfront import poly_dist, rect_dist

WORLD = "world"
SX, SY, SZ = 150, 40, 100
G = 8
ORIGIN = (300, -63, 100)   # south of Woudfront (which ends at z 49)
HALF = SX // 2
SEED = 77

CANVAS = Canvas(SX, SY, SZ, SEED)

BASE = (5, 30, 30, 69)
GATES_E = ((38, 41), (58, 61))
GATE_NS = (15, 17)
HQ = (9, 42, 18, 57)
YARD = (37, 3, 64, 27)
WADI = [(36, 83), (44, 81), (52, 85), (62, 82)]
STREET = (31, 47, 74, 52)
HOUSES = [  # x1, z1, x2, z2, floors, stair side, ruined
    (39, 35, 47, 44, 2, "east", False),
    (51, 37, 58, 44, 1, "west", False),
    (40, 56, 46, 64, 1, "east", False),
    (50, 55, 59, 63, 2, "west", True),
]
# lower annexes, built before the main houses so the shared wall belongs to the house
ANNEXES = [(40, 30, 45, 35, 1), (54, 31, 58, 37, 1), (36, 58, 40, 63, 1), (52, 63, 57, 68, 1)]
DOMES = [(54, 40, 2.6), (43, 60, 2.2)]          # x, z, radius on a roof
TOWER = (60, 31, 62, 33)                       # village lookout (no access: a landmark)
TANK = (63, 9)                                 # burnt-out tank in the open sand
STALLS = [(65, 39, 70, 43), (65, 56, 70, 60)]

SPAWNS = {0: [(7.5, G + 1, 40.5), (7.5, G + 1, 59.5), (13.5, G + 1, 34.5), (13.5, G + 1, 64.5),
              (21.5, G + 1, 37.5), (7.5, G + 1, 49.5)]}
SPAWNS[1] = [(SX - p[0], p[1], SZ - p[2]) for p in SPAWNS[0]]
FLAGS = {0: (24.5, G + 2, 49.5)}
FLAGS[1] = (SX - FLAGS[0][0], FLAGS[0][1], SZ - FLAGS[0][2])
YAW = {0: -90.0, 1: 90.0}
POWERUPS = [(50.5, G + 1, 10.5), (68.5, G + 1, 49.5), (99.5, G + 1, 89.5), (81.5, G + 1, 50.5)]
CLOSEUPS = {"base": (0, 26, 36, 74), "village": (34, 30, 80, 70), "yard": (34, 0, 70, 30),
            "wadi": (32, 70, 72, 99)}

SAND = Pal(("sand", 10), ("sandstone", 1), ("smooth_sandstone", 1), seed=1, scale=4)
STRATA = ["sandstone", "smooth_sandstone", "terracotta", "white_terracotta", "sandstone",
          "light_gray_terracotta", "smooth_sandstone", "terracotta", "white_terracotta", "sandstone"]


def strata(x, y, z):
    """Layered rock like a mesa: bands by height that wobble a little."""
    k = (y + int(vnoise(x, z, 9, 5) * 3)) % len(STRATA)
    return STRATA[k]
ROAD = Pal(("coarse_dirt", 3), ("packed_mud", 2), ("sand", 3), ("dirt_path", 1), seed=2, scale=2)
CONCRETE = Pal(("smooth_stone", 3), ("polished_andesite", 2), ("andesite", 1), ("gravel", 1), seed=3, scale=3)
ADOBE = Grad([Pal(("sandstone", 3), ("packed_mud", 1), ("smooth_sandstone", 1), seed=4),
              Pal(("smooth_sandstone", 3), ("sandstone", 2), ("cut_sandstone", 1), seed=5),
              Pal(("smooth_sandstone", 4), ("cut_sandstone", 2), ("chiseled_sandstone", 1), seed=6)],
             G + 1, G + 9, seed=7)


def in_yard(x, z):
    return rect_dist(x, z, YARD) < 0.5


def in_street(x, z):
    return STREET[0] <= x <= STREET[2] and STREET[1] <= z <= STREET[3]


# --------------------------------------------------------------------------- terrain


def height(x, z):
    flat = 0.0
    for r, k in ((BASE, 6), (YARD, 4), (STREET, 4), ((36, 33, 74, 66), 3)):
        flat = max(flat, 1 - min(1, rect_dist(x, z, r) / k))
    d = (fbm(x, z, 18, SEED) - 0.45) * 2
    dune = max(0.0, d) * 9 + (fbm(x, z, 7, SEED + 1) - 0.5) * 2
    if 70 < z < 95 and x > 32:
        dune += 1.5
    edge = min(x, z, SZ - 1 - z)
    rise = max(0, 6 - edge) * 1.3
    return G + round(dune * (1 - flat) + rise)


def terrain(c: Canvas):
    for x in range(HALF):
        for z in range(SZ):
            h = height(x, z)
            top = SAND
            if in_yard(x, z):
                top = CONCRETE if fbm(x, z, 6, 9) > 0.3 else SAND
            elif in_street(x, z) or poly_dist(x, z, [(31, 39.5), (35, 39.5), (38, 46)]) < 1.5 \
                    or poly_dist(x, z, [(31, 59.5), (35, 59.5), (38, 53)]) < 1.5:
                top = ROAD
            elif fbm(x, z, 11, 97) > 0.68:
                top = Pal(("coarse_dirt", 3), ("gravel", 2), ("packed_mud", 2), ("sandstone", 2), ("sand", 3), seed=98, scale=2)
            elif 64 <= x and 30 <= z <= 69:
                top = Pal(("smooth_sandstone", 3), ("sandstone", 2), ("cut_sandstone", 1), ("sand", 2), seed=10, scale=2)
            c.column(x, z, 0, h - 4, "sandstone")
            c.column(x, z, h - 3, h - 1, "sand")
            if h > G + 2 and min(x, z, SZ - 1 - z) < 7:
                for y in range(G, h):
                    c.set(x, y, z, strata)
            c.set(x, h, z, top)
    # the wadi: a dry riverbed three deep with sloped banks
    for x in range(30, HALF):
        for z in range(70, SZ - 4):
            d = poly_dist(x, z, WADI)
            if x > 64:
                continue
            if d < 5:
                h = c.top_solid(x, z)
                depth = 3 if d < 2.5 else (2 if d < 3.8 else 1)
                floor = G - depth
                for y in range(floor + 1, h + 1):
                    c.set(x, y, z, "air")
                c.set(x, floor, z, Pal(("gravel", 2), ("coarse_dirt", 2), ("sand", 3), ("packed_mud", 1), seed=11, scale=2)
                      if d < 2.5 else Pal(("sand", 3), ("sandstone", 2), ("smooth_sandstone", 1), seed=12))


# --------------------------------------------------------------------------- pieces


def hesco(c: Canvas, x, z, y, height=3):
    """A Hesco bastion block column: wire-mesh sand bag, beige with darker seams."""
    for j in range(height):
        seam = (x + z) % 3 == 0
        c.set(x, y + j, z, "cut_sandstone" if seam else Pal(("smooth_sandstone", 3), ("sandstone", 1), seed=20))
    c.set(x, y + height, z, "sandstone_slab[type=bottom]" if h3(x, 0, z, 21) < 0.5 else "air")


def house(c: Canvas, x1, z1, x2, z2, floors, stair_side, ruined, seed):
    """Adobe house: inset doorways, protruding roof beams (vigas), parapet, roof terrace stairs."""
    top = G + floors * 4
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            c.set(x, G, z, Pal(("smooth_sandstone", 2), ("cut_sandstone", 1), seed=seed))
            for y in range(G + 1, top + 4):
                c.set(x, y, z, "air")
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            if x in (x1, x2) or z in (z1, z2):
                wt = top
                if ruined:
                    t = (x - x1) / (x2 - x1) + (z - z1) / (z2 - z1)
                    if t > 1.1:
                        wt = max(G + 1, top - int((t - 1.1) * 9) - (1 if h3(x, 2, z, seed) < 0.3 else 0))
                for y in range(G + 1, wt + 1):
                    c.set(x, y, z, ADOBE)
                if wt == top:
                    c.set(x, top + 1, z, "smooth_sandstone_slab[type=bottom]" if (x + z) % 2 else "cut_sandstone")
            elif any(y == G + 4 * f for f in range(1, floors + 1) for y in [G + 4 * f]):
                pass
    # floors and roof
    for f in range(1, floors + 1):
        y = G + 4 * f
        for x in range(x1 + 1, x2):
            for z in range(z1 + 1, z2):
                if ruined and (x - x1) / (x2 - x1) + (z - z1) / (z2 - z1) > 1.15 - 0.1 * h3(x, y, z, seed + 1):
                    continue
                c.set(x, y, z, "smooth_sandstone" if f == floors else "spruce_planks")
    # vigas: beam ends poking out under the roof line on the long sides
    for x in range(x1 + 1, x2, 2):
        for z, f in ((z1 - 1, "north"), (z2 + 1, "south")):
            c.set(x, top - 1, z, "stripped_spruce_log[axis=z]")
    # corner buttresses (depth)
    for (x, z) in ((x1, z1), (x1, z2), (x2, z1), (x2, z2)):
        c.column(x, z, G + 1, top - 2, "cut_sandstone")
    # doorways: centred on all four sides, 2 high, inset frame with slabs above
    xm, zm = (x1 + x2) // 2, (z1 + z2) // 2
    for (dx, dz) in ((xm, z1), (xm, z2), (x1, zm), (x2, zm)):
        for y in (G + 1, G + 2):
            c.set(dx, y, dz, "air")
        c.set(dx, G + 3, dz, "stripped_spruce_log[axis=" + ("x" if dz in (z1, z2) else "z") + "]")
    # windows: on each floor, with shutters/bars
    for f in range(floors):
        wy = G + 2 + 4 * f
        for x in range(x1 + 2, x2 - 1, 3):
            if abs(x - xm) <= 1 and f == 0:
                continue
            for z, out in ((z1, -1), (z2, 1)):
                c.set(x, wy, z, "air")
                f = "north" if out < 0 else "south"
                if h3(x, wy, z, seed + 5) < 0.7:
                    for sx2 in (x - 1, x + 1):
                        c.set(sx2, wy, z + out, f"spruce_trapdoor[facing={f},half=bottom,open=true]")
                c.set(x, wy - 1, z + out, f"smooth_sandstone_slab[type=top]")
        for z in range(z1 + 2, z2 - 1, 3):
            if abs(z - zm) <= 1 and f == 0:
                continue
            for x, out in ((x1, -1), (x2, 1)):
                c.set(x, wy, z, "air")
                f = "west" if out < 0 else "east"
                if h3(x, wy, z, seed + 6) < 0.7:
                    for sz2 in (z - 1, z + 1):
                        c.set(x + out, wy, sz2, f"spruce_trapdoor[facing={f},half=bottom,open=true]")
                c.set(x + out, wy - 1, z, f"smooth_sandstone_slab[type=top]")
    # outside stairs up to the roof terrace, against one end wall
    if stair_side:
        sx_ = x2 + 1 if stair_side == "east" else x1 - 1
        facing = "north"
        for i in range(top - G):
            z = z2 - i
            if z < z1:
                break
            c.set(sx_, G + 1 + i, z, f"sandstone_stairs[facing={facing},half=bottom,shape=straight]")
            for k in range(G + 1, G + 1 + i):
                c.set(sx_, k, z, ADOBE)
        # landing and a gap in the parapet
        z_land = z2 - (top - G)
        c.set(sx_, top, z_land, "smooth_sandstone")
        c.set(x2 if stair_side == "east" else x1, top + 1, z_land, "air")
        c.set(x2 if stair_side == "east" else x1, top + 1, z_land + 1, "air")
    # rooftop clutter: water barrels, a crate pile, a rooftop hut on bigger roofs
    if not ruined:
        c.set(x1 + 1, top + 1, z1 + 1, "barrel[facing=up,open=false]")
        c.set(x1 + 2, top + 1, z1 + 1, "barrel[facing=up,open=false]")
    # props inside
    P.crate(c, x1 + 1, G + 1, z1 + 1, seed)
    c.set(x2 - 1, G + 1, z2 - 1, "decorated_pot")


def awning(c: Canvas, x1, z1, x2, z2, y, cols=("white_wool", "yellow_wool")):
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            c.set(x, y, z, cols[(x if (x2 - x1) <= (z2 - z1) else z) % 2])
    for (px, pz) in ((x1, z1), (x1, z2), (x2, z1), (x2, z2)):
        c.column(px, pz, G + 1, y - 1, "spruce_fence")


def stall(c: Canvas, x1, z1, x2, z2, seed):
    awning(c, x1, z1, x2, z2, G + 4, ("white_wool", "yellow_wool") if seed % 2 else ("white_wool", "lime_wool"))
    for x in range(x1 + 1, x2):
        c.set(x, G + 1, z1 + 1, "spruce_planks")
        c.set(x, G + 2, z1 + 1, Pal("barrel[facing=up,open=false]", "hay_block[axis=y]", "decorated_pot", "air", seed=seed))


def jeep(c: Canvas, x, y, z, axis="z", wreck=False):
    body = Pal(("green_terracotta", 5), ("moss_block", 1), seed=30) if not wreck else \
        Pal(("brown_terracotta", 3), ("green_terracotta", 2), ("gray_terracotta", 1), seed=31)
    L, W = 5, 3

    def at(i, a):  # i along the car, a across
        return (x + i, z + a) if axis == "x" else (x + a, z + i)
    for i in range(L):
        for a in range(W):
            px, pz = at(i, a)
            c.set(px, y, pz, body)
            if i in (0, L - 1) or a in (0, W - 1):
                if i >= 2:
                    c.set(px, y + 1, pz, "green_terracotta" if not wreck else body)
    for i in (0, L - 1):
        for a in (-1, W):
            px, pz = at(i, a)
            c.set(px, y, pz, "polished_blackstone" if not wreck else "air")
    px, pz = at(1, 1)
    c.set(px, y + 1, pz, "glass_pane" if not wreck else "air")
    for a in (0, 2):
        px, pz = at(1, a)
        c.set(px, y + 1, pz, "glass_pane" if not wreck else "air")
    px, pz = at(L - 1, 1)
    c.set(px, y + 2, pz, "barrel[facing=up,open=false]")


def hedgehog(c: Canvas, x, y, z):
    """Tank traps: three concrete teeth in a staggered row (block + slab = 1.5 high cover)."""
    for k, (dx, dz) in enumerate(((0, 0), (2, 1), (-2, 1))):
        ty = c.top_solid(x + dx, z + dz) + 1
        c.set(x + dx, ty, z + dz, Pal(("polished_andesite", 3), ("andesite", 1), seed=k))
        c.set(x + dx, ty + 1, z + dz, "andesite_slab[type=bottom]")


def base(c: Canvas):
    x1, z1, x2, z2 = BASE
    for x in range(x1 + 1, x2):
        for z in range(z1 + 1, z2):
            c.set(x, G, z, Pal(("smooth_sandstone", 2), ("sand", 3), ("packed_mud", 1), ("gravel", 1), seed=40, scale=3))
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            if not (x in (x1, x2) or z in (z1, z2)):
                continue
            gate = (x == x2 and any(a <= z <= b for a, b in GATES_E)) or \
                   (z in (z1, z2) and GATE_NS[0] <= x <= GATE_NS[1])
            if gate:
                continue
            hesco(c, x, z, G + 1, 4 if x in (x1, x2) and z in (z1, z2) else 3)
    # gate posts with a sign beam and lamps
    for a, b in GATES_E:
        for z in (a - 1, b + 1):
            c.column(x2 + 1, z, G + 1, G + 5, "stripped_spruce_log[axis=y]")
        for z in range(a - 1, b + 2):
            c.set(x2 + 1, G + 5, z, "stripped_spruce_log[axis=z]")
        c.set(x2 + 2, G + 4, a - 1, "lantern[hanging=false]")
        for z in (a, b):
            c.set(x2 + 2, G + 5, z, "team_wall_banner[facing=east]")
        # sandbag chicane outside each gate
        P.sandbags(c, x2 + 4, a - 2, x2 + 4, b + 2, None, 1.5)
    # walkway behind the east wall
    for z in range(z1 + 1, z2):
        c.set(x2 - 1, G + 3, z, "spruce_planks")
        if z % 5 == 0 and not any(a - 1 <= z <= b + 1 for a, b in GATES_E):
            c.column(x2 - 1, z, G + 1, G + 2, "spruce_fence")
    for lz in (45, 54):
        c.column(x2 - 1, lz, G + 1, G + 2, "spruce_planks")
        c.column(x2 - 2, lz, G + 1, G + 3, "ladder[facing=west]")
    hq(c)
    fx, fy, fz = int(FLAGS[0][0]), int(FLAGS[0][1]), int(FLAGS[0][2])
    P.flag_podium(c, fx, fy, fz, stone="smooth_stone", trim="sandstone_stairs")
    for (sx_, sz_, ex, ez) in ((fx - 2, fz - 3, fx + 2, fz - 3), (fx - 2, fz + 3, fx + 2, fz + 3)):
        P.sandbags(c, sx_, sz_, ex, ez, G + 1, 1.5)
    # vehicle bay under camo netting, supply dump, tower
    P.camo_net(c, 20, 59, 28, 67, G + 5, seed=41)
    jeep(c, 22, G + 1, 61, axis="z")
    jeep(c, 26, G + 1, 61, axis="z")
    P.camo_net(c, 20, 32, 27, 38, G + 5, seed=42)
    P.crate_stack(c, 23, G + 1, 33, w=3, d=2, h=2, seed=43)
    c.set(25, G + 1, 36, "barrel[facing=up,open=false]")
    P.watchtower(c, 6, G + 1, 31, height=8, wood="jungle", ladder_side="east")
    # water tank on legs next to the HQ
    for (px, pz) in ((20, 42), (22, 42), (20, 44), (22, 44)):
        c.column(px, pz, G + 1, G + 4, "iron_bars")
    c.box(19, G + 5, 41, 23, G + 7, 45, Pal(("light_gray_concrete", 3), ("white_concrete", 1), seed=44))
    c.box(20, G + 8, 42, 22, G + 8, 44, "smooth_stone_slab[type=bottom]")


def hq(c: Canvas):
    x1, z1, x2, z2 = HQ
    wall = Grad([Pal(("smooth_sandstone", 2), ("cut_sandstone", 2), ("sandstone", 1), seed=50),
                 Pal(("smooth_sandstone", 4), ("cut_sandstone", 1), seed=51)], G + 1, G + 9, seed=52)
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            edge = x in (x1, x2) or z in (z1, z2)
            for y in range(G + 1, G + 9):
                c.set(x, y, z, wall if edge else "air")
            c.set(x, G + 4, z, "smooth_stone" if not edge else wall)
            c.set(x, G + 8, z, "smooth_stone")
            if edge:
                c.set(x, G + 9, z, "smooth_sandstone_slab[type=bottom]" if (x + z) % 2 else "smooth_sandstone")
    # a band of dark stone at floor level and pilasters (depth)
    for x in range(x1 - 1, x2 + 2):
        for z in (z1 - 1, z2 + 1):
            c.set(x, G + 1, z, "polished_andesite")
            if x % 3 == 0:
                c.column(x, z, G + 2, G + 8, "cut_sandstone")
    for z in range(z1 - 1, z2 + 2):
        for x in (x1 - 1, x2 + 1):
            c.set(x, G + 1, z, "polished_andesite")
            if z % 3 == 0:
                c.column(x, z, G + 2, G + 8, "cut_sandstone")
    # firing slits: one high, on both floors
    for f in (0, 1):
        y = G + 2 + 4 * f
        for z in range(z1 + 2, z2 - 1, 2):
            for x in (x1, x2):
                c.set(x, y, z, "air")
        for x in range(x1 + 2, x2 - 1, 2):
            for z in (z1, z2):
                c.set(x, y, z, "air")
    # doors east and west, 2 wide
    zm = (z1 + z2) // 2
    for z in (zm, zm + 1):
        for y in (G + 1, G + 2, G + 3):
            c.set(x2, y, z, "air")
            c.set(x1, y, z, "air")
            c.set(x2 + 1, y, z, "air" if y < G + 3 else "air")
            c.set(x1 - 1, y, z, "air")
        c.set(x2 + 1, G + 4, z, "smooth_stone_slab[type=top]")
    # stairs: ground -> first floor (north wall) and first floor -> roof (south wall)
    for i in range(4):
        c.set(x1 + 2 + i, G + 1 + i, z1 + 1, "smooth_quartz_stairs[facing=east,half=bottom,shape=straight]" if False else
              "sandstone_stairs[facing=east,half=bottom,shape=straight]")
        c.set(x1 + 2 + i, G + 4, z1 + 1, "air" if i < 3 else c.get(x1 + 2 + i, G + 4, z1 + 1))
        c.set(x1 + 2 + i, G + 4, z1 + 2, "air" if i < 3 else c.get(x1 + 2 + i, G + 4, z1 + 2))
    for i in range(4):
        c.set(x2 - 2 - i, G + 5 + i, z2 - 1, "sandstone_stairs[facing=west,half=bottom,shape=straight]")
        if i < 3:
            c.set(x2 - 2 - i, G + 8, z2 - 1, "air")
            c.set(x2 - 2 - i, G + 8, z2 - 2, "air")
    # team flag on a pole above the roof
    c.column(x2 - 2, z1 + 2, G + 9, G + 14, "spruce_fence")
    c.set(x2 - 2, G + 15, z1 + 2, "team_banner[rotation=12]")
    # roof: antenna, dish, AC units, a sandbag nest facing east
    c.column(x1 + 2, z1 + 2, G + 9, G + 16, "iron_bars")
    c.set(x1 + 2, G + 17, z1 + 2, "waxed_lightning_rod")
    c.set(x1 + 5, G + 9, z1 + 3, "daylight_detector[inverted=false,power=0]")
    c.set(x1 + 5, G + 10, z1 + 3, "white_concrete")
    c.set(x1 + 4, G + 9, z2 - 3, "smooth_stone")
    c.set(x1 + 5, G + 9, z2 - 3, "iron_trapdoor[facing=north,half=bottom,open=false]")
    P.sandbags(c, x2 - 1, z1 + 4, x2 - 1, z2 - 4, G + 9, 1.0)
    # inside: map table, crates, bunks
    c.set(x1 + 4, G + 1, zm, "cartography_table")
    c.set(x1 + 4, G + 1, zm + 1, "cartography_table")
    P.crate_stack(c, x1 + 1, G + 1, z2 - 3, w=1, d=2, h=2, seed=53)
    for z in range(z1 + 2, z2 - 1, 3):
        c.set(x1 + 1, G + 5, z, "white_wool")
        c.set(x1 + 2, G + 5, z, "white_wool")


def container_yard(c: Canvas):
    rows = [
        (39, 7, 7, "x", "orange", True, False), (48, 7, 7, "x", "green", True, True), (57, 7, 7, "x", "white", False, False),
        (41, 13, 6, "x", "yellow", False, False),
        (38, 20, 7, "x", "cyan", True, False), (47, 20, 7, "x", "orange", False, True), (56, 20, 7, "x", "light_gray", True, False),
        (62, 11, 6, "z", "lime", False, False),
    ]
    for (x, z, n, axis, col, doors, stacked) in rows:
        P.container(c, x, G + 1, z, length=n, axis=axis, color=col, doors=doors, seed=x + z)
        if stacked:
            top_col = {"green": "yellow", "orange": "brown"}.get(col, "white")
            P.container(c, x + 1, G + 4, z, length=n - 2, axis=axis, color=top_col, doors=True, seed=x * 3 + z)
    # gantry crane over the middle row, with a container hanging from it
    yb = G + 12
    for (px, pz) in ((38, 10), (38, 16), (60, 10), (60, 16)):
        c.column(px, pz, G + 1, yb, Pal(("yellow_concrete", 4), ("yellow_terracotta", 1), seed=60))
    for x in range(37, 62):
        for pz in (10, 16):
            c.set(x, yb, pz, "yellow_concrete")
            c.set(x, yb + 1, pz, "iron_bars")
    for z in range(10, 17):
        for px in (38, 60):
            c.set(px, yb, z, "yellow_concrete")
    for z in range(11, 16):
        for x in (49, 50, 51):
            c.set(x, yb, z, "gray_concrete")       # trolley the container hangs from
    for x in (49, 51):
        for y in range(G + 9, yb):
            c.set(x, y, 13, "iron_chain[axis=y]")
    P.container(c, 47, G + 6, 13, length=7, axis="x", color="green", doors=True, seed=99)
    c.box(39, yb + 1, 9, 41, yb + 3, 11, Pal(("yellow_concrete", 2), ("glass", 1), seed=61))
    # pallets and drums in the yard
    for (x, z) in ((44, 16), (45, 16), (55, 4), (53, 25), (36, 14)):
        c.set(x, G + 1, z, "barrel[facing=up,open=false]")
    for (x, z) in ((46, 25), (58, 15), (42, 4)):
        P.crate_stack(c, x, G + 1, z, w=2, d=1, h=2, seed=x)


def village(c: Canvas):
    for i, (x1, z1, x2, z2, floors) in enumerate(ANNEXES):
        house(c, x1, z1, x2, z2, floors, None, False, seed=60 + i)
    for i, (x1, z1, x2, z2, floors, side, ruined) in enumerate(HOUSES):
        house(c, x1, z1, x2, z2, floors, side, ruined, seed=70 + i)
    for (x, z, r) in DOMES:
        dome(c, x, z, r, c.top_solid(x, z) if False else G + 4)
    village_tower(c)
    arch(c, 35)
    street_shades(c)
    # awnings on the street fronts
    awning(c, 41, 45, 45, 46, G + 4, ("white_wool", "brown_wool"))
    awning(c, 52, 53, 57, 54, G + 4, ("white_wool", "green_wool"))
    # street clutter as cover
    for (x, z) in ((36, 48), (49, 51), (56, 48), (62, 51)):
        P.crate_stack(c, x, G + 1, z, w=1, d=1, h=1 + (x % 2), seed=x)
    for (x, z) in ((48, 47), (60, 52)):
        c.set(x, G + 1, z, "hay_block[axis=x]")
    # low ruined walls in the alleys
    for (x, z1, z2) in ((49, 36, 40), (48, 58, 62)):
        for z in range(z1, z2 + 1):
            if h3(x, 0, z, 80) < 0.75:
                c.set(x, G + 1, z, ADOBE)
                if h3(x, 1, z, 81) < 0.5:
                    c.set(x, G + 2, z, "sandstone_slab[type=bottom]")


def plaza(c: Canvas):
    # well: 4x4 centred on the map centre (the other half comes from the rotation)
    for x in (72, 73, 74):
        for z in range(47, 53):
            edge = x == 72 or z in (47, 52)
            c.set(x, G, z, "water" if not edge else "smooth_sandstone")
            c.set(x, G - 1, z, "sandstone" if edge else "water")
            if edge:
                c.set(x, G + 1, z, "smooth_sandstone_slab[type=bottom]")
    for (x, z) in ((72, 47), (72, 52)):
        c.column(x, z, G + 2, G + 3, "spruce_fence")
        c.set(x, G + 4, z, "spruce_slab[type=bottom]")
    for z in range(47, 53):
        c.set(72, G + 4, z, "spruce_slab[type=bottom]")
    for z in range(48, 52):
        c.set(73, G + 4, z, "spruce_slab[type=bottom]")
        c.set(74, G + 4, z, "spruce_slab[type=bottom]")
    c.set(73, G + 3, 49, "iron_chain[axis=y]")
    c.set(73, G + 2, 49, "lantern[hanging=true]")
    for i, s in enumerate(STALLS):
        stall(c, *s, seed=i)
    P.palm(c, 67, G + 1, 46, height=7, seed=1)
    P.palm(c, 69, G + 1, 63, height=6, seed=2)
    # ruined walls north and south of the plaza
    for (x1, z1, x2, z2) in ((64, 24, 70, 24), (66, 30, 66, 35), (64, 74, 71, 74), (68, 66, 68, 70)):
        for x in range(x1, x2 + 1):
            for z in range(z1, z2 + 1):
                hh = 1 + int(h3(x, 0, z, 90) * 3)
                for j in range(hh):
                    c.set(x, c.top_solid(x, z) + 1, z, ADOBE)
    # radio mast with guy wires
    mx, mz = 69, 17
    by = c.top_solid(mx, mz)
    c.box(mx - 1, by + 1, mz - 1, mx + 1, by + 1, mz + 1, "polished_andesite")
    c.column(mx, mz, by + 2, by + 20, "iron_bars")
    c.set(mx, by + 21, mz, "waxed_lightning_rod")
    for k in (8, 13, 18):
        for d in (-1, 1):
            c.set(mx + d, by + k, mz, "iron_bars")
            c.set(mx, by + k, mz + d, "iron_bars")
    c.set(mx - 1, by + 2, mz + 1, "lantern[hanging=false]")


def wadi(c: Canvas):
    # a wrecked truck on its side in the riverbed and a plank footbridge
    jeep(c, 42, G - 2, 81, axis="x", wreck=True)
    for x in range(50, 54):
        pass
    for z in range(79, 89):
        if poly_dist(55, z, WADI) < 5:
            c.set(55, G, z, "spruce_planks")
            c.set(56, G, z, "spruce_planks")
            c.set(54, G + 1, z, "spruce_fence")
            c.set(57, G + 1, z, "spruce_fence")
    for x in (55, 56):
        c.column(x, 83, G - 3, G - 1, "spruce_log[axis=y]")
    for (x, z, r) in ((38, 86, 1.8), (60, 79, 1.6), (47, 88, 1.4)):
        c.blob(x, G - 2, z, r, 1.2, r, Pal(("sandstone", 3), ("smooth_sandstone", 1), seed=x), only_empty=False, rough=0.4, seed=x)
    for (x, z) in ((33, 77), (49, 92), (58, 91), (35, 91)):
        hedgehog(c, x, c.top_solid(x, z) + 1, z)
    P.sandbags(c, 44, 76, 50, 76, c.top_solid(47, 76) + 1, 1.5)
    P.sandbags(c, 48, 91, 54, 92, c.top_solid(51, 91) + 1, 1.5)


def dome(c: Canvas, x, z, r, top):
    """Plaster dome on a drum, with a finial: breaks the flat-roof skyline."""
    for dx in range(-int(r) - 1, int(r) + 2):
        for dz in range(-int(r) - 1, int(r) + 2):
            if dx * dx + dz * dz <= r * r:
                c.set(x + dx, top + 1, z + dz, "cut_sandstone")
    for y in range(int(r) + 2):
        rr = math.sqrt(max(0.0, r * r - (y * 1.1) ** 2))
        for dx in range(-int(r) - 1, int(r) + 2):
            for dz in range(-int(r) - 1, int(r) + 2):
                if dx * dx + dz * dz <= rr * rr + 0.3:
                    c.set(x + dx, top + 2 + y, z + dz, Pal(("smooth_sandstone", 5), ("white_terracotta", 1), seed=5))
    yt = c.top(x, z)
    c.set(x, yt + 1, z, "waxed_lightning_rod")


def village_tower(c: Canvas):
    x1, z1, x2, z2 = TOWER
    h = G + 17
    for y in range(G + 1, h + 1):
        for x in range(x1 - 1, x2 + 2):
            for z in range(z1 - 1, z2 + 2):
                if x in (x1 - 1, x2 + 1) or z in (z1 - 1, z2 + 1):
                    taper = y > G + 12 and (x in (x1 - 1, x2 + 1) and z in (z1 - 1, z2 + 1))
                    c.set(x, y, z, "air" if taper else ADOBE)
                else:
                    c.set(x, y, z, ADOBE if y < G + 12 else "air")
    # belt course, open lookout with arches, crenellated top
    for x in range(x1 - 2, x2 + 3):
        for z in range(z1 - 2, z2 + 3):
            if x in (x1 - 2, x2 + 2) or z in (z1 - 2, z2 + 2):
                c.set(x, G + 12, z, "smooth_sandstone_slab[type=top]")
    for y in (G + 13, G + 14, G + 15):
        for k in (x1, x1 + 1, x1 + 2):
            c.set(k, y, z1 - 1, "air")
            c.set(k, y, z2 + 1, "air")
        for k in (z1, z1 + 1, z1 + 2):
            c.set(x1 - 1, y, k, "air")
            c.set(x2 + 1, y, k, "air")
    c.box(x1 - 1, h + 1, z1 - 1, x2 + 1, h + 1, z2 + 1, "smooth_sandstone_slab[type=bottom]")
    for (x, z) in ((x1 - 1, z1 - 1), (x2 + 1, z1 - 1), (x1 - 1, z2 + 1), (x2 + 1, z2 + 1)):
        c.set(x, h + 1, z, "chiseled_sandstone")
        c.set(x, h + 2, z, "sandstone_slab[type=bottom]")
    c.set(x1 + 1, G + 13, z1 + 1, "lantern[hanging=false]")
    for y in range(G + 3, G + 11, 4):
        c.set(x1, y, z1 - 1, "air")
        c.set(x2 + 1, y, z1 + 1, "air")


def arch(c: Canvas, x):
    """Gate arch over the village street, with the team's banners on the side facing the base."""
    za, zb = STREET[1] - 2, STREET[3] + 2
    for z in (za, za + 1, zb - 1, zb):
        for xx in (x, x + 1):
            c.column(xx, z, G + 1, G + 9, ADOBE)
    for xx in (x, x + 1):
        for z in range(za + 2, zb - 1):
            d = min(z - (za + 2), (zb - 2) - z)
            low = G + 8 - min(d, 2)
            for y in range(low, G + 10):
                c.set(xx, y, z, ADOBE)
            c.set(xx, low - 1, z, "sandstone_stairs[facing=" + ("south" if z < (za + zb) / 2 else "north") + ",half=top,shape=straight]" if d < 2 else "air")
        for z in range(za, zb + 1):
            c.set(xx, G + 10, z, "smooth_sandstone_slab[type=bottom]" if z % 2 else "cut_sandstone")
    c.set(x, G + 9, (za + zb) // 2, "chiseled_sandstone")
    c.set(x - 1, G + 9, za + 1, "team_wall_banner[facing=west]")
    c.set(x - 1, G + 9, zb - 1, "team_wall_banner[facing=west]")
    c.set(x - 1, G + 5, za, "lantern[hanging=false]")


def street_shades(c: Canvas):
    """Cloth shades on ropes across the street: shade, colour, and cover from roof shots."""
    for (x1, x2) in ((40, 45), (50, 55), (59, 63)):
        for z in range(STREET[1] - 1, STREET[3] + 2):
            for x in range(x1, x2 + 1):
                if h3(x, 0, z, 88) < 0.12:
                    continue
                # a carpet needs a block under it, or the first block update pops it off
                c.set(x, G + 5, z, "spruce_slab[type=top]")
                c.set(x, G + 6, z, ("white_carpet", "yellow_carpet", "white_carpet", "orange_carpet")[(z // 2) % 4]
                      if x1 != 50 else ("white_carpet", "lime_carpet")[(x // 2) % 2])
        for x in (x1, x2):
            for z in (STREET[1] - 1, STREET[3] + 1):
                if c.empty(x, G + 4, z):
                    c.column(x, z, G + 1, G + 4, "spruce_fence")
                    c.set(x, G + 5, z, "spruce_log[axis=y]")


def tank(c: Canvas):
    """Burnt-out tank: olive hull on dark tracks, turret turned sideways, scorch marks, drifted sand."""
    x0, z0 = TANK
    y = c.top_solid(x0 + 4, z0 + 2) + 1
    hull = Pal(("green_terracotta", 6), ("moss_block", 1), ("brown_terracotta", 1), seed=101)
    for i in range(9):
        for a in range(5):
            x, z = x0 + i, z0 + a
            c.set(x, y - 1, z, "coarse_dirt")
            if a in (0, 4):
                c.set(x, y, z, "polished_blackstone" if i % 2 else "blackstone")
                c.set(x, y + 1, z, "polished_blackstone_slab[type=bottom]" if 0 < i < 8 else "air")
            else:
                c.set(x, y, z, hull)
                c.set(x, y + 1, z, hull)
            if 0 < i < 8:
                c.set(x, y + 2, z, hull if a in (1, 2, 3) else "green_terracotta")
    for a in range(5):
        c.set(x0 - 1, y + 1, z0 + a, "mud_brick_stairs[facing=east,half=bottom,shape=straight]" if a in (1, 2, 3) else "air")
        c.set(x0 + 9, y + 1, z0 + a, "mud_brick_stairs[facing=west,half=bottom,shape=straight]" if a in (1, 2, 3) else "air")
    # turret, turned toward the north-east, hatch open
    c.box(x0 + 3, y + 3, z0 + 1, x0 + 5, y + 4, z0 + 3, Pal(("green_terracotta", 4), ("gray_terracotta", 1), seed=102))
    c.set(x0 + 4, y + 5, z0 + 2, "iron_trapdoor[facing=north,half=bottom,open=true]")
    for k in range(0, 4):
        c.set(x0 + 4, y + 4, z0 - k, "polished_basalt[axis=z]")
    # scorch and drifted sand
    for (dx, dz) in ((2, 1), (6, 3), (5, 2), (1, 3)):
        c.set(x0 + dx, y + 2, z0 + dz, "coal_block")
    c.blob(x0 + 9, y, z0 + 2, 2.4, 1.6, 3.0, "sand", only_empty=True, rough=0.4, seed=103)
    c.blob(x0 - 1, y - 0.5, z0 + 4, 1.8, 1.0, 1.8, "sand", only_empty=True, rough=0.4, seed=104)
    for (dx, dz) in ((-2, -1), (10, 5), (3, 6)):
        c.set(x0 + dx, c.top_solid(x0 + dx, z0 + dz) + 1, z0 + dz, "barrel[facing=north,open=false]")


def oasis(c: Canvas):
    """A small pool at the deep end of the wadi, with reeds and palms."""
    for x in range(55, 65):
        for z in range(76, 90):
            if poly_dist(x, z, WADI) < 1.8 and x >= 57:
                c.set(x, G - 3, z, "water")
                c.set(x, G - 4, z, "mud")
                if h3(x, 0, z, 111) < 0.12:
                    c.set(x, G - 2, z, "lily_pad")
            elif poly_dist(x, z, WADI) < 2.6 and x >= 57:
                c.set(x, G - 3, z, "mud")
                if h3(x, 1, z, 112) < 0.5:
                    c.column(x, z, G - 2, G - 1 + int(h3(x, 2, z, 113) * 2), "sugar_cane")
    P.palm(c, 60, G - 2, 87, height=7, seed=5)
    P.palm(c, 63, G - 1, 78, height=6, seed=6)


def ground_cover(c: Canvas):
    """Dry grass, dead bushes and pebbles on open sand; never on roads, roofs or in buildings."""
    for x in range(1, HALF):
        for z in range(1, SZ - 1):
            y = c.top(x, z)
            if y < 0 or not c.get(x, y, z).startswith("sand") or y > G + 9:
                continue
            if in_street(x, z) or in_yard(x, z) or rect_dist(x, z, BASE) < 1:
                continue
            r = h3(x, 3, z, 95)
            clump = fbm(x, z, 9, 96)
            if r < 0.05 + 0.10 * max(0.0, clump - 0.5):
                c.set(x, y + 1, z, "short_dry_grass")
            elif r < 0.07 + 0.08 * max(0.0, clump - 0.55):
                c.set(x, y + 1, z, "tall_dry_grass")
            elif r < 0.085:
                c.set(x, y + 1, z, "dead_bush")
            elif r < 0.095:
                c.set(x, y + 1, z, "sandstone_slab[type=bottom]")


def rock(c: Canvas, x, z, r, height, seed):
    """Mesa outcrop: a lumpy column that narrows upward, banded like the cliffs."""
    base = c.top_solid(x, z)
    for y in range(base - 1, base + height + 1):
        t = (y - base) / height
        rr = r * (1 - 0.45 * t) + (h3(0, y, 0, seed) - 0.5) * 0.8
        for dx in range(-int(r) - 2, int(r) + 3):
            for dz in range(-int(r) - 2, int(r) + 3):
                d = math.hypot(dx * 1.0, dz * 1.25) + (vnoise(x + dx, z + dz + y * 3, 3, seed) - 0.5) * 1.6
                if d <= rr:
                    c.set(x + dx, y, z + dz, strata)
    for dx in range(-int(r), int(r) + 1):
        for dz in range(-int(r), int(r) + 1):
            y = c.top_solid(x + dx, z + dz)
            if y >= base + height - 1:
                c.set(x + dx, y, z + dz, "sand")


def field(c: Canvas):
    """Open sand between the compound and the lanes: tank traps, sandbag nests, dead bushes."""
    for (x, z) in ((34, 30), (36, 69), (33, 44), (33, 56)):
        hedgehog(c, x, c.top_solid(x, z) + 1, z)
    P.sandbags(c, 35, 31, 35, 36, None, 1.5)
    P.sandbags(c, 35, 63, 35, 68, None, 1.5)
    for (x, z, r, hh) in ((30, 17, 3.2, 7), (16, 12, 4.0, 9), (19, 88, 4.0, 8), (31, 79, 2.6, 5), (67, 91, 3.4, 10)):
        rock(c, x, z, r, hh, seed=x * 31 + z)
    for (x, z) in ((3, 20), (2, 80), (30, 94), (60, 97), (28, 5), (66, 3)):
        P.palm(c, x, c.top_solid(x, z) + 1, z, height=6 + x % 3, seed=x)


def border(c: Canvas):
    for x in range(SX):
        for z in range(SZ):
            if x in (0, SX - 1) or z in (0, SZ - 1):
                h = c.top_solid(x, z)
                for y in range(max(h + 1, 1), SY):
                    c.set(x, y, z, "barrier")


# The owner's in-game edits of 2026-11-22, ported into the west half so the rotation repeats them
# for team 1: a supply cache dug into the south cliff off the wadi footbridge (entrance tunnel,
# chests, barrels, a short rail track, a sandstone roof so the sand above stays put), sugar cane
# at the oasis, an opened doorway in a timber screen, a removed roof slab and one floor tile.
STASH = [
    (54, 8, 89, "spruce_planks"), (55, 8, 89, "spruce_planks"), (56, 8, 89, "spruce_planks"),
    (54, 9, 89, "spruce_fence"), (56, 11, 89, "sandstone"), (56, 12, 89, "sand"),
    (55, 8, 92, "sand"), (58, 9, 91, "sand"), (60, 9, 92, "sand"),
    (54, 9, 92, "chest[facing=east,type=left,waterlogged=false]"),
    (54, 9, 93, "chest[facing=east,type=right,waterlogged=false]"),
    (59, 9, 92, "chest[facing=west,type=single,waterlogged=false]"),
    (59, 10, 92, "chest[facing=west,type=single,waterlogged=false]"),
    (59, 9, 93, "barrel[facing=west,open=false]"), (59, 10, 93, "barrel[facing=west,open=false]"),
    (59, 9, 94, "barrel[facing=west,open=false]"),
    (55, 9, 95, "rail[shape=ascending_east,waterlogged=false]"),
    (56, 9, 95, "barrel[facing=up,open=false]"), (57, 9, 95, "barrel[facing=up,open=false]"),
    (56, 10, 95, "rail[shape=east_west,waterlogged=false]"),
    (57, 10, 95, "rail[shape=east_west,waterlogged=false]"),
    (58, 9, 95, "rail[shape=ascending_west,waterlogged=false]"),
    (59, 9, 95, "chest[facing=west,type=single,waterlogged=false]"),
]
STASH_AIR = [  # the tunnel and the room
    (55, 9, 89), (56, 9, 89), (55, 10, 89), (56, 10, 89), (55, 9, 90), (56, 9, 90), (55, 10, 90), (56, 10, 90),
    (55, 9, 91), (56, 9, 91), (55, 10, 91), (56, 10, 91),
    (55, 9, 92), (56, 9, 92), (57, 9, 92), (58, 9, 92), (54, 10, 92), (55, 10, 92), (56, 10, 92), (57, 10, 92),
    (58, 10, 92), (55, 9, 93), (56, 9, 93), (57, 9, 93), (58, 9, 93), (55, 10, 93), (56, 10, 93), (57, 10, 93),
    (58, 10, 93), (55, 9, 94), (56, 9, 94), (57, 9, 94), (58, 9, 94), (55, 10, 94), (56, 10, 94),
    (57, 10, 94), (58, 10, 94), (59, 10, 94), (55, 10, 95), (58, 10, 95),
    (54, 10, 93), (59, 10, 95), (60, 9, 93),
]
STASH_ROOF = [(x, 11, z) for x in range(54, 60) for z in range(90, 93)]
CANE = [(61, 80), (62, 80), (63, 80), (59, 81), (64, 81), (60, 85)]


def owner_edits(c: Canvas):
    for p in STASH_AIR:
        c.set(*p, "air")
    for p in STASH_ROOF:
        if c.get(*p) in ("sand", "air"):
            c.set(*p, "sandstone")
    for x, y, z, s in STASH:
        c.set(x, y, z, s)
    for x, z in CANE:
        c.set(x, G, z, "sugar_cane[age=0]")
    c.set(9, 10, 32, "air")              # doorway through the timber screen
    c.set(49, 13, 60, "air")             # roof slab
    c.set(35, 10, 46, "smooth_sandstone")


def build():
    c = CANVAS
    c.b.clear()
    terrain(c)
    base(c)
    container_yard(c)
    village(c)
    plaza(c)
    wadi(c)
    oasis(c)
    field(c)
    tank(c)
    ground_cover(c)
    owner_edits(c)
    c.rotate_west_half()
    c.drop_floating()
    border(c)
    c.fix_connections()
