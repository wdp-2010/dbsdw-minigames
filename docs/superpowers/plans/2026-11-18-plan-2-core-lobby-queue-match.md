# Plan 2: Core — Lobby, Queue, Teams, Arenas, Match Lifecycle — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A player who arrives on the minigame server lands in a lobby, opens a menu (lobby item or a game NPC), queues for a mode, and when enough players are queued and an arena is free, a countdown starts, teams are split, the match runs, a results screen shows, and everyone returns to the lobby — proven end to end with a trivial test game.

**Architecture:** MinigameCore gets a small game API (`core/api`) that game plugins register with through Bukkit's `ServicesManager`. All rules (arena fit and validation, arena reservation, queues and countdowns, team split, match phases and "last team standing") are plain Java with unit tests; a thin Bukkit layer (lobby, items, menus, NPCs, admin commands, the match manager ticking every server tick) sits on top. A test-only plugin `testgame` (never deployed) exercises the API in CI and in the e2e run.

**Tech Stack:** Java 25, Paper API 26.3 (`26.3.build.255-stable`), Gradle 9.9.1 (existing `build-logic` conventions, version catalog), JUnit (version from the catalog), Bukkit `YamlConfiguration` for arena files, the existing `tools/e2e/` harness on node1.

**Spec:** `docs/superpowers/specs/2026-11-18-minigames-paintball-design.md` (§4.4, §5, §5.1, §6.2 step 3, §6.3, §6.4, §7.7 end-of-match screen, §11.1, §12 permissions/config, §13 step 2). Plan 1 (`docs/superpowers/plans/2026-11-18-plan-1-travel.md`) is merged.

## Global Constraints

- Paper API `io.papermc.paper:paper-api:26.3.build.255-stable`, Java 25, `-Xlint:all -Werror`, `org.gradle.warning.mode=fail`. No deprecated API.
- Gradle runs in Docker (no local Java): `docker run --rm -u 1000:1000 -e HOME=/tmp -e GRADLE_USER_HOME=/gh -v /tmp/dbsdw-gradle-home:/gh -v "$PWD":/work -w /work eclipse-temurin:25-jdk ./gradlew --no-daemon -q <tasks>`. Below, `GRADLE <tasks>` means this command.
- Game logic classes (`core/.../arena`, `queue`, `match` plain classes listed per task) use no Bukkit/Paper/Adventure types.
- MinigameCore runs on Paper only (not Folia): the Bukkit scheduler is allowed in `core/` and `testgame/`. `bridge/` is not touched.
- Player-facing text: Dutch defaults in `core/src/main/resources/messages.yml`, MiniMessage. Log lines English, prefixed `[lobby]`, `[queue]`, `[match]`, `[arena]`.
- Team index → name/colour comes from `messages.yml` keys `team.0` (`<red>Rood</red>`), `team.1` (`<blue>Blauw</blue>`), `team.2`, `team.3`.
- Defaults (`core/src/main/resources/config.yml`): `queue.countdown-seconds: 30`, `queue.full-countdown-seconds: 10`, `match.results-seconds: 10`, `lobby:` location (world, x, y, z, yaw, pitch; empty until `/mg setlobby`).
- Admin permission `minigames.admin` (default `op`) for every `/mg` subcommand. Player commands in this plan: `/leave` (plus `/survival` from plan 1).
- No vanilla damage and no hunger for any player anywhere on the minigame server.
- Test servers only on node1 via `tools/e2e/`, never on the WDP host, never against live. Tear down afterwards.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Decisions this plan makes (owner: check these)

1. **One lobby spawn** (`/mg setlobby`) is the return point for every arena; the spec's per-arena "lobby return point" is dropped (there is one lobby).
2. **One queue per player.** Clicking another mode moves the player there.
3. **The arena is reserved when the countdown starts**, so two queues never race for one arena; the second queue waits with a "waiting for a free arena" message.
4. **The trivial test game is a separate test-only plugin** (`testgame/`, jar `MgTestGame`), built in CI and used in e2e, never deployed.
5. **Stats are not in this plan.** The game API gets stat reporting in plan 5 together with storage; the lobby stats item comes then too.
6. **Arena world must be loaded at startup**; an arena whose world is missing is skipped with one log line (no world loading/creation in v1).

## Review Focus

1. **Queued player quits or transfers during the countdown.** They leave the queue; if it drops below the minimum the countdown stops, the arena is released and the rest are told. → `QueueBookTest.leaveBelowMinimumCancelsAndReleasesArena` (Task 2), quit handling (Task 7), e2e check 5 (Task 8).
2. **Players leave mid-match.** One team left → it wins (`end()` called once); if the rest leave during the results, the arena is released at once (no arena stuck "busy" forever). → `MatchStateTest.lastTeamStandingWins`, `MatchStateTest.everyoneLeftEndsResultsAtOnce` (Task 3), e2e check 4 (Task 8).
3. **Server stops or the game plugin disables during a match.** Every running match is aborted with `end()` called before plan 1's shutdown transfer; a disabled game's queues are cleared and its players told. → Task 7 steps; e2e check 7 (Task 8).
4. **Two modes (or two queues) want the only arena.** Only one gets it; the other waits and starts once the arena is released. → `ArenaPoolTest.reservedArenaIsNotOfferedAgain`, `QueueBookTest.waitsForFreeArenaThenStarts` (Tasks 1–2).
5. **Broken arena file** (missing spawn for a team, point outside bounds, unknown world, mode not registered). Server keeps running, one clear log line per arena, and the menu shows "geen arena" instead of a countdown that can never start. → `ArenaValidatorTest`, `ArenaStoreTest.unknownWorldIsSkipped` (Tasks 1, 4), menu lore (Task 6).

