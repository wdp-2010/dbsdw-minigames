"""Contact sheet of every texture in the pack (scaled up, on a checkerboard) for a quick look.

Run: python3 tools/pack_src/preview.py <resourcepack dir> <out.png>
"""

from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw

CELL = 150
SCALE_TO = 128


def checker(w: int, h: int) -> Image.Image:
    img = Image.new("RGBA", (w, h), (120, 124, 130, 255))
    d = ImageDraw.Draw(img)
    for y in range(0, h, 16):
        for x in range((y // 16) % 2 * 16, w, 32):
            d.rectangle((x, y, x + 15, y + 15), fill=(140, 144, 150, 255))
    return img


def main(pack: Path, out: Path) -> None:
    files = sorted(p for p in (pack / "assets").rglob("*.png")) + [pack / "pack.png"]
    files = [f for f in files if f.exists()]
    cols = 8
    rows = (len(files) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * CELL, rows * (CELL + 14)), (40, 40, 48, 255))
    d = ImageDraw.Draw(sheet)
    for i, f in enumerate(files):
        img = Image.open(f).convert("RGBA")
        w, h = img.size
        k = max(1, SCALE_TO // max(w, h))
        big = img.resize((w * k, h * k), resample=Image.NEAREST)
        if big.width > SCALE_TO or big.height > SCALE_TO:
            big.thumbnail((SCALE_TO, SCALE_TO), Image.NEAREST)
        x, y = (i % cols) * CELL, (i // cols) * (CELL + 14)
        cell = checker(CELL - 6, CELL - 6)
        cell.alpha_composite(big, ((cell.width - big.width) // 2, (cell.height - big.height) // 2))
        sheet.paste(cell, (x + 3, y + 3))
        d.text((x + 4, y + CELL - 2), f.stem[:22], fill=(230, 230, 230, 255))
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)


if __name__ == "__main__":
    main(Path(sys.argv[1]), Path(sys.argv[2]))
