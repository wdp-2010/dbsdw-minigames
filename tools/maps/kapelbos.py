"""Kapelbos: a bomb map (paintball:bomb only) in a hilly wood around a village.

Not symmetric: the attackers start in an old quarry in the south (spawn 0), the defenders in the
parsonage yard in the north (spawn 1). Three ways north:

- west: across the sheep pasture with its dry-stone walls, up the chapel hill through the graveyard
  to site A, the ruined chapel;
- middle: the sunken lane (holle weg) between hedges to the village square with the well and the
  inn, then a church path to A and a cart track to B;
- east: down into a dry ravine through the forest, which climbs out right next to site B, the
  charcoal burners' yard with its smoking kilns.

The arena flags 2 and 3 are the sites (see BombSites in the paintball plugin).
"""
from __future__ import annotations

import math

import analyze
import parts as P
from mcbuild import Canvas, Grad, Pal, fbm, h3, is_full
from molenwaard import bomb_distances, letter, log_stack, sacks, stone_wall
from woudfront import poly_dist, rect_dist

WORLD = "world"
SX, SY, SZ = 110, 44, 124
G = 8
ORIGIN = (300, -63, 750)   # south of Molenwaard (z 600..691)
SEED = 91
MODES = ["paintball:bomb"]

CANVAS = Canvas(SX, SY, SZ, SEED)

# --------------------------------------------------------------------------- block-out

HILL = 2                          # the chapel stands this much higher than the village
CHAPEL = (18, 22, 28, 38)         # nave x1, z1, x2, z2 (apse north of z1)
GRAVEYARD = (12, 40, 34, 50)
KILNS = (74, 26, 100, 46)         # site B yard
SQUARE = (55, 63)                 # village square centre
SQUARE_R = 8
INN = (46, 44, 62, 53)
PARSONAGE = (38, 3, 54, 10)
CT_YARD = (40, 12, 70, 20)
QUARRY = (36, 104, 74, 119)       # attackers' spawn floor
RAVINE = [(78, 106), (83, 94), (88, 80), (89, 64), (87, 52)]
LANE = [(55, 106), (54, 94), (56, 82), (55, 72)]   # the sunken lane

SITE_A = (23.5, G + HILL + 1, 31.5)
SITE_B = (87.5, G + 1, 36.5)

PATHS = [
    # attackers: west over the pasture to the graveyard gate, middle up the lane, east to the ravine
    [(48, 108), (36, 100), (24, 88), (20, 72), (22, 56), (23, 46), (23, 40)],
    [(55, 108), (55, 106)],
    [(64, 108), (74, 106), (78, 106)],
    # village: square to A (church path), square to B (cart track), square to the defenders
    [(48, 62), (38, 56), (30, 46), (28, 40)],
    [(62, 62), (72, 54), (80, 46)],
    [(55, 55), (55, 40), (55, 22)],
    # defenders to both sites
    [(46, 16), (36, 20), (29, 24)],
    [(64, 16), (76, 22), (84, 28)],
    # ravine top exit to the kilns
    [(87, 52), (87, 47)],
]

SPAWNS = {
    0: [(46.5, G + 1, 113.5), (50.5, G + 1, 113.5), (54.5, G + 1, 113.5), (58.5, G + 1, 113.5),
        (62.5, G + 1, 113.5), (54.5, G + 1, 109.5)],
    1: [(46.5, G + 1, 15.5), (50.5, G + 1, 15.5), (54.5, G + 1, 15.5), (58.5, G + 1, 15.5),
        (62.5, G + 1, 15.5), (54.5, G + 1, 18.5)],
}
FLAGS = {2: SITE_A, 3: SITE_B}
YAW = {0: 180.0, 1: 0.0, 2: 0.0, 3: 0.0}
POWERUPS = []
CLOSEUPS = {"siteA": (8, 14, 40, 54), "siteB": (70, 20, 104, 56), "square": (38, 40, 74, 76),
            "quarry": (30, 96, 80, 124), "ravine": (70, 50, 100, 110)}

BUILT = [CHAPEL, GRAVEYARD, KILNS, INN, PARSONAGE, CT_YARD, QUARRY]


# --------------------------------------------------------------------------- helpers


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def ravine_depth(x, z):
    """How deep the ravine floor lies here (0 outside): 4 in the middle, ramps at both ends."""
    d = poly_dist(x, z, RAVINE)
    if d > 2.6:
        return 0
    ramp = min(smooth((RAVINE[0][1] - z) / 10), smooth((z - RAVINE[-1][1]) / 7))
    return round(4 * ramp)


def lane_depth(x, z):
    d = poly_dist(x, z, LANE)
    if d > 2.2:
        return 0
    ramp = min(smooth((LANE[0][1] - z) / 6), smooth((z - LANE[-1][1]) / 6))
    return round(2 * ramp)


def hill(x, z):
    """The chapel hill: HILL blocks up on the chapel and the graveyard, sloping off over ~9 blocks."""
    d = min(rect_dist(x, z, CHAPEL), rect_dist(x, z, (16, 20, 30, 42)))
    return HILL * smooth(1 - (d - 2) / 9)


def height(x, z):
    n = (fbm(x, z, 22, SEED) - 0.5) * 6
    flat = 0.0
    for r in BUILT:
        flat = max(flat, 1 - min(1, rect_dist(x, z, r) / 6))
    flat = max(flat, 1 - min(1, (math.hypot(x - SQUARE[0], z - SQUARE[1]) - SQUARE_R) / 6))
    flat = max(flat, 1 - min(1, min(poly_dist(x, z, p) for p in PATHS) / 4))
    flat = max(flat, 1 - min(1, poly_dist(x, z, RAVINE) / 6))
    flat = max(flat, 1 - min(1, poly_dist(x, z, LANE) / 5))
    edge = min(x, z, SX - 1 - x, SZ - 1 - z)
    rise = max(0, 6 - edge) * 0.9
    if z > 112:   # the quarry face behind the attackers' spawn
        rise = max(rise, (z - 112) * 1.4)
    return G + round(n * (1 - flat) + rise + hill(x, z))


