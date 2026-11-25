package be.dbsdw.minigames.core.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LevelCurveTest {
    private final LevelCurve curve = new LevelCurve(100, 1.5, 50);

    @Test
    void levelOneNeedsNoXp() {
        assertEquals(0, curve.xpFor(1));
        assertEquals(0, curve.xpFor(0));
        assertEquals(1, curve.levelOf(0));
    }

    @Test
    void levelAndXpAreInverse() {
        for (int level = 1; level <= 50; level++) {
            assertEquals(level, curve.levelOf(curve.xpFor(level)), "level " + level);
            if (level > 1) {
                assertEquals(level - 1, curve.levelOf(curve.xpFor(level) - 1), "just below " + level);
            }
        }
    }

    @Test
    void eachLevelCostsMoreThanTheLast() {
        for (int level = 2; level < 50; level++) {
            assertTrue(curve.xpFor(level + 1) - curve.xpFor(level) >= curve.xpFor(level) - curve.xpFor(level - 1));
        }
    }

    @Test
    void levelStopsAtTheMax() {
        assertEquals(50, curve.levelOf(Long.MAX_VALUE));
        assertEquals(0, curve.xpToNext(Long.MAX_VALUE));
        assertEquals(1.0, curve.fraction(Long.MAX_VALUE));
    }

    @Test
    void xpToNextAndFractionTrackProgress() {
        long start = curve.xpFor(3);
        long next = curve.xpFor(4);
        assertEquals(next - start, curve.xpToNext(start));
        assertEquals(0.0, curve.fraction(start));
        assertEquals(1, curve.xpToNext(next - 1));
        assertTrue(curve.fraction(next - 1) < 1.0);
    }

    @Test
    void badSettingsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new LevelCurve(0, 1.5, 10));
        assertThrows(IllegalArgumentException.class, () -> new LevelCurve(100, 0.5, 10));
        assertThrows(IllegalArgumentException.class, () -> new LevelCurve(100, 1.5, 0));
    }
}
