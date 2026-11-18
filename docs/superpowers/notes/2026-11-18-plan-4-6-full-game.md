# Plans 4–6 (code part): CTF, power-ups, splats, stats, leaderboards, pack hosting (2026-11-18)

Built in one pass at the owner's request ("finish the full minigame, stop where textures and props
start"). No separate plan documents; the decisions are recorded here.

## Decisions

1. CTF and TDM share one match class (`PaintballMatch`); only scoring, time limit and boss bar differ.
2. Flag touch radius 1.5 blocks horizontally, 2 vertically. A carrier who leaves the bounds sends
   the flag home at once (it would otherwise lie outside the arena).
3. The carrier glows in their team colour (per-match scoreboard teams, which also colour name tags)
   and wears the enemy banner as a helmet.
4. Power-ups: placeholder items (blaze powder, prismarine crystals, sugar, shield, golden apple),
   floating and spinning item displays. Shield is lost when the player is out. Repair on a player
   with no hits is used up without effect.
5. Splats: thin concrete block displays on the hit face, random size and rotation. Displays cannot
   fade, so they disappear after the lifetime; a fade needs pack textures (text or item display).
6. Stats: the core counts matches/wins/losses/draws for players present at the end (leavers get
   "matches" only); a match stopped without a result (shutdown, game error) still saves the stats
   gathered so far. Games declare their stat names (`GameType.stats()`, `statName()`).
7. Resource pack hosting is code-complete but inactive until a `pack.zip` and `pack.public-url`
   exist (plan 6 content). The pack HTTP port is not yet allocated in Pterodactyl.
8. `-Xlint:classfile` is excluded build-wide: Paper's JOML jar triggers it, not our code.

## Bot smoke test (node1, torn down afterwards)

| Check | Result | Evidence |
|-------|--------|----------|
| Pack offered in the configuration phase | PASS | bot `pack_offered url=http://minigames:25600/<sha1>.zip` before joining |
| CTF menu entry | PASS | modes menu: slot 11 Team Deathmatch, slot 13 Capture the Flag |
| Flag pickup and capture | PASS | `BotA heeft de vlag van Rood gepakt!`, `... veroverd!`, title `Blauw wint!`, `Veroverd: Rood 0 - Blauw 1` |
| Power-up pickup | PASS | BotB action bar `+ Schild ●●● ⛨` |
| Stats saved and shown | PASS | `/stats`: Gespeeld 1, Gewonnen 1 / Verloren 1, Vlaggen veroverd 1 |
| `/top` | PASS | `Top 10 Vlaggen veroverd (Paintball)`, `1. BotA - 1` |

No exceptions in the server log.

## Not done (needs textures, models, sounds or building)

Resource pack content (gun, paintball, flag, power-up models, splat decals, sounds, HUD glyphs,
menu backgrounds), the real lobby and arena, and the live survival link (plan 8, owner's OK).
