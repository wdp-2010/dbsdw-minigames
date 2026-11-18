package be.dbsdw.minigames.paintball.rules;

/** Formats a tick count as {@code m:ss}, rounding partial seconds up. */
public final class Clock {
    private Clock() {
    }

    public static String format(long ticks) {
        long seconds = Math.max(0, (ticks + 19) / 20);
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    /** Whole seconds left, rounded up, never negative. */
    public static long secondsLeft(long ticks) {
        return Math.max(0, (ticks + 19) / 20);
    }
}
