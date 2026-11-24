package be.dbsdw.minigames.paintball.rules;

import java.util.List;
import java.util.function.DoubleFunction;

/**
 * Which part of a partial block (stairs, slab, wall, ...) a paintball hit, so the splat stays on
 * that part instead of covering the whole block's outline (a stair's side is an L, not a square).
 * The block's shape is a list of boxes in block coordinates (0..1): {minX, minY, minZ, maxX, maxY,
 * maxZ}. The hit face is on {@code axis} (0 x, 1 y, 2 z) on the {@code n} side (+1 or -1); the face
 * coordinates are {@code ua} and {@code va}.
 */
public final class PartialFace {
    /** A face part: its plane (block coordinates along the axis) and its rectangle in (u, v). */
    public record Part(double plane, double loU, double loV, double hiU, double hiV) {
        public double room() {
            return Math.min(hiU - loU, hiV - loV);
        }
    }

    private static final double SLACK = 0.03;

    private PartialFace() {
    }

    /**
     * The box face the ball hit: the one whose rectangle contains the ball's path where it crosses
     * that face's plane (the outermost such face if more do), else the nearest one. Heights are cut
     * at 1: fences and walls collide higher than they look. {@code hitAt} gives the (u, v) where the
     * ball's path crosses a plane. Null when the shape is empty.
     */
    public static Part pick(List<double[]> boxes, int axis, int n, int ua, int va, DoubleFunction<double[]> hitAt) {
        Part best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (double[] b : boxes) {
            double[] min = {b[0], b[1], b[2]};
            double[] max = {b[3], Math.min(1, b[4]), b[5]};
            if (max[1] <= min[1]) {
                continue;
            }
            double plane = n > 0 ? max[axis] : min[axis];
            Part part = new Part(plane, min[ua], min[va], max[ua], max[va]);
            double[] hit = hitAt.apply(plane);
            double off = Math.max(gap(hit[0], part.loU(), part.hiU()), gap(hit[1], part.loV(), part.hiV()));
            // inside beats outside; among the inside ones the outermost face; else the nearest
            double score = off <= SLACK ? 10 + n * plane : -off;
            if (score > bestScore) {
                bestScore = score;
                best = part;
            }
        }
        return best;
    }

    private static double gap(double x, double lo, double hi) {
        return x < lo ? lo - x : x > hi ? x - hi : 0;
    }
}
