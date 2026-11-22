#!/bin/sh
# Build an arena on a TEST server: tools/maps/deploy.sh <map> [--arena]
#   1. python3 tools/maps/build.py <map> must have run (out/<map>/ exists)
#   2. uploads the datapack functions to <server>/world/datapacks/dbsdw_maps and reloads datapacks
#   3. force-loads the area, runs build_0..n (clear + strict fills), unloads it
#   --arena also copies out/<map>/<map>.yml to plugins/MinigameCore/arenas/ (loaded at the next start)
# Test servers only (default server 5, DBSDW Minigames); never live.
set -eu
MAP="$1"
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$HERE/out/$MAP"
SERVER="${MCC_SERVER:-7512ee6f-f994-417f-8945-47967bd4ca25}"
KEY="${MCC_KEY:-$HOME/.ssh/wings-node_ed25519}"
case "$SERVER" in b69a7f63*) echo "deploy: refusing the live server" >&2; exit 2 ;; esac
VOL="/var/lib/pterodactyl/volumes/$SERVER"
MCC="$HERE/../console/mcc"
[ -d "$OUT/datapack/dbsdw_maps" ] || { echo "run build.py $MAP first" >&2; exit 1; }

tar -C "$OUT/datapack" -cf - dbsdw_maps | ssh -i "$KEY" -o BatchMode=yes root@62.83.125.228 \
  "rm -rf '$VOL/world/datapacks/dbsdw_maps/data/dbsdw_maps/function/$MAP' && \
   tar -C '$VOL/world/datapacks' -xf - && chown -R 988:988 '$VOL/world/datapacks/dbsdw_maps'"
if MCC_WAIT=5 "$MCC" "minecraft:reload" | grep -E "Failed to load function|Whilst parsing"; then
  echo "deploy: the server refused a function (see above); nothing built" >&2
  exit 1
fi

n=$(cat "$OUT/parts.txt")
MCC_WAIT=6 "$MCC" "function dbsdw_maps:$MAP/forceload"
i=0
while [ "$i" -lt "$n" ]; do
  MCC_WAIT=8 "$MCC" "function dbsdw_maps:$MAP/build_$i"
  i=$((i + 1))
done
MCC_WAIT=2 "$MCC" "function dbsdw_maps:$MAP/unload"

if [ "${2:-}" = "--arena" ]; then
  ssh -i "$KEY" -o BatchMode=yes root@62.83.125.228 \
    "cat > '$VOL/plugins/MinigameCore/arenas/$MAP.yml' && chown 988:988 '$VOL/plugins/MinigameCore/arenas/$MAP.yml'" \
    < "$OUT/$MAP.yml"
  echo "arena file $MAP.yml written (loaded at the next server start)"
fi
