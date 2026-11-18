# DBSDW Minigames: MinigameCore + Paintball — Design

- **Date:** 2026-11-18
- **Status:** approved by the owner on 2026-11-18
- **Repo:** `wdp-2010/dbsdw-minigames` (public)

## 1. Intent

DBSDW is a Flemish Minecraft survival community with roughly 5–20 players online at once. The
owner wants a separate minigame server that those players can hop to from survival and back,
for fun between survival sessions. The first game is **paintball**. More games will follow later,
each as its own plugin on a shared core, so the shared parts (lobby, queue, teams, arenas, stats,
resource pack) are built once.

Success: current players actually use it. They get from survival into a match in a few seconds
without a second login, a match feels good (shots that fly true, clear hit feedback, bright
cartoony look), and they come back to it.

## 2. Goals, non-goals, anti-goals

**Goals (v1)**

- A separate Paper 26.3 server with a lobby, an NPC and a menu to queue, auto-start when enough
  players are queued.
- Paintball with two modes: team deathmatch (TDM) and capture the flag (CTF).
- One gun for everyone. Real, visible paintballs that arc and can be dodged, with enough range to
  fight across the arena.
- Power-ups picked up in the arena.
- Stats and leaderboards.
- A required resource pack, made by us, in a cartoony and bright style: gun model, paint effects,
  sounds, HUD/UI.
- Travel between survival and minigames with the vanilla transfer packet and no second login.
- One simple arena now; a more advanced arena later.
- Everything configurable (rules, timings, numbers); defaults below.

**Non-goals (v1)**

- Bedrock players (Geyser). Java only first; a Bedrock pack comes later.
- Unlocks, cosmetics, gun classes, ranks, parties, spectating a running match, joining a match
  already in progress.
- A Velocity/BungeeCord proxy.
- Any link with PlayLimit. Minigame time does not count toward the survival play limit; the owner
  says the limit is temporary.
- Games other than paintball (but the core must not assume paintball).

**Anti-goals (would be a failure even if it works)**

- Anyone can join the minigame server as any player name (it runs in offline mode).
- Players have to log in twice per trip.
- The map gets permanently changed by a match, or needs a manual reset.
- The minigame server's memory use endangers the live survival server.
- Changes to the live survival server made without the owner's say-so.

## 3. Constraints and facts

| Topic | Fact |
|-------|------|
| Server software | Paper 26.3 (not Folia), API `io.papermc.paper:paper-api:26.3.build.255-stable`, Java 25 |
| Resource pack format | 97.1 (`min_format`/`max_format` `[97, 1]` in `pack.mcmeta`) |
| Host | Pterodactyl panel `panel.dbsdw.com`, node `node1.dbsdw.com`, 15 GiB RAM |
| Memory | Minigames 3 GB, live survival 10 GB, Folia test server 2 GB. Owner accepts the OOM risk. Shrinking live from its current size needs a restart: ask before stopping live. |
| Live survival | Paper 26.3, `online-mode=false`, AuthMe 6.0.1 (SQLite, pre-join login dialog, sessions on), Geyser + Floodgate, ViaVersion, EssentialsX, PlayLimit (daily play limit). Moving to Folia later (separate project), so anything we add to survival must run on Paper **and** Folia. |
| Minigame server mode | `online-mode=false` too (same offline UUIDs as survival, so stats line up by UUID). |
| Transfers | Both servers need `accepts-transfers=true` in `server.properties` (default `false`). A client that was transferred keeps its cookies. |
| Players | Flemish; player-facing text defaults to Dutch. |
| No proxy | Players connect directly to each server; the minigame server's port must be public. |

## 4. Approaches considered

### 4.1 Login on the minigame server — **picked: signed transfer ticket**

1. **Own AuthMe on the minigame server.** Players type `/login` again on every trip. Direct joins
   are possible but need the password.
2. **Signed transfer ticket (picked).** Before transferring, the sending server stores a short-lived,
   HMAC-signed cookie on the client. The receiving server reads and checks it and lets the player in
   without a password. The minigame server refuses every join without a valid ticket, which closes
   the offline-mode hole completely. The way back to survival uses the same ticket.
   Costs a small plugin on survival, which is needed anyway for the `/minigames` command and NPC.

### 4.2 Paintball projectile — **picked: vanilla snowball with our model**

