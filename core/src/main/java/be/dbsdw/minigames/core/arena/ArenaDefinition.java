package be.dbsdw.minigames.core.arena;

import be.dbsdw.minigames.core.api.ArenaFeature;
import be.dbsdw.minigames.core.api.ModeSpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An arena: the modes it supports, team spawns, flags, power-up points and two bounding corners.
 * Immutable; the {@code with…} methods return changed copies. Corners are null until set.
 */
public record ArenaDefinition(String name, String world, Set<String> modes, Map<Integer, List<Pos>> spawns,
                              Map<Integer, Pos> flags, List<Pos> powerups, Pos corner1, Pos corner2) {
    public ArenaDefinition {
        modes = Set.copyOf(modes);
        Map<Integer, List<Pos>> copied = new HashMap<>();
        spawns.forEach((team, list) -> copied.put(team, List.copyOf(list)));
        spawns = Map.copyOf(copied);
        flags = Map.copyOf(flags);
        powerups = List.copyOf(powerups);
    }

    public static ArenaDefinition empty(String name, String world) {
        return new ArenaDefinition(name, world, Set.of(), Map.of(), Map.of(), List.of(), null, null);
    }

    public ArenaDefinition withModes(Set<String> newModes) {
        return new ArenaDefinition(name, world, newModes, spawns, flags, powerups, corner1, corner2);
    }

    public ArenaDefinition withSpawn(int team, Pos pos) {
        Map<Integer, List<Pos>> next = new HashMap<>(spawns);
        List<Pos> list = new ArrayList<>(spawns.getOrDefault(team, List.of()));
        list.add(pos);
        next.put(team, list);
        return new ArenaDefinition(name, world, modes, next, flags, powerups, corner1, corner2);
    }

    public ArenaDefinition withFlag(int team, Pos pos) {
        Map<Integer, Pos> next = new HashMap<>(flags);
        next.put(team, pos);
        return new ArenaDefinition(name, world, modes, spawns, next, powerups, corner1, corner2);
    }

    public ArenaDefinition withPowerup(Pos pos) {
        List<Pos> next = new ArrayList<>(powerups);
        next.add(pos);
        return new ArenaDefinition(name, world, modes, spawns, flags, next, corner1, corner2);
    }

    /** {@code which} is 1 or 2. */
    public ArenaDefinition withCorner(int which, Pos pos) {
        return switch (which) {
            case 1 -> new ArenaDefinition(name, world, modes, spawns, flags, powerups, pos, corner2);
            case 2 -> new ArenaDefinition(name, world, modes, spawns, flags, powerups, corner1, pos);
            default -> throw new IllegalArgumentException("corner must be 1 or 2");
        };
    }

    public boolean hasBounds() {
        return corner1 != null && corner2 != null;
    }

    /** Inside the block box spanned by the two corners, corner blocks included. */
    public boolean contains(Pos p) {
        if (!hasBounds()) {
            return false;
        }
        return within(p.x(), corner1.blockX(), corner2.blockX())
                && within(p.y(), corner1.blockY(), corner2.blockY())
                && within(p.z(), corner1.blockZ(), corner2.blockZ());
    }

    private static boolean within(double v, int a, int b) {
        return v >= Math.min(a, b) && v < Math.max(a, b) + 1;
    }

    /** Lists the mode, has bounds, a spawn for every team and, if needed, a flag for every team. */
    public boolean fits(ModeSpec mode) {
        if (!modes.contains(mode.key()) || !hasBounds()) {
            return false;
        }
        for (int team = 0; team < mode.teams(); team++) {
            if (spawns.getOrDefault(team, List.of()).isEmpty()) {
                return false;
            }
            if (mode.features().contains(ArenaFeature.FLAGS) && !flags.containsKey(team)) {
                return false;
            }
        }
        return true;
    }
}
