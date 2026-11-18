# Plan 3: Paintball Team Deathmatch — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Players queued for "Paintball – Team Deathmatch" get a gun, shoot visible paintballs that arc and can be dodged, are out after 3 hits, respawn after 3 s with 2 s of spawn protection, see scores, time and hits left on a HUD, and the first team to 30 eliminations (or the leader after 10 minutes) wins — proven in-game on node1.

**Architecture:** The Paintball plugin registers game `paintball` with mode `tdm` through MinigameCore's `GameRegistry` (same pattern as `testgame/`). All rules — settings validation, hits/outs/protection/respawn timing, team score and win, fire-rate gate, spread cone, clock text, top players — are plain Java with unit tests. A thin Bukkit layer (`TdmMatch` as the `MatchHandler`, one listener, the gun item, the HUD) turns server events into calls on those classes, driven by the core's per-tick `tick()`.

**Tech Stack:** Java 25, Paper API 26.3 (`26.3.build.255-stable`), Gradle 9.9.1 (existing `build-logic`, version catalog), JUnit (catalog), core's public `Messages` loader, the existing `tools/e2e/` harness on node1.

**Spec:** `docs/superpowers/specs/2026-11-18-minigames-paintball-design.md` (§5, §5.1, §7.1 TDM column, §7.2, §7.3, §7.7, §12 config/logs, §13 step 3, §14). Plans 1 and 2 are merged (`main` = `0705f26`).

## Global Constraints

