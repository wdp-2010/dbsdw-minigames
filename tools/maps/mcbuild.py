"""Voxel canvas for building arenas in code, then exporting them as vanilla /fill commands.

The method (see docs/building-method.md): block out the gameplay first, then paint it with
palettes (weighted block mixes and gradients), add depth with frames, trims and overhangs,
and scatter props by rules. Everything is deterministic: the same seed gives the same map.

Coordinates are local: x 0..sx-1 (west to east), y 0..sy-1 (up), z 0..sz-1 (north to south).
"""
from __future__ import annotations

import math
import re

# --------------------------------------------------------------------------- noise


def h3(x: int, y: int, z: int, seed: int = 0) -> float:
    """Hash of a position: a float in [0, 1), stable across runs."""
    n = (x * 374761393 + y * 668265263 + z * 1442695041 + seed * 2246822519) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    n = (n ^ (n >> 16)) & 0xFFFFFFFF
    return (n & 0xFFFFFF) / 0x1000000


def _smooth(t: float) -> float:
    return t * t * (3 - 2 * t)


def vnoise(x: float, z: float, scale: float, seed: int = 0) -> float:
    """2D value noise in [0, 1), features about `scale` blocks wide."""
    fx, fz = x / scale, z / scale
    ix, iz = math.floor(fx), math.floor(fz)
    tx, tz = _smooth(fx - ix), _smooth(fz - iz)
    a = h3(ix, 0, iz, seed)
    b = h3(ix + 1, 0, iz, seed)
    c = h3(ix, 0, iz + 1, seed)
    d = h3(ix + 1, 0, iz + 1, seed)
    return (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * tz


def fbm(x: float, z: float, scale: float, seed: int = 0, octaves: int = 3) -> float:
    """Layered value noise in about [0, 1)."""
    total, amp, norm = 0.0, 1.0, 0.0
    for o in range(octaves):
        total += vnoise(x, z, scale / (2 ** o), seed + o * 101) * amp
        norm += amp
        amp *= 0.5
    return total / norm


# --------------------------------------------------------------------------- materials


class Pal:
    """A weighted mix of block states, e.g. Pal(("stone_bricks", 5), ("cracked_stone_bricks", 1)).

    The pick depends only on the position (and seed), so a palette is a texture, not randomness
    that changes between runs. `scale` > 1 makes patches instead of single-block noise.
    """

    def __init__(self, *items, seed: int = 0, scale: float = 1.0):
        self.items = [(i, 1.0) if isinstance(i, str) else (i[0], float(i[1])) for i in items]
        self.total = sum(w for _, w in self.items)
        self.seed = seed
        self.scale = scale

    def __call__(self, x, y, z):
        if self.scale > 1:
            r = (vnoise(x + y * 0.7, z - y * 0.3, self.scale, self.seed) * 0.7
                 + h3(x, y, z, self.seed) * 0.3)
        else:
            r = h3(x, y, z, self.seed)
        r *= self.total
        for state, w in self.items:
            r -= w
            if r < 0:
                return state
        return self.items[-1][0]


class Grad:
    """Bottom-to-top gradient over materials between y0 and y1, with a noisy edge.

    Real buildings are darker, dirtier and mossier at the base and lighter at the top; a gradient
    with jitter gives that without visible stripes.
    """

    def __init__(self, layers, y0: int, y1: int, jitter: float = 0.18, seed: int = 0):
        self.layers = layers
        self.y0, self.y1 = y0, y1
        self.jitter = jitter
        self.seed = seed

    def __call__(self, x, y, z):
        t = (y - self.y0) / max(1, self.y1 - self.y0)
        t += (h3(x, y, z, self.seed + 7) - 0.5) * 2 * self.jitter
        i = min(len(self.layers) - 1, max(0, int(t * len(self.layers))))
        return resolve(self.layers[i], x, y, z)


def resolve(m, x, y, z):
    if m is None or isinstance(m, str):
        return m
    return m(x, y, z)


# --------------------------------------------------------------------------- block states

_STATE = re.compile(r"^([a-z0-9_:]+)(?:\[(.*)\])?$")


def parse(state: str):
    m = _STATE.match(state)
    if not m:
        raise ValueError("bad block state " + state)
    props = {}
    if m.group(2):
        for kv in m.group(2).split(","):
            k, v = kv.split("=")
            props[k] = v
    return m.group(1), props


def fmt(name: str, props: dict) -> str:
    if not props:
        return name
    return name + "[" + ",".join(f"{k}={v}" for k, v in props.items()) + "]"


_FLIP = {"north": "south", "south": "north", "east": "west", "west": "east"}


def rot180(state: str) -> str:
    name, props = parse(state)
    if not props:
        return state
    out = {}
    for k, v in props.items():
        if k == "facing" and v in _FLIP:
            out[k] = _FLIP[v]
        elif k == "shape" and any(d in v for d in _FLIP):    # rails; stair shapes are relative
            v2 = "_".join(_FLIP.get(t, t) for t in v.split("_"))
            out[k] = {"west_east": "east_west", "south_north": "north_south"}.get(v2, v2)
        elif k == "rotation":
            out[k] = str((int(v) + 8) % 16)
        elif k in _FLIP:
            out[_FLIP[k]] = v
        else:
            out[k] = v
    # keep the property order stable for nicer commands
    return fmt(name, {k: out[k] for k in sorted(out)})


_NOT_FULL = ("slab", "stairs", "fence", "pane", "wall", "door", "carpet", "ladder", "torch",
             "lantern", "chain", "button", "flower", "short_grass", "tall_grass", "fern",
             "bush", "sapling", "rail", "sign", "banner", "rod", "bars", "vine", "air",
             "barrier", "water", "lever", "plate", "candle", "head", "skull", "pot", "anvil",
             "dandelion", "poppy", "tulip", "daisy", "allium", "orchid", "bluet", "cornflower",
             "lily", "petals", "scaffolding", "dripleaf", "campfire", "bell", "hopper", "grindstone",
             "cauldron", "composter", "lectern", "snow", "path", "farmland", "kelp", "seagrass",
             "pickle", "moss_carpet", "leaf_litter", "cactus", "sugar_cane", "roots", "grate")


def is_full(state: str | None) -> bool:
    """A full, solid cube (good enough for connections, cover and the renderer)."""
    if not state or state == "air":
        return False
    name = parse(state)[0]
    if name.endswith("trapdoor"):
        return False
    return not any(k in name for k in _NOT_FULL)


def is_solid(state: str | None) -> bool:
    """Blocks a paintball or a player (cover). Slabs, stairs, fences and walls count."""
    if not state or state == "air":
        return False
    name = parse(state)[0]
    soft = ("flower", "short_grass", "tall_grass", "fern", "bush", "sapling", "torch", "rail",
            "sign", "banner", "vine", "water", "lever", "plate", "button", "carpet", "dandelion",
            "poppy", "tulip", "daisy", "allium", "orchid", "bluet", "cornflower", "petals",
            "leaf_litter", "lily", "candle", "snow", "kelp", "seagrass", "roots", "sugar_cane",
            "propagule", "hanging_moss")
    if name == "barrier":
        return True
    return not any(k in name for k in soft)


def _family(name: str) -> str | None:
    if name.endswith("_fence") and "nether" not in name:
        return "fence"
    if name.endswith("_fence_gate"):
        return "gate"
    if name.endswith("pane") or name == "iron_bars":
        return "pane"
    if name.endswith("_wall"):
        return "wall"
    return None


# --------------------------------------------------------------------------- canvas


TEAM = "team_"  # in a block id: red_ on the west half (team 0), blue_ on the east half (team 1)


class Canvas:
    def __init__(self, sx: int, sy: int, sz: int, seed: int = 1):
        self.sx, self.sy, self.sz = sx, sy, sz
        self.seed = seed
        self.b: dict[tuple[int, int, int], str] = {}

    # basic access
    def inside(self, x, y, z):
        return 0 <= x < self.sx and 0 <= y < self.sy and 0 <= z < self.sz

    def set(self, x, y, z, m):
        if not self.inside(x, y, z):
            return
        s = resolve(m, x, y, z)
        if s is None:
            return
        if s == "air":
            self.b.pop((x, y, z), None)
        else:
            self.b[(x, y, z)] = s

    def get(self, x, y, z) -> str:
        return self.b.get((x, y, z), "air")

    def empty(self, x, y, z) -> bool:
        return (x, y, z) not in self.b

    def top(self, x, z, below: int | None = None) -> int:
        """Highest y with a block (or -1)."""
        y = (self.sy - 1) if below is None else below
        while y >= 0 and (x, y, z) not in self.b:
            y -= 1
        return y

    def top_solid(self, x, z) -> int:
        y = self.sy - 1
        while y >= 0 and not is_full(self.b.get((x, y, z))):
            y -= 1
        return y

    # shapes (all bounds inclusive, any corner order)
    def box(self, x1, y1, z1, x2, y2, z2, m):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.set(x, y, z, m)

    def walls(self, x1, y1, z1, x2, y2, z2, m):
        xa, xb, za, zb = min(x1, x2), max(x1, x2), min(z1, z2), max(z1, z2)
        for y in range(min(y1, y2), max(y1, y2) + 1):
            for x in range(xa, xb + 1):
                self.set(x, y, za, m)
                self.set(x, y, zb, m)
            for z in range(za, zb + 1):
                self.set(xa, y, z, m)
                self.set(xb, y, z, m)

    def line(self, x1, y1, z1, x2, y2, z2, m):
        n = max(abs(x2 - x1), abs(y2 - y1), abs(z2 - z1), 1)
        for i in range(n + 1):
            t = i / n
            self.set(round(x1 + (x2 - x1) * t), round(y1 + (y2 - y1) * t), round(z1 + (z2 - z1) * t), m)

    def disc(self, cx, y, cz, r, m, cond=None):
        ri = int(math.ceil(r))
        for x in range(cx - ri, cx + ri + 1):
            for z in range(cz - ri, cz + ri + 1):
                if (x - cx) ** 2 + (z - cz) ** 2 <= r * r + 0.5 and (cond is None or cond(x, y, z)):
                    self.set(x, y, z, m)

    def blob(self, cx, cy, cz, rx, ry, rz, m, only_empty=True, rough=0.25, seed=0):
        """A noisy ellipsoid: canopies, bushes, boulders."""
        for x in range(int(cx - rx - 1), int(cx + rx + 2)):
            for y in range(int(cy - ry - 1), int(cy + ry + 2)):
                for z in range(int(cz - rz - 1), int(cz + rz + 2)):
                    d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 + ((z - cz) / rz) ** 2
                    if d <= 1 + (h3(x, y, z, seed) - 0.5) * 2 * rough:
                        if not only_empty or self.empty(x, y, z):
                            self.set(x, y, z, m)

    def column(self, x, z, y1, y2, m):
        for y in range(y1, y2 + 1):
            self.set(x, y, z, m)

    # whole-canvas passes
    def rotate_west_half(self):
        """Make the map 180-degree rotationally symmetric: the east half becomes the west half
        turned around the centre. Each team gets the same map, but it does not look mirrored.
        Block ids starting team_ become red_ (west, team 0) or blue_ (east, team 1)."""
        half = self.sx // 2
        west = {k: v for k, v in self.b.items() if k[0] < half}
        self.b = {k: v.replace(TEAM, "red_") for k, v in west.items()}
        for (x, y, z), s in west.items():
            self.b[(self.sx - 1 - x, y, self.sz - 1 - z)] = rot180(s).replace(TEAM, "blue_")

    def drop_floating(self, max_size=60, keep=("barrier",)):
        """Remove small groups of blocks that do not touch the ground through other blocks
        (sandbags over a dip, loose bits of a ruin, stray leaves). Returns how many groups."""
        from collections import deque
        b = {k: v for k, v in self.b.items() if not any(s in v for s in keep)}
        steps = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
        seen = {k for k in b if k[1] == 0}
        q = deque(seen)
        while q:
            x, y, z = q.popleft()
            for dx, dy, dz in steps:
                n = (x + dx, y + dy, z + dz)
                if n in b and n not in seen:
                    seen.add(n)
                    q.append(n)
        dropped = 0
        done = set()
        for k in b:
            if k in seen or k in done:
                continue
            group = [k]
            done.add(k)
            i = 0
            while i < len(group):
                x, y, z = group[i]
                i += 1
                for dx, dy, dz in steps:
                    n = (x + dx, y + dy, z + dz)
                    if n in b and n not in seen and n not in done:
                        done.add(n)
                        group.append(n)
            if len(group) <= max_size:
                for p in group:
                    self.b.pop(p, None)
                dropped += 1
        return dropped

    def fix_connections(self):
        """Fences, panes and walls get their side states from their neighbours (fills run in
        strict mode, so the game will not do it)."""
        dirs = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
        for (x, y, z), s in list(self.b.items()):
            name, props = parse(s)
            fam = _family(name)
            if fam is None or fam == "gate":
                continue
            out = {k: v for k, v in props.items() if k not in dirs and k != "up"}
            links = {}
            for d, (dx, dz) in dirs.items():
                n = self.b.get((x + dx, y, z + dz))
                ok = False
                if n:
                    nn, np_ = parse(n)
                    nf = _family(nn)
                    if fam == "fence":
                        ok = nf == "fence" or is_full(n) or (nf == "gate" and _gate_links(np_, d))
                    elif fam == "pane":
                        ok = nf in ("pane", "wall") or is_full(n)
                    else:
                        ok = nf in ("wall", "pane") or is_full(n) or (nf == "gate" and _gate_links(np_, d))
                links[d] = ok
            if fam == "wall":
                above = self.b.get((x, y + 1, z))
                tall = is_full(above) or (above is not None and _family(parse(above)[0]) == "wall")
                for d in dirs:
                    out[d] = ("tall" if tall else "low") if links[d] else "none"
                straight = (links["north"] and links["south"] and not links["east"] and not links["west"]) or \
                           (links["east"] and links["west"] and not links["north"] and not links["south"])
                out["up"] = "false" if straight and not tall else "true"
            else:
                for d in dirs:
                    out[d] = "true" if links[d] else "false"
            self.b[(x, y, z)] = fmt(name, {k: out[k] for k in sorted(out)})


def _gate_links(props, d):
    f = props.get("facing", "north")
    return (f in ("north", "south")) == (d in ("east", "west"))


# --------------------------------------------------------------------------- export

FILL_LIMIT = 32768


def fills(canvas: Canvas, origin: tuple[int, int, int]):
    """Greedy box merge of equal blocks into /fill commands (strict: no block updates)."""
    ox, oy, oz = origin
    b = canvas.b
    done = set()
    cmds = []
    # place full blocks first so attached blocks (torches, ladders, carpets) have support
    order = sorted(b.keys(), key=lambda k: (0 if is_full(b[k]) else 1, k[1], k[2], k[0]))
    for (x, y, z) in order:
        if (x, y, z) in done:
            continue
        s = b[(x, y, z)]

        def free(px, py, pz):
            return (px, py, pz) not in done and b.get((px, py, pz)) == s

        x2 = x
        while free(x2 + 1, y, z) and (x2 + 2 - x) <= FILL_LIMIT:
            x2 += 1
        w = x2 - x + 1
        z2 = z
        while w * (z2 - z + 2) <= FILL_LIMIT and all(free(i, y, z2 + 1) for i in range(x, x2 + 1)):
            z2 += 1
        d = z2 - z + 1
        y2 = y
        while w * d * (y2 - y + 2) <= FILL_LIMIT and all(
                free(i, y2 + 1, k) for i in range(x, x2 + 1) for k in range(z, z2 + 1)):
            y2 += 1
        for i in range(x, x2 + 1):
            for j in range(y, y2 + 1):
                for k in range(z, z2 + 1):
                    done.add((i, j, k))
        a = f"{ox + x} {oy + y} {oz + z}"
        if (x, y, z) == (x2, y2, z2):
            cmds.append(f"setblock {a} {s} strict")
        else:
            cmds.append(f"fill {a} {ox + x2} {oy + y2} {oz + z2} {s} strict")
    return cmds


def clear_cmds(canvas: Canvas, origin, ymin=0):
    ox, oy, oz = origin
    out = []
    for x in range(0, canvas.sx, 32):
        for z in range(0, canvas.sz, 32):
            for y in range(ymin, canvas.sy, 32):
                out.append(f"fill {ox + x} {oy + y} {oz + z} {ox + min(canvas.sx, x + 32) - 1} "
                           f"{oy + min(canvas.sy, y + 32) - 1} {oz + min(canvas.sz, z + 32) - 1} air strict")
    return out
