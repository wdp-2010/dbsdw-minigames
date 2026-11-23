"""Moerasdorp: a fishing village on stilts in a marsh. Each team holds a raised timber platform
(spawn deck, longhouse, watchtower, flag) over its own island; boardwalks on piles run through
the marsh to stilt houses and to a covered fish hall that stands over the channel in the middle.

Built on the west half (team 0) and turned 180 degrees for team 1. Three lanes:
north = open reed marsh with a fishing hut on tall stilts (long range), centre = boardwalks past
two stilt houses into the fish hall (mid range), south = mangrove forest with a collapsed stilt
house (close range). The channel is crossed by the hall and by a footbridge in each outer lane.

The piles are the point of the map (owner, 2026-11-23: "mooie palen"). Every pile is a log that
runs from below the mud up through the deck, dark where it stands in water, on a stone pad where
it stands on land, with roots at its foot, a girder on top and knee braces (upside-down stairs)
under the girder. Where a pile comes through the deck it becomes a railing post. A fence is
never left as a bare pole: fences only run between posts.
"""
from __future__ import annotations

import math

import parts as P
from mcbuild import Canvas, Grad, Pal, fbm, h3, parse, fmt
from woudfront import poly_dist, rect_dist

WORLD = "world"
SX, SY, SZ = 150, 40, 100
G = 8                      # water surface (top water block) in local y; low land is G + 1
ORIGIN = (300, -63, 400)   # south of Duinbasis (z 100..199) and Schaakwoud (z 300..347)
HALF = SX // 2
SEED = 63

CANVAS = Canvas(SX, SY, SZ, SEED)

ISLAND = (1, 28, 30, 71)        # the base stands on this island
DECK = (4, 35, 24, 64)          # base platform
DD = G + 5                      # base deck (block) height
HQ = (6, 44, 12, 55)            # longhouse on the deck
HALL = (64, 41, 85, 58)         # fish hall over the channel (the east part is the rotation)
HD = G + 2                      # hall floor
HOUSES = [  # walls x1, z1, x2, z2, deck, door side
    (38, 31, 45, 37, G + 6, "south"),
    (39, 63, 46, 69, G + 6, "north"),
]
FISH = (44, 6, 49, 10, G + 7, "south")     # fishing hut in the north marsh
RUIN = (44, 74, 50, 79, G + 5)              # collapsed stilt house in the mangroves
BRIDGES = ((13, 15), (84, 86))              # z ranges of the outer footbridges (x 63..74)
JETTIES = ((67, 34, 70, 40), (67, 59, 70, 65))
PATHS = [
    [(28, 41), (33, 45.5), (42, 47), (52, 46.5), (63, 45)],          # centre north
    [(28, 58), (33, 55), (42, 53), (52, 53.5), (63, 54)],            # centre south
    [(13, 30), (16, 25), (26, 21), (37, 21), (47, 21.5), (57, 17), (64, 14), (74, 14)],
    [(13, 69), (16, 75), (25, 81), (36, 85.5), (50, 85.5), (64, 85), (74, 85)],
    [(52, 46), (53, 37), (55, 27), (57, 17)],                        # links between lanes
    [(52, 53.5), (53, 63), (55, 73), (57, 85)],
]

SPAWNS = {0: [(7.5, DD + 1, 38.5), (7.5, DD + 1, 61.5), (14.5, DD + 1, 39.5),
              (14.5, DD + 1, 60.5), (15.5, DD + 1, 46.5), (15.5, DD + 1, 53.5)]}
SPAWNS[1] = [(SX - p[0], p[1], SZ - p[2]) for p in SPAWNS[0]]
FLAGS = {0: (20.5, DD + 2, 49.5)}
FLAGS[1] = (SX - FLAGS[0][0], FLAGS[0][1], SZ - FLAGS[0][2])
YAW = {0: -90.0, 1: 90.0}
POWERUPS = [(47.5, G + 2, 21.5), (60.5, HD + 1, 47.5), (102.5, G + 2, 78.5), (89.5, HD + 1, 52.5)]
CLOSEUPS = {"base": (0, 26, 34, 74), "hall": (56, 30, 94, 70), "houses": (28, 28, 60, 72),
            "north": (34, 0, 80, 26), "mangrove": (30, 66, 76, 99)}

# --------------------------------------------------------------------------- materials

PILE = "spruce_log[axis=y]"
PILE_WET = "dark_oak_log[axis=y]"           # the wet, dark foot of a pile
PAD = Pal(("mossy_cobblestone", 3), ("cobblestone", 2), ("mossy_stone_bricks", 1), seed=2)
PLANKS = Pal(("mangrove_planks", 6), ("spruce_planks", 3), ("dark_oak_planks", 1), seed=3, scale=2)
ROOF_WOOD = Pal(("dark_oak", 5), ("spruce", 3), ("mangrove", 1), seed=4)
DAUB = Grad([Pal(("mud_bricks", 3), ("packed_mud", 2), ("mud", 1), seed=5),
             Pal(("packed_mud", 4), ("mud_bricks", 2), seed=6),
             Pal(("packed_mud", 3), ("mud_bricks", 1), ("stripped_birch_wood[axis=y]", 1), seed=7)],
            G + 6, G + 9, seed=8)
GABLE = Pal(("spruce_planks", 3), ("stripped_spruce_log[axis=y]", 1), seed=9)
WALL_LOW = Pal(("mud_bricks", 3), ("packed_mud", 2), seed=10)
FENCE = "spruce_fence"

GRASS = "grass_block[snowy=false]"
LAND = Pal((GRASS, 6), ("moss_block", 2), ("podzol[snowy=false]", 2), ("coarse_dirt", 1), seed=11, scale=3)
SHORE = Pal(("mud", 4), ("muddy_mangrove_roots", 2), ("coarse_dirt", 1), (GRASS, 2), seed=12, scale=2)
MUDFLAT = Pal(("mud", 3), ("muddy_mangrove_roots", 2), ("moss_block", 2), ("podzol[snowy=false]", 1), seed=13, scale=2)
BED = Pal(("mud", 5), ("clay", 1), ("gravel", 1), ("dirt", 1), seed=14, scale=2)

FLOOR: dict[tuple[int, int], int] = {}      # top ground block of each column (under water: the bed)

WATERLOG = ("stairs", "slab", "fence", "roots", "trapdoor", "ladder", "lantern", "propagule")


