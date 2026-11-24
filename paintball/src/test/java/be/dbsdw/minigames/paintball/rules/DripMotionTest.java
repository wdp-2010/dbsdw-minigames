package be.dbsdw.minigames.paintball.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import be.dbsdw.minigames.paintball.rules.DripMotion.Frame;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class DripMotionTest {
    private static List<Frame> plan(double length, boolean landed, long seed) {
        return DripMotion.plan(length, landed, new SplittableRandom(seed));
    }

    private static double step(List<Frame> f, int i) {
        return f.get(i).distance() - f.get(i - 1).distance();
    }

    @Test
    void startsAtTheSplatAndEndsAtTheEndOfTheRun() {
        for (long seed = 0; seed < 50; seed++) {
            for (boolean landed : new boolean[] {true, false}) {
                List<Frame> f = plan(3.4, landed, seed);
                assertEquals(0, f.get(0).distance(), 1e-9);
                assertEquals(3.4, f.get(f.size() - 1).distance(), 1e-9);
            }
        }
    }

    @Test
    void neverRunsBackUpAndNeverTakesTooLong() {
        for (long seed = 0; seed < 50; seed++) {
            for (double length : new double[] {0.3, 2, 8}) {
                List<Frame> f = plan(length, seed % 2 == 0, seed);
                for (int i = 1; i < f.size(); i++) {
                    assertTrue(step(f, i) >= 0);
                }
                assertTrue(f.size() * DripMotion.STEP <= DripMotion.MAX_TICKS, length + ": " + f.size());
            }
        }
    }

    @Test
    void slowAboutABlockASecondAtMost() {
        for (long seed = 0; seed < 50; seed++) {
            List<Frame> f = plan(3, true, seed);
            for (int i = 1; i < f.size(); i++) {
                assertTrue(step(f, i) <= 0.06 * DripMotion.STEP, "step " + step(f, i));
            }
            assertTrue(f.size() * DripMotion.STEP >= 60, "3 blocks take at least 3 s");
        }
    }

    @Test
    void waitsAMomentThenGetsGoingGently() {
        List<Frame> f = plan(2, true, 7);
        assertEquals(0, f.get(1).distance(), 1e-9);
        int first = 1;
        while (f.get(first).distance() == 0) {
            first++;
        }
        assertTrue(step(f, first) < step(f, first + 4), "it speeds up first");
    }

    @Test
    void aDropThatRunsDryCreepsToAStop() {
        List<Frame> f = plan(1.5, false, 3);
        double fastest = 0;
        for (int i = 1; i < f.size(); i++) {
            fastest = Math.max(fastest, step(f, i));
        }
        int n = f.size();
        assertTrue(step(f, n - 2) < fastest / 3, "it slows right down before it stops");
    }

    @Test
    void aDropThatReachesTheFloorIsStillMoving() {
        List<Frame> f = plan(3, true, 11);
        int n = f.size();
        assertTrue(step(f, n - 2) > 0.01, "the last full step still moves"); // the last frame may be a partial step
    }
}
