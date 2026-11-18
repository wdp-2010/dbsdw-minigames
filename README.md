# DBSDW Minigames

Minigame server plugins and resource pack for the DBSDW Minecraft community.
Runs on Paper 26.3 (Java 25).

| Path | What it is |
|------|------------|
| `travel/` | Plain-Java transfer tickets (HMAC-signed cookie, single use, 60 s), shared by both plugins below |
| `core/` | **MinigameCore** plugin (minigame server): refuses joins without a valid ticket, `/survival`; later: lobby, queue, teams, arenas, stats, resource pack |
| `bridge/` | **DbsdwBridge** plugin (survival server, Paper and Folia): `/minigames`, the minigames NPC, ticket login on return via AuthMe |
| `paintball/` | **Paintball** plugin: team deathmatch and capture the flag, built on MinigameCore |
| `resourcepack/` | The required resource pack (models, textures, sounds, HUD) |
| `tools/` | Resource pack validation and build script; `tools/e2e/` throwaway test servers and bots |
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

## Travel setup (survival ↔ minigames)

1. Start the minigame server once: MinigameCore writes a new `ticket-secret` to
   `plugins/MinigameCore/config.yml`. Copy that value to `plugins/DbsdwBridge/config.yml` on
   survival. Never share it.
2. Set `minigames.host`/`port` in the bridge config and `survival.host`/`port` in the core config
   to the public addresses.
3. Set `accepts-transfers=true` in `server.properties` on **both** servers.
4. Admins place the survival NPC with `/minigames npc set` (`/minigames npc remove` within 5 blocks).

End-to-end checks: `tools/e2e/README.md`.
