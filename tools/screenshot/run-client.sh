#!/bin/sh
# Start the screenshot client: run-client.sh <player name> <host:port>
# State lives in /opt/dbsdw-shot (cache = downloads, game = .minecraft, out = screenshots).
set -eu
cd "$(dirname "$0")"
mkdir -p /opt/dbsdw-shot/cache /opt/dbsdw-shot/game /opt/dbsdw-shot/out
docker build -q -t dbsdw-shot-client . >/dev/null
docker rm -f dbsdw-shot-client >/dev/null 2>&1 || true
rm -f /opt/dbsdw-shot/game/logs/latest.log
docker run -d --name dbsdw-shot-client --memory 3g --memory-swap 3g --cpus 3 \
  -v /opt/dbsdw-shot/cache:/cache -v /opt/dbsdw-shot/game:/game \
  dbsdw-shot-client "$1" "$2"
