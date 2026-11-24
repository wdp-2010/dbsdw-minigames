"""Duinbasis: a desert outpost. Two walled compounds with an HQ bunker; between them a container
yard under a gantry crane (north lane), an adobe village around a market square with a domed well
hall in the middle (centre), and a dry wadi with an oasis under a sandstone bluff (south lane).

Built on the west half (team 0) and turned 180 degrees, so each team's left lane starts in the
container yard and ends in the enemy's wadi.

Fourth pass (2026-11-24): the owner found the ground noisy, the houses full of odd holes and
the street cluttered. The terrain is now long smooth dunes (no small bumps, no colour blotches),
every house has its doors where they are drawn, real stairs to the roof and a ladder between floors,
and the middle is a market square around a well hall with a green copper dome.
"""
from __future__ import annotations

import math

import parts as P
from mcbuild import Canvas, Grad, Pal, h3, vnoise
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
VILLAGE = (32, 28, 74, 71)
WADI = [(36, 83), (44, 81), (52, 85), (62, 82)]
NORTH_ST = (31, 37, 74, 43)
SOUTH_ST = (31, 55, 50, 61)
SQUARE = (48, 37, 74, 62)
HALL = (68, 43, 74, 56)          # west half of the well hall; the rotation adds x 75..81
# x1, z1, x2, z2, floors, doors (side, position along it), outside stair to the roof, ruined
HOUSES = [
    (36, 29, 46, 36, 2, (("N", 41), ("W", 32), ("E", 33)), ("S", -1), False),
    (50, 29, 57, 35, 1, (("S", 53), ("N", 54)), None, False),
    (38, 44, 47, 54, 1, (("W", 49), ("E", 50), ("N", 43)), None, False),
    (51, 57, 61, 64, 2, (("W", 60), ("S", 56), ("E", 61)), ("N", 1), False),
    (37, 62, 45, 68, 1, (("N", 41), ("E", 65)), None, True),
]
TOWER = (61, 30, 63, 32)         # village lookout (a landmark, no way up)
TANK = (65, 16)                  # burnt-out tank in the open sand
STALLS = [(60, 38, 64, 41), (63, 58, 67, 61)]
TRACKS = [[(16, 29), (16, 21), (22, 15), (36, 15)], [(16, 70), (16, 77), (24, 80), (34, 80)]]

SPAWNS = {0: [(7.5, G + 1, 40.5), (7.5, G + 1, 59.5), (13.5, G + 1, 34.5), (13.5, G + 1, 64.5),
              (21.5, G + 1, 37.5), (7.5, G + 1, 49.5)]}
SPAWNS[1] = [(SX - p[0], p[1], SZ - p[2]) for p in SPAWNS[0]]
FLAGS = {0: (24.5, G + 2, 49.5)}
FLAGS[1] = (SX - FLAGS[0][0], FLAGS[0][1], SZ - FLAGS[0][2])
YAW = {0: -90.0, 1: 90.0}
POWERUPS = [(50.5, G + 1, 10.5), (66.5, G + 1, 49.5), (99.5, G + 1, 89.5), (83.5, G + 1, 50.5)]
CLOSEUPS = {"base": (0, 26, 36, 74), "village": (32, 26, 76, 72), "square": (46, 34, 104, 66),
            "yard": (34, 0, 76, 30), "wadi": (28, 70, 74, 99)}

SAND = Pal(("sand", 14), ("sandstone", 1), seed=1, scale=8)
HARDPAN = Pal(("sandstone", 3), ("smooth_sandstone", 2), ("sand", 1), seed=13, scale=4)
PAVING = Pal(("smooth_sandstone", 5), ("sandstone", 2), ("cut_sandstone", 1), seed=2, scale=4)
RUTS = Pal(("coarse_dirt", 3), ("packed_mud", 1), seed=14, scale=3)
CONCRETE = Pal(("smooth_stone", 3), ("polished_andesite", 2), ("andesite", 1), seed=3, scale=4)
STRATA = ["sandstone", "smooth_sandstone", "terracotta", "white_terracotta", "sandstone",
          "light_gray_terracotta", "smooth_sandstone", "terracotta", "white_terracotta", "sandstone"]
ADOBE = Grad([Pal(("sandstone", 3), ("packed_mud", 1), ("smooth_sandstone", 1), seed=4),
              Pal(("smooth_sandstone", 3), ("sandstone", 2), ("cut_sandstone", 1), seed=5),
              Pal(("smooth_sandstone", 4), ("cut_sandstone", 2), ("chiseled_sandstone", 1), seed=6)],
             G + 1, G + 9, seed=7)
# copper gone green: a turquoise dome (waxed, so it stays this colour)
TILES = Pal(("waxed_oxidized_copper", 4), ("waxed_oxidized_cut_copper", 2), ("waxed_weathered_cut_copper", 1),
            seed=15, scale=2)


def strata(x, y, z):
    """Layered rock like a mesa: bands by height that wobble a little."""
    k = (y + int(vnoise(x, z, 9, 5) * 3)) % len(STRATA)
    return STRATA[k]


def smooth(t):
    t = min(1.0, max(0.0, t))
    return t * t * (3 - 2 * t)


def inside(r, x, z, pad=0):
    return r[0] - pad <= x <= r[2] + pad and r[1] - pad <= z <= r[3] + pad


# --------------------------------------------------------------------------- terrain


