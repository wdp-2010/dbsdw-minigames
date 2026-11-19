#!/bin/sh
# Send a chat line or command from the client: chat.sh "/minigames"
set -eu
D="docker exec -e DISPLAY=:99 dbsdw-shot-client xdotool"
$D key t
sleep 0.8
$D type --delay 40 -- "$1"
sleep 0.2
$D key Return
