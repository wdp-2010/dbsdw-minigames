"""Every pack id the plugins use must exist in the resource pack.

The plugins keep their pack references in PackAssets.java (one per module) and in messages.yml
(fonts and private-use glyphs). A missing id would show a purple-black item, play no sound or
draw a box, so this fails the build instead. Run: python3 -m unittest discover -s tools
"""

from __future__ import annotations

import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PACK = ROOT / "resourcepack" / "assets" / "dbsdw"
ID = re.compile(r"dbsdw:[a-z0-9_./]+")
PRIVATE_USE = re.compile("[-]")
UNICODE_ESCAPE = re.compile(r"\\u([0-9a-fA-F]{4})")


def sources() -> list[Path]:
    files = sorted(ROOT.glob("*/src/main/java/**/PackAssets.java"))
    files += sorted(ROOT.glob("*/src/main/resources/messages.yml"))
    return files


def pack_ids() -> set[str]:
    ids = {f"dbsdw:{p.stem}" for p in (PACK / "items").glob("*.json")}
    ids |= {f"dbsdw:{p.stem}" for p in (PACK / "font").glob("*.json")}
    sounds = PACK / "sounds.json"
    if sounds.exists():
        ids |= {f"dbsdw:{event}" for event in json.loads(sounds.read_text(encoding="utf-8"))}
    return ids


def font_chars() -> set[str]:
    chars: set[str] = set()
    for font in (PACK / "font").glob("*.json"):
        for provider in json.loads(font.read_text(encoding="utf-8"))["providers"]:
            for row in provider.get("chars", []):
                chars.update(row)
            chars.update(provider.get("advances", {}))
    return chars


def text_of(path: Path) -> str:
    """File text with Java/YAML \\uXXXX escapes turned into the characters they stand for."""
    text = path.read_text(encoding="utf-8")
    return UNICODE_ESCAPE.sub(lambda m: chr(int(m.group(1), 16)), text)


class PackKeysTest(unittest.TestCase):
    def test_every_key_used_in_code_exists(self) -> None:
        known = pack_ids()
        missing = [f"{f.relative_to(ROOT)}: {key}" for f in sources() for key in set(ID.findall(text_of(f)))
                   if key not in known]
        self.assertEqual(missing, [], "pack ids used by the plugins but missing from the pack")

    def test_every_private_use_char_in_messages_has_a_glyph(self) -> None:
        chars = font_chars()
        missing = [f"{f.relative_to(ROOT)}: U+{ord(ch):04X}" for f in sources() for ch in sorted(set(PRIVATE_USE.findall(text_of(f))))
                   if ch not in chars]
        self.assertEqual(missing, [], "glyphs used by the plugins but missing from the pack fonts")


if __name__ == "__main__":
    unittest.main()
