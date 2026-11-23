package be.dbsdw.minigames.chess;

import java.util.List;

/**
 * Everything from config.yml. Times are in milliseconds or ticks as named.
 *
 * @param pieceScale piece size as a share of a square (1 = the model is one square wide)
 */
public record ChessSettings(List<TimeControl> modes, long awayGraceTicks, int maxUnfinished, long expireMillis,
                            long inviteTicks, double pieceScale, int moveTicks, double reach, boolean debug) {
    /** One mode: its id, menu name (MiniMessage) and clock; 0 minutes is a game without a clock. */
    public record TimeControl(String id, String name, long initialMillis, long incrementMillis) {}

    public ChessSettings {
        modes = List.copyOf(modes);
    }
}
