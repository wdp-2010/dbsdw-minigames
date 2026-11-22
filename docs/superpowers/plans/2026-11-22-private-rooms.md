# Private Rooms Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Friends gather in a private room in the lobby; the host picks mode, map and team rule and starts a match from 2 players; after the match the group is back in the room.

**Architecture:** A pure, unit-tested `RoomBook` (state, rules, countdown) and `RoomTeams` (final teams) in a new `core/.../room` package. A Bukkit-side `RoomService` owns the book, the chest menus and the messages, and asks `MatchManager` (through `RoomHooks`) to launch matches. `MatchManager`'s start path is split so the queue and rooms share one launch with ready-made teams.

**Tech Stack:** Java 25, Paper 26.3 API, Adventure/MiniMessage, JUnit 5, Gradle (built in `eclipse-temurin:25-jdk` on node1; no local JDK).

**Spec:** `docs/superpowers/specs/2026-11-22-private-rooms-design.md`

## Global Constraints

- All player text Dutch MiniMessage in `core/src/main/resources/messages.yml`; raise its `version` (3 → 4).
- Room start minimum: 2 members and at least 1 player per team after balancing; capacity `mode.maximum()`; team cap `mode.maxPerTeam()`.
- Start countdown 5 s (same title/sound as the queue countdown). Invites expire after 60 s (1200 ticks).
- Team rules: `PICK` "Spelers kiezen" (default), `HOST` "Host verdeelt", `RANDOM` "Willekeurig".
- Room matches count for stats like normal matches (no stats change needed).
- A room holds an arena only from start-press to match end; busy → message, no waiting list.
- Room and public queue are exclusive per player.
- Room lobby item in hotbar slot 6, `Material.CAKE`, no pack model.
- Public queue behaviour unchanged.

## Review Focus

- Host quits during the 5 s countdown → countdown keeps going only if ≥2 members remain, new host told; arena released otherwise. (Task 2 test `hostLeavingDuringCountdownHandsOver`.)
- Both members pick the same team, host presses Start → refused with "elk team heeft minstens 1 speler", not a 2v0 match. (Task 2 test `startRefusedWhenATeamWouldBeEmpty`.)
- Member quits during the room's match → match handles leave as now; after the match the room no longer lists them. (Task 2 test `leaveWhilePlayingRemovesMember`.)
- Invited player is already in another room → join refused, not silently moved. (Task 2 test `joinRefusedWhenInAnotherRoom`.)
- Host switches mode while countdown runs → refused. (Task 2 test `settingsLockedDuringCountdown`.)

---

### Task 1: `ArenaPool.reserve(name, mode)` and `RoomTeams`

**Files:**
- Modify: `core/src/main/java/be/dbsdw/minigames/core/arena/ArenaPool.java`
- Create: `core/src/main/java/be/dbsdw/minigames/core/room/RoomTeams.java`
- Test: `core/src/test/java/be/dbsdw/minigames/core/arena/ArenaPoolTest.java`, `core/src/test/java/be/dbsdw/minigames/core/room/RoomTeamsTest.java`

**Interfaces:**
- Produces: `Optional<ArenaDefinition> ArenaPool.reserve(String name, ModeSpec mode)` — empty when unknown, busy or not fitting.
- Produces: `enum RoomTeams.Rule { PICK, HOST, RANDOM; Rule next() }`
- Produces: `static List<List<UUID>> RoomTeams.build(List<UUID> members, Map<UUID,Integer> picks, Rule rule, int teams, RandomGenerator rnd)` — RANDOM delegates to `TeamSplit.split`; otherwise picks kept, unpicked (in member order, shuffled) each go to the currently smallest team, ties broken by `rnd`.

- [ ] Tests: `reserveByNameRefusesBusyAndUnfitting`; `twoUnpickedBecomeOneVsOne`, `threeUnpickedBecomeTwoVsOne`, `picksAreKept` (A,B picked 0, C unpicked → C in team 1), `randomIgnoresPicks` (sizes 1/1 for 2 members both picked 0).
- [ ] Implement, run `:core:test`, commit.

