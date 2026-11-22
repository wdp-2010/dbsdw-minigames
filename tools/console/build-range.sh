#!/bin/sh
# Builds the Paintball practice range in the lobby world of a test server, north of spawn
# (x -8..8, z -9..-39, floor y -63), matching practice: in paintball/src/main/resources/config.yml.
# Idempotent: it fills the same blocks again. Uses mcc, so never the live server.
#
#   tools/console/build-range.sh
set -e
mcc="$(dirname "$0")/mcc"
in="execute in minecraft:overworld run"
MCC_WAIT=2 "$mcc" \
  "$in fill -8 -62 -9 8 -55 -39 minecraft:air" \
  "$in fill -7 -63 -10 7 -63 -13 minecraft:spruce_planks" \
  "$in fill -7 -63 -14 7 -63 -38 minecraft:coarse_dirt" \
  "$in fill -6 -63 -16 6 -63 -38 minecraft:packed_mud replace minecraft:coarse_dirt" \
  "$in fill -7 -62 -14 7 -62 -14 minecraft:mud_brick_slab[type=bottom]" \
  "$in fill -8 -62 -9 -8 -60 -39 minecraft:green_terracotta" \
  "$in fill 8 -62 -9 8 -60 -39 minecraft:green_terracotta" \
  "$in fill -8 -62 -9 -8 -62 -39 minecraft:mud_bricks" \
  "$in fill 8 -62 -9 8 -62 -39 minecraft:mud_bricks" \
  "$in fill -8 -59 -9 -8 -59 -39 minecraft:spruce_slab[type=bottom]" \
  "$in fill 8 -59 -9 8 -59 -39 minecraft:spruce_slab[type=bottom]" \
  "$in fill -7 -62 -39 7 -57 -39 minecraft:green_terracotta" \
  "$in fill -7 -62 -39 7 -61 -39 minecraft:hay_block" \
  "$in fill -8 -58 -39 8 -58 -39 minecraft:moss_block" \
  "$in fill -8 -62 -9 -8 -58 -9 minecraft:spruce_log" \
  "$in fill 8 -62 -9 8 -58 -9 minecraft:spruce_log" \
  "$in fill -8 -57 -9 8 -57 -9 minecraft:dark_oak_log[axis=x]" \
  "$in kill @e[type=minecraft:text_display,tag=dbsdw_range]" \
  "$in summon minecraft:text_display 0.5 -55.6 -9.5 {Tags:[\"dbsdw_range\"],billboard:\"vertical\",background:-1442840576,text:{text:\"OEFENTERREIN\",color:\"#D9C78F\",bold:true},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[2.5f,2.5f,2.5f]}}"
