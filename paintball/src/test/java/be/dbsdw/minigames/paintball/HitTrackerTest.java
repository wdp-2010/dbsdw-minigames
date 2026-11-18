package be.dbsdw.minigames.paintball;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HitTrackerTest {
    @Test
    void outAfterThreeHits() {
        HitTracker t = new HitTracker(3);
        assertFalse(t.hit());
        assertFalse(t.hit());
        assertTrue(t.hit());
        assertTrue(t.isOut());
    }

    @Test
    void resetClearsHits() {
        HitTracker t = new HitTracker(1);
        t.hit();
        t.reset();
        assertFalse(t.isOut());
    }
}
