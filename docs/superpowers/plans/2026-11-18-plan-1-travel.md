# Plan 1: Travel Between Survival and Minigames — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Players go from survival to the minigame server with `/minigames` (or the survival NPC) and back with `/survival`, without a second login, and nobody can join the minigame server without a valid signed ticket.

**Architecture:** A plain-Java `travel` module holds the ticket format (HMAC-SHA256-signed cookie), verification, replay protection and a reachability probe; it is bundled into two plugins. `DbsdwBridge` (new, survival, Paper + Folia) issues tickets and transfers players, and on their return hands a verified ticket to AuthMe's public `forceLoginFromProxy` API so the pre-join login dialog is skipped. `MinigameCore` refuses every join without a valid ticket during the configuration phase and sends players back with a ticket.

**Tech Stack:** Java 25, Paper API 26.3 (`26.3.build.255-stable`), Gradle 9.9.1 (Kotlin DSL, version catalog, `build-logic` convention plugins), JUnit 5, AuthMe 6.0.1 API, ViaVersion API 5.12.2, Floodgate API (pinned snapshot), Docker for throwaway test servers, Node.js bots (`minecraft-protocol`) for end-to-end tests.

**Spec:** `docs/superpowers/specs/2026-11-18-minigames-paintball-design.md` (§3, §5, §6.1, §6.2, §6.4, §8, §12, §13 steps 0–1). This is plan 1 of several; lobby, paintball, pack and arena get their own plans after this one ships.

## Global Constraints

- Paper API `io.papermc.paper:paper-api:26.3.build.255-stable`, Java 25, `options.release = 25`.
- Every Java module compiles with `-Xlint:all -Werror`; Gradle runs with `org.gradle.warning.mode=fail` (already in `gradle.properties`). No deprecated API.
- Repositories are declared only in `settings.gradle.kts` (`RepositoriesMode.FAIL_ON_PROJECT_REPOS`); third-party repositories use `exclusiveContent` filtered to their group.
- Cookie key: `dbsdw:ticket`. Ticket lifetime: 60 s. Allowed clock skew: 5 s. Cookie read timeout: 5 s.
- Ticket: HMAC-SHA256 with a 32-byte secret, stored base64 in each plugin's `config.yml` under `ticket-secret`. The secret and ticket bytes are never logged or printed.
- `DbsdwBridge` must run on Paper and Folia: `folia-supported: true`, no `BukkitScheduler`, no `Bukkit.getScheduler()`.
- Player-facing text: Dutch defaults in `messages.yml`, MiniMessage format. Log lines: English.
- The live survival server and the panel are not touched in this plan. All testing is on throwaway Docker servers on this host.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Target server down.** `/minigames` while the minigame server is offline must not disconnect the player from survival: they get "niet beschikbaar" and stay. Same for `/survival` when survival is down. → `ReachabilityTest` (Task 2), e2e check (Task 8).
2. **Secrets differ between the servers** (admin forgot to copy it). Every arrival is refused, and the log must say why in one line: "ticket signature invalid — check that ticket-secret is the same on both servers". → `TicketVerifierTest.otherSecretIsBadSignature` (Task 2), log line in Task 5.
3. **Name case.** AuthMe treats names case-insensitively, but offline UUIDs come from the exact name. A ticket for `Steve` must not admit `steve`. → `TicketVerifierTest.nameCaseMismatchIsWrongPlayer` (Task 2).
4. **Reconnect with an old cookie.** A player disconnects and reconnects within 60 s. The client still holds the used cookie, so it must be refused as reused, and the cookie is cleared after first use. → `TicketVerifierTest.reusedNonceIsRefused` (Task 2), cookie overwrite in Tasks 5 and 6, e2e check (Task 8).
5. **Missing or broken secret in config.** The plugin must not start ticket features, must refuse or decline travel with the "unavailable" message, and must log one clear error. It must not crash, and must not accept unsigned joins. → `TicketSecretsTest` (Task 2), startup behaviour in Tasks 4 and 5, e2e check (Task 8).

---

## File Structure

