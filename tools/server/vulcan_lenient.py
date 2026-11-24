#!/usr/bin/env python3
"""Turn the survival server's Vulcan config.yml into a lenient one for the minigame server.

Usage: python3 vulcan_lenient.py <survival config.yml> <output config.yml>

What changes (line by line, so every comment and unknown key stays as it is):
- every check's punishment is a kick, never a (temp)ban; the kick message says you may rejoin;
- every check's max-violations is doubled (at least 2), so a few flags never kick;
- /vulcan ban and judgement days kick instead of ban;
- settings.server-name is "Minigames" and the Discord punishment webhook shows it.

The survival config holds a Discord webhook URL: never commit its input or output.
"""
import re
import sys

KICK = "minecraft:kick %player% Vulcan: verdacht gedrag gezien. Je mag meteen opnieuw joinen."
# Commands that ban or tempban (with or without a namespace); kicks stay kicks.
BAN = re.compile(r"^(\s*-\s*)['\"]?(?:[a-z0-9_.-]+:)?(?:temp)?ban(?:-ip)?\s", re.IGNORECASE)
# Essentials-style kicks with colour codes, which the vanilla /kick would show as "&c".
COLOUR_KICK = re.compile(r"^(\s*-\s*)['\"]?kick\s+%player%\s+&", re.IGNORECASE)
LIST_KEYS = ("punishment-commands:", "commands:")


def indent(line):
    return len(line) - len(line.lstrip(" "))


def transform(lines):
    out = []
    in_checks = False
    in_settings = False
    in_list = None  # indent of the key that owns the current command list
    for line in lines:
        stripped = line.strip()
        top = line and not line.startswith(" ") and not line.startswith("#") and stripped
        if top:
            in_checks = stripped == "checks:"
            in_settings = stripped == "settings:"
            in_list = None

        if in_list is not None:
            if stripped.startswith("- ") and indent(line) >= in_list:
                m = BAN.match(line) or COLOUR_KICK.match(line)
                if m:
                    line = f"{m.group(1)}{KICK}\n"
                out.append(line)
                continue
            if stripped and not stripped.startswith("#"):
                in_list = None

        if stripped in LIST_KEYS:
            in_list = indent(line)
        elif in_checks and stripped.startswith("max-violations:"):
            value = stripped.split(":", 1)[1].strip()
            if value.isdigit():
                line = " " * indent(line) + f"max-violations: {max(2, int(value) * 2)}\n"
        elif in_settings and stripped.startswith("server-name:"):
            line = " " * indent(line) + "server-name: Minigames\n"
        out.append(line)
    return out


def enable_webhook_server_name(lines):
    """Under discord-webhook.punishments, show which server punished."""
    out = []
    section = []
    for line in lines:
        stripped = line.strip()
        if line and not line.startswith(" ") and stripped and not stripped.startswith("#"):
            section = [stripped]
        elif stripped.endswith(":") and not stripped.startswith("#") and not stripped.startswith("-"):
            level = indent(line) // 2
            section = section[:level] + [stripped]
        if section[:2] == ["discord-webhook:", "punishments:"] and stripped.startswith("server-name-field:"):
            line = " " * indent(line) + "server-name-field: true\n"
        out.append(line)
    return out


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    with open(sys.argv[1], encoding="utf-8") as f:
        lines = f.readlines()
    out = enable_webhook_server_name(transform(lines))
    bans = [l for l in out if BAN.match(l)]
    if bans:
        sys.exit("still a ban command left: " + bans[0].strip())
    with open(sys.argv[2], "w", encoding="utf-8") as f:
        f.writelines(out)


if __name__ == "__main__":
    main()
