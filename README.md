# DBSDW Minigames

Minigame server plugins and resource pack for the DBSDW Minecraft community.
Runs on Paper 26.3 (Java 25).

| Path | What it is |
|------|------------|
| `travel/` | Plain-Java transfer tickets (HMAC-signed cookie, single use, 60 s), shared by both plugins below |
| `core/` | **MinigameCore** plugin (minigame server): refuses joins without a valid ticket, `/survival`, lobby, queue menus and game NPCs, arenas, teams, match lifecycle, the game API; later: stats, resource pack |
| `bridge/` | **DbsdwBridge** plugin (survival server, Paper and Folia): `/minigames`, the minigames NPC, ticket login on return via AuthMe |
| `testgame/` | **MgTestGame**: test-only game (first team to 3 points) for the e2e checks. **Never deploy it.** |
| `paintball/` | **Paintball** plugin: team deathmatch and capture the flag, built on MinigameCore |
| `valvloer/` | **Valvloer** plugin: the floor drops away under your feet, last team standing wins (arena Zaagtoren) |
| `verstoppertje/` | **Verstoppertje** plugin: hide and seek, hiders disguise as blocks (arena Houtkamp) |
| `resourcepack/` | The required resource pack (models, textures, sounds, HUD) |
| `tools/` | Resource pack validation and build script; `tools/e2e/` throwaway test servers and bots; `tools/maps/` arena builder (see `docs/building-method.md`) |
| `build-logic/` | Shared Gradle convention plugin (`dbsdw.paper-plugin`) for every plugin module |
| `gradle/libs.versions.toml` | All dependency versions in one place |

## Build

Requires JDK 25.

```sh
./gradlew build
```

Jars land in `core/build/libs/` and `paintball/build/libs/`.

## Resource pack

Requires Python 3.10+, no extra packages.

```sh
python3 tools/build_pack.py              # validate + build dist/dbsdw-minigames-pack.zip and .sha1
python3 -m unittest discover -s tools    # tests for the build script
```

The script rejects invalid JSON, duplicate JSON keys, invalid resource paths, fake PNGs,
stray files and a `pack.mcmeta` that does not cover the server's pack format (97.1).
The zip is reproducible: the same content always gives the same SHA1.

## CI

Every push and pull request runs the Gradle build with unit tests and the resource pack
build. Jars and the pack are uploaded as workflow artifacts.

## Travel setup (survival ↔ minigames)

1. Start the minigame server once: MinigameCore writes a new `ticket-secret` to
   `plugins/MinigameCore/config.yml`. Copy that value to `plugins/DbsdwBridge/config.yml` on
   survival. Never share it.
2. Set `minigames.host`/`port` in the bridge config and `survival.host`/`port` in the core config
   to the public addresses.
3. Set `accepts-transfers=true` in `server.properties` on **both** servers.
4. Admins place the survival NPC with `/minigames npc set` (`/minigames npc remove` within 5 blocks).

End-to-end checks: `tools/e2e/README.md`.

## Lobby and arenas (minigame server)

Admin commands need `minigames.admin` (ops have it). Positions are taken from where you stand.

- `/mg setlobby` sets the lobby spawn. Players arrive there in adventure mode with two items:
  **Spellen** (queue menu) and **Terug naar survival**. No damage or hunger anywhere on the server.
- `/mg npc set <game>` places a game NPC (a Mannequin) that opens that game's queue menu;
  `/mg npc remove` removes game NPCs within 5 blocks.
- Arenas: `/mg arena create <name>`, then `modes <game:mode,…>`, `setspawn <team>` (repeat for
  more spawns), `setflag <team>` (modes that need flags), `addpowerup`, `setbounds 1` and
  `setbounds 2` (two opposite corners), and `save`. Saving checks that every point lies inside the
  bounds and that each registered mode has a spawn (and flag, if needed) for every team; nothing
  is saved while there are errors. `/mg arena list` and `/mg arena delete <name>` also work from
  the console. The arena's world must be loaded at startup.
- Arena files live in `plugins/MinigameCore/arenas/<name>.yml`:

  ```yaml
  world: world
  modes: [paintball:tdm, paintball:ctf]
  corner1: {x: 10, y: -64, z: -10}
  corner2: {x: 40, y: -50, z: 10}
  spawns:
    "0": [{x: 15.5, y: -62.0, z: 0.5, yaw: -90.0, pitch: 0.0}]
    "1": [{x: 35.5, y: -62.0, z: 0.5, yaw: 90.0, pitch: 0.0}]
  flags:
    "0": {x: 12.5, y: -62.0, z: 0.5, yaw: 0.0, pitch: 0.0}
    "1": {x: 38.5, y: -62.0, z: 0.5, yaw: 0.0, pitch: 0.0}
  powerups: []
  ```

- Queue rules: one queue per player. When a mode has its start minimum queued and a fitting arena
  is free, the arena is reserved and a countdown runs (`queue.countdown-seconds`, 30; cut to
  `queue.full-countdown-seconds`, 10, once the queue is full). Players beyond the maximum stay
  queued for the next match. Results show for `match.results-seconds` (10), then everyone returns
  to the lobby. `/leave` leaves the queue or the match.
