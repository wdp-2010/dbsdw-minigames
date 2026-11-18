package be.dbsdw.minigames.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ModeSpecTest {
    @Test
    void rejectsOneTeam() {
        assertThrows(IllegalArgumentException.class, () -> new ModeSpec("g", "m", "M", 1, 1, 2, Set.of()));
    }

    @Test
    void rejectsMinAboveMax() {
        assertThrows(IllegalArgumentException.class, () -> new ModeSpec("g", "m", "M", 2, 3, 2, Set.of()));
    }

    @Test
    void rejectsBadId() {
        assertThrows(IllegalArgumentException.class, () -> new ModeSpec("paintball", "TDM", "TDM", 2, 2, 8, Set.of()));
    }

    @Test
    void keyAndCounts() {
        ModeSpec mode = new ModeSpec("paintball", "tdm", "TDM", 2, 2, 8, Set.of());
        assertEquals("paintball:tdm", mode.key());
        assertEquals(4, mode.startMinimum());
        assertEquals(16, mode.maximum());
    }
}