```
gradle/libs.versions.toml                                   modify: AuthMe, ViaVersion, Floodgate entries
settings.gradle.kts                                         modify: include travel, bridge; exclusiveContent repos
build-logic/src/main/kotlin/dbsdw.java-conventions.gradle.kts   create: Java 25, lint, JUnit (plain Java modules)
build-logic/src/main/kotlin/dbsdw.paper-plugin.gradle.kts       modify: applies java-conventions + Paper + bundling
travel/build.gradle.kts                                     create
travel/src/main/java/be/dbsdw/minigames/travel/
    Destination.java        enum MINIGAMES, SURVIVAL
    Ticket.java             record: the signed facts
    TicketSigner.java       encode/decode + HMAC
    TicketIssuer.java       makes fresh tickets (nonce, times)
    TicketVerifier.java     all arrival checks
    TicketException.java    failure + Reason
    NonceCache.java         replay protection
    TicketSecrets.java      generate/parse the shared secret
    Reachability.java       TCP probe before transferring
    TravelConstants.java    cookie key, TTL, skew, timeouts
travel/src/test/java/be/dbsdw/minigames/travel/            tests for all of the above + MutableClock helper
bridge/build.gradle.kts                                     create
bridge/src/main/resources/{plugin.yml,config.yml,messages.yml}
bridge/src/main/java/be/dbsdw/minigames/bridge/
    BridgePlugin.java       wiring, config, secret check
    Eligibility.java        pure: who may travel
    BridgeTravel.java       ticket + probe + transfer to minigames
    MinigamesCommand.java   /minigames and /minigames npc set|remove
    TicketLoginListener.java  return trip: verified ticket → AuthMe forceLoginFromProxy
    MinigamesNpc.java       survival Mannequin NPC
bridge/src/test/java/be/dbsdw/minigames/bridge/EligibilityTest.java
core/src/main/resources/{plugin.yml (modify),config.yml,messages.yml}
core/src/main/java/be/dbsdw/minigames/core/
    MinigameCorePlugin.java (modify)  wiring, secret generation, shutdown transfer
    travel/ArrivalListener.java       refuses joins without a valid ticket
    travel/CoreTravel.java            ticket + probe + transfer to survival
    travel/SurvivalCommand.java       /survival
tools/e2e/                                                  create: throwaway two-server harness + bots
docs/superpowers/notes/2026-11-18-spike-results.md          create (Task 1)
README.md                                                   modify: bridge, travel, e2e harness
```

---

### Task 1: Spike + end-to-end harness

Confirms the three unknowns from spec §13 step 0 before any product code. The harness under
`tools/e2e/` is kept and reused in Task 8. The spike plugin is throwaway and lives in `/tmp/dbsdw-spike/`, never in the repo.

**Files:**
- Create: `tools/e2e/README.md`, `tools/e2e/docker-compose.yml`, `tools/e2e/fetch-server-jars.sh`, `tools/e2e/bot/package.json`, `tools/e2e/bot/package-lock.json`, `tools/e2e/bot/hop.js`
- Create: `docs/superpowers/notes/2026-11-18-spike-results.md`
- Modify: `.gitignore` (add `tools/e2e/servers/`, `tools/e2e/bot/node_modules/`)

**Interfaces:**
- Produces: `docker compose -f tools/e2e/docker-compose.yml up -d` starts `survival` (host port 25570) and `minigames` (host port 25571), Paper 26.3 build 255, each `online-mode=false`, `accepts-transfers=true`, ViaVersion 5.12.2 + ViaBackwards (so 1.21.11 bots can join), on one Docker network where they reach each other as `survival:25565` and `minigames:25565`. Server folders live in `tools/e2e/servers/{survival,minigames}/`; plugin jars are dropped into their `plugins/`.
- Produces: `node tools/e2e/bot/hop.js --host <h> --port <p> --name <n> [--password <pw>] [--command "<cmd>"] [--decline-pack]` connects one bot, which handles `store_cookie`, `cookie_request` and `transfer` like a real client, follows transfers, submits AuthMe's pre-join login dialog with `--password` when shown, and prints one JSON line per event (`{"event":"dialog_shown"|"pack_offered"|"transferred"|"cookie_stored"|"cookie_requested"|"joined"|"kicked"|"chat", ...}`) so checks can grep the output.

- [ ] **Step 1: Write `fetch-server-jars.sh`**
  Downloads `paper-26.3-255.jar` from `https://fill.papermc.io/v3/projects/paper/versions/26.3/builds/255` and verifies the sha256 the API reports. Downloads ViaVersion 5.12.2, ViaBackwards (latest release compatible with 5.12.2) and `AuthMe-6.0.1-Paper.jar` from the GitHub release `AuthMe/AuthMeReloaded@6.0.1`. Verify each against the release's published digest where one exists, and print the sha256 of each. Exits non-zero on any mismatch.

- [ ] **Step 2: Write `docker-compose.yml`** with the two services above, image `eclipse-temurin:25-jdk`, running as the host user, `-Xmx1G` each, ports bound to `127.0.0.1` only.

- [ ] **Step 3: Write the bot.** Base it on `minecraft-protocol` (pin the version in `package-lock.json`) with protocol version `1.21.11`, following the approach in `~/projects/DBSDW-Folia-Transfer/v1/tools/testbot`.
  - **Cookies:** keep a cookie map per bot. Answer `cookie_request` from it, empty if absent.
  - **Transfer:** reconnect to the given host/port with handshake intent "transfer" and the same cookie map.
  - **Resource pack:** answer `add_resource_pack` with accepted → downloaded → successfully_loaded. With `--decline-pack`, answer declined.

