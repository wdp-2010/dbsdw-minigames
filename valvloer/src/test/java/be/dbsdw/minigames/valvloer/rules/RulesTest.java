package be.dbsdw.minigames.valvloer.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RulesTest {
    @Test
    void blockKeyRoundTripsNegativeCoordinates() {
        for (int[] p : new int[][] {{0, 0, 0}, {-1, -64, -1}, {500, -28, -50}, {-30000000, 319, 29999999}}) {
            long k = BlockKey.of(p[0], p[1], p[2]);
            assertEquals(p[0], BlockKey.x(k));
            assertEquals(p[1], BlockKey.y(k));
            assertEquals(p[2], BlockKey.z(k));
        }
    }

    @Test
    void footprintIsTheBlocksUnderTheHitboxCorners() {
        // standing in the middle of a block: one block under the feet
        assertEquals(List.of(BlockKey.of(10, -50, 5)), Footprint.under(10.5, -49.0, 5.5));
        // on a corner: four blocks
        assertEquals(4, Footprint.under(10.0, -49.0, 5.0).size());
        // negative coordinates round down
        assertEquals(List.of(BlockKey.of(-3, -50, -8)), Footprint.under(-2.5, -49.0, -7.5));
    }

    @Test
    void noFootprintInTheAir() {
        assertEquals(List.of(), Footprint.under(10.5, -48.6, 5.5));
        assertEquals(List.of(), Footprint.under(10.5, -49.3, 5.5));
        assertEquals(1, Footprint.under(10.5, -48.95, 5.5).size(), "just above the block still counts");
    }

    @Test
    void blocksDropAfterTheDelayInOrder() {
        FloorDecay d = new FloorDecay(8);
        assertTrue(d.touch(1, 100));
        assertFalse(d.touch(1, 101), "only the first step counts");
        assertTrue(d.touch(2, 103));
        assertEquals(List.of(), d.due(107));
        assertEquals(List.of(1L), d.due(108));
        assertEquals(List.of(2L), d.due(200));
        assertTrue(d.marked(1));
        assertEquals(List.of(), d.due(300));
    }

    @Test
    void crackingGrowsUntilTheBlockDrops() {
        FloorDecay d = new FloorDecay(8);
        d.touch(1, 100);
        d.touch(2, 104);
        List<FloorDecay.Crack> at104 = d.cracking(104);
        assertEquals(2, at104.size());
        assertEquals(0.5f, at104.get(0).progress(), 1e-6);
        assertEquals(0.05f, at104.get(1).progress(), 1e-6, "a fresh step already shows a little crack");
        d.due(108);
        assertEquals(List.of(new FloorDecay.Crack(2, 0.5f)), d.cracking(108));
        assertEquals(1f, new FloorDecay(0).cracking(0).stream().findFirst().map(FloorDecay.Crack::progress).orElse(1f), 1e-6);
    }

    @Test
    void lastTeamOnTheFloorWins() {
        UUID a = new UUID(0, 1);
        UUID b = new UUID(0, 2);
        UUID c = new UUID(0, 3);
        Map<UUID, Integer> teams = new LinkedHashMap<>();
        teams.put(a, 0);
        teams.put(b, 1);
        teams.put(c, 1);
        Survivors s = new Survivors(2, teams);
        assertEquals(OptionalInt.of(3), s.fall(b), "first one out is last place");
        assertEquals(new Survivors.Result.Running(), s.result());
        assertEquals(OptionalInt.empty(), s.fall(b));
        assertEquals(OptionalInt.of(2), s.fall(a));
        assertEquals(new Survivors.Result.Win(1), s.result());
    }

    @Test
    void everyoneFallingTogetherIsADraw() {
        UUID a = new UUID(0, 1);
        UUID b = new UUID(0, 2);
        Survivors s = new Survivors(2, Map.of(a, 0, b, 1));
        s.fall(a);
        s.fall(b);
        assertEquals(new Survivors.Result.Draw(), s.result());
    }

    @Test
    void timeLimitGoesToTheBiggerTeam() {
        UUID a = new UUID(0, 1);
        UUID b = new UUID(0, 2);
        UUID c = new UUID(0, 3);
        Survivors s = new Survivors(2, Map.of(a, 0, b, 1, c, 1));
        assertEquals(new Survivors.Result.Win(1), s.atTimeLimit());
        s.remove(c);
        assertEquals(new Survivors.Result.Draw(), s.atTimeLimit());
    }

    @Test
    void snapshotRoundTripsThroughText() {
        Map<Long, String> blocks = new LinkedHashMap<>();
        blocks.put(BlockKey.of(500, -49, -20), "minecraft:oak_planks");
        blocks.put(BlockKey.of(-3, -28, 7), "minecraft:stripped_birch_wood[axis=x]");
        Snapshot s = new Snapshot("world", blocks);
        Snapshot back = Snapshot.parse(s.toLines());
        assertEquals("world", back.world());
        assertEquals(blocks, back.blocks());
        assertEquals(-49, back.lowestY());
        assertThrows(IllegalArgumentException.class, () -> Snapshot.parse(List.of("1 2 3 stone")));
        assertThrows(IllegalArgumentException.class, () -> Snapshot.parse(List.of("world w", "1 x 3 stone")));
    }
}
