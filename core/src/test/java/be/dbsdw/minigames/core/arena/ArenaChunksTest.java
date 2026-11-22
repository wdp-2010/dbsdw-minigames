package be.dbsdw.minigames.core.arena;

import static be.dbsdw.minigames.core.arena.ArenaFixtures.bounded;
import static be.dbsdw.minigames.core.arena.ArenaFixtures.pos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ArenaChunksTest {
    @Test
    void coversEveryChunkOfTheBounds() {
        // (0..59, 0..39): chunks x 0..3, z 0..2
        List<int[]> chunks = ArenaChunks.chunks(bounded("a"));
        assertEquals(12, chunks.size());
    }

    @Test
    void handlesNegativeCoordinatesAndSwappedCorners() {
        ArenaDefinition a = ArenaDefinition.empty("w", "world")
                .withCorner(1, pos(449, -24, 49)).withCorner(2, pos(300, -64, -50));
        List<int[]> chunks = ArenaChunks.chunks(a);
        // x 300..449 -> chunks 18..28 (11), z -50..49 -> chunks -4..3 (8)
        assertEquals(88, chunks.size());
        assertTrue(chunks.stream().anyMatch(c -> c[0] == 18 && c[1] == -4));
        assertTrue(chunks.stream().anyMatch(c -> c[0] == 28 && c[1] == 3));
    }

    @Test
    void noBoundsNoChunks() {
        assertTrue(ArenaChunks.chunks(ArenaDefinition.empty("x", "world")).isEmpty());
    }
}
