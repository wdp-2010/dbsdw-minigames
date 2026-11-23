package be.dbsdw.minigames.core.arena;

import static be.dbsdw.minigames.core.arena.ArenaFixtures.TDM;
import static be.dbsdw.minigames.core.arena.ArenaFixtures.tdmArena;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArenaCopiesTest {
    @Test
    void copiesSitInARowEastOnChunkSteps() {
        // 60 blocks wide + 32 gap = 92 -> 96 when the spacing asks for less; the spacing otherwise.
        ArenaDefinition template = tdmArena("woud").withCopies(3);
        assertEquals(96, ArenaCopies.step(template, 50));
        assertEquals(512, ArenaCopies.step(template, ArenaCopies.DEFAULT_SPACING));
        assertEquals(208, ArenaCopies.step(template, 200));
        List<ArenaDefinition> copies = ArenaCopies.of(template, 50);
        assertEquals(List.of("woud#1", "woud#2", "woud#3"), copies.stream().map(ArenaDefinition::name).toList());
        ArenaDefinition second = copies.get(1);
        assertEquals(192, second.corner1().blockX());
        assertEquals(192 + 5, second.spawns().get(0).getFirst().x(), 1e-9);
        assertEquals(20, second.spawns().get(0).getFirst().z(), 1e-9);
        assertEquals(0, second.copies(), "a copy is a plain arena");
        assertEquals(192, ArenaCopies.offsetX(template, second));
        assertTrue(ArenaCopies.of(tdmArena("plain"), 512).isEmpty());
        assertEquals("woud", ArenaCopies.templateName("woud#2"));
        assertThrows(IllegalArgumentException.class, () -> tdmArena("x").withCopies(ArenaDefinition.MAX_COPIES + 1));
    }

    @Test
    void poolHandsOutFreeCopiesOfATemplate() {
        ArenaPool pool = new ArenaPool(List.of(tdmArena("woud").withCopies(2)));
        Random rnd = new Random(1);
        Optional<ArenaDefinition> a = pool.reserve(TDM, rnd);
        Optional<ArenaDefinition> b = pool.reserve("woud", TDM);
        assertEquals("woud#1", a.orElseThrow().name());
        assertEquals("woud#2", b.orElseThrow().name());
        assertTrue(pool.reserve(TDM, rnd).isEmpty(), "both copies in use");
        assertTrue(pool.isBusy("woud"));
        assertEquals(2, pool.busyCount("woud"));
        assertEquals("woud", pool.templateOf("woud#1").orElseThrow().name());
        assertTrue(pool.templateOf("woud").isEmpty());
        pool.release("woud#1");
        assertEquals("woud#1", pool.reserve(TDM, rnd).orElseThrow().name());
        assertEquals(List.of("woud"), pool.all().stream().map(ArenaDefinition::name).toList(),
                "the pool lists maps, not copies");
    }

    @Test
    void templatesAndPlainArenasRotate() {
        ArenaPool pool = new ArenaPool(List.of(tdmArena("woud").withCopies(2), tdmArena("duin")));
        Random rnd = new Random(7);
        String first = ArenaCopies.templateName(pool.reserve(TDM, rnd).orElseThrow().name());
        String second = ArenaCopies.templateName(pool.reserve(TDM, rnd).orElseThrow().name());
        assertFalse(first.equals(second), "the map not used yet comes next");
    }

    @Test
    void storeKeepsTheCopyCount(@TempDir Path dir) throws Exception {
        ArenaStore store = new ArenaStore(dir, Logger.getAnonymousLogger());
        store.save(tdmArena("woud").withCopies(5));
        store.save(tdmArena("duin"));
        assertTrue(Files.readString(dir.resolve("woud.yml")).contains("copies: 5"));
        assertFalse(Files.readString(dir.resolve("duin.yml")).contains("copies"));
        List<ArenaDefinition> loaded = store.loadAll(w -> true);
        assertEquals(5, loaded.stream().filter(a -> a.name().equals("woud")).findFirst().orElseThrow().copies());
    }
}
