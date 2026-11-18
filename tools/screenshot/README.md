# Screenshot client

A real, unmodified Minecraft 26.3 client in Docker, rendered in software (Xvfb + Mesa; the
OpenGL backend finds no GLX visual under Xvfb and the client falls back on its own), driven
with `xdotool`. Used to check the resource pack and HUD with real in-game screenshots.

**Run it on node1 only**, never on the WDP host and never against live. The container is capped
at 3 GB RAM and 3 CPUs. Sound is off (no audio device).

## Setup on node1

```sh
tar -C tools -cf - screenshot | ssh -i ~/.ssh/wings-node_ed25519 root@62.83.125.228 \
  'mkdir -p /opt/dbsdw-shot && tar -C /opt/dbsdw-shot -xf -'
```

On the survival test server: whitelist the player and register it with AuthMe from the console,
with the password stored in `/opt/dbsdw-shot/pw` (mode 600; never print it).

## Use (on node1, in /opt/dbsdw-shot/screenshot)

| Script | Does |
|--------|------|
| `login.sh <name> <host:port>` | starts the client (downloads 26.3 into `/opt/dbsdw-shot/cache` once, ~600 MB, SHA1-checked), logs in through AuthMe's dialog, runs `/minigames` |
| `run-client.sh <name> <host:port>` | only starts the client (`--quickPlayMultiplayer`); the server entry has `acceptTextures` on, so required packs load without a prompt |
| `chat.sh "<text>"` | types a chat line or command |
| `xdo.sh <xdotool args>` | raw input, e.g. `key F5`, `mousedown 3`, `mousemove_relative -- 200 0` |
| `shot.sh <label>` | in-game screenshot (F2) to `/opt/dbsdw-shot/out/<label>.png` |
| `grab.sh <label>` | grabs the whole screen (also menus and loading screens) |

Client log: `/opt/dbsdw-shot/game/logs/latest.log`.

## Tear down

`docker rm -f dbsdw-shot-client && rm -rf /opt/dbsdw-shot` (and remove the test player from the
whitelist and AuthMe).
