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

- **Carpets need a block under them.** Duinbasis's street shades were carpets on air; a block
  update popped one shade off in-game. Every carpet now sits on a top slab, as the camo nets do.
- **Wax the copper.** Plain lightning rods oxidise to green within hours; use `waxed_lightning_rod`.
- **Edits made in-game go into the code.** Diff the world against the canvas (`anvil.read_box` on a
  copied region file), keep the intended edits in an `owner_edits(c)` step before the rotation so
  team 1 gets them too, and restore everything else by redeploying. Duinbasis has one (2026-11-22).

## Using it

```sh
python3 tools/maps/build.py woudfront          # build, check, previews in tools/maps/out/woudfront/
tools/maps/deploy.sh woudfront --arena         # test server 5 only; the arena loads at the next start
```

The lobby is built the same way: `python3 tools/maps/build.py basiskamp`, then
`tools/maps/deploy.sh basiskamp`. A module with `LOBBY = True` skips the arena file and the
gameplay report; instead `build.py` writes `entities.mcfunction` (signs as text displays, the game
NPC as a Mannequin with the `dbsdw:game_npc` tag, removal of stray animals), `lobby.txt` (the
world positions the plugins' configs need) and a lobby report (every point of interest reachable
on foot from the spawn, no floor at the edge of the build). `deploy.sh` runs the entities function
after the fills.

A new map is a module in `tools/maps/` with `WORLD`, `ORIGIN`, `CANVAS`, `SPAWNS`, `FLAGS`,
`YAW`, `POWERUPS`, optional `CLOSEUPS`, and a `build()` function. Keep maps apart in the world:
Woudfront is x 300..449, z -50..49; Duinbasis x 300..449, z 100..199; Schaakwoud x 300..347,
z 300..347; Moerasdorp x 300..449, z 400..499 (the lobby is at 0,0 and `proef` at x 99..162).

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

Third pass (2026-11-22, evening): the owner's in-game edits were ported into `owner_edits()`
(a supply cache dug into the south cliff off the wadi footbridge: tunnel, chests, barrels, a short
rail track, a sandstone roof; sugar cane at both oases; an opened doorway in a timber screen; a
removed roof slab; one floor tile), so both teams have them. A strip torn out of the dune south of
the centre and a loose sand pile were restored, the street shades rebuilt on slabs, and the
copper waxed. After the redeploy a fresh world snapshot equals the code (0 differences) and the
map is exactly 180° symmetric. Backup of the world before: node1
`/root/server5-duinbasis-backup-20261122/`.

**Moerasdorp** (marsh, 2026-11-23, 150 × 100 at x 300..449, z 400..499, water surface at y -55,
low land at y -54). Asked for as a third paintball arena next to Duinbasis, with "mooie palen".
A fishing village on stilts: each team's raised deck stands on a grid of piles over its island
(longhouse, watchtower, flag podium, a mud-brick breastwork with team banners along the front,
stairs down on three sides, a boat shed under the deck); boardwalks on piles run through the
marsh past two stilt houses (timber frame, mud daub, shutters, porch with railings) to a covered
fish hall that stands over the channel in the middle, with half walls, counters, jetties with
boats and a lantern turret on its ridge as the landmark. North lane: open reed marsh with a
fishing hut on tall stilts, peat stacks, upturned boats and a half-sunk wreck (long range).
South lane: mangrove forest with prop roots and a collapsed stilt house (close range). The
channel is crossed by the hall and by a footbridge in each outer lane; elsewhere players wade
(1 deep) or swim. Willows with hanging (pale) moss, sugar cane, lily pads and firefly bushes.

The piles follow how real stilt houses are built (diagonal and knee bracing, a girder on top of
each pile row, piles protected where they meet the water): every pile is a log running from below
the mud through the deck, dark oak where it is wet and spruce above, on a mossy stone pad on land
and with mangrove roots at its foot in the water; a girder ring under each deck with knee braces
(upside-down stairs leaning on the pile); on tall houses a wale halfway down the long sides and
two-stair diagonal braces in a chevron on the short sides. Where a pile reaches the deck edge it
comes through as a railing post; fences only run between posts, and a railing that stops at a
stair opening ends in a log post with a lantern, never in a bare fence (owner: a fence used as
a pole gets a full block on top). Report: walkable floor 10 970, no spawn sees an enemy spawn,
cover 73 %, far sightlines 6 %. Deployed to server 5 on 2026-11-23 (80 random blocks checked in
the world, all present); backup of the region before: node1 `/root/server5-moerasdorp-backup-20261123/`.