---

## File Structure

```
settings.gradle.kts                          modify: include "testgame"
core/src/main/java/be/dbsdw/minigames/core/
  api/ArenaFeature.java        enum FLAGS
  api/ModeSpec.java            record: one mode of a game (teams, min/max per team, features)
  api/GameType.java            what a game plugin registers
  api/MatchHandler.java        callbacks the core calls on a game's match
  api/MatchContext.java        what the core offers a running match
  api/ArenaView.java           arena positions as Bukkit Locations
  api/MatchOutcome.java        winner team or draw + result lines
  api/GameRegistry.java        register/look up games (ServicesManager service)
  arena/Pos.java               plain position record
  arena/ArenaDefinition.java   plain arena: modes, spawns, flags, power-ups, corners; fit + bounds
  arena/ArenaValidator.java    save-time errors and warnings
  arena/ArenaPool.java         free/busy arenas, random reservation
  arena/ArenaStore.java        arenas/<name>.yml load/save (YamlConfiguration)
  arena/BukkitArenaView.java   ArenaView over an ArenaDefinition + World
  queue/QueueBook.java         all queues, one per mode; countdown rules
  queue/QueueEvent.java        sealed events emitted by QueueBook.tick()
  queue/QueueTimings.java      record(countdownSeconds, fullCountdownSeconds)
  match/TeamSplit.java         shuffle + TeamBalancer
  match/MatchState.java        plain teams/phases/leave outcome
  match/GameRegistryImpl.java
  match/RunningMatch.java      MatchContext impl, one per arena in use
  match/MatchManager.java      ticks queues and matches, joins/leaves, shutdown
  lobby/LobbyService.java      lobby spawn, lobby state, lobby items
  lobby/LobbyListener.java     join → lobby, item use, protections, quit
  lobby/LeaveCommand.java      /leave
  menu/GamesMenu.java, menu/ModesMenu.java   InventoryHolder menus
  menu/MenuListener.java
  npc/GameNpc.java             one Mannequin per game in the lobby
  admin/MgCommand.java         /mg arena …, /mg setlobby, /mg npc …
  MinigameCorePlugin.java      modify: wiring, tick task, shutdown order
core/src/main/resources/{config.yml,messages.yml,plugin.yml}   modify
core/src/test/java/be/dbsdw/minigames/core/{arena,queue,match}/…Test.java
testgame/build.gradle.kts, testgame/src/main/resources/plugin.yml
testgame/src/main/java/be/dbsdw/minigames/testgame/{TestGamePlugin,PointsRace,PointsMatch}.java
testgame/src/test/java/be/dbsdw/minigames/testgame/PointsRaceTest.java
tools/e2e/bot/hop.js                         modify: --do steps, new events
tools/e2e/README.md                          modify
docs/superpowers/notes/2026-11-18-plan-2-e2e-results.md   create
README.md                                    modify: lobby, arenas, admin commands
```

---

### Task 1: Game API records + plain arena model, validator, pool

**Files:**
- Create: `core/.../api/ArenaFeature.java`, `core/.../api/ModeSpec.java`, `core/.../arena/Pos.java`, `core/.../arena/ArenaDefinition.java`, `core/.../arena/ArenaValidator.java`, `core/.../arena/ArenaPool.java`
- Test: `core/src/test/java/be/dbsdw/minigames/core/arena/{ArenaDefinitionTest,ArenaValidatorTest,ArenaPoolTest}.java`, `core/src/test/java/be/dbsdw/minigames/core/api/ModeSpecTest.java`

