package be.dbsdw.minigames.paintball.rules;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Timed power-up effects per player (rapid fire, triple shot, speed). */
public final class Buffs {
    private final Map<UUID, Map<PowerupType, Long>> until = new HashMap<>();

    /** Starts or extends the effect to {@code now + duration}. */
    public void give(UUID player, PowerupType type, long now, long duration) {
        until.computeIfAbsent(player, k -> new EnumMap<>(PowerupType.class)).put(type, now + duration);
    }

    public boolean active(UUID player, PowerupType type, long now) {
        return left(player, type, now) > 0;
    }

    public long left(UUID player, PowerupType type, long now) {
        Map<PowerupType, Long> m = until.get(player);
        Long end = m == null ? null : m.get(type);
        return end == null ? 0 : Math.max(0, end - now);
    }

    public void clear(UUID player) {
        until.remove(player);
    }
}
