# Paintball: bomb mode ("Verfbom") and the shop items

Date: 2026-11-25. Owner asked for a Counter-Strike-like, round-based bomb mode in which the shop is
required, plus the items of the shop. The shop (menu, emerald hotbar item, level lock) was built by
another session and is empty; it is shared by `tdm_shop`, `ctf_shop` and the new bomb mode.

## Intent

A third, more tactical paintball mode for players who have levelled up: short rounds, no respawns,
money that carries over between rounds, and a bomb to plant or defuse. The shop gives every shop
mode something to spend on.

## Approaches considered

1. **Bomb as a third `PaintballMatch.Mode` (picked).** Reuses shooting, hits, bots, splats, scope,
   HUD and bounds. Round logic, cash and shop live in new pure classes (`BombRounds`, `Wallet`,
   `ShopItem`) so they can be unit tested and the 1100-line match class only gets hooks.
2. A separate `BombMatch` handler: clean, but would copy most of `PaintballMatch` (shooting, bots,
   splats). Rejected: two copies drift apart.
3. A new game plugin: rejected, the owner wants it inside paintball next to the shop modes.

## Rules (all in `paintball/config.yml` under `bomb:`)

- Mode id `paintball:bomb`, unlocks at paintball level **10** (`bomb.unlock-level`).
- Arena: any arena with flags. The **bomb site is the defending team's flag spot** (radius 4), so
  every capture-the-flag arena works without new arena data. Team 0 attacks first; sides swap after
  half the rounds, and cash and items reset then.
- Round: 10 s buy phase (frozen at spawn, shop open), then 90 s. 3 hits = out for the rest of the
  round (no respawn). First to 5 round wins, at most 8 rounds (4-4 is a draw).
- The bomb starts with a random attacker (hotbar slot 7). A carrier who goes out drops it; any
  attacker can pick it up. Plant: sneak on the site with the bomb in hand for 4 s. Fuse: 35 s.
  Defuse: a defender sneaks next to the bomb for 6 s (3 s with a defuse kit). Moving off or being
  hit stops the progress.
- Round win: all enemies out; the bomb explodes (attackers); the bomb is defused or time runs out
  with no bomb planted (defenders). A planted bomb keeps the round going past the round time.
- Bots play: attackers walk to the site (the carrier plants), defenders guard it and defuse.

## Cash (`Wallet`, per match, never saved)

- Bomb: start 800, round win +3000, round loss +1900, elimination +300, plant or defuse +300,
  cap 9000.
- Other shop modes: start 800, elimination +300, flag capture +600, flag return +200.

## Shop items (`ShopItem`)

| Item | Price | Effect |
|------|------:|--------|
| Verfvest (vest) | 400 | Absorbs the next hit (the shield) |
| EHBO-kit (repair) | 250 | Removes one hit taken, at once |
| Sprintschoenen | 350 | Speed I until you are out |
| Snelvuur-patroon | 500 | Hotbar item: right-click for 10 s rapid fire |
| Driedubbel-patroon | 600 | Hotbar item: right-click for 8 s triple shot |
| Verfgranaat | 300 | Thrown; one hit on every enemy within 3.5 blocks (max 2) |
| Rookgranaat | 300 | Thrown; a thick smoke cloud for 9 s (max 1) |
| Ontmantelset | 400 | Bomb mode, defenders only: defuse in 3 s |

Items last until the player is out (or the half ends). In the bomb mode the shop only opens in the
buy phase; in the other shop modes any time while in the field.

## Props (resource pack)

3D models made with the existing box-model generator in a new `tools/pack_src/props.py`: the paint
bomb (planted on the site as an item display and held in hand), the paint grenade, the smoke grenade
and the defuse kit; sprite icons for the vest, the boots and the two cartridges.

## Left to the builder

Message texts (Dutch, house colours), the exact HUD lines, the bot details, model shapes.
