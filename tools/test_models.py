"""Generated 3D models: no inside-out boxes, and the gun on the pixel grid.

Every corner of the gun sits on the 1/4-unit grid (one texture pixel at 4 pixels per unit, give or
take the 0.01 nudges that keep crossed boxes apart), so no face shows a stretched or cut pixel. Only
the axes a face spans count: a side-only inlay may stand a hair proud. No two faces looking the same
way may share a plane and overlap, or they flicker (z-fighting).
Run: python3 -m unittest discover -s tools
"""

from __future__ import annotations

import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODELS = ROOT / "resourcepack" / "assets" / "dbsdw" / "models" / "item"
GRID = 0.25
NUDGE = 0.011
# face -> (axis it looks along, sign, the two axes it spans)
FACES = {"east": (0, 1, (1, 2)), "west": (0, -1, (1, 2)), "up": (1, 1, (0, 2)),
         "down": (1, -1, (0, 2)), "south": (2, 1, (0, 1)), "north": (2, -1, (0, 1))}


class ModelTest(unittest.TestCase):
    def test_no_inside_out_boxes(self):
        for path in MODELS.glob("*.json"):
            for i, el in enumerate(json.loads(path.read_text(encoding="utf-8")).get("elements", [])):
                for k in range(3):
                    self.assertLessEqual(el["from"][k], el["to"][k], f"{path.name} element {i}")

    def test_gun_on_pixel_grid(self):
        gun = json.loads((MODELS / "gun.json").read_text(encoding="utf-8"))
        for i, el in enumerate(gun["elements"]):
            spanned = {k for face in el["faces"] for k in FACES[face][2]}
            for k in spanned:
                for v in (el["from"][k], el["to"][k]):
                    off = abs(v / GRID - round(v / GRID)) * GRID
                    self.assertLessEqual(off, NUDGE, f"gun element {i}: {el['from']} -> {el['to']}")

    def test_gun_no_coplanar_faces(self):
        """Faces looking the same way in one plane flicker, unless a third box hides the overlap."""
        gun = json.loads((MODELS / "gun.json").read_text(encoding="utf-8"))
        flat = [(i, el) for i, el in enumerate(gun["elements"]) if not el.get("rotation")]

        def hidden(plane, axis, sign, lo, hi, skip):
            # inside a third box, or pressed flat against one (the face looks into it)
            def blocks(c):
                start, end = c["from"][axis], c["to"][axis]
                return start < plane < end or abs(plane - (start if sign > 0 else end)) <= NUDGE

            return any(blocks(c)
                       and all(c["from"][k] <= lo[k] + NUDGE and c["to"][k] >= hi[k] - NUDGE for k in lo)
                       for n, c in flat if n not in skip)

        for a, (i, ea) in enumerate(flat):
            for j, eb in flat[a + 1:]:
                for face, (axis, sign, spans) in FACES.items():
                    if face not in ea["faces"] or face not in eb["faces"]:
                        continue
                    pa = ea["to"][axis] if sign > 0 else ea["from"][axis]
                    pb = eb["to"][axis] if sign > 0 else eb["from"][axis]
                    lo = {k: max(ea["from"][k], eb["from"][k]) for k in spans}
                    hi = {k: min(ea["to"][k], eb["to"][k]) for k in spans}
                    if abs(pa - pb) > 1e-4 or any(hi[k] - lo[k] <= NUDGE for k in spans):
                        continue
                    self.assertTrue(hidden(pa, axis, sign, lo, hi, {i, j}),
                                    f"gun elements {i} and {j} share a visible {face} face plane")

if __name__ == "__main__":
    unittest.main()