### Task 2: `RoomBook`

**Files:**
- Create: `core/src/main/java/be/dbsdw/minigames/core/room/RoomBook.java`, `RoomEvent.java`, `RoomView.java`
- Test: `core/src/test/java/be/dbsdw/minigames/core/room/RoomBookTest.java`

**Interfaces:**
- Consumes: Task 1.
- Produces:
  - `RoomBook(ArenaPool pool, RandomGenerator rnd)`
  - `enum RoomBook.Outcome { OK, NO_ROOM, NOT_HOST, IN_ROOM, NO_INVITE, NOT_MEMBER, FULL, TEAM_FULL, TOO_FEW, EMPTY_TEAM, BUSY, NO_ARENA, PLAYING, COUNTING, NOT_ALLOWED }`
  - `record RoomBook.Left(int roomId, boolean removed, Optional<UUID> newHost)`
  - `Outcome create(UUID host, ModeSpec mode)`; `Outcome invite(UUID host, UUID target)`; `Outcome join(UUID player, UUID host)` (consumes invite); `Optional<Left> leave(UUID player)`; `Outcome kick(UUID host, UUID target)`;
    `Outcome setMode(UUID host, ModeSpec mode)` (FULL if members > maximum; drops picks ≥ teams); `Outcome setArena(UUID host, Optional<String> arena)`; `Outcome cycleRule(UUID host)`;
    `Outcome pick(UUID player, OptionalInt team)` (PICK rule only, else NOT_ALLOWED); `Outcome move(UUID host, UUID target)` (HOST rule only; cycles 0 → 1 → … → none → 0);
    `Outcome start(UUID host)` (checks, reserves arena, starts 5 s countdown); `Outcome cancel(UUID host)`;
    `List<RoomEvent> tick()`; `void matchEnded(int roomId)`; `Optional<RoomView> roomOf(UUID player)`; `Optional<RoomView> room(int id)`; `boolean invited(UUID player, UUID host)`.
  - Settings, kick and start return COUNTING during a countdown and PLAYING while playing (join, invite, leave stay allowed). `create`/`join` return IN_ROOM when the player is in any room.
  - `RoomEvent`: `CountdownTick(int roomId, List<UUID> members, int seconds)` (at 5,4,3,2,1), `CountdownCancelled(int roomId, List<UUID> members)`, `Start(int roomId, ModeSpec mode, ArenaDefinition arena, List<List<UUID>> teams)`; on Start the room is marked playing and the arena stays reserved (MatchManager releases it on close, as for the queue).
  - `RoomView` record: `int id, UUID host, List<UUID> members, ModeSpec mode, Optional<String> arena, RoomTeams.Rule rule, Map<UUID,Integer> picks, OptionalInt secondsLeft, boolean playing`.
  - Countdown is cancelled (arena released, `CountdownCancelled`) when at tick time `RoomTeams.build` would give an empty team or members < 2.
- [ ] Tests (plus the Review Focus ones): `createMakesHost`, `joinNeedsInvite`, `inviteExpiresAfter60s`, `hostLeavingHandsToOldestMember`, `lastLeaveRemovesRoom`, `kickOnlyByHost`, `fullRoomRefusesJoin`, `pickOnlyUnderPickRule`, `moveCyclesUnderHostRule`, `startNeedsTwo`, `startBusyArena` (BUSY for named busy arena, NO_ARENA when none free), `countdownEmitsStartWithTeamsAfter100Ticks`, `cancelReleasesArena`, `matchEndedAllowsNewStart`.
- [ ] Implement, run `:core:test`, commit.

### Task 3: Room menus, `RoomService`, messages

**Files:**
- Create: `core/src/main/java/be/dbsdw/minigames/core/room/RoomService.java`, `RoomHooks.java`, `RoomMenu.java`, `InviteMenu.java`, `RoomMenuListener.java`
- Modify: `core/src/main/resources/messages.yml`

