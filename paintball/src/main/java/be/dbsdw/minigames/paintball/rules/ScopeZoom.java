package be.dbsdw.minigames.paintball.rules;

/**
 * Scope zoom levels. The client narrows its view when the walk speed drops (by (speed + 1) / 2 of
 * the normal field of view, with FOV effects on), so each level is a walk-speed slowdown: deeper
 * zoom, slower steps. The mouse wheel steps through the levels while aiming.
 */
public final class ScopeZoom {
    /** Walk-speed multiplier per level (MULTIPLY_SCALAR_1), from light to deep zoom. */
    private static final double[] SLOWDOWN = {-0.3, -0.6, -0.8, -0.95};
    public static final int LEVELS = SLOWDOWN.length;
    /** The zoom a scope starts at (the original one). */
    public static final int DEFAULT_LEVEL = 1;
    private static final int HOTBAR = 9;

    private ScopeZoom() {
    }

    public static double slowdown(int level) {
        return SLOWDOWN[Math.clamp(level, 0, LEVELS - 1)];
    }

    /** A hotbar change to a neighbouring slot (wrapping round) is one mouse-wheel notch. */
    public static boolean isScroll(int from, int to) {
        int step = Math.floorMod(to - from, HOTBAR);
        return step == 1 || step == HOTBAR - 1;
    }

    /** Wheel up (one slot to the left) zooms in, wheel down zooms out; other changes keep the level. */
    public static int levelAfterScroll(int level, int from, int to) {
        int step = Math.floorMod(to - from, HOTBAR);
        if (step == HOTBAR - 1) {
            return Math.min(LEVELS - 1, level + 1);
        }
        if (step == 1) {
            return Math.max(0, level - 1);
        }
        return level;
    }
}
