package be.dbsdw.minigames.paintball.rules;

/**
 * A pop-up target on the practice range: after enough hits it sinks into the floor, stays down
 * for a while and rises again with its hits reset. Hits while it is down or moving do nothing.
 */
public final class PopupTarget {
    public enum Hit { HIT, DOWN, IGNORED }

    /** How far it sinks, in blocks (a dummy is two blocks tall). */
    public static final double DEPTH = 2.0;
    /** Ticks to sink or to rise. */
    public static final long MOVE_TICKS = 8;

    private final int hitsToDown;
    private final long downTicks;
    private int hits;
    private long downAt = Long.MIN_VALUE;

    public PopupTarget(int hitsToDown, long downTicks) {
        Settings.require(hitsToDown >= 1, "practice.target-hits must be >= 1");
        Settings.require(downTicks >= MOVE_TICKS, "practice.target-down-seconds is too short");
        this.hitsToDown = hitsToDown;
        this.downTicks = downTicks;
    }

    public Hit hit(long now) {
        if (isDown(now)) {
            return Hit.IGNORED;
        }
        if (++hits >= hitsToDown) {
            hits = 0;
            downAt = now;
            return Hit.DOWN;
        }
        return Hit.HIT;
    }

    /** Down, sinking or rising: from the last hit until it is fully up again. */
    public boolean isDown(long now) {
        return downAt != Long.MIN_VALUE && now < downAt + downTicks + MOVE_TICKS;
    }

    /** How far below its spot it is now, 0 (up) to {@link #DEPTH}. */
    public double sink(long now) {
        if (!isDown(now)) {
            return 0;
        }
        long t = now - downAt;
        if (t < MOVE_TICKS) {
            return DEPTH * t / MOVE_TICKS;
        }
        if (t < downTicks) {
            return DEPTH;
        }
        return DEPTH * (1 - (double) (t - downTicks) / MOVE_TICKS);
    }
}
