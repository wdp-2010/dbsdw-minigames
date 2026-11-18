#!/bin/sh
# Grab the whole virtual screen as PNG (also works on menus and loading screens): grab.sh <label>
set -eu
docker exec -e DISPLAY=:99 dbsdw-shot-client sh -c 'xwd -root -silent | xwdtopnm 2>/dev/null | pnmtopng' \
  > "/opt/dbsdw-shot/out/$1.png"
echo "/opt/dbsdw-shot/out/$1.png"
