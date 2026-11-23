package be.dbsdw.minigames.core.api;

import java.util.List;
import java.util.UUID;

/**
 * Starts a match for players chosen by a game itself, without a queue or room, e.g. to continue a
 * saved game with the same two players. Get it with
 * {@code getServer().getServicesManager().load(MatchStarter.class)}.
 */
public interface MatchStarter {
    enum Result {
        STARTED,
        /** A player is offline, in a match or in a private room. */
        PLAYER_BUSY,
        /** Every arena for the mode is in use. */
        NO_ARENA,
        UNKNOWN_MODE
    }

    /** Whether the player is online and in no match and no private room (a queue is fine). */
    boolean available(UUID player);

    /**
     * Reserves an arena for the mode and starts the match at once with exactly these teams (team
     * index = position in the list). The players leave any queue they were in.
     */
    Result start(ModeSpec mode, List<List<UUID>> teams);
}