- Private rooms (`/room` or the **Speel met vrienden** lobby item, slot 6): the host invites friends
  (menu button or `/room invite <speler>`, clickable **[Meedoen]**, valid 60 s), picks mode, map and
  team rule (players pick / host assigns / random), and presses start from 2 players (1v1 is fine).
  A 5 s countdown follows; after the match everyone is back in the room. Room matches count for
  stats. A room holds an arena only from start to match end. Players in a room can't use the
  public queue. `/room leave|kick|start`, `/leave` in the lobby also leaves the room. Design:
  `docs/superpowers/specs/2026-11-22-private-rooms-design.md`.

Games register through `be.dbsdw.minigames.core.api.GameRegistry` (a Bukkit service);
`testgame/` is the smallest example.

## Paintball (minigame server)

Two modes, Red vs Blue: `paintball:tdm` (team deathmatch) and `paintball:ctf` (capture the flag;
the arena needs a flag position per team). Right-click with the gun (hotbar slot 0) to shoot;
holding the button fires 4 paintballs per second. Three hits and you are out: hidden and frozen
for the respawn delay, then back at a team spawn with a short spawn protection (shooting ends it).
Leaving the arena bounds also counts as out, without a point for anyone.

- **TDM:** first team to the elimination target wins; at the time limit the leader wins, equal
  scores are a draw.
- **CTF:** walk into the enemy flag to take it (you glow and wear it), bring it to your own flag
  while your flag is home to capture. A carrier who is out drops the flag; a teammate of that flag
  touching it returns it, otherwise it goes home by itself after `ctf.flag-return-seconds`. A
  carrier who leaves the bounds sends the flag straight home. Boss bar shows both flags.
- **Power-ups** spawn at the arena's power-up points (`/mg arena addpowerup`) and refill 30 s after
  a pickup: rapid fire, triple shot, speed, shield (next hit absorbed), repair (one hit removed).
- **Paint splats** appear on blocks that are hit, vanish after 20 s, at most 300 per match, all
  removed at the end. The world itself is never changed.

All values are in `plugins/Paintball/config.yml`; keys added by a newer version are written into
an existing file on start. Invalid values disable the plugin with one log line naming the key.
Texts are in `plugins/Paintball/messages.yml` (Dutch, MiniMessage).

Until the resource pack the gun is a carrot on a stick, paintballs show as red/blue dye, flags are
banners, power-ups are vanilla items, splats are thin concrete squares, sounds are vanilla and HUD
icons are plain text.

## Valvloer (minigame server)

One mode, `valvloer:teams`, Red vs Blue, 1-8 per team. Everyone starts on the top of four stacked
floors. After a few seconds' grace, every floor block someone stands on drops away 8 ticks later;
whoever falls below the lowest floor is out and watches as a spectator. The last team with someone
on the floor wins; at the time limit (5 min) the team with more players left wins. Standing on
anything that is not floor for 3 s counts as falling.

The floor is every block of `floor.blocks` (config.yml) inside the arena bounds, so an arena may
use those blocks only for its floors. They are saved to `plugins/Valvloer/restore/<arena>.txt` when
a match starts and put back when it ends; after a crash the next start restores them. The arena
is built in code: `tools/maps/zaagtoren.py`.

## Verstoppertje (minigame server)

One mode, `verstoppertje:klassiek`, 2-24 players. One player in five seeks (at least one; in a
room, players who picked the second team seek first). Hiders start at the team 0 spawns as a
block (hotbar: right-click a block to become it); they are invisible, only their block shows.
Standing still for 3 s makes it a solid block lined up with the grid. Seekers wait blind at the
team 1 spawns for 30 s, then hit suspicious blocks with their stick: a hit hider is found and
seeks along. Every 30 s every hider squeaks. Seekers win when everyone is found, hiders when one
of them is still hidden after 4 minutes. Settings and the disguise blocks: `plugins/Verstoppertje/config.yml`.
The arena is built in code: `tools/maps/houtkamp.py`.

Core API additions for these games: `GameType.teamName(mode, team)` (a game's own team names) and
`MatchContext.moveToTeam(player, team)` (a found hider joins the seekers).

## Stats, leaderboards, resource pack (MinigameCore)

- Stats live in `plugins/MinigameCore/stats.db` (SQLite, written off the main thread when a match
  ends). The core counts matches, wins, losses and draws; paintball adds eliminations, outs, hits,
  shots, captures and returns. Players: `/stats [player]`, `/top <stat> [game]`, or the book in the
  lobby hotbar.
- Lobby leaderboards (top 10, refreshed every minute): stand where it should float and run
  `/mg leaderboard add <game> <stat>` (it appears 2 blocks above you); `/mg leaderboard remove`
  removes the ones within 5 blocks. Stored under `leaderboards:` in config.yml.
- Resource pack: put the zip at `plugins/MinigameCore/pack.zip`, set `pack.port` to a second
  allocation of the server and `pack.public-url` to `http://<public ip>:<port>`. MinigameCore
  serves it at `/<sha1>.zip` and sends it as required during login; declining or a failed download
  disconnects with a message. Without the file or URL, players join without a pack.