def dunes(x, z):
    """Long dunes: ridges run roughly north-south, gentle on the west (windward) side and
    steeper on the lee side. Nothing in here is smaller than about eight blocks, so the ground
    reads as dunes instead of noise."""
    warp = (vnoise(x, z, 34, SEED) - 0.5) * 14
    p = ((x * 0.93 + z * 0.36 + warp) / 19.0) % 1.0
    prof = smooth(p / 0.7 if p < 0.7 else (1 - p) / 0.3)
    amp = 1.0 + 3.5 * vnoise(x, z, 28, SEED + 3)
    return prof * amp + (vnoise(x, z, 20, SEED + 5) - 0.5) * 1.0


def raw_height(x, z):
    flat = 0.0
    for r, k in ((BASE, 6), (YARD, 4), (VILLAGE, 4)):
        flat = max(flat, 1 - min(1, rect_dist(x, z, r) / k))
    h = max(0.0, dunes(x, z)) * (1 - flat)
    edge = min(x, z, SZ - 1 - z)
    h += max(0, 7 - edge) ** 1.3 * 0.75
    # the sandstone bluff south of the wadi (the owner's supply cache is dug into it)
    h += 5 * smooth((z - 86.5) / 3) * smooth((x - 26) / 6) * smooth((70 - x) / 4)
    return h


def height_map():
    """Integer heights of the west half. Near the centre line each column is blended with the
    column it meets after the rotation, so the two halves join without a step. Lone one-block
    bumps and pits are smoothed away."""
    H = {}
    for x in range(HALF):
        w = 0.5 + 0.5 * smooth((HALF - 1 - x) / 10)
        for z in range(SZ):
            H[(x, z)] = G + round(w * raw_height(x, z) + (1 - w) * raw_height(x, SZ - 1 - z))
    for (x, z), h in list(H.items()):
        nb = [H.get((x + dx, z + dz)) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))]
        nb = [n for n in nb if n is not None]
        if all(n < h for n in nb):
            H[(x, z)] = h - 1
        elif all(n > h for n in nb):
            H[(x, z)] = h + 1
    return H


