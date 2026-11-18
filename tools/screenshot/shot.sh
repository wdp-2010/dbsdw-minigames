#!/bin/sh
# Take an in-game screenshot (F2) and copy it to /opt/dbsdw-shot/out/<label>.png
set -eu
dir=/opt/dbsdw-shot/game/screenshots
before=$(ls "$dir" 2>/dev/null | wc -l)
docker exec -e DISPLAY=:99 dbsdw-shot-client xdotool key F2
for _ in $(seq 1 50); do
  [ "$(ls "$dir" 2>/dev/null | wc -l)" -gt "$before" ] && break
  sleep 0.2
done
sleep 0.5
cp "$dir/$(ls -t "$dir" | head -1)" "/opt/dbsdw-shot/out/$1.png"
echo "/opt/dbsdw-shot/out/$1.png"