All three: 6 spawns per team behind cover, flag on a podium inside the base, TDM and CTF.

**Zaagtoren** (Valvloer, 2026-11-23, 72 × 72 at x 500..571, z -50..21, ground at y -55). An
old sawmill's drying tower in a forest clearing: four round plank floors (radius 15.5) stacked
seven blocks apart at world y -49, -42, -35 and -28, inside a ring of eight 2 × 2 dark oak columns
on stone footings, with a spruce rim beam at every floor, knee braces with hanging lanterns, a
narrow eave on top and a hoist beam across it with a chained bundle of logs as the landmark. A
mill pond lies under the tower; log piles, a saw bench and lamp posts in the yard; forest all
round. The floors are the only blocks in mangrove (bottom), jungle, oak and birch (top) planks
and stripped wood, the woods the Valvloer plugin drops and restores (`floor.blocks`); each floor
has its own pattern (rings, diagonal boards, checker, a sixteen-spoke compass) and the lower
three have five 2 × 2 holes that never line up. An invisible barrier ring above each rim keeps
players on the floors. The map's own `report()` checks that the floor woods appear nowhere else,
that the spawns (6 per team on the top floor, west and east) stand on floor, and, by flooding the
space with every floor block removed, that nobody can get past the barrier ring before dropping
below the lowest floor (which is out).

**Houtkamp** (Verstoppertje, 2026-11-23, 84 × 84 at x 500..583, z 40..123, ground at y -55). A
lumber camp full of the blocks hiders turn into: an open sawmill hall (workbenches, stonecutters
on a saw line, log racks, barrels, composters), a log yard of stacks, stumps and a timber cart,
a creek between two ponds with a footbridge and a dock, a fenced pumpkin garden, a stable corner
with a hay lean-to and paddock, two cabins and the forester's hut (where the seekers wait), a
camp fire square in the middle (where the hiders start), and forest with moss mounds and stumps,
barriers at the edge. Not rotated: hide and seek is not two equal sides. Its `report()` checks
that the seekers can walk to the hiders, that no floor reaches the edge, and that each disguise
block (barrel, hay, spruce and oak log, crafting table, composter, moss, pumpkin) stands next to
walkable floor at least 10 times.

`build.py` takes a map's own `MODES` and `report()` when it has them (otherwise paintball and the
paintball checks), and writes `flags: {}` for maps without flags.

## The lobby

**Basiskamp** (2026-11-22, 112 × 112, x -56..55, z -56..55, ground at y -59, spawn 0.5 -58 0.5
facing north). A woodland army camp in Woudfront's style: a parade ground with a ring emblem
around the spawn; the practice range to the north (log palisade lanes, firing shed, earth berm;
the Paintball practice zone x -8..8, z -9..-39, y -59..-51); two leaderboard plinths at
(-10.5, -56, -8.5) and (11.5, -56, -8.5); the paintball HQ to the west (palisade compound,
command hall, watchtower, flag podium) with the Paintball NPC at (-17.5, -58, 0.5) facing east;
the friends' camp (private rooms) to the east with a campfire circle, tents and a cabin; to the
south two sand tables, relief models of Woudfront and Duinbasis built from the arena code itself
(1:6 across, 1:3 up, team spawns in concrete, flags as banners), then a creek with a bridge and
the closed camp gate between two watchtowers; barracks (NW), a supply depot with a water tower
(NE), a motor pool (SE) and forest on rising hills all round, with barriers at the edge.

Moving the ground from -63 to -59 moved the lobby spawn, the leaderboards and the practice
zone and dummies up by 4; the values are in `out/basiskamp/lobby.txt` and in the repo's Paintball
`config.yml`. Backup of the old lobby on node1: `/root/server5-lobby-backup-20261122/`.

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
