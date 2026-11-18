package be.dbsdw.minigames.core.arena;

import static be.dbsdw.minigames.core.arena.ArenaFixtures.CTF;
import static be.dbsdw.minigames.core.arena.ArenaFixtures.TDM;
import static be.dbsdw.minigames.core.arena.ArenaFixtures.pos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ArenaPoolTest {
    @Test
    void reservedArenaIsNotOfferedAgain() {
        ArenaPool pool = new ArenaPool(List.of(ArenaFixtures.tdmArena("a")));
        Random rnd = new Random(1);
        assertTrue(pool.reserve(TDM, rnd).isPresent());
        assertTrue(pool.isBusy("a"));
        assertTrue(pool.reserve(TDM, rnd).isEmpty());
        pool.release("a");
        assertTrue(pool.reserve(TDM, rnd).isPresent());
    }

    @Test
    void onlyFittingArenasAreReserved() {
        ArenaDefinition ctfOnly = ArenaFixtures.bounded("c").withModes(Set.of(CTF.key()))
                .withSpawn(0, pos(5, 65, 5)).withSpawn(1, pos(50, 65, 5))
                .withFlag(0, pos(2, 65, 20)).withFlag(1, pos(57, 65, 20));
        ArenaPool pool = new ArenaPool(List.of(ctfOnly, ArenaFixtures.tdmArena("t")));
        Random rnd = new Random(1);
        for (int i = 0; i < 20; i++) {
            assertEquals("t", pool.reserve(TDM, rnd).orElseThrow().name());
            pool.release("t");
        }
    }

    @Test
    void anyFitsIgnoresBusy() {
        ArenaPool pool = new ArenaPool(List.of(ArenaFixtures.tdmArena("a")));
        pool.reserve(TDM, new Random(1));
        assertTrue(pool.anyFits(TDM));
        assertFalse(pool.anyFits(CTF));
    }
}