def terrain(c: Canvas):
    H = height_map()

    def hh(x, z):
        if x >= HALF:                       # east of the centre line: the rotated west column
            x, z = SX - 1 - x, SZ - 1 - z
        return H.get((x, z), G)
    for (x, z), h in H.items():
        if inside(YARD, x, z):
            top = CONCRETE
        elif inside(SQUARE, x, z) or inside(NORTH_ST, x, z) or inside(SOUTH_ST, x, z):
            top = PAVING
        elif h == G and not inside(VILLAGE, x, z, 2) and vnoise(x, z, 14, 98) > 0.74:
            top = HARDPAN
        else:
            top = SAND
        if any(abs(poly_dist(x, z, t) - 1.0) < 0.5 for t in TRACKS):
            top = RUTS
        c.column(x, z, 0, h - 4, "sandstone")
        c.column(x, z, h - 3, h - 1, "sand")
        low = min(hh(x + dx, z + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if h - low >= 2:                    # a cliff face shows banded rock, the top stays sand
            for y in range(max(G - 1, low), h):
                c.set(x, y, z, strata)
        c.set(x, h, z, top)
    # the wadi: a dry riverbed three deep with sloped banks
    for x in range(30, 65):
        for z in range(70, SZ - 4):
            d = poly_dist(x, z, WADI)
            if d < 5:
                h = c.top_solid(x, z)
                depth = 3 if d < 2.5 else (2 if d < 3.8 else 1)
                floor = G - depth
                for y in range(floor + 1, h + 1):
                    c.set(x, y, z, "air")
                c.set(x, floor, z, Pal(("gravel", 2), ("coarse_dirt", 2), ("sand", 3), ("packed_mud", 1), seed=11, scale=3)
                      if d < 2.5 else Pal(("sand", 4), ("sandstone", 1), seed=12, scale=3))


# --------------------------------------------------------------------------- pieces


def hesco(c: Canvas, x, z, y, height=3):
    """A Hesco bastion column: wire-mesh sand bag, beige with darker seams, a slab cap."""
    for j in range(height):
        c.set(x, y + j, z, "cut_sandstone" if (x + z) % 3 == 0 else Pal(("smooth_sandstone", 3), ("sandstone", 1), seed=20))
    c.set(x, y + height, z, "sandstone_slab[type=bottom]")


def wall_cell(side, x1, z1, x2, z2, pos):
    """The wall block of a house side at `pos` along it, and the outward direction."""
    return {"N": ((pos, z1), (0, -1)), "S": ((pos, z2), (0, 1)),
            "W": ((x1, pos), (-1, 0)), "E": ((x2, pos), (1, 0))}[side]


def house(c: Canvas, x1, z1, x2, z2, floors, doors, stair, ruined, seed):
    """Adobe house: doorways only where listed, windows with shutters and sills, protruding roof
    beams (vigas), a parapet, a ladder between floors and, if asked, an outside stair up to the
    roof that really reaches it. A ruined house has one corner collapsed on a diagonal."""
    top = G + floors * 4
    xm, zm = (x1 + x2) / 2, (z1 + z2) / 2

    def collapsed(x, z, y):
        if not ruined:
            return False
        t = (x - x1) / (x2 - x1) + (z - z1) / (z2 - z1)
        return y > top - int(max(0.0, t - 1.0) * 9)

    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            c.set(x, G, z, Pal(("smooth_sandstone", 2), ("cut_sandstone", 1), seed=seed))
            for y in range(G + 1, top + 3):
                c.set(x, y, z, "air")
            if x in (x1, x2) or z in (z1, z2):
                corner = x in (x1, x2) and z in (z1, z2)
                for y in range(G + 1, top + 1):
                    if not collapsed(x, z, y):
                        c.set(x, y, z, "cut_sandstone" if corner and y < top else ADOBE)
                if not collapsed(x, z, top):
                    c.set(x, top + 1, z, "cut_sandstone" if (x + z) % 2 == 0 else "smooth_sandstone_slab[type=bottom]")
    for f in range(1, floors + 1):
        y = G + 4 * f
        for x in range(x1 + 1, x2):
            for z in range(z1 + 1, z2):
                if not collapsed(x, z, y):
                    c.set(x, y, z, "smooth_sandstone" if f == floors else "spruce_planks")
    # rubble where the corner came down
    if ruined:
        for (dx, dz, r) in ((-2, -1, 1.6), (0, 0, 2.0), (1, -2, 1.3)):
            c.blob(x2 + dx, G + 1, z2 + dz, r, 1.0, r, ADOBE, only_empty=True, rough=0.3, seed=seed + dx)
    stair_side = stair[0] if stair else None
    # vigas: beam ends poking out under the roof line on the long sides (not over the stair)
    long_x = (x2 - x1) >= (z2 - z1)
    for k in range((x1 if long_x else z1) + 1, (x2 if long_x else z2), 2):
        for side in (("N", "S") if long_x else ("W", "E")):
            if side == stair_side:
                continue
            (wx, wz), (ox, oz) = wall_cell(side, x1, z1, x2, z2, k)
            if not collapsed(wx, wz, top - 1):
                c.set(wx + ox, top - 1, wz + oz, f"stripped_spruce_log[axis={'z' if long_x else 'x'}]")
    # windows: one per three blocks, never next to a door or a corner, with a sill and shutters
    door_cells = {wall_cell(s, x1, z1, x2, z2, p)[0] for s, p in doors}
    for f in range(floors):
        wy = G + 2 + 4 * f
        for side in ("N", "S", "W", "E"):
            a, b = (x1, x2) if side in ("N", "S") else (z1, z2)
            for k in range(a + 2, b - 1, 3):
                (wx, wz), (ox, oz) = wall_cell(side, x1, z1, x2, z2, k)
                if f == 0 and any(abs(wx - dx) + abs(wz - dz) <= 1 for dx, dz in door_cells):
                    continue
                if side == stair_side or collapsed(wx, wz, wy + 1):
                    continue
                c.set(wx, wy, wz, "air")
                c.set(wx + ox, wy - 1, wz + oz, "smooth_sandstone_slab[type=top]")
                facing = {(0, -1): "north", (0, 1): "south", (-1, 0): "west", (1, 0): "east"}[(ox, oz)]
                for s in (-1, 1):
                    sx_, sz_ = (wx + s, wz) if side in ("N", "S") else (wx, wz + s)
                    c.set(sx_ + ox, wy, sz_ + oz, f"spruce_trapdoor[facing={facing},half=bottom,open=true]")
    # doorways: 2 high, a timber lintel above
    for side, pos in doors:
        (dx, dz), _ = wall_cell(side, x1, z1, x2, z2, pos)
        c.set(dx, G + 1, dz, "air")
        c.set(dx, G + 2, dz, "air")
        if not collapsed(dx, dz, G + 3):
            c.set(dx, G + 3, dz, "stripped_spruce_log[axis=" + ("x" if side in ("N", "S") else "z") + "]")
    # ladder between floors, in the north-west corner
    if floors > 1:
        for y in range(G + 1, G + 4 * floors - 3):
            c.set(x1 + 1, y, z1 + 1, "ladder[facing=south]")
        for f in range(1, floors):
            c.set(x1 + 1, G + 4 * f, z1 + 1, "ladder[facing=south]")
    # outside stair up to the roof terrace, along one long wall, filled underneath
    if stair:
        side, d = stair
        n = top - G
        (wx, wz), (ox, oz) = wall_cell(side, x1, z1, x2, z2, x1 if side in ("N", "S") else z1)
        start = (x2 - 1 if d < 0 else x1 + 1) if side in ("N", "S") else (z2 - 1 if d < 0 else z1 + 1)
        facing = {(-1, "x"): "west", (1, "x"): "east", (-1, "z"): "north", (1, "z"): "south"}[
            (d, "x" if side in ("N", "S") else "z")]
        for i in range(n):
            k = start + d * i
            sx_, sz_ = (k, wz + oz) if side in ("N", "S") else (wx + ox, k)
            for y in range(G + 1, G + 1 + i):
                c.set(sx_, y, sz_, ADOBE)
            c.set(sx_, G + 1 + i, sz_, f"sandstone_stairs[facing={facing},half=bottom,shape=straight]"
                  if i < n - 1 else "smooth_sandstone")
            for y in range(G + 2 + i, G + 5 + i):
                c.set(sx_, y, sz_, "air")
        # the gap in the parapet at the top of the stair
        k = start + d * (n - 1)
        gx, gz = (k, wz) if side in ("N", "S") else (wx, k)
        c.set(gx, top + 1, gz, "air")
    # roof clutter and a few things inside
    if not ruined:
        c.set(x2 - 1, top + 1, z2 - 1, "barrel[facing=up,open=false]")
        c.set(x2 - 2, top + 1, z2 - 1, "barrel[facing=up,open=false]")
    P.crate(c, x2 - 1, G + 1, z1 + 1, seed)
    c.set(x1 + 1, G + 1, z2 - 1, "decorated_pot")


def dome(c: Canvas, cx, cz, r, base, tiles, tall=1.0):
    """A dome on a low drum, centred on (cx, cz) (may be half a block for an even width).
    tall > 1 stretches it upward."""
    rr = int(r) + 2
    x0, z0 = int(math.floor(cx)), int(math.floor(cz))
    for dx in range(-rr, rr + 2):
        for dz in range(-rr, rr + 2):
            x, z = x0 + dx, z0 + dz
            d = math.hypot(x - cx, z - cz)
            if d <= r + 0.5:
                c.set(x, base, z, "chiseled_sandstone" if d > r - 0.5 else "cut_sandstone")
            for y in range(int(r * tall) + 1):
                rad = math.sqrt(max(0.0, r * r - (y * 1.15 / tall) ** 2))
                if d <= rad + 0.3:
                    c.set(x, base + 1 + y, z, tiles)
    c.set(x0, c.top(x0, z0) + 1, z0, "waxed_lightning_rod")


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
        c.set(x, G + 2, z1 + 1, ("barrel[facing=up,open=false]", "hay_block[axis=y]", "decorated_pot")[(x + seed) % 3])


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
            if (i in (0, L - 1) or a in (0, W - 1)) and i >= 2:
                c.set(px, y + 1, pz, "green_terracotta" if not wreck else body)
    if not wreck:
        for i in (0, L - 1):
            for a in (-1, W):
                px, pz = at(i, a)
                c.set(px, y, pz, "polished_blackstone")
        for a in (0, 1, 2):
            px, pz = at(1, a)
            c.set(px, y + 1, pz, "glass_pane")
    px, pz = at(L - 1, 1)
    c.set(px, y + 2, pz, "barrel[facing=up,open=false]")


def hedgehog(c: Canvas, x, z):
    """Tank traps: three concrete teeth in a staggered row (block + slab = 1.5 high cover)."""
    for k, (dx, dz) in enumerate(((0, 0), (2, 1), (-2, 1))):
        ty = c.top_solid(x + dx, z + dz) + 1
        c.set(x + dx, ty, z + dz, Pal(("polished_andesite", 3), ("andesite", 1), seed=k))
        c.set(x + dx, ty + 1, z + dz, "andesite_slab[type=bottom]")


# --------------------------------------------------------------------------- the compound


def base(c: Canvas):
    x1, z1, x2, z2 = BASE
    for x in range(x1 + 1, x2):
        for z in range(z1 + 1, z2):
            c.set(x, G, z, Pal(("smooth_sandstone", 3), ("sandstone", 2), ("sand", 2), seed=40, scale=5))
    # a paved apron in front of the HQ doors and the flag
    for x in range(19, x2):
        for z in range(47, 53):
            c.set(x, G, z, CONCRETE)
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            if not (x in (x1, x2) or z in (z1, z2)):
                continue
            gate = (x == x2 and any(a <= z <= b for a, b in GATES_E)) or \
                   (z in (z1, z2) and GATE_NS[0] <= x <= GATE_NS[1])
            if not gate:
                hesco(c, x, z, G + 1, 4 if x in (x1, x2) and z in (z1, z2) else 3)
    # gate frames: two posts, a beam with the team's banners, lanterns on the beam ends
    for a, b in GATES_E:
        for z in (a - 1, b + 1):
            c.column(x2 + 1, z, G + 1, G + 5, "stripped_spruce_log[axis=y]")
            c.set(x2 + 1, G + 6, z, "lantern[hanging=false]")
        for z in range(a, b + 1):
            c.set(x2 + 1, G + 5, z, "stripped_spruce_log[axis=z]")
        for z in (a, b):
            c.set(x2 + 2, G + 5, z, "team_wall_banner[facing=east]")
        P.sandbags(c, x2 + 4, a - 1, x2 + 4, b + 1, None, 1.5)
    # walkway behind the east wall on log supports, with two ladders
    for z in range(z1 + 1, z2):
        c.set(x2 - 1, G + 3, z, "spruce_planks")
        if z % 4 == 2 and not any(a - 1 <= z <= b + 1 for a, b in GATES_E):
            c.column(x2 - 1, z, G + 1, G + 2, "stripped_spruce_log[axis=y]")
    for lz in (45, 54):
        c.column(x2 - 1, lz, G + 1, G + 2, "spruce_planks")
        c.column(x2 - 2, lz, G + 1, G + 3, "ladder[facing=west]")
    hq(c)
    fx, fy, fz = int(FLAGS[0][0]), int(FLAGS[0][1]), int(FLAGS[0][2])
    P.flag_podium(c, fx, fy, fz, stone="smooth_stone", trim="sandstone_stairs")
    for (sx_, sz_, ex, ez) in ((fx - 2, fz - 3, fx + 2, fz - 3), (fx - 2, fz + 3, fx + 2, fz + 3)):
        P.sandbags(c, sx_, sz_, ex, ez, G + 1, 1.5)
    # vehicle bay under camo netting, supply dump, watchtower
    P.camo_net(c, 20, 59, 28, 67, G + 5, seed=41)
    jeep(c, 22, G + 1, 61, axis="z")
    jeep(c, 26, G + 1, 61, axis="z")
    P.camo_net(c, 20, 32, 27, 38, G + 5, seed=42)
    P.crate_stack(c, 23, G + 1, 33, w=3, d=2, h=2, seed=43)
    c.set(25, G + 1, 36, "barrel[facing=up,open=false]")
    P.watchtower(c, 6, G + 1, 31, height=8, wood="jungle", ladder_side="east")
    # water tank on four log legs next to the HQ
    for (px, pz) in ((20, 42), (22, 42), (20, 44), (22, 44)):
        c.column(px, pz, G + 1, G + 4, "stripped_spruce_log[axis=y]")
    c.box(19, G + 5, 41, 23, G + 7, 45, Pal(("light_gray_concrete", 3), ("white_concrete", 1), seed=44))
    c.box(20, G + 8, 42, 22, G + 8, 44, "smooth_stone_slab[type=bottom]")


def hq(c: Canvas):
    """Two-storey bunker: dark plinth, corner pilasters, one window per three blocks on each
    floor, a door front and back, stairs inside, a crenellated roof with a sandbag nest."""
    x1, z1, x2, z2 = HQ
    wall = Grad([Pal(("smooth_sandstone", 2), ("cut_sandstone", 2), ("sandstone", 1), seed=50),
                 Pal(("smooth_sandstone", 4), ("cut_sandstone", 1), seed=51)], G + 1, G + 9, seed=52)
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            edge = x in (x1, x2) or z in (z1, z2)
            for y in range(G + 1, G + 8):
                c.set(x, y, z, wall if edge else "air")
            c.set(x, G + 4, z, "smooth_stone" if not edge else wall)
            c.set(x, G + 8, z, "smooth_stone")
            if edge:
                c.set(x, G + 9, z, "smooth_sandstone" if (x + z) % 3 else "smooth_sandstone_slab[type=bottom]")
    for x in range(x1 - 1, x2 + 2):
        for z in range(z1 - 1, z2 + 2):
            if x in (x1 - 1, x2 + 1) or z in (z1 - 1, z2 + 1):
                c.set(x, G + 1, z, "polished_andesite")
    for (x, z) in ((x1 - 1, z1 - 1), (x2 + 1, z1 - 1), (x1 - 1, z2 + 1), (x2 + 1, z2 + 1)):
        c.column(x, z, G + 2, G + 8, "cut_sandstone")
    zm = (z1 + z2) // 2
    for f in (0, 1):
        y = G + 2 + 4 * f
        for z in range(z1 + 2, z2 - 1, 3):
            if f == 0 and zm - 1 <= z <= zm + 2:
                continue
            for x in (x1, x2):
                c.set(x, y, z, "air")
        for x in range(x1 + 2, x2 - 1, 3):
            for z in (z1, z2):
                c.set(x, y, z, "air")
    # doors east and west, 2 wide and 3 high, a slab awning over the front one
    for z in (zm, zm + 1):
        for y in (G + 1, G + 2, G + 3):
            for x in (x1 - 1, x1, x2, x2 + 1):
                c.set(x, y, z, "air")
        c.set(x2 + 1, G + 4, z, "smooth_stone_slab[type=top]")
    # stairs: ground -> first floor along the north wall, first floor -> roof along the south wall
    for i in range(4):
        c.set(x1 + 2 + i, G + 1 + i, z1 + 1, "sandstone_stairs[facing=east,half=bottom,shape=straight]")
        if i < 3:
            c.set(x1 + 2 + i, G + 4, z1 + 1, "air")
            c.set(x1 + 2 + i, G + 4, z1 + 2, "air")
    for i in range(4):
        c.set(x2 - 2 - i, G + 5 + i, z2 - 1, "sandstone_stairs[facing=west,half=bottom,shape=straight]")
        if i < 3:
            c.set(x2 - 2 - i, G + 8, z2 - 1, "air")
            c.set(x2 - 2 - i, G + 8, z2 - 2, "air")
    # team flag on a pole above the roof
    c.column(x2 - 2, z1 + 2, G + 9, G + 14, "spruce_fence")
    c.set(x2 - 2, G + 15, z1 + 2, "team_banner[rotation=12]")
    # roof: a dish, AC units, a sandbag nest facing east
    c.set(x1 + 3, G + 9, z1 + 3, "white_concrete")
    c.set(x1 + 3, G + 10, z1 + 3, "daylight_detector[inverted=false,power=0]")
    for z in (z2 - 4, z2 - 5):
        c.set(x1 + 2, G + 9, z, "smooth_stone")
        c.set(x1 + 3, G + 9, z, "iron_trapdoor[facing=west,half=bottom,open=false]")
    P.sandbags(c, x2 - 1, z1 + 4, x2 - 1, z2 - 6, G + 9, 1.0)
    # inside: map table, crates, bunks
    c.set(x1 + 4, G + 1, zm, "cartography_table")
    c.set(x1 + 4, G + 1, zm + 1, "cartography_table")
    P.crate_stack(c, x1 + 1, G + 1, z2 - 3, w=1, d=2, h=2, seed=53)
    for z in range(z1 + 3, z2 - 3, 3):
        c.set(x1 + 1, G + 5, z, "white_wool")
        c.set(x1 + 2, G + 5, z, "white_wool")


# --------------------------------------------------------------------------- north lane


def container_yard(c: Canvas):
    rows = [
        (39, 7, 7, "x", "orange", True, False), (48, 7, 7, "x", "green", True, True), (57, 7, 7, "x", "white", False, False),
        (41, 13, 6, "x", "yellow", False, False),
        (38, 20, 7, "x", "cyan", True, False), (47, 20, 7, "x", "orange", False, True), (56, 20, 7, "x", "light_gray", True, False),
        (63, 9, 6, "z", "lime", False, False),
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
    for (x, z) in ((44, 16), (45, 16), (55, 4), (53, 25), (36, 14)):
        c.set(x, G + 1, z, "barrel[facing=up,open=false]")
    for (x, z) in ((46, 25), (57, 15), (42, 4)):
        P.crate_stack(c, x, G + 1, z, w=2, d=1, h=2, seed=x)


def tank(c: Canvas):
    """Burnt-out tank: olive hull on dark tracks, turret turned sideways, scorch marks, drifted sand."""
    x0, z0 = TANK
    y = max(c.top_solid(x0 + i, z0 + a) for i in range(9) for a in range(5)) + 1
    hull = Pal(("green_terracotta", 6), ("moss_block", 1), ("brown_terracotta", 1), seed=101)
    for i in range(9):
        for a in range(5):
            x, z = x0 + i, z0 + a
            for yy in range(c.top_solid(x, z) + 1, y):
                c.set(x, yy, z, "sand")
            c.set(x, y - 1, z, "coarse_dirt")
            if a in (0, 4):
                c.set(x, y, z, "polished_blackstone" if i % 2 else "blackstone")
                c.set(x, y + 1, z, "polished_blackstone_slab[type=bottom]" if 0 < i < 8 else "air")
            else:
                c.set(x, y, z, hull)
                c.set(x, y + 1, z, hull)
            if 0 < i < 8:
                c.set(x, y + 2, z, hull if a in (1, 2, 3) else "green_terracotta")
    for a in (1, 2, 3):
        c.set(x0 - 1, y + 1, z0 + a, "mud_brick_stairs[facing=east,half=bottom,shape=straight]")
        c.set(x0 + 9, y + 1, z0 + a, "mud_brick_stairs[facing=west,half=bottom,shape=straight]")
        c.set(x0 - 1, y, z0 + a, "mud_bricks")
        c.set(x0 + 9, y, z0 + a, "mud_bricks")
    c.box(x0 + 3, y + 3, z0 + 1, x0 + 5, y + 4, z0 + 3, Pal(("green_terracotta", 4), ("gray_terracotta", 1), seed=102))
    c.set(x0 + 4, y + 5, z0 + 2, "iron_trapdoor[facing=north,half=bottom,open=true]")
    for k in range(0, 4):
        c.set(x0 + 4, y + 4, z0 - k, "polished_basalt[axis=z]")
    for (dx, dz) in ((2, 1), (6, 3), (5, 2), (1, 3)):
        c.set(x0 + dx, y + 2, z0 + dz, "coal_block")
    c.blob(x0 + 9, y, z0 + 2, 2.4, 1.6, 3.0, "sand", only_empty=True, rough=0.2, seed=103)


# --------------------------------------------------------------------------- the village


def village(c: Canvas):
    for i, (x1, z1, x2, z2, floors, doors, stair, ruined) in enumerate(HOUSES):
        house(c, x1, z1, x2, z2, floors, doors, stair, ruined, seed=70 + i)
    dome(c, 42.5, 49, 2.6, G + 5, Pal(("smooth_sandstone", 5), ("white_terracotta", 1), seed=5))
    village_tower(c)
    arch(c, 32)
    # awnings over two doorways, on slabs fixed to the wall (no posts in the street)
    for (x1, x2, z, out) in ((52, 55, 36, 1), (40, 45, 43, -1)):
        for x in range(x1, x2 + 1):
            c.set(x, G + 4, z, "spruce_slab[type=top]")
            c.set(x, G + 5, z, ("white_carpet", "orange_carpet")[x % 2])
    # street clutter as cover: crates and hay, a handcart
    for (x, z, h) in ((35, 39, 2), (48, 42, 1), (34, 57, 1), (47, 59, 2)):
        P.crate_stack(c, x, G + 1, z, w=1, d=1, h=h, seed=x)
    c.set(56, G + 1, 41, "hay_block[axis=x]")
    c.set(57, G + 1, 41, "hay_block[axis=x]")
    c.set(56, G + 2, 41, "hay_block[axis=x]")


def village_tower(c: Canvas):
    """A tall adobe lookout: a solid shaft, a belt course, an open top with arches. A landmark."""
    x1, z1, x2, z2 = TOWER
    h = G + 17
    for y in range(G + 1, h + 1):
        for x in range(x1 - 1, x2 + 2):
            for z in range(z1 - 1, z2 + 2):
                ring = x in (x1 - 1, x2 + 1) or z in (z1 - 1, z2 + 1)
                corner = x in (x1 - 1, x2 + 1) and z in (z1 - 1, z2 + 1)
                if y < G + 12:
                    c.set(x, y, z, "cut_sandstone" if corner else ADOBE)
                elif ring:
                    c.set(x, y, z, "cut_sandstone" if corner else ADOBE)
                else:
                    c.set(x, y, z, "air")
    for x in range(x1 - 2, x2 + 3):
        for z in range(z1 - 2, z2 + 3):
            if x in (x1 - 2, x2 + 2) or z in (z1 - 2, z2 + 2):
                c.set(x, G + 12, z, "smooth_sandstone_slab[type=top]")
    # arched openings, three wide, on each side of the lookout
    for y in (G + 13, G + 14):
        for k in (x1, x1 + 1, x1 + 2):
            c.set(k, y, z1 - 1, "air")
            c.set(k, y, z2 + 1, "air")
        for k in (z1, z1 + 1, z1 + 2):
            c.set(x1 - 1, y, k, "air")
            c.set(x2 + 1, y, k, "air")
    c.box(x1 - 1, h + 1, z1 - 1, x2 + 1, h + 1, z2 + 1, "smooth_sandstone_slab[type=bottom]")
    for (x, z) in ((x1 - 1, z1 - 1), (x2 + 1, z1 - 1), (x1 - 1, z2 + 1), (x2 + 1, z2 + 1)):
        c.set(x, h + 1, z, "chiseled_sandstone")
    c.set(x1 + 1, G + 12, z1 + 1, "smooth_sandstone")
    c.set(x1 + 1, G + 13, z1 + 1, "lantern[hanging=false]")
    # blind niches (recessed, not holes) on the shaft
    for y in (G + 4, G + 8):
        c.set(x1 + 1, y, z1 - 1, "chiseled_sandstone")
        c.set(x2 + 1, y, z1 + 1, "chiseled_sandstone")


def arch(c: Canvas, x):
    """Gate arch over the north street, with the team's banners on the side facing the base."""
    za, zb = NORTH_ST[1] - 2, NORTH_ST[3] + 2
    for z in (za, za + 1, zb - 1, zb):
        for xx in (x, x + 1):
            c.column(xx, z, G + 1, G + 9, ADOBE)
    for xx in (x, x + 1):
        for z in range(za + 2, zb - 1):
            d = min(z - (za + 2), (zb - 2) - z)
            low = G + 8 - min(d, 2)
            for y in range(low, G + 10):
                c.set(xx, y, z, ADOBE)
            if d < 2:
                c.set(xx, low - 1, z, "sandstone_stairs[facing=" + ("north" if z < (za + zb) / 2 else "south")
                      + ",half=top,shape=straight]")
        for z in range(za, zb + 1):
            c.set(xx, G + 10, z, "smooth_sandstone_slab[type=bottom]" if z % 2 else "cut_sandstone")
    c.set(x, G + 9, (za + zb) // 2, "chiseled_sandstone")
    c.set(x - 1, G + 9, za + 1, "team_wall_banner[facing=west]")
    c.set(x - 1, G + 9, zb - 1, "team_wall_banner[facing=west]")


# --------------------------------------------------------------------------- the middle


def hall(c: Canvas):
    """The well hall in the middle of the map: an arcade of piers around a paved floor, a flat
    roof and a turquoise dome over the well. Every side is open, so it is a crossroads with
    cover, and the dome shows where the middle is from anywhere on the map."""
    x1, z1, x2, z2 = HALL
    cx, cz = HALF - 0.5, (SZ - 1) / 2
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            c.set(x, G, z, "cut_sandstone" if (x + z) % 2 else "smooth_sandstone")
            for y in range(G + 1, G + 6):
                c.set(x, y, z, "air")
    piers_z = (z1, z1 + 4, z2 - 4, z2)
    piers_x = (x1, x1 + 3, x2)
    piers = {(x1, z) for z in piers_z} | {(x, z) for x in piers_x for z in (z1, z2)}
    for (x, z) in piers:
        c.set(x, G + 1, z, "chiseled_sandstone")
        c.column(x, z, G + 2, G + 4, "cut_sandstone")
    # lintels and arch corners
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            edge = x == x1 or z in (z1, z2)
            c.set(x, G + 5, z, ADOBE if edge else "smooth_sandstone")
            c.set(x, G + 6, z, "smooth_sandstone_slab[type=bottom]" if edge and (x + z) % 2 else "smooth_sandstone")
            if edge and (x, z) not in piers:
                near = any(abs(x - px) + abs(z - pz) == 1 for px, pz in piers)
                if near:
                    to = [(px, pz) for px, pz in piers if abs(x - px) + abs(z - pz) == 1][0]
                    facing = {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}[(to[0] - x, to[1] - z)]
                    c.set(x, G + 4, z, f"sandstone_stairs[facing={facing},half=top,shape=straight]")
    # inner piers that carry the dome drum
    for (x, z) in ((x2 - 3, z1 + 4), (x2 - 3, z2 - 4)):
        c.set(x, G + 1, z, "chiseled_sandstone")
        c.column(x, z, G + 2, G + 4, "cut_sandstone")
    dome(c, cx, cz, 4.4, G + 7, TILES, tall=1.5)
    # the well: a 4x4 rim (half of it here) with water in the middle and a lantern over it
    for x in (x2 - 1, x2):
        for z in range(int(cz) - 1, int(cz) + 3):
            rim = x == x2 - 1 or z in (int(cz) - 1, int(cz) + 2)
            c.set(x, G, z, "smooth_sandstone" if rim else "water")
            c.set(x, G - 1, z, "sandstone" if rim else "water")
            c.set(x, G - 2, z, "sandstone")
            if rim:
                c.set(x, G + 1, z, "smooth_sandstone_slab[type=bottom]")
    c.set(x2, G + 5, int(cz), "iron_chain[axis=y]")
    c.set(x2, G + 4, int(cz), "lantern[hanging=true]")
    # benches against the inner side of the piers
    for z in (z1 + 1, z2 - 1):
        for x in (x1 + 1, x1 + 2):
            c.set(x, G + 1, z, f"sandstone_stairs[facing={'north' if z == z1 + 1 else 'south'},half=bottom,shape=straight]")


def square(c: Canvas):
    """The market square between the houses and the hall: stalls, palms, a broken wall,
    water troughs and crates, so there is cover all the way across."""
    for i, s in enumerate(STALLS):
        stall(c, *s, seed=i)
    P.palm(c, 66, G + 1, 39, height=7, seed=1)
    P.palm(c, 49, G + 1, 56, height=6, seed=2)
    # a broken garden wall, two high in the middle and stepping down at the ends
    for i, x in enumerate(range(55, 62)):
        h = 1 if i in (0, 6) else 2
        for y in range(G + 1, G + 1 + h):
            c.set(x, y, 47, ADOBE)
        if i in (1, 5):
            c.set(x, G + 3, 47, "sandstone_slab[type=bottom]")
    # water troughs (1.5 high: block + water would flow, so a cauldron row)
    for x in (52, 53, 54):
        c.set(x, G + 1, 52, "cauldron")
    P.crate_stack(c, 61, G + 1, 52, w=2, d=1, h=2, seed=12)
    c.set(58, G + 1, 52, "barrel[facing=up,open=false]")
    c.set(58, G + 1, 53, "barrel[facing=up,open=false]")


# --------------------------------------------------------------------------- south lane


def wadi(c: Canvas):
    # a wrecked truck on its side in the riverbed and a plank footbridge
    jeep(c, 42, G - 2, 81, axis="x", wreck=True)
    for z in range(79, 89):
        if poly_dist(55, z, WADI) < 5:
            c.set(55, G, z, "spruce_planks")
            c.set(56, G, z, "spruce_planks")
            c.set(54, G + 1, z, "spruce_fence")
            c.set(57, G + 1, z, "spruce_fence")
    for x in (55, 56):
        c.column(x, 83, G - 3, G - 1, "spruce_log[axis=y]")
    for (x, z, r) in ((38, 86, 1.8), (60, 79, 1.6), (47, 88, 1.4)):
        c.blob(x, G - 2, z, r, 1.2, r, Pal(("sandstone", 3), ("smooth_sandstone", 1), seed=x), only_empty=False, rough=0.2, seed=x)
    for (x, z) in ((33, 75), (49, 76), (63, 74)):
        hedgehog(c, x, z)
    P.sandbags(c, 44, 75, 48, 75, None, 1.5)


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


# --------------------------------------------------------------------------- open ground


def ground_cover(c: Canvas):
    """Clumps of dry grass and a few dead bushes in the dune troughs; never on paving or roofs."""
    for x in range(1, HALF):
        for z in range(1, SZ - 1):
            y = c.top(x, z)
            if y < 0 or y > G + 4 or c.get(x, y, z) not in ("sand", "sandstone"):
                continue
            if inside(VILLAGE, x, z) or inside(YARD, x, z) or rect_dist(x, z, BASE) < 1:
                continue
            clump = vnoise(x, z, 7, 96)
            r = h3(x, 3, z, 95)
            if clump > 0.72 and r < 0.35:
                c.set(x, y + 1, z, "short_dry_grass" if r < 0.25 else "tall_dry_grass")
            elif r < 0.006:
                c.set(x, y + 1, z, "dead_bush")


def rock(c: Canvas, x, z, r, height, seed):
    """Mesa outcrop: stacked tiers, each a little smaller, banded like the cliffs, sand on top."""
    base = c.top_solid(x, z)
    tiers = max(2, height // 3)
    for t in range(tiers):
        rr = r * (1 - 0.22 * t)
        y0 = base - 1 + t * (height // tiers)
        y1 = base + (t + 1) * (height // tiers)
        for dx in range(-int(r) - 2, int(r) + 3):
            for dz in range(-int(r) - 2, int(r) + 3):
                d = math.hypot(dx, dz * 1.2) + (vnoise(x + dx, z + dz, 4, seed + t) - 0.5) * 0.9
                if d <= rr:
                    for y in range(y0, y1 + 1):
                        c.set(x + dx, y, z + dz, strata)
    for dx in range(-int(r) - 2, int(r) + 3):
        for dz in range(-int(r) - 2, int(r) + 3):
            y = c.top_solid(x + dx, z + dz)
            if y > base + 1:
                c.set(x + dx, y, z + dz, "sand")


def field(c: Canvas):
    """Open sand between the compound and the lanes: tank traps, rock outcrops, palms."""
    for (x, z) in ((33, 26), (36, 70), (34, 49)):
        hedgehog(c, x, z)
    for (x, z, r, hh) in ((29, 17, 3.0, 7), (14, 12, 4.0, 9), (19, 88, 4.0, 8), (69, 92, 3.0, 6), (70, 4, 2.6, 6)):
        rock(c, x, z, r, hh, seed=x * 31 + z)
    for (x, z) in ((3, 22), (2, 79), (28, 93), (27, 5)):
        P.palm(c, x, c.top_solid(x, z) + 1, z, height=6 + x % 3, seed=x)


def border(c: Canvas):
    for x in range(SX):
        for z in range(SZ):
            if x in (0, SX - 1) or z in (0, SZ - 1):
                h = c.top_solid(x, z)
                for y in range(max(h + 1, 1), SY):
                    c.set(x, y, z, "barrier")


# The owner's in-game edits of 2026-11-22, ported into the west half so the rotation repeats them
# for team 1: a supply cache dug into the south bluff off the wadi footbridge (entrance tunnel,
# chests, barrels, a short rail track, a sandstone roof so the sand above stays put), sugar cane
# at the oasis and an opened doorway in the watchtower's braces. (The roof slab and floor tile the
# owner also changed belonged to buildings the fourth pass replaced.)
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
    c.set(9, 10, 32, "air")              # doorway through the watchtower's braces


def build():
    c = CANVAS
    c.b.clear()
    terrain(c)
    base(c)
    container_yard(c)
    village(c)
    hall(c)
    square(c)
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
