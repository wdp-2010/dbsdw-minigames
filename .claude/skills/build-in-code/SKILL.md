---
name: build-in-code
description: Use when asked to build, rebuild, improve, repair or restyle a Minecraft structure, arena, map, lobby or spawn for the DBSDW servers (paintball arenas, the minigames lobby, the survival spawn), or when an in-game build "looks bad", is "broken", has floating blocks, or must be symmetric for two teams.
---

# Build in code (DBSDW maps, lobby, spawn)

## Overview

Every DBSDW build is a Python module that paints a voxel `Canvas`. It is checked and rendered
offline, then placed on the server in one go. Never hand-place blocks with console `fill`. The
code is the source of truth: to change a build, change the module and redeploy it.

Full background, research and per-map notes: `docs/building-method.md`. Tools: `tools/maps/`
(`mcbuild.py` canvas, palettes, export; `parts.py` trees, tents, towers, sandbags and the like;
`analyze.py` checks; `preview.py` renders; `build.py`; `deploy.sh`).

## The workflow

1. **Read the existing modules first.** `woudfront.py` is the reference style the owner wants
   (forest, log palisades, timber, creek). `duinbasis.py` is the desert one, `basiskamp.py`
   the lobby. Reuse their helpers (`poly_dist`, `rect_dist`, `hall`, `jeep`, `hesco`) and
   `parts.py`. Don't write new versions of them.
2. **Block out as data**: rectangles, paths (polylines), points of interest, spawns. Positions
   go in local coordinates, with world = local + `ORIGIN`.
3. **Terrain**: `height(x, z)` uses noise, flattened near anything built and rising into a border
   belt. The flat world's grass is at y -63, and builds put the ground a few blocks higher
   (arenas G = 8, at -55; lobby G = 4, at -59) so water and dips have depth.
4. **Paint with `Pal` / `Grad`**: no big single-block areas. Darker at the base. Add depth with
   frames, overhangs, posts sticking out and shutters.
5. **Scatter** trees and props on a jittered grid, with a `blocked()` mask around everything
   built, every path and water. Keep the inside sparse and the border dense.
6. **Finish passes, in this order**: `rotate_west_half()` (arenas only) → `drop_floating()` →
   barriers → `fix_connections()`.
7. **Build and look**: `python3 tools/maps/build.py <map>`, then open `out/<map>/iso.png`,
   `top.png` and every `iso_<closeup>.png` (add a `CLOSEUPS` dict). Fix the code and rebuild
   until it looks right. The renders are cheap, so use them instead of in-game screenshots.
8. **Checks must pass**: block ids known in 26.3 (build stops otherwise). For an arena, the
   report covers walkable spawns and flags, no spawn-to-spawn sight, cover at 55–85 % and long
   sightlines at 5–25 %. For a lobby (`LOBBY = True`), every `POINTS` entry must be reachable
   from `SPAWN`, with 0 floor at the edge.
9. **Back up, then deploy** (see the table below). Then **verify on the server**: run
   `execute unless block … run say MISSING` on about 60 random canvas blocks and `data get` the
   entities.
10. **Update what moved**: if spawn, ground height, zones or NPCs changed, update the plugin
    configs on the server and the repo defaults, restart, and check the log. `lobby.txt` lists
    the world positions.
11. **Document** a section in `docs/building-method.md` and copy the previews to
    `docs/superpowers/notes/maps-previews/`.

## Where it goes

| Server | How to place | Notes |
|---|---|---|
| 5, DBSDW Minigames (Paper) `7512ee6f…` | `tools/maps/deploy.sh <map> [--arena]` (datapack functions: clear + strict fills, then `entities` for lobbies) | `tools/console/mcc` for console checks. `--arena` writes the arena yml, which loads at the next start |
| 4, survival test (Folia) `9c30a937…` | BlockPainter: `bp verify <name>`, then `bp apply <name>` (see `~/projects/DBSDW-Folia-Transfer/v1/spawn/README.md`) | Console `fill`/`setblock`/`function` do not work on Folia. Diff against a fresh `anvil.py` snapshot |
| `b69a7f63…` live | never | `mcc` and `deploy.sh` refuse it |

Backup before deploying: copy the region, entities and poi `.mca` files for the area, plus the
configs you will touch, to `/root/server<N>-<what>-backup-<date>/` on node1.

