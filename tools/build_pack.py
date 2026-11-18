#!/usr/bin/env python3
"""Validate the DBSDW Minigames resource pack and build a reproducible zip.

Usage: python3 tools/build_pack.py [--src resourcepack] [--out dist]

Checks (any failure exits non-zero and nothing is written):
  * pack.mcmeta exists, has pack.description, and its min_format/max_format
    range covers TARGET_FORMAT (the format of the Minecraft version we run).
  * Every .json and .mcmeta file is valid JSON with no duplicate keys.
  * Every path under assets/ is a valid resource location (lowercase a-z, 0-9,
    '_', '-', '.', '/'), so the client does not silently ignore it.
  * Every .png file has a PNG signature.
  * No stray hidden files (.gitkeep placeholders are skipped).
  * The finished zip is under the client's download limit.

Output: <out>/dbsdw-minigames-pack.zip and <out>/dbsdw-minigames-pack.zip.sha1.
The zip is byte-for-byte reproducible: entries are sorted and carry a fixed
timestamp and permissions, so the SHA1 only changes when the content does.
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
import sys
import zipfile
from pathlib import Path

# Minecraft 26.3 resource pack format. Update together with the server version.
TARGET_FORMAT = (97, 1)
PACK_NAME = "dbsdw-minigames-pack.zip"
# The vanilla client refuses server resource packs larger than 250 MiB.
MAX_ZIP_BYTES = 250 * 1024 * 1024
FIXED_TIMESTAMP = (1980, 1, 1, 0, 0, 0)
RESOURCE_PATH = re.compile(r"^[a-z0-9_.\-/]+$")
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
IGNORED_NAMES = {".gitkeep"}
# Only these top-level entries go into the zip.
TOP_LEVEL = {"pack.mcmeta", "pack.png", "assets"}
# Files that live next to the pack in the repo but are not shipped to players.
SOURCE_ONLY = {"CREDITS.md"}


class PackError(Exception):
    """A problem that makes the pack invalid."""


def _no_duplicate_keys(pairs: list[tuple[str, object]]) -> dict[str, object]:
    result: dict[str, object] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate key {key!r}")
        result[key] = value
    return result


def load_json(path: Path) -> object:
    try:
        return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=_no_duplicate_keys)
    except (ValueError, UnicodeDecodeError) as e:
        raise PackError(f"{path}: invalid JSON: {e}") from e


def parse_format(value: object, field: str, is_max: bool) -> tuple[int, int]:
    """Parse a min_format/max_format value: 97, [97] or [97, 1]."""
    if isinstance(value, bool):
        raise PackError(f"pack.mcmeta: {field} must be an int or [major, minor]")
    if isinstance(value, int):
        value = [value]
    if (
        not isinstance(value, list)
        or not 1 <= len(value) <= 2
        or not all(isinstance(v, int) and not isinstance(v, bool) for v in value)
    ):
        raise PackError(f"pack.mcmeta: {field} must be an int or [major, minor]")
    if len(value) == 2:
        return (value[0], value[1])
    # A bare major version means minor 0 as a minimum and any minor as a maximum.
    return (value[0], 0x7FFFFFFF if is_max else 0)


def check_mcmeta(src: Path) -> None:
    mcmeta = src / "pack.mcmeta"
    if not mcmeta.is_file():
        raise PackError(f"{mcmeta}: missing")
    data = load_json(mcmeta)
    pack = data.get("pack") if isinstance(data, dict) else None
    if not isinstance(pack, dict):
        raise PackError("pack.mcmeta: missing 'pack' object")
    if "description" not in pack:
        raise PackError("pack.mcmeta: missing pack.description")
    for field in ("min_format", "max_format"):
        if field not in pack:
            raise PackError(f"pack.mcmeta: missing pack.{field}")
    low = parse_format(pack["min_format"], "min_format", is_max=False)
    high = parse_format(pack["max_format"], "max_format", is_max=True)
    if low > high:
        raise PackError(f"pack.mcmeta: min_format {low} is above max_format {high}")
    if not low <= TARGET_FORMAT <= high:
        raise PackError(
            f"pack.mcmeta: format range {low}..{high} does not cover the server's {TARGET_FORMAT}"
        )


def collect_files(src: Path) -> list[Path]:
    files: list[Path] = []
    for path in sorted(src.rglob("*")):
        if not path.is_file() or path.name in IGNORED_NAMES:
            continue
        rel = path.relative_to(src)
        if rel.as_posix() in SOURCE_ONLY:
            continue
        if any(part.startswith(".") for part in rel.parts):
            raise PackError(f"{rel.as_posix()}: hidden file, remove it")
        if rel.parts[0] not in TOP_LEVEL:
            raise PackError(f"{rel.as_posix()}: not part of a resource pack (allowed: {sorted(TOP_LEVEL)})")
        files.append(path)
    return files


def check_file(src: Path, path: Path) -> None:
    rel = path.relative_to(src).as_posix()
    if rel.startswith("assets/") and not RESOURCE_PATH.match(rel):
        raise PackError(f"{rel}: invalid resource location (use lowercase a-z, 0-9, _ - . /)")
    if path.suffix in (".json", ".mcmeta"):
        load_json(path)
    elif path.suffix == ".png" and path.read_bytes()[:8] != PNG_SIGNATURE:
        raise PackError(f"{rel}: not a PNG file")


def build_zip(src: Path, files: list[Path]) -> bytes:
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        for path in files:
            info = zipfile.ZipInfo(path.relative_to(src).as_posix(), date_time=FIXED_TIMESTAMP)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            info.create_system = 3  # Unix, so the permissions above are the same on every OS
            zf.writestr(info, path.read_bytes(), compresslevel=9)
    data = buffer.getvalue()
    if len(data) > MAX_ZIP_BYTES:
        raise PackError(f"pack is {len(data)} bytes, over the client limit of {MAX_ZIP_BYTES}")
    return data


def build(src: Path, out: Path) -> str:
    """Validate src, write the zip and .sha1 to out, and return the SHA1."""
    if not src.is_dir():
        raise PackError(f"{src}: not a directory")
    check_mcmeta(src)
    files = collect_files(src)
    for path in files:
        check_file(src, path)
    data = build_zip(src, files)
    sha1 = hashlib.sha1(data).hexdigest()
    out.mkdir(parents=True, exist_ok=True)
    (out / PACK_NAME).write_bytes(data)
    (out / f"{PACK_NAME}.sha1").write_text(f"{sha1}  {PACK_NAME}\n", encoding="utf-8")
    return sha1


def main(argv: list[str] | None = None) -> int:
    root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--src", type=Path, default=root / "resourcepack")
    parser.add_argument("--out", type=Path, default=root / "dist")
    args = parser.parse_args(argv)
    try:
        sha1 = build(args.src, args.out)
    except PackError as e:
        print(f"error: {e}", file=sys.stderr)
        return 1
    print(f"built {args.out / PACK_NAME}")
    print(f"sha1  {sha1}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
