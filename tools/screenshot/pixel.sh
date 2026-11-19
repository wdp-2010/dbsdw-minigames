#!/bin/sh
# Print the colour of one screen pixel as "r g b": pixel.sh <x> <y>
exec docker exec -e DISPLAY=:99 dbsdw-shot-client sh -c "xwd -root -silent | xwdtopnm 2>/dev/null | python3 -c '
import sys
data = sys.stdin.buffer.read()
# binary PPM: magic, width, height, maxval separated by whitespace, then RGB bytes
parts, i = [], 0
while len(parts) < 4:
    while data[i:i+1].isspace(): i += 1
    j = i
    while not data[j:j+1].isspace(): j += 1
    parts.append(data[i:j]); i = j
i += 1
w = int(parts[1]); x, y = $1, $2
o = i + (y * w + x) * 3
print(data[o], data[o+1], data[o+2])
'"