- Paper API `io.papermc.paper:paper-api:26.3.build.255-stable`, Java 25, `-Xlint:all -Werror`, `org.gradle.warning.mode=fail`. No deprecated API.
- Gradle runs in Docker (no local Java): `docker run --rm -u 1000:1000 -e HOME=/tmp -e GRADLE_USER_HOME=/gh -v /tmp/dbsdw-gradle-home:/gh -v "$PWD":/work -w /work eclipse-temurin:25-jdk ./gradlew --no-daemon -q <tasks>`. Below, `GRADLE <tasks>` means this command.
- Rule classes in `paintball/.../rules/` use no Bukkit/Paper/Adventure types.
- Paintball runs on Paper only; the Bukkit scheduler is allowed, but all per-match timing goes through `MatchHandler.tick()` and `MatchContext.ticks()` (no own timers).
- Defaults (`paintball/src/main/resources/config.yml`), verbatim from the spec: 2 teams, `tdm.min-per-team: 2`, `tdm.max-per-team: 8`, `tdm.win-eliminations: 30`, `tdm.time-limit-minutes: 10`, `player.hits-to-out: 3`, `player.respawn-seconds: 3`, `player.spawn-protection-seconds: 2`, `gun.shot-interval-ticks: 5` (4 shots/s), `gun.speed: 3.0` (blocks/tick), `gun.spread-degrees: 1.0`, `gun.ball-lifetime-ticks: 60` (3 s). Gravity and drag stay vanilla.
- Tie at the time limit = draw. Friendly fire off: paintballs pass through teammates. A team with no players left loses (the core already does this).
- No vanilla damage anywhere (the core cancels all player damage and hunger already; paintball adds nothing that deals damage).
- Player-facing text: Dutch defaults in `paintball/src/main/resources/messages.yml`, MiniMessage, loaded with core's `be.dbsdw.minigames.core.Messages.load(plugin)` (no third copy of the loader). Logs English, prefix `[paintball]`.
- Team colours: team 0 red, team 1 blue (core's `team.0`/`team.1` names via `MatchContext.teamName`).
- Test servers only on node1 via `tools/e2e/`, never the WDP host, never live. Tear down afterwards.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Decisions this plan makes (owner: check these)

1. **Placeholder look until plan 6.** Gun = `CARROT_ON_A_STICK` named "Paintgeweer" (right-click in the air is always sent by the client, no side effects); paintballs show `RED_DYE` / `BLUE_DYE`; sounds are vanilla (`ENTITY_SLIME_SQUISH` hit, `ENTITY_ARROW_HIT_PLAYER` hit marker, `ENTITY_GENERIC_SPLASH` out, `BLOCK_NOTE_BLOCK_PLING` respawn); HUD icons are text (`●`). Plan 6 swaps in `item_model`s, pack sounds, glyphs and the victim's paint-splash overlay, which needs a pack texture.
2. **Holding right-click gives exactly 4 shots/s.** The client repeats "use" every 4 ticks; a click that arrives during the 5-tick cooldown is kept (one at most) and fires the moment the cooldown ends. Without this, holding the button would give 2.5 shots/s.
3. **Spawn protection also applies at match start** (2 s), not only after a respawn.
4. **A hit on a protected player is absorbed** (the paintball breaks, no hit); hits on teammates and on players who are out pass through.
5. **Paintballs in flight still count after their shooter is out or has left.** The elimination point goes to the shooter's team; a shooter who left gets no personal elimination.
6. **"Out" = hidden from every player and frozen in place** (movement and jump attributes set to 0 with transient modifiers, gun disabled) for the respawn delay, with a countdown title; then teleported to a random spawn of their team.
7. **Elimination feed in chat** to everyone in the match ("Blauw-speler X is uit (door Y)"); results lines = team score line + top 3 by eliminations.
8. **Stats are not reported yet** (plan 5). CTF is not registered yet (plan 4).

## Review Focus

1. **Holding right-click** (client sends "use" every 4 ticks) → steady 4 shots/s, never 5 and never 2.5. → `FireGateTest.heldTriggerEvery4TicksFiresEvery5` (Task 2), e2e check 3.
2. **Player leaves mid-match while out, protected, or holding a queued shot** → they are visible again, can move, their scoreboard/boss bar is gone, and no shot fires for them later; the remaining match is unaffected. → `CombatTest.removedPlayerIsIgnored` (Task 1), `FireGateTest` via `TdmMatch.playerLeft` cleanup (Task 4), e2e check 6.
3. **Paintball hits someone who is out or a teammate** → passes through, no hit, no sound; it can still hit an enemy behind them. → `CombatTest.teammateAndOutPassThrough` (Task 1), e2e check 4.
4. **Player falls into the void or glitches outside the bounds** → out with no shooter, no team point, respawn at their spawn. → `CombatTest.outOfBoundsGivesNoPoint` (Task 1), e2e check 5.
5. **Match ends while players are out, frozen or hidden** (win, time limit, last team standing, server stop) → in the lobby everyone is visible, can move, has no paintball scoreboard or boss bar, and no paintballs remain in the arena. → `TdmMatch.end()` cleanup (Task 4), e2e checks 6–7.

---

## File Structure

```
paintball/src/main/java/be/dbsdw/minigames/paintball/
  PaintballPlugin.java            modify: load settings + messages, register the game, register listener
  PaintballGame.java              GameType "paintball" with mode tdm; index of player -> match, ball -> match
  rules/Settings.java             record of all config values + validation
  rules/TeamScore.java            team scores, target, leader
  rules/Combat.java               per-player hits, out/respawn, protection, eliminations, top list
  rules/HitTracker.java           moved from the package root (unchanged)
  rules/FireGate.java             per-player fire-rate gate with one queued shot
  rules/Spread.java               random direction inside a cone
  rules/Clock.java                ticks -> "m:ss"
  tdm/TdmMatch.java               MatchHandler: shooting, hits, outs, respawns, bounds, time limit, cleanup
  tdm/Hud.java                    sidebar, boss bar, action bar, titles for one match
  Gun.java                        gun and paintball items, PDC tag check
  PaintballListener.java          right-click, projectile hit, gun drop/swap/move protection
paintball/src/main/resources/config.yml, messages.yml (new), plugin.yml (unchanged)
paintball/src/test/java/be/dbsdw/minigames/paintball/rules/*Test.java
tools/e2e/bot/hop.js, tools/e2e/README.md, README.md
docs/superpowers/notes/2026-11-18-plan-3-e2e-results.md
```

---

### Task 1: Rules — settings, team score, clock, combat

**Files:**
- Create: `paintball/.../rules/{Settings,TeamScore,Clock,Combat}.java`, tests `paintball/src/test/.../rules/{SettingsTest,TeamScoreTest,ClockTest,CombatTest}.java`
- Move: `HitTracker.java` and `HitTrackerTest.java` into `rules/` (`git mv`, package line only)

**Interfaces:**
- Produces:
  - `record Settings(int minPerTeam, int maxPerTeam, int winEliminations, long timeLimitTicks, int hitsToOut, long respawnTicks, long protectionTicks, int shotIntervalTicks, double speed, double spreadDegrees, long ballLifetimeTicks)`; compact constructor throws `IllegalArgumentException` naming the field if: `minPerTeam < 1`, `maxPerTeam < minPerTeam`, `winEliminations < 1`, `timeLimitTicks < 20`, `hitsToOut < 1`, `respawnTicks < 0`, `protectionTicks < 0`, `shotIntervalTicks < 1`, `speed <= 0 || speed > 10`, `spreadDegrees < 0 || spreadDegrees > 45`, `ballLifetimeTicks < 1`. `static Settings defaults()` = the Global Constraints values (`timeLimitTicks` 12000, `respawnTicks` 60, `protectionTicks` 40).
  - `TeamScore(int teams, int target)`: `OptionalInt add(int team)` (returns the team when this point reaches `target`, once), `int points(int team)`, `OptionalInt leader()` (empty on a tie for first).
  - `Clock.format(long ticks) -> String`: `m:ss`, rounding partial seconds **up**, never negative (`0:00`).
  - `Combat(Map<UUID,Integer> teams, Settings s)` (insertion order = join order):
    - `enum HitResult { PASS_THROUGH, ABSORBED, HIT, OUT }`
    - `HitResult hit(UUID victim, UUID shooter, int shooterTeam, long now)` — victim unknown, out, or `team == shooterTeam` → `PASS_THROUGH`; protected (`now < protectedUntil`) → `ABSORBED`; else hit; at `hitsToOut` → `OUT`, credits `eliminations(shooter)+1` if the shooter is still known, sets `respawnAt = now + respawnTicks`.
    - `boolean outOfBounds(UUID victim, long now)` — alive player → out with no credit, returns true; else false.
    - `void start(long now)` — every player alive, hits 0, protected until `now + protectionTicks`.
    - `void shot(UUID shooter)` — ends their protection.
    - `List<UUID> respawnsDue(long now)` — players out with `now >= respawnAt`; each becomes alive, hits 0, protected until `now + protectionTicks`. Ordered by join order.
    - `boolean isOut(UUID)`, `boolean isProtected(UUID, long now)`, `int hitsLeft(UUID)`, `long respawnAt(UUID)`, `int eliminations(UUID)`, `void remove(UUID)`, `List<UUID> top(int n)` (by eliminations desc, ties in join order, players with 0 included).
  - `Combat` uses `HitTracker` for hit counting.

- [ ] **Step 1: Write the failing tests**
  - `SettingsTest.defaultsMatchSpec` (all eleven values above), `rejectsZeroShotInterval`, `rejectsMaxBelowMin`, `rejectsNonPositiveSpeed` (message contains the field name).
  - `TeamScoreTest.reachesTargetOnce` (target 3: `add(0)` ×2 empty, third → `OptionalInt.of(0)`, fourth → empty), `leaderEmptyOnTie`, `leaderIsHighest`.
  - `ClockTest`: `format(12000) == "10:00"`, `format(1199) == "1:00"`, `format(21) == "0:02"`, `format(0) == "0:00"`, `format(-5) == "0:00"`.
  - `CombatTest` (players A, B on team 0, C on team 1; defaults; `start(0)`):
    - `protectedAtStart`: `hit(C, A, 0, 39) == ABSORBED`; `hit(C, A, 0, 40) == HIT`.
    - `shootingEndsProtection`: `shot(C)`; `hit(C, A, 0, 1) == HIT`.
    - `threeHitsIsOut`: at t=100: HIT, HIT, OUT; `eliminations(A) == 1`; `isOut(C)`; `respawnAt(C) == 160`; `hitsLeft(C) == 0`.
    - `teammateAndOutPassThrough`: `hit(B, A, 0, 100) == PASS_THROUGH`; after C is out, `hit(C, A, 0, 101) == PASS_THROUGH`.
    - `respawnResetsAndProtects`: C out at 100; `respawnsDue(159)` empty; `respawnsDue(160) == [C]`; `hitsLeft(C) == 3`; `isProtected(C, 199)`; not at 200.
    - `outOfBoundsGivesNoPoint`: `outOfBounds(C, 100)` true; all `eliminations` 0; second call false.
    - `removedPlayerIsIgnored`: `remove(C)`; `hit(C, A, 0, 100) == PASS_THROUGH`; `respawnsDue` never lists C; a ball from removed shooter C still puts A out on the third hit at t≥40 with `eliminations` unchanged for everyone (`hit(A, C, 1, t)`).
    - `topSortsByEliminationsThenJoinOrder`: give B 2 eliminations, A 1 → `top(3) == [B, A, C]`; equal counts keep join order.
- [ ] **Step 2: Run** `GRADLE :paintball:test` — Expected: FAIL (classes missing).
- [ ] **Step 3: Implement** the four classes and move `HitTracker`.
- [ ] **Step 4: Run** `GRADLE :paintball:test` — Expected: PASS.
- [ ] **Step 5: Commit** — `"Paintball: settings, team score, clock and combat rules"`

---

### Task 2: Rules — fire gate and spread

**Files:**
- Create: `paintball/.../rules/{FireGate,Spread}.java`, tests `FireGateTest`, `SpreadTest`

**Interfaces:**
- Produces:
  - `FireGate` (one per player): `enum Decision { FIRE, QUEUED, IGNORED }`; `Decision request(long now, int interval)` — first shot or `now - lastShot >= interval` → `FIRE` (records `lastShot = now`); else if nothing queued → `QUEUED`; else `IGNORED`. `boolean pollQueued(long now, int interval)` — true (and records `lastShot = now`, clears the queue) when a shot is queued and `now - lastShot >= interval`. `void clear()` drops a queued shot.
  - `Spread.apply(double x, double y, double z, double maxDegrees, RandomGenerator rng) -> double[3]`: unit vector inside the cone of half-angle `maxDegrees` around the (normalised) input. Uniform over the cone's solid angle:
    ```
    cosT = 1 - u * (1 - cos(maxRad))      // u = rng.nextDouble()
    sinT = sqrt(1 - cosT²); phi = 2π * rng.nextDouble()
    pick helper axis = (|x| < 0.9 ? X : Y); a = normalize(cross(dir, helper)); b = cross(dir, a)
    result = dir*cosT + a*(sinT*cos phi) + b*(sinT*sin phi)
    ```
    `maxDegrees == 0` returns the normalised input exactly.

- [ ] **Step 1: Write the failing tests**
  - `FireGateTest.firstShotFires`, `secondWithinIntervalIsQueuedThenFiresAtInterval` (FIRE at 0; `request(4,5) == QUEUED`; `pollQueued(4,5)` false; `pollQueued(5,5)` true; `pollQueued(6,5)` false), `onlyOneQueued` (`request(1,5)` QUEUED, `request(3,5)` IGNORED), `clearDropsQueued`.
  - `FireGateTest.heldTriggerEvery4TicksFiresEvery5`: simulate ticks 0..199, `request` at every multiple of 4, `pollQueued` every tick; collect fire ticks → exactly 40 shots, consecutive gaps all `== 5`.
  - `SpreadTest.zeroSpreadKeepsDirection` (input (0,0,2) → (0,0,1)), `staysInsideCone` (10 000 draws with seeded `SplittableRandom(1)`, 1°: every result unit length ±1e-9 and angle to input ≤ 1° + 1e-9), `worksStraightUpAndAlongX` (inputs (0,1,0) and (1,0,0) also stay inside the cone), `actuallySpreads` (some draw > 0.5° away).
- [ ] **Step 2: Run** `GRADLE :paintball:test` — Expected: FAIL.
- [ ] **Step 3: Implement** `FireGate` and `Spread`.
- [ ] **Step 4: Run** `GRADLE :paintball:test` — Expected: PASS.
- [ ] **Step 5: Commit** — `"Paintball: fire-rate gate and spread cone"`

---

### Task 3: Plugin wiring — config, messages, game registration, gun item

**Files:**
- Modify: `PaintballPlugin.java`
- Create: `PaintballGame.java`, `Gun.java`, `paintball/src/main/resources/config.yml`, `paintball/src/main/resources/messages.yml`

**Interfaces:**
- Consumes: `Settings` (Task 1); core API `GameRegistry`, `GameType`, `ModeSpec`, `MatchContext`, `MatchHandler`; `be.dbsdw.minigames.core.Messages`.
- Produces:
  - `PaintballPlugin.onEnable`: `saveDefaultConfig()`; build `Settings` from `config.yml` (keys in Global Constraints; seconds/minutes × 20 → ticks). On `IllegalArgumentException`: log `[paintball] config.yml: <message> — Paintball is disabled` at SEVERE and `getServer().getPluginManager().disablePlugin(this)`; nothing registered. Load `Messages.load(this)`. Get `GameRegistry` like `TestGamePlugin` does (missing → `IllegalStateException`). Register `PaintballGame`. Log `[paintball] enabled: tdm <min>-<max> per team, first to <n>, <m> min`.
  - `PaintballGame implements GameType`: id `paintball`, display name `messages "game.name"` (`Paintball`), icon `Material.SNOWBALL`, modes `[new ModeSpec("paintball", "tdm", <messages raw "mode.tdm">, 2, minPerTeam, maxPerTeam, Set.of())]`; `newMatch(ctx)` → `new TdmMatch(ctx, this)`. Holds `Map<UUID, TdmMatch> players` and `Map<UUID, TdmMatch> balls` (entity id) with `match(Player)`, `ballMatch(Entity)`, register/unregister methods used by `TdmMatch`; plus `settings()`, `messages()`, `plugin()`.
  - `Gun`: `static NamespacedKey KEY = paintball:item`; `ItemStack gun(Messages)` — `CARROT_ON_A_STICK`, name `item.gun`, unbreakable, PDC `KEY = "gun"`; `ItemStack ball(int team)` — `RED_DYE` for 0, `BLUE_DYE` for 1; `boolean isGun(ItemStack)`. Snowballs we launch get PDC `KEY = "ball"`; `boolean isBall(Entity)`.
  - Messages keys (Dutch; wording builder's choice): `game.name`, `mode.tdm` ("Team Deathmatch"), `item.gun`, `hud.title`, `hud.score` (`<team>: <score>`), `hud.time` (`Tijd: <time>`), `hud.bossbar` (`Tijd over: <time>`), `hud.hits` (`<icons>`), `hud.protected`, `hud.marker`, `out.title` ("Uit!"), `out.subtitle` ("Terug over <seconds>"), `feed.out` (`<victim> is uit (door <shooter>)`), `feed.out-bounds` (`<victim> is uit (buiten de arena)`), `result.score`, `result.top` (`<rank>. <name> – <count> uitgeschakeld`), `result.top-header`.

- [ ] **Step 1: Implement** the files above. `TdmMatch` may be an empty `MatchHandler` stub in this task so the build passes.
- [ ] **Step 2: Run** `GRADLE build` — Expected: BUILD SUCCESSFUL, all tests pass.
- [ ] **Step 3: Commit** — `"Paintball: config, messages, game registration and gun item"`

---

### Task 4: The match — shooting, hits, outs, respawns, bounds, end

**Files:**
- Create/replace: `tdm/TdmMatch.java`, `PaintballListener.java`
- Modify: `PaintballPlugin.java` (register the listener)

**Interfaces:**
- Consumes: Tasks 1–3; `MatchContext` (`team`, `teamOf`, `teamName`, `ticks`, `arena().spawns/bounds/world`, `finish`).
- Produces (Task 5 adds the HUD calls):
  - `TdmMatch implements MatchHandler`; package-visible `void trigger(Player)`, `void onBallHit(ProjectileHitEvent)`.
  - `PaintballListener`: `PlayerInteractEvent` (main hand, `RIGHT_CLICK_AIR`/`RIGHT_CLICK_BLOCK`, holding the gun, player in a match) → `event.setCancelled(true)` + `match.trigger(player)`. **Do not use `ignoreCancelled = true`**: Paper fires air clicks already marked cancelled. `ProjectileHitEvent` on a ball → `game.ballMatch(ball)`; none → cancel + remove the ball; else `match.onBallHit(event)`. Cancel `PlayerDropItemEvent`, `PlayerSwapHandItemsEvent`, `InventoryClickEvent`, `InventoryDragEvent` touching the gun (same checks as core's `LobbyListener`).

Behaviour of `TdmMatch`:
- `start()`: build `Combat` from `ctx.team(0/1)` (join order = team order), `TeamScore(2, winEliminations)`, a `FireGate` per player; `combat.start(0)`; remember each player's name (for results after they leave); give each player the gun in hotbar slot 0; register players in `PaintballGame`.
- `trigger(p)`: ignore if `combat.isOut`. `gate.request(now, shotIntervalTicks)`: `FIRE` → `fire(p)`; `QUEUED`/`IGNORED` → nothing.
- `fire(p)`: `combat.shot(p)`; direction = `Spread.apply(eye direction, spreadDegrees, ThreadLocalRandom.current())`; `p.launchProjectile(Snowball.class, dir × speed)`; set the ball's item (`Gun.ball(team)`), PDC ball tag; track `ball id -> Shot(shooter, team, spawnTick)` in the match and `PaintballGame.balls`; `p.setCooldown(gun, shotIntervalTicks)`; shot sound at the shooter.
- `onBallHit(e)`: untrack the ball. Hit entity not a `Player` → leave the event (ball breaks). Player → `combat.hit(victim, shot.shooter, shot.team, now)`:
  - `PASS_THROUGH` → `e.setCancelled(true)` and keep tracking the ball (it flies on).
  - `ABSORBED` → nothing more.
  - `HIT` → victim `playHurtAnimation(0)`, hit sound, dust burst in the shooter's team colour; shooter (if online) hit-marker sound + marker.
  - `OUT` → `eliminate(victim, shooter)`; then `score.add(team)` present → `ctx.finish(win(team, resultLines()))`.
- `eliminate(victim, shooterOrNull)`: big splash particles + out sound; `hideEntity` the victim from every online player; add transient `MOVEMENT_SPEED` and `JUMP_STRENGTH` modifiers (key `paintball:frozen`, `ADD_MULTIPLIED_TOTAL`, `-1`); `gate.clear()`; feed message to everyone in the match.
- `tick()`, in this order, with `now = ctx.ticks()`:
  1. `now >= timeLimitTicks` → finish with `score.leader()` win or draw, `resultLines()`; return.
  2. For each player whose `gate.pollQueued(now, interval)` is true and who is not out → `fire`.
  3. Remove balls with `now - spawnTick >= ballLifetimeTicks` (entity `remove()`, untrack).
  4. Every alive player not in `arena().world()` or with a position outside `arena().bounds()` → `combat.outOfBounds` → `eliminate(p, null)` (feed `feed.out-bounds`), teleport to a random own-team spawn so they wait inside the arena.
  5. `combat.respawnsDue(now)` → teleport to a random spawn of their team, `showEntity` to everyone, remove the frozen modifiers, respawn sound.
- `playerLeft(p, team)`: `combat.remove`, drop their gate, `cleanup(p)`, unregister from `PaintballGame`. Balls they fired stay tracked (decision 5).
- `end()`: for every player still in the match `cleanup(p)`; remove every tracked ball entity; unregister all players and balls from `PaintballGame`.
- `cleanup(p)` (used by leave and end, safe to call twice): `showEntity` to every online player, remove the frozen modifiers, remove the gun from the inventory. (Task 5 adds HUD removal here.)
- `resultLines()`: `result.score` (`Rood 30 – Blauw 24`), `result.top-header`, then `combat.top(3)` as `result.top` using remembered names.

- [ ] **Step 1: Implement** `TdmMatch` and `PaintballListener`; register the listener in `PaintballPlugin`.
- [ ] **Step 2: Run** `GRADLE build` — Expected: BUILD SUCCESSFUL, all tests pass.
- [ ] **Step 3: Commit** — `"Paintball: TDM match with gun, paintballs, hits, outs and respawns"`

---

### Task 5: HUD — sidebar, boss bar, action bar, titles

**Files:**
- Create: `tdm/Hud.java`
- Modify: `tdm/TdmMatch.java`

**Interfaces:**
- Consumes: `Clock`, `Combat`, `TeamScore`, `Settings`, `Messages`, `MatchContext`.
- Produces: `Hud(MatchContext, Messages, Settings)` with `void show(Player)`, `void hide(Player)` (safe twice), `void refresh(long now, TeamScore, Combat)`, `void hits(Player, int left, boolean protectedNow)`, `void marker(Player, long now)`, `void outTitle(Player, long secondsLeft)`.

Behaviour:
- One new `Scoreboard` per match (`Bukkit.getScoreboardManager().getNewScoreboard()`), objective `paintball` in `DisplaySlot.SIDEBAR`, title `hud.title`, `numberFormat(NumberFormat.blank())`; four fixed entries with `customName`: red score, blue score, empty line, time (`Clock.format(timeLimitTicks - now)`). `show` sets it on the player; `hide` puts back `Bukkit.getScoreboardManager().getMainScoreboard()`.
- One boss bar (`BossBar.bossBar`, `hud.bossbar`, progress `(timeLimit - now)/timeLimit` clamped 0..1, colour white, `NOTCHED_10`); shown with `player.showBossBar`, removed with `hideBossBar`.
- Action bar: `hits-left` icons — `left` filled `●` in the player's team colour + `hitsToOut - left` grey `●` — plus `hud.protected` while protected; for 20 ticks after a hit marker, `hud.marker` comes first.
- Titles: on out, `out.title` + `out.subtitle` with the seconds left, re-sent each whole second until respawn (fade-in 0).
- `TdmMatch` wiring: `start` → `show` everyone; `tick` → `refresh` + action bars every 20 ticks and out titles each whole second; immediately after a hit, out, respawn or shot that ends protection → that player's action bar; `cleanup` → `hide`.

- [ ] **Step 1: Implement** `Hud`; wire it into `TdmMatch`.
- [ ] **Step 2: Run** `GRADLE build` — Expected: BUILD SUCCESSFUL, all tests pass.
- [ ] **Step 3: Commit** — `"Paintball: HUD with sidebar, boss bar, hits left and out titles"`

---

### Task 6: End-to-end checks on node1, docs, push

**Files:**
- Modify: `tools/e2e/bot/hop.js`, `tools/e2e/README.md`, `README.md`
- Create: `docs/superpowers/notes/2026-11-18-plan-3-e2e-results.md`

**Interfaces:**
- Produces: `hop.js` new steps `aim:<name>` (turn to look at the eyes of the named player from the last known entity positions; `aim_failed` if unknown) and `fire:<count>` (right-click the held item `count` times, 4 ticks = 200 ms apart, like a held button). New events: `actionbar {text}`, `sidebar {lines}` (from `scoreboard_score`/`update_score` custom names), `bossbar {title}`, `visible {name, shown}` (player info / entity destroy of a named player), `entity_spawn {type}`. Existing flags and steps keep working.

- [ ] **Step 1: Extend `hop.js`** as above.
- [ ] **Step 2: Set up the harness on node1** per `tools/e2e/README.md`: `MinigameCore` + `Paintball` on `minigames`, `DbsdwBridge` on `survival` (ticket secret copied file to file, never printed), `connection-throttle: 0` on both. Arena file `plugins/MinigameCore/arenas/test.yml` with mode `paintball:tdm`, spawns 10 blocks apart in open flat ground (so aimed bots hit), bounds 30×30×20 around them. Test `plugins/Paintball/config.yml`: `min-per-team: 1`, `win-eliminations: 2`, `time-limit-minutes: 1` (only for checks 1–6; check 7 uses the defaults with `min-per-team: 1`).
- [ ] **Step 3: Run the checks.** Two bots queue through the menu, as in plan 2. Each check names its evidence:
  1. **Match start:** `held_items` slot 0 `Paintgeweer`; `sidebar` lines with both teams at 0 and `Tijd: 1:00`; `bossbar` `Tijd over: 1:00`; `actionbar` with 3 filled icons and `hud.protected`.
  2. **Shooting and hits:** A `aim:B`, `fire:1` after protection ends → B gets `actionbar` with 2 filled icons; A gets the marker `actionbar`.
  3. **Fire rate:** A `fire:20` (button held 4 s, a click every 4 ticks) → B sees `entity_spawn {type: snowball}` 16 ± 1 times over those 4 s (not 20, not 10).
  4. **Out and respawn:** third hit on B → B `title` `Uit!` + `Terug over 3/2/1`; A sees `visible {B, false}`; chat `feed.out`; after 3 s B `position` at a team-1 spawn, `visible {B, true}`, sidebar `Rood: 1`.
  5. **Out of bounds:** console `tp B <outside bounds>` → chat `feed.out-bounds`, sidebar scores unchanged, B back at a spawn after 3 s.
  6. **Win and cleanup:** second elimination → `title` `Rood wint!`, chat score + top lines; log `[match] paintball:tdm on test ended: winner team 0`; after 10 s both bots in the lobby with no `sidebar`/`bossbar` (empty events), `held_items` = lobby items, both `visible`. Also: B quits while out in a fresh match → A wins by last team standing; on rejoin B is visible and can move.
  7. **Time limit draw:** defaults + `time-limit-minutes: 1`, no shots → `title` `Gelijkspel!`; log `ended: draw`.
  8. **Gun protection:** `drop:0` mid-match → next `held_items` still has `Paintgeweer`.
- [ ] **Step 4: Write `docs/superpowers/notes/2026-11-18-plan-3-e2e-results.md`** (each check: PASS/FAIL + evidence) and update `README.md` (Paintball config keys, placeholder items until plan 6) and `tools/e2e/README.md` (new steps and events).
- [ ] **Step 5: Tear down on node1:** `docker compose down`, remove `/opt/dbsdw-e2e` and pulled images.
- [ ] **Step 6: Commit and push the branch; confirm CI green** — `"Record plan 3 end-to-end results; document paintball TDM"`.

---

## Out of scope (later plans)

CTF (plan 4); power-ups, block splats, stats reporting, leaderboards (plan 5); pack models, sounds, glyphs, splash overlay and pack hosting (plan 6); the built lobby and arena (plan 7); deployment and live changes (plan 8). A real-client feel test (range, fire rate, hit feedback) needs the owner's client and the pack; it is noted for plan 6/7, not done here.
