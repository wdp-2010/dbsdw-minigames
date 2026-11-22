# Private rooms with host start — design

Date: 2026-11-22. Status: approved 2026-11-22.

## Why

Friends can't play together today. Each player queues on their own, has to pick the same mode as
the others, and nothing starts below 4 players (2 per team). A group has to arrange everything in
chat and then still wait for strangers. The server is small (two arenas, often only PaintBot
online), so "wait for enough players" usually means "never".

A private room lets one player gather their friends, pick the game, and press start when everyone
is there, even with only two of them.

## Goals

- One player (the host) makes a room and invites friends; friends join with one click.
- The host picks mode and map, and starts the match whenever they want, from 2 players up.
- The host decides how teams are made: players pick their own team, the host assigns everyone, or
  random. Anyone left without a team is balanced in automatically.
- After the match the group lands back in the lobby **still in the room**, so the host can start the
  next round straight away. No re-inviting.
- The public queue keeps working exactly as now for people who aren't in a room.

## Non-goals (v1)

- Open rooms that strangers can browse and join. Invite-only for now.
- Parties that queue together in the public queue.
- Rooms that survive a server restart or a player logging out.
- Bots (PaintBot) as fill-in players.
- Custom match rules (score limit, time) per room.

## Anti-goals

- A room that gets stuck: host gone, arena held, members unable to leave or queue. Every way out
  (leave, quit, kick, host leaving) must leave a clean state.
- Room players blocking the public queue's arenas while they are only waiting in the lobby. A room
  holds an arena only from the moment the host presses start.

## Constraints from the current code

- Match start lives in `MatchManager.start(mode, arena, players)`; teams come from
  `TeamSplit.split`. Room starts need the same path but with teams given by the room.
- `ModeSpec.startMinimum()` is `teams × minPerTeam` (4 for paintball). Room matches use their own
  minimum: one player per team (2 for paintball). Paintball's match logic has no per-team minimum,
  and `MatchState` already ends a match when only one team has players left.
- `ArenaPool` has two paintball arenas (`duinbasis`, `woudfront`). Rooms and the public queue share
  them through `reserve`/`release`.
- Lobby hotbar: 0 games, 2 practice gun, 4 stats, 8 survival. The room item goes in slot 6.
- Menus are chest inventories with an `InventoryHolder` (`GamesMenu`, `ModesMenu`) handled in
  `MenuListener`. The room menu follows the same pattern.
- Commands register through Paper's lifecycle registrar in `MinigameCorePlugin` (as `/stats`).
- All player text is Dutch MiniMessage in `core/src/main/resources/messages.yml`.

## Approaches considered

1. **Private room with host start (chosen).** Group forms in the lobby, host controls mode, map and
   start, 2+ players. Fixes "we're too few" and "we have to wait for each other" in one go.
2. **Party in the public queue.** Group queues as one and always lands on the same team. Keeps the
   automatic countdown, but still needs 4 players, so a group of 2–3 still waits forever. Rejected
   for now; it can reuse the room's group logic later.
3. **Lower the public minimum and add a ready-vote.** Smallest change, but strangers can still drop
   into the match and split the group across teams, and it doesn't keep the group together between
   rounds. Rejected.

The user picked approach 1.

## How it works for players

### Making a room and inviting

- Lobby item in slot 6, "Room" (pack model like the other lobby items, fallback `Material.CAKE`).
  Right-click: no room yet → makes one with you as host and opens the room menu; in a room → opens
  the room menu.
- `/room` does the same as the item. Subcommands:
  - `/room invite <speler>`: host only. The invited player gets a chat message with a clickable
    **[Join]** (runs `/room join <host>`). An invite is valid for 60 seconds.
  - `/room join <host>`: only with a valid invite.
  - `/room leave`: leave the room.
  - `/room kick <speler>`: host only.
  - `/room start`: host only, same as the start button.
- Tab completion offers online lobby players for `invite`, members for `kick`, hosts who invited you
  for `join`.
- The room menu also has an **Invite** button that lists online lobby players (heads); clicking one
  sends the invite. This is the main way; the command is for chat-savvy players.

### The room menu

```
┌──────────────── Room van Wannes ────────────────┐
│ [TDM ✔] [CTF]         [Map: willekeurig ▸]      │
│                                                  │
│  ROOD                     BLAUW                  │
│  [head Wannes]            [head Jonas]           │
│  [head Lotte]             [ + kies blauw ]       │
│  [ + kies rood ]                                 │
│                                                  │
│  Geen team: [head Pieter]                        │
│                                                  │
│ [Uitnodigen]   [Verlaten]          [▶ START]     │
└──────────────────────────────────────────────────┘
```

