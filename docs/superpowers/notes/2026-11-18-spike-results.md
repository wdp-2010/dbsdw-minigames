# Spike results: travel between survival and minigames (2026-11-18)

Spec §13 step 0. Throwaway servers on node1 (`/opt/dbsdw-e2e`, Docker, 1.5 GB cap each), the
`tools/e2e` harness, a throwaway spike plugin (`/hop <host> <port>` stores cookie `dbsdw:spike`
and transfers; an `AsyncPlayerConnectionConfigureEvent` listener at `LOW` reads it, optionally
calls AuthMe's `forceLoginFromProxy`, optionally sends a required pack) and the `hop.js` bot
(minecraft-protocol 1.68.0, protocol 26.1 through ViaBackwards 5.12.2).

## Versions (sha256 checked by `tools/e2e/fetch-server-jars.sh`)

| Jar | sha256 |
|-----|--------|
| paper-26.3-255.jar | a7116085a8ba88cf00771d5e61a2925007942092650811da7327dc23243f0cec |
| ViaVersion-5.12.2.jar | 24e4876fefe73e08a4c87cd6c457ee0916e62dd69656631caf41e26f909d504b |
| ViaBackwards-5.12.2.jar | 9a148a80ea22074f4bd1626cdbe254a4cd998bf44e7ceaff4e285a1b6b35da42 |
| AuthMe-6.0.1-Paper.jar | 7704335e9e73a634d9d926344f77897f4c74f78f82453a59f5aa0f8d2722450a |

## AuthMe settings

Read from live (`plugins/AuthMe/config.yml` on server `b69a7f63`, read-only, password lines filtered):
`settings.registration.dialog.preJoin.enable: true`, `allowCloseWithEscape: false`,
`registerCancelKicks: false`, `loginCancelKicks: true`, `settings.sessions.enabled: true`,
`timeout: 10000`, `Hooks.bungeecord: false`, `registration.force: true`. Live `server.properties`:
`online-mode=false`, `accepts-transfers=false`.

The spike used the same values except `sessions.enabled: false`, so a session could not hide the
effect of the ticket login.

## Results

| # | Check | Result | Evidence |
|---|-------|--------|----------|
| A | Direct join to minigames, no cookie | PASS | `SPIKE configure SpikeBot isTransferred=false`, no cookie read |
| B | survival `/hop minigames` | PASS | bot `cookie_stored` → `transferred` → `cookie_requested present=true`; minigames log `SPIKE cookie=1795016135447` in the configuration phase |
| C | Required pack in the configuration phase, accepted | PASS | bot `pack_offered state=configuration forced=true` before `joined`; log `SPIKE pack=SUCCESSFULLY_LOADED` |
| D | Same, declined | PASS | bot `pack_answered DECLINED` → `kicked "SPIKE pack required"`; `connection.disconnect(...)` works in the configuration phase |
| E | survival → minigames → survival with `forceLoginFromProxy` from a `LOW` configure listener | PASS | no `dialog_shown` on the return; AuthMe: `The user SpikeBot has been automatically logged in, as present in autologin queue.` |
| F | Control: same trip without the API call (sessions off) | PASS | `dialog_shown kind=login` on the return |

## Conclusions for plan 1

- Cookies survive a transfer and are readable in `AsyncPlayerConnectionConfigureEvent`. The
  design in spec §8 works as written.
- A required pack can be sent and awaited in the configuration phase (spec §9.3).
- AuthMe 6.0.1's public `forceLoginFromProxy(name)`, called from a `LOW`-priority configure
  listener, skips the pre-join login dialog with the bungeecord hook off. Task 6 goes ahead as
  planned; the session fallback is not needed.
- Live has AuthMe sessions on with a 10000-minute timeout, so returning players from the same IP
  would usually skip the dialog anyway; the ticket also covers changed IPs and expired sessions.

## Harness notes

- Test servers must run on node1: on the WDP host two 1 GB servers left ~0.5 GB free and were
  SIGTERMed.
- Paper 26.3 starts with `white-list=true` by default; the harness sets `white-list=false`.
- The console is a FIFO opened read-write by the Java process (PID 1). With `tail -f | java`
  Paper saw console EOF and stopped.
