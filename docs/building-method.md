# Building method for arenas

How the paintball arenas are built: in code, checked offline, then placed on the server with
vanilla commands. The tools are in `tools/maps/`; the first two maps (`woudfront`, `duinbasis`)
were made this way on 2026-11-22.

## What good building looks like (research)

Sources are listed at the end. The points that matter for arenas:

**Looks**

1. **Palettes, not single blocks.** A large area of one block reads as flat and fake. Mix 3–5
   blocks of the same colour family with different textures (stone bricks + cracked + andesite,
   sand + sandstone + smooth sandstone).
2. **Gradients.** Order a palette from dark to light and run it bottom to top: darker, dirtier and
   mossier at the base, lighter at the top. Don't skip steps, or you get visible stripes.
3. **Depth.** No flat walls: frames (corner posts, pilasters) that stick out one block, windows and
   doors set back, roofs that overhang, trims of stairs and slabs, beams poking out of walls.
4. **Shape before detail.** Varied heights and silhouettes (gable, tower, chimney, crane) matter
   more than fine detail; too much detail is noise.
5. **Terrain that blends.** Noise heightmaps instead of flat ground, soft material transitions
   (path → coarse dirt → grass, sand → smooth sandstone), banded rock (strata) on cliffs.
6. **Wear and story.** Ruins, missing blocks, rubble, moss, props (crates, barrels, hay, tools)
   placed by rule near walls, never in the main walking lines.

**Play** (multiplayer level design)

1. **Symmetry for fairness.** Both teams get the same map. A 180° rotation (instead of a mirror)
   keeps it fair while each side of the map still looks different from where you stand.
2. **Three lanes** with different ranges: one long-range, one mid, one close-quarters. Several
   routes between any two points so no single chokepoint can be camped.
3. **Cover everywhere, open sightlines rarely.** Cover within a few blocks of most floor; long
   sightlines only on purpose (one lane, a tower), never from spawn to spawn.
4. **Landmarks** so players know where they are (bridge, crane, tower, well).
5. **Hard borders** that look natural (dense trees, cliffs) with barrier blocks behind them; in
   Paintball leaving the bounds is an elimination.
6. **Team colours off large surfaces**: red and blue paint splats must stay readable (plan 6 note).

Minecraft-specific numbers used: a player is 1.8 high (1.5 sneaking), eye height 1.62 (1.27
sneaking). So a 1.5-high wall (block + slab) lets a standing player shoot over it and hides a
sneaking one; 2 high is full cover. A player steps up 1 block and the power-up flood fill drops
at most 3.

## The method

1. **Block-out first.** Write the layout as data: base rectangles, gates, lanes, paths
   (polylines), the centre feature, spawns and flags. Only the west half (team 0); the east half
   is the 180° rotation (`Canvas.rotate_west_half`). Anything that crosses the centre line must be
   built in the west half at both `z` and `sz-1-z` (fords, the well).
2. **Terrain.** `height(x, z)`: value noise, flattened near buildings and lanes, rising at the
   edges into the border. Columns get stone/dirt/sand below and a palette on top.
3. **Pieces.** Reusable parts in `tools/maps/parts.py` (trees, bushes, boulders, sandbags, crates,
   tents, watchtower, containers, camo nets, palms, flag podium), each with depth built in.
4. **Paint.** `Pal` (weighted mix, optionally in patches) and `Grad` (bottom-to-top gradient with a
   noisy edge) are block materials, so any shape can be filled with them. Everything is keyed on
   the position hash, so the same code always gives the same map.
5. **Scatter by rule.** Trees and props on a jittered grid, with masks (no trees on paths, in the
   trench, near buildings, on the creek) and densities per lane.
