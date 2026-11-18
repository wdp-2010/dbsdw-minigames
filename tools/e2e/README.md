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
docker compose up -d survival minigames packhost
```

Bot dependencies: `cd bot && npm install --ignore-scripts` (inside `node:22-bookworm-slim`).

## Use

- Console command: `echo 'op SomeBot' > servers/survival/console.in`
- Bot (on the compose network):
  ```sh
  docker run --rm -i --memory 256m --network dbsdw-e2e_default -u 1000:1000 \
    -v /opt/dbsdw-e2e/bot:/bot -w /bot node:22-bookworm-slim \
    node hop.js --host survival --port 25565 --name Bot --password pw --command "/minigames"
  ```
  It prints one JSON line per event (`joined`, `transferred`, `dialog_shown`, `kicked`, ...).
  See the header of `hop.js` for all flags.
- Each container is capped at 1.5 GB (`mem_limit`), ports are bound to 127.0.0.1.

## Tear down

`docker compose down && rm -rf /opt/dbsdw-e2e`
