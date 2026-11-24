package be.dbsdw.minigames.paintball.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * How a drop of paint moves down a wall, as frames {@link #STEP} ticks apart (the client glides
 * between them). Paint only runs where it is thicker than its yield stress holds, so:
 * <ul>
 *   <li>first the drop gathers at the bottom of the splat and swells, without moving;</li>
 *   <li>then it gets going (it speeds up, it does not start at full speed);</li>
 *   <li>it leaves paint behind in its trail, so it gets smaller and slower the further it runs;
 *       a drop that does not reach the floor creeps to a stop where it runs out;</li>
 *   <li>now and then it sticks on the rough wall for a moment and then breaks loose again.</li>
 * </ul>
 */
public final class DripMotion {
    /** Ticks between frames. */
    public static final int STEP = 2;
    /** A run never takes longer than this (ticks), stalls included. */
    public static final int MAX_TICKS = 260;

    /** Where the drop is ({@code distance} in blocks from the start) and its size (0..1). */
    public record Frame(double distance, double size) {
    }

    private DripMotion() {
    }

    /**
     * Frames for a run of {@code length} blocks; {@code landed} runs reach the floor still moving,
     * the others slow to a stop at {@code length}. Frame 0 is the start; the run is over one STEP
     * after the last frame.
     */
    public static List<Frame> plan(double length, boolean landed, RandomGenerator rnd) {
        List<Frame> frames = new ArrayList<>();
        int gather = 6 + rnd.nextInt(10);
        for (int t = 0; t < gather; t += STEP) {
            frames.add(new Frame(0, 0.45 + 0.55 * t / gather));
        }
        // the distance at which the drop would have given all its paint to the trail
        double reach = landed ? length * (1.4 + rnd.nextDouble() * 0.6) : length;
        double top = 0.10 + rnd.nextDouble() * 0.04; // blocks per tick at full size, about 2-3 blocks a second
        double s = 0;
        double v = 0;
        int stuck = 0;
        for (int t = 1; s < length && t <= MAX_TICKS; t++) {
            double left = Math.max(0, 1 - s / reach);
            if (stuck > 0) {
                stuck--;
                v = 0;
            } else {
                // its speed goes with the paint it has left, which it loses evenly along the way
                double target = top * left;
                v += (target - v) * 0.3;
                s = Math.min(length, s + v);
                if (!landed && length - s < 0.03) {
                    s = length; // it has crept the last few pixels and stops
                }
                if (s > 0.3 && s < length - 0.2 && rnd.nextDouble() < 0.03) {
                    stuck = 3 + rnd.nextInt(8);
                }
            }
            if (t % STEP == 0 || s >= length) {
                frames.add(new Frame(s, size(1 - s / reach)));
            }
        }
        Frame last = frames.get(frames.size() - 1);
        if (last.distance() < length) {
            frames.add(new Frame(length, size(1 - length / reach)));
        }
        return frames;
    }

    /** The drop shrinks as it gives paint to its trail, but never below 70 %. */
    private static double size(double left) {
        return 0.7 + 0.3 * Math.max(0, Math.min(1, left));
    }
}