def on_path(x, z, w=1.4):
    return any(poly_dist(x, z, p) < w + 0.6 * h3(x, 1, z, 3) for p in PATHS)


def ground(c, x, z):
    return c.top_solid(x, z) + 1


# --------------------------------------------------------------------------- terrain


def terrain(c: Canvas):
    grass = "grass_block[snowy=false]"
    path_pal = Pal(("dirt_path", 5), ("coarse_dirt", 2), ("gravel", 1), seed=3)
    forest_floor = Pal((grass, 6), ("podzol[snowy=false]", 2), ("coarse_dirt", 1), ("moss_block", 1), seed=6, scale=3)
    rock = Pal(("stone", 4), ("andesite", 3), ("cobblestone", 1), ("tuff", 1), seed=7, scale=2)
    rock_wall = Grad([Pal(("tuff", 2), ("stone", 2), ("andesite", 2), seed=8),
                      Pal(("stone", 3), ("andesite", 2), ("cobblestone", 1), seed=9)], G - 5, G + 1, seed=10)
    ravine_floor = Pal(("gravel", 3), ("coarse_dirt", 2), ("moss_block", 1), ("rooted_dirt", 1), seed=11, scale=2)
    lane_floor = Pal(("dirt_path", 3), ("coarse_dirt", 2), ("mud", 1), seed=12)
    quarry_floor = Pal(("gravel", 3), ("stone", 2), ("andesite", 2), ("coarse_dirt", 1), seed=13, scale=2)
    for x in range(SX):
        for z in range(SZ):
            h = height(x, z)
            rd = ravine_depth(x, z)
            ld = lane_depth(x, z)
            in_quarry = rect_dist(x, z, QUARRY) < 1
            if rd:
                h = G - rd
                top = ravine_floor
            elif ld:
                h = G - ld
                top = lane_floor
            elif in_quarry:
                h = G
                top = quarry_floor
            elif z > 112 or 2.6 < poly_dist(x, z, RAVINE) < 4.2:
                top = rock                     # quarry face and the ravine's rocky rim
            elif on_path(x, z):
                top = path_pal
            elif x > 68 and 50 < z < 104 or fbm(x, z, 14, SEED + 9) > 0.6:
                top = forest_floor
            else:
                top = grass
            c.column(x, z, 0, h - 4, "stone")
            if top is rock or rd:
                c.column(x, z, h - 3, h - 1, rock_wall)
            else:
                c.column(x, z, h - 3, h - 1, "dirt")
            c.set(x, h, z, top)
    # the ravine and lane sides show rock and earth, not grass on dirt walls
    for x in range(1, SX - 1):
        for z in range(1, SZ - 1):
            if ravine_depth(x, z) or lane_depth(x, z):
                continue
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                deep = ravine_depth(x + dx, z + dz)
                if deep:
                    for y in range(G - deep + 1, c.top_solid(x, z)):
                        c.set(x, y, z, rock_wall)


# --------------------------------------------------------------------------- site A: the chapel