- [ ] **Step 4: Spike plugin in `/tmp/dbsdw-spike/`** (throwaway, compiled against the same Paper API).
  - **`/hop <host> <port>`:** stores cookie `dbsdw:spike` = current epoch millis as UTF-8, then calls `player.transfer(host, port)`.
  - **Arrival listener:** an `AsyncPlayerConnectionConfigureEvent` listener at `EventPriority.LOW` that, if `connection.isTransferred()`:
    1. logs `SPIKE cookie=<value>` from `connection.retrieveCookie(key).get(5, SECONDS)`;
    2. if AuthMe is present, calls `AuthMeApi.getInstance().forceLoginFromProxy(name)`;
    3. if the config option `send-pack: true` is set, sends a required one-file test pack with `connection.getAudience().sendResourcePacks(...)` and blocks until the callback reports a final status, logging `SPIKE pack=<status>`.
  - **Pack hosting:** the test pack is served by `python3 -m http.server` in a third compose service or on the host.

- [ ] **Step 5: Configure AuthMe on `survival` like live.**
  - Pre-join dialog UI on, sessions on, bungeecord hook **off**. Read the live values once, read-only, from the live server's `plugins/AuthMe/config.yml` through the panel's file API. Write down which keys you copied.
  - Register bot `SpikeBot` with password `spike-pass-1` by joining once with `--password`.

- [ ] **Step 6: Run the checks and record each result.**

  | # | Check | Pass when |
  |---|-------|-----------|
  | A | Bot joins `minigames` directly, no cookie | log shows `SPIKE` listener ran with `isTransferred=false`; no cookie read attempted |
  | B | Bot on `survival` runs `/hop minigames 25565` | bot prints `transferred`; `minigames` log shows `SPIKE cookie=<millis>` |
  | C | `send-pack: true` on `minigames`, bot hops in | `SPIKE pack=SUCCESSFULLY_LOADED`, bot `joined` only after the pack status |
  | D | Same as C with `--decline-pack` | `SPIKE pack=DECLINED`; listener can then `connection.disconnect(...)` and bot prints `kicked` |
  | E | Bot hops `survival` → `minigames` → back to `survival` (spike `/hop survival 25565` on minigames) with `forceLoginFromProxy` called | bot prints **no** `dialog_shown` on the return and AuthMe logs the player as logged in |
  | F | Control: same as E but with the `forceLoginFromProxy` call removed, and sessions off | bot prints `dialog_shown` on the return |

- [ ] **Step 7: Write `docs/superpowers/notes/2026-11-18-spike-results.md`** with the table above filled in: result per check, exact log lines, the AuthMe config keys used, and the jar sha256s.

- [ ] **Step 8: Checkpoint.** Report the results to the owner.
  - If A–E pass, continue with Task 2.
  - If E fails, stop. Spec §8.3's fallback (AuthMe sessions) needs the owner's go-ahead, and Task 6 changes.
  - If B, C or D fail, stop and report. The design depends on them.

- [ ] **Step 9: Commit** the harness, notes and `.gitignore` change: `git commit -m "Add the two-server e2e harness and the travel spike results"`.

---

### Task 2: Build split + `travel` module (tickets, replay protection, reachability)

**Files:**
- Create: `build-logic/src/main/kotlin/dbsdw.java-conventions.gradle.kts`
- Modify: `build-logic/src/main/kotlin/dbsdw.paper-plugin.gradle.kts` (move the Java/JUnit/lint parts into java-conventions and apply it)
- Modify: `settings.gradle.kts` (`include("travel", "core", "paintball")`; Task 3 adds `bridge`)
- Create: `travel/build.gradle.kts` (`plugins { id("dbsdw.java-conventions") }`, no other dependencies)
- Create: the `travel` sources and tests listed in File Structure
- Test: `travel/src/test/java/be/dbsdw/minigames/travel/{TicketSignerTest,TicketVerifierTest,NonceCacheTest,TicketSecretsTest,ReachabilityTest,MutableClock}.java`

