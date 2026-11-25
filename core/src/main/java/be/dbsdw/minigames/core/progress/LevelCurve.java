package be.dbsdw.minigames.core.progress;

/**
 * Maps total XP to a level and back. Level 1 needs no XP; reaching level {@code n} takes
 * {@code round(base * (n - 1) ^ exponent)} total XP, so each level costs a bit more than the last.
 */
public record LevelCurve(double base, double exponent, int maxLevel) {
    public LevelCurve {
        if (base <= 0) {
            throw new IllegalArgumentException("curve base must be > 0");
        }
        if (exponent < 1) {
            throw new IllegalArgumentException("curve exponent must be >= 1");
        }
        if (maxLevel < 1) {
            throw new IllegalArgumentException("max level must be >= 1");
        }
    }

    public static LevelCurve defaults() {
        return new LevelCurve(100, 1.5, 100);
    }

    /** Total XP needed to reach {@code level} (0 for level 1 and below). */
    public long xpFor(int level) {
        if (level <= 1) {
            return 0;
        }
        return Math.round(base * Math.pow(Math.min(level, maxLevel) - 1, exponent));
    }

    /** The level a player with this much total XP is on, 1 to {@code maxLevel}. */
    public int levelOf(long totalXp) {
        int level = 1;
        while (level < maxLevel && totalXp >= xpFor(level + 1)) {
            level++;
        }
        return level;
    }

    /** XP still missing for the next level, or 0 at the max level. */
    public long xpToNext(long totalXp) {
        int level = levelOf(totalXp);
        return level >= maxLevel ? 0 : xpFor(level + 1) - totalXp;
    }

    /** Progress through the current level, 0.0 up to just under 1.0; 1.0 at the max level. */
    public double fraction(long totalXp) {
        int level = levelOf(totalXp);
        if (level >= maxLevel) {
            return 1.0;
        }
        long from = xpFor(level);
        return (double) (totalXp - from) / (xpFor(level + 1) - from);
    }
}