def chapel(c: Canvas):
    """A ruined stone chapel on the hill: nave with tall windows, a round apse to the north, a
    bell turret on the south front. The south-east corner has come down on a diagonal."""
    x1, z1, x2, z2 = CHAPEL
    f = G + HILL
    wall = Grad([Pal(("mossy_cobblestone", 3), ("cobblestone", 2), ("mossy_stone_bricks", 2), seed=20),
                 Pal(("stone_bricks", 3), ("mossy_stone_bricks", 2), ("cracked_stone_bricks", 1), ("cobblestone", 1),
                     seed=21),
                 Pal(("stone_bricks", 3), ("cracked_stone_bricks", 2), ("andesite", 1), seed=22)], f + 1, f + 9, seed=23)
    floor = Pal(("stone_bricks", 3), ("cracked_stone_bricks", 1), ("mossy_stone_bricks", 1), ("gravel", 1),
                ("coarse_dirt", 1), seed=24)
    for x in range(x1 - 2, x2 + 3):
        for z in range(z1 - 6, z2 + 3):
            for y in range(f + 1, f + 16):
                c.set(x, y, z, "air")
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            c.set(x, f, z, floor)
    # apse: half disc north of the nave
    cx, cz, r = (x1 + x2) / 2, z1, (x2 - x1) / 2
    for x in range(x1, x2 + 1):
        for z in range(z1 - 6, z1):
            d = math.hypot(x - cx, z - cz)
            if d <= r + 0.3:
                c.set(x, f, z, floor)
                if d > r - 0.8:
                    top = f + 7 - (1 if h3(x, 0, z, 25) < 0.3 else 0)
                    for y in range(f + 1, top + 1):
                        c.set(x, y, z, wall)
    for x in range(x1 + 1, x2):          # the nave opens into the apse
        for y in range(f + 1, f + 8):
            c.set(x, y, z1, "air")

    def broken(x, z):
        """The collapsed south-east corner: walls stop on a diagonal."""
        return (x - x1) + (z2 - z) * 0.9 > 0 and (x2 - x) + (z2 - z) < 6

    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            if x not in (x1, x2) and z not in (z1, z2):
                continue
            if z == z1 and x1 < x < x2:
                continue
            top = f + 8 + (1 if h3(x, 1, z, 26) < 0.25 else 0)
            if broken(x, z):
                top = f + max(0, (x2 - x) + (z2 - z)) - 1
            for y in range(f + 1, top + 1):
                c.set(x, y, z, "stone_bricks" if x in (x1, x2) and z in (z1, z2) else wall)
    # buttresses on the long walls
    for z in range(z1 + 2, z2 - 1, 4):
        for x, ox in ((x1, -1), (x2, 1)):
            if broken(x, z):
                continue
            for y in range(f + 1, f + 6):
                c.set(x + ox, y, z, "stone_bricks" if y < f + 5 else "stone_brick_stairs[facing="
                      + ("east" if ox < 0 else "west") + ",half=bottom,shape=straight]")
    # tall windows between the buttresses (open: you can shoot through)
    for z in range(z1 + 4, z2 - 1, 4):
        for x in (x1, x2):
            if broken(x, z):
                continue
            for y in range(f + 3, f + 7):
                c.set(x, y, z, "air")
            c.set(x, f + 2, z, "stone_brick_slab[type=bottom]")
    # doors: the main door in the south front, a side door east, a gap in the west wall
    zm = (x1 + x2) // 2
    for x in (zm - 1, zm, zm + 1):
        for y in range(f + 1, f + 5):
            c.set(x, y, z2, "air")
    for z in (z1 + 6, z1 + 7):
        for y in range(f + 1, f + 4):
            c.set(x2, y, z, "air")
    for z in (z1 + 9, z1 + 10):
        for y in range(f + 1, f + 4):
            c.set(x1, y, z, "air")
    # bell turret over the south front, on its west half (the east half has come down)
    for y in range(f + 9, f + 13):
        for (tx, tz) in ((x1, z2), (x1 + 3, z2)):
            c.set(tx, y, tz, "stone_bricks")
    for tx in range(x1, x1 + 4):
        c.set(tx, f + 9, z2, "stone_bricks")
        c.set(tx, f + 13, z2, "stone_brick_slab[type=bottom]")
    c.set(x1 + 1, f + 13, z2, "stone_bricks")
    c.set(x1 + 2, f + 13, z2, "stone_bricks")
    c.set(x1 + 1, f + 12, z2, "bell[attachment=ceiling,facing=south,powered=false]")
    # inside: broken pews, an altar block in the apse, rubble under the collapse
    for z in range(z1 + 3, z2 - 2, 3):
        for x in range(x1 + 1, x1 + 4):
            if h3(x, 2, z, 27) < 0.8:
                c.set(x, f + 1, z, "spruce_stairs[facing=south,half=bottom,shape=straight]")
        for x in range(x2 - 3, x2):
            if h3(x, 3, z, 28) < 0.55 and not broken(x, z):
                c.set(x, f + 1, z, "spruce_stairs[facing=south,half=bottom,shape=straight]")
    c.set(zm, f + 1, z1 - 3, "chiseled_stone_bricks")
    c.set(zm - 1, f + 1, z1 - 3, "stone_brick_stairs[facing=east,half=bottom,shape=straight]")
    c.set(zm + 1, f + 1, z1 - 3, "stone_brick_stairs[facing=west,half=bottom,shape=straight]")
    rub = Pal(("cobblestone", 3), ("mossy_cobblestone", 2), ("stone_bricks", 1), ("gravel", 1), seed=29)
    for (rx, rz, rr) in ((x2 - 2, z2 - 2, 1.8), (x2 + 2, z2 - 1, 1.5), (x2 - 1, z2 + 2, 1.4)):
        c.blob(rx, f + 0.6, rz, rr, 0.9, rr, rub, rough=0.5, seed=rx + rz)
    for (sx_, sz_) in ((x2 + 3, z2 - 4), (x2 - 4, z2 + 3)):
        c.set(sx_, ground(c, sx_, sz_), sz_, "cobblestone_slab[type=bottom]")
    # a fallen roof beam across the nave
    for x in range(x1 + 1, x2):
        c.set(x, f + 1 + (1 if x > zm else 0), z1 + 12, "dark_oak_log[axis=x]")


def graveyard(c: Canvas):
    """South of the chapel: a low wall with a lychgate, gravestones in rows, two yews, the A board."""
    x1, z1, x2, z2 = GRAVEYARD
    stone_wall(c, x1, z2, x2, z2, height=1.5, gaps=((21, 25),), seed=30)
    stone_wall(c, x1, z1 - 4, x1, z2, height=1.5, gaps=((44, 45),), seed=31)
    stone_wall(c, x2, z1, x2, z2, height=1.5, gaps=((45, 46),), seed=32)
    # lychgate over the south gap: a little roof on posts
    gx1, gx2, gz = 21, 25, z2
    for x in (gx1, gx2):
        c.column(x, gz, ground(c, x, gz), ground(c, x, gz) + 2, "dark_oak_log[axis=y]")
    gy = ground(c, gx1, gz) + 3
    for x in range(gx1 - 1, gx2 + 2):
        c.set(x, gy, gz - 1, "dark_oak_stairs[facing=south,half=bottom,shape=straight]")
        c.set(x, gy, gz + 1, "dark_oak_stairs[facing=north,half=bottom,shape=straight]")
        c.set(x, gy, gz, "dark_oak_planks")
        c.set(x, gy + 1, gz, "dark_oak_slab[type=bottom]")
    # gravestones: one block, some with a slab, in rows; crosses are stone brick walls with a block on top
    for z in range(z1 + 1, z2 - 1, 3):
        for x in range(x1 + 2, x2 - 1, 3):
            if 20 <= x <= 26 or h3(x, 4, z, 33) < 0.25:
                continue
            y = ground(c, x, z)
            kind = h3(x, 5, z, 34)
            if kind < 0.5:
                c.set(x, y, z, Pal("mossy_stone_bricks", "stone_bricks", "cracked_stone_bricks", seed=35))
                c.set(x, y + 1, z, "mossy_stone_brick_slab[type=bottom]" if kind < 0.25 else "stone_brick_slab[type=bottom]")
            else:
                c.set(x, y, z, "chiseled_stone_bricks")
                c.set(x, y + 1, z, "stone_brick_stairs[facing=south,half=bottom,shape=straight]")
    for (yx, yz) in ((x1 + 3, z1 + 2), (x2 - 3, z2 - 3)):
        P.spruce(c, yx, ground(c, yx, yz), yz, height=6, seed=yx)
    # the A board by the lychgate, on two logs
    bx, bz = 28, z2 + 2
    by = ground(c, bx + 2, bz)
    c.column(bx - 1, bz, by, by + 7, "dark_oak_log[axis=y]")
    c.column(bx + 5, bz, by, by + 7, "dark_oak_log[axis=y]")
    letter(c, "A", bx, by + 1, bz, along="x", step=1)
    for x in range(bx, bx + 5):
        c.set(x, by, bz, "dark_oak_log[axis=x]")
    for x in range(bx - 1, bx + 6):
        c.set(x, by + 8, bz, "dark_oak_slab[type=bottom]")


