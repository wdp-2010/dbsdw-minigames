package be.dbsdw.minigames.core.progress;

import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * How much XP one player earns from one finished match.
 *
 * @param played        base XP for finishing a match
 * @param win           extra XP for a win
 * @param draw          extra XP for a draw
 * @param minSeconds    matches shorter than this give no XP (anti-farm)
 * @param botFactor     multiplier (0..1) for matches played with bots
 */
public record XpRules(int played, int win, int draw, int minSeconds, double botFactor) {
    public XpRules {
        if (played < 0 || win < 0 || draw < 0 || minSeconds < 0) {
            throw new IllegalArgumentException("xp values must be >= 0");
        }
        if (botFactor < 0 || botFactor > 1) {
            throw new IllegalArgumentException("bot factor must be between 0 and 1");
        }
    }

    public static XpRules defaults() {
        return new XpRules(20, 20, 10, 60, 0.25);
    }

    /** The result of a match for one player. */
    public enum Result { WIN, DRAW, LOSS }

    /**
     * XP for the player, or 0 for a practice match, a match that was too short, or a player who
     * was not in it at the end.
     *
     * @param stats         the player's stat deltas of this match
     * @param xpPerStat     the game's XP weight for a stat name
     * @param withBots      whether bots played in the match
     */
    public long award(Result result, Map<String, Long> stats, ToIntFunction<String> xpPerStat,
                      long matchSeconds, boolean practice, boolean present, boolean withBots) {
        if (practice || !present || matchSeconds < minSeconds) {
            return 0;
        }
        long xp = played;
        xp += switch (result) {
            case WIN -> win;
            case DRAW -> draw;
            case LOSS -> 0;
        };
        for (Map.Entry<String, Long> e : stats.entrySet()) {
            long amount = Math.max(0, e.getValue());
            xp += amount * Math.max(0, xpPerStat.applyAsInt(e.getKey()));
        }
        return withBots ? Math.round(xp * botFactor) : xp;
    }
}