**Interfaces:**
- Produces:
  - `enum ArenaFeature { FLAGS }`
  - `record ModeSpec(String gameId, String id, String displayName, int teams, int minPerTeam, int maxPerTeam, Set<ArenaFeature> features)` — compact constructor throws `IllegalArgumentException` unless ids match `[a-z0-9_-]+`, `teams >= 2`, `1 <= minPerTeam <= maxPerTeam`; copies `features` with `Set.copyOf`. Methods `String key()` → `gameId + ":" + id`, `int startMinimum()` → `teams * minPerTeam`, `int maximum()` → `teams * maxPerTeam`. `displayName` is a MiniMessage string (kept plain-Java).
  - `record Pos(double x, double y, double z, float yaw, float pitch)`
  - `record ArenaDefinition(String name, String world, Set<String> modes, Map<Integer, List<Pos>> spawns, Map<Integer, Pos> flags, List<Pos> powerups, Pos corner1, Pos corner2)` — corners may be null while editing; all collections copied immutable. `static ArenaDefinition empty(String name, String world)`; withers `withModes(Set<String>)`, `withSpawn(int team, Pos)` (appends), `withFlag(int team, Pos)` (replaces), `withPowerup(Pos)`, `withCorner(int which /*1|2*/, Pos)`; `boolean hasBounds()`; `boolean contains(Pos)` (inclusive block box: `min <= v < max + 1` per axis, using the corners' block coordinates); `boolean fits(ModeSpec)` (lists `mode.key()`, has bounds, ≥1 spawn for every team `0..teams-1`, and a flag for every team if `FLAGS` is required).
  - `record Validation(List<String> errors, List<String> warnings)` nested in `ArenaValidator`; `static Validation ArenaValidator.validate(ArenaDefinition, Function<String, Optional<ModeSpec>> modes)`.
  - `ArenaPool(Collection<ArenaDefinition>)`: `Optional<ArenaDefinition> reserve(ModeSpec, RandomGenerator)` (uniform random among free arenas that fit), `void release(String name)`, `boolean anyFits(ModeSpec)` (busy or not), `boolean isBusy(String name)`, `void put(ArenaDefinition)` (add/replace; a running match keeps its own copy), `Collection<ArenaDefinition> all()`.

- [ ] **Step 1: Write the failing tests**

`ModeSpecTest`: `rejectsOneTeam`, `rejectsMinAboveMax`, `rejectsBadId` (`"TDM"`), `keyAndCounts` (`new ModeSpec("paintball","tdm","TDM",2,2,8,Set.of())` → `key()=="paintball:tdm"`, `startMinimum()==4`, `maximum()==16`).

`ArenaDefinitionTest` (fixture: corners `(0,60,0)` and `(59,80,39)`, mode `paintball:tdm` 2 teams):
- `containsIsInclusiveOfCornerBlocks`: `(59.9,80.5,39.9)` inside, `(60.0,70,10)` outside, `(-0.1,70,10)` outside.
- `fitsNeedsSpawnForEveryTeam`: spawn only for team 0 → false; add team 1 → true.
- `fitsNeedsListedMode`: same arena, mode `paintball:ctf` not listed → false.
- `fitsNeedsFlagsWhenRequired`: CTF mode with `FLAGS`, listed, spawns both teams, flag only team 0 → false; both → true.
- `fitsNeedsBounds`: no corners → false.

`ArenaValidatorTest`:
- `missingBoundsIsError` → errors contain a line with `"bounds"`.
- `noModesIsError`.
- `pointOutsideBoundsIsError`: spawn at `(100,70,10)` → error naming `"spawn"` and `"team 0"`.
- `registeredModeThatDoesNotFitIsError`: lists `paintball:tdm` (registered), only team-0 spawn → error naming the mode.
- `unregisteredModeIsWarningOnly`: lists `paintball:tmd` (lookup returns empty) → no error for it, one warning containing `"paintball:tmd"`.
- `validArenaHasNoErrors`.

`ArenaPoolTest`:
- `reservedArenaIsNotOfferedAgain`: one fitting arena → first `reserve` present, second empty, after `release` present again.
- `onlyFittingArenasAreReserved`: two arenas, one lists only `ctf` → `reserve(tdm)` always returns the tdm one (seeded `new Random(1)` over 20 calls).
- `anyFitsIgnoresBusy`: reserved arena still `anyFits == true`; arena that fits nothing → false.

- [ ] **Step 2: Run tests to verify they fail**

Run: `GRADLE :core:test` — Expected: compilation failure (types missing).

- [ ] **Step 3: Implement the types above** exactly as in Interfaces. Error/warning text is English, one line each, naming the arena, the point kind and the team.

- [ ] **Step 4: Run tests to verify they pass**

Run: `GRADLE :core:test` — Expected: BUILD SUCCESSFUL, the new tests pass.

- [ ] **Step 5: Commit** — `git add core && git commit -m "MinigameCore: arena model, validation and reservation"`

---

### Task 2: Queues and countdown rules (plain Java)

**Files:**
- Create: `core/.../queue/QueueBook.java`, `core/.../queue/QueueEvent.java`, `core/.../queue/QueueTimings.java`
- Test: `core/src/test/java/be/dbsdw/minigames/core/queue/QueueBookTest.java`

**Interfaces:**
- Consumes: `ModeSpec`, `ArenaDefinition`, `ArenaPool` (Task 1).
- Produces:
  - `record QueueTimings(int countdownSeconds, int fullCountdownSeconds)` (validates `1 <= full <= countdown`).
  - `sealed interface QueueEvent` with records `CountdownStarted(String mode, String arena, int seconds)`, `CountdownTick(String mode, int seconds, List<UUID> queued)`, `CountdownCancelled(String mode, List<UUID> queued)`, `WaitingForArena(String mode, List<UUID> queued)`, `Start(ModeSpec mode, ArenaDefinition arena, List<UUID> players)`.
  - `QueueBook(ArenaPool pool, QueueTimings timings, RandomGenerator rnd)`:
    - `void addMode(ModeSpec)` / `List<UUID> removeMode(String key)` (returns who was queued; releases a reserved arena)
    - `enum Toggle { JOINED, LEFT, MOVED }`; `Toggle toggle(UUID player, String modeKey)` — throws `IllegalArgumentException` for an unknown mode
    - `boolean remove(UUID player)`; `Optional<String> queueOf(UUID)`; `int size(String key)`; `OptionalInt secondsLeft(String key)`
    - `List<QueueEvent> tick()` — called once per server tick.

Rules for `tick()`, per mode, in the order modes were added:
1. No countdown and `size >= startMinimum`: `pool.reserve(mode)`. Found → countdown of `fullCountdownSeconds` if `size >= maximum` else `countdownSeconds`, emit `CountdownStarted`. Not found → emit `WaitingForArena` once (again only after the queue dropped below the minimum and came back).
2. Countdown running and `size < startMinimum`: release the arena, stop, emit `CountdownCancelled`.
3. Countdown running, `size >= maximum` and more than `fullCountdownSeconds` left: set to `fullCountdownSeconds`, emit `CountdownTick` with that value.
4. Otherwise count down one tick. On a whole second in {30, 20, 10, 5, 4, 3, 2, 1} emit `CountdownTick`. At zero: take the first `maximum` players by join order, remove them from all queues, emit `Start` (the arena stays reserved; the match releases it). Players beyond the maximum stay queued in order.

- [ ] **Step 1: Write the failing tests** (mode `t:m` 2 teams 1..2 per team → start 2, max 4; timings 30/10; one fitting arena unless stated; helper `ticks(book, n)` collects events)

- `toggleJoinsThenLeaves`: JOINED then LEFT, `queueOf` empty after.
- `toggleOtherModeMoves`: in `t:m`, toggle `t:n` → `MOVED`, `size("t:m")==0`, `size("t:n")==1`.
- `belowMinimumNoCountdown`: 1 player, 100 ticks → no events.
- `countdownStartsAtMinimumAndReservesArena`: 2 players → first tick `CountdownStarted(seconds=30)`, `pool.isBusy("a")`.
- `fullQueueShortensTo10`: 2 queued, countdown at 30 → two more join → next tick `CountdownTick(seconds=10)`, `secondsLeft==10`.
- `fullFromTheStartUses10`: 4 queued before the first tick → `CountdownStarted(seconds=10)`.
- `startsAfterCountdownWithFirstMaximumPlayers`: 5 queued (join order p1..p5) → after 10 s worth of ticks a `Start` with `[p1..p4]`; `queueOf(p5)` still `t:m`; p1 no longer queued.
- `leaveBelowMinimumCancelsAndReleasesArena`: 2 queued, countdown running, p2 `remove` → next tick `CountdownCancelled`, `!pool.isBusy("a")`.
- `waitsForFreeArenaThenStarts`: arena reserved by someone else beforehand → 2 queued → `WaitingForArena` once over 100 ticks, no `CountdownStarted`; `pool.release("a")` → next tick `CountdownStarted`.
- `announcesOnlyAtFixedSeconds`: count `CountdownTick` events over a full 30 s countdown → seconds `[20,10,5,4,3,2,1]` (30 is the `CountdownStarted`).
- `removeModeReturnsQueuedAndReleasesArena`.

- [ ] **Step 2: Run** `GRADLE :core:test` — Expected: compilation failure.
- [ ] **Step 3: Implement** `QueueBook` per the rules above (a `LinkedHashMap<String, ModeQueue>` of private per-mode state; join order kept with `LinkedHashSet<UUID>`).
- [ ] **Step 4: Run** `GRADLE :core:test` — Expected: all pass.
- [ ] **Step 5: Commit** — `"MinigameCore: queues and countdown rules"`

---

### Task 3: Team split and match state (plain Java)

**Files:**
- Create: `core/.../match/TeamSplit.java`, `core/.../match/MatchState.java`
- Test: `core/src/test/java/be/dbsdw/minigames/core/match/{TeamSplitTest,MatchStateTest}.java`

**Interfaces:**
- Consumes: `TeamBalancer.pickTeam(int[])` (existing).
- Produces:
  - `static List<List<UUID>> TeamSplit.split(List<UUID> players, int teams, RandomGenerator rnd)` — shuffles a copy, then places each player with `TeamBalancer.pickTeam`.
  - `MatchState(List<List<UUID>> teams, int resultsTicks)`; `enum Phase { RUNNING, ENDING, DONE }`; `Phase phase()`; `OptionalInt teamOf(UUID)`; `List<UUID> members(int team)`; `List<UUID> everyone()`;
    `sealed interface LeaveResult` with `None`, `LastTeamStanding(int team)` — `LeaveResult leave(UUID)` (only `RUNNING` produces `LastTeamStanding`: the first leave that leaves exactly one team with players; leaving during `ENDING` just removes);
    `boolean finish()` (RUNNING → ENDING, false if already past RUNNING); `void abort()` (→ DONE from any phase); `boolean tickEnding()` (counts down `resultsTicks` while ENDING, true on the tick it reaches DONE, and true at once if nobody is left).

- [ ] **Step 1: Write the failing tests**

`TeamSplitTest`: `sizesDifferByAtMostOne` (7 players, 2 teams → sizes {4,3}), `everyPlayerOnce` (no duplicates, all present), `sameSeedSameSplit`.

`MatchStateTest`:
- `leaveKeepsMatchWhileTwoTeamsHavePlayers` → `None`.
- `lastTeamStandingWins`: 1v2, the team-0 player leaves → `LastTeamStanding(1)`.
- `everyoneLeftEndsResultsAtOnce`: 1v1, first leave → `LastTeamStanding(1)`, `finish()`, second leave → `None`, next `tickEnding()` → true, `phase()==DONE`.
- `finishOnlyOnce`: `finish()` true then false.
- `resultsLastConfiguredTicks`: `resultsTicks=200` → `tickEnding()` false 199 times, true on the 200th, `phase()==DONE`.
- `leaveDuringEndingJustRemoves`.

- [ ] **Step 2: Run** `GRADLE :core:test` — Expected: compilation failure.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `GRADLE :core:test` — Expected: all pass.
- [ ] **Step 5: Commit** — `"MinigameCore: team split and match state"`

---

### Task 4: Game API, registry, arena storage, test game

**Files:**
- Create: `core/.../api/{GameType,MatchHandler,MatchContext,ArenaView,MatchOutcome,GameRegistry}.java`, `core/.../match/GameRegistryImpl.java`, `core/.../arena/ArenaStore.java`, `core/.../arena/BukkitArenaView.java`
- Create: `testgame/build.gradle.kts`, `testgame/src/main/resources/plugin.yml`, `testgame/src/main/java/be/dbsdw/minigames/testgame/{TestGamePlugin,PointsRace,PointsMatch}.java`
- Modify: `settings.gradle.kts` (`include(..., "testgame")`), `core/.../MinigameCorePlugin.java` (register `GameRegistry` in `ServicesManager` at `ServicePriority.Normal` during `onEnable`)
- Test: `core/src/test/java/be/dbsdw/minigames/core/arena/ArenaStoreTest.java`, `testgame/src/test/java/be/dbsdw/minigames/testgame/PointsRaceTest.java`

**Interfaces:**
- Consumes: `ModeSpec`, `ArenaFeature`, `ArenaDefinition`, `Pos` (Task 1).
- Produces (all in `be.dbsdw.minigames.core.api`):
  - `interface GameType { String id(); Component displayName(); Material icon(); List<ModeSpec> modes(); MatchHandler newMatch(MatchContext context); }`
  - `interface MatchHandler { void start(); void tick(); void playerLeft(Player player, int team); void end(); }` — `start` runs after players are split, teleported to their team spawns and reset; `tick` every server tick while RUNNING; `playerLeft` after the player is removed from the match; `end` exactly once when the match stops for any reason (win, draw, last team standing, abort, shutdown) and must remove everything the game spawned.
  - `interface MatchContext { ModeSpec mode(); ArenaView arena(); int teamCount(); List<Player> team(int team); OptionalInt teamOf(Player player); Component teamName(int team); long ticks(); void finish(MatchOutcome outcome); }` — `finish` is ignored after the first call.
  - `interface ArenaView { String name(); World world(); List<Location> spawns(int team); Optional<Location> flag(int team); List<Location> powerups(); BoundingBox bounds(); }`
  - `record MatchOutcome(OptionalInt winner, List<Component> lines)` with `static MatchOutcome win(int team, List<Component> lines)`, `static MatchOutcome draw(List<Component> lines)`.
  - `interface GameRegistry { void register(Plugin owner, GameType game); Collection<GameType> games(); Optional<GameType> game(String id); Optional<ModeSpec> mode(String key); }` — `register` throws `IllegalArgumentException` on a duplicate game id or a mode whose `gameId` differs from the game's id. Lookup from a game plugin: `getServer().getServicesManager().load(GameRegistry.class)`.
  - `ArenaStore(Path arenasDir, Logger log)`: `List<ArenaDefinition> loadAll(Predicate<String> worldLoaded)` (one `arenas/<name>.yml` per arena; an unreadable file or one whose world is not loaded is skipped with one `[arena]` warning naming the file and the reason), `void save(ArenaDefinition)` (writes `<name>.yml` atomically: temp file + move), `boolean delete(String name)`. YAML layout: `world`, `modes` (list of keys), `corner1`/`corner2` (`x y z` maps), `spawns: {0: [pos…], 1: […]}`, `flags: {0: pos, 1: pos}`, `powerups: [pos…]`, pos = map `x y z yaw pitch`.
  - `BukkitArenaView(ArenaDefinition, World)` implements `ArenaView`.
  - Test game (plugin `MgTestGame`, `depend: [MinigameCore]`, jar `MgTestGame`): registers game `testgame` (icon `SLIME_BALL`) with one mode `ModeSpec("testgame","points","Punten",2,1,1,Set.of())`. `PointsRace(int teams, int target)`: `OptionalInt score(int team)` (adds a point, returns the winner when a team reaches `target`), `int points(int team)`, `OptionalInt leader()` (empty on a tie). `PointsMatch` implements `MatchHandler`: target 3, time limit 60 s (`ctx.ticks() >= 1200` → `leader()` win or draw), result line `"<Rood> 3 - 1 <Blauw>"` built from `teamName`. Command `/testgame score` (any player in a testgame match) adds one point for their team.

- [ ] **Step 1: Write the failing tests**

`ArenaStoreTest` (JUnit `@TempDir`; `YamlConfiguration` works without a server):
- `saveThenLoadRoundTrips`: full arena (2 modes, spawns for 2 teams with yaw/pitch, 2 flags, 3 power-ups, both corners) → `loadAll(w -> true)` returns an equal `ArenaDefinition`.
- `unknownWorldIsSkipped`: world predicate false → empty list (no exception).
- `brokenFileIsSkipped`: a file containing `"::: not yaml"` next to a valid one → only the valid one loads.

`PointsRaceTest`: `firstToTargetWins` (team 1 scores 3 → third call returns 1), `leaderEmptyOnTie`, `leaderIsHigherScore`.

- [ ] **Step 2: Run** `GRADLE :core:test :testgame:test` — Expected: compilation failure.
- [ ] **Step 3: Implement** the API, `GameRegistryImpl`, `ArenaStore`, `BukkitArenaView` and the test game. `testgame/build.gradle.kts` mirrors `paintball/build.gradle.kts` (`compileOnly(project(":core"))`, `testImplementation(project(":core"))`, jar name `MgTestGame`). The test game registers in `onEnable` and the command via `LifecycleEvents.COMMANDS` like `SurvivalCommand`.
- [ ] **Step 4: Run** `GRADLE build` — Expected: BUILD SUCCESSFUL, `testgame/build/libs/MgTestGame-*.jar` exists.
- [ ] **Step 5: Commit** — `"MinigameCore: game API, arena files; test-only MgTestGame"`

---

### Task 5: Lobby, lobby items, protections, `/leave`

**Files:**
- Create: `core/.../lobby/{LobbyService,LobbyListener,LeaveCommand}.java`
- Modify: `core/src/main/resources/{config.yml,messages.yml,plugin.yml}`, `MinigameCorePlugin.java`

**Interfaces:**
- Produces:
  - `LobbyService(Plugin, Messages)`: `Optional<Location> spawn()` (from `lobby.*` in config; empty if unset or its world is not loaded), `void setSpawn(Location)` (saves config), `void send(Player)` — teleport to spawn (if set), `GameMode.ADVENTURE`, clear inventory, full health and food, remove potion effects, give items; `boolean isLobbyItem(ItemStack)`; `Optional<LobbyItem> lobbyItem(ItemStack)`.
  - `enum LobbyItem { GAMES /*slot 0, COMPASS, "Spellen"*/, SURVIVAL /*slot 8, RED_BED, "Terug naar survival"*/ }` nested in `LobbyService`; items carry PDC key `dbsdw:lobby_item` = enum name; names/lore from `messages.yml` (`item.games`, `item.survival`).
  - `LobbyListener` needs callbacks it does not own yet; it takes `LobbyService`, `CoreTravel` (may be null when the secret is invalid), and two functional hooks wired in Task 7: `Consumer<Player> openGames`, `Consumer<Player> onQuit`.
  - `LeaveCommand(Consumer<Player> leave)` — `/leave`, description "Verlaat de wachtrij of het spel".

Behaviour: `PlayerJoinEvent` → `send`. Right-click with `GAMES` → `openGames`; with `SURVIVAL` → `travel.sendToSurvival` (or the `unavailable` message). Cancel for every player: all `EntityDamageEvent` on players, `FoodLevelChangeEvent`. Cancel for players holding lobby items: `PlayerDropItemEvent`, `PlayerSwapHandItemsEvent`, and `InventoryClickEvent`/`InventoryDragEvent` touching a lobby item. `PlayerQuitEvent` → `onQuit`. On enable, if `spawn()` is empty log once: `[lobby] lobby spawn not set — use /mg setlobby`.

- [ ] **Step 1: Implement** the three classes; register listener and `/leave` in `MinigameCorePlugin` (temporary `onQuit`/`openGames`/`leave` no-ops are replaced in Task 7 — mark them `// wired in MatchManager` so the reviewer sees they are intentional).
- [ ] **Step 2: Add messages** `item.games`, `item.survival`, `leave.nothing` ("Je staat niet in een wachtrij of spel.") and the `team.0..3` keys.
- [ ] **Step 3: Run** `GRADLE build` — Expected: BUILD SUCCESSFUL.
- [ ] **Step 4: Commit** — `"MinigameCore: lobby state, lobby items, /leave"`

(In-game behaviour is checked in Task 8; this task has no plain-Java logic to unit-test.)

---

### Task 6: Menus, game NPCs, admin commands

**Files:**
- Create: `core/.../menu/{GamesMenu,ModesMenu,MenuListener}.java`, `core/.../npc/GameNpc.java`, `core/.../admin/MgCommand.java`
- Modify: `core/src/main/resources/{messages.yml,plugin.yml}` (permission `minigames.admin`, default op), `MinigameCorePlugin.java`

**Interfaces:**
- Consumes: `GameRegistry`, `QueueBook` (via `MatchManager` in Task 7 — use the `MenuModel` interface below so this task compiles first), `ArenaPool`, `ArenaStore`, `ArenaValidator`, `LobbyService`.
- Produces:
  - `interface MenuModel { Collection<GameType> games(); int queued(String modeKey); OptionalInt secondsLeft(String modeKey); boolean anyArena(ModeSpec); boolean isQueued(UUID player, String modeKey); boolean inMatch(UUID player); void toggle(Player player, String modeKey); }` in `menu/`; implemented by `MatchManager` in Task 7.
  - `GamesMenu.open(Player, MenuModel, Messages)`: 27 slots, one icon per game from slot 10. `ModesMenu.open(Player, String gameId, MenuModel, Messages)`: 27 slots, mode icons from slot 11 step 2; lore lines: `<queued>/<startMinimum> spelers` (and `start over <s> s` while counting down), or `Geen arena beschikbaar` when `!anyArena`; glint when the player is queued for it. Both are `InventoryHolder`s; `MenuListener` cancels every click/drag in them, opens `ModesMenu` from `GamesMenu`, calls `toggle` from `ModesMenu`, and `static void refreshOpen(MenuModel, Messages)` re-renders every open `ModesMenu` (called once a second by Task 7).
  - `GameNpc(Function<String, Optional<GameType>> games, BiConsumer<Player, String> openModes)`: `void spawn(Player admin, String gameId)` (Mannequin, name = game display name, same settings as `bridge/.../MinigamesNpc.spawn`, PDC `dbsdw:game_npc` = game id), `int removeNear(Player admin)` (radius 5). Listens on `PlayerInteractEntityEvent` (Paper 26.3 fires the `…AtEntityEvent` subclass only; the parent listener receives it), main hand only, ignores players in a match; cancels damage to NPCs.
  - `MgCommand` (`/mg`, permission `minigames.admin`, tab completion for subcommands, game ids, mode keys and arena names):
    - `setlobby`
    - `npc set <game>` / `npc remove`
    - `arena create <name>` (starts an edit draft in the admin's world; loads the saved one if it exists), `arena modes <mode,mode…>`, `arena setspawn <team>` (adds), `arena setflag <team>`, `arena addpowerup`, `arena setbounds <1|2>`, `arena save` (runs `ArenaValidator`; errors → nothing saved, each error shown; warnings shown; ok → `ArenaStore.save` + `ArenaPool.put`), `arena list` (name, modes, busy/free), `arena delete <name>` (refused while busy).
    - Drafts live per admin UUID in memory; positions use the admin's location. Commands needing a position refuse the console with a clear message; `list`, `delete` work from the console.

- [ ] **Step 1: Implement** the classes; register `/mg` and listeners in `MinigameCorePlugin`.
- [ ] **Step 2: Messages** `menu.games.title`, `menu.modes.title`, `menu.mode.queued`, `menu.mode.countdown`, `menu.mode.no-arena`, `queue.joined`, `queue.left`, `queue.moved` (Dutch, MiniMessage; wording is the builder's choice per spec §15).
- [ ] **Step 3: Run** `GRADLE build` — Expected: BUILD SUCCESSFUL.
- [ ] **Step 4: Commit** — `"MinigameCore: queue menus, game NPCs, /mg admin commands"`

---

### Task 7: Match manager — queues to matches to lobby

**Files:**
- Create: `core/.../match/{MatchManager,RunningMatch}.java`
- Modify: `MinigameCorePlugin.java`, `core/src/main/resources/messages.yml`

**Interfaces:**
- Consumes: everything above.
- Produces:
  - `MatchManager(Plugin, GameRegistryImpl, ArenaPool, QueueBook, LobbyService, Messages, int resultsTicks)` implements `MenuModel`; `void tick()` (scheduled every tick with `runTaskTimer(plugin, manager::tick, 1, 1)`); `void leave(Player)` (queue or match, then lobby); `void quit(Player)`; `void shutdown()`; `void gameDisabled(Plugin owner)`.
  - `RunningMatch` implements `MatchContext`: holds `MatchState`, the `GameType`, the `MatchHandler`, the `ArenaView`.
  - `GameRegistryImpl.register` calls `QueueBook.addMode` for each mode (via a listener the manager installs).

Behaviour:
- `tick()`: handle each `QueueEvent` — `CountdownStarted`/`CountdownTick` → title + chat to the queued players and `[queue] <mode> countdown <s>s on arena <a>` (start only); `CountdownCancelled` → message `queue.cancelled`, log; `WaitingForArena` → `queue.waiting`, log `[queue] <mode> waiting for a free arena`; `Start` → drop offline players from the list (if that leaves fewer than `startMinimum`, put the online ones back at the front of the queue in order and release the arena), split with `TeamSplit`, create `RunningMatch`: for each player close inventory, teleport to `spawns(team)` (round-robin), adventure, clear inventory, full health/food, title `match.start`; then `handler = game.newMatch(ctx)`; `handler.start()`; log `[match] <mode> on <arena> started: <n> players`. Then tick every running match: RUNNING → `handler.tick()`; ENDING → `state.tickEnding()`, when done every remaining player → `lobby.send`, `pool.release`, log ended. Every 20 ticks call `MenuListener.refreshOpen`.
- `finish(outcome)` (from the handler or core): `state.finish()`; `handler.end()`; title `match.win` with the team name / `match.draw` to everyone in the match; `outcome.lines()` in chat; log `[match] <mode> on <arena> ended: winner <team>|draw`.
- Leave/quit in a match: `state.leave` → `handler.playerLeft` → `LastTeamStanding(t)` → `finish(MatchOutcome.win(t, List.of()))`. If everyone has gone during the results, `tickEnding()` ends it at once and the arena is released. The leaving player (if still online) → `lobby.send`.
- A `RuntimeException` from any handler call: log `[match] <mode> on <arena> aborted: game error` with the stack trace at SEVERE, abort the match as above, send its players to the lobby with `match.error`. (Contained so one game bug does not take the server down; it is loud in the log.)
- `shutdown()` (first thing in `onDisable`, before plan 1's `sendAllToSurvivalForShutdown`): abort every match (`handler.end()` called), release arenas.
- `gameDisabled(owner)` (on `PluginDisableEvent`): abort that game's matches (players to the lobby with `match.error`), `removeMode` its modes and tell the queued players `queue.closed`, unregister the game.
- Players in a match cannot open menus or toggle queues (`inMatch`); `/leave` with nothing to leave → `leave.nothing`.
- On server start (`ServerLoadEvent`), log one `[arena]` warning for each arena mode key no registered game provides.

- [ ] **Step 1: Implement** `MatchManager` and `RunningMatch`; replace the Task 5 placeholder hooks; wire the tick task, `PluginDisableEvent`, `ServerLoadEvent` and the shutdown order in `MinigameCorePlugin`.
- [ ] **Step 2: Messages** `queue.cancelled`, `queue.waiting`, `queue.closed`, `queue.countdown` (title + subtitle), `match.start`, `match.win`, `match.draw`, `match.error`.
- [ ] **Step 3: Run** `GRADLE build` — Expected: BUILD SUCCESSFUL, all unit tests pass.
- [ ] **Step 4: Commit** — `"MinigameCore: match lifecycle from queue to results to lobby"`

---

### Task 8: End-to-end checks on node1, docs, push

**Files:**
- Modify: `tools/e2e/bot/hop.js`, `tools/e2e/README.md`, `README.md`
- Create: `docs/superpowers/notes/2026-11-18-plan-2-e2e-results.md`

**Interfaces:**
- Produces: `hop.js` gains repeatable `--do <step>` run in order after the final join: `use:<hotbarSlot>` (select and right-click the held item), `click:<slot>` (left-click a slot in the open window), `cmd:<text>`, `wait:<seconds>`, `interact:<entityType>` (nearest entity), `drop:<hotbarSlot>` (select the slot and press the drop key). New events: `window_open {title}`, `window_items {items:[{slot,name}]}`, `title {text}`, `position {x,y,z}`, `game_mode {mode}`, `held_items {items:[{slot,name}]}` (player inventory slots with a custom name).

- [ ] **Step 1: Extend `hop.js`** as above; keep every existing flag working.
- [ ] **Step 2: Set up the harness on node1** per `tools/e2e/README.md` with `MinigameCore`, `MgTestGame` on `minigames` and `DbsdwBridge` on `survival` (same `ticket-secret`, copied file to file, never printed). On `minigames`: write `plugins/MinigameCore/arenas/test.yml` (world `world`, mode `testgame:points`, bounds around spawn, one spawn per team) and set the lobby with `lobby:` in config before start.
- [ ] **Step 3: Run the checks** (bots via `viaproxy`, `/minigames` to hop). Each check names the bot output event or minigames log line that proves it:
  1. Arrival → `game_mode adventure`, `held_items` has the games item in slot 0 and the survival item in slot 8; log `[lobby]`.
  2. `--do use:0 --do click:10` → `window_open` for the games menu then the modes menu; `--do click:11` → chat `queue.joined`; clicking again → `queue.left`.
  3. Two bots queue → `[queue] testgame:points countdown 10s on arena test` (full at 2), titles counting down, then `[match] testgame:points on test started: 2 players`, both get `position` inside the arena. A third bot queued meanwhile gets `queue.waiting` and stays queued.
  4. In a new match, one bot disconnects → log `ended: winner <other team>`; after 10 s the other bot's `position` is the lobby; `arena list` (console) shows `test` free.
  5. Two bots queued, countdown running, one disconnects → `CountdownCancelled` message to the other, log cancelled, arena free.
  6. `cmd:/testgame score` ×3 by one bot → `title` win text for both, result line in chat, both back in the lobby after 10 s.
  7. `stop` on minigames during a running match → log shows `ended: aborted` before the shutdown transfer lines, no stack traces; bots `transferred` to survival.
  8. Opped bot: `cmd:/mg npc set testgame`; a second bot `interact:mannequin` → `window_open` modes menu. `/mg arena create x` + `setspawn 0` + `save` from the bot → error lines naming the missing bounds/team 1 spawn; nothing written.
  9. Lobby protections: `--do drop:0 --do wait:2` → the next `held_items` still has the games item in slot 0.
- [ ] **Step 4: Write `docs/superpowers/notes/2026-11-18-plan-2-e2e-results.md`** (each check: pass/fail + the evidence line) and update `README.md` (lobby setup, `/mg` commands, arena file format, MgTestGame is test-only) and `tools/e2e/README.md` (new `--do` steps).
- [ ] **Step 5: Tear down on node1:** `docker compose down`, remove `/opt/dbsdw-e2e` and pulled images.
- [ ] **Step 6: Commit and push the branch; confirm CI green** — `"Record plan 2 end-to-end results; document lobby and arenas"`.

---

## Out of scope (later plans)

Paintball itself (plan 3–4), stats/leaderboards and the stats lobby item (plan 5), resource pack items/models and pack hosting (plan 6), the built lobby and first arena (plan 7), deployment and live changes (plan 8). Deferred minors from plan 1 (duplicated `Messages` loader, NPE when the bundled `messages.yml` is missing) stay deferred unless a task here touches them.
