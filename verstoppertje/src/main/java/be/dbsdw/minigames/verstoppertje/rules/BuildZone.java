package be.dbsdw.minigames.verstoppertje.rules;

import java.util.List;

/**
 * Where nobody may place or break blocks: around the seekers' spawns (so they cannot be walled in or
 * have the floor dug out from under them) and along the arena's sides (no digging through the edge or
 * building over it). Plain numbers, so it is testable without a server.
 */
public final class BuildZone {
    /** Blocks from a seekers' spawn (horizontally, and up to this far above or below) that stay as built. */
    public static final int SPAWN_RADIUS = 4;
    /** Blocks along each side of the arena that stay as built. */
    public static final int EDGE = 2;

    private BuildZone() {
    }

    /**
     * Whether the block at x, y, z is off limits. {@code spawns} holds block coordinates {x, y, z};
     * the bounds are the arena's, in block coordinates (min inclusive, max exclusive).
     */
    public static boolean blocked(int x, int y, int z, List<int[]> spawns,
                                  double minX, double minZ, double maxX, double maxZ) {
        double cx = x + 0.5;
        double cz = z + 0.5;
        if (cx - minX < EDGE || maxX - cx < EDGE || cz - minZ < EDGE || maxZ - cz < EDGE) {
            return true;
        }
        for (int[] s : spawns) {
            if (Math.abs(x - s[0]) <= SPAWN_RADIUS && Math.abs(z - s[2]) <= SPAWN_RADIUS
                    && Math.abs(y - s[1]) <= SPAWN_RADIUS) {
                return true;
            }
        }
        return false;
    }
}