**Interfaces:**
- Consumes: Task 2.
- Produces:
  - `interface RoomHooks { boolean launch(int roomId, ModeSpec mode, ArenaDefinition arena, List<List<UUID>> teams); boolean inMatch(UUID player); void leaveQueue(UUID player); }`
  - `RoomService(Plugin plugin, RoomBook book, Supplier<List<ModeSpec>> modes, ArenaPool pool, Messages messages, RoomHooks hooks)`
  - `void openOrCreate(Player)`, `void invite(Player host, Player target)`, `void join(Player, Player host)`, `void leave(Player)` (also used on quit), `void kick(Player host, Player target)`, `void start(Player host)` (start or, while counting, cancel), `void tick()` (book events → titles/sound/launch; menus redrawn once a second), `void matchEnded(int roomId)` (tells members "Terug in de room van <host>"), `boolean inRoom(UUID)`, `RoomMenuListener listener()`, `List<String> invitableNames(Player)`, `List<String> memberNames(Player)`, `List<String> inviterNames(Player)`.
  - Launch failure (hook returns false) → book `matchEnded`, members told `room.start-failed`.
  - Every `Outcome` other than OK maps to message `room.error.<outcome lowercase with - for _>`.
- Menu layout (54 slots): row 0 mode buttons from slot 0 (glint = selected), map button slot 6 (cycles random → each fitting arena), rule button slot 8; row 1 slot 9 team 0 header (red wool; click = pick team 0 under PICK), heads 10–17; row 2 slot 18 team 1 header (blue wool), heads 19–26; row 3 slot 27 "Geen team" header (light gray wool; click = pick none), heads 28–44; row 5 invite 45 (opens `InviteMenu`), leave 49, start/cancel 53 (lore shows why it can't start). Under HOST, host clicking a head calls `move`. Title `room.menu.title` with `<host>`.
- `InviteMenu`: heads of online players not in a match and not in this room, up to 45; click = invite, then back to room menu.
- [ ] Add messages (version 4): `item.room`, `room.menu.*`, `room.created`, `room.invited` (with clickable `<click:run_command:'/room join <host>'>[Join]</click>`), `room.invite-sent`, `room.joined`, `room.member-joined`, `room.left`, `room.member-left`, `room.kicked`, `room.new-host`, `room.back`, `room.start-failed`, `room.wait-match`, `room.in-room`, `room.error.*`.
- [ ] Compile `:core:build`, commit.

### Task 4: Wire into `MatchManager`, lobby, `/room`

**Files:**
- Modify: `core/.../match/MatchManager.java`, `core/.../lobby/LobbyService.java`, `core/.../lobby/LobbyListener.java`, `core/.../MinigameCorePlugin.java`
- Create: `core/.../room/RoomCommand.java`

**Interfaces:**
- Consumes: Task 3.
- `MatchManager implements RoomHooks`; owns `RoomService rooms()`; `start(mode, arena, players)` → `TeamSplit` then shared `launch(mode, arena, teams, OptionalInt roomId)`; `close` calls `rooms.matchEnded(id)` for room matches after sending players to the lobby; `quit` → `rooms.leave`; `leave` (lobby, not in match/queue) → leaves the room; `toggle` refuses with `room.in-room` while in a room; joining/creating a room removes the queue entry (`leaveQueue`).
- `LobbyItem.ROOM(6, Material.CAKE, "item.room", null)`; `create` skips the item model when null; `LobbyListener` gets a `Consumer<Player> openRoom`.
- `/room [invite|join|leave|kick|start] [speler]` as `BasicCommand` with `suggest`, registered as `room` ("Speel met je vrienden").
- [ ] Build `:core:build` + `:paintball:build`, commit.

### Task 5: Deploy and live check

- [ ] Deploy `MinigameCore-0.1.0-SNAPSHOT.jar` to server 5 when no match runs; restart; check log has no errors; bring PaintBot back.
- [ ] Live: `mg arena list` free; with console/bot where possible verify `/room` registered. Ask the user for the two-client test (make room, invite, 1v1 TDM and CTF, back in room, restart, host leaves, kick, busy arena).
- [ ] Note results in `docs/superpowers/notes/2026-11-22-private-rooms-results.md`; README lobby section mentions rooms; commit.
