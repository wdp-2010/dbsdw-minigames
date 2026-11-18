package be.dbsdw.minigames.paintball.rules;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class GateAndSpreadTest {
    @Test
    void heldTriggerEvery4TicksFiresEvery5() {
        FireGate gate = new FireGate();
        List<Long> shots = new ArrayList<>();
        for (long t = 0; t < 200; t++) {
            if (t % 4 == 0 && gate.request(t, 5) == FireGate.Decision.FIRE) {
                shots.add(t);
            }
            if (gate.pollQueued(t, 5)) {
                shots.add(t);
            }
        }
        assertEquals(40, shots.size());
        for (int i = 1; i < shots.size(); i++) {
            assertEquals(5, shots.get(i) - shots.get(i - 1));
        }
    }

    @Test
    void twoPacketsInOneTickAreOnePull() {
        FireGate gate = new FireGate();
        assertEquals(FireGate.Decision.FIRE, gate.request(0, 5));
        assertEquals(FireGate.Decision.IGNORED, gate.request(0, 5));
        assertEquals(FireGate.Decision.QUEUED, gate.request(1, 5));
        assertEquals(FireGate.Decision.IGNORED, gate.request(2, 5));
    }

    @Test
    void spreadStaysInsideConeForAnyDirection() {
        SplittableRandom rng = new SplittableRandom(1);
        double[][] dirs = {{0, 0, 2}, {0, 1, 0}, {1, 0, 0}, {0.3, -0.5, 0.8}};
        double widest = 0;
        for (double[] d : dirs) {
            double len = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
            for (int i = 0; i < 5000; i++) {
                double[] r = Spread.apply(d[0], d[1], d[2], 1.0, rng);
                double rl = Math.sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2]);
                assertEquals(1, rl, 1e-9);
                double dot = (r[0] * d[0] + r[1] * d[1] + r[2] * d[2]) / len;
                double angle = Math.toDegrees(Math.acos(Math.min(1, dot)));
                assertTrue(angle <= 1 + 1e-6, "angle " + angle);
                widest = Math.max(widest, angle);
            }
        }
        assertTrue(widest > 0.5);
        assertArrayEquals(new double[] {0, 0, 1}, Spread.apply(0, 0, 2, 0, rng), 0);
    }

    @Test
    void clockRoundsUp() {
        assertEquals("10:00", Clock.format(12000));
        assertEquals("1:00", Clock.format(1199));
        assertEquals("0:02", Clock.format(21));
        assertEquals("0:00", Clock.format(-5));
    }
}
