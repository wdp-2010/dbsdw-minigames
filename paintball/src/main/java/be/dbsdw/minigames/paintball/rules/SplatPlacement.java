package be.dbsdw.minigames.paintball.rules;

/**
 * Where a splat goes on the face a paintball hit, in face coordinates: (u, v) in [0, 1] is the
 * hit block's face, neighbouring faces in the same plane are cells (-1..1, -1..1) around it.
 * The splat is an axis-aligned square of half size {@code h} (at most 0.5) centred where the ball
 * hit. It may lie across the seam onto a neighbour's face, but only if that neighbour is a full,
 * uncovered face in the same plane; otherwise it is pushed back so it never hangs over an edge.
 */
public final class SplatPlacement {
    /** Whether the face of the cell (du, dv) next to the hit cell can carry paint. */
    public interface Cells {
        boolean paintable(int du, int dv);
    }

    private SplatPlacement() {}

    /** Returns {u, v} of the splat centre. The hit cell itself is assumed paintable. */
    public static double[] place(double u, double v, double h, Cells cells) {
        h = Math.min(h, 0.5);
        u = clamp(u, 0, 1);
        v = clamp(v, 0, 1);
        if (!cells.paintable(-1, 0)) {
            u = Math.max(u, h);
        }
        if (!cells.paintable(1, 0)) {
            u = Math.min(u, 1 - h);
        }
        if (!cells.paintable(0, -1)) {
            v = Math.max(v, h);
        }
        if (!cells.paintable(0, 1)) {
            v = Math.min(v, 1 - h);
        }
        for (int du = -1; du <= 1; du += 2) {
            for (int dv = -1; dv <= 1; dv += 2) {
                if (cells.paintable(du, dv) || !overlaps(u, h, du) || !overlaps(v, h, dv)) {
                    continue;
                }
                // over a corner into a cell that can't carry paint: move back along the shorter way
                double shiftU = du < 0 ? h - u : u - (1 - h);
                double shiftV = dv < 0 ? h - v : v - (1 - h);
                if (shiftU <= shiftV) {
                    u = du < 0 ? h : 1 - h;
                } else {
                    v = dv < 0 ? h : 1 - h;
                }
            }
        }
        return new double[] {u, v};
    }

    private static boolean overlaps(double c, double h, int d) {
        return d < 0 ? c - h < 0 : c + h > 1;
    }

    private static double clamp(double x, double lo, double hi) {
        return Math.max(lo, Math.min(hi, x));
    }
}
