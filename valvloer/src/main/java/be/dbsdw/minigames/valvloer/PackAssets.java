package be.dbsdw.minigames.valvloer;

/**
 * Ids of the resource pack sounds Valvloer uses. Every id is written out, so tools/test_pack_keys.py
 * checks each one exists in the pack.
 */
public final class PackAssets {
    /** A floor block starts to crack under someone's feet. */
    public static final String SOUND_CRACK = "dbsdw:valvloer.crack";
    /** A floor block gives way. */
    public static final String SOUND_DROP = "dbsdw:valvloer.drop";
    /** A player falls through the floor and is out. */
    public static final String SOUND_FALL = "dbsdw:valvloer.fall";
    /** Grace is over: the floor starts to drop. */
    public static final String SOUND_GO = "dbsdw:valvloer.go";

    private PackAssets() {
    }
}
