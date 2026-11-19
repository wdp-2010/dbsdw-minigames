"""Unit tests for build_pack.py. Run: python3 -m unittest discover -s tools"""

from __future__ import annotations

import json
import tempfile
import unittest
import zipfile
from pathlib import Path

import build_pack

VALID_MCMETA = {"pack": {"description": "test", "min_format": [97, 1], "max_format": [97, 1]}}
PNG = build_pack.PNG_SIGNATURE + b"rest-of-image"


class BuildPackTest(unittest.TestCase):
    def setUp(self) -> None:
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.src = Path(tmp.name) / "pack"
        self.out = Path(tmp.name) / "dist"
        self.write("pack.mcmeta", json.dumps(VALID_MCMETA))
        self.write("assets/dbsdw/models/item/gun.json", '{"parent": "item/generated"}')
        self.write("assets/dbsdw/textures/item/gun.png", PNG)
        self.write("assets/dbsdw/.gitkeep", "")

    def write(self, rel: str, content: str | bytes) -> None:
        path = self.src / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        if isinstance(content, bytes):
            path.write_bytes(content)
        else:
            path.write_text(content, encoding="utf-8")

    def assert_fails(self, message: str) -> None:
        with self.assertRaises(build_pack.PackError) as ctx:
            build_pack.build(self.src, self.out)
        self.assertIn(message, str(ctx.exception))
        self.assertFalse((self.out / build_pack.PACK_NAME).exists(), "no zip on failure")

    def test_valid_pack_builds_with_sha1_file(self) -> None:
        sha1 = build_pack.build(self.src, self.out)
        sha1_line = (self.out / f"{build_pack.PACK_NAME}.sha1").read_text(encoding="utf-8")
        self.assertEqual(sha1_line, f"{sha1}  {build_pack.PACK_NAME}\n")
        with zipfile.ZipFile(self.out / build_pack.PACK_NAME) as zf:
            self.assertEqual(
                zf.namelist(),
                [
                    "assets/dbsdw/models/item/gun.json",
                    "assets/dbsdw/textures/item/gun.png",
                    "pack.mcmeta",
                ],
            )

    def test_build_is_reproducible(self) -> None:
        first = build_pack.build(self.src, self.out)
        # Touching mtimes must not change the result.
        for path in self.src.rglob("*"):
            path.touch()
        self.assertEqual(first, build_pack.build(self.src, self.out))

    def test_invalid_json_fails(self) -> None:
        self.write("assets/dbsdw/models/item/gun.json", '{"parent": ')
        self.assert_fails("invalid JSON")

    def test_duplicate_key_fails(self) -> None:
        self.write("assets/dbsdw/models/item/gun.json", '{"a": 1, "a": 2}')
        self.assert_fails("duplicate key")

    def test_uppercase_path_fails(self) -> None:
        self.write("assets/dbsdw/textures/item/Gun.png", PNG)
        self.assert_fails("invalid resource location")

    def test_fake_png_fails(self) -> None:
        self.write("assets/dbsdw/textures/item/gun.png", b"not a png")
        self.assert_fails("not a PNG")

    def test_hidden_file_fails(self) -> None:
        self.write("assets/dbsdw/.DS_Store", b"junk")
        self.assert_fails("hidden file")

    def test_ogg_without_signature_fails(self) -> None:
        self.write("assets/dbsdw/sounds/shot.ogg", b"RIFF-not-ogg")
        self.assert_fails("assets/dbsdw/sounds/shot.ogg: not an Ogg file")

    def test_model_texture_missing_fails(self) -> None:
        self.write("assets/dbsdw/models/item/gun.json",
                   '{"parent": "item/generated", "textures": {"layer0": "dbsdw:item/nope"}}')
        self.assert_fails("assets/dbsdw/textures/item/nope.png")

    def test_model_parent_missing_fails(self) -> None:
        self.write("assets/dbsdw/models/item/gun.json", '{"parent": "dbsdw:item/base"}')
        self.assert_fails("assets/dbsdw/models/item/base.json")

    def test_item_definition_model_missing_fails(self) -> None:
        self.write("assets/dbsdw/items/gun.json", json.dumps({"model": {
            "type": "minecraft:range_dispatch", "property": "minecraft:custom_model_data",
            "entries": [{"threshold": 1, "model": {"type": "minecraft:model", "model": "dbsdw:item/gone"}}],
            "fallback": {"type": "minecraft:model", "model": "dbsdw:item/gun"}}}))
        self.assert_fails("assets/dbsdw/models/item/gone.json")

    def test_sound_file_missing_fails(self) -> None:
        self.write("assets/dbsdw/sounds.json", '{"paintball.shot": {"sounds": ["dbsdw:paintball/shot1"]}}')
        self.assert_fails("assets/dbsdw/sounds/paintball/shot1.ogg")

    def test_font_texture_missing_fails(self) -> None:
        self.write("assets/dbsdw/font/hud.json", json.dumps({"providers": [
            {"type": "bitmap", "file": "dbsdw:font/drop.png", "ascent": 8, "height": 9, "chars": ["\ue000"]}]}))
        self.assert_fails("assets/dbsdw/textures/font/drop.png")

    def test_complete_references_build(self) -> None:
        self.write("assets/dbsdw/models/item/gun.json",
                   '{"parent": "minecraft:item/generated", "textures": {"layer0": "dbsdw:item/gun"}}')
        self.write("assets/dbsdw/items/gun.json",
                   '{"model": {"type": "minecraft:model", "model": "dbsdw:item/gun"}}')
        self.write("assets/dbsdw/sounds/paintball/shot1.ogg", b"OggS-rest")
        self.write("assets/dbsdw/sounds.json", '{"paintball.shot": {"sounds": [{"name": "dbsdw:paintball/shot1"}]}}')
        build_pack.build(self.src, self.out)

    def test_credits_file_stays_out_of_the_zip(self) -> None:
        self.write("CREDITS.md", "# Credits")
        build_pack.build(self.src, self.out)
        with zipfile.ZipFile(self.out / build_pack.PACK_NAME) as zf:
            self.assertNotIn("CREDITS.md", zf.namelist())

    def test_unknown_top_level_file_fails(self) -> None:
        self.write("notes.txt", "hello")
        self.assert_fails("not part of a resource pack")

    def test_missing_description_fails(self) -> None:
        self.write("pack.mcmeta", json.dumps({"pack": {"min_format": 97, "max_format": 97}}))
        self.assert_fails("pack.description")

    def test_format_not_covering_target_fails(self) -> None:
        mcmeta = {"pack": {"description": "x", "min_format": [96, 0], "max_format": [96, 0]}}
        self.write("pack.mcmeta", json.dumps(mcmeta))
        self.assert_fails("does not cover")

    def test_bare_major_max_format_covers_any_minor(self) -> None:
        mcmeta = {"pack": {"description": "x", "min_format": 97, "max_format": 97}}
        self.write("pack.mcmeta", json.dumps(mcmeta))
        build_pack.build(self.src, self.out)

    def test_min_above_max_fails(self) -> None:
        mcmeta = {"pack": {"description": "x", "min_format": [98, 0], "max_format": [97, 1]}}
        self.write("pack.mcmeta", json.dumps(mcmeta))
        self.assert_fails("above max_format")

    def test_bad_format_type_fails(self) -> None:
        mcmeta = {"pack": {"description": "x", "min_format": "97.1", "max_format": [97, 1]}}
        self.write("pack.mcmeta", json.dumps(mcmeta))
        self.assert_fails("must be an int")


if __name__ == "__main__":
    unittest.main()
