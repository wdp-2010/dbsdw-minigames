"""Offline previews of a Canvas: an isometric view and a top-down map, as PNG, pure Python.

Cheap enough to render after every change, so real in-game screenshots are only needed for the
final look (lighting, textures). Colours are averages of the vanilla textures, by hand.
"""
from __future__ import annotations

import struct
import zlib

from mcbuild import Canvas, is_full, parse

COLORS = {
    "grass_block": (95, 140, 55), "dirt": (134, 96, 67), "coarse_dirt": (119, 85, 59),
    "rooted_dirt": (144, 103, 76), "podzol": (91, 63, 24), "mud": (60, 57, 60),
    "packed_mud": (142, 106, 79), "mud_bricks": (137, 103, 79), "mud_brick_slab": (137, 103, 79),
    "mud_brick_wall": (137, 103, 79), "mud_brick_stairs": (137, 103, 79),
    "dirt_path": (148, 122, 65), "gravel": (131, 127, 126), "sand": (219, 207, 163),
    "red_sand": (190, 102, 33), "sandstone": (216, 203, 155), "cut_sandstone": (217, 206, 159),
    "smooth_sandstone": (223, 214, 170), "chiseled_sandstone": (216, 202, 154),
    "suspicious_sand": (217, 203, 158), "stone": (125, 125, 125), "cobblestone": (127, 127, 127),
    "mossy_cobblestone": (110, 118, 94), "stone_bricks": (122, 121, 122),
    "mossy_stone_bricks": (115, 121, 105), "cracked_stone_bricks": (118, 117, 118),
    "andesite": (136, 136, 136), "polished_andesite": (132, 135, 134), "tuff": (108, 109, 102),
    "diorite": (188, 188, 188), "granite": (149, 103, 85), "calcite": (223, 224, 220),
    "moss_block": (89, 109, 45), "moss_carpet": (89, 109, 45), "clay": (160, 166, 179),
    "water": (63, 118, 228), "oak_log": (109, 85, 50), "oak_wood": (109, 85, 50),
    "stripped_oak_log": (177, 144, 86), "oak_planks": (162, 130, 78), "spruce_log": (58, 37, 16),
    "spruce_wood": (58, 37, 16), "stripped_spruce_log": (115, 89, 52), "spruce_planks": (114, 84, 48),
    "dark_oak_log": (60, 46, 26), "dark_oak_planks": (66, 43, 20), "birch_log": (216, 215, 210),
    "birch_planks": (192, 175, 121), "jungle_log": (85, 67, 25), "jungle_planks": (160, 115, 80),
    "oak_leaves": (60, 120, 30), "spruce_leaves": (40, 80, 45), "birch_leaves": (105, 140, 60),
    "dark_oak_leaves": (45, 100, 25), "jungle_leaves": (50, 135, 25), "azalea_leaves": (90, 115, 45),
    "hay_block": (166, 136, 38), "barrel": (120, 88, 50), "composter": (120, 80, 40),
    "green_terracotta": (76, 83, 42), "brown_terracotta": (77, 51, 35), "terracotta": (152, 94, 67),
    "yellow_terracotta": (186, 133, 35), "orange_terracotta": (161, 83, 37),
    "white_terracotta": (209, 178, 161), "light_gray_terracotta": (135, 106, 97),
    "lime_terracotta": (103, 117, 52), "cyan_terracotta": (86, 91, 91), "gray_terracotta": (57, 42, 35),
    "green_wool": (84, 109, 27), "brown_wool": (114, 71, 40), "white_wool": (233, 236, 236),
    "yellow_wool": (248, 197, 39), "lime_wool": (112, 185, 25), "orange_wool": (240, 118, 19),
    "green_concrete": (73, 91, 36), "orange_concrete": (224, 97, 0), "yellow_concrete": (240, 175, 21),
    "white_concrete": (207, 213, 214), "lime_concrete": (94, 168, 24), "gray_concrete": (54, 57, 61),
    "light_gray_concrete": (125, 125, 115), "brown_concrete": (96, 59, 31),
    "iron_block": (220, 220, 220), "iron_chain": (60, 64, 75), "iron_bars": (120, 120, 120),
    "lantern": (200, 150, 70), "glass": (200, 220, 230), "glass_pane": (200, 220, 230),
    "scaffolding": (170, 135, 75), "anvil": (68, 68, 68), "polished_basalt": (88, 88, 92),
    "smooth_stone": (159, 159, 159), "bamboo_block": (127, 140, 50), "dead_bush": (107, 79, 40),
    "lightning_rod": (180, 100, 70), "cut_copper": (191, 106, 80), "weathered_cut_copper": (109, 145, 107),
    "exposed_cut_copper": (154, 121, 101), "oxidized_cut_copper": (82, 162, 132),
    "bone_block": (229, 225, 207), "pale_oak_planks": (227, 217, 216), "target": (226, 170, 157),
    "bricks": (150, 97, 83), "red_banner": (176, 46, 38), "blue_banner": (60, 68, 170),
    "red_wall_banner": (176, 46, 38), "blue_wall_banner": (60, 68, 170), "short_dry_grass": (190, 160, 90),
    "tall_dry_grass": (190, 160, 90), "sugar_cane": (120, 170, 80), "lily_pad": (40, 110, 40),
    "white_carpet": (233, 236, 236), "yellow_carpet": (248, 197, 39), "coal_block": (20, 20, 22),
    "blackstone": (42, 36, 41), "polished_blackstone": (53, 48, 56), "flower_pot": (120, 70, 50), "spruce_fence": (114, 84, 48), "mossy_cobblestone_wall": (110, 118, 94), "decorated_pot": (140, 80, 60), "cauldron": (70, 70, 70),
}

