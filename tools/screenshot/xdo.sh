#!/bin/sh
# Drive the client window with xdotool: xdo.sh <xdotool arguments...>
# Examples: xdo.sh key F5   |   xdo.sh mousedown 3   |   xdo.sh mousemove_relative -- 200 0
exec docker exec -e DISPLAY=:99 dbsdw-shot-client xdotool "$@"
