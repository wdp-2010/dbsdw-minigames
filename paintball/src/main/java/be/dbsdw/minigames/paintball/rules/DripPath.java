package be.dbsdw.minigames.paintball.rules;

/**
 * How far paint runs down a wall under a splat. The run starts {@code start} blocks above the
 * bottom of its own wall block and goes down block by block: past every wall block with open air
 * in front of it, until it meets a floor in front of the wall (then it lands there and forms a
 * puddle) or the wall stops (a gap, an overhang, a slab or the edge of the map: it dries up there).
 * A run never gets longer than {@code max} blocks; a longer one dries up on the way.
 */
public final class DripPath {
    /** What is at a level below the start block, looking at the wall and the block in front of it. */
    public enum Cell {
        /** The wall goes on with nothing in front: the paint runs past. */
        WALL,
        /** A full floor block in front: its top is where the run ends and the puddle lies. */
        FLOOR,
        /** No wall, or something in front that paint can't lie on: the run dries up above it. */
        END
    }

    /** What is {@code k} levels (1, 2, ...) below the start block. */
    public interface Cells {
        Cell below(int k);
    }

    /** {@code length} in blocks from the start down; {@code level} is the floor's k when it landed. */
    public record Run(double length, boolean landed, int level) {
    }

    private DripPath() {
    }

    public static Run run(double start, double max, Cells cells) {
        double length = Math.max(0, Math.min(1, start));
        for (int k = 1; ; k++) {
            if (length > max) {
                return new Run(max, false, 0);
            }
            switch (cells.below(k)) {
                case FLOOR -> {
                    return new Run(length, true, k);
                }
                case END -> {
                    return new Run(length, false, 0);
                }
                case WALL -> length += 1;
            }
        }
    }
}
