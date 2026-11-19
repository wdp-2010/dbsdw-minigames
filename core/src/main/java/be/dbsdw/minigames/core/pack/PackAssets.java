package be.dbsdw.minigames.core.pack;

/**
 * Ids of the resource pack assets MinigameCore uses. Every pack id lives in a PackAssets class, so
 * tools/test_pack_keys.py can check that the pack has it. See {@link PackItems} to use them.
 */
public final class PackAssets {
    public static final String LOBBY_GAMES = "dbsdw:lobby_games";
    public static final String LOBBY_STATS = "dbsdw:lobby_stats";
    public static final String LOBBY_SURVIVAL = "dbsdw:lobby_survival";

    public static final String SOUND_COUNTDOWN = "dbsdw:match.countdown";
    public static final String SOUND_START = "dbsdw:match.start";
    public static final String SOUND_WIN = "dbsdw:match.win";
    public static final String SOUND_LOSE = "dbsdw:match.lose";
    public static final String SOUND_DRAW = "dbsdw:match.draw";

    private PackAssets() {
    }
}
