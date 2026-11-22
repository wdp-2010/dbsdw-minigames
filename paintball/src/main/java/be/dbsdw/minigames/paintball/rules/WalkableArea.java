package be.dbsdw.minigames.paintball.rules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Every floor position a player can walk to from the team spawns, found by a flood fill: a step goes
 * to a neighbouring column, up at most one block (with head room to jump) or down at most three.
 * Power-ups only spawn on these positions, so never on a roof, inside a wall or in a closed room.
 */
public final class WalkableArea {
    /** Block queries; {@code open} means a player can stand in it (passable, not a liquid). */
    public interface Blocks {
        boolean solid(int x, int y, int z);

        boolean open(int x, int y, int z);
    }

    /** A floor position: the block a player's feet are in. */
    public record Cell(int x, int y, int z) {
        public double distanceSquared(double px, double py, double pz) {
            double dx = x + 0.5 - px;
            double dy = y - py;
            double dz = z + 0.5 - pz;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[] STEPS = {0, 1, -1, -2, -3};

    private WalkableArea() {
    }

    /**
     * @param min inclusive lower corner {x, y, z}
     * @param max exclusive upper corner {x, y, z}
     * @param starts spawn positions; each is moved down onto the floor below it (at most 3 blocks)
     * @param limit stop after this many cells (a safety net for huge arenas)
     */
    public static List<Cell> flood(Blocks blocks, int[] min, int[] max, List<Cell> starts, int limit) {
        Set<Cell> seen = new HashSet<>();
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        for (Cell s : starts) {
            for (int down = 0; down <= 3; down++) {
                Cell c = new Cell(s.x(), s.y() - down, s.z());
                if (inside(c, min, max) && standable(blocks, c)) {
                    if (seen.add(c)) {
                        queue.add(c);
                    }
                    break;
                }
            }
        }
        List<Cell> out = new ArrayList<>();
        while (!queue.isEmpty() && out.size() < limit) {
            Cell c = queue.poll();
            out.add(c);
            for (int[] d : DIRECTIONS) {
                for (int dy : STEPS) {
                    Cell n = new Cell(c.x() + d[0], c.y() + dy, c.z() + d[1]);
                    if (!inside(n, min, max) || !standable(blocks, n)) {
                        continue;
                    }
                    if (dy > 0 && !blocks.open(c.x(), c.y() + 2, c.z())) {
                        continue; // no head room to jump up
                    }
                    if (dy < 0 && !(blocks.open(n.x(), c.y(), n.z()) && blocks.open(n.x(), c.y() + 1, n.z()))) {
                        continue; // a wall between here and the drop
                    }
                    if (seen.add(n)) {
                        queue.add(n);
                    }
                    break; // the first floor found in this column is the one a player reaches
                }
            }
        }
        return out;
    }

    static boolean standable(Blocks b, Cell c) {
        return b.solid(c.x(), c.y() - 1, c.z()) && b.open(c.x(), c.y(), c.z()) && b.open(c.x(), c.y() + 1, c.z());
    }

    private static boolean inside(Cell c, int[] min, int[] max) {
        return c.x() >= min[0] && c.x() < max[0] && c.y() >= min[1] && c.y() < max[1]
                && c.z() >= min[2] && c.z() < max[2];
    }
}
