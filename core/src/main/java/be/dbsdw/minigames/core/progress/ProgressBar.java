package be.dbsdw.minigames.core.progress;

/** A text progress bar for menu lore, e.g. {@code ■■■■□□□□□□}, as MiniMessage. */
public final class ProgressBar {
    private static final String FULL = "■";
    private static final String DONE = "<#9CC05A>";
    private static final String TODO = "<dark_gray>";

    private ProgressBar() {
    }

    /** How many of {@code width} cells are filled: whole cells only, so a bar is full only when it is. */
    public static int filled(double fraction, int width) {
        if (width <= 0 || Double.isNaN(fraction)) {
            return 0;
        }
        return (int) Math.floor(Math.max(0.0, Math.min(1.0, fraction)) * width + 1e-9);
    }

    /** MiniMessage text of the bar. */
    public static String mini(double fraction, int width) {
        int filled = filled(fraction, width);
        return DONE + FULL.repeat(filled) + "</#9CC05A>" + TODO + FULL.repeat(Math.max(0, width - filled));
    }
}
