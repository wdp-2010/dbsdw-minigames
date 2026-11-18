# Plan 2 end-to-end results (2026-11-18)

Throwaway servers on node1 (`/opt/dbsdw-e2e`, torn down afterwards): Paper 26.3-255 `survival`
(AuthMe 6.0.1 with the pre-join dialog, DbsdwBridge) and `minigames` (MinigameCore + the test-only
MgTestGame), one arena `test` (mode `testgame:points`, 1 v 1, so a full queue is 2 players) and
the lobby at the world spawn. Bots (`tools/e2e/bot/hop.js`, protocol 26.1) go through ViaProxy
3.4.14 and travel survival → minigames with `/minigames`, like a real player.

| # | Check | Result | Evidence |
|---|-------|--------|----------|
| 1 | Arrival in the lobby | PASS | bot: `game_mode adventure`, `held_items` slot 0 `Spellen (rechtsklik)`, slot 8 `Terug naar survival (rechtsklik)`, `position 0.5,-62,0.5`; log `[lobby] BotB in lobby` |
| 2 | Menus and queue toggle | PASS | `use:0` → `window_open "Kies een spel"` (slot 10 `Testspel`); `click:10` → `window_open "Testspel"` (slot 11 `Punten`); `click:11` → `Je staat in de wachtrij voor Punten.`; again → `Je hebt de wachtrij verlaten.`; log `[queue] BotA joined/left testgame:points` |
| 3 | Countdown and start | PASS | two bots queued → `[queue] testgame:points countdown 10s on arena test` (full at 2), titles `10`, `5`…`1`, `Start!`; `[match] testgame:points on test started: 2 players`; bots at `15.5,-62,0.5` and `35.5,-62,0.5` (team spawns). A third bot queued meanwhile stayed queued. It got no "waiting for a free arena" message: alone it is below the start minimum, so that message correctly never applies. |
| 4 | A player quits mid-match | PASS | `BotF` quits → `[match] … ended: winner team 0`, the other bot gets `Rood wint!` and is back at the lobby (`position 0.5,-62,0.5`) 10 s later |
| 5 | A player quits during the countdown | PASS | `[queue] testgame:points countdown cancelled`, the other bot gets `Te weinig spelers: het aftellen is gestopt.`; `/mg arena list` → `test (world) testgame:points — free` |
| 6 | Win by score | PASS | three `/testgame score` → both bots `Blauw wint!` + chat `Rood 0 - Blauw 3`; lobby 10 s later |
| 7 | Server stops mid-match | PASS | `[match] … ended: aborted (server stopping)` before `[travel] … -> survival` and `shutdown: sent 2 player(s) to survival`; bots `transferred`; no stack traces |
| 8 | Game NPC and arena save errors | PASS | opped bot: `/mg npc set testgame` → `NPC for testgame placed.`; second bot `interact:mannequin` → `window_open "Testspel"`. `/mg arena create x` + `setspawn 0` + `save` → `arena x: no bounds — …`, `arena x: no modes — …`, `Not saved.`; no `x.yml` written |
| 9 | Lobby item cannot be dropped | PASS | `drop:0` → next `held_items` still has `Spellen` in slot 0 |

## Found and fixed along the way

- Players queued beyond a mode's maximum got the countdown titles for a match they would not be
  in. The countdown now goes only to the players who will start (`QueueBook.starting`,
  `QueueBookTest.countdownTickListsOnlyPlayersWhoWillStart`).
- On server stop the game plugin disables before MinigameCore, so players briefly saw "Er ging
  iets mis met dit spel". Matches of a disabled game now end quietly while the server is stopping.
- Arrivals were not logged; MinigameCore now logs `[lobby] <name> in lobby`.
- Harness: Paper's `connection-throttle` kicks bots that join within 4 s of each other (set it to
  0 on test servers), and `--stay` also ends earlier legs of a trip (new `--do quit` step for
  disconnect checks). Both are in `tools/e2e/README.md`.
- `gamerule` typed into the console of a Paper 26.3 test server throws a NullPointerException in
  Paper itself (no level context); not needed for these checks.
