package be.dbsdw.minigames.paintball.rules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Paint splats of one match, oldest first: a cap and a lifetime decide which ones go. */
public final class SplatQueue<T> {
    private record Entry<T>(T id, long at) {
    }

    private final Deque<Entry<T>> splats = new ArrayDeque<>();
    private final int cap;
    private final long lifetime;

    public SplatQueue(int cap, long lifetime) {
        this.cap = cap;
        this.lifetime = lifetime;
    }

    /** Adds a splat; returns the ones pushed out by the cap (to remove). */
    public List<T> add(T id, long now) {
        List<T> evicted = new ArrayList<>();
        if (cap == 0) {
            evicted.add(id);
            return evicted;
        }
        splats.addLast(new Entry<>(id, now));
        while (splats.size() > cap) {
            evicted.add(splats.removeFirst().id());
        }
        return evicted;
    }

    /** Splats older than the lifetime (to remove). */
    public List<T> expired(long now) {
        List<T> old = new ArrayList<>();
        while (!splats.isEmpty() && now - splats.peekFirst().at() >= lifetime) {
            old.add(splats.removeFirst().id());
        }
        return old;
    }

    /** Everything left (to remove at match end). */
    public List<T> clear() {
        List<T> all = splats.stream().map(Entry::id).toList();
        splats.clear();
        return all;
    }

    public int size() {
        return splats.size();
    }
}
