package be.dbsdw.minigames.paintball;

import be.dbsdw.minigames.paintball.rules.PowerupType;
import be.dbsdw.minigames.paintball.rules.ShopItem;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.TextColor;

/**
 * Ids of the resource pack assets Paintball uses (models, sounds, fonts) and the team colours the
 * pack's tints expect. Every pack id lives here, so tools/test_pack_keys.py can check the pack.
 */
public final class PackAssets {
    public static final int RED = 0xFF3B30;
    public static final int BLUE = 0x2F7BFF;

    public static final String GUN = "dbsdw:gun";
    public static final String PAINTBALL = "dbsdw:paintball";
    public static final String SPLAT = "dbsdw:splat";
    /** Paint running down a wall under a splat: floats[0] part (0 trail, 1 drop), floats[1] fade stage, floats[2] shape. */
    public static final String DRIP = "dbsdw:drip";
    public static final String FLAG = "dbsdw:flag";
    /** The paint bomb of the bomb mode: held in hand and planted on the site (item display). */
    public static final String BOMB = "dbsdw:bomb";
    /** Shop items: 3D grenades and defuse kit, sprite icons for the rest. */
    public static final String PAINT_GRENADE = "dbsdw:paint_grenade";
    public static final String SMOKE_GRENADE = "dbsdw:smoke_grenade";
    public static final String DEFUSE_KIT = "dbsdw:defuse_kit";
    public static final String SHOP_VEST = "dbsdw:shop_vest";
    public static final String SHOP_BOOTS = "dbsdw:shop_boots";
    public static final String SHOP_RAPID = "dbsdw:shop_rapid_cartridge";
    public static final String SHOP_TRIPLE = "dbsdw:shop_triple_cartridge";

    /** Invisible helmet that shows the scope overlay (equippable camera_overlay). */
    public static final String SCOPE = "dbsdw:scope";
    public static final String SCOPE_OVERLAY = "dbsdw:misc/scope";
    public static final String SOUND_SHOT = "dbsdw:paintball.shot";
    public static final String SOUND_SPLAT = "dbsdw:paintball.splat";
    public static final String SOUND_HIT = "dbsdw:paintball.hit";
    public static final String SOUND_HITMARKER = "dbsdw:paintball.hitmarker";
    public static final String SOUND_DRY_FIRE = "dbsdw:paintball.dry";
    public static final String SOUND_ELIMINATE = "dbsdw:paintball.eliminate";
    public static final String SOUND_OUT = "dbsdw:paintball.out";
    public static final String SOUND_RESPAWN = "dbsdw:paintball.respawn";
    public static final String SOUND_SHIELD_BLOCK = "dbsdw:paintball.shield_block";
    public static final String SOUND_SHIELD_BREAK = "dbsdw:paintball.shield_break";
    public static final String SOUND_FLAG_TAKEN = "dbsdw:paintball.flag.taken";
    public static final String SOUND_FLAG_DROPPED = "dbsdw:paintball.flag.dropped";
    public static final String SOUND_FLAG_RETURNED = "dbsdw:paintball.flag.returned";
    public static final String SOUND_FLAG_CAPTURED = "dbsdw:paintball.flag.captured";
    public static final String SOUND_POWERUP = "dbsdw:paintball.powerup";

    /** HUD icons: paint drop (coloured by the text colour) and the full-colour power-up icons. */
    public static final Key HUD_FONT = Key.key("dbsdw:hud");
    public static final String DROP = "";
    /**
     * Paint on the goggles when hit: camera overlays (the client stretches them over the whole
     * screen, whatever its size) per team, full and fading.
     */
    public static final String SPLASH_RED = "dbsdw:misc/splash_red";
    public static final String SPLASH_RED_FADE = "dbsdw:misc/splash_red_fade";
    public static final String SPLASH_BLUE = "dbsdw:misc/splash_blue";
    public static final String SPLASH_BLUE_FADE = "dbsdw:misc/splash_blue_fade";

    public static String splash(int team, boolean fade) {
        if (team == 0) {
            return fade ? SPLASH_RED_FADE : SPLASH_RED;
        }
        return fade ? SPLASH_BLUE_FADE : SPLASH_BLUE;
    }

    private PackAssets() {
    }

    public static int teamRgb(int team) {
        return team == 0 ? RED : BLUE;
    }

    public static TextColor teamColour(int team) {
        return TextColor.color(teamRgb(team));
    }

    public static String powerup(PowerupType type) {
        return switch (type) {
            case RAPID_FIRE -> "dbsdw:powerup_rapid_fire";
            case TRIPLE_SHOT -> "dbsdw:powerup_triple_shot";
            case SPEED -> "dbsdw:powerup_speed";
            case SHIELD -> "dbsdw:powerup_shield";
            case REPAIR -> "dbsdw:powerup_repair";
        };
    }

    /** The model a shop item shows, in the menu and in the hotbar. */
    public static String shopItem(ShopItem item) {
        return switch (item) {
            case VEST -> SHOP_VEST;
            case REPAIR -> "dbsdw:powerup_repair";
            case BOOTS -> SHOP_BOOTS;
            case RAPID_CARTRIDGE -> SHOP_RAPID;
            case TRIPLE_CARTRIDGE -> SHOP_TRIPLE;
            case PAINT_GRENADE -> PAINT_GRENADE;
            case SMOKE_GRENADE -> SMOKE_GRENADE;
            case DEFUSE_KIT -> DEFUSE_KIT;
        };
    }
}
