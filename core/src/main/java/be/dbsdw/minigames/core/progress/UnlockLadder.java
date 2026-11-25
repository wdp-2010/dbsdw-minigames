package be.dbsdw.minigames.core.progress;

import be.dbsdw.minigames.core.api.ModeSpec;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;

/** The modes of one game in the order they unlock, and where a player stands on that ladder. */
public final class UnlockLadder {
    public enum State {
        /** No level needed. */
        OPEN,
        /** The player's level is high enough. */
        UNLOCKED,
        /** Locked, and the first one to come: the player's next goal. */
        NEXT,
        /** Locked, further away than the next goal. */
        LOCKED
    }

    /** One mode: the level it needs, whether the player has it, and the XP still missing. */
    public record Entry(ModeSpec mode, int need, State state, long xpMissing) {
    }

    private UnlockLadder() {
    }

    /**
     * Sorted by required level, modes with the same level in their original order.
     *
     * @param required the level a mode needs (1 or less: none)
     * @param xp       the player's total XP in the game
     */
    public static List<Entry> build(List<ModeSpec> modes, ToIntFunction<ModeSpec> required, LevelCurve curve, long xp) {
        long total = Math.max(0, xp);
        int level = curve.levelOf(total);
        List<ModeSpec> sorted = modes.stream()
                .sorted(Comparator.comparingInt(m -> Math.max(1, required.applyAsInt(m)))).toList();
        int next = Integer.MAX_VALUE;
        for (ModeSpec mode : sorted) {
            int need = required.applyAsInt(mode);
            if (need > level) {
                next = Math.min(next, need);
            }
        }
        int nextLevel = next;
        return sorted.stream().map(mode -> {
            int need = Math.max(1, required.applyAsInt(mode));
            if (need <= 1) {
                return new Entry(mode, 1, State.OPEN, 0);
            }
            if (need <= level) {
                return new Entry(mode, need, State.UNLOCKED, 0);
            }
            long missing = Math.max(0, curve.xpFor(need) - total);
            return new Entry(mode, need, need == nextLevel ? State.NEXT : State.LOCKED, missing);
        }).toList();
    }

    /** How many of the entries the player can play. */
    public static long playable(List<Entry> ladder) {
        return ladder.stream().filter(e -> e.state() == State.OPEN || e.state() == State.UNLOCKED).count();
    }
}