**Interfaces (Produces; all in package `be.dbsdw.minigames.travel`):**
```java
public enum Destination { MINIGAMES, SURVIVAL }

public record Ticket(UUID playerId, String playerName, Destination destination,
                     Instant issuedAt, Instant expiresAt, String nonceHex) {}
                     // compact constructor: playerName 1..16 chars, nonceHex 32 lowercase hex chars, else IllegalArgumentException

public final class TravelConstants {
    public static final String COOKIE_NAMESPACE = "dbsdw";
    public static final String COOKIE_PATH = "ticket";
    public static final Duration TICKET_TTL = Duration.ofSeconds(60);
    public static final Duration CLOCK_SKEW = Duration.ofSeconds(5);
    public static final Duration COOKIE_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration PROBE_TIMEOUT = Duration.ofSeconds(1);
}

public final class TicketSigner {
    public TicketSigner(byte[] secret);                       // copies; requires 32 bytes
    public byte[] encode(Ticket ticket);
    public Ticket decode(byte[] data) throws TicketException;  // MALFORMED or BAD_SIGNATURE
}

public final class TicketIssuer {
    public TicketIssuer(TicketSigner signer, Clock clock, SecureRandom random);
    public byte[] issue(UUID playerId, String playerName, Destination destination);  // expires = now + TICKET_TTL
}

public final class TicketVerifier {
    public TicketVerifier(TicketSigner signer, Destination self, Clock clock, NonceCache nonces);
    public Ticket verify(byte[] data, UUID playerId, String playerName) throws TicketException;
}

public final class TicketException extends Exception {
    public enum Reason { MISSING, MALFORMED, BAD_SIGNATURE, WRONG_DESTINATION, EXPIRED, NOT_YET_VALID, WRONG_PLAYER, REUSED }
    public TicketException(Reason reason, String detail);
    public Reason reason();
}

public final class NonceCache {
    public NonceCache(Clock clock);
    public synchronized boolean markUsed(String nonceHex, Instant expiresAt);  // false if seen and not yet expired; prunes expired entries
}

public final class TicketSecrets {
    public static byte[] generate(SecureRandom random);        // 32 bytes
    public static String toBase64(byte[] secret);
    public static byte[] fromBase64(String text);              // IllegalArgumentException unless it decodes to exactly 32 bytes
}

public final class Reachability {
    public static boolean isReachable(String host, int port, Duration timeout);  // plain TCP connect, false on any IOException
}
```

**Wire format** (fixed, version 1). Big-endian, then an HMAC-SHA256 over every byte before the MAC:

`version u8 = 1 | destination u8 (MINIGAMES=0, SURVIVAL=1) | uuid msb i64 | uuid lsb i64 | issuedAt epoch-seconds i64 | expiresAt epoch-seconds i64 | nonce 16 bytes | nameLength u8 | name UTF-8 | mac 32 bytes`

`decode` checks:
1. length first, then version, then the MAC with `MessageDigest.isEqual`, then the remaining fields.
2. Any structural problem is `MALFORMED`.
3. A MAC mismatch is `BAD_SIGNATURE`.

**Verification order** in `verify`:
1. null or empty → MISSING
2. decode → MALFORMED / BAD_SIGNATURE
3. destination ≠ self → WRONG_DESTINATION
4. now > expiresAt → EXPIRED
5. issuedAt > now + CLOCK_SKEW → NOT_YET_VALID
6. uuid or exact name differs → WRONG_PLAYER
7. `nonces.markUsed` false → REUSED

`MutableClock` (test helper) is a `Clock` with `set(Instant)` and `advance(Duration)`, zone UTC.

- [ ] **Step 1: Create `dbsdw.java-conventions`** (java-library, group, version, toolchain 25, release 25, `-Xlint:all -Werror`, UTF-8, JUnit BOM + jupiter + launcher from the catalog, `useJUnitPlatform`). Then make `dbsdw.paper-plugin` apply it and keep only the Paper dependency and `plugin.yml` expansion. Run `./gradlew build`; expected BUILD SUCCESSFUL with the existing 5 tests.

- [ ] **Step 2: Write the failing tests.** Fixed clock start `2026-11-18T12:00:00Z`. Secret A = 32 × `0x01`, secret B = 32 × `0x02`. Player `Steve` with `UUID.nameUUIDFromBytes("OfflinePlayer:Steve".getBytes(UTF_8))`.

```java
// TicketSignerTest
@Test void roundTripKeepsAllFields()            // decode(encode(t)) equals t
@Test void flippedPayloadByteIsBadSignature()    // flip byte 20 → Reason.BAD_SIGNATURE
@Test void otherSecretIsBadSignature()           // encode with A, decode with B → BAD_SIGNATURE
@Test void truncatedDataIsMalformed()            // first 10 bytes → MALFORMED
@Test void unknownVersionIsMalformed()           // byte 0 = 2 → MALFORMED (checked before the MAC)
@Test void sixteenCharNameFitsInACookie()        // encode(...).length <= 5120
@Test void seventeenCharNameIsRejected()         // new Ticket(..., "A".repeat(17), ...) → IllegalArgumentException

// TicketVerifierTest (verifier for MINIGAMES with secret A)
@Test void freshTicketIsAccepted()               // returns ticket with playerName "Steve"
@Test void nullAndEmptyAreMissing()              // null → MISSING; new byte[0] → MISSING
@Test void otherSecretIsBadSignature()           // issued with B → BAD_SIGNATURE
@Test void survivalTicketIsWrongDestination()    // issued for SURVIVAL → WRONG_DESTINATION
@Test void ticketIsExpiredAfter60Seconds()       // advance 61 s → EXPIRED; at exactly 60 s → accepted
@Test void futureTicketWithinSkewIsAccepted()    // issuer clock +5 s → accepted; +6 s → NOT_YET_VALID
@Test void otherUuidIsWrongPlayer()
@Test void nameCaseMismatchIsWrongPlayer()       // ticket "Steve", verify as "steve" with the same uuid → WRONG_PLAYER
@Test void reusedNonceIsRefused()                // same bytes verified twice → second throws REUSED

// NonceCacheTest
@Test void firstUseIsTrueSecondIsFalse()
@Test void entryIsForgottenAfterExpiry()         // markUsed(n, now+60s); advance 61 s; markUsed(n, ...) → true

// TicketSecretsTest
@Test void generatedSecretIs32Bytes()
@Test void base64RoundTrip()
@Test void wrongLengthIsRejected()               // base64 of 31 bytes → IllegalArgumentException
@Test void garbageIsRejected()                   // "not base64!!" → IllegalArgumentException
@Test void blankIsRejected()                     // "" → IllegalArgumentException

// ReachabilityTest
@Test void listeningPortIsReachable()            // ServerSocket(0) on 127.0.0.1 → true
@Test void closedPortIsUnreachable()             // bind ServerSocket(0), note port, close it → false within 1 s
```

