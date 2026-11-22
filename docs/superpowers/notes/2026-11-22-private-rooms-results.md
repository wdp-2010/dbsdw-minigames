# Private rooms — results (2026-11-22)

Plan: `docs/superpowers/plans/2026-11-22-private-rooms.md`.

## Built

- `core/.../room/`: `RoomBook` (state and rules), `RoomTeams` (final teams), `RoomService`
  (menus, messages, countdown), `RoomMenu` + `RoomMenuListener`, `RoomCommand`, `RoomHooks`.
- `MatchManager` shares one match start between queue and rooms, ticks the rooms, sends room
  members back to their room after a match, keeps room and queue exclusive.
- `ArenaPool.reserve(name, mode)` for a chosen map.
- Lobby item ROOM (slot 6, cake, no pack model yet). messages.yml v4.

## Tests

- `RoomBookTest` 23/23, `RoomTeamsTest` 6/6, `ArenaPoolTest` 4/4; full Gradle build green;
  `tools` Python tests 27 OK.

## Live (server 5)

- Deployed MinigameCore 16:41; old jar in `/root/rooms-backup-2211/` on node1. Note: this core
  build also carries the earlier uncommitted ArenaChunks change (chunks held loaded during a match).
- Enabled without errors; messages.yml v3 replaced by v4 (old file kept as `.v3.bak`).
- `/room` is registered (console gets "Only players can use /room.").
- Not yet checked with two real clients: make room, invite, 1v1 TDM and CTF, back in room,
  start again, host leaving, kick, both maps busy.
