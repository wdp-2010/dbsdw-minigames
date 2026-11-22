"""Reusable pieces for the arenas. Each takes a Canvas and a position; y is the ground surface
(the block the piece stands on is y - 1)."""
from __future__ import annotations

import math

from mcbuild import Canvas, Pal, h3

LEAF = "[persistent=true]"


def leaves(kind):
    return f"{kind}_leaves{LEAF}"


# --------------------------------------------------------------------------- nature


def oak(c: Canvas, x, y, z, height=5, r=2.6, kind="oak", seed=0):
    """Round broadleaf tree: trunk with a couple of side branches and a lumpy two-blob canopy."""
    log = f"{kind}_log[axis=y]"
    for i in range(height):
        c.set(x, y + i, z, log)
    top = y + height
    leaf = Pal((leaves(kind), 6), (leaves("azalea" if kind == "oak" else kind), 1), seed=seed)
    c.blob(x, top - 0.5, z, r, r * 0.75, r, leaf, rough=0.35, seed=seed)
    ox = 1 if h3(x, 0, z, seed) > 0.5 else -1
    oz = 1 if h3(z, 1, x, seed) > 0.5 else -1
    c.blob(x + ox * r * 0.6, top - 1.2, z + oz * r * 0.5, r * 0.7, r * 0.55, r * 0.7, leaf, rough=0.4, seed=seed + 3)
    # branches poking out of the canopy read as depth
    c.set(x + ox, top - 2, z, f"{kind}_log[axis=x]")
    c.set(x, top - 1, z + oz, f"{kind}_log[axis=z]")
    for i in range(height - 1, height + 1):
        c.set(x, y + i, z, log)


def spruce(c: Canvas, x, y, z, height=9, seed=0):
    """Conifer: stacked rings that shrink upward with a ragged edge."""
    for i in range(height):
        c.set(x, y + i, z, "spruce_log[axis=y]")
    leaf = leaves("spruce")
    ring_start = 2
    for i in range(ring_start, height + 1):
        t = (i - ring_start) / max(1, height - ring_start)
        r = (1 - t) * 3.0 + 0.6
        if (i % 2) == 1:
            r -= 0.8
        c.disc(x, y + i, z, max(0.6, r), leaf,
               cond=lambda a, b, d: c.empty(a, b, d) and h3(a, b, d, seed) > 0.12)
    c.set(x, y + height + 1, z, leaf)


def birch(c: Canvas, x, y, z, height=6, seed=0):
    for i in range(height):
        c.set(x, y + i, z, "birch_log[axis=y]")
    c.blob(x, y + height - 1, z, 1.9, 2.4, 1.9, leaves("birch"), rough=0.35, seed=seed)
    c.set(x, y + height, z, "birch_log[axis=y]")


def bush(c: Canvas, x, y, z, r=1.4, kind="oak", seed=0):
    c.blob(x, y + 0.2, z, r, r * 0.8, r, Pal((leaves(kind), 4), (leaves("azalea"), 1), seed=seed), rough=0.4, seed=seed)


def boulder(c: Canvas, x, y, z, r=2.0, seed=0, mossy=True):
    pal = Pal(("stone", 4), ("andesite", 3), ("cobblestone", 2), ("tuff", 1),
              *((("mossy_cobblestone", 2), ("moss_block", 1)) if mossy else ()), seed=seed)
    c.blob(x, y - 0.6, z, r * 1.1, r * 0.8, r, pal, only_empty=False, rough=0.35, seed=seed)


def fallen_log(c: Canvas, x, y, z, length=6, axis="x", kind="oak", seed=0):
    for i in range(length):
        px, pz = (x + i, z) if axis == "x" else (x, z + i)
        c.set(px, y, pz, f"{kind}_log[axis={axis}]")
        if h3(px, y, pz, seed) < 0.25:
            c.set(px, y + 1, pz, "moss_carpet")


# --------------------------------------------------------------------------- army props

SANDBAG = Pal(("packed_mud", 3), ("mud_bricks", 2), seed=11)
SANDBAG_TOP = "mud_brick_slab[type=bottom]"


