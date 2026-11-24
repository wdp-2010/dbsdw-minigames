package be.dbsdw.minigames.paintball.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * How a drop of paint moves down a wall, as frames {@link #STEP} ticks apart (the client glides
 * between them). It sits still for a moment under the splat, gets going gently and then slows
 * down as it leaves its paint behind in the trail: a drop that reaches the floor is still moving
 * there, one that runs dry creeps to a stop. Slow, about a block a second at most.
 */
public final class DripMotion {
    /** Ticks between frames. */
    public static final int STEP = 2;
    /** A run never takes longer than this (ticks); a long one runs a little faster to make it. */
    public static final int MAX_TICKS = 300;

    /** Where the drop is: {@code distance} in blocks from the start. */
    public record Frame(double distance) {
    }

    private DripMotion() {
    }

    /**
     * Frames for a run of {@code length} blocks; {@code landed} runs reach the floor still moving,
     * the others slow to a stop at {@code length}. Frame 0 is the start; the run is over one STEP
     * after the last frame.
     */
    public static List<Frame> plan(double length, boolean landed, RandomGenerator rnd) {
        int wait = 6 + rnd.nextInt(10);
        // the distance at which the drop would have given all its paint to the trail
        double reach = length * (landed ? 1.6 + rnd.nextDouble() * 0.6 : 1.1);
        double top = 0.045 + rnd.nextDouble() * 0.015; // blocks per tick at the start, about 1 a second
        List<Frame> frames = run(length, landed, reach, top, wait);
        while (frames.size() * STEP > MAX_TICKS) {
            top *= 1.25;
            frames = run(length, landed, reach, top, wait);
        }
        return frames;
    }

    private static List<Frame> run(double length, boolean landed, double reach, double top, int wait) {
        List<Frame> frames = new ArrayList<>();
        for (int t = 0; t < wait; t += STEP) {
            frames.add(new Frame(0));
        }
        double s = 0;
        double v = 0;
        for (int t = 1; s < length; t++) {
            // its speed goes with the paint it has left, which it loses evenly along the way
            double target = top * Math.max(0, 1 - s / reach);
            v += (target - v) * 0.15;
            s = Math.min(length, s + v);
            if (!landed && length - s < 0.02) {
                s = length; // it has crept the last pixel and stops
            }
            if (t % STEP == 0 || s >= length) {
                frames.add(new Frame(s));
            }
        }
        return frames;
    }
}
