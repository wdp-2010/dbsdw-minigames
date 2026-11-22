"""Build a Minecraft elements model and its texture together from a list of boxes.

Every face of every box gets its own region in the texture, sized at PX_PER_UNIT pixels per model
unit, so outlines and highlights are the same thickness on every face (cartoon look). Faces of
tinted boxes are drawn in greys and carry tintindex 0.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Callable

from PIL import Image

from palette import OUTLINE
from pixel import Colour, darken, lighten

PX_PER_UNIT = 4
DIRS = ("north", "south", "east", "west", "up", "down")

# draw(face_image, direction) paints extra detail on one face after the base fill.
Decorate = Callable[[Image.Image, str], None]


@dataclass
class Box:
    frm: tuple[float, float, float]
    to: tuple[float, float, float]
    colour: Colour
    tint: bool = False
    faces: tuple[str, ...] = DIRS
    rotation: dict | None = None
    decorate: Decorate | None = None
    outline: Colour | None = OUTLINE
    gloss: bool = True
    shade: bool = True
    regions: dict = field(default_factory=dict)

    def face_size(self, d: str) -> tuple[float, float]:
        sx, sy, sz = (self.to[i] - self.frm[i] for i in range(3))
        return {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy),
                "up": (sx, sz), "down": (sx, sz)}[d]


def _face_image(box: Box, d: str, w: int, h: int) -> Image.Image:
    base = box.colour
    if box.shade and d == "up":
        base = lighten(base, 0.18)
    elif box.shade and d == "down":
        base = darken(base, 0.22)
    img = Image.new("RGBA", (w, h), base + (255,))
    if box.gloss and d not in ("up", "down") and h >= 6 and w >= 4:
        for x in range(1, w - 1):
            img.putpixel((x, 1), lighten(base, 0.45) + (255,))
        for x in range(1, w - 1):
            img.putpixel((x, h - 2), darken(base, 0.18) + (255,))
    if box.decorate:
        box.decorate(img, d)
    if box.outline is not None:
        for x in range(w):
            img.putpixel((x, 0), box.outline + (255,))
            img.putpixel((x, h - 1), box.outline + (255,))
        for y in range(h):
            img.putpixel((0, y), box.outline + (255,))
            img.putpixel((w - 1, y), box.outline + (255,))
    return img


def build(boxes: list[Box], texture_id: str, size: int = 128) -> tuple[dict, Image.Image]:
    """Pack all faces into one size x size texture; return (model elements json, texture)."""
    for b in boxes:
        if any(b.to[i] < b.frm[i] for i in range(3)):
            raise ValueError(f"{texture_id}: box {b.frm} -> {b.to} is inside out")
    faces = []
    for b in boxes:
        for d in b.faces:
            fw, fh = b.face_size(d)
            w, h = max(2, round(fw * PX_PER_UNIT)), max(2, round(fh * PX_PER_UNIT))
            faces.append((h, w, b, d))
    faces.sort(key=lambda f: (-f[0], -f[1]))
    tex = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    x = y = shelf = 0
    for h, w, b, d in faces:
        if x + w > size:
            x, y, shelf = 0, y + shelf, 0
        if y + h > size:
            raise ValueError(f"texture {texture_id} too small for all faces")
        tex.paste(_face_image(b, d, w, h), (x, y))
        k = 16 / size
        b.regions[d] = [x * k, y * k, (x + w) * k, (y + h) * k]
        x += w
        shelf = max(shelf, h)
    elements = []
    for b in boxes:
        el: dict = {"from": list(b.frm), "to": list(b.to), "faces": {}}
        if b.rotation:
            el["rotation"] = b.rotation
        for d in b.faces:
            face: dict = {"uv": [round(v, 4) for v in b.regions[d]], "texture": "#tex"}
            if b.tint:
                face["tintindex"] = 0
            el["faces"][d] = face
        elements.append(el)
    model = {"textures": {"tex": texture_id, "particle": texture_id}, "elements": elements}
    return model, tex
