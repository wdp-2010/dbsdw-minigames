package be.dbsdw.minigames.chess.bot;

import be.dbsdw.minigames.chess.rules.Piece;
import java.util.Locale;
import java.util.Optional;

/** How strong the bot plays. Weaker levels search less deep, add noise to their scores and sometimes play at random. */
public enum BotLevel {
    MAKKELIJK(Piece.PAWN, new Engine.Limits(1, 1500, 150, 0.25)),
    NORMAAL(Piece.KNIGHT, new Engine.Limits(2, 1500, 40, 0.05)),
    MOEILIJK(Piece.ROOK, new Engine.Limits(4, 1500, 0, 0)),
    MEESTER(Piece.QUEEN, new Engine.Limits(30, 3000, 0, 0));

    private final int icon;
    private final Engine.Limits limits;

    BotLevel(int icon, Engine.Limits limits) {
        this.icon = icon;
        this.limits = limits;
    }

    /** The piece type shown for this level in the bot menu. */
    public int icon() {
        return icon;
    }

    public Engine.Limits limits() {
        return limits;
    }

    /** Lower-case id, used in messages and commands. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<BotLevel> parse(String id) {
        for (BotLevel l : values()) {
            if (l.id().equalsIgnoreCase(id)) {
                return Optional.of(l);
            }
        }
        return Optional.empty();
    }

    /**
     * The search limits for one move, given the bot's clock: a strong level never spends more than a
     * share of its time left. {@code clockMillis} &lt;= 0 means no clock. In the first moves a little
     * noise makes games differ.
     */
    public Engine.Limits limitsFor(long clockMillis, long incrementMillis, int movesPlayed) {
        long millis = limits.millis();
        if (clockMillis > 0) {
            millis = Math.max(100, Math.min(millis, clockMillis / 30 + incrementMillis / 2));
        }
        int noise = limits.noise() == 0 && movesPlayed < 6 ? 12 : limits.noise();
        return new Engine.Limits(limits.maxDepth(), millis, noise, limits.randomChance());
    }
}
