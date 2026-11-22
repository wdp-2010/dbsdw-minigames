package be.dbsdw.minigames.paintball.rules;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RangeBoxTest {
    private final RangeBox box = RangeBox.of(8, -63, -9, -8, -55, -39);

    @Test
    void containsBlocksBetweenTheCornersInclusive() {
        assertTrue(box.contains(0.5, -62, -20.5));
        assertTrue(box.contains(-8.0, -63, -39.0));
        assertTrue(box.contains(8.99, -55.5, -8.01)); // inside the corner blocks
    }

    @Test
    void outsideTheCornerBlocksIsOut() {
        assertFalse(box.contains(9.0, -62, -20));
        assertFalse(box.contains(0, -62, -8.0));
        assertFalse(box.contains(0, -54, -20));
        assertFalse(box.contains(-8.01, -62, -20));
    }
}