FALLBACK = [
    ("leaves", (60, 120, 35)), ("log", (100, 75, 45)), ("planks", (150, 115, 70)),
    ("fence", (130, 100, 60)), ("trapdoor", (130, 100, 60)), ("door", (130, 100, 60)),
    ("grass", (90, 145, 50)), ("daisy", (230, 230, 210)), ("dandelion", (240, 220, 50)), ("ladder", (150, 115, 70)), ("stripped_spruce", (115, 89, 52)), ("bluet", (200, 210, 230)), ("table", (130, 95, 60)), ("chiseled", (120, 120, 120)), ("fern", (80, 130, 50)), ("flower", (220, 200, 60)),
    ("sandstone", (216, 203, 155)), ("stone", (125, 125, 125)), ("cobble", (125, 125, 125)),
    ("wool", (200, 200, 200)), ("carpet", (200, 200, 200)), ("terracotta", (150, 100, 70)),
    ("concrete", (150, 150, 150)), ("copper", (160, 110, 80)), ("brick", (140, 100, 80)),
]

DECOR = ("short_grass", "dry_grass", "banner", "sugar_cane", "lily_pad", "tall_grass", "fern", "flower", "dandelion", "poppy", "tulip", "daisy",
         "bush", "torch", "petals", "sapling", "leaf_litter", "button", "lever", "allium", "cornflower",
         "bluet", "orchid", "candle", "lily")

_cache: dict[str, tuple[int, int, int] | None] = {}


def color(state: str):
    if state in _cache:
        return _cache[state]
    name = parse(state)[0]
    for suffix in ("_slab", "_stairs", "_wall", "_fence", "_trapdoor", "_carpet"):
        base = name[: -len(suffix)] if name.endswith(suffix) else None
        if base and base in COLORS:
            c = COLORS[base]
            break
        if base and base + "s" in COLORS:
            c = COLORS[base + "s"]
            break
        if base and base + "_wool" in COLORS:
            c = COLORS[base + "_wool"]
            break
        if base and base + "_planks" in COLORS:
            c = COLORS[base + "_planks"]
            break
    else:
        c = COLORS.get(name)
    if name in COLORS:
        c = COLORS[name]
    if c is None:
        for k, v in FALLBACK:
            if k in name:
                c = v
                break
    if name in ("barrier", "air", "light"):
        c = None
    elif c is None:
        c = (255, 0, 255)  # unknown: loud magenta so it gets a colour
    _cache[state] = c
    return c


def write_png(path, w, h, px: bytearray):
    raw = b"".join(b"\x00" + bytes(px[y * w * 3:(y + 1) * w * 3]) for y in range(h))

    def chunk(t, d):
        c = struct.pack(">I", len(d)) + t + d
        return c + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")
        f.write(chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)))
        f.write(chunk(b"IDAT", zlib.compress(raw, 6)))
        f.write(chunk(b"IEND", b""))


