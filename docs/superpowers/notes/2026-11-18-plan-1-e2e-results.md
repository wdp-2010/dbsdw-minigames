# Plan 1 end-to-end results (2026-11-18)

Throwaway servers on node1 (`/opt/dbsdw-e2e`): Paper 26.3-255 `survival` (AuthMe 6.0.1 with the
live pre-join dialog settings, sessions **off** so only the ticket can skip the login, DbsdwBridge)
and `minigames` (MinigameCore), both `online-mode=false`, `accepts-transfers=true`. Bots
(`tools/e2e/bot/hop.js`, protocol 26.1) connect through ViaProxy 3.4.14, so the servers see a
real 26.3 client and the plugins run with live settings.

| # | Check | Result | Evidence |
|---|-------|--------|----------|
| 1 | `E2eBot` logs in on survival, `/minigames` | PASS | `Je gaat naar de minigames...` → `transferred` → `joined`; survival `[travel] E2eBot -> minigames`; minigames `[travel] E2eBot arrived` |
| 2 | On minigames, `/survival` | PASS | `transferred` → `joined` survival with **no** `dialog_shown`; `[travel] E2eBot back from minigames, logged in by ticket`; AuthMe `Successful login!` |
| 3 | Direct join to minigames | PASS | kicked: `Je kunt hier alleen binnenkomen via de survivalserver. Ga naar survival en typ /minigames.`; log `refused: not transferred` |
| 4 | Not logged in, `/minigames` | N/A (unit-tested) | With AuthMe's pre-join dialog nobody reaches the world unauthenticated; `EligibilityTest.notLoggedInWinsOverEverything` covers the rule. The neighbouring gate was proven end-to-end: a 26.1 bot without the proxy gets `De minigames werken alleen met Minecraft 26.3…`, log `refused: WRONG_VERSION` |
| 5 | Minigames stopped, `/minigames` | PASS | `De minigames zijn nu niet beschikbaar…`, bot stays on survival; log `refused: minigame server minigames:25565 unreachable` |
| 6 | One character of the bridge secret changed | PASS | kicked on minigames; log `refused: ticket signature invalid — check that ticket-secret is the same on both servers` |
| 7 | Replay an old ticket | PASS | a client that ignores the cookie clear (`--keep-cookies`) reconnects with the used ticket → `refused: REUSED`; a normal client (cookie cleared) → `refused: MISSING` |
| 8 | Blank bridge secret | PASS | plugin enabled; `ERROR … ticket-secret is missing or invalid … travel to minigames is disabled until it is set`; player gets `niet beschikbaar` and stays |
| 9 | Bot on minigames, minigames stops | PASS | `De minigameserver start opnieuw op.` → `transferred` → `joined` survival without a login dialog; log `shutdown: sent 1 player(s) to survival`. Transfer arrives before the shutdown kick, no delay needed |
| 10 | Ticket for `CaseBot` presented as `casebot` | PASS | `refused: WRONG_PLAYER` |
| 11 | Admin places NPC, another bot right-clicks it | PASS | `Minigames-NPC geplaatst.`; `interacted` → `Je gaat naar de minigames...` → `transferred`; minigames `NpcBot arrived` |

## Found and fixed along the way

- Paper 26.3 fires only `PlayerInteractAtEntityEvent` for entity right-clicks; the NPC listener on
  `PlayerInteractEntityEvent` still receives it (shared HandlerList) — confirmed.
- The NPC listener now uses `ignoreCancelled = true`, so clicks AuthMe or region protection cancelled
  are respected.
- Harness: bots must confirm teleports, report `player_loaded` and respawn like a real client; test
  servers run peaceful (a creeper killed a bot mid-check), with flight allowed and spawn radius 0.