def sandbags(c: Canvas, x1, z1, x2, z2, y=None, height=1.5):
    """A sandbag line from (x1,z1) to (x2,z2). height 1 = crouch cover, 1.5 = shoot over, 2 = wall.
    y=None follows the ground under each bag (never floats over a dip)."""
    n = max(abs(x2 - x1), abs(z2 - z1), 1)
    ground = y is None
    for i in range(n + 1):
        t = i / n
        x, z = round(x1 + (x2 - x1) * t), round(z1 + (z2 - z1) * t)
        if ground:
            y = c.top_solid(x, z) + 1
        full = int(height)
        for k in range(full):
            c.set(x, y + k, z, SANDBAG)
        if height - full >= 0.5:
            c.set(x, y + full, z, SANDBAG_TOP)


def crate(c: Canvas, x, y, z, seed=0):
    """Wooden supply crate (1 block) or a barrel."""
    r = h3(x, y, z, seed)
    if r < 0.35:
        c.set(x, y, z, "barrel[facing=up,open=false]")
    elif r < 0.6:
        c.set(x, y, z, "barrel[facing=north,open=false]" if r < 0.48 else "barrel[facing=east,open=false]")
    else:
        c.set(x, y, z, Pal(("spruce_planks", 2), ("stripped_spruce_wood[axis=y]", 1), seed=seed))


def crate_stack(c: Canvas, x, y, z, w=2, d=2, h=2, seed=0):
    for i in range(w):
        for k in range(d):
            hh = h if h3(x + i, y, z + k, seed) > 0.3 else max(1, h - 1)
            for j in range(hh):
                crate(c, x + i, y + j, z + k, seed + j)


def tent(c: Canvas, x, y, z, length=6, facing="x", cloth="green_wool", seed=0):
    """Army A-frame tent, 5 wide, 3 high, open at both ends with poles."""
    # solid A-frame profile (no corner-only joints): X = cloth
    #   y+2  . X X X .
    #   y+1  X X . X X
    #   y    X . . . X
    profile = {-2: (0, 1), -1: (1, 2), 0: (2,), 1: (1, 2), 2: (0, 1)}
    for i in range(length):
        for off, ys in profile.items():
            px, pz = (x + i, z + off) if facing == "x" else (x + off, z + i)
            for j in ys:
                c.set(px, y + j, pz, cloth)
        # floor cloth
        for off in (-1, 0, 1):
            px, pz = (x + i, z + off) if facing == "x" else (x + off, z + i)
            c.set(px, y - 1, pz, "coarse_dirt")
    # ridge pole stubs and ropes at the ends
    for i in (-1, length):
        px, pz = (x + i, z) if facing == "x" else (x, z + i)
        c.set(px, y, pz, "spruce_fence")
        c.set(px, y + 1, pz, "spruce_fence")
        c.set(px, y + 2, pz, "spruce_fence")


def flag_podium(c: Canvas, x, y, z, stone="polished_andesite", trim="stone_brick_stairs"):
    """Raised 3x3 podium the flag stands on (the plugin places the flag itself)."""
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            c.set(x + dx, y - 1, z + dz, "chiseled_stone_bricks" if dx == dz == 0 else stone)
    for dx, dz, f in ((0, -2, "south"), (0, 2, "north"), (-2, 0, "east"), (2, 0, "west")):
        for k in (-1, 0, 1):
            px, pz = (x + dx, z + k) if dx else (x + k, z + dz)
            c.set(px, y - 1, pz, f"{trim}[facing={f},half=bottom]")


def watchtower(c: Canvas, x, y, z, height=7, wood="spruce", ladder_side="west"):
    """4x4 timber tower: corner logs, X braces, a sandbagged platform and a slab roof."""
    log = f"{wood}_log[axis=y]"
    for dx in (0, 3):
        for dz in (0, 3):
            c.column(x + dx, z + dz, y, y + height + 3, log)
    # cross braces on two levels
    for lvl in (1, 4):
        for dx in (1, 2):
            c.set(x + dx, y + lvl, z, f"{wood}_fence")
            c.set(x + dx, y + lvl, z + 3, f"{wood}_fence")
            c.set(x, y + lvl, z + dx, f"{wood}_fence")
            c.set(x + 3, y + lvl, z + dx, f"{wood}_fence")
    # platform (with a ladder hole)
    p = y + height
    c.box(x - 1, p, z - 1, x + 4, p, z + 4, f"{wood}_planks")
    for i in range(-1, 5):
        for (px, pz) in ((x + i, z - 1), (x + i, z + 4), (x - 1, z + i), (x + 4, z + i)):
            c.set(px, p + 1, pz, f"{wood}_fence")
    for (px, pz, f) in ((x + i, z - 1, "south") for i in range(0, 4)):
        c.set(px, p + 1, pz, SANDBAG)
    lx, lz, lf = {"west": (x + 1, z + 1, "east"), "east": (x + 2, z + 2, "west")}[ladder_side]
    c.set(lx, p, lz, "air")
    # ladder against a plank column inside the tower
    bx = lx - 1 if lf == "east" else lx + 1
    for j in range(y, p + 1):
        c.set(bx, j, lz, f"{wood}_planks")
        c.set(lx, j, lz, f"ladder[facing={lf}]")
    # roof
    r = p + 4
    c.box(x - 1, r, z - 1, x + 4, r, z + 4, f"{wood}_slab[type=bottom]")
    c.box(x, r, z, x + 3, r, z + 3, f"{wood}_planks")
    c.set(x + 1, r + 1, z + 1, f"{wood}_slab[type=bottom]")
    c.box(x + 1, r + 1, z + 1, x + 2, r + 1, z + 2, f"{wood}_slab[type=bottom]")
    c.set(x + 1, p + 3, z + 1, "lantern[hanging=true]")


