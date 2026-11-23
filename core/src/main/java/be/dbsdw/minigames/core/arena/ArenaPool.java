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

/**
 * All arenas and which of them are in use (reserved by a countdown or running a match).
 *
 * <p>A template arena ({@link ArenaDefinition#isTemplate}) is listed and chosen like any other map,
 * but reserving it hands out one of its free copies ({@link ArenaCopies}); the copy's name is what
 * gets released again. The template itself is never reserved.
 */
public final class ArenaPool {
    private final Map<String, ArenaDefinition> arenas = new LinkedHashMap<>();
    /** Copies of each template, by template name. */
    private final Map<String, List<ArenaDefinition>> copies = new HashMap<>();
    /** Names of reserved arenas and reserved copies. */
    private final Set<String> busy = new HashSet<>();
    /** When each arena was last reserved (a counter, not a clock); never reserved = absent. */
    private final Map<String, Long> lastUsed = new HashMap<>();
    private long uses;
    private final int copySpacing;

    public ArenaPool(Collection<ArenaDefinition> initial) {
        this(initial, ArenaCopies.DEFAULT_SPACING);
    }

    /** {@code copySpacing}: blocks from one copy of a template to the next (see {@link ArenaCopies}). */
    public ArenaPool(Collection<ArenaDefinition> initial, int copySpacing) {
        this.copySpacing = copySpacing;
        initial.forEach(this::put);
    }

    public int copySpacing() {
        return copySpacing;
    }

    /**
     * Reserves a free arena that fits the mode: the one reserved longest ago (never reserved counts
     * as oldest), a random one among equals. So players rotate through the maps instead of getting
     * the same one again by chance.
     */
    public Optional<ArenaDefinition> reserve(ModeSpec mode, RandomGenerator rnd) {
        List<ArenaDefinition> free = arenas.values().stream()
                .filter(a -> freeCopy(a).isPresent() && a.fits(mode))
                .toList();
        if (free.isEmpty()) {
            return Optional.empty();
        }
        long oldest = free.stream().mapToLong(a -> lastUsed.getOrDefault(a.name(), -1L)).min().orElseThrow();
        List<ArenaDefinition> candidates = free.stream()
                .filter(a -> lastUsed.getOrDefault(a.name(), -1L) == oldest).toList();
        ArenaDefinition picked = candidates.get(rnd.nextInt(candidates.size()));
        return Optional.of(take(picked));
    }

    /** A plain arena if it is free, or a template's first free copy. */
    private Optional<ArenaDefinition> freeCopy(ArenaDefinition arena) {
        if (!arena.isTemplate()) {
            return busy.contains(arena.name()) ? Optional.empty() : Optional.of(arena);
        }
        return copies.getOrDefault(arena.name(), List.of()).stream().filter(c -> !busy.contains(c.name())).findFirst();
    }

    /** Reserves this arena if it exists, is free and fits the mode. */
    public Optional<ArenaDefinition> reserve(String name, ModeSpec mode) {
        ArenaDefinition arena = arenas.get(name);
        if (arena == null || freeCopy(arena).isEmpty() || !arena.fits(mode)) {
            return Optional.empty();
        }
        return Optional.of(take(arena));
    }

    /** Marks the arena (or a free copy of the template) busy; returns what was reserved. */
    private ArenaDefinition take(ArenaDefinition arena) {
        ArenaDefinition reserved = freeCopy(arena).orElseThrow();
        busy.add(reserved.name());
        lastUsed.put(arena.name(), ++uses);
        return reserved;
    }

    /** The template a reserved copy was made from; empty for a plain arena. */
    public Optional<ArenaDefinition> templateOf(String reservedName) {
        if (!ArenaCopies.isCopyName(reservedName)) {
            return Optional.empty();
        }
        return Optional.ofNullable(arenas.get(ArenaCopies.templateName(reservedName))).filter(ArenaDefinition::isTemplate);
    }

    public void release(String name) {
        busy.remove(name);
    }

    /** Whether any arena, busy or free, fits the mode. */
    public boolean anyFits(ModeSpec mode) {
        return arenas.values().stream().anyMatch(a -> a.fits(mode));
    }

    /** For a template: whether any of its copies is in use. */
    public boolean isBusy(String name) {
        ArenaDefinition arena = arenas.get(name);
        if (arena != null && arena.isTemplate()) {
            return copies.getOrDefault(name, List.of()).stream().anyMatch(c -> busy.contains(c.name()));
        }
        return busy.contains(name);
    }

    /** How many copies of a template are in use (0 or 1 for a plain arena). */
    public int busyCount(String name) {
        ArenaDefinition arena = arenas.get(name);
        if (arena != null && arena.isTemplate()) {
            return (int) copies.getOrDefault(name, List.of()).stream().filter(c -> busy.contains(c.name())).count();
        }
        return busy.contains(name) ? 1 : 0;
    }

    /**
     * Adds or replaces an arena. A match already running on it keeps its own copy. A copy still in
     * use keeps its name reserved, so a shrunk template hands it out again only once it is released.
     */
    public void put(ArenaDefinition arena) {
        arenas.put(arena.name(), arena);
        copies.put(arena.name(), ArenaCopies.of(arena, copySpacing));
    }

    public boolean remove(String name) {
        copies.remove(name);
        return arenas.remove(name) != null;
    }

    public Optional<ArenaDefinition> get(String name) {
        return Optional.ofNullable(arenas.get(name));
    }

    public Collection<ArenaDefinition> all() {
        return List.copyOf(arenas.values());
    }
}
