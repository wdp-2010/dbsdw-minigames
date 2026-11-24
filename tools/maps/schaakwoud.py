"""Schaakwoud: the chess template arena. A giant board (3x3 blocks a square) on a timber plinth in a
forest clearing, with a raised timber stand for each player behind their side of the board.

MinigameCore never plays on this template: with `copies: 5` it builds a fresh copy east of it for each
game and removes it afterwards (see README, "Lobby and arenas").

Layout (local x east, z south): the board covers x 12..35, z 12..35. White's stand is north (low z)
and looks south; black's is south and looks north. Seen from white, the a-file is on the left, which is
east (+x), so a1 is the north-east corner square. Arena flag 0 marks the centre of a1 and flag 1 the
centre of h8 on the board's top surface; the plugin works out the board from those two and white's
spawn.
"""
from __future__ import annotations

import parts as P
from mcbuild import Canvas, Pal, h3

WORLD = "world"
SX, SY, SZ = 48, 26, 48
ORIGIN = (3000, -63, 3000)  # far from the lobby and paintball maps; copies go east every 512 blocks
G = 1                      # ground top in local y (local 0 is the flat world's grass)
SEED = 64
COPIES = 5
MODES = ["chess:snel", "chess:normaal", "chess:vrij"]

CANVAS = Canvas(SX, SY, SZ, SEED)

BX, BZ = 12, 12            # board's north-west block
SQ = 3                     # blocks per square
TOP = G + 2                # board top surface (squares are the blocks at y = G + 1)
STAND = G + 14             # stand floor block; players stand one above
CX = BX + 4 * SQ           # board centre line (x = 24.0 is between files d and e)

SPAWNS = {0: [(CX + 0.0, STAND + 1, 6.5)], 1: [(CX + 0.0, STAND + 1, SZ - 6.5)]}
YAW = {0: 0.0, 1: 180.0}
PITCH = 50.0


def square_origin(f: int, r: int) -> tuple[int, int]:
    """North-west block of square (file 0..7 = a..h, rank 0..7 = 1..8)."""
    return BX + (7 - f) * SQ, BZ + r * SQ


def square_centre(f: int, r: int) -> tuple[float, float]:
    x, z = square_origin(f, r)
    return x + SQ / 2, z + SQ / 2


A1 = square_centre(0, 0)
H8 = square_centre(7, 7)
FLAGS = {0: (A1[0], TOP, A1[1]), 1: (H8[0], TOP, H8[1])}
POWERUPS: list = []

LIGHT = Pal(("birch_planks", 6), ("stripped_birch_wood", 1), seed=SEED, scale=2)
DARK = Pal(("dark_oak_planks", 6), ("stripped_dark_oak_wood", 1), seed=SEED + 1, scale=2)
GRASS = Pal(("grass_block", 12), ("moss_block", 2), ("podzol", 1), seed=SEED + 2, scale=3)
PATH = Pal(("coarse_dirt", 3), ("dirt_path", 4), ("rooted_dirt", 1), seed=SEED + 3, scale=2)


def ground():
    c = CANVAS
    for x in range(SX):
        for z in range(SZ):
            edge = min(x, z, SX - 1 - x, SZ - 1 - z)
            h = G if edge >= 2 else 0
            for y in range(h):
                c.set(x, y, z, "dirt")
            c.set(x, h, z, GRASS)
    # A walked path around the plinth.
    for x in range(BX - 3, BX + 8 * SQ + 3):
        for z in range(BZ - 3, BZ + 8 * SQ + 3):
            if not (BX - 1 <= x < BX + 8 * SQ + 1 and BZ - 1 <= z < BZ + 8 * SQ + 1):
                c.set(x, G, z, PATH)


def board():
    c = CANVAS
    for f in range(8):
        for r in range(8):
            x0, z0 = square_origin(f, r)
            dark = (f + r) % 2 == 0          # a1 is dark
            c.box(x0, G + 1, z0, x0 + SQ - 1, G + 1, z0 + SQ - 1, DARK if dark else LIGHT)
            c.box(x0, G, z0, x0 + SQ - 1, G, z0 + SQ - 1, "spruce_planks")
    x1, z1 = BX - 1, BZ - 1
    x2, z2 = BX + 8 * SQ, BZ + 8 * SQ
    # Frame of logs and a slab step around it.
    for x in range(x1, x2 + 1):
        c.set(x, G + 1, z1, "stripped_spruce_log[axis=x]")
        c.set(x, G + 1, z2, "stripped_spruce_log[axis=x]")
    for z in range(z1, z2 + 1):
        c.set(x1, G + 1, z, "stripped_spruce_log[axis=z]")
        c.set(x2, G + 1, z, "stripped_spruce_log[axis=z]")
    for x in range(x1 - 1, x2 + 2):
        for z in (z1 - 1, z2 + 1):
            c.set(x, G + 1, z, "spruce_slab[type=bottom]")
    for z in range(z1 - 1, z2 + 2):
        for x in (x1 - 1, x2 + 1):
            c.set(x, G + 1, z, "spruce_slab[type=bottom]")
    # Corner posts: bridge() raises them to the walkway and puts the lanterns on top.