def put(c: Canvas, x, y, z, state):
    """Set a block; parts that can hold water are waterlogged where they replace water."""
    if c.get(x, y, z) == "water" and isinstance(state, str) and any(k in state for k in WATERLOG):
        name, props = parse(state)
        props["waterlogged"] = "true"
        state = fmt(name, props)
    c.set(x, y, z, state)


# --------------------------------------------------------------------------- layout helpers


def creek_x(z):
    return HALF - 0.5 + 3.0 * math.sin((z - (SZ - 1) / 2) / 9.0)


def platform_rect(h):
    """Walls of a house -> its platform: one block of ledge all round, two more of porch."""
    x1, z1, x2, z2, _, door = h
    px1, pz1, px2, pz2 = x1 - 1, z1 - 1, x2 + 1, z2 + 1
    if door == "south":
        pz2 += 2
    elif door == "north":
        pz1 -= 2
    elif door == "east":
        px2 += 2
    else:
        px1 -= 2
    return px1, pz1, px2, pz2


def stilt_rects():
    out = [platform_rect(h) for h in HOUSES] + [platform_rect(FISH)]
    x1, z1, x2, z2, _ = RUIN
    out.append((x1 - 1, z1 - 1, x2 + 1, z2 + 1))
    return out


def spaced(a, b, step):
    n = max(1, round((b - a) / step))
    return sorted({a + round(i * (b - a) / n) for i in range(n + 1)})


# --------------------------------------------------------------------------- terrain


def column_kind(x, z):
    """(floor y, is land). Water stands from floor + 1 up to G."""
    edge = min(x, z, SZ - 1 - z)
    rise = max(0, 7 - edge)
    if rise:
        return G + 1 + round(rise * 0.6 + (fbm(x, z, 9, SEED + 1) - 0.5) * 2), True
    cd = abs(x - creek_x(z))
    if cd < 3.5:
        return G - 3, False
    if cd < 5.5:
        return G - 2, False
    if rect_dist(x, z, ISLAND) < 0.5:
        return G + 1, True
    if any(rect_dist(x, z, r) < 1.5 for r in stilt_rects()) or rect_dist(x, z, HALL) < 2:
        return G - 1, False
    n = fbm(x, z, 11, SEED)
    if z > 70:
        n += 0.1                    # muddy flats under the mangroves
    elif z < 28:
        n -= 0.04                   # more open water in the reed marsh
    if rect_dist(x, z, ISLAND) < 4:
        n += 0.2
    if n > 0.53:
        return G + 1 + (1 if n > 0.7 else 0), True
    return (G - 1 if n > 0.34 else G - 2), False


