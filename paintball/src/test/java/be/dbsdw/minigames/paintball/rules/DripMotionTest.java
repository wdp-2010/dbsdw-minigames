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
    void neverRunsBackUpAndTakesBoundedTime() {
        for (long seed = 0; seed < 50; seed++) {
            List<Frame> f = plan(8, seed % 2 == 0, seed);
            for (int i = 1; i < f.size(); i++) {
                assertTrue(f.get(i).distance() >= f.get(i - 1).distance());
            }
            assertTrue(f.size() * DripMotion.STEP <= DripMotion.MAX_TICKS + 40, "frames: " + f.size());
        }
    }

    @Test
    void gathersAndSwellsBeforeItMoves() {
        List<Frame> f = plan(2, true, 7);
        assertEquals(0, f.get(1).distance(), 1e-9);
        assertTrue(f.get(1).size() > f.get(0).size());
    }

    @Test
    void speedsUpFirstThenSlowsDownAsItLosesPaint() {
        List<Frame> f = plan(4, false, 3);
        int moving = 0;
        while (f.get(moving + 1).distance() == 0) {
            moving++;
        }
        double first = f.get(moving + 1).distance() - f.get(moving).distance();
        double best = 0;
        for (int i = moving + 1; i < f.size(); i++) {
            best = Math.max(best, f.get(i).distance() - f.get(i - 1).distance());
        }
        assertTrue(best > first, "it starts slower than its fastest");
        int n = f.size();
        double lastStep = f.get(n - 1).distance() - f.get(n - 2).distance();
        assertTrue(lastStep < best / 2, "a drop that dries up creeps to a stop");
    }

    @Test
    void aDropThatReachesTheFloorIsStillMovingAndSmallerThanAtTheStart() {
        List<Frame> f = plan(3, true, 11);
        int n = f.size();
        assertTrue(f.get(n - 1).distance() > f.get(n - 2).distance());
        assertTrue(f.get(n - 1).size() < 1);
        assertTrue(f.get(n - 1).size() >= 0.7);
    }

    @Test
    void sometimesSticksForAMomentOnALongRun() {
        int withStall = 0;
        for (long seed = 0; seed < 40; seed++) {
            List<Frame> f = plan(6, true, seed);
            for (int i = 1; i < f.size(); i++) {
                if (f.get(i).distance() > 0.3 && f.get(i).distance() == f.get(i - 1).distance()) {
                    withStall++;
                    break;
                }
            }
        }
        assertTrue(withStall > 10 && withStall < 40, "runs with a stall: " + withStall);
    }
}
