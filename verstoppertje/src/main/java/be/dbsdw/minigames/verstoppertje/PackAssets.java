package be.dbsdw.minigames.verstoppertje;

/**
 * Ids of the resource pack sounds Verstoppertje uses. Every id is written out, so tools/test_pack_keys.py
 * checks each one exists in the pack.
 */
public final class PackAssets {
    /** The hint every hider makes now and then, heard in the world. */
    public static final String SOUND_SQUEAK = "dbsdw:verstoppertje.squeak";
    /** A hider is found (heard in the world). */
    public static final String SOUND_FOUND = "dbsdw:verstoppertje.found";
    /** The seekers are let loose. */
    public static final String SOUND_RELEASE = "dbsdw:verstoppertje.release";
    /** A hider standing still becomes a solid block. */
    public static final String SOUND_LOCK = "dbsdw:verstoppertje.lock";

    private PackAssets() {
    }
}
