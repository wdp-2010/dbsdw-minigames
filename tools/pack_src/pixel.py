"""Tiny pixel-art toolkit on top of Pillow: hard-edged masks, cartoon shading and outlines.

Everything is drawn at the final resolution without anti-aliasing, so the result stays crisp in
game. Masks are 'L' images (0 or 255). All randomness takes an explicit random.Random, so every
asset is reproducible.
"""

from __future__ import annotations

import math
import random
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

Colour = tuple[int, int, int]


def mask(size: tuple[int, int]) -> Image.Image:
    return Image.new("L", size, 0)


def ellipse(m: Image.Image, box: tuple[float, float, float, float]) -> Image.Image:
    ImageDraw.Draw(m).ellipse(box, fill=255)
    return m


def polygon(m: Image.Image, points: list[tuple[float, float]]) -> Image.Image:
    ImageDraw.Draw(m).polygon(points, fill=255)
    return m


def rect(m: Image.Image, box: tuple[float, float, float, float], radius: int = 0) -> Image.Image:
    ImageDraw.Draw(m).rounded_rectangle(box, radius=radius, fill=255)
    return m


def union(*masks: Image.Image) -> Image.Image:
    out = masks[0]
    for m in masks[1:]:
        out = ImageChops.lighter(out, m)
    return out


def subtract(a: Image.Image, b: Image.Image) -> Image.Image:
    return ImageChops.subtract(a, b)


def intersect(a: Image.Image, b: Image.Image) -> Image.Image:
    return ImageChops.darker(a, b)


def shift(m: Image.Image, dx: int, dy: int) -> Image.Image:
    out = mask(m.size)
    out.paste(m, (dx, dy))
    return out


def dilate(m: Image.Image, r: int = 1, diagonal: bool = True) -> Image.Image:
    out = m
    for _ in range(r):
        parts = [out, shift(out, 1, 0), shift(out, -1, 0), shift(out, 0, 1), shift(out, 0, -1)]
        if diagonal:
            parts += [shift(out, 1, 1), shift(out, -1, -1), shift(out, 1, -1), shift(out, -1, 1)]
        out = union(*parts)
    return out


def erode(m: Image.Image, r: int = 1) -> Image.Image:
    inv = ImageChops.invert(m)
    return ImageChops.invert(dilate(inv, r, diagonal=False))


def blob(rng: random.Random, size: int, cx: float, cy: float, radius: float, bumps: int,
         wobble: float, steps: int = 64) -> Image.Image:
    """A splat-like closed shape: a circle whose radius wobbles with a few random sine bumps."""
    phases = [(rng.uniform(0, math.tau), rng.randint(2, 7), rng.uniform(0.3, 1.0)) for _ in range(bumps)]
    pts = []
    for i in range(steps):
        a = math.tau * i / steps
        r = radius * (1 + wobble * sum(amp * math.sin(k * a + p) for p, k, amp in phases) / max(1, bumps))
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return polygon(mask((size, size)), pts)


def lighten(c: Colour, f: float) -> Colour:
    return tuple(round(v + (255 - v) * f) for v in c)  # type: ignore[return-value]


def darken(c: Colour, f: float) -> Colour:
    return tuple(round(v * (1 - f)) for v in c)  # type: ignore[return-value]


class Canvas:
    def __init__(self, w: int, h: int | None = None):
        self.img = Image.new("RGBA", (w, h or w), (0, 0, 0, 0))

    @property
    def size(self) -> tuple[int, int]:
        return self.img.size

    def fill(self, m: Image.Image, colour: Colour, alpha: int = 255) -> "Canvas":
        layer = Image.new("RGBA", self.size, colour + (alpha,))
        self.img.paste(layer, (0, 0), m)
        return self

    def shaded(self, m: Image.Image, base: Colour, light: float = 0.45, shade: float = 0.30,
               outline: Colour | None = None, thick: int = 1) -> "Canvas":
        """Cartoon fill: base colour, a light rim top-left, a dark rim bottom-right, outline."""
        self.fill(m, base)
        self.fill(subtract(m, shift(m, -1, -1)), darken(base, shade))
        self.fill(subtract(m, shift(m, 1, 1)), lighten(base, light))
        if outline is not None:
            self.fill(subtract(dilate(m, thick), m), outline)
        return self

    def outline(self, m: Image.Image, colour: Colour, thick: int = 1) -> "Canvas":
        return self.fill(subtract(dilate(m, thick), m), colour)

    def px(self, x: int, y: int, colour: Colour, alpha: int = 255) -> "Canvas":
        if 0 <= x < self.size[0] and 0 <= y < self.size[1]:
            self.img.putpixel((x, y), colour + (alpha,))
        return self

    def opaque_mask(self) -> Image.Image:
        return self.img.getchannel("A").point(lambda a: 255 if a else 0)

    def save(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        # Fixed PNG settings and no metadata, so the bytes only change when the pixels do.
        self.img.save(path, format="PNG", optimize=False, compress_level=9)
