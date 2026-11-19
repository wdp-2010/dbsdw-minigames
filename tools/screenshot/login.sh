#!/bin/sh
# Start the client, log in through AuthMe's pre-join dialog on the survival test server and travel
# to the minigame server with /minigames. Usage: login.sh <player name> <survival host:port>
# The AuthMe password is read from /opt/dbsdw-shot/pw (never printed).
set -eu
cd "$(dirname "$0")"
./run-client.sh "$1" "$2" >/dev/null
log=/opt/dbsdw-shot/game/logs/latest.log
for _ in $(seq 1 180); do grep -q "Connecting to" "$log" 2>/dev/null && break; sleep 1; done
# AuthMe shows its login dialog (black password field at 640,243 with a grey border at 640,224),
# unless its session login lets the player straight in. Wait for one or the other.
dialog=no
for _ in $(seq 1 40); do
  if [ "$(./pixel.sh 640 243)" = "0 0 0" ] && [ "$(./pixel.sh 440 243)" != "0 0 0" ]; then dialog=yes; break; fi
  grep -q "Logged-in due to Session" "$log" && break
  sleep 1
done
if [ "$dialog" = yes ]; then
  ./xdo.sh mousemove 640 243 click 1
  sleep 0.3
  ./xdo.sh type --delay 30 -- "$(cat /opt/dbsdw-shot/pw)"
  ./xdo.sh mousemove 487 303 click 1
fi
sleep 6
./chat.sh "/minigames"
for _ in $(seq 1 60); do [ "$(grep -c "Connecting to" "$log")" -ge 2 ] && break; sleep 1; done
sleep 10
echo "arrived"
