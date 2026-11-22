# Army pass: gun, look, texts, power-ups — results (2026-11-22)

Owner's request: take over the gun they edited in the deployed pack, put the loader boxes on the
pixel grid, redo the colours (army, not too serious), redo every text (hit marker as a subtitle under
the crosshair), overhaul power-ups (no glow, random reachable spots, quiet help for the losing team)
and five more improvements, checked with as few screenshots as possible, deployed at the end.

## What the owner had changed

The deployed `pack.zip` (re-zipped on a Mac on 2026-11-19) differed from the repo only in
`gun.json` and `gun.png`: the two inside-out hopper end caps removed (the generator built them with
`from > to`), the back cap 1 unit lower, new first-person (scale 1, rot [5,0,0], trans
[5.25,-4,-5.5]) and third-person (upright, trans [0,0.5,0.25]) right-hand values, and 4 pixels that
wrap the hopper window round the back corners. All of it is in `tools/pack_src/models.py` now; the
left hand mirrors the right.

The zip itself had everything inside a `pack/` folder plus `__MACOSX` and `.DS_Store` files, so
clients got no usable pack. `python3 tools/build_pack.py --check-zip <zip>` now refuses such a zip.

## Gun

- Every box corner sits on the 1/4-unit grid (one texture pixel at 4 px per unit); a 0.01 nudge
  only separates crossed boxes. `tools/test_models.py` enforces it, and no element may be inside out
  (build_pack.py and boxmodel.py refuse those too).
- Loader corners round in two 2-pixel steps instead of one deep notch; the back plate is 2 px thin
  and sunk 1 px into the loader (owner's call while reviewing).
- Olive body, khaki tank, gunmetal barrel and grip; the team colour stays on the loader, muzzle and
  inlay.

## Look

Olive patches with a coloured rim for the power-ups, a medal for the stats item, olive camo menu
background and pack icon, warm near-black outlines. Splats, paintballs and the hit overlay keep the
bright paint colours.

## Texts

All three `messages.yml` files rewritten (Dutch, army flavour, light). Hit marker, shield break,
elimination, streaks and power-up pickups show as a subtitle under the crosshair. Win/lose/draw
titles have a subtitle. Mode names are `Teamgevecht` and `Vlagroof` (from messages.yml now, not
config.yml). The files carry a `version`; a server with an older file gets it backed up as
`messages.yml.v<old>.bak` and replaced. `tools/test_messages.py` fails the build when Java asks for a
literal key that the file lacks.

## Gameplay

- Power-ups: no glow; up to `powerups.max-active` (3) at once on random floor that a player can walk
  to from the team spawns (flood fill: step up 1 with head room, drop up to 3; never roofs, wall tops
  or closed rooms), at least 4 blocks from players, 5 from spawns and 7 from each other.
- Catch-up (`powerups.catch-up`, default 1): when one team leads, spots near the trailing team's
  players weigh more and near them repair/shield come up more often. It grows with the score gap
  (full at 20 % of the win score) and is capped: in tests the trailing side gets about two thirds.
- Respawn at the team spawn furthest from living enemies (spawns within 4 blocks of the best are
  picked at random).
- Streaks: 3, 5, 10, ... eliminations in a row are announced; whoever ends a streak of 3+ is named.
- End of match: MVP line and each player's own line (eliminations, hits, accuracy, best streak),
  also when the match ends because the other side left.
- Lobby: the action bar shows the queue (players / needed, then the countdown).
- Sounds: punchier shot (sub-thump), dry-fire click when pulling the trigger while out.

## Tools

- `tools/console/mcc`: console commands on a test server through Wings, with the console output
  back; `--log N`, `--power start|stop|restart`. Refuses the live server.
- `tools/render/render.js`: offline item-model renderer (three.js in headless Chrome), first-person,
  GUI and side views, plus on-screen numbers. Its first-person placement matched a real-client
  screenshot of the old gun to about 1 % of the screen.
- `hop.js` reports subtitles.

## Checked on the test servers (no screenshots)

Server 5 (minigames) and server 4 (survival test) only; live untouched. Backups of the old jars,
configs, messages and pack are on node1 in `/root/army-backup-2211`.

- Startup: both message files upgraded v0 → v2; pack served with the new SHA1.
- Two bots (`PaintBot`, `PaintBot2`) through ViaProxy: queue action bar, "Kies je missie" menu,
  countdown subtitles, "GO GO GO!", 3 power-ups on random floor spots with the new item models,
  `RAAK!` / `UITGESCHAKELD` subtitles for the shooter, `GERAAKT!` for the victim, kill feed, streak
  announcement, "Rood wint!".
- A test with a bot teleported into a cover wall showed splats on the wall instead of hits: the
  arena, not the code. Bots need open floor (row z=8, x 107..111 in `proef`).

## Not done

- A practice gun with targets in the lobby (offered as part of idea 4): only the queue HUD was built.
- The owner's own client check: gun feel in hand, sounds, colours in the real client.

## Later the same day

- Minigame world locked on day: `execute in minecraft:overworld run gamerule advance_time false` plus
  `advance_weather false`, `time set day`, `weather clear` (plain console `gamerule` throws in Paper
  26.3; `doDaylightCycle` no longer exists). Survives restarts.
- Queue countdown 15 s (`queue.countdown-seconds`, repo default and server 5).
- PaintBot (`/opt/dbsdw-bot`, container `dbsdw-bot`, restart unless-stopped) now runs with
  `--fight 35`: a turret that aims at the nearest enemy and fires bursts. It does not walk.
- Scope: sneak with the gun = slower walk (client zoom with FOV effects on), 0.2° spread, camera
  overlay `dbsdw:misc/scope` on an invisible helmet. `gun.gravity: 0.5` halves the paintball drop.
  Built and tested (unit tests, pack checks); deployed to server 5 after the owner's running match.
  Not yet tried in the real client.