def stand(north: bool):
    """A timber stand: posts, braces, a fenced floor, a half roof at the back and a ladder."""
    c = CANVAS
    z_front, z_back = (8, 3) if north else (SZ - 9, SZ - 4)
    za, zb = min(z_front, z_back), max(z_front, z_back)
    xa, xb = CX - 7, CX + 6
    step = -1 if north else 1                     # from the front towards the back
    c.box(xa, STAND, za, xb, STAND, zb, "spruce_planks")
    # The front two rows are barriers: solid to stand on but invisible, so looking steeply down at the
    # near ranks nothing (no glass frame or streaks) is in the way.
    for x in range(xa + 1, xb):
        for k in (0, 1):
            c.set(x, STAND, z_front + k * step, "barrier")
    # Posts at the corners and the middle, braced with stripped logs halfway up. The front posts stand
    # under the last plank row: the two barrier rows in front of it show nothing for a post to carry.
    z_edge = z_front + 2 * step
    pa, pb = min(z_edge, z_back), max(z_edge, z_back)
    for x in (xa, (xa + xb) // 2, xb):
        for z in (pa, pb):
            c.box(x, G + 1, z, x, STAND - 1, z, "spruce_log[axis=y]")
    for z in (pa, pb):
        c.box(xa, G + 7, z, xb, G + 7, z, "stripped_spruce_log[axis=x]")
    for x in (xa, xb):
        c.box(x, G + 7, pa, x, G + 7, pb, "stripped_spruce_log[axis=z]")
    # Railing all round (it is a long way down), lanterns on the front corners.
    for x in range(xa, xb + 1):
        c.set(x, STAND + 1, z_back, "spruce_fence")
        c.set(x, STAND + 1, z_front, "barrier" if xa < x < xb else "spruce_fence")
    for z in range(za, zb + 1):
        c.set(xa, STAND + 1, z, "spruce_fence")
        c.set(xb, STAND + 1, z, "spruce_fence")
    for x in (xa, xb):
        c.set(x, STAND + 2, z_front, "lantern[hanging=false]")
    # Half roof over the back of the stand: posts and a slab roof with a trim.
    roof = STAND + 5
    for x in (xa, xb):
        c.box(x, STAND + 1, z_back, x, roof - 1, z_back, "spruce_log[axis=y]")
        c.box(x, STAND + 1, z_back - 2 * step, x, roof - 1, z_back - 2 * step, "spruce_log[axis=y]")
    for x in range(xa - 1, xb + 2):
        for k in range(-1, 4):
            c.set(x, roof, z_back - k * step, "dark_oak_slab[type=bottom]")
        c.set(x, roof - 1, z_back + step, "spruce_trapdoor[half=top,open=false,facing=north]")
    c.set((xa + xb) // 2, roof - 1, z_back - step, "lantern[hanging=true]")
    # Ladder up the back, on a log column just behind the stand.
    lz = z_back + step
    c.box(CX, G + 1, lz, CX, STAND, lz, "spruce_log[axis=y]")
    c.set(CX, STAND, lz, "spruce_planks")
    facing = "north" if north else "south"
    c.box(CX, G + 1, lz + step, CX, STAND + 1, lz + step, f"ladder[facing={facing}]")
    # The railing stays closed over the ladder: players start on the stand and walk the bridge, they
    # never need to climb down, and nobody can fall through the gap.


def bridge():
    """An invisible walkway at stand height over the whole board, from one stand to the other.

    Barrier floor (x over the board frame, z between the stands' fronts) with a barrier wall two blocks
    high on its open edges (one block can be jumped onto). The stands' front railings go, so you walk
    straight on. The board's corner posts rise to the walkway and carry its lanterns.
    """
    c = CANVAS
    x1, x2 = BX - 1, BX + 8 * SQ          # board frame lines
    zn, zs = 9, SZ - 10                    # first rows past the north and south stand fronts
    for x in range(x1, x2 + 1):
        for z in range(zn, zs + 1):
            c.set(x, STAND, z, "barrier")
    xa, xb = CX - 7, CX + 6               # stand width
    # Stand front railings open onto the walkway (the corner fences and lanterns stay).
    for z_front in (8, SZ - 9):
        for x in range(xa + 1, xb):
            c.set(x, STAND + 1, z_front, "air")
    wall = []
    for z in range(zn, zs + 1):
        wall += [(x1, z), (x2, z)]
    for x in range(x1, x2 + 1):
        if not xa <= x <= xb:              # beside the stands the walkway ends in the open
            wall += [(x, zn), (x, zs)]
    for x, z in wall:
        c.set(x, STAND + 1, z, "barrier")
        c.set(x, STAND + 2, z, "barrier")
    # Corner posts of the board, up to the walkway, lanterns on top.
    for x, z in ((x1, BZ - 1), (x2, BZ - 1), (x1, BZ + 8 * SQ), (x2, BZ + 8 * SQ)):
        c.box(x, G + 1, z, x, STAND + 2, z, "spruce_log[axis=y]")
        c.set(x, STAND + 3, z, "lantern[hanging=false]")


def forest():
    """Trees round the clearing, kept off the board, the stands and the path."""
    c = CANVAS
    placed = 0
    for gx in range(1, SX, 5):
        for gz in range(1, SZ, 5):
            x = gx + int(h3(gx, 0, gz, SEED) * 3)
            z = gz + int(h3(gz, 1, gx, SEED) * 3)
            if not (0 <= x < SX and 0 <= z < SZ):
                continue
            inner = BX - 5 <= x <= BX + 8 * SQ + 4 and BZ - 5 <= z <= BZ + 8 * SQ + 4
            stands = CX - 10 <= x <= CX + 9 and (z <= 11 or z >= SZ - 12)
            if inner or stands:
                continue
            y = c.top(x, z) + 1
            kind = h3(x, 2, z, SEED)
            s = SEED + placed
            if kind < 0.45:
                P.spruce(c, x, y, z, height=8 + int(h3(x, 3, z, SEED) * 4), seed=s)
            elif kind < 0.75:
                P.oak(c, x, y, z, height=5 + int(h3(x, 4, z, SEED) * 2), seed=s)
            else:
                P.birch(c, x, y, z, height=6, seed=s)
            placed += 1
    # Undergrowth: ferns and bushes in the gaps.
    for x in range(SX):
        for z in range(SZ):
            inner = BX - 4 <= x <= BX + 8 * SQ + 3 and BZ - 4 <= z <= BZ + 8 * SQ + 3
            stands = CX - 9 <= x <= CX + 8 and (z <= 10 or z >= SZ - 11)
            if inner or stands:
                continue
            y = c.top(x, z)
            if c.get(x, y, z).startswith(("grass_block", "moss_block", "podzol")) and h3(x, 5, z, SEED) > 0.82:
                c.set(x, y + 1, z, "fern" if h3(x, 6, z, SEED) > 0.5 else "short_grass")


def build():
    ground()
    board()
    stand(north=True)
    stand(north=False)
    bridge()
    forest()


def arena_yaml(origin) -> str:
    """The MinigameCore arena file: a template with copies, chess modes, flags 0/1 = a1/h8 centres."""
    ox, oy, oz = origin

    def pos(p, yaw, pitch=0.0):
        return f"{{x: {ox + p[0]}, y: {oy + p[1]}.0, z: {oz + p[2]}, yaw: {yaw}, pitch: {pitch}}}"

    out = [f"world: {WORLD}", "modes: [" + ", ".join(MODES) + "]",
           f"corner1: {{x: {ox}, y: {oy}, z: {oz}}}",
           f"corner2: {{x: {ox + SX - 1}, y: {oy + SY - 1}, z: {oz + SZ - 1}}}", "spawns:"]
    for t, lst in SPAWNS.items():
        out.append(f'  "{t}": [' + ", ".join(pos(p, YAW[t], PITCH) for p in lst) + "]")
    out.append("flags:")
    for t, p in FLAGS.items():
        out.append(f'  "{t}": ' + pos(p, 0.0))
    out.append("powerups: []")
    out.append(f"copies: {COPIES}")
    return "\n".join(out) + "\n"
