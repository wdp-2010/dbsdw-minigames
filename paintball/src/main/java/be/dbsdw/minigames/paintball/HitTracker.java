package be.dbsdw.minigames.paintball;

/** Counts paint hits on one player; the player is out when hits reach the limit. */
public final class HitTracker {
    private final int hitsToOut;
    private int hits;

    public HitTracker(int hitsToOut) {
        if (hitsToOut < 1) {
            throw new IllegalArgumentException("hitsToOut must be >= 1");
        }
        this.hitsToOut = hitsToOut;
    }

    /** Registers a hit and returns true if this hit put the player out. */
    public boolean hit() {
        hits++;
        return hits == hitsToOut;
    }

    public boolean isOut() {
        return hits >= hitsToOut;
    }

    public void reset() {
        hits = 0;
    }
}