- [ ] **Step 3: Run** `./gradlew :travel:test`. Expected: compilation fails (classes missing).

- [ ] **Step 4: Implement the classes** with the signatures and wire format above. `TicketIssuer` makes the nonce from 16 bytes of `SecureRandom`, hex-encoded with `HexFormat.of()`. The MAC uses `javax.crypto.Mac.getInstance("HmacSHA256")` with a new `Mac` per call (thread safety).

- [ ] **Step 5: Run** `./gradlew build`. Expected: BUILD SUCCESSFUL, all travel tests pass, no warnings.

- [ ] **Step 6: Commit:** `git commit -m "Add the travel module: signed transfer tickets, replay protection, reachability probe"`.

---

### Task 3: Bundle `travel` into plugin jars + `bridge` module skeleton

**Files:**
- Modify: `build-logic/src/main/kotlin/dbsdw.paper-plugin.gradle.kts` (add a `bundle` configuration)
- Modify: `gradle/libs.versions.toml`, `settings.gradle.kts` (include `bridge`; repos)
- Modify: `core/build.gradle.kts` (`bundle(project(":travel"))`)
- Create: `bridge/build.gradle.kts`, `bridge/src/main/resources/plugin.yml`, `bridge/src/main/java/be/dbsdw/minigames/bridge/BridgePlugin.java` (empty `onEnable` logging the version, like `MinigameCorePlugin`)

**Interfaces:**
- Produces: in any module that applies `dbsdw.paper-plugin`, the `bundle` configuration adds its dependencies to `implementation` and merges their class files into the module's jar. Shading the dependency in also counts as merging.
- Produces (catalog aliases):
  - `authme-core` = `fr.xephi:authme-core:6.0.1`, used `compileOnly` with `isTransitive = false`
  - `viaversion-api` = `com.viaversion:viaversion-api:5.12.2`
  - `floodgate-api` = `org.geysermc.floodgate:api:2.2.5-20260917.145236-21`. That is a timestamped snapshot, which is immutable; there are no releases.
- Produces (`settings.gradle.kts`, `exclusiveContent` each):
  - `https://repo.codemc.io/repository/maven-public/` for group `fr.xephi`
  - `https://repo.viaversion.com/` for group `com.viaversion`
  - `https://repo.opencollab.dev/main/` for groups `org.geysermc.*`, via `includeGroupAndSubgroups("org.geysermc")`
- Produces: `bridge/plugin.yml`:
  - `name: DbsdwBridge`, `main: be.dbsdw.minigames.bridge.BridgePlugin`, `api-version: '26.3'`
  - `folia-supported: true`, `depend: [AuthMe]`, `softdepend: [floodgate, ViaVersion]`
  - jar base name `DbsdwBridge`

- [ ] **Step 1: Make the changes above.** `bridge` gets `bundle(project(":travel"))` and the three compileOnly APIs. Run `./gradlew build --configuration-cache`. Expected: BUILD SUCCESSFUL, no warnings.

- [ ] **Step 2: Verify the bundling.** Run:
  ```bash
  unzip -l core/build/libs/MinigameCore-0.1.0-SNAPSHOT.jar | grep -c 'be/dbsdw/minigames/travel/'
  unzip -l bridge/build/libs/DbsdwBridge-0.1.0-SNAPSHOT.jar | grep -c 'be/dbsdw/minigames/travel/'
  unzip -l bridge/build/libs/DbsdwBridge-0.1.0-SNAPSHOT.jar | grep -cE 'fr/xephi|com/viaversion|org/geysermc'
  ```
  Expected: the first two print the number of travel classes (> 0, equal). The third prints `0`, because the APIs are not bundled.

- [ ] **Step 3: Load check.** Copy `DbsdwBridge` into the `survival` e2e server (which has AuthMe) and `MinigameCore` into `minigames`. Start both. Expected: both logs show `enabled` with no errors. The bridge must also load on Folia, so start a third throwaway container with Folia 26.3 (latest build from `fill.papermc.io`) plus AuthMe-6.0.1-Folia and the bridge, and expect `enabled` there too.

