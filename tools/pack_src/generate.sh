#!/bin/sh
# Regenerate every generated asset in resourcepack/ and the preview sheet.
# Usage (from the repo root): tools/pack_src/generate.sh [preview.png]
set -eu
cd "$(dirname "$0")/../.."
docker build -q -t dbsdw-pack-tools tools/pack_src >/dev/null
run() { docker run --rm -u "$(id -u):$(id -g)" -v "$PWD":/work -w /work/tools/pack_src dbsdw-pack-tools "$@"; }
for script in textures.py models.py chess.py glyphs.py sounds.py; do
  [ -f "tools/pack_src/$script" ] && run python3 "$script" /work/resourcepack
done
run python3 preview.py /work/resourcepack "/work/${1:-build/pack-preview.png}"
