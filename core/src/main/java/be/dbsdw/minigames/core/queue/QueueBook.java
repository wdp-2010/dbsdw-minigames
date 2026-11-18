package be.dbsdw.minigames.core.queue;

import be.dbsdw.minigames.core.api.ModeSpec;
import be.dbsdw.minigames.core.arena.ArenaDefinition;
import be.dbsdw.minigames.core.arena.ArenaPool;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Every mode's queue and countdown. A player is in at most one queue. Call {@link #tick()} once per
 * server tick; it reserves arenas, runs countdowns and says when a match should start.
 */
public final class QueueBook {
    public enum Toggle { JOINED, LEFT, MOVED }

    private static final int TICKS_PER_SECOND = 20;
    private static final Set<Integer> ANNOUNCE = Set.of(30, 20, 10, 5, 4, 3, 2, 1);

    private final ArenaPool pool;
    private final QueueTimings timings;
    private final RandomGenerator rnd;
    private final Map<String, ModeQueue> queues = new LinkedHashMap<>();

    public QueueBook(ArenaPool pool, QueueTimings timings, RandomGenerator rnd) {
        this.pool = pool;
        this.timings = timings;
        this.rnd = rnd;
    }

    public void addMode(ModeSpec mode) {
        queues.putIfAbsent(mode.key(), new ModeQueue(mode));
    }

    /** Drops a mode; returns who was queued for it and releases its reserved arena. */
    public List<UUID> removeMode(String key) {
        ModeQueue queue = queues.remove(key);
        if (queue == null) {
            return List.of();
        }
        queue.stopCountdown();
        return List.copyOf(queue.players);
    }

    public Toggle toggle(UUID player, String modeKey) {
        ModeQueue target = queues.get(modeKey);
        if (target == null) {
            throw new IllegalArgumentException("unknown mode " + modeKey);
        }
        if (target.players.remove(player)) {
            return Toggle.LEFT;
        }
        boolean moved = remove(player);
        target.players.add(player);
        return moved ? Toggle.MOVED : Toggle.JOINED;
    }

    /** Takes the player out of whatever queue they are in. */
    public boolean remove(UUID player) {
        for (ModeQueue queue : queues.values()) {
            if (queue.players.remove(player)) {
                return true;
            }
        }
        return false;
    }

    public Optional<String> queueOf(UUID player) {
        return queues.values().stream()
                .filter(q -> q.players.contains(player))
                .map(q -> q.mode.key())
                .findFirst();
    }

    /** The players queued for the mode, in join order. */
    public List<UUID> queued(String key) {
        ModeQueue queue = queues.get(key);
        return queue == null ? List.of() : List.copyOf(queue.players);
    }

    /** Puts players back at the front of a queue, in order (a start that fell through). */
    public void requeueFront(String key, List<UUID> players) {
        ModeQueue queue = queues.get(key);
        if (queue == null) {
            throw new IllegalArgumentException("unknown mode " + key);
        }
        players.forEach(this::remove);
        List<UUID> rest = List.copyOf(queue.players);
        queue.players.clear();
        queue.players.addAll(players);
        queue.players.addAll(rest);
    }

    public int size(String key) {
        ModeQueue queue = queues.get(key);
        return queue == null ? 0 : queue.players.size();
    }

    public OptionalInt secondsLeft(String key) {
        ModeQueue queue = queues.get(key);
        if (queue == null || queue.arena == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of((queue.ticksLeft + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND);
    }

    public List<QueueEvent> tick() {
        List<QueueEvent> events = new ArrayList<>();
        for (ModeQueue queue : queues.values()) {
            queue.tick(events);
        }
        return events;
    }

    private final class ModeQueue {
        final ModeSpec mode;
        final LinkedHashSet<UUID> players = new LinkedHashSet<>();
        ArenaDefinition arena;
        int ticksLeft;
        boolean waitingAnnounced;

        ModeQueue(ModeSpec mode) {
            this.mode = mode;
        }

        void tick(List<QueueEvent> events) {
            int size = players.size();
            if (arena == null) {
                if (size < mode.startMinimum()) {
                    waitingAnnounced = false;
                    return;
                }
                Optional<ArenaDefinition> reserved = pool.reserve(mode, rnd);
                if (reserved.isEmpty()) {
                    if (!waitingAnnounced) {
                        waitingAnnounced = true;
                        events.add(new QueueEvent.WaitingForArena(mode.key(), List.copyOf(players)));
                    }
                    return;
                }
                waitingAnnounced = false;
                arena = reserved.get();
                int seconds = size >= mode.maximum() ? timings.fullCountdownSeconds() : timings.countdownSeconds();
                ticksLeft = seconds * TICKS_PER_SECOND;
                events.add(new QueueEvent.CountdownStarted(mode.key(), arena.name(), seconds));
                return;
            }
            if (size < mode.startMinimum()) {
                stopCountdown();
                events.add(new QueueEvent.CountdownCancelled(mode.key(), List.copyOf(players)));
                return;
            }
            int fullTicks = timings.fullCountdownSeconds() * TICKS_PER_SECOND;
            if (size >= mode.maximum() && ticksLeft > fullTicks) {
                ticksLeft = fullTicks;
                events.add(new QueueEvent.CountdownTick(mode.key(), timings.fullCountdownSeconds(), List.copyOf(players)));
                return;
            }
            ticksLeft--;
            if (ticksLeft == 0) {
                List<UUID> starting = players.stream().limit(mode.maximum()).toList();
                starting.forEach(players::remove);
                events.add(new QueueEvent.Start(mode, arena, starting));
                arena = null;
                return;
            }
            if (ticksLeft % TICKS_PER_SECOND == 0 && ANNOUNCE.contains(ticksLeft / TICKS_PER_SECOND)) {
                events.add(new QueueEvent.CountdownTick(mode.key(), ticksLeft / TICKS_PER_SECOND, List.copyOf(players)));
            }
        }

        void stopCountdown() {
            if (arena != null) {
                pool.release(arena.name());
                arena = null;
            }
        }
    }
}