# --------------------------------------------------------------------------- site B: charcoal kilns


def kiln(c: Canvas, x, z, r=3.0, lit=True, seed=0):
    """A charcoal kiln: a round earth mound with a smoking fire on top."""
    y = ground(c, x, z)
    skin = Pal(("coarse_dirt", 3), ("podzol[snowy=false]", 2), ("mud", 1), ("packed_mud", 1), ("rooted_dirt", 1),
               seed=seed)
    c.blob(x, y - 0.5, z, r, r * 0.95, r, skin, only_empty=False, rough=0.2, seed=seed)
    top = c.top_solid(x, z)
    if lit:
        c.set(x, top + 1, z, "campfire[lit=true,signal_fire=false,facing=north,waterlogged=false]")


def kilns(c: Canvas):
    x1, z1, x2, z2 = KILNS
    floor = Pal(("coarse_dirt", 3), ("black_concrete_powder", 1), ("gravel", 2), ("podzol[snowy=false]", 1),
                ("coal_block", 1), seed=40, scale=2)
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            if rect_dist(x, z, (x1 + 2, z1 + 2, x2 - 2, z2 - 2)) < 2.5:
                c.set(x, G, z, floor)
                for y in range(G + 1, G + 14):
                    c.set(x, y, z, "air")
    sx, _, sz = (int(v) for v in SITE_B)
    kiln(c, sx - 7, sz - 4, r=3.2, seed=41)
    kiln(c, sx + 6, sz + 3, r=3.0, seed=42)
    kiln(c, sx + 8, sz - 6, r=2.4, lit=False, seed=43)
    # on the site: charcoal sacks, a cart, a log stack waiting for the next burn
    sacks_pal = Pal(("black_wool", 3), ("gray_wool", 1), seed=44)
    for (bx, bz) in ((sx - 2, sz + 2), (sx - 1, sz + 2), (sx - 2, sz + 3), (sx + 2, sz - 2)):
        c.set(bx, G + 1, bz, sacks_pal)
    c.set(sx - 2, G + 2, sz + 2, "black_wool")
    for x in range(sx + 1, sx + 4):
        for z in (sz + 1,):
            c.set(x, G + 1, z, "spruce_slab[type=top]")
            c.set(x, G + 2, z, "coal_block" if x != sx + 2 else "black_wool")
    for (wx, wz) in ((sx + 1, sz), (sx + 3, sz), (sx + 1, sz + 2), (sx + 3, sz + 2)):
        c.set(wx, G + 1, wz, "dark_oak_trapdoor[facing=" + ("north" if wz <= sz else "south") + ",half=bottom,open=true]")
    log_stack(c, sx - 6, G + 1, sz + 5, length=6, axis="x", kind="oak", rows=(2, 1))
    # the burners' shed on the east side, with the B board on its back wall facing the site
    hx1, hz1, hx2, hz2 = x2 - 6, z1 + 3, x2 - 1, z2 - 6
    for x in range(hx1, hx2 + 1):
        for z in range(hz1, hz2 + 1):
            c.set(x, G, z, "spruce_planks")
    for (px, pz) in ((hx1, hz1), (hx1, hz2), (hx1, (hz1 + hz2) // 2)):
        c.column(px, pz, G + 1, G + 4, "spruce_log[axis=y]")
    for z in range(hz1, hz2 + 1):           # back wall (east)
        for y in range(G + 1, G + 6):
            c.set(hx2, y, z, "spruce_log[axis=y]" if z in (hz1, hz2) else
                  Pal(("spruce_planks", 3), ("dark_oak_planks", 1), seed=45))
    for x in range(hx1 + 1, hx2):           # side walls, half height
        for z in (hz1, hz2):
            for y in range(G + 1, G + 3):
                c.set(x, y, z, "spruce_planks")
    for x in range(hx1 - 1, hx2 + 1):       # lean-to roof falling to the west
        for z in range(hz1 - 1, hz2 + 2):
            y = G + 5 + (1 if x >= hx2 - 1 else 0)
            c.set(x, y, z, "spruce_slab[type=bottom]")
    for z in range(hz1, hz2 + 1):
        c.set(hx1, G + 5, z, "spruce_log[axis=z]")
    # coal heap and baskets inside the shed
    for z in range(hz1 + 1, hz2):
        c.set(hx2 - 1, G + 1, z, "coal_block")
        if z % 2:
            c.set(hx2 - 1, G + 2, z, "black_wool")
    c.set(hx1 + 2, G + 1, hz1 + 2, "barrel[facing=up,open=false]")
    # the B board on the shed's back wall, seen from the site through the open front
    letter_wall(c, "B", hx2, G + 1, (hz1 + hz2) // 2 - 2)
    # the defenders' side: a woodpile wall with a gap
    for x in range(x1 + 2, x2 - 8):
        if 82 <= x <= 85:
            continue
        c.set(x, G + 1, z1, "oak_log[axis=x]")


def letter_wall(c: Canvas, ch, x, y, z):
    """The letter on a wall facing west (read from the west, so z grows to the right)."""
    letter(c, ch, x, y, z, along="z", step=1)


# --------------------------------------------------------------------------- the village


def square(c: Canvas):
    cx, cz = SQUARE
    cobbles = Pal(("cobblestone", 3), ("stone_bricks", 2), ("andesite", 1), ("mossy_cobblestone", 1),
                  ("gravel", 1), seed=50, scale=2)
    for x in range(cx - SQUARE_R - 1, cx + SQUARE_R + 2):
        for z in range(cz - SQUARE_R - 1, cz + SQUARE_R + 2):
            if math.hypot(x - cx, z - cz) <= SQUARE_R + 0.5 * h3(x, 0, z, 51):
                c.set(x, G, z, cobbles)
                for y in range(G + 1, G + 10):
                    c.set(x, y, z, "air")
    # the well: stone ring, two posts, a little roof
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if dx or dz:
                c.set(cx + dx, G + 1, cz + dz, "stone_bricks")
    c.set(cx, G, cz, "water")
    c.set(cx, G - 1, cz, "stone")
    for dx in (-1, 1):
        c.column(cx + dx, cz, G + 2, G + 3, "spruce_fence")
        c.set(cx + dx, G + 4, cz, "spruce_planks")
    for dz in (-1, 0, 1):
        c.set(cx, G + 4, cz + dz, "spruce_slab[type=bottom]")
    c.set(cx, G + 3, cz, "iron_chain[axis=y]")
    # market stalls: plank counters under striped awnings (carpets on top slabs)
    for (sx, sz, cols) in ((cx - 6, cz + 2, ("red_wool", "white_wool")), (cx + 5, cz - 3, ("green_wool", "white_wool"))):
        for i in range(3):
            c.set(sx, G + 1, sz + i, "spruce_planks")
            c.set(sx, G + 2, sz + i, "spruce_slab[type=bottom]")
        for dx in (-1, 1):
            for dz in (0, 2):
                c.column(sx + dx, sz + dz, G + 1, G + 2, "spruce_fence")
                c.set(sx + dx, G + 3, sz + dz, "spruce_planks")
        for dx in (-1, 0, 1):
            for dz in (0, 1, 2):
                c.set(sx + dx, G + 3, sz + dz, "spruce_planks" if dx and dz in (0, 2) else "spruce_slab[type=top]")
                c.set(sx + dx, G + 4, sz + dz, (cols[(dz + dx) % 2]).replace("_wool", "_carpet"))
    # a hay cart and barrels
    P.crate_stack(c, cx + 3, G + 1, cz + 5, w=2, d=1, h=1, seed=52)
    c.set(cx - 3, G + 1, cz - 5, "barrel[facing=up,open=false]")
    c.set(cx - 4, G + 1, cz - 5, "hay_block[axis=x]")
    P.lamp_post(c, cx - 5, G + 1, cz - 1)


def inn(c: Canvas):
    """The inn on the north side of the square: stone ground floor, timber upper floor with
    windows over the square, doors south (square) and north (toward the defenders)."""
    x1, z1, x2, z2 = INN
    for x in range(x1 - 1, x2 + 2):
        for z in range(z1 - 1, z2 + 2):
            for y in range(G + 1, G + 18):
                c.set(x, y, z, "air")
    stone = Grad([Pal(("cobblestone", 3), ("mossy_cobblestone", 2), ("stone", 1), seed=60),
                  Pal(("stone_bricks", 3), ("cobblestone", 2), ("andesite", 1), seed=61)], G + 1, G + 4, seed=62)
    timber = Pal(("oak_planks", 3), ("stripped_oak_log[axis=y]", 1), ("spruce_planks", 1), seed=63)
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            c.set(x, G, z, Pal(("spruce_planks", 3), ("oak_planks", 1), seed=64))
            edge = x in (x1, x2) or z in (z1, z2)
            corner = x in (x1, x2) and z in (z1, z2)
            if not edge:
                continue
            for y in range(G + 1, G + 9):
                if corner:
                    c.set(x, y, z, "stripped_dark_oak_log[axis=y]")
                elif y <= G + 4:
                    c.set(x, y, z, stone)
                else:
                    post = (x - x1) % 4 == 0 if z in (z1, z2) else (z - z1) % 3 == 0
                    c.set(x, y, z, "dark_oak_log[axis=y]" if post else timber)
    for x in range(x1 + 1, x2):
        for z in range(z1 + 1, z2):
            c.set(x, G + 5, z, Pal(("spruce_planks", 4), ("dark_oak_planks", 1), seed=65))
    # doors: south at x 49..50 and north at x 58..59 (offset: no straight line through)
    for y in (G + 1, G + 2, G + 3):
        for x in (49, 50):
            c.set(x, y, z2, "air")
        for x in (58, 59):
            c.set(x, y, z1, "air")
    # ground floor windows, upper floor windows over the square
    for x in (x1 + 4, x1 + 11):
        c.set(x, G + 2, z2, "air")
        c.set(x, G + 3, z2, "air")
    for x in (x1 + 2, x1 + 6, x1 + 10, x1 + 14):
        if x < x2:
            c.set(x, G + 6, z2, "air")
            c.set(x, G + 7, z2, "air")
            c.set(x, G + 5, z2 + 1, "dark_oak_trapdoor[facing=south,half=top,open=false]")
    for z in (z1 + 3, z1 + 6):
        c.set(x1, G + 6, z, "air")
        c.set(x2, G + 6, z, "air")
    # stairs up along the west wall, a hole in the upper floor
    for i in range(4):
        c.set(x1 + 1, G + 1 + i, z1 + 2 + i, "spruce_stairs[facing=south,half=bottom,shape=straight]")
        c.set(x1 + 1, G + 5, z1 + 2 + i, "air")
    c.set(x1 + 1, G + 5, z1 + 6, "air")
    # inside: bar counter, tables, barrels
    for x in range(x1 + 6, x1 + 11):
        c.set(x, G + 1, z1 + 3, "spruce_planks")
        c.set(x, G + 2, z1 + 3, "spruce_slab[type=bottom]")
    for x in range(x1 + 6, x1 + 11, 2):
        c.set(x, G + 1, z1 + 1, "barrel[facing=south,open=false]")
    for (tx, tz) in ((x1 + 4, z2 - 3), (x2 - 4, z2 - 3)):
        c.set(tx, G + 1, tz, "spruce_fence")
        c.set(tx, G + 2, tz, "spruce_pressure_plate[powered=false]")
    for (lx, lz) in ((x1 + 5, z1 + 4), (x2 - 4, z1 + 4)):
        c.set(lx, G + 4, lz, "lantern[hanging=true]")
    # roof along x, a hanging sign bracket over the south door
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
    c.column(x2 - 2, z1 + 1, G + 1, G + 9 + half + 1, Pal("bricks", "cobblestone", "stone_bricks", seed=66))
    c.set(48, G + 4, z2 + 1, "spruce_fence")
    c.set(48, G + 3, z2 + 1, "lantern[hanging=true]")


def cottages(c: Canvas):
    """Two small cottages at the square's west and east sides: cover and some silhouette."""
    for (x1, z1, x2, z2, door) in ((37, 60, 43, 67, "east"), (67, 58, 73, 65, "west")):
        for x in range(x1, x2 + 1):
            for z in range(z1, z2 + 1):
                c.set(x, G, z, "spruce_planks")
                for y in range(G + 1, G + 10):
                    c.set(x, y, z, "air")
                if x in (x1, x2) or z in (z1, z2):
                    corner = x in (x1, x2) and z in (z1, z2)
                    for y in range(G + 1, G + 4):
                        c.set(x, y, z, "spruce_log[axis=y]" if corner else
                              Pal(("white_terracotta", 2), ("light_gray_terracotta", 1), ("oak_planks", 1), seed=x1))
        dx = x2 if door == "east" else x1
        for z in ((z1 + z2) // 2, (z1 + z2) // 2 + 1):
            for y in (G + 1, G + 2):
                c.set(dx, y, z, "air")
        ox = x1 if door == "east" else x2
        c.set(ox, G + 2, (z1 + z2) // 2, "air")
        for i in range(4):
            y = G + 4 + i
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
                    c.set(x, y, z, "oak_planks")


def lane(c: Canvas):
    """The sunken lane: hedges along both rims, an old cart wheel and a shrine halfway up."""
    for i in range(len(LANE) - 1):
        (ax, az), (bx, bz) = LANE[i], LANE[i + 1]
        n = int(max(abs(bx - ax), abs(bz - az)))
        for k in range(n + 1):
            t = k / max(1, n)
            px, pz = ax + (bx - ax) * t, az + (bz - az) * t
            if not (LANE[-1][1] + 4 < pz < LANE[0][1] - 4):
                continue
            for side in (-3.2, 3.2):
                hx, hz = round(px + side), round(pz)
                if h3(hx, 0, hz, 70) < 0.85:
                    P.bush(c, hx, ground(c, hx, hz), hz, r=1.3, seed=hx * 5 + hz)
    # a wayside shrine in a niche of the west bank
    sx, sz = 51, 88
    y = ground(c, sx, sz)
    c.column(sx, sz, y, y + 1, "stone_bricks")
    c.set(sx, y + 2, sz, "stone_brick_stairs[facing=east,half=bottom,shape=straight]")


# --------------------------------------------------------------------------- spawns


def quarry(c: Canvas):
    """The attackers' quarry: a stone face behind them, cut blocks, a derrick, a hut."""
    x1, z1, x2, z2 = QUARRY
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            for y in range(G + 1, G + 12):
                c.set(x, y, z, "air")
    cut = Pal(("stone", 2), ("smooth_stone", 2), ("andesite", 1), ("polished_andesite", 1), seed=80)
    for (bx, bz, w, d, h) in ((39, 106, 2, 2, 2), (69, 106, 2, 3, 2), (44, 117, 3, 1, 1), (65, 117, 2, 1, 2),
                              (52, 104, 1, 1, 1), (58, 104, 1, 1, 1)):
        for i in range(w):
            for k in range(d):
                hh = h if h3(bx + i, 0, bz + k, 81) > 0.3 else max(1, h - 1)
                for j in range(hh):
                    c.set(bx + i, G + 1 + j, bz + k, cut)
    # the derrick: a log mast with a boom over the cut blocks
    mx, mz = 41, 116
    c.column(mx, mz, G + 1, G + 10, "spruce_log[axis=y]")
    for k in range(1, 6):
        c.set(mx + k, G + 10 - (k // 3), mz, "spruce_log[axis=x]")
    c.column(mx + 5, mz, G + 6, G + 7, "iron_chain[axis=y]")
    c.set(mx + 5, G + 5, mz, "smooth_stone")
    for (dx, dz) in ((-1, 0), (0, -1), (1, 0)):
        c.set(mx + dx, G + 1, mz + dz, "spruce_planks")
    # a tool hut on the east side
    hx1, hz1, hx2, hz2 = 66, 110, 72, 115
    for x in range(hx1, hx2 + 1):
        for z in range(hz1, hz2 + 1):
            c.set(x, G, z, "spruce_planks")
            if x in (hx1, hx2) or z in (hz1, hz2):
                for y in range(G + 1, G + 4):
                    c.set(x, y, z, "spruce_log[axis=y]" if (x in (hx1, hx2) and z in (hz1, hz2)) else "spruce_planks")
    for z in (112, 113):
        for y in (G + 1, G + 2):
            c.set(hx1, y, z, "air")
    for x in range(hx1 - 1, hx2 + 2):
        for z in range(hz1 - 1, hz2 + 2):
            c.set(x, G + 4, z, "spruce_slab[type=bottom]")
    c.set(hx2 - 1, G + 1, hz1 + 1, "barrel[facing=up,open=false]")
    c.set(hx2 - 1, G + 1, hz2 - 1, "anvil[facing=north]")
    # a rail spur with a mine cart's worth of stone
    for x in range(45, 64):
        c.set(x, G + 1, 119, "rail[shape=east_west,waterlogged=false]")
    P.lamp_post(c, 49, G + 1, 117)
    P.lamp_post(c, 61, G + 1, 117)


def parsonage(c: Canvas):
    """The defenders' parsonage north of their yard: a stone hall, a garden wall, an arch."""
    from basiskamp import hall
    x1, z1, x2, z2 = PARSONAGE
    hall(c, x1, z1, x2, z2, h=G, door_x=None)
    for x in (46, 47):
        for y in (G + 1, G + 2, G + 3):
            c.set(x, y, z2, "air")
    yx1, yz1, yx2, yz2 = CT_YARD
    floor = Pal(("gravel", 2), ("coarse_dirt", 2), ("dirt_path", 2), ("grass_block[snowy=false]", 1), seed=90, scale=2)
    for x in range(yx1 - 2, yx2 + 3):
        for z in range(yz1 - 1, yz2 + 2):
            if rect_dist(x, z, CT_YARD) < 1.5:
                c.set(x, G, z, floor)
                for y in range(G + 1, G + 10):
                    c.set(x, y, z, "air")
    # a garden wall east of the hall with a vegetable plot behind it
    stone_wall(c, 57, 4, 57, 11, height=2, seed=91)
    stone_wall(c, 57, 11, 70, 11, height=1.5, gaps=((62, 64),), seed=92)
    for x in range(58, 70):
        for z in range(4, 11):
            if (x + z) % 2 and h3(x, 0, z, 93) < 0.7:
                c.set(x, ground(c, x, z), z, "fern")
    # cover at the yard's south edge: crates and a woodpile
    P.crate_stack(c, 44, G + 1, 20, w=2, d=1, h=2, seed=94)
    P.crate_stack(c, 64, G + 1, 20, w=2, d=1, h=2, seed=95)
    log_stack(c, 50, G + 1, 21, length=3, axis="x", kind="spruce", rows=(2, 1))
    P.lamp_post(c, 41, G + 1, 14)
    P.lamp_post(c, 68, G + 1, 14)


# --------------------------------------------------------------------------- nature


def pasture(c: Canvas):
    """The west lane: a sheep pasture with dry-stone walls, a field barn and a few trees."""
    stone_wall(c, 10, 70, 32, 70, height=1.5, gaps=((18, 23),), seed=100)
    stone_wall(c, 32, 70, 32, 92, height=1.5, gaps=((80, 83),), seed=101)
    stone_wall(c, 12, 92, 26, 92, height=1.5, gaps=((20, 24),), seed=102)
    stone_wall(c, 26, 58, 34, 58, height=1.5, seed=103)
    # field barn (shelter, open on the south)
    bx1, bz1, bx2, bz2 = 11, 76, 17, 84
    for x in range(bx1, bx2 + 1):
        for z in range(bz1, bz2 + 1):
            y = G
            c.set(x, y, z, "coarse_dirt")
            for yy in range(y + 1, y + 8):
                c.set(x, yy, z, "air")
            if x in (bx1, bx2) or z == bz1:
                for yy in range(y + 1, y + 4):
                    c.set(x, yy, z, "spruce_log[axis=y]" if (x in (bx1, bx2) and z in (bz1, bz2)) else "spruce_planks")
    for z in (bz2,):
        c.column(bx1, z, G + 1, G + 3, "spruce_log[axis=y]")
        c.column(bx2, z, G + 1, G + 3, "spruce_log[axis=y]")
    for x in range(bx1 - 1, bx2 + 2):
        for z in range(bz1 - 1, bz2 + 2):
            c.set(x, G + 4, z, "spruce_slab[type=bottom]")
    for x in range(bx1 + 1, bx2):
        c.set(x, G + 1, bz1 + 1, "hay_block[axis=x]")
        if x % 2:
            c.set(x, G + 2, bz1 + 1, "hay_block[axis=x]")
    for (x, z) in ((27, 78), (14, 98), (34, 98)):
        P.oak(c, x, ground(c, x, z), z, height=4, r=2.4, seed=x + z)
    for (x, z, r) in ((22, 64, 1.8), (29, 86, 2.0)):
        P.boulder(c, x, ground(c, x, z), z, r=r, seed=x)
    c.set(25, ground(c, 25, 74), 74, "hay_block[axis=y]")
    c.set(26, ground(c, 26, 74), 74, "hay_block[axis=y]")


def ravine_props(c: Canvas):
    """Fallen trunks and boulders in the ravine (cover in a tight space) and a log bridge over it."""
    for (x, z, r) in ((84, 90, 1.3), (89, 72, 1.2)):
        P.boulder(c, x, ground(c, x, z), z, r=r, seed=x)
    P.fallen_log(c, 86, ground(c, 86, 82), 82, length=3, axis="x", seed=110)
    P.fallen_log(c, 87, ground(c, 87, 61), 61, length=3, axis="x", seed=111)
    # a log bridge at z 68: east forest to the cart track
    bz = 68
    xs = [x for x in range(80, 98) if poly_dist(x, bz, RAVINE) < 4.6]
    for x in range(min(xs) - 1, max(xs) + 2):
        c.set(x, G, bz, "spruce_log[axis=x]")
        c.set(x, G, bz + 1, "spruce_log[axis=x]")
        c.set(x, G + 1, bz - 1, "spruce_fence")
        c.set(x, G + 1, bz + 2, "spruce_fence")
        c.set(x, G + 1, bz, "air")
        c.set(x, G + 1, bz + 1, "air")


def woods(c: Canvas):
    def blocked(x, z, pad):
        for r in BUILT + [(11, 76, 17, 84), (37, 58, 43, 67), (67, 58, 73, 65)]:
            if rect_dist(x, z, r) < 3.5 + pad:
                return True
        if math.hypot(x - SQUARE[0], z - SQUARE[1]) < SQUARE_R + 4 + pad:
            return True
        if poly_dist(x, z, RAVINE) < 5 + pad or poly_dist(x, z, LANE) < 4.5 + pad:
            return True
        if any(poly_dist(x, z, p) < 2.8 + pad for p in PATHS):
            return True
        if 10 <= x <= 34 and 58 <= z <= 92:   # the pasture stays open
            return True
        return False

    step = 5
    for gx in range(0, SX, step):
        for gz in range(0, SZ, step):
            x = gx + int(h3(gx, 5, gz, 60) * step)
            z = gz + int(h3(gz, 6, gx, 61) * step)
            if x >= SX or z >= SZ:
                continue
            border = x < 5 or z < 5 or x > SX - 6 or (z > SZ - 6 and not 34 <= x <= 76)
            if not border and blocked(x, z, 0):
                continue
            if border:
                dens = 0.95
            elif x > 66 and 48 < z < 102:
                dens = 0.85                      # the dense east wood around the ravine
            elif z < 50:
                dens = 0.4
            else:
                dens = 0.3 + 0.4 * fbm(x, z, 20, SEED + 2)
            r = h3(x, 7, z, 62)
            if r > dens:
                if not border and r < dens + 0.25 and not blocked(x, z, 0):
                    P.bush(c, x, ground(c, x, z), z, r=1.2 + h3(x, 8, z, 63), seed=x * 7 + z)
                continue
            y = ground(c, x, z)
            kind = h3(x, 9, z, 64)
            if border and kind < 0.5 or kind < 0.25:
                P.spruce(c, x, y, z, height=8 + int(h3(x, 1, z, 65) * 5), seed=x + z)
            elif kind < 0.45:
                P.birch(c, x, y, z, height=5 + int(h3(x, 2, z, 66) * 3), seed=x + z)
            else:
                P.oak(c, x, y, z, height=4 + int(h3(x, 3, z, 67) * 3), r=2.3 + h3(x, 4, z, 68), seed=x + z)
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if h3(x + dx, 0, z + dz, 69) < 0.3 and c.empty(x + dx, y, z + dz):
                    c.set(x + dx, y, z + dz, "fern")
    for (x, z, r) in ((44, 80, 1.8), (66, 86, 2.0), (40, 34, 1.7), (70, 34, 1.9), (62, 96, 1.6)):
        if not blocked(x, z, -2):
            P.boulder(c, x, ground(c, x, z), z, r=r, seed=x)
    for (x, z, a) in ((42, 90, "z"), (64, 76, "z"), (36, 40, "x")):
        if not blocked(x, z, -1):
            P.fallen_log(c, x, ground(c, x, z), z, length=5, axis=a, seed=x)


def ground_cover(c: Canvas):
    deco = Pal(("short_grass", 30), ("fern", 4), ("dandelion", 1), ("poppy", 1), ("azure_bluet", 1), seed=80)
    for x in range(1, SX - 1):
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
                for y in range(1, SY):
                    if not is_full(c.b.get((x, y, z))):
                        c.set(x, y, z, "barrier")


def build():
    c = CANVAS
    c.b.clear()
    terrain(c)
    quarry(c)
    parsonage(c)
    square(c)
    inn(c)
    cottages(c)
    lane(c)
    pasture(c)
    graveyard(c)
    chapel(c)
    kilns(c)
    ravine_props(c)
    woods(c)
    ground_cover(c)
    c.drop_floating()
    border(c)
    c.fix_connections()


def report():
    lines, area = analyze.report(CANVAS, SPAWNS, FLAGS)
    lines = [ln.replace("team 2 flag", "site A").replace("team 3 flag", "site B")
             .replace("team 0", "attackers (spawn 0)").replace("team 1", "defenders (spawn 1)") for ln in lines]
    lines += bomb_distances(CANVAS, SPAWNS, FLAGS, area)
    return lines
