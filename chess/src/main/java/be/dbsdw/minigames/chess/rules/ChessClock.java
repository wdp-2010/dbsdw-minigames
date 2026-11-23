package be.dbsdw.minigames.chess.rules;

/**
 * Two chess clocks with an increment (Fischer). A clock with no starting time never runs out. Time is
 * fed in by the caller ({@link #run}), so the class does not read a system clock.
 */
public final class ChessClock {
    private final long initialMillis;
    private final long incrementMillis;
    private long whiteMillis;
    private long blackMillis;

    public ChessClock(long initialMillis, long incrementMillis) {
        this(initialMillis, incrementMillis, initialMillis, initialMillis);
    }

    /** A clock restored from a saved game. */
    public ChessClock(long initialMillis, long incrementMillis, long whiteMillis, long blackMillis) {
        if (initialMillis < 0 || incrementMillis < 0) {
            throw new IllegalArgumentException("clock times must not be negative");
        }
        this.initialMillis = initialMillis;
        this.incrementMillis = incrementMillis;
        this.whiteMillis = whiteMillis;
        this.blackMillis = blackMillis;
    }

    public boolean unlimited() {
        return initialMillis == 0;
    }

    public long initialMillis() {
        return initialMillis;
    }

    public long incrementMillis() {
        return incrementMillis;
    }

    public long millis(boolean white) {
        return white ? whiteMillis : blackMillis;
    }

    /** Takes elapsed time off the side that is thinking. */
    public void run(boolean whiteThinking, long elapsedMillis) {
        if (unlimited() || elapsedMillis <= 0) {
            return;
        }
        if (whiteThinking) {
            whiteMillis = Math.max(0, whiteMillis - elapsedMillis);
        } else {
            blackMillis = Math.max(0, blackMillis - elapsedMillis);
        }
    }

    /** The side finished a move: it gets its increment. */
    public void moved(boolean white) {
        if (unlimited()) {
            return;
        }
        if (white) {
            whiteMillis += incrementMillis;
        } else {
            blackMillis += incrementMillis;
        }
    }

    public boolean flagged(boolean white) {
        return !unlimited() && millis(white) <= 0;
    }

    /** m:ss, or h:mm:ss from an hour; tenths below ten seconds (0:09.4). */
    public static String format(long millis) {
        long ms = Math.max(0, millis);
        if (ms < 10_000) {
            return String.format("0:%02d.%d", ms / 1000, (ms % 1000) / 100);
        }
        long s = ms / 1000;
        if (s >= 3600) {
            return String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
        }
        return String.format("%d:%02d", s / 60, s % 60);
    }
}