- **Mode** (host only): one button per mode of the game. Paintball: TDM and CTF.
- **Map** (host only): cycles "willekeurig" (random free arena) → each arena that fits the mode.
- **Teamregel** (host only): cycles between three rules, shown to everyone:
  - **Spelers kiezen** (default): each member clicks a team's "kies" slot to join that team; click
    again to go back to "geen team".
  - **Host verdeelt**: only the host can move players; clicking a member's head moves them to the
    next team (rood → blauw → geen team → rood). Members' "kies" slots are hidden.
  - **Willekeurig**: no picking; at start everyone is shuffled into balanced teams. The team columns
    show everyone under "geen team".
  Switching the rule keeps current picks (except Willekeurig, which ignores them while selected).
- A team can't get more than `maxPerTeam` players.
- **Start** (host only, greyed for others): see below.
- Members see heads with names; the host's head is marked "host".
- The menu redraws for every member who has it open when anything changes, once per second like the
  queue menus.

### Starting

- Start needs at least one player per team after balancing, i.e. at least 2 members. Below that the
  button says "minstens 2 spelers".
- Players without a team are added to the smallest team (ties random), so a 3-player room with
  nobody picking becomes 2v1. Under Willekeurig all members are treated as without a team.
- If the chosen map is busy, or with "willekeurig" no fitting arena is free, the host gets "alle
  arena's zijn bezet, probeer zo opnieuw" and nothing else happens. No waiting list in v1.
- Otherwise the arena is reserved and every member sees a 5-second countdown title (same look and
  sound as the queue). During it the menu shows "start over Xs" and Start becomes **Annuleren** for
  the host. If the room drops below 2 members, the countdown stops and the arena is released.
- At zero the match starts through the normal match path with the room's teams.

### During and after the match

- Room members in a running match are "in match" like now. The room remembers it is playing.
- Players invited while the room is playing can join the room; they wait in the lobby until the
  match ends and get a message saying so.
- `/leave` during a room match takes you out of the match but keeps you in the room.
- When the match ends, everyone goes back to the lobby as now and gets "Terug in de room van <host>".
  Teams stay as they were picked (balanced players go back to "geen team"). The host can start again.

### Leaving, host changes, clean-up

- `/room leave`, the Verlaten button, being kicked, or logging out takes you out of the room.
- If the host leaves, the member who has been in the room longest becomes host; everyone is told.
- An empty room is removed.
- Being in a room and in a public queue are exclusive: making or joining a room takes you out of
  your queue, and the modes menu shows "Je zit in een room" instead of joining while you're in one.
- A room has at most `teams × maxPerTeam` members for its current mode (16 for paintball).

## Parts of the design

- `core/.../room/RoomBook` — pure state, no Bukkit, unit tested like `QueueBook`: rooms by id,
  member → room, host, join order, chosen mode/map, team picks, invites with expiry, countdown, and
  "playing" flag. Methods return results/events (`Joined`, `HostChanged`, `CountdownTick`,
  `Start(mode, arena, teams)`, …) for `MatchManager` to act on. `tick()` runs countdowns and expires
  invites.
- `core/.../room/RoomTeams` — pure: turns picks + unpicked members into final teams under the team
  rule (fill smallest team, ties random). Unit tested.
- `core/.../room/RoomMenu` — the chest menu (`InventoryHolder`), rendered from a read-only view of
  the room; clicks handled in `MenuListener`.
- `core/.../room/RoomCommand` — `/room` with Brigadier subcommands, registered in
  `MinigameCorePlugin`.
- `MatchManager` — owns the `RoomBook`, ticks it, turns `Start` into a match by sharing the start
  path with the queue (a start method that takes ready-made teams), marks the room playing, and on
  match close sends members back to the room. `leave`/`quit` also update the room.
- `LobbyService.LobbyItem.ROOM` in slot 6, handled in `LobbyListener`.
- New Dutch messages under `room.*` and `menu.room.*` in `messages.yml`.
- `ModesMenu` / `toggle` refuse queueing while in a room.

## Testing

- Unit: `RoomBook` (create, invite expiry, join without invite refused, host hand-over, empty room
  removed, capacity, countdown cancel below 2, start event carries teams, queue/room exclusivity via
  `MatchManager` rules where they are pure), `RoomTeams` (2 → 1v1, 3 unpicked → 2v1, picks kept,
  full team refused).
- Live on server 5 with two clients: make room, invite, 1v1 TDM and CTF, return to room, start
  again, host leaves mid-room, kick, both arenas busy message.

## Decided by the user

- Room matches count for stats and leaderboards like normal matches.
- The host chooses the team rule (players pick / host assigns / random).

## Calls made by the designer (correct if wrong)

- Invite-only rooms, no browsing.
- Default team rule is "Spelers kiezen"; leftovers auto-balanced.
- 5-second start countdown, host can cancel.
- Busy arenas: refuse with a message instead of a waiting list.
- Room item slot 6; invite via menu head list and `/room invite`.
- Invites expire after 60 s.
- Leaving a match with `/leave` keeps you in the room.