1. **Vanilla snowball (picked)** showing our paintball item model. Smooth client-side rendering,
   vanilla collision, cheap on the server. We set speed and spread; gravity stays vanilla
   (0.03 blocks/tick², drag 0.99). Owner: "make sure it has enough range" — see §7.2.
2. **Simulated projectile** (display entity moved every tick with ray casts). Full control of
   gravity and drag, but visibly jittery at 20 TPS and much more code.

### 4.3 Paint on the map — **picked: splat decals**

1. **Splat decals (picked):** flat, pack-textured display entities on the surface that was hit.
   They fade after a while and are all removed at match end. The world is never changed.
2. **Real coloured blocks restored after the match:** looks the most painted, but a failed or
   interrupted reset leaves a broken map.

### 4.4 Smaller calls (proposed by Claude, accepted by the owner)

- Lobby NPC: vanilla `Mannequin` entity, no Citizens dependency.
- Menus: chest GUIs (they also work for Bedrock later), not Dialog screens.
- Stats: SQLite file per server, not MySQL.
- Several arenas can run matches at the same time (one match per arena); one arena ships in v1.

## 5. Architecture

Three plugin jars, built from this repo:

| Module | Jar | Runs on | Does |
|--------|-----|---------|------|
| `core/` | MinigameCore | minigame server | ticket check on join, lobby, NPC, queue menus, teams, arenas, match lifecycle, stats, leaderboards, resource pack hosting and enforcement, travel back to survival |
| `paintball/` | Paintball | minigame server | registers the paintball game (TDM, CTF) with MinigameCore |
| `bridge/` (new) | DbsdwBridge | live survival (Paper now, Folia later) | `/minigames` command and survival NPC, issues tickets and transfers, accepts tickets on return. Uses Paper's global/region/entity schedulers only (no Bukkit scheduler), so the same jar runs on Folia, like PlayLimit. |

The ticket format and its signing/verification are one piece of plain Java (no Bukkit types), used
by both MinigameCore and DbsdwBridge, and unit-tested once. How it is shared (a small `ticket`
module bundled into both jars, or similar) is the builder's choice; there must be one
implementation, not two copies.

Game logic (scores, hit counting, match states, flag states, queue rules, power-up timers, team
balancing) is written as plain Java classes with no Bukkit types, so it can be unit-tested in CI.
Bukkit/Paper code is a thin layer of listeners, schedulers and entity handling on top.

### 5.1 MinigameCore's API for games

A game plugin registers a game type with MinigameCore at enable time. The game type provides:

- its id and display name, and its modes (paintball: `tdm`, `ctf`), each with min/max players per
  team and the number of teams;
- which arena features a mode needs (CTF needs flag positions) so the core only offers arenas that
  fit;
- a factory that creates the game's handler for one match.

The match handler gets callbacks from the core: match start (players already split into teams and
teleported to spawns), player leave, a per-tick update, and match end; it tells the core when the
match is over and who won, and reports stat increments. The core owns everything that is the same
for every game: queueing, team assignment, teleporting, countdowns, the ending screen, returning
players to the lobby and saving stats.

## 6. The player's journey

### 6.1 Survival → minigames (DbsdwBridge)

1. The player runs `/minigames` or right-clicks the minigames NPC at survival spawn (a `Mannequin`,
   placed by an admin command).
2. DbsdwBridge refuses, with a clear Dutch message, if:
   - the player is not logged in to AuthMe yet;
   - the player is a Bedrock player (Floodgate) — "Java only for now";
   - the player's client is not on the 26.3 protocol (checked through ViaVersion's API when
     ViaVersion is present), because the minigame server and pack only support 26.3.
3. Otherwise it stores a ticket cookie and transfers the player to the minigame server's public
   address and port (from config).

### 6.2 Arriving on the minigame server

1. During the login/configuration phase MinigameCore reads the ticket cookie. No valid ticket →
   disconnect with "Join via survival: /minigames". Valid → continue.
2. Still in the configuration phase, the server sends the required resource pack (§9.3). Declined
   or failed → disconnect with a message explaining that the pack is required.
3. The player spawns in the lobby: adventure mode, no damage, no hunger, inventory cleared and
   given the lobby items: queue menu, stats, back to survival.

### 6.3 Lobby and queue

