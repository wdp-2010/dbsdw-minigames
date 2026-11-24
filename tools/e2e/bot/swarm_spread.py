#!/usr/bin/env python3
"""Console commands for the swarm.js load test, one per line, for tools/console/mcc (test servers only).

  python3 swarm_spread.py prep    [--count 50] [--prefix Swarm]   whitelist + LuckPerms group "swarm"
  python3 swarm_spread.py spread  [--count 50] [--spacing 3072] [--y 200]
  python3 swarm_spread.py cleanup [--count 50]                    undo prep, kick the bots

  python3 swarm_spread.py spread | xargs -d '\\n' env MCC_SERVER=9c30a937-af2e-4640-9e2d-a37bb5c99c7a tools/console/mcc

Layout: bots go in pairs (Swarm01+02, 03+04, ...), the two of a pair 6 blocks apart, so each pair
shares one Folia region. Pairs sit on a square grid, `spacing` blocks apart, centred on spawn with
the spawn cell left empty. With grid-exponent 4 a region section is 16 chunks (256 blocks); a
player's loaded chunks (view-distance 9, plus generation neighbours) touch up to ~2 sections each
way, Folia keeps empty sections 2 around them and merges regions whose sections are adjacent, so
pairs closer than ~9 sections (2304 blocks) can merge. 3072 keeps a margin.

Group "swarm" lets the bots past AuthMe's per-IP limits (they all come from one ViaProxy IP:
maxRegPerIp 10, maxJoinPerIp 20), keeps them in creative (no gravity, no fly kick, no deaths), and
keeps their joins/quits out of Discord.
"""
import argparse
import hashlib
import math
import uuid


def offline_uuid(name):
    """Java's UUID.nameUUIDFromBytes("OfflinePlayer:" + name): what an offline-mode server gives a player."""
    b = bytearray(hashlib.md5(('OfflinePlayer:' + name).encode()).digest())
    b[6] = (b[6] & 0x0F) | 0x30
    b[8] = (b[8] & 0x3F) | 0x80
    return str(uuid.UUID(bytes=bytes(b)))


def cells(pairs, spacing):
    """Grid cells around (0, 0), nearest first, skipping the spawn cell."""
    side = 1
    while side * side - 1 < pairs:
        side += 2
    half = side // 2
    out = [(i, j) for i in range(-half, half + 1) for j in range(-half, half + 1) if (i, j) != (0, 0)]
    out.sort(key=lambda c: (math.hypot(*c), c))
    return [(i * spacing, j * spacing) for i, j in out[:pairs]]


PERMS = ['authme.allowmultipleaccounts', 'authme.bypassantibot', 'authme.bypassforcesurvival',
         'discordsrv.silentjoin', 'discordsrv.silentquit']


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('mode', choices=['prep', 'spread', 'cleanup'])
    ap.add_argument('--count', type=int, default=50)
    ap.add_argument('--prefix', default='Swarm')
    ap.add_argument('--spacing', type=int, default=3072)
    ap.add_argument('--y', type=int, default=200)
    a = ap.parse_args()
    names = [f'{a.prefix}{n:02d}' for n in range(1, a.count + 1)]
    out = []
    if a.mode == 'prep':
        out.append('lp creategroup swarm')
        out += [f'lp group swarm permission set {p} true' for p in PERMS]
        for n in names:
            out.append(f'whitelist add {n}')
            out.append(f'lp user {offline_uuid(n)} parent add swarm')
    elif a.mode == 'spread':
        for k, (x, z) in enumerate(cells(math.ceil(len(names) / 2), a.spacing)):
            for m, n in enumerate(names[2 * k:2 * k + 2]):
                out.append(f'minecraft:gamemode creative {n}')
                out.append(f'tp {n} {x + 0.5 + 6 * m} {a.y} {z + 0.5}')
    else:
        for n in names:
            out.append(f'kick {n} load test over')
            out.append(f'whitelist remove {n}')
        out.append('lp deletegroup swarm')
    print('\n'.join(out))


if __name__ == '__main__':
    main()
