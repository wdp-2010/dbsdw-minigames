#!/bin/sh
# Start a virtual screen and the client. Arguments: <player name> <host:port>
set -eu
NAME="$1"; TARGET="$2"
Xvfb :99 -screen 0 1280x720x24 -nolisten tcp &
export DISPLAY=:99
for _ in $(seq 1 50); do xdpyinfo >/dev/null 2>&1 && break; sleep 0.2; done
python3 /tools/fetch_client.py prepare /cache /game "$NAME" "$TARGET"
export LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe LP_NUM_THREADS=3 SDL_VIDEODRIVER=x11
exec sh /game/launch.sh