- [ ] **Step 4: Commit:** `git commit -m "Bundle travel into plugin jars and add the DbsdwBridge module"`.

---

### Task 4: Survival → minigames (`/minigames`)

**Files:**
- Create: `bridge/src/main/java/be/dbsdw/minigames/bridge/{Eligibility,BridgeTravel,MinigamesCommand}.java`
- Create: `bridge/src/main/resources/config.yml`, `bridge/src/main/resources/messages.yml`
- Modify: `BridgePlugin.java`
- Test: `bridge/src/test/java/be/dbsdw/minigames/bridge/EligibilityTest.java`

**Interfaces:**
- Consumes: `TicketIssuer`, `TicketSigner`, `TicketSecrets`, `Reachability`, `TravelConstants`, `Destination` (Task 2).
- Produces:
```java
public enum Eligibility {
    OK, NOT_LOGGED_IN, BEDROCK, WRONG_VERSION;
    // first failing rule wins, in this order
    public static Eligibility check(boolean loggedIn, boolean bedrock, int clientProtocol, int serverProtocol);
}
public final class BridgeTravel {
    public void sendToMinigames(Player player);  // used by MinigamesCommand and MinigamesNpc (Task 7)
}
```
- `config.yml`:
  ```yaml
  minigames:
    host: "minigames.dbsdw.com"   # public address players are transferred to
    port: 25565
  ticket-secret: ""               # paste MinigameCore's ticket-secret here
  ```
- `messages.yml` (Dutch defaults):
  ```yaml
  not-logged-in: "<red>Log eerst in voordat je naar de minigames gaat."
  bedrock: "<red>De minigames werken voorlopig alleen op Minecraft Java Edition."
  wrong-version: "<red>De minigames werken alleen met Minecraft 26.3. Jij speelt met een andere versie."
  unavailable: "<red>De minigames zijn nu niet beschikbaar. Probeer het later opnieuw."
  transferring: "<green>Je gaat naar de minigames..."
  ```

- [ ] **Step 1: Write the failing test**
```java
@Test void loggedInJavaPlayerOnServerVersionIsOk()   // check(true, false, 777, 777) == OK
@Test void notLoggedInWinsOverEverything()           // check(false, true, 1, 777) == NOT_LOGGED_IN
@Test void bedrockPlayerIsRefused()                  // check(true, true, 777, 777) == BEDROCK
@Test void otherProtocolIsRefused()                  // check(true, false, 774, 777) == WRONG_VERSION
```

- [ ] **Step 2: Run** `./gradlew :bridge:test`. Expected: FAIL, `Eligibility` missing.

- [ ] **Step 3: Implement `Eligibility.check`.**

- [ ] **Step 4: Run** `./gradlew :bridge:test`. Expected: PASS.

- [ ] **Step 5: Implement the glue.**

  `BridgePlugin.onEnable`:
  1. Save the default `config.yml` and `messages.yml`.
  2. Parse `ticket-secret` with `TicketSecrets.fromBase64`.
  3. On failure, log `ERROR ticket-secret is missing or invalid in plugins/DbsdwBridge/config.yml — travel to minigames is disabled until it is set` and keep running with travel disabled.
  4. Register `/minigames` through Paper's `LifecycleEvents.COMMANDS` as a `BasicCommand`, players only.

  `BridgeTravel.sendToMinigames(player)`:
  1. If travel is disabled, send `unavailable`.
  2. Run the eligibility check:
     - logged in: `AuthMeApi.getInstance().isAuthenticated(player)`
     - Bedrock: `FloodgateApi.getInstance().isFloodgatePlayer(uuid)` when the `floodgate` plugin is enabled, else false
     - protocols: `Via.getAPI().getPlayerProtocolVersion(uuid)` vs `Via.getAPI().getServerVersion().highestSupportedProtocolVersion()` when ViaVersion is enabled, else both equal
  3. Send the matching message for any non-OK result.
  4. Probe `Reachability.isReachable(host, port, PROBE_TIMEOUT)` off the main/region thread, using `Bukkit.getAsyncScheduler()`.
  5. Back on the player's thread (`player.getScheduler().run(...)`):
     - unreachable → `unavailable`
     - reachable → `transferring`, then `player.storeCookie(new NamespacedKey("dbsdw", "ticket"), issuer.issue(uuid, name, MINIGAMES))`, then `player.transfer(host, port)`
  6. Log one line `[travel] <name> → minigames` or `[travel] <name> refused: <REASON>`.

- [ ] **Step 6: Run** `./gradlew build`. Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit:** `git commit -m "DbsdwBridge: /minigames checks eligibility and transfers with a signed ticket"`.

---

### Task 5: Minigame server refuses joins without a ticket; `/survival`; shutdown transfer

**Files:**
- Create: `core/src/main/java/be/dbsdw/minigames/core/travel/{ArrivalListener,CoreTravel,SurvivalCommand}.java`
- Create: `core/src/main/resources/config.yml`, `core/src/main/resources/messages.yml`
- Modify: `core/src/main/java/be/dbsdw/minigames/core/MinigameCorePlugin.java`

