package be.dbsdw.minigames.core.arena;

import be.dbsdw.minigames.core.api.ModeSpec;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

/** All arenas and which of them are in use (reserved by a countdown or running a match). */
public final class ArenaPool {
    private final Map<String, ArenaDefinition> arenas = new LinkedHashMap<>();
    private final Set<String> busy = new HashSet<>();
    /** When each arena was last reserved (a counter, not a clock); never reserved = absent. */
    private final Map<String, Long> lastUsed = new HashMap<>();
    private long uses;

    public ArenaPool(Collection<ArenaDefinition> initial) {
        initial.forEach(this::put);
    }

    /**
     * Reserves a free arena that fits the mode: the one reserved longest ago (never reserved counts
     * as oldest), a random one among equals. So players rotate through the maps instead of getting
     * the same one again by chance.
     */
    public Optional<ArenaDefinition> reserve(ModeSpec mode, RandomGenerator rnd) {
        List<ArenaDefinition> free = arenas.values().stream()
                .filter(a -> !busy.contains(a.name()) && a.fits(mode))
                .toList();
        if (free.isEmpty()) {
            return Optional.empty();
        }
        long oldest = free.stream().mapToLong(a -> lastUsed.getOrDefault(a.name(), -1L)).min().orElseThrow();
        List<ArenaDefinition> candidates = free.stream()
                .filter(a -> lastUsed.getOrDefault(a.name(), -1L) == oldest).toList();
        ArenaDefinition picked = candidates.get(rnd.nextInt(candidates.size()));
        take(picked.name());
        return Optional.of(picked);
    }

    /** Reserves this arena if it exists, is free and fits the mode. */
    public Optional<ArenaDefinition> reserve(String name, ModeSpec mode) {
        ArenaDefinition arena = arenas.get(name);
        if (arena == null || busy.contains(name) || !arena.fits(mode)) {
            return Optional.empty();
        }
        take(name);
        return Optional.of(arena);
    }

    private void take(String name) {
        busy.add(name);
        lastUsed.put(name, ++uses);
    }

    public void release(String name) {
        busy.remove(name);
    }

    /** Whether any arena, busy or free, fits the mode. */
    public boolean anyFits(ModeSpec mode) {
        return arenas.values().stream().anyMatch(a -> a.fits(mode));
    }

    public boolean isBusy(String name) {
        return busy.contains(name);
    }

    /** Adds or replaces an arena. A match already running on it keeps its own copy. */
    public void put(ArenaDefinition arena) {
        arenas.put(arena.name(), arena);
    }

    public boolean remove(String name) {
        return arenas.remove(name) != null;
    }

    public Optional<ArenaDefinition> get(String name) {
        return Optional.ofNullable(arenas.get(name));
    }

    public Collection<ArenaDefinition> all() {
        return List.copyOf(arenas.values());
    }
}
