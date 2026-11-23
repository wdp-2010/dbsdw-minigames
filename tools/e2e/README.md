# End-to-end harness (throwaway servers)

Two Paper 26.3 servers (`survival`, `minigames`), an optional Folia server (`folia` profile) and
a static pack host, plus `bot/hop.js`, a headless client that handles cookies, transfers,
resource packs and AuthMe's pre-join dialog. **Run it on node1**, never on the WDP host (no spare
RAM) and never against live.

## Setup on node1

```sh
tar -C tools -cf - e2e | ssh -i ~/.ssh/wings-node_ed25519 root@62.83.125.228 \
  'mkdir -p /opt/dbsdw-e2e && tar -C /opt/dbsdw-e2e --strip-components=1 -xf -'
# then on node1, in /opt/dbsdw-e2e:
./fetch-server-jars.sh                 # downloads pinned jars, checks sha256, prepares servers/
cp <plugin jars> servers/<name>/plugins/
chown -R 1000:1000 /opt/dbsdw-e2e
docker compose up -d survival minigames packhost viaproxy viaproxy-minigames
```

Bots speak 26.1; connect them to `viaproxy` (→ survival) or `viaproxy-minigames` so the servers
see a 26.3 client. Test servers run peaceful with flight allowed; set `gamerule respawn_radius 0`
on survival for NPC checks.

Bot dependencies: `cd bot && npm install --ignore-scripts` (inside `node:22-bookworm-slim`).

## Use

- Console command: `echo 'op SomeBot' > servers/survival/console.in`
- Bot (on the compose network):
  ```sh
  docker run --rm -i --memory 256m --network dbsdw-e2e_default -u 1000:1000 \
    -v /opt/dbsdw-e2e/bot:/bot -w /bot node:22-bookworm-slim \
    node hop.js --host viaproxy --port 25565 --name Bot --password pw --command "/minigames"
  ```
  It prints one JSON line per event (`joined`, `transferred`, `dialog_shown`, `kicked`,
  `held_items`, `window_open`, `title`, `position`, ...). See the header of `hop.js` for all flags.
- Lobby and menu steps run after the last join, in order: `--do use:0` (right-click hotbar slot 0),
  `--do click:11` (click a slot of the open menu), `--do cmd:/leave`, `--do wait:3`,
  `--do interact:mannequin`, `--do drop:0`, `--do quit`,
  `--do aim:<player>` (look at that player), `--do fire:<n>` (n right-clicks 200 ms apart, like
  holding the button). Extra events: `actionbar`, `entity_spawn` (snowballs), `visible`, `aimed`.
- Playing bots: `--requeue 11` keeps a bot online and back in a mode's queue after every match,
  `--fight 35` aims and fires at the nearest enemy, `--walk` makes it move like a player
  (`world.js` reads the chunks: A* path to the nearest known enemy or the way its spawn faces,
  collision, step-up, jumps, gravity; it then also fires only with a clear line of sight).
  When the server keeps putting it back on the same spot (`setback` event), it avoids that block
  for 20 s. Set `WALK_DEBUG=1` to log the blocks around a setback and the moves sent after it.
- The bot acts like a 26.3 client where the server needs it: it sends `tick_end` every 50 ms
  (without it the second real move packet gets "Invalid move player packet received") and
  acknowledges chunk batches (without it the server stops sending chunks, and players in chunks it
  has not sent are never shown). The process exits once the bot is disconnected for good, so a
  container restart policy brings a long-running bot back.
- A match countdown is 30 s unless the queue is full; time bot steps after `Start!`.
  `--stay` also ends earlier legs of the trip, so keep it at 3 or more (the `--command` goes out
  1.5 s after a join).
- Paper's `connection-throttle` (in `bukkit.yml`) kicks bots that join within 4 s of each other;
  set it to `0` on the test servers when several bots run at once.
- Each container is capped at 1.5 GB (`mem_limit`), ports are bound to 127.0.0.1.

## Tear down

`docker compose down && rm -rf /opt/dbsdw-e2e`
