package be.dbsdw.minigames.paintball.rules;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Eliminations in a row without going out; announced at {@link #ON_FIRE} and {@link #UNSTOPPABLE}. */
public final class Streaks {
    public static final int ON_FIRE = 3;
    public static final int UNSTOPPABLE = 5;

    private final Map<UUID, Integer> streak = new HashMap<>();
    private final Map<UUID, Integer> best = new HashMap<>();

    /** The shooter eliminated someone; returns the new streak. */
    public int eliminated(UUID shooter) {
        int n = streak.merge(shooter, 1, Integer::sum);
        best.merge(shooter, n, Math::max);
        return n;
    }

    /** The player went out; returns the streak that ended (0 if none). */
    public int out(UUID player) {
        Integer n = streak.remove(player);
        return n == null ? 0 : n;
    }

    public int best(UUID player) {
        return best.getOrDefault(player, 0);
    }

    /** True when this streak count deserves an announcement. */
    public static boolean announce(int n) {
        return n == ON_FIRE || n == UNSTOPPABLE || (n > UNSTOPPABLE && n % 5 == 0);
    }
}
