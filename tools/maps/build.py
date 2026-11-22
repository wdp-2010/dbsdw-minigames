"""Build an arena: python3 tools/maps/build.py <map> [--no-preview] [--no-report]

Writes to tools/maps/out/<map>/:
  datapack/dbsdw_maps/...   functions dbsdw_maps:<map>/build_<n> (clear + fill, strict)
  <map>.yml                 MinigameCore arena file (world coordinates)
  iso.png, top.png          previews; report.txt gameplay checks
Run the functions with tools/maps/deploy.sh <map>.
Block ids are checked against blocks-26.3.txt (every block with a loot table in the 26.3 server jar).
"""
from __future__ import annotations

import importlib
import json
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import analyze  # noqa: E402
import preview  # noqa: E402
from mcbuild import clear_cmds, fills, parse  # noqa: E402

PER_FUNCTION = 12000
DATA_FORMAT = [121, 0]


def arena_yaml(m, origin):
    ox, oy, oz = origin
    c = m.CANVAS

    def pos(p, yaw):
        return f"{{x: {ox + p[0]}, y: {oy + p[1]}.0, z: {oz + p[2]}, yaw: {yaw}, pitch: 0.0}}"

    out = [f"world: {m.WORLD}", "modes: [paintball:tdm, paintball:ctf]",
           f"corner1: {{x: {ox}, y: {oy - 1}, z: {oz}}}",
           f"corner2: {{x: {ox + c.sx - 1}, y: {oy + c.sy - 1}, z: {oz + c.sz - 1}}}", "spawns:"]
    for t, lst in m.SPAWNS.items():
        out.append(f'  "{t}": [' + ", ".join(pos(p, m.YAW[t]) for p in lst) + "]")
    out.append("flags:")
    for t, p in m.FLAGS.items():
        out.append(f'  "{t}": ' + pos(p, m.YAW[t]))
    out.append("powerups: [" + ", ".join(pos(p, 0.0) for p in m.POWERUPS) + "]")
    return "\n".join(out) + "\n"


def main():
    name = sys.argv[1]
    t0 = time.time()
    m = importlib.import_module(name)
    m.build()
    c = m.CANVAS
    print(f"built {name}: {len(c.b)} blocks in {time.time() - t0:.1f}s")
    here = os.path.dirname(os.path.abspath(__file__))
    with open(os.path.join(here, "blocks-26.3.txt")) as f:
        known = set(f.read().split()) | {"air", "water", "lava", "barrier", "light", "cave_air"}
    unknown = sorted(n for n in {parse(s)[0] for s in c.b.values()}
                     if n not in known and n.replace("_wall_", "_") not in known)
    if unknown:
        sys.exit(f"unknown block ids for 26.3: {', '.join(unknown)}")
    out = os.path.join(here, "out", name)
    fdir = os.path.join(out, "datapack", "dbsdw_maps", "data", "dbsdw_maps", "function", name)
    os.makedirs(fdir, exist_ok=True)
    for f in os.listdir(fdir):
        os.remove(os.path.join(fdir, f))
    with open(os.path.join(out, "datapack", "dbsdw_maps", "pack.mcmeta"), "w") as f:
        json.dump({"pack": {"description": "DBSDW arena builds", "min_format": DATA_FORMAT,
                            "max_format": DATA_FORMAT}}, f, indent=2)
    cmds = clear_cmds(c, m.ORIGIN) + fills(c, m.ORIGIN)
    parts = [cmds[i:i + PER_FUNCTION] for i in range(0, len(cmds), PER_FUNCTION)]
    for i, p in enumerate(parts):
        with open(os.path.join(fdir, f"build_{i}.mcfunction"), "w") as f:
            f.write("\n".join(p) + "\n")
    ox, oy, oz = m.ORIGIN
    with open(os.path.join(fdir, "forceload.mcfunction"), "w") as f:
        f.write(f"forceload add {ox} {oz} {ox + c.sx - 1} {oz + c.sz - 1}\n")
    with open(os.path.join(fdir, "unload.mcfunction"), "w") as f:
        f.write(f"forceload remove {ox} {oz} {ox + c.sx - 1} {oz + c.sz - 1}\n")
    with open(os.path.join(out, "parts.txt"), "w") as f:
        f.write(f"{len(parts)}\n")
    with open(os.path.join(out, f"{name}.yml"), "w") as f:
        f.write(arena_yaml(m, m.ORIGIN))
    print(f"{len(cmds)} commands in {len(parts)} functions")
    if "--no-report" not in sys.argv:
        lines, _ = analyze.report(c, m.SPAWNS, m.FLAGS)
        with open(os.path.join(out, "report.txt"), "w") as f:
            f.write("\n".join(lines) + "\n")
        print("\n".join(lines))
    if "--no-preview" not in sys.argv:
        preview.iso(c, os.path.join(out, "iso.png"))
        marks = [(p[0], p[2], (230, 40, 40) if t == 0 else (40, 80, 230)) for t, l in m.SPAWNS.items() for p in l]
        marks += [(p[0], p[2], (255, 255, 255)) for p in m.FLAGS.values()]
        preview.topdown(c, os.path.join(out, "top.png"), marks=marks)
        for label, box in getattr(m, "CLOSEUPS", {}).items():
            preview.iso(c, os.path.join(out, f"iso_{label}.png"), scale=2, box=box)
        print(f"previews in {out} ({time.time() - t0:.1f}s)")


if __name__ == "__main__":
    main()
