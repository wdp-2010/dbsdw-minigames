package be.dbsdw.minigames.paintball.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ScopeZoomTest {
    @Test
    void startsAtTheDefaultLevel() {
        assertEquals(ScopeZoom.DEFAULT_LEVEL, ScopeZoom.levelAfterScroll(ScopeZoom.DEFAULT_LEVEL, 0, 0));
        assertEquals(-0.6, ScopeZoom.slowdown(ScopeZoom.DEFAULT_LEVEL), 1e-9);
    }

    @Test
    void scrollUpZoomsInScrollDownZoomsOut() {
        int level = ScopeZoom.DEFAULT_LEVEL;
        assertEquals(level + 1, ScopeZoom.levelAfterScroll(level, 4, 3)); // wheel up: slot - 1
        assertEquals(level - 1, ScopeZoom.levelAfterScroll(level, 4, 5)); // wheel down: slot + 1
    }

    @Test
    void scrollWrapsAroundTheHotbarEnds() {
        int level = ScopeZoom.DEFAULT_LEVEL;
        assertEquals(level + 1, ScopeZoom.levelAfterScroll(level, 0, 8));
        assertEquals(level - 1, ScopeZoom.levelAfterScroll(level, 8, 0));
    }

    @Test
    void staysWithinTheLevels() {
        assertEquals(ScopeZoom.LEVELS - 1, ScopeZoom.levelAfterScroll(ScopeZoom.LEVELS - 1, 4, 3));
        assertEquals(0, ScopeZoom.levelAfterScroll(0, 4, 5));
    }

    @Test
    void isScrollOnlyForNeighbouringSlots() {
        assertEquals(true, ScopeZoom.isScroll(4, 3));
        assertEquals(true, ScopeZoom.isScroll(0, 8));
        assertEquals(false, ScopeZoom.isScroll(1, 5));
        assertEquals(false, ScopeZoom.isScroll(2, 2));
    }

    @Test
    void deeperLevelsSlowMore() {
        for (int level = 1; level < ScopeZoom.LEVELS; level++) {
            assertEquals(true, ScopeZoom.slowdown(level) < ScopeZoom.slowdown(level - 1));
        }
    }
}
