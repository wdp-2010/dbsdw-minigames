"""Every message key the plugins ask for by literal name must exist in that module's messages.yml.

A missing key shows its raw name in game (for example "hud.streak" as a subtitle). Keys built at
runtime ("powerup." + type, "match.voice-" + rule) are not checked. Run: python3 -m unittest discover -s tools
"""

from __future__ import annotations

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CALL = re.compile(r'messages\.(?:get|raw)\(\s*"([a-z0-9_.\-]+)"')
TERNARY = re.compile(r'messages\.get\([^;]*?\?\s*"([a-z0-9_.\-]+)"\s*:\s*"([a-z0-9_.\-]+)"')
FLAG_EVENT = re.compile(r'flagEvent\(\s*"([a-z0-9_.\-]+)"')


def yaml_keys(path: Path) -> set[str]:
    keys, stack = set(), []
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        m = re.match(r'^( *)("?[^":]+"?):', line)
        if not m:
            continue
        depth = len(m.group(1)) // 2
        stack[depth:] = [m.group(2).strip('"')]
        keys.add(".".join(stack))
    return keys


class MessageKeysTest(unittest.TestCase):
    def test_literal_keys_exist(self):
        for module in ("core", "paintball", "bridge", "valvloer", "verstoppertje", "chess"):
            keys = yaml_keys(ROOT / module / "src/main/resources/messages.yml")
            for java in (ROOT / module / "src/main/java").rglob("*.java"):
                text = java.read_text(encoding="utf-8")
                used = set(CALL.findall(text))
                for a, b in TERNARY.findall(text):
                    used |= {a, b}
                used |= set(FLAG_EVENT.findall(text))
                for key in sorted(k for k in used if not k.endswith((".", "-"))):
                    self.assertIn(key, keys, f"{java.relative_to(ROOT)} uses {key}, missing in {module}/messages.yml")


if __name__ == "__main__":
    unittest.main()