## Look rules (from the owner's feedback)

- Use palettes and gradients. No flat single-block walls or floors.
- **No lone thin posts.** Fences, walls and chains only in lines or with something on them
  (railings, lamp arms, hanging lanterns). Don't use alternating wall posts as a parapet. Use
  chains only where something hangs from them.
- **Nothing floats.** Place props at `top_solid(x, z) + 1` (`y=None` for sandbags), never join
  blocks only at their corners, and run `drop_floating()`.
- Shutters (open trapdoors) have `facing` pointing away from the wall.
- Ruins are shaped: one corner collapsed on a diagonal, with rubble. No random holes.
- Team colours (`team_` prefix → red west, blue east) go on banners and accents, never floors.
- Landmarks: give each area a silhouette (tower, water tower, crane, gate) so players know
  where they are.

## Play rules (arenas)

- Use a 180° rotation (`rotate_west_half`), not a mirror. Anything crossing the centre line is
  built in the west half at both `z` and `sz-1-z`.
- Three lanes (long / mid / close), several routes, cover within 3 blocks of most floor.
- Spawns behind cover, and no sightline from spawn to spawn.
- 1.5 high (block + slab) = shoot over standing, hidden sneaking. 2 high = full cover.

## Symmetry check (arenas)

After editing or repairing a two-team map, every block must equal its rotated partner:
`c.b[(x,y,z)] == rot180(c.b[(sx-1-x, y, sz-1-z)])` with `red_`/`blue_` swapped. Check the
rebuilt canvas against itself. To compare with the world, snapshot the region with `anvil.py`
and diff it against the canvas: differences show in-game edits. Port the wanted edits into the
module's west half and rebuild, never fix the world by hand.

## After a repair

The diff of a fresh world snapshot against the rebuilt canvas must be 0 differences in block
names. Grass↔dirt flips are normal grass spread and can be ignored. Also check symmetry: no
west-half block may differ from its rotated partner.

## Common mistakes

| Mistake | Fix |
|---|---|
| Barrier only above `top_solid` → players walk out under tree canopies at the edge | Fill **every** non-full block in the edge columns with barrier. The lobby report's "floor at the edge" check catches it |
| Ground raised but configs not → spawn, leaderboards, practice zone, dummies stuck 4 blocks too low | Move every y in the configs (server and repo defaults), restart, check the startup log |
| Trees crowd the buildings and the parade ground | Large `blocked()` radius around the centre, low interior density, dense border |
| Water at the canvas edge flows out into the world | End streams in a culvert or pond inside the canvas |
| Entities from the old build stay behind | Tag everything you summon (`dbsdw_lobby`) and kill by tag first. Kill stray animals in the box |
| Game NPC summoned without the plugin tag | Mannequin NBT `BukkitValues:{"dbsdw:game_npc":"<game>"}`, then check it with `data get` |
| Edited in-game, then redeployed → edits lost | Diff the world against the canvas before deploying (`anvil.read_box` on a copied region file, compare block names) and port the wanted edits into an `owner_edits(c)` step that runs before the rotation |
| Carpets hung on air (awnings, shades) pop off at the first block update | Put a `*_slab[type=top]` under every carpet (as `camo_net` does) |
| Plain `lightning_rod` (copper) turns green over days | Use `waxed_lightning_rod` (and waxed copper in general) |
| `kill` on the game NPC does nothing (GameNpc cancels all damage, `/kill` included) | `execute as @e[type=minecraft:mannequin,tag=…] run data merge entity @s {Health:0f}` |
| Old entities survive a redeploy: they load seconds after their chunk | Wait about 10 s after `forceload` before the entities function (deploy.sh does) and recount afterwards |
| Rails turned the wrong way on team 1's side | `rot180` flips rail `shape` (east_west stays valid); check new stateful blocks after rotating |
| Backups overwrite each other (`region/r.0.0.mca` and `entities/r.0.0.mca` share a name) | Keep the `region/`, `entities/`, `poi/` folders in the backup |

## Quick reference

```sh
python3 tools/maps/build.py basiskamp            # build + checks + previews → tools/maps/out/basiskamp/
tools/maps/deploy.sh basiskamp                   # server 5 only
tools/console/mcc "execute in minecraft:overworld run forceload add -56 -56 55 55"
tools/console/mcc --power restart                # after config changes
```