_MASK = ["...TT...",
         ".TTTTTT.",
         "LLTTTTRR",
         "LLLTTRRR",
         "LLLLRRRR",
         "LLLLRRRR",
         ".LLLRRR.",
         "...LR..."]


def iso(canvas: Canvas, path, scale=1, box=None, sky=(170, 200, 230)):
    """Isometric view from the south-east. `box` = (x1, z1, x2, z2) crops to a region."""
    T = 4 * scale
    b = canvas.b
    x1, z1, x2, z2 = box or (0, 0, canvas.sx - 1, canvas.sz - 1)
    mask = [[_MASK[r // scale][c // scale] for c in range(2 * T)] for r in range(2 * T)]
    nx, nz = x2 - x1 + 1, z2 - z1 + 1
    w = (nx + nz) * T
    h = (nx + nz) * T // 2 + canvas.sy * T + T
    px = bytearray(bytes(sky) * (w * h))

    def put(u, v, c):
        if 0 <= u < w and 0 <= v < h:
            i = (v * w + u) * 3
            px[i:i + 3] = bytes(c)

    cells = [(k, s) for k, s in b.items() if x1 <= k[0] <= x2 and z1 <= k[2] <= z2]
    cells.sort(key=lambda kv: kv[0][0] + kv[0][1] + kv[0][2])
    for (x, y, z), s in cells:
        c = color(s)
        if c is None:
            continue
        if is_full(b.get((x + 1, y, z))) and is_full(b.get((x, y + 1, z))) and is_full(b.get((x, y, z + 1))):
            continue
        lx, lz = x - x1, z - z1
        u0 = (lx - lz) * T + nz * T - T
        v0 = (lx + lz) * T // 2 + (canvas.sy - y) * T
        name = parse(s)[0]
        decor = any(k in name for k in DECOR)
        low = "slab" in name and "top" not in s or "carpet" in name
        top_c = c
        # simple ambient occlusion on the top face: darker when something stands next to it above
        occl = sum(1 for d in ((1, 0), (-1, 0), (0, 1), (0, -1)) if is_full(b.get((x + d[0], y + 1, z + d[1]))))
        f_top = 1.0 - 0.08 * occl
        for r in range(2 * T):
            for col in range(2 * T):
                m = mask[r][col]
                if m == ".":
                    continue
                if decor and not (T // 2 <= col < T * 3 // 2 and T // 2 <= r < T * 3 // 2):
                    continue
                if low and m == "T":
                    rr = r + T // 2
                elif low:
                    if r < T + T // 2:
                        continue
                    rr = r
                else:
                    rr = r
                if m == "T":
                    f = f_top
                    edge = mask[r - 1][col] == "." if r > 0 else True
                    if edge:
                        f *= 0.9
                elif m == "L":
                    f = 0.78
                else:
                    f = 0.62
                put(u0 + col, v0 + rr, tuple(min(255, int(ch * f)) for ch in top_c))
    write_png(path, w, h, px)
    return path


def topdown(canvas: Canvas, path, scale=4, marks=()):
    """Map view: top block colours with hillshade; marks = [(x, z, (r, g, b))]."""
    w, h = canvas.sx * scale, canvas.sz * scale
    px = bytearray(w * h * 3)
    heights = {}
    tops = {}
    for (x, y, z), s in canvas.b.items():
        if color(s) is None:
            continue
        if y > heights.get((x, z), -1):
            heights[(x, z)] = y
            tops[(x, z)] = s
    for z in range(canvas.sz):
        for x in range(canvas.sx):
            s = tops.get((x, z))
            c = color(s) if s else (0, 0, 0)
            hh = heights.get((x, z), 0)
            shade = 1.0 + 0.06 * (hh - heights.get((x - 1, z - 1), hh))
            shade = max(0.6, min(1.35, shade)) * (0.85 + 0.15 * min(1.0, hh / 20))
            cc = bytes(min(255, int(ch * shade)) for ch in c)
            for dy in range(scale):
                i = ((z * scale + dy) * w + x * scale) * 3
                px[i:i + 3 * scale] = cc * scale
    for (mx, mz, mc) in marks:
        for dz in range(-scale, scale * 2):
            for dx in range(-scale, scale * 2):
                u, v = int(mx * scale) + dx, int(mz * scale) + dz
                if 0 <= u < w and 0 <= v < h:
                    i = (v * w + u) * 3
                    px[i:i + 3] = bytes(mc)
    write_png(path, w, h, px)
    return path
