"""Gameplay checks on a Canvas, before anything goes to the server.

- walkable area from the spawns, with the same rules as Paintball's WalkableArea (power-ups spawn
  on it), and whether the other team's spawns and flag are reachable;
- spawn safety: can a player standing at a spawn see the enemy spawns or the middle of the map;
- cover: how much of the floor has full cover (2 high) within a few blocks;
- sightlines: how often two random floor spots far apart can see each other;
- build faults: ladders whose foot nobody can reach, and fence/wall posts with a slab or carpet on
  top instead of a full block (a top slab hangs half a block above the post).
"""
from __future__ import annotations

import math
import random
from collections import deque

from mcbuild import Canvas, is_solid, parse


def _open(c: Canvas, x, y, z):
    s = c.b.get((x, y, z))
    if s is None:
        return True
    return not is_solid(s) and "water" not in parse(s)[0]


def _standable(c, x, y, z):
    return is_solid(c.b.get((x, y - 1, z))) and _open(c, x, y, z) and _open(c, x, y + 1, z)


def walkable(c: Canvas, starts):
    seen = set()
    q = deque()
    for (x, y, z) in starts:
        for d in range(4):
            if _standable(c, x, y - d, z):
                seen.add((x, y - d, z))
                q.append((x, y - d, z))
                break
    while q:
        x, y, z = q.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            for step in (0, 1, -1, -2, -3):
                ny = y + step
                if step == 1 and not _open(c, x, y + 2, z):
                    continue
                if 0 <= nx < c.sx and 0 <= nz < c.sz and 0 < ny < c.sy and _standable(c, nx, ny, nz):
                    if step < 0 and not all(_open(c, nx, ny + k, nz) for k in range(2, 2 - step + 1)):
                        continue
                    if (nx, ny, nz) not in seen:
                        seen.add((nx, ny, nz))
                        q.append((nx, ny, nz))
                    break
    return seen


def los(c: Canvas, a, b, step=0.3):
    """Line of sight between two points (floats); paintball-blocking blocks stop it."""
    d = math.dist(a, b)
    n = max(1, int(d / step))
    for i in range(1, n):
        t = i / n
        p = (math.floor(a[0] + (b[0] - a[0]) * t), math.floor(a[1] + (b[1] - a[1]) * t),
             math.floor(a[2] + (b[2] - a[2]) * t))
        if is_solid(c.b.get(p)):
            return False
    return True


def report(c: Canvas, spawns: dict[int, list], flags: dict[int, tuple], seed=5):
    rnd = random.Random(seed)
    lines = []
    starts = [tuple(int(v) for v in s) for t in spawns for s in spawns[t]]
    area = walkable(c, starts)
    lines.append(f"walkable floor cells: {len(area)}")
    for t, lst in spawns.items():
        ok = all(any((int(s[0]), int(s[1]) - d, int(s[2])) in area for d in range(4)) for s in lst)
        lines.append(f"team {t} spawns on walkable floor: {'yes' if ok else 'NO'}")
    for t, f in flags.items():
        ok = any((int(f[0]), int(f[1]) - d, int(f[2])) in area for d in range(4))
        lines.append(f"team {t} flag reachable: {'yes' if ok else 'NO'}")
    # spawn safety
    eye = 1.62
    for t, lst in spawns.items():
        enemy = [s for tt, l2 in spawns.items() if tt != t for s in l2]
        seen = sum(1 for s in lst for e in enemy
                   if los(c, (s[0], s[1] + eye, s[2]), (e[0], e[1] + eye, e[2])))
        lines.append(f"team {t}: spawn pairs with a clear line to an enemy spawn: {seen} of {len(lst) * len(enemy)}")
    # cover: a solid block 2 high within 3 blocks
    cells = list(area)
    sample = rnd.sample(cells, min(3000, len(cells)))
    covered = 0
    for (x, y, z) in sample:
        found = False
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                if (dx or dz) and is_solid(c.b.get((x + dx, y, z + dz))) and is_solid(c.b.get((x + dx, y + 1, z + dz))):
                    found = True
                    break
            if found:
                break
        covered += found
    lines.append(f"floor with full cover within 3 blocks: {100 * covered // max(1, len(sample))}% (aim 55-85)")
    # sightlines
    far, open_far, longest = 0, 0, 0.0
    for _ in range(2500):
        a, b = rnd.sample(cells, 2)
        d = math.dist(a, b)
        if d < 30:
            continue
        far += 1
        if los(c, (a[0] + .5, a[1] + eye, a[2] + .5), (b[0] + .5, b[1] + eye, b[2] + .5)):
            open_far += 1
            longest = max(longest, d)
    lines.append(f"far pairs (30+ blocks) that see each other: {100 * open_far // max(1, far)}% "
                 f"(aim 5-25), longest seen {longest:.0f}")
    lines += build_faults(c, area)
    return lines, area


def build_faults(c: Canvas, area):
    """Ladders that cannot be reached from the walkable floor, and slab/carpet-capped posts."""
    unreachable, capped = [], []
    for (x, y, z), st in c.b.items():
        name = parse(st)[0]
        if name == "ladder" and parse(c.b.get((x, y - 1, z), "air"))[0] != "ladder":
            near = ((x + dx, y + dy, z + dz) for dx, dz in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1))
                    for dy in (-1, 0, 1))
            if not any(p in area for p in near):
                unreachable.append((x, y, z))
        elif name.endswith("_fence") or name.endswith("_wall"):
            above = parse(c.b.get((x, y + 1, z), "air"))[0]
            if above.endswith("_slab") or above.endswith("_carpet"):
                capped.append((x, y, z))
    return [f"ladders nobody can reach: {len(unreachable)} {sorted(unreachable)[:6]}",
            f"fence/wall posts with a slab or carpet on top: {len(capped)} {sorted(capped)[:6]}"]
