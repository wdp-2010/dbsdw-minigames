package be.dbsdw.minigames.paintball;

import be.dbsdw.minigames.paintball.rules.PowerupType;
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
    public static final String FLAG = "dbsdw:flag";

    public static final String SOUND_SHOT = "dbsdw:paintball.shot";
    public static final String SOUND_SPLAT = "dbsdw:paintball.splat";
    public static final String SOUND_HIT = "dbsdw:paintball.hit";
    public static final String SOUND_HITMARKER = "dbsdw:paintball.hitmarker";
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
    /** Full-screen paint splash shown as a title when the player is hit. */
    public static final Key OVERLAY_FONT = Key.key("dbsdw:overlay");
    public static final String OVERLAY = "";

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
}