**Interfaces:**
- Consumes: Task 2 classes.
- Produces: `public final class CoreTravel { public void sendToSurvival(Player player); public void sendAllToSurvivalForShutdown(); }`. The lobby item in a later plan calls `sendToSurvival`.
- `config.yml`:
  ```yaml
  survival:
    host: "play.dbsdw.com"   # public survival address
    port: 25565
  ticket-secret: ""          # generated on first start; copy it to DbsdwBridge
  ```
- `messages.yml`:
  ```yaml
  no-ticket: "<red>Je kunt hier alleen binnenkomen via de survivalserver.<newline><gray>Ga naar survival en typ <white>/minigames</white>."
  unavailable: "<red>De minigames zijn nu niet beschikbaar. Probeer het later opnieuw."
  survival-unavailable: "<red>Survival is nu niet bereikbaar. Probeer het later opnieuw."
  to-survival: "<green>Je gaat terug naar survival..."
  shutdown: "<yellow>De minigameserver start opnieuw op."
  ```

- [ ] **Step 1: Secret handling in `MinigameCorePlugin.onEnable`.**
  - If `ticket-secret` is blank: generate one with `TicketSecrets.generate(new SecureRandom())`, save it to `config.yml`, and log `WARN Generated a new ticket-secret in plugins/MinigameCore/config.yml. Copy it to plugins/DbsdwBridge/config.yml on the survival server.` (no value printed).
  - If it is present but invalid: log an error and run with joins **refused for everyone** (fail closed: `ArrivalListener` disconnects with `unavailable`).

- [ ] **Step 2: Implement `ArrivalListener`.** `@EventHandler(priority = EventPriority.LOWEST)` on `AsyncPlayerConnectionConfigureEvent`.
  1. Secret invalid → disconnect with `unavailable`.
  2. `!connection.isTransferred()` → disconnect with `no-ticket`, log `[travel] <name> refused: not transferred`.
  3. Otherwise, read `connection.retrieveCookie(key).get(COOKIE_TIMEOUT)`. A timeout or exception counts as `MISSING`.
  4. Then `verifier.verify(bytes, profile.getId(), profile.getName())`.
  5. On success, overwrite the cookie with `connection.storeCookie(key, new byte[0])` and log `[travel] <name> arrived`.
  6. On `TicketException`, disconnect with `no-ticket` and log `[travel] <name> refused: <REASON>`.
     - For `BAD_SIGNATURE` the line is exactly `[travel] <name> refused: ticket signature invalid — check that ticket-secret is the same on both servers`.

- [ ] **Step 3: Implement `CoreTravel.sendToSurvival`** like `BridgeTravel.sendToMinigames`, with no eligibility check: probe, `to-survival`, cookie with a `SURVIVAL` ticket, transfer. Unreachable → `survival-unavailable`, and the player stays.
  - Register `/survival` (players only) through `LifecycleEvents.COMMANDS`.

- [ ] **Step 4: Implement `sendAllToSurvivalForShutdown`**, called first in `onDisable`.
  - For each online player: message `shutdown`, store a `SURVIVAL` ticket, transfer. No probe: the server is going down either way.
  - No delay: packets reach the client in order, so the transfer arrives before Paper's shutdown kick. Task 8 check 9 proves this. If it fails, report it instead of adding a sleep.

- [ ] **Step 5: Run** `./gradlew build`. Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit:** `git commit -m "MinigameCore: refuse joins without a valid ticket, /survival, transfer everyone back on shutdown"`.

---

### Task 6: Return trip without a second login

Depends on Task 1 check E passing. If the owner picked the session fallback instead, replace this task with: verify and clear the ticket, log it, and do nothing else.

**Files:**
- Create: `bridge/src/main/java/be/dbsdw/minigames/bridge/TicketLoginListener.java`
- Modify: `BridgePlugin.java` (register it when travel is enabled)

**Interfaces:**
- Consumes: `TicketVerifier` (Destination `SURVIVAL`), `NonceCache`, `TravelConstants` (Task 2).

- [ ] **Step 1: Implement `TicketLoginListener`.** `@EventHandler(priority = EventPriority.LOW)` on `AsyncPlayerConnectionConfigureEvent`. It must run before AuthMe's `HIGHEST` dialog handler. The survival server never refuses anyone here.
  1. Not transferred → return.
  2. Read the cookie, same as Task 5.
  3. Verify. On failure, log `[travel] <name> ticket not accepted: <REASON>` (the `BAD_SIGNATURE` wording from Task 5) and return. AuthMe then asks for the password as usual.
  4. On success: overwrite the cookie with empty bytes, call `AuthMeApi.getInstance().forceLoginFromProxy(profile.getName())`, and log `[travel] <name> back from minigames, logged in by ticket`.