def container(c: Canvas, x, y, z, length=7, axis="x", color="green", doors=True, seed=0):
    """Shipping container: 3 wide, 3 high, ribbed sides (terracotta + concrete stripes),
    iron-bar doors at one end, open inside so players can run through when doors=False."""
    body = f"{color}_terracotta"
    rib = f"{color}_concrete"
    for i in range(length):
        for a in (-1, 0, 1):
            for j in range(3):
                px, pz = (x + i, z + a) if axis == "x" else (x + a, z + i)
                edge = a != 0 or j in (0, 2)
                if edge:
                    c.set(px, y + j, pz, rib if (i % 2 == 0 and a != 0 and j == 1) else body)
                else:
                    c.set(px, y + j, pz, "air")
    ends = (0, length - 1)
    for e in ends:
        for a in (-1, 0, 1):
            for j in range(3):
                px, pz = (x + e, z + a) if axis == "x" else (x + a, z + e)
                if not doors and a == 0 and j < 2:
                    c.set(px, y + j, pz, "air")
                elif e == 0 and doors:
                    c.set(px, y + j, pz, "iron_block" if a == 0 and j == 1 else body)
    # rust spots
    for i in range(length):
        px, pz = (x + i, z - 1) if axis == "x" else (x - 1, z + i)
        if h3(px, y, pz, seed) < 0.12:
            c.set(px, y, pz, "brown_terracotta")


def camo_net(c: Canvas, x1, z1, x2, z2, y, posts=True, seed=0):
    """Camo netting on fence posts: patches of green, olive and brown carpet with holes."""
    pal = Pal(("green_carpet", 4), ("moss_carpet", 3), ("brown_carpet", 2), ("lime_carpet", 1), seed=seed, scale=3)
    for x in range(min(x1, x2), max(x1, x2) + 1):
        for z in range(min(z1, z2), max(z1, z2) + 1):
            if h3(x, y, z, seed) < 0.9:
                c.set(x, y, z, "spruce_slab[type=top]")
                c.set(x, y + 1, z, pal)
    if posts:
        for (px, pz) in ((x1, z1), (x1, z2), (x2, z1), (x2, z2)):
            for j in range(y - 4, y):
                c.set(px, j, pz, "spruce_fence")


def lamp_post(c: Canvas, x, y, z, height=4):
    for j in range(height):
        c.set(x, y + j, z, "spruce_fence")
    c.set(x, y + height, z, "spruce_fence")
    c.set(x, y + height + 1, z, "spruce_slab[type=bottom]")
    c.set(x + 1, y + height, z, "spruce_fence")
    c.set(x + 1, y + height - 1, z, "lantern[hanging=true]")


def palm(c: Canvas, x, y, z, height=7, seed=0):
    """Desert palm: a leaning trunk and drooping fronds."""
    lean = 1 if h3(x, 0, z, seed) > 0.5 else -1
    px = x
    for j in range(height):
        if j == height // 2:
            px += lean
        c.set(px, y + j, z, "jungle_log[axis=y]")
    t = y + height
    c.set(px, t, z, leaves("jungle"))
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
        for k in range(1, 4):
            if abs(dx) + abs(dz) == 2 and k == 3:
                continue
            c.set(px + dx * k, t - (1 if k == 3 else 0), z + dz * k, leaves("jungle"))
