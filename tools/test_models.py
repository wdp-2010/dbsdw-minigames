"""Generated 3D models: no inside-out boxes, and the gun on the pixel grid.

Every corner of the gun sits on the 1/4-unit grid (one texture pixel at 4 pixels per unit, give or
take the 0.01 nudges that keep crossed boxes apart), so no face shows a stretched or cut pixel.
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


class ModelTest(unittest.TestCase):
    def test_no_inside_out_boxes(self):
        for path in MODELS.glob("*.json"):
            for i, el in enumerate(json.loads(path.read_text(encoding="utf-8")).get("elements", [])):
                for k in range(3):
                    self.assertLessEqual(el["from"][k], el["to"][k], f"{path.name} element {i}")

    def test_gun_on_pixel_grid(self):
        gun = json.loads((MODELS / "gun.json").read_text(encoding="utf-8"))
        for i, el in enumerate(gun["elements"]):
            for v in el["from"] + el["to"]:
                off = abs(v / GRID - round(v / GRID)) * GRID
                self.assertLessEqual(off, NUDGE, f"gun element {i}: {el['from']} -> {el['to']}")


if __name__ == "__main__":
    unittest.main()
