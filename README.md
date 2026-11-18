# DBSDW Minigames

Minigame server plugins and resource pack for the DBSDW Minecraft community.
Runs on Paper 26.3 (Java 25).

| Path | What it is |
|------|------------|
| `core/` | **MinigameCore** plugin: lobby, queue, teams, arenas, stats and resource pack delivery, shared by every game |
| `paintball/` | **Paintball** plugin: team deathmatch and capture the flag, built on MinigameCore |
| `resourcepack/` | The required resource pack (models, textures, sounds, HUD) |
| `tools/` | Resource pack validation and build script |
| `build-logic/` | Shared Gradle convention plugin (`dbsdw.paper-plugin`) for every plugin module |
| `gradle/libs.versions.toml` | All dependency versions in one place |

## Build

Requires JDK 25.

```sh
./gradlew build
```

Jars land in `core/build/libs/` and `paintball/build/libs/`.

## Resource pack

Requires Python 3.10+, no extra packages.

```sh
python3 tools/build_pack.py              # validate + build dist/dbsdw-minigames-pack.zip and .sha1
python3 -m unittest discover -s tools    # tests for the build script
```

The script rejects invalid JSON, duplicate JSON keys, invalid resource paths, fake PNGs,
stray files and a `pack.mcmeta` that does not cover the server's pack format (97.1).
The zip is reproducible: the same content always gives the same SHA1.

## CI

Every push and pull request runs the Gradle build with unit tests and the resource pack
build. Jars and the pack are uploaded as workflow artifacts.
