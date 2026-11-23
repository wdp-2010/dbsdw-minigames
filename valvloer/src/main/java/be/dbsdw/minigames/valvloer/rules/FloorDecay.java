package be.dbsdw.minigames.valvloer.rules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Which floor blocks are about to drop: a block goes {@code delay} ticks after it is first stepped
 * on. The delay is the same for every block, so a queue keeps them in drop order.
 */
public final class FloorDecay {
    private record Pending(long key, long due) {}

    private final long delay;
    private final ArrayDeque<Pending> queue = new ArrayDeque<>();
    private final Set<Long> marked = new HashSet<>();

    public FloorDecay(long delay) {
        if (delay < 0) {
            throw new IllegalArgumentException("drop delay must be 0 or more");
        }
        this.delay = delay;
    }

    /** Someone stands on the block now. True the first time (the block starts to crack). */
    public boolean touch(long key, long now) {
        if (!marked.add(key)) {
            return false;
        }
        queue.add(new Pending(key, now + delay));
        return true;
    }

    public boolean marked(long key) {
        return marked.contains(key);
    }

    /** The blocks whose time has come, in the order they were stepped on. */
    public List<Long> due(long now) {
        List<Long> out = new ArrayList<>();
        while (!queue.isEmpty() && queue.peek().due() <= now) {
            out.add(queue.poll().key());
        }
        return out;
    }
}
