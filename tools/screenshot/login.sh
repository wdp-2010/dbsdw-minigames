#!/bin/sh
# Start the client, log in through AuthMe's pre-join dialog on the survival test server and travel
# to the minigame server with /minigames. Usage: login.sh <player name> <survival host:port>
# The AuthMe password is read from /opt/dbsdw-shot/pw (never printed).
set -eu
cd "$(dirname "$0")"
./run-client.sh "$1" "$2" >/dev/null
log=/opt/dbsdw-shot/game/logs/latest.log
for _ in $(seq 1 180); do grep -q "Connecting to" "$log" 2>/dev/null && break; sleep 1; done
sleep 8
# Dialog layout at 1280x720, guiScale 2: password field, then the Login button.
./xdo.sh mousemove 640 243 click 1
sleep 0.3
./xdo.sh type --delay 30 -- "$(cat /opt/dbsdw-shot/pw)"
./xdo.sh mousemove 487 303 click 1
sleep 6
./chat.sh "/minigames"
for _ in $(seq 1 60); do [ "$(grep -c "Connecting to" "$log")" -ge 2 ] && break; sleep 1; done
sleep 10
echo "arrived"
