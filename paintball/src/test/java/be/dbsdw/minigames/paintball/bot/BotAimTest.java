package be.dbsdw.minigames.paintball.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class BotAimTest {
    private static final double SPEED = 3.0;
    private static final double GRAVITY = 0.5;

    /** Flies a paintball the way BotAim models it and returns its height at the target's distance. */
    private static double heightAt(BotAim.Solution s, double distance) {
        double pitch = Math.asin(s.y());
        return BotAim.fly(pitch, SPEED, GRAVITY, distance, 60).height();
    }

    @Test
    void farTargetIsAimedAbove() {
        BotAim.Solution s = BotAim.solve(new double[] {0, 0, 0}, new double[] {40, 0, 0}, new double[3], SPEED, GRAVITY,
                60, false).orElseThrow();
        assertTrue(s.y() > 0, "aims up to make up for the drop");
        assertEquals(0, heightAt(s, 40), 0.05);
        assertEquals(1, s.x(), 0.01);
    }

    @Test
    void hitsTargetAboveAndToTheSide() {
        BotAim.Solution s = BotAim.solve(new double[] {0, 0, 0}, new double[] {15, 4, 20}, new double[3], SPEED,
                GRAVITY, 60, false).orElseThrow();
        assertEquals(4, heightAt(s, 25), 0.05);
        assertEquals(15.0 / 20.0, s.x() / s.z(), 0.001);
    }

    @Test
    void leadsAMovingTarget() {
        double[] velocity = {0, 0, 0.25};
        BotAim.Solution s = BotAim.solve(new double[] {0, 0, 0}, new double[] {30, 0, 0}, velocity, SPEED, GRAVITY,
                60, true).orElseThrow();
        double z = s.z() / s.x() * 30;
        assertEquals(0.25 * s.ticks(), z, 0.3);
        assertTrue(z > 1);
    }

    @Test
    void outOfReachIsEmpty() {
        Optional<BotAim.Solution> s = BotAim.solve(new double[] {0, 0, 0}, new double[] {500, 0, 0}, new double[3],
                SPEED, GRAVITY, 60, false);
        assertTrue(s.isEmpty());
    }
}
