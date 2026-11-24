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

    /** A block on its way down: {@code progress} runs from just above 0 (stepped on) to 1 (drops now). */
    public record Crack(long key, float progress) {}

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

    /** Every block that is stepped on but not yet dropped, with how far along it is. */
    public List<Crack> cracking(long now) {
        List<Crack> out = new ArrayList<>(queue.size());
        for (Pending p : queue) {
            float progress = delay == 0 ? 1f : 1f - (float) Math.max(0, p.due() - now) / delay;
            out.add(new Crack(p.key(), Math.max(0.05f, Math.min(1f, progress))));
        }
        return out;
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
