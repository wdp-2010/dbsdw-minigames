# Plan 3 bot smoke test (2026-11-18)

Shallow check by agreement with the owner (feel is tested with a real client). Throwaway servers
on node1 (`/opt/dbsdw-e2e`, torn down afterwards): `survival` (AuthMe, DbsdwBridge) and
`minigames` (MinigameCore, Paintball). Arena `test`: mode `paintball:tdm`, spawns 10 blocks apart.
Test Paintball config: 1 per team minimum, first to 2, 2 minutes. Two bots travel from survival
with `/minigames`, queue through the menu; BotA aims at BotB and fires twice 8 shots.

| Check | Result | Evidence |
|-------|--------|----------|
| Match start, gun, HUD | PASS | `Start!`, `held_items` slot 0 `Paintgeweer`, action bar `●●● Beschermd` for 2 s then `●●●` |
| Fire rate | PASS | BotB sees snowballs 250 ms apart (5 ticks) while BotA clicks every 200 ms |
| Hits and hit marker | PASS | BotA action bar `✖ Raak!` on each hit |
| Out, feed, respawn | PASS | third hit: `✖ Uitgeschakeld: BotB`, BotB hidden (`visible false`), chat `BotB is uit (door BotA)`, BotB titles `Uit!` each second for 3 s, then `●●● Beschermd` |
| Win and results | PASS | second elimination: `Rood wint!`, chat `Score: Rood 2 - Blauw 0`, top list; log `ended: winner team 0` |
| Cleanup | PASS | gun removed at the end; 10 s later both bots hold the lobby items |

No exceptions in the server log. Not checked by bots: out of bounds, leaving mid-match while
out, time-limit draw — left for the owner's client test.
