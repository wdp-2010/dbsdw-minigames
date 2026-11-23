package be.dbsdw.minigames.verstoppertje.rules;

/** Hide phase first (seekers wait blind), then the seek phase until the time is up. */
public enum Phase {
    HIDE, SEEK, OVER;

    public static Phase at(long tick, long hideTicks, long seekTicks) {
        if (tick < hideTicks) {
            return HIDE;
        }
        return tick < hideTicks + seekTicks ? SEEK : OVER;
    }
}