- [ ] **Step 2: Run** `./gradlew build`. Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit:** `git commit -m "DbsdwBridge: log returning players in with their ticket via AuthMe's forceLoginFromProxy"`.

---

### Task 7: Survival NPC

**Files:**
- Create: `bridge/src/main/java/be/dbsdw/minigames/bridge/MinigamesNpc.java`
- Modify: `MinigamesCommand.java`, `bridge/src/main/resources/plugin.yml` (permission `minigames.admin`, default op), `messages.yml`

**Interfaces:**
- Consumes: `BridgeTravel.sendToMinigames(Player)` (Task 4).
- Produces:
  - `/minigames npc set`: spawns a `Mannequin` at the admin's location, on the admin's thread so it lands in the right Folia region.
    - Invulnerable, no gravity, immovable, persistent, custom name `<gradient:#ff4d4d:#4d79ff><bold>Minigames</bold></gradient>`, always visible.
    - Tagged with PDC key `dbsdw:minigames_npc` (byte 1).
  - `/minigames npc remove`: removes tagged mannequins within 5 blocks.
  - Right-clicking a tagged mannequin (`PlayerInteractEntityEvent`, main hand only) calls `sendToMinigames`.
  - Damage to tagged mannequins is cancelled.
  - New messages:
    ```yaml
    npc-set: "<green>Minigames-NPC geplaatst."
    npc-removed: "<green><count> minigames-NPC('s) verwijderd."
    ```

- [ ] **Step 1: Implement as above.** Run `./gradlew build`. Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Commit:** `git commit -m "DbsdwBridge: minigames NPC on survival"`.

---

### Task 8: End-to-end checks, docs, push

**Files:**
- Modify: `tools/e2e/README.md` (how to run these checks), `README.md` (bridge module, travel, setup steps: copy the secret, `accepts-transfers=true`, ports)
- Create: `docs/superpowers/notes/2026-11-18-plan-1-e2e-results.md`

- [ ] **Step 1: Set up the harness.**
  - Install AuthMe and `DbsdwBridge` on `survival`, and `MinigameCore` on `minigames`.
  - Start `minigames` once to generate the secret, and copy it to the bridge.
  - Point the bridge at host `minigames` port `25565`, and MinigameCore at `survival` port `25565`.
  - Register `E2eBot` (password `e2e-pass-1`) on survival.

- [ ] **Step 2: Run each check with the bot and record the result.**

  | # | Check | Pass when |
  |---|-------|-----------|
  | 1 | `E2eBot` logs in on survival, runs `/minigames` | `transferred`, then `joined` on minigames; minigames log `[travel] E2eBot arrived` |
  | 2 | On minigames, `/survival` | `transferred`, then `joined` on survival with **no** `dialog_shown`; survival log `logged in by ticket` |
  | 3 | Bot joins minigames directly | `kicked` with the no-ticket text; log `refused: not transferred` |
  | 4 | Bot not logged in on survival runs `/minigames` | chat shows `not-logged-in`, bot stays on survival |
  | 5 | `docker compose stop minigames`, then `/minigames` | chat shows `unavailable`, bot stays on survival (Review Focus 1) |
  | 6 | Change one character of the bridge's secret, restart, `/minigames` | `kicked` on minigames; minigames log has the exact `ticket signature invalid — check that ticket-secret is the same on both servers` line (Review Focus 2) |
  | 7 | Bot replays: after check 1, disconnect, then connect to minigames with the transfer intent and the old cookie map | `kicked`; log `refused: MISSING` (cookie was cleared). With the cookie clearing disabled in a throwaway build, the log shows `refused: REUSED` (Review Focus 4) |
  | 8 | Blank `ticket-secret` on the bridge, restart, `/minigames` | `unavailable`; bridge log has the one-line error; plugin still enabled (Review Focus 5) |
  | 9 | Bot on minigames, `docker compose stop minigames` | bot `transferred` to survival and `joined` |
  | 10 | Bot name case: register `CaseBot`, transfer, then forge a reconnect as `casebot` with that cookie | `kicked`; log `refused: WRONG_PLAYER` (Review Focus 3) |
  | 11 | NPC: admin runs `/minigames npc set` (op the bot), a second bot right-clicks it | second bot `transferred` |

- [ ] **Step 3: Write the results note.** Fix any failure in the owning task's code, re-run its check, and note the fix.

- [ ] **Step 4: Update the READMEs, commit, push.** Run `git push origin main`, then watch CI with `gh run watch`. Expected: all jobs green.

- [ ] **Step 5: Tear down.** `docker compose down`, remove `tools/e2e/servers/`, and remove the spike folder `/tmp/dbsdw-spike/`.

- [ ] **Step 6: Report to the owner.**
  - The results table.
  - What live needs, all waiting for the owner's OK:
    - install DbsdwBridge
    - paste the secret
    - set `accepts-transfers=true`
    - restart
  - Plan 2 (lobby, NPC, queue, teams, arenas, match lifecycle) is next.