- The lobby is a small, bright area in its own world, built by Claude and reproducible from the
  repo in the same way as the arena (§11.2), with one `Mannequin` NPC per game ("Paintball"), a
  leaderboard display (§10) and the lobby items.
- Clicking the NPC or the menu item opens the game's chest menu: one entry per mode (TDM, CTF),
  showing how many players are queued and how many are needed. Click = join that queue; click
  again = leave.
- When a queue reaches the start minimum and an arena that fits the mode is free, a countdown
  starts (default 30 s, shortened to 10 s when the match is full). If players leave and the queue
  drops below the minimum, the countdown stops.
- At zero the queued players (up to the maximum) are split into teams by the auto-balancer and
  teleported into the arena. Players over the maximum stay queued for the next match.
- Arena choice: random among free arenas that fit the mode.

### 6.4 Back to survival

The "back to survival" lobby item and `/survival` store a ticket for survival and transfer the
player to the survival address. DbsdwBridge on survival accepts the ticket and the player is not
asked to log in again (§8.3).

## 7. Paintball

### 7.1 Teams and defaults

Two teams, Red and Blue. All values below are defaults in `config.yml`.

| Rule | TDM | CTF |
|------|-----|-----|
| Players per team | 2–8 | 2–8 |
| Start minimum | 4 players (2 v 2) | 4 players (2 v 2) |
| Win | first team to 30 eliminations | first team to 3 captures |
| Time limit | 10 min, then most eliminations wins | 12 min, then most captures wins |
| Tie at time limit | draw | draw |
| Hits to be out | 3 | 3 |
| Respawn delay | 3 s | 3 s |
| Spawn protection | 2 s (can't be hit; shooting ends it early) | same |

Friendly fire is off: paintballs pass through teammates. If a team has no players left during a
match (everyone left), the other team wins.

### 7.2 The gun and the paintballs

- One gun item for everyone, with our model. Right-click to shoot.
- **Fire rate:** 4 shots/s (5-tick cooldown, shown with the client's cooldown animation). Unlimited
  ammo, no reload in v1.
- **Speed and range:** paintballs leave at **3.0 blocks/tick** (vanilla snowballs: 1.5). With
  vanilla gravity and drag this gives:

  | Distance | Drop | Flight time |
  |----------|------|-------------|
  | 20 blocks | 0.6 blocks | 0.35 s |
  | 40 blocks | 3.0 blocks | 0.75 s |
  | 60 blocks | 7.1 blocks | 1.15 s |

  So the effective range covers the whole arena (§11) while the arc and flight time keep shots
  dodgeable at distance. Vanilla speed (1.5) would drop 12.7 blocks over 40 blocks, which is too
  short. Speed and spread are configurable.
- **Spread:** 1° random cone.
- **Lifetime:** a paintball that hits nothing is removed after 3 s.
- The paintball shows the shooter's team colour.

### 7.3 Hits and elimination

- A paintball that hits an enemy adds one hit. The victim gets a hit sound, the hurt camera tilt,
  a paint-splash overlay on the HUD and their hits-left indicator updates. The shooter gets a
  hit-marker sound and an action-bar marker.
- At 3 hits the victim is **out**: big splash effect, they become invisible and unable to act for
  the respawn delay with a countdown title, then respawn at their team's spawn with hits reset
  and spawn protection. The shooter gets the elimination (TDM: +1 team score).
- No vanilla damage of any kind in the arena (fall, hunger, suffocation and so on are cancelled).
  Leaving the arena bounds (falling into a void, glitching out) counts as out, with no shooter.

### 7.4 Capture the flag

- Each team has a flag at its base (a banner with a pack model, at a position set in the arena).
- Walking into the enemy flag picks it up. The carrier glows in their team colour and shows the
  flag on their head; everyone gets a message and sound.
- Bringing the enemy flag to your own flag position while your own flag is at home = 1 capture.
- If the carrier is out, the flag drops where they were. A teammate of the flag's team touching a
  dropped flag returns it home immediately; otherwise it returns home by itself after 30 s.
- Flag states (home, carried by X, dropped at P since T) live in a plain-Java class with unit tests.

### 7.5 Power-ups

Power-up spawn points are set per arena. Each point spawns a random power-up (from the enabled
list) that floats and spins as a display entity; walking into it picks it up; it respawns 30 s
after pickup.

| Power-up | Effect | Default duration |
|----------|--------|------------------|
| Rapid fire | double fire rate | 10 s |
| Triple shot | each shot fires 3 paintballs in a small fan | 8 s |
| Speed | Speed II | 10 s |
| Shield | the next hit is absorbed | until used |
| Repair | removes one hit already taken | instant |

Active power-ups are shown on the HUD with an icon and time left.

### 7.6 Paint splats

When a paintball hits a block, a flat splat decal in the shooter's team colour appears on the hit
face (random variant and rotation, slightly off the surface to avoid flicker). Splats fade after
20 s. A per-arena cap (default 300) removes the oldest when exceeded. All splats are removed at
match end. Hitting a player shows a paint particle burst instead.

### 7.7 HUD

- Sidebar: mode, team scores, time left.
- Boss bar: time left (TDM) or flag status (CTF).
- Action bar: hits left as paint-drop icons, active power-ups.
- Titles: countdown, "Out!", respawn countdown, match result.
- End of match: 10 s results screen (winner, top players by eliminations), then everyone returns
  to the lobby.

Icons are font glyphs from the resource pack.

## 8. Transfer tickets

### 8.1 Ticket contents and signing

A ticket is a cookie (`dbsdw:ticket`) holding: format version, player UUID, player name,
destination (`minigames` or `survival`), issue time, expiry (issue + 60 s) and a random nonce,
signed with HMAC-SHA256 using a secret shared by both servers. MinigameCore generates the secret
on first start if its config has none; the admin copies it into DbsdwBridge's config once. It is
never logged or printed, and neither plugin starts its ticket features while the secret is
missing (they log one clear error instead).

### 8.2 Checks on arrival (both directions)

The receiving server accepts a ticket only if: the signature is valid, the destination is this
server, it has not expired, the UUID and name match the connecting player, and the nonce has not
been used before (used nonces are remembered until they expire). After use the cookie is
overwritten so it cannot be used again. Any failure is logged (player name and reason, never the
ticket or secret) and the player is refused with a message.

### 8.3 Returning to survival without a second login

Survival keeps AuthMe as its login system. A player arriving at survival with a valid ticket must
skip AuthMe's pre-join login dialog and be logged in. **This depends on what AuthMe 6.0.1 allows**
(an API hook to skip the pre-join dialog, or `forceLogin` after join). This is the first spike
(§13, step 0). If AuthMe offers no clean way, the fallback is AuthMe's existing session login
(players returning from the same IP within the session time are logged in automatically), and the
owner is told before building anything else on top.

Tickets are only issued on survival to players who are logged in to AuthMe.

### 8.4 Consequences of refusing direct joins

- A player whose game crashes or disconnects on the minigame server cannot reconnect to it
  directly. They join survival as usual (AuthMe login) and use `/minigames` again. The refusal
  message says so.
- When the minigame server shuts down or restarts, MinigameCore transfers everyone online back to
  survival with a ticket instead of kicking them.

## 9. Resource pack

### 9.1 Contents

All original work, made by Claude (or CC0 assets, credited in `resourcepack/CREDITS.md`). Style:
cartoony, bright, saturated team colours (red and blue), thick outlines.

- Gun: 3D item model (first and third person).
- Paintball items in red and blue.
- Splat decals: three shapes in red and blue.
- Flags: red and blue, on-base and carried.
- Power-up models: five.
- Lobby items: queue menu, stats, back to survival.
- Sounds: shot, hit (victim), hit marker (shooter), out, respawn, flag pickup/drop/return/capture,
  power-up pickup, countdown tick, match win/lose. Generated with `ffmpeg` or similar synthesis,
  or CC0.
- Font glyphs for the HUD (paint drops for hits left, power-up icons, team icons) and chest-menu
  backgrounds.

Custom items use the `item_model` data component pointing to `dbsdw:` models; no vanilla item is
retextured, so the survival look is never affected.

### 9.2 Build

`tools/build_pack.py` (already in the repo) validates and zips the pack reproducibly with its
SHA1; CI builds it on every push. Texture and sound sources (generator scripts, Blockbench files)
live in the repo so the pack can be rebuilt.

### 9.3 Hosting and enforcement

MinigameCore runs a small HTTP server (JDK built-in) on a second port of the minigame server,
serving only the pack zip at a URL that contains its SHA1 (so clients never use a stale cached
copy). At startup it loads the zip from its data folder and computes the SHA1. Players receive
the pack as **required** during the configuration phase, before they enter the world.

## 10. Stats and leaderboards

- Stored in SQLite in MinigameCore's data folder, keyed by player UUID, game and stat name, so any
  future game can add stats without schema changes. Player names are stored for display.
- Database work runs off the main thread.
- Paintball stats: matches played, wins, losses, draws, eliminations, outs (deaths), hits,
  shots, flags captured, flags returned.
- `/stats [player]` shows a player's stats. `/top <stat>` shows the top 10.
- The lobby shows text-display leaderboards (e.g. top eliminations, top wins), refreshed every
  minute.
- No seasons or resets in v1.

## 11. Arena

### 11.1 Arena definition

An arena is a region in a world on the minigame server, plus a config file holding: name, modes it
supports, team spawn points, flag positions (CTF), power-up spawn points, play-area bounds, and
the lobby return point. Admins set these by standing at a spot and running a command
(`/mg arena create|setspawn|setflag|addpowerup|setbounds|save`). Arenas never change during a
match (only display entities are added and removed), so no reset is needed.

### 11.2 First arena

One simple symmetric arena, about 60 × 40 blocks: two bases at the ends, cover in the middle
(walls, crates, a raised centre platform), side lanes, bright cartoony blocks (concrete, wool,
terracotta). It supports both TDM and CTF. The arena must be reproducible from the repo
(a generator or a saved structure committed to the repo), not only hand-built on the server.

## 12. Deployment and operations

- New Pterodactyl server "DBSDW Minigames" on node1: Paper 26.3, 3 GB, two ports (game, pack HTTP),
  `online-mode=false`, `accepts-transfers=true`. Plugins: MinigameCore, Paintball. No AuthMe.
- Live survival: install DbsdwBridge, set `accepts-transfers=true`, place the NPC. This needs a
  restart of live; **ask the owner before touching live.** The network map (`~/NETWORK-MAP.md`) is
  updated with the new server and ports.
- Admin commands work from the panel console. Permissions: `minigames.admin` for arena and NPC
  setup and reloads, given to ops; no permissions plugin on the minigame server in v1. Players
  only see and tab-complete the minigame commands (`/stats`, `/top`, `/survival`, `/leave`).
- Config and messages files: `config.yml` (rules, ports, addresses), `messages.yml` (Dutch
  defaults, MiniMessage format), per-arena files.
- Logs: clear one-line messages on ticket refusals, pack failures and match start/end.

## 13. Build order

0. **Spikes (throwaway):** on a throwaway Paper 26.3 server, confirm (a) reading a cookie during
   the configuration phase after a transfer, (b) sending a required pack during the configuration
   phase, (c) how AuthMe 6.0.1 can let a ticket holder skip its pre-join login (§8.3).
   Report results to the owner before step 1.
1. Tickets + DbsdwBridge + MinigameCore join check, travel both ways.
2. Core: lobby, NPC, queue menus, teams, arenas, match lifecycle (with a trivial test game).
3. Paintball TDM: gun, paintballs, hits, outs, respawn, HUD.
4. CTF.
5. Power-ups, splats, stats, leaderboards.
6. Resource pack: all models, textures, sounds, glyphs; pack hosting.
7. The lobby and the first arena.
8. Deploy: minigame server, then (with the owner's OK) the live survival changes.

Each step ends with unit tests green in CI and an in-game check on a test server.

## 14. Testing

- Unit tests (JUnit, in CI) for all plain-Java logic: tickets (valid, expired, wrong destination,
  wrong player, reused nonce, bad signature), team balancing, queue rules, match states, scoring
  and win conditions for TDM and CTF, flag states, hit tracking, power-up timers, splat cap.
- Pack: `tools/build_pack.py` checks in CI.
- In-game: a throwaway Paper 26.3 server with test bots and a real client for feel (range, fire
  rate, hit feedback), the way PlayLimit was tested.

## 15. Left to the builder

- Package and class layout, storage library for SQLite, how the ticket code is shared between
  jars.
- Exact base item for the gun, as long as right-click in the air is detected reliably.
- Exact model shapes, textures and sounds, within the style above.
- Exact menu layouts and message wording (Dutch defaults).
- Whether the arena is built by a generator or saved as a structure, as long as it is in the repo.
