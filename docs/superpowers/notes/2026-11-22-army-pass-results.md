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

## Afternoon pass (owner feedback)

Owner on the scope: "perfect". Bot difficulty: leave as is. All below is live on server 5 and
checked with `PaintBot2` (events in the bot log, no screenshots).

- Gun flicker: the side inlay had been snapped onto the body's side plane (z-fighting); the muzzle,
  the barrel's back part and the trigger guard had smaller coplanar spots. Fixed in `models.py`;
  `test_models.py` now rejects visible coplanar faces.
- Raw `hud.streak` on screen: the server kept an older version-2 `messages.yml` without that key.
  Lesson: raise `version` whenever a key is added, not only when texts change.
- Queue action bar lagged the countdown title by up to 1 s (own once-a-second phase); it now also
  updates on the tick the countdown second changes.
- Scope zoom on the mouse wheel: 4 levels (walk-speed slowdown -0.3 / -0.6 default / -0.8 / -0.95;
  client FOV = (speed + 1) / 2, so 2x is the limit), subtitle "Zoom n/4", kept for the match.
  The scroll is a cancelled hotbar change, so number keys next to the gun slot also zoom.
- Practice range north of lobby spawn (x -8..8, z -9..-39, `tools/console/build-range.sh`): the
  zone hands out a practice gun in slot 3, five pop-up dummies (armor stands in army leather) sink
  after 3 hits and rise after 3 s. Splats on blocks, no player hits, nothing counted. Scope works
  there too (`Scope` class shared with matches). Config under `practice:` in Paintball's config.yml.
- Forfeit: "Rood wint!" with subtitle "Blauw gaf op · missie geslaagd!" and a chat line;
  `MatchOutcome.gaveUp()`.
- Bot: steps `sneak:on|off`, `slot:<n>`, `look:<yaw>:<pitch>`; events `held_slot`, `speed`.

Seen on server 5 around 14:06: someone else ran console commands (`forceload add 300 -48`, a test
`fill` at x 300, gamerule queries) and there are 75 zombie horses and a few farm animals in the
world. Not from this work.

## Evening: hit splash fills any screen

Owner: on a big screen the hit splash did not reach the screen edges. It was a title glyph, which
has a fixed size in GUI units, so with a large window, a low GUI scale or a wide aspect the edges
stayed clear. It is now a camera overlay (like the scope), which the client always stretches over
the whole screen: `Goggles` wears an invisible head item with `equippable.camera_overlay`
`dbsdw:misc/splash_<team>` for 8 ticks, then `splash_<team>_fade` for 6, then puts back what the
head slot should hold (carried flag, scope or nothing). A carried flag stays visible under the
paint (the flag item itself gets the overlay). Swaps make no sound (`intentionally_empty`). The
old `dbsdw:overlay` font is gone. Deployed to server 5 (jar + pack, backup
`/root/splash-backup-20261122/`); not yet seen in the real client.

## Evening: gun held up in third person

Owner asked for the arm to be raised in third person. In the 26.3 client (`AvatarRenderer.getArmPose`)
the only item held in an aiming pose without being used is a loaded crossbow (`CROSSBOW_HOLD`, both
arms up). The gun is now a crossbow with a charged arrow underneath (tooltip line hidden); the
plugin already cancels every right-click with it, and the client clears the charge only on the
server side, so it never fires. In first person the client shifts a loaded crossbow (translate x
-0.641864, turn 10 degrees about y); the gun model's first-person display undoes exactly that, so
the gun sits where it was tuned. The lobby NPC holds the same crossbow gun. Owner checked it in
the client: fine.

## 2026-11-23: PaintBot walks

- PaintBot (`/opt/dbsdw-bot` on node1, container `dbsdw-bot`, restart unless-stopped) had been
  kicked from survival for AFK on 22 Nov at 21:41 and stayed offline: the node process kept running
  after the kick, so Docker never restarted it. `hop.js` now exits once the bot is disconnected for
  good, and with `--requeue` it sends `/minigames` again when it lands somewhere other than the
  minigames lobby.
- The container now runs `--requeue 11 --fight 35 --walk`: in a match the bot walks a path
  (A* on the chunks it receives) to the nearest enemy, circles, closes in and backs off in a fight,
  and fires only with a clear line of sight. Checked on duinbasis and woudfront with `PaintBot2`;
  blowiek3008 played along in one test match (hits both ways).
- Two protocol gaps found on the way: 26.3 kicks a client that sends a second real move packet
  without `tick_end` in between, and without chunk batch acknowledgements the server stops sending
  chunks, so far-away players were never visible to `--fight`.
- One spot on woudfront (the dirt path west of the team-1 spawn, x 434 z 13) puts the bot back on
  every step; the cause is unknown. The bot now avoids a block it is put back from twice and walks
  around it.
- Recreate: `docker run -d --name dbsdw-bot --restart unless-stopped --network dbsdw-bot --memory 256m
  --memory-swap 512m -u 1000:1000 -w /bot -v /opt/dbsdw-bot/bot:/bot -v /opt/dbsdw-bot/pw:/pw:ro
  node:22-bookworm-slim sh -c 'exec node hop.js --host dbsdw-bot-viaproxy --port 25565 --name PaintBot
  --password "$(cat /pw)" --command /minigames --requeue 11 --fight 35 --walk'`. The previous
  `hop.js` is kept as `hop.js.bak-2311`.
