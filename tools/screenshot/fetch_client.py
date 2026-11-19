#!/usr/bin/env python3
"""Download the vanilla Minecraft client and prepare an offline launch for screenshots.

Usage:
  fetch_client.py fetch   <cache>                          download client, libraries, assets
  fetch_client.py prepare <cache> <game> <name> <host:port> fetch, then write options.txt,
                                                           servers.dat and launch.sh in <game>

Every download is checked against the SHA1 in Mojang's metadata; files already in the cache with
the right SHA1 are not downloaded again. Uses only the Python standard library.
"""

from __future__ import annotations

import hashlib
import json
import os
import struct
import sys
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

VERSION = "26.3"
MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
RESOURCES = "https://resources.download.minecraft.net"


def sha1(path: Path) -> str:
    h = hashlib.sha1()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def download(url: str, dest: Path, want_sha1: str | None) -> Path:
    if dest.exists() and (want_sha1 is None or sha1(dest) == want_sha1):
        return dest
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(dest.suffix + ".part")
    with urllib.request.urlopen(url, timeout=60) as r, tmp.open("wb") as f:
        while chunk := r.read(1 << 20):
            f.write(chunk)
    if want_sha1 is not None and sha1(tmp) != want_sha1:
        tmp.unlink()
        raise SystemExit(f"SHA1 mismatch for {url}")
    tmp.replace(dest)
    return dest


def allowed(lib: dict) -> bool:
    """Apply the launcher's os rules for 64-bit x86 Linux."""
    rules = lib.get("rules")
    if not rules:
        return "aarch_64" not in lib["name"]
    ok = False
    for rule in rules:
        os_rule = rule.get("os")
        if os_rule is None or os_rule.get("name") == "linux":
            ok = rule["action"] == "allow"
    return ok and "aarch_64" not in lib["name"] and "arm64" not in lib["name"]


def fetch(cache: Path) -> dict:
    manifest = json.load(urllib.request.urlopen(MANIFEST, timeout=60))
    entry = next(v for v in manifest["versions"] if v["id"] == VERSION)
    version_file = download(entry["url"], cache / "versions" / f"{VERSION}.json", entry["sha1"])
    version = json.loads(version_file.read_text())

    jobs: list[tuple[str, Path, str]] = []
    client = version["downloads"]["client"]
    jobs.append((client["url"], cache / "versions" / f"{VERSION}.jar", client["sha1"]))
    classpath = []
    for lib in version["libraries"]:
        if not allowed(lib):
            continue
        art = lib["downloads"]["artifact"]
        dest = cache / "libraries" / art["path"]
        jobs.append((art["url"], dest, art["sha1"]))
        classpath.append(dest)
    classpath.append(cache / "versions" / f"{VERSION}.jar")

    index = version["assetIndex"]
    index_file = download(index["url"], cache / "assets" / "indexes" / f"{index['id']}.json", index["sha1"])
    for obj in json.loads(index_file.read_text())["objects"].values():
        h = obj["hash"]
        jobs.append((f"{RESOURCES}/{h[:2]}/{h}", cache / "assets" / "objects" / h[:2] / h, h))

    with ThreadPoolExecutor(16) as pool:
        list(pool.map(lambda j: download(*j), jobs))
    return {"version": version, "classpath": classpath, "asset_index": index["id"]}


def offline_uuid(name: str) -> uuid.UUID:
    """Same as Java's UUID.nameUUIDFromBytes("OfflinePlayer:" + name)."""
    digest = bytearray(hashlib.md5(f"OfflinePlayer:{name}".encode()).digest())
    digest[6] = (digest[6] & 0x0F) | 0x30
    digest[8] = (digest[8] & 0x3F) | 0x80
    return uuid.UUID(bytes=bytes(digest))


def _nbt_string(s: str) -> bytes:
    data = s.encode("utf-8")
    return struct.pack(">H", len(data)) + data


def servers_dat(target: str) -> bytes:
    """Uncompressed NBT: {servers: [{name, ip, acceptTextures: 1b}]}, so the pack is accepted."""
    server = (
        b"\x08" + _nbt_string("name") + _nbt_string("DBSDW test")
        + b"\x08" + _nbt_string("ip") + _nbt_string(target)
        + b"\x01" + _nbt_string("acceptTextures") + b"\x01"
        + b"\x00"
    )
    servers = b"\x09" + _nbt_string("servers") + b"\x0a" + struct.pack(">i", 1) + server
    return b"\x0a" + _nbt_string("") + servers + b"\x00"


OPTIONS = """version:4700
guiScale:2
renderDistance:6
simulationDistance:5
maxFps:30
pauseOnLostFocus:false
onboardAccessibility:false
skipMultiplayerWarning:true
joinedFirstServer:true
tutorialStep:none
narrator:0
soundCategory_master:0.0
soundCategory_music:0.0
fov:0.0
lang:en_us
"""


def prepare(cache: Path, game: Path, name: str, target: str) -> None:
    info = fetch(cache)
    game.mkdir(parents=True, exist_ok=True)
    options = game / "options.txt"
    if not options.exists():
        options.write_text(OPTIONS)
    (game / "servers.dat").write_bytes(servers_dat(target))
    natives = game / "natives"
    cp = ":".join(str(p) for p in info["classpath"])
    args = [
        "java", "-Xms512m", "-Xmx1536m",
        "-XX:StackShadowPages=32", "--enable-native-access=ALL-UNNAMED",
        "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED",
        f"-Djava.library.path={natives}/java", f"-Djna.tmpdir={natives}/jna",
        f"-Dorg.lwjgl.system.SharedLibraryExtractPath={natives}/lwjgl",
        f"-Dio.netty.native.workdir={natives}/netty",
        "-cp", cp, info["version"]["mainClass"],
        "--username", name, "--uuid", str(offline_uuid(name)).replace("-", ""),
        "--accessToken", "0", "--userType", "legacy", "--version", VERSION,
        "--gameDir", str(game), "--assetsDir", str(cache / "assets"),
        "--assetIndex", info["asset_index"], "--width", "1280", "--height", "720",
        "--quickPlayMultiplayer", target,
    ]
    script = "#!/bin/sh\ncd " + str(game) + "\nexec " + " ".join(f"'{a}'" for a in args) + "\n"
    (game / "launch.sh").write_text(script)
    os.chmod(game / "launch.sh", 0o755)


def main(argv: list[str]) -> None:
    if len(argv) == 3 and argv[1] == "fetch":
        fetch(Path(argv[2]))
    elif len(argv) == 6 and argv[1] == "prepare":
        prepare(Path(argv[2]), Path(argv[3]), argv[4], argv[5])
    else:
        raise SystemExit(__doc__)


if __name__ == "__main__":
    main(sys.argv)
