package be.dbsdw.minigames.core.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProgressBarTest {
    @Test
    void onlyWholeCellsFill() {
        assertEquals(0, ProgressBar.filled(0.0, 10));
        assertEquals(0, ProgressBar.filled(0.099, 10));
        assertEquals(1, ProgressBar.filled(0.1, 10));
        assertEquals(9, ProgressBar.filled(0.999, 10));
        assertEquals(10, ProgressBar.filled(1.0, 10));
    }

    @Test
    void outOfRangeFractionsAreClamped() {
        assertEquals(0, ProgressBar.filled(-3, 10));
        assertEquals(10, ProgressBar.filled(7, 10));
        assertEquals(0, ProgressBar.filled(Double.NaN, 10));
        assertEquals(0, ProgressBar.filled(0.5, 0));
    }

    @Test
    void theTextAlwaysHasTheFullWidth() {
        for (int n = 0; n <= 10; n++) {
            String bar = ProgressBar.mini(n / 10.0, 10);
            assertEquals(10, bar.chars().filter(c -> c == '■').count(), bar);
        }
        assertTrue(ProgressBar.mini(0.5, 10).startsWith("<#9CC05A>■■■■■</#9CC05A>"));
    }
}