6. **Fix-ups.** Fences, panes and walls get their connections from their neighbours
   (`fix_connections`), because the fills run in `strict` mode (no block updates: sand doesn't
   fall, leaves don't decay, nothing pops off).
7. **Check offline, every time** (`build.py` prints it and writes `report.txt`):
   - every block id exists in 26.3 (`blocks-26.3.txt`, from the server jar's loot tables);
   - walkable floor from the spawns (the same rules as Paintball's power-up flood fill), both
     teams' spawns on it and both flags reachable;
   - no spawn sees an enemy spawn;
   - share of floor with full cover within 3 blocks (aim 55–85 %);
   - share of far (30+ blocks) floor pairs that see each other (aim 5–25 %).
8. **Look offline.** `iso.png` (isometric, ambient-occluded) and `top.png` (map with spawns in
   team colours and flags in white), plus close-ups. Cheap, so they replace most screenshots.
9. **Place.** `fills()` merges equal blocks into boxes (max 32 768 blocks per fill) and writes
   datapack functions (≤ 12 000 commands each, under `max_command_sequence_length` 65 536).
   `deploy.sh` uploads them, reloads datapacks (and stops if the server rejects a function),
   force-loads the area, clears it and fills it, then writes the arena file.
10. **Verify on the server** with `execute unless block … run say MISSING` on a random sample of
    blocks, then a few real screenshots for the final look.

## Lessons from the owner's first look (2026-11-22)

- **No lone thin posts.** A single wall, fence or chain block with nothing to connect to reads as
  a random stick. Use them only in lines (railings, rails, poles with arms), never as a parapet
  or roof decoration; a block/slab crenellation reads better than alternating wall posts.
- **No chain sculptures.** Chains only where something hangs from them (the crane container, the
  well lantern). Tank traps are concrete teeth (block + slab), not chain crosses.
- **Shutters lie flat against the wall.** An open trapdoor's `facing` must point away from the
  wall (outward), or it sticks out like a fin.
- **Ruins are shaped, not noisy.** Collapse one corner on a diagonal (walls step down, the roof
  over that corner is gone, rubble below) instead of random holes everywhere.
- **Nothing floats.** Props placed at a fixed height float over dips in noisy terrain (the
  sandbags outside Woudfront's gates did). Place props on `top_solid(x, z) + 1` (sandbags with
  `y=None`), never join blocks only by their corners (the first tents did), and let
  `Canvas.drop_floating()` remove any small group that doesn't reach the ground through other blocks.
  Both maps call it after the rotation; anything meant to hang must touch what it hangs from.
- **Team colour where it helps orientation.** Use the `team_` prefix in a block id (for example
  `team_wall_banner`); the rotation turns it into `red_` on team 0's half and `blue_` on team
  1's. Keep it to banners and small accents, never floors.

## Using it

```sh
python3 tools/maps/build.py woudfront          # build, check, previews in tools/maps/out/woudfront/
tools/maps/deploy.sh woudfront --arena         # test server 5 only; the arena loads at the next start
```

A new map is a module in `tools/maps/` with `WORLD`, `ORIGIN`, `CANVAS`, `SPAWNS`, `FLAGS`,
`YAW`, `POWERUPS`, optional `CLOSEUPS`, and a `build()` function. Keep maps apart in the world:
Woudfront is x 300..449, z -50..49; Duinbasis x 300..449, z 100..199 (the lobby is at 0,0 and
`proef` at x 99..162).

## The maps

**Woudfront** (forest, 150 × 100, ground at y -55). Log palisade forts with a walkway behind the
front wall, two gates and a watchtower; a zig-zag trench with plank revetment, duckboards and a
half-high sandbag lip; a meandering creek through the middle with a wooden bridge and two
stepping-stone fords; a burnt-out two-storey stone farmhouse and a hunters' cabin on the flanks;
open meadow (north), mixed wood (centre) and dense forest with boulders (south).

**Duinbasis** (desert, 150 × 100, ground at y -55; second pass the same day: domes, annexes,
a village lookout tower, a gate arch with team banners, street shades, a burnt-out tank, an oasis
in the wadi, dry grass and rocky patches). Hesco-walled compounds with a two-storey HQ
bunker (roof position, antenna, firing slits), a water tank, a vehicle bay under camo netting and
a timber tower; a container yard under a yellow gantry crane with a hanging container; an adobe
village street with roof terraces, protruding roof beams and awnings; a plaza with a well,
market stalls and palms; a dry wadi with a wrecked truck and a footbridge; mesa outcrops and
banded cliffs at the edges; tank traps and sandbag nests in the open ground.

Both: 6 spawns per team behind cover, flag on a podium inside the base, TDM and CTF.

## Sources

- [WesterosCraft: gradients](https://westeroscraft.com/docs/building/fundamentals/gradients)
- [Minecraft.net: make your houses better](https://www.minecraft.net/en-us/article/make-your-houses-better)
- [Sportskeeda: 4 ways to add depth](https://sportskeeda.com/minecraft/4-best-ways-add-depth-minecraft-build),
  [tips to become a better builder](https://sportskeeda.com/minecraft/tips-to-help-become-better-builder-minecraft)
- [Improve your builds (teletype)](https://teletype.in/@minecraftarticles/improve-your-builds)
- [PvP arena design for small servers](https://minecraft.how/blog/post/pvp-arena-design-small-servers)
- [The Mind Studios: multiplayer level design techniques](https://themindstudios.com/blog/multiplayer-level-design-techniques)
- [Game Developer: deathmatch map design, the architecture of flow](https://www.gamedeveloper.com/design/deathmatch-map-design-the-architecture-of-flow)
- [BuiltByBit: Desert Town minigame map (reference layout)](https://builtbybit.com/resources/desert-town-minigame-map-100-x-200.88129/)
- [Wikipedia: Woodsball](https://en.wikipedia.org/wiki/Woodsball)
