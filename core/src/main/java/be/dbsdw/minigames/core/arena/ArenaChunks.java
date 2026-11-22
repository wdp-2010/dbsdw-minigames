package be.dbsdw.minigames.core.arena;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/**
 * Keeps an arena's chunks loaded and ticking while a match runs on it (plugin chunk tickets).
 * Large arenas are wider than the simulation distance: without this, the far side stops ticking
 * when everyone is at one end, and entities there (flags, power-ups) go invalid and get
 * duplicated by games that respawn what they think is gone.
 */
public final class ArenaChunks {
    private ArenaChunks() {}

    /** Chunk coordinates {x, z} covering the arena's bounds (empty without bounds). */
    public static List<int[]> chunks(ArenaDefinition arena) {
        List<int[]> out = new ArrayList<>();
        if (!arena.hasBounds()) {
            return out;
        }
        int x1 = Math.min(arena.corner1().blockX(), arena.corner2().blockX()) >> 4;
        int x2 = Math.max(arena.corner1().blockX(), arena.corner2().blockX()) >> 4;
        int z1 = Math.min(arena.corner1().blockZ(), arena.corner2().blockZ()) >> 4;
        int z2 = Math.max(arena.corner1().blockZ(), arena.corner2().blockZ()) >> 4;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                out.add(new int[] {x, z});
            }
        }
        return out;
    }

    public static void hold(Plugin plugin, World world, ArenaDefinition arena) {
        for (int[] c : chunks(arena)) {
            world.addPluginChunkTicket(c[0], c[1], plugin);
        }
    }

    public static void release(Plugin plugin, World world, ArenaDefinition arena) {
        for (int[] c : chunks(arena)) {
            world.removePluginChunkTicket(c[0], c[1], plugin);
        }
    }
}