def terrain(c: Canvas):
    FLOOR.clear()
    kinds = {}
    for x in range(HALF):
        for z in range(SZ):
            kinds[(x, z)] = column_kind(x, z)
    for (x, z), (f, land) in kinds.items():
        FLOOR[(x, z)] = f
        c.column(x, z, 0, f - 4, "stone")
        c.column(x, z, f - 3, f - 1, "dirt")
        if not land:
            c.set(x, f, z, BED)
            for y in range(f + 1, G + 1):
                c.set(x, y, z, "water")
            continue
        wet = any(not kinds.get((x + dx, z + dz), (0, True))[1] for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if wet:
            top = SHORE
        elif z > 70 or min(x, z, SZ - 1 - z) < 7:
            top = MUDFLAT
        else:
            top = LAND
        c.set(x, f, z, top)
        if f > G + 1:
            c.column(x, z, G + 1, f - 1, "dirt")


# --------------------------------------------------------------------------- piles and decks


def pile(c: Canvas, x, z, top):
    """One pile from below the mud (or a stone pad on land) up to `top`."""
    f = FLOOR.get((x, z), G - 1)
    if f >= G + 1:
        c.set(x, f + 1, z, PAD)
        start = f + 2
    else:
        start = f - 1
        k = 0
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if h3(x + dx, 0, z + dz, 15) < 0.4 and k < 2 and c.get(x + dx, f + 1, z + dz) == "water":
                put(c, x + dx, f + 1, z + dz, "mangrove_roots")
                k += 1
    for y in range(start, top + 1):
        c.set(x, y, z, PILE_WET if y <= G + 1 else PILE)


def knee(c: Canvas, x, y, z, toward):
    """Knee brace: an upside-down stair whose thick side leans on the pile."""
    if y <= G or not c.empty(x, y, z):
        return
    put(c, x, y, z, f"spruce_stairs[facing={toward},half=top,shape=straight]")


def platform(c: Canvas, x1, z1, x2, z2, D, step=4, interior=False, rail=True, gaps=(),
             wale=False, post_up=1):
    """A deck at height D on piles: a girder ring (and rows along x when `interior`) at D - 1,
    knee braces under the girders, a log rim, and the piles coming through as railing posts."""
    xs, zs = spaced(x1, x2, step), spaced(z1, z2, step)
    piles = {(x, z) for x in xs for z in (z1, z2)} | {(x, z) for x in (x1, x2) for z in zs}
    rows = set(zs) if interior else {z1, z2}
    if interior:
        piles |= {(x, z) for x in xs for z in zs}
    gaps = set(gaps)
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            edge_z = z in (z1, z2)
            edge_x = x in (x1, x2)
            if (x, z) in piles:
                continue
            if edge_z:
                c.set(x, D, z, "spruce_log[axis=x]")
            elif edge_x:
                c.set(x, D, z, "spruce_log[axis=z]")
            else:
                c.set(x, D, z, PLANKS)
            for y in range(D + 1, D + 4):
                if c.get(x, y, z) != "air" and not (edge_x or edge_z):
                    c.set(x, y, z, "air")
    # girders
    for z in rows:
        for x in range(x1, x2 + 1):
            if (x, z) not in piles:
                put(c, x, D - 1, z, "spruce_log[axis=x]")
    for x in (x1, x2):
        for z in range(z1, z2 + 1):
            if (x, z) not in piles:
                put(c, x, D - 1, z, "spruce_log[axis=z]")
    # piles, through the deck
    for (x, z) in sorted(piles):
        pile(c, x, z, D)
        on_edge = x in (x1, x2) or z in (z1, z2)
        if rail and on_edge and (x, z) not in gaps:
            c.column(x, z, D + 1, D + post_up, PILE)
    # knee braces under the girders
    for (x, z) in piles:
        if z in rows:
            for dx, f in ((1, "west"), (-1, "east")):
                if x1 <= x + dx <= x2 and (x + dx, z) not in piles:
                    knee(c, x + dx, D - 2, z, f)
        if x in (x1, x2):
            for dz, f in ((1, "north"), (-1, "south")):
                if z1 <= z + dz <= z2 and (x, z + dz) not in piles:
                    knee(c, x, D - 2, z + dz, f)
    # a wale (horizontal tie) halfway down on the long sides of tall platforms
    if wale:
        yw = (G + 1 + D - 2) // 2
        if yw > G:
            if x2 - x1 >= z2 - z1:
                for z in (z1, z2):
                    for x in range(x1, x2 + 1):
                        if (x, z) not in piles and c.get(x, yw, z) in ("air", "water"):
                            put(c, x, yw, z, "stripped_spruce_log[axis=x]")
            else:
                for x in (x1, x2):
                    for z in range(z1, z2 + 1):
                        if (x, z) not in piles and c.get(x, yw, z) in ("air", "water"):
                            put(c, x, yw, z, "stripped_spruce_log[axis=z]")
    # diagonal braces (two stairs climbing from one pile to the next) on the short sides
    if wale and D - G >= 6:
        yb = G + 2
        if x2 - x1 >= z2 - z1:
            for x in (x1, x2):
                for i, (za, zb) in enumerate(zip(zs, zs[1:])):
                    if zb - za != 3:
                        continue
                    up = i % 2 == 0
                    for k, zz in enumerate((za + 1, za + 2)):
                        yy = yb + (k if up else 1 - k)
                        if c.get(x, yy, zz) in ("air", "water"):
                            put(c, x, yy, zz, f"spruce_stairs[facing={'south' if up else 'north'},half=bottom,shape=straight]")
        else:
            for z in (z1, z2):
                for i, (xa, xb) in enumerate(zip(xs, xs[1:])):
                    if xb - xa != 3:
                        continue
                    up = i % 2 == 0
                    for k, xx in enumerate((xa + 1, xa + 2)):
                        yy = yb + (k if up else 1 - k)
                        if c.get(xx, yy, z) in ("air", "water"):
                            put(c, xx, yy, z, f"spruce_stairs[facing={'east' if up else 'west'},half=bottom,shape=straight]")
    # railing between the posts
    if rail:
        for x in range(x1, x2 + 1):
            for z in range(z1, z2 + 1):
                if (x in (x1, x2) or z in (z1, z2)) and (x, z) not in piles and (x, z) not in gaps:
                    end = any((x + dx, z + dz) in gaps for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                    # a railing ends in a post at a stair opening, never in a bare fence
                    c.set(x, D + 1, z, PILE if end else FENCE)
                    if end:
                        c.set(x, D + 2, z, "lantern[hanging=false]")
    return piles


def flight(c: Canvas, cells, d, D, low=G + 1):
    """Stairs down from a deck at D: `cells` are the deck edge cells the flight leaves from,
    `d` the direction (dx, dz) it runs in. Ends on a deck or the ground at `low`."""
    dx, dz = d
    toward = {(1, 0): "west", (-1, 0): "east", (0, 1): "north", (0, -1): "south"}[d]
    away = {"west": "east", "east": "west", "north": "south", "south": "north"}[toward]
    n = D - low
    for (cx, cz) in cells:
        for k in range(1, n + 1):
            x, z = cx + dx * k, cz + dz * k
            y = D - k + 1
            for yy in range(y, y + 3):
                if c.get(x, yy, z) != "water":
                    c.set(x, yy, z, "air")
            c.set(x, y, z, f"spruce_stairs[facing={toward},half=bottom,shape=straight]")
            if y - 1 > low:
                put(c, x, y - 1, z, f"spruce_stairs[facing={away},half=top,shape=straight]")
            elif FLOOR.get((x, z), G + 1) < low:
                c.set(x, low, z, PLANKS)
        # a landing plank and a pile under the foot of the flight if it ends over water
        x, z = cx + dx * (n + 1), cz + dz * (n + 1)
        if FLOOR.get((x, z), G + 1) < low and c.get(x, low, z) in ("air", "water"):
            c.set(x, low, z, PLANKS)
        mx, mz = cx + dx * (n // 2 + 1), cz + dz * (n // 2 + 1)
        if FLOOR.get((mx, mz), G + 1) < low:
            pile(c, mx, mz, D - n // 2 - 2)


# --------------------------------------------------------------------------- buildings


def roof(c: Canvas, x1, z1, x2, z2, y0, ridge, gables=(True, True), seed=0):
    """Gable roof over walls x1..x2, z1..z2 with a one-block overhang. Ridge along `ridge`."""
    if ridge == "x":
        u1, u2, v1, v2 = x1, x2, z1, z2
        lo, hi = "south", "north"

        def at(u, v):
            return u, v
    else:
        u1, u2, v1, v2 = z1, z2, x1, x2
        lo, hi = "east", "west"

        def at(u, v):
            return v, u
    i = 0
    while True:
        y = y0 + i
        va, vb = v1 - 1 + i, v2 + 1 - i
        if va > vb:
            break
        for u in range(u1 - 1, u2 + 2):
            if va == vb:
                x, z = at(u, va)
                c.set(x, y, z, ROOF_WOOD(x, y, z) + "_slab[type=bottom]")
                continue
            x, z = at(u, va)
            c.set(x, y, z, ROOF_WOOD(x, y, z) + f"_stairs[facing={lo},half=bottom,shape=straight]")
            x, z = at(u, vb)
            c.set(x, y, z, ROOF_WOOD(x, y, z) + f"_stairs[facing={hi},half=bottom,shape=straight]")
        for u, on in ((u1, gables[0]), (u2, gables[1])):
            if not on:
                continue
            for v in range(max(va + 1, v1), min(vb - 1, v2) + 1):
                x, z = at(u, v)
                c.set(x, y, z, GABLE)
        i += 1
    return y0 + i - 1


def walls(c: Canvas, x1, z1, x2, z2, D, door, seed=0, ruined=None):
    """Timber-framed walls with mud daub, top plate at D + 3, windows with shutters, a door."""
    posts = {(x, z) for x in spaced(x1, x2, 3) for z in (z1, z2)} | \
            {(x, z) for x in (x1, x2) for z in spaced(z1, z2, 3)}
    xm, zm = (x1 + x2) // 2, (z1 + z2) // 2
    door_cells = {"south": {(xm, z2), (xm + 1, z2)}, "north": {(xm, z1), (xm + 1, z1)},
                  "east": {(x2, zm), (x2, zm + 1)}, "west": {(x1, zm), (x1, zm + 1)}}[door]
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            edge = x in (x1, x2) or z in (z1, z2)
            if not edge:
                for y in range(D + 1, D + 4):
                    c.set(x, y, z, "air")
                continue
            axis = "x" if z in (z1, z2) else "z"
            if (x, z) in posts:
                c.column(x, z, D + 1, D + 3, "stripped_spruce_log[axis=y]")
                continue
            c.set(x, D + 3, z, f"spruce_log[axis={axis}]")
            if (x, z) in door_cells:
                c.set(x, D + 1, z, "air")
                c.set(x, D + 2, z, "air")
                continue
            c.set(x, D + 1, z, DAUB)
            c.set(x, D + 2, z, DAUB)
    # windows: the middle of each wall panel between two posts, with shutters outside
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            edge = x in (x1, x2) or z in (z1, z2)
            corner = x in (x1, x2) and z in (z1, z2)
            if not edge or corner or (x, z) in posts or (x, z) in door_cells:
                continue
            if z in (z1, z2):
                side = "north" if z == z1 else "south"
                nb = [(x - 1, z), (x + 1, z)]
                out = (0, -1) if z == z1 else (0, 1)
            else:
                side = "west" if x == x1 else "east"
                nb = [(x, z - 1), (x, z + 1)]
                out = (-1, 0) if x == x1 else (1, 0)
            # a window needs posts or wall on both sides (single-block panel or the middle of three)
            if not all(n in posts for n in nb) and h3(x, D, z, seed) > 0.25:
                continue
            if any(n in door_cells for n in nb):
                continue
            c.set(x, D + 2, z, "air")
            ox, oz = x + out[0], z + out[1]
            c.set(ox, D + 1, oz, f"spruce_trapdoor[facing={side},half=top,open=false]")
            for (nx, nz) in nb:
                sx_, sz_ = nx + out[0], nz + out[1]
                if c.empty(sx_, D + 2, sz_):
                    c.set(sx_, D + 2, sz_, f"spruce_trapdoor[facing={side},half=bottom,open=true]")
    return door_cells


def house(c: Canvas, h, seed=0):
    x1, z1, x2, z2, D, door = h
    px1, pz1, px2, pz2 = platform_rect(h)
    xm, zm = (x1 + x2) // 2, (z1 + z2) // 2
    d = {"south": (0, 1), "north": (0, -1), "east": (1, 0), "west": (-1, 0)}[door]
    if door in ("south", "north"):
        ez = pz2 if door == "south" else pz1
        fl = [(xm, ez), (xm + 1, ez)]
    else:
        ex = px2 if door == "east" else px1
        fl = [(ex, zm), (ex, zm + 1)]
    platform(c, px1, pz1, px2, pz2, D, step=3, gaps=fl, wale=True)
    walls(c, x1, z1, x2, z2, D, door, seed=seed)
    ridge = "x" if x2 - x1 >= z2 - z1 else "z"
    roof(c, x1, z1, x2, z2, D + 4, ridge, seed=seed)
    flight(c, fl, d, D)
    # inside: a stove corner, nets and barrels
    c.set(x1 + 1, D + 1, z1 + 1, "barrel[facing=up,open=false]")
    c.set(x2 - 1, D + 1, z1 + 1, "composter[level=0]")
    c.set(x1 + 1, D + 1, z2 - 1, "crafting_table")
    c.set(x2 - 1, D + 1, z2 - 1, "barrel[facing=north,open=false]")
    # lantern under the eave over the door, hung from the overhang
    lx, lz = fl[0][0] - d[0] * 2, fl[0][1] - d[1] * 2
    if door in ("south", "north"):
        lx = xm - 1
    else:
        lz = zm - 1
    c.set(lx + d[0], D + 3, lz + d[1], "lantern[hanging=true]")
    # a lantern on two railing posts
    for (px, pz) in ((px1, pz1), (px2, pz2)):
        if c.get(px, D + 1, pz).startswith("spruce_log"):
            c.set(px, D + 2, pz, "lantern[hanging=false]")


def base(c: Canvas):
    x1, z1, x2, z2 = DECK
    D = DD
    north = [(12, z1), (13, z1)]
    south = [(12, z2), (13, z2)]
    east = [(x2, z) for z in (40, 41, 42, 57, 58, 59)]
    piles = platform(c, x1, z1, x2, z2, D, step=4, interior=True, gaps=north + south + east)
    # the front (east) edge is a breastwork instead of a railing: mud brick + slab, 1.5 high
    for z in range(z1 + 1, z2):
        if (x2, z) in piles:
            c.column(x2, z, D + 1, D + 2, PILE)
            continue
        if (x2, z) in east:
            continue
        c.set(x2, D + 1, z, WALL_LOW)
        c.set(x2, D + 2, z, "mud_brick_slab[type=bottom]")
    for (px, pz) in piles:
        if px == x2 and pz not in (z1, z2) and not any(a <= pz <= b for a, b in ((40, 42), (57, 59))):
            c.set(px + 1, D + 2, pz, "team_wall_banner[facing=east]")
    flight(c, [(x2, z) for z in (40, 41, 42)], (1, 0), D)
    flight(c, [(x2, z) for z in (57, 58, 59)], (1, 0), D)
    flight(c, north, (0, -1), D)
    flight(c, south, (0, 1), D)
    # longhouse
    hx1, hz1, hx2, hz2 = HQ
    walls(c, hx1, hz1, hx2, hz2, D, "east", seed=21)
    roof(c, hx1, hz1, hx2, hz2, D + 4, "z", seed=22)
    c.set(hx1 + 1, D + 1, hz1 + 1, "barrel[facing=up,open=false]")
    c.set(hx1 + 1, D + 1, hz1 + 2, "barrel[facing=up,open=false]")
    c.set(hx1 + 1, D + 1, hz2 - 1, "crafting_table")
    c.set(hx1 + 1, D + 1, hz2 - 2, "smoker[facing=east,lit=false]")
    # flag, tower, cover on the deck
    fx, fy, fz = int(FLAGS[0][0]), int(FLAGS[0][1]), int(FLAGS[0][2])
    P.flag_podium(c, fx, fy, fz, stone="polished_andesite", trim="stone_brick_stairs")
    P.watchtower(c, 19, D + 1, 36, height=6, wood="spruce", ladder_side="west")
    P.crate_stack(c, 17, D + 1, 43, w=2, d=1, h=2, seed=23)
    P.crate_stack(c, 17, D + 1, 56, w=2, d=1, h=2, seed=24)
    P.crate_stack(c, 21, D + 1, 44, w=1, d=1, h=1, seed=25)
    P.crate_stack(c, 21, D + 1, 55, w=1, d=1, h=1, seed=26)
    for z in (40, 59):
        c.set(10, D + 1, z, "barrel[facing=up,open=false]")
    # lanterns on the corner posts and hanging under the deck
    for (px, pz) in ((x1, z1), (x1, z2), (x2, z2)):
        c.set(px, D + 2, pz, "lantern[hanging=false]")
    for (px, pz) in piles:
        if (px + pz) % 8 == 3 and x1 < px < x2 and c.empty(px + 2, D - 2, pz) and not c.empty(px + 2, D - 1, pz):
            c.set(px + 2, D - 2, pz, "lantern[hanging=true]")
    # under the deck: the boat shed
    upturned_boat(c, 9, 41, "x")
    upturned_boat(c, 14, 56, "x")
    P.crate_stack(c, 6, G + 2, 50, w=1, d=2, h=1, seed=27)
    c.set(18, G + 2, 49, "hay_block[axis=z]")
    c.set(18, G + 2, 50, "hay_block[axis=z]")


def hall(c: Canvas):
    """Fish hall over the channel: posts every three blocks, half walls, an open floor with
    counters, a tall gable roof with a lantern turret on the ridge (the map's landmark)."""
    x1, z1, x2, z2 = HALL
    xe = HALF - 1
    D = HD
    xs = [64, 67, 70, 73]
    zs_w = [41, 43, 47, 52, 56, 58]
    posts = {(x, z) for x in xs for z in (z1, z2)} | {(x1, z) for z in zs_w}
    inner = {(x, z) for x in (67, 70, 73) for z in (46, 53)}
    for x in range(x1, xe + 1):
        for z in range(z1, z2 + 1):
            for y in range(D, D + 7):
                c.set(x, y, z, "air")
            if z in (z1, z2):
                c.set(x, D, z, "spruce_log[axis=x]")
            elif x == x1:
                c.set(x, D, z, "spruce_log[axis=z]")
            else:
                c.set(x, D, z, PLANKS)
    for z in (z1, 46, 53, z2):
        for x in range(x1, xe + 1):
            put(c, x, D - 1, z, "spruce_log[axis=x]")
    for z in range(z1, z2 + 1):
        put(c, x1, D - 1, z, "spruce_log[axis=z]")
    for (x, z) in posts | inner:
        pile(c, x, z, D)
    for (x, z) in posts:
        c.column(x, z, D + 1, D + 5, PILE)
    # top plate and tie beams
    for x in range(x1, xe + 1):
        for z in (z1, z2):
            if (x, z) not in posts:
                c.set(x, D + 5, z, "spruce_log[axis=x]")
            c.set(x, D + 6, z, "spruce_log[axis=x]")
    for z in range(z1, z2 + 1):
        if (x1, z) not in posts:
            c.set(x1, D + 5, z, "spruce_log[axis=z]")
        c.set(x1, D + 6, z, "spruce_log[axis=z]")
    for x in (67, 70, 73):
        for z in range(z1 + 1, z2):
            c.set(x, D + 6, z, "stripped_spruce_log[axis=z]")
        for z in (45, 54):
            c.set(x, D + 5, z, "lantern[hanging=true]")
    # knee braces under the top plate, inside the posts
    for (x, z) in posts:
        for dx, f in ((1, "west"), (-1, "east")):
            if x1 <= x + dx <= xe and (x + dx, z) not in posts and z in (z1, z2):
                c.set(x + dx, D + 4, z, f"spruce_stairs[facing={f},half=top,shape=straight]")
        if x == x1:
            for dz, f in ((1, "north"), (-1, "south")):
                if z1 <= z + dz <= z2 and (x, z + dz) not in posts:
                    c.set(x, D + 4, z + dz, f"spruce_stairs[facing={f},half=top,shape=straight]")
    # half walls, with the entrances left open
    ent = {(x1, z) for z in (44, 45, 46, 53, 54, 55)} | {(x, z) for x in (68, 69) for z in (z1, z2)}
    for x in range(x1, xe + 1):
        for z in range(z1, z2 + 1):
            if not (x == x1 or z in (z1, z2)) or (x, z) in posts or (x, z) in ent:
                continue
            c.set(x, D + 1, z, WALL_LOW)
            c.set(x, D + 2, z, "mud_brick_slab[type=bottom]")
    for z in (44, 45, 46, 53, 54, 55):
        c.set(x1 - 1, D, z, "spruce_stairs[facing=east,half=bottom,shape=straight]")
    for x in (68, 69):
        c.set(x, D, z1 - 1, "spruce_stairs[facing=south,half=bottom,shape=straight]")
        c.set(x, D, z2 + 1, "spruce_stairs[facing=north,half=bottom,shape=straight]")
    # roof: ridge along x, gable on the west end only (the east end is the rotation)
    top = roof(c, x1, z1, xe - 1, z2, D + 7, "x", gables=(True, False), seed=31)
    for z in range(z1, z2 + 1):
        if c.get(x1, D + 7, z) == "air" and z1 < z < z2:
            c.set(x1, D + 7, z, GABLE)
    # a round window in the gable
    for (wy, wz) in ((D + 11, 49), (D + 11, 50), (D + 12, 49), (D + 12, 50)):
        c.set(x1, wy, wz, "air")
    c.set(x1 - 1, D + 10, 49, "spruce_trapdoor[facing=west,half=top,open=false]")
    c.set(x1 - 1, D + 10, 50, "spruce_trapdoor[facing=west,half=top,open=false]")
    # lantern turret on the ridge, 4 x 4 around the centre (the west two columns)
    ty = top + 1
    for (x, z) in ((73, 48), (73, 51)):
        c.column(x, z, ty - 1, ty + 3, "stripped_spruce_log[axis=y]")
    for z in range(48, 52):
        c.set(74, ty - 1, z, "spruce_planks")
        c.set(73, ty - 1, z, "spruce_planks" if z in (49, 50) else c.get(73, ty - 1, z))
    for z in (49, 50):
        c.set(73, ty, z, FENCE)
    c.set(74, ty + 2, 49, "lantern[hanging=true]")
    for x in range(72, 75):
        for z in range(47, 53):
            edge = x == 72 or z in (47, 52)
            c.set(x, ty + 4, z, "dark_oak_stairs[facing=" + ("east" if x == 72 else ("south" if z == 47 else "north"))
                  + ",half=bottom,shape=straight]" if edge else "dark_oak_planks")
    for z in range(48, 52):
        c.set(73, ty + 5, z, "dark_oak_stairs[facing=" + ("east" if z in (49, 50) else ("south" if z == 48 else "north"))
              + ",half=bottom,shape=straight]")
        c.set(74, ty + 5, z, "dark_oak_planks")
    for z in (49, 50):
        c.set(74, ty + 6, z, "dark_oak_slab[type=bottom]")
    c.set(74, ty + 7, 49, "waxed_lightning_rod[facing=up]")
    # inside: counters of barrels and crates, ice for the fish
    for x in range(66, 73):
        for z in (47, 52):
            if x in (69,):
                continue
            c.set(x, D + 1, z, "barrel[facing=up,open=false]" if (x + z) % 3 else "spruce_planks")
            if (x + z) % 4 == 0:
                c.set(x, D + 2, z, "packed_ice")
    P.crate_stack(c, 73, D + 1, 49, w=2, d=2, h=2, seed=32)
    P.crate_stack(c, 65, D + 1, 42, w=2, d=1, h=1, seed=33)
    P.crate_stack(c, 65, D + 1, 57, w=1, d=1, h=2, seed=34)
    # jetties at the north and south doors, a boat at each
    for (jx1, jz1, jx2, jz2) in JETTIES:
        platform(c, jx1, jz1, jx2, jz2, G + 1, step=3, rail=False)
        for (px, pz) in ((jx1, jz1 if jz1 < z1 else jz2), (jx2, jz1 if jz1 < z1 else jz2)):
            c.set(px, G + 2, pz, PILE)
    moored_boat(c, 65, 36, "z")
    moored_boat(c, 72, 62, "z")


def footbridges(c: Canvas):
    for (z1, z2) in BRIDGES:
        x1, x2 = 63, HALF - 1
        for x in range(x1, x2 + 1):
            for z in range(z1, z2 + 1):
                for y in range(G + 1, G + 4):
                    c.set(x, y, z, "air")
                c.set(x, G + 1, z, PLANKS)
            for z in (z1 - 1, z2 + 1):
                c.set(x, G + 1, z, "spruce_log[axis=x]")
                post = x % 3 == 0
                if post:
                    pile(c, x, z, G + 2)
                    if x % 6 == 0:
                        c.set(x, G + 3, z, "lantern[hanging=false]")
                else:
                    put(c, x, G, z, "spruce_log[axis=x]")
                    c.set(x, G + 2, z, FENCE)
            if x % 3 == 0:
                pile(c, x, (z1 + z2) // 2, G)


def boardwalks(c: Canvas):
    for pts in PATHS:
        xs = [p[0] for p in pts]
        zs = [p[1] for p in pts]
        cells = set()
        for x in range(max(0, int(min(xs)) - 2), min(HALF, int(max(xs)) + 3)):
            for z in range(max(0, int(min(zs)) - 2), min(SZ, int(max(zs)) + 3)):
                if poly_dist(x, z, pts) < 1.5:
                    cells.add((x, z))
        for (x, z) in cells:
            for y in range(G + 2, G + 5):
                c.set(x, y, z, "air")
            c.set(x, G + 1, z, PLANKS)
        for (x, z) in cells:
            if FLOOR.get((x, z), G + 1) >= G + 1:
                continue
            missing = [(dx, dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)) if (x + dx, z + dz) not in cells]
            if not missing:
                if (x * 3 + z * 5) % 11 == 0:
                    pile(c, x, z, G)
                continue
            axis = "z" if missing[0][1] == 0 else "x"
            if (x + z) % 4 == 0:
                if (x * 7 + z * 3) % 3 == 0:
                    pile(c, x, z, G + 2)          # a bollard: the pile sticks up through the deck
                    if h3(x, 1, z, 16) < 0.2:
                        c.set(x, G + 3, z, "lantern[hanging=false]")
                else:
                    pile(c, x, z, G)
                    c.set(x, G + 1, z, PLANKS)
            else:
                put(c, x, G, z, f"spruce_log[axis={axis}]")


def ruin(c: Canvas):
    """A stilt house whose south-east corner gave way: the piles there snapped, the deck and
    walls step down along a diagonal, planks and a log lie in the mud below."""
    x1, z1, x2, z2, D = RUIN
    h = (x1, z1, x2, z2, D, "west")
    px1, pz1, px2, pz2 = x1 - 1, z1 - 1, x2 + 1, z2 + 1
    platform(c, px1, pz1, px2, pz2, D, step=3, wale=True)
    walls(c, x1, z1, x2, z2, D, "west", seed=41)
    roof(c, x1, z1, x2, z2, D + 4, "x", seed=42)
    for x in range(px1 - 1, px2 + 2):
        for z in range(pz1 - 1, pz2 + 2):
            # 0 at the north-west corner, 1 at the south-east one: the slope of the collapse
            t = ((x - px1) / (px2 - px1) + (z - pz1) / (pz2 - pz1)) / 2
            if t <= 0.45:
                continue
            keep_to = D + 7 - int((t - 0.45) * 22) + int(h3(x, 0, z, 43) * 2)
            for y in range(max(G + 1, keep_to + 1), SY):
                if c.get(x, y, z) != "water":
                    c.set(x, y, z, "air")
    rub = Pal(("mangrove_planks", 3), ("spruce_planks", 2), ("packed_mud", 2), ("mud_bricks", 1), seed=44)
    for (rx, rz) in ((x2 + 2, z2 + 2), (x2, z2 + 3), (x2 + 3, z2)):
        f = FLOOR.get((rx, rz), G - 1)
        c.blob(rx, max(f, G - 1) + 1, rz, 1.4, 0.7, 1.2, rub, only_empty=False, rough=0.5, seed=rx)
    P.fallen_log(c, x2 - 1, G + 1, z2 + 4, length=5, axis="x", kind="spruce", seed=45)
    for (x, z) in ((x2 + 2, z2 + 2), (x2 + 3, z2 + 2)):
        put(c, x, G + 1, z, "spruce_slab[type=bottom]")


# --------------------------------------------------------------------------- props


def upturned_boat(c: Canvas, x, z, axis="x", length=5):
    """A rowing boat turned keel-up on the ground: 2 high in the middle, rounded with stairs."""
    for i in range(length):
        end = i in (0, length - 1)
        for v in (-1, 0, 1):
            px, pz = (x + i, z + v) if axis == "x" else (x + v, z + i)
            y = c.top_solid(px, pz) + 1
            if end and v != 0:
                continue
            if end:
                f = ("west" if i == length - 1 else "east") if axis == "x" else ("north" if i == length - 1 else "south")
                c.set(px, y, pz, f"spruce_stairs[facing={f},half=bottom,shape=straight]")
                continue
            if v == 0:
                c.set(px, y, pz, "spruce_planks")
                c.set(px, y + 1, pz, "dark_oak_planks")
            else:
                f = ("south" if v < 0 else "north") if axis == "x" else ("east" if v < 0 else "west")
                c.set(px, y, pz, "spruce_planks")
                c.set(px, y + 1, pz, f"spruce_stairs[facing={f},half=bottom,shape=straight]")


def moored_boat(c: Canvas, x, z, axis="z", length=5):
    """A boat lying in the water: plank hull at the water line, slab gunwales, a dry floor."""
    for i in range(length):
        end = i in (0, length - 1)
        for v in (-1, 0, 1):
            px, pz = (x + v, z + i) if axis == "z" else (x + i, z + v)
            if end and v != 0:
                continue
            if end or v != 0:
                c.set(px, G, pz, "spruce_planks")
                put(c, px, G - 1, pz, "spruce_slab[type=top]")
                c.set(px, G + 1, pz, "spruce_slab[type=bottom]" if not end else "spruce_trapdoor[facing=north,half=bottom,open=false]")
            else:
                c.set(px, G, pz, "spruce_slab[type=bottom]")
                put(c, px, G - 1, pz, "spruce_planks")
    px, pz = (x, z + 2) if axis == "z" else (x + 2, z)
    c.set(px, G + 1, pz, "barrel[facing=up,open=false]")


def peat_stack(c: Canvas, x, z, w=2, d=1):
    """Cut peat stacked to dry: 1.5 high (shoot over standing, hidden crouching)."""
    for i in range(w):
        for k in range(d):
            px, pz = x + i, z + k
            y = c.top_solid(px, pz) + 1
            c.set(px, y, pz, Pal(("packed_mud", 3), ("mud_bricks", 1), ("mud", 1), seed=51))
            c.set(px, y + 1, pz, "mud_brick_slab[type=bottom]")


def hanging_moss(c: Canvas, x, y, z, n):
    for k in range(n):
        if not c.empty(x, y - k, z):
            return
        c.set(x, y - k, z, "pale_hanging_moss[tip=" + ("true" if k == n - 1 else "false") + "]")


def willow(c: Canvas, x, z, height=6, r=3.3, seed=0):
    """Swamp oak: short trunk, a wide flat canopy whose edge droops in curtains of leaves, and
    grey moss hanging from it."""
    y = FLOOR.get((x, z), c.top_solid(x, z)) + 1
    if c.top_solid(x, z) >= y:
        return
    for i in range(height):
        c.set(x, y + i, z, "oak_log[axis=y]")
    top = y + height
    leaf = Pal((P.leaves("oak"), 5), (P.leaves("dark_oak"), 2), (P.leaves("azalea"), 1), seed=seed)
    c.blob(x, top - 0.5, z, r, r * 0.45, r, leaf, rough=0.3, seed=seed)
    for dx, dz, a in ((1, 0, "x"), (-1, 0, "x"), (0, 1, "z"), (0, -1, "z")):
        if h3(x + dx, 0, z + dz, seed) < 0.6:
            c.set(x + dx, top - 1, z + dz, f"oak_log[axis={a}]")
            c.set(x + 2 * dx, top - 1, z + 2 * dz, f"oak_log[axis={a}]")
    ri = int(r) + 1
    for px in range(x - ri, x + ri + 1):
        for pz in range(z - ri, z + ri + 1):
            dist = math.hypot(px - x, pz - z)
            if dist < r * 0.55 or dist > r + 0.5:
                continue
            ly = None
            for yy in range(top - 3, top + 3):
                if "leaves" in c.get(px, yy, pz):
                    ly = yy
                    break
            if ly is None:
                continue
            drop = int(h3(px, 1, pz, seed) * 3)
            for k in range(1, drop + 1):
                if c.empty(px, ly - k, pz):
                    c.set(px, ly - k, pz, leaf)
            if h3(px, 2, pz, seed) < 0.35:
                hanging_moss(c, px, ly - drop - 1, pz, 1 + int(h3(px, 3, pz, seed) * 3))


def mangrove(c: Canvas, x, z, height=6, r=2.8, seed=0):
    """Mangrove: a trunk raised on arching prop roots that reach down into the mud or the water,
    a flat canopy, propagules hanging under it."""
    f = FLOOR.get((x, z), c.top_solid(x, z))
    if c.top_solid(x, z) > max(f, G):
        return
    base = max(f, G) + 2
    for y in range(f + 1, base):
        put(c, x, y, z, "mangrove_roots")
    for i in range(height):
        c.set(x, base + i, z, "mangrove_log[axis=y]")
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1)):
        if h3(x + dx, 5, z + dz, seed) < 0.25:
            continue
        for k in (1, 2):
            rx, rz = x + dx * k, z + dz * k
            ry = base + 1 - k
            fr = FLOOR.get((rx, rz), c.top_solid(rx, rz))
            if k == 1:
                put(c, rx, ry, rz, "mangrove_roots")
            else:
                for yy in range(fr + 1, ry + 1):
                    put(c, rx, yy, rz, "mangrove_roots")
                if fr >= G + 1:
                    c.set(rx, fr, rz, "muddy_mangrove_roots")
    top = base + height
    leaf = Pal((P.leaves("mangrove"), 6), (P.leaves("azalea"), 1), seed=seed)
    c.blob(x, top - 0.5, z, r, r * 0.55, r, leaf, rough=0.35, seed=seed)
    ox = 1 if h3(x, 0, z, seed) > 0.5 else -1
    c.set(x + ox, top - 1, z, "mangrove_log[axis=x]")
    c.blob(x + ox * 2, top - 1, z + 1, r * 0.6, r * 0.45, r * 0.6, leaf, rough=0.4, seed=seed + 3)
    ri = int(r) + 1
    for px in range(x - ri - 1, x + ri + 2):
        for pz in range(z - ri - 1, z + ri + 2):
            if h3(px, 4, pz, seed) > 0.18:
                continue
            for yy in range(top - 3, top + 2):
                if "leaves" in c.get(px, yy, pz) and c.empty(px, yy - 1, pz):
                    if h3(px, 6, pz, seed) < 0.5:
                        c.set(px, yy - 1, pz, "mangrove_propagule[age=4,hanging=true,stage=0]")
                    else:
                        hanging_moss(c, px, yy - 1, pz, 2)
                    break


def blocked(x, z, pad=0):
    if rect_dist(x, z, ISLAND) < 1 + pad or rect_dist(x, z, HALL) < 5 + pad:
        return True
    if any(rect_dist(x, z, r) < 3 + pad for r in stilt_rects()):
        return True
    if any(rect_dist(x, z, j) < 3 + pad for j in JETTIES):
        return True
    if any(poly_dist(x, z, p) < 3 + pad for p in PATHS):
        return True
    if abs(x - creek_x(z)) < 6 + pad and 7 < z < 92:
        return True
    if any(a - 3 <= z <= b + 3 for a, b in BRIDGES) and x > 60:
        return True
    return False


def woods(c: Canvas):
    step = 5
    for gx in range(0, HALF, step):
        for gz in range(0, SZ, step):
            x = gx + int(h3(gx, 5, gz, 60) * step)
            z = gz + int(h3(gz, 6, gx, 61) * step)
            if x >= HALF - 6 or z >= SZ or x < 1 or z < 1 or z > SZ - 2:
                continue
            border = x < 6 or z < 6 or z > SZ - 7
            if not border and blocked(x, z):
                continue
            land = FLOOR.get((x, z), 0) >= G + 1
            if border:
                dens = 0.95
            elif z > 70:
                dens = 0.85
            elif z < 28:
                dens = 0.2
            else:
                dens = 0.3
            r = h3(x, 7, z, 62)
            if r > dens:
                if land and not border and r < dens + 0.3 and c.top_solid(x, z) == FLOOR.get((x, z)):
                    P.bush(c, x, c.top_solid(x, z) + 1, z, r=1.1 + h3(x, 8, z, 63) * 0.8, seed=x * 7 + z)
                continue
            mangrove_zone = z > 66 or (border and h3(x, 9, z, 64) < 0.5)
            if mangrove_zone:
                mangrove(c, x, z, height=4 + int(h3(x, 1, z, 65) * 3), r=2.4 + h3(x, 2, z, 66), seed=x + z)
            elif land:
                willow(c, x, z, height=4 + int(h3(x, 3, z, 67) * 3), r=2.8 + h3(x, 4, z, 68) * 1.2, seed=x + z)
            elif FLOOR.get((x, z), 0) >= G - 1:
                mangrove(c, x, z, height=4, r=2.3, seed=x + z)


def marsh_props(c: Canvas):
    # peat stacks and upturned boats on the hummocks: cover in the open marsh
    spots = [(33, 24), (24, 12), (58, 30), (33, 52), (49, 41), (58, 60), (30, 77), (61, 70), (37, 9), (60, 6)]
    for i, (x, z) in enumerate(spots):
        if FLOOR.get((x, z), 0) < G + 1 or FLOOR.get((x + 1, z), 0) < G + 1:
            continue
        if any(poly_dist(x, z, p) < 2.5 for p in PATHS):
            continue
        if i % 2:
            upturned_boat(c, x, z, "z" if i % 3 else "x")
        else:
            peat_stack(c, x, z, w=2, d=1 + i % 2)
    # a half-sunk wreck in the north marsh
    for i in range(6):
        for v in (-1, 0, 1):
            x, z = 28 + i, 6 + v
            if i in (0, 5) and v != 0:
                continue
            f = FLOOR.get((x, z), G - 1)
            if f >= G + 1:
                continue
            if v == 0:
                put(c, x, G - 1 if i < 4 else G, z, "dark_oak_planks")
            else:
                put(c, x, G, z, "dark_oak_slab[type=bottom]" if i > 2 else "spruce_stairs[facing=" +
                    ("south" if v < 0 else "north") + ",half=bottom,shape=straight]")
                if i < 3:
                    c.set(x, G + 1, z, "spruce_slab[type=bottom]")
    # driftwood
    for (x, z, a) in ((22, 22, "x"), (55, 9, "z"), (34, 90, "x")):
        if FLOOR.get((x, z), 0) >= G + 1:
            P.fallen_log(c, x, c.top_solid(x, z) + 1, z, length=4, axis=a, kind="mangrove" if z > 70 else "oak", seed=x)


def ground_cover(c: Canvas):
    deco = Pal(("short_grass", 26), ("fern", 6), ("blue_orchid", 2), ("firefly_bush", 1), ("bush", 2), seed=80)
    reeds = lambda x, z: fbm(x, z, 7, SEED + 5) > 0.55 or z < 28 and fbm(x, z, 7, SEED + 5) > 0.45
    for x in range(1, HALF):
        for z in range(1, SZ - 1):
            y = c.top(x, z)
            s = c.get(x, y, z)
            name = parse(s)[0] if s != "air" else "air"
            if name == "water" and y == G:
                if (x, z) in FLOOR and FLOOR[(x, z)] <= G - 2 and h3(x, 13, z, 83) < 0.3:
                    put(c, x, FLOOR[(x, z)] + 1, z, "seagrass")
                if h3(x, 12, z, 84) < (0.1 if z < 70 else 0.05) and not any(poly_dist(x, z, p) < 2 for p in PATHS):
                    c.set(x, G + 1, z, "lily_pad")
                continue
            if name not in ("grass_block", "moss_block", "podzol", "mud", "muddy_mangrove_roots", "coarse_dirt"):
                continue
            if y != FLOOR.get((x, z)):
                continue
            wet = any(c.get(x + dx, G, z + dz) == "water" for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if wet and reeds(x, z) and name in ("grass_block", "mud", "coarse_dirt", "podzol", "moss_block"):
                for k in range(1 + int(h3(x, 14, z, 85) * 3)):
                    c.set(x, y + 1 + k, z, "sugar_cane[age=0]")
                continue
            if name == "grass_block" and h3(x, 15, z, 86) < 0.4:
                c.set(x, y + 1, z, deco)
            elif name in ("moss_block", "podzol") and h3(x, 16, z, 87) < 0.3:
                c.set(x, y + 1, z, "moss_carpet" if name == "moss_block" else "fern")
            elif name in ("mud", "muddy_mangrove_roots") and h3(x, 17, z, 88) < 0.06:
                c.set(x, y + 1, z, "firefly_bush")


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
    boardwalks(c)
    footbridges(c)
    base(c)
    for i, h in enumerate(HOUSES):
        house(c, h, seed=100 + i)
    house(c, FISH, seed=110)
    ruin(c)
    hall(c)
    marsh_props(c)
    woods(c)
    ground_cover(c)
    c.rotate_west_half()
    c.drop_floating()
    border(c)
    c.fix_connections()
