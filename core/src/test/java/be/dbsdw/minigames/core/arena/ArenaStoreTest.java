package be.dbsdw.minigames.core.arena;

import static be.dbsdw.minigames.core.arena.ArenaFixtures.pos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArenaStoreTest {
    @TempDir
    Path dir;

    private final Logger log = Logger.getLogger("ArenaStoreTest");

    private static ArenaDefinition full() {
        return ArenaFixtures.bounded("main")
                .withModes(Set.of("paintball:tdm", "paintball:ctf"))
                .withSpawn(0, new Pos(5.5, 65, 20.5, 90f, 10f)).withSpawn(0, pos(6, 65, 22))
                .withSpawn(1, new Pos(54.5, 65, 20.5, -90f, 0f))
                .withFlag(0, pos(2, 65, 20)).withFlag(1, pos(57, 65, 20))
                .withPowerup(pos(30, 66, 20)).withPowerup(pos(30, 66, 5)).withPowerup(pos(30, 66, 35));
    }

    @Test
    void saveThenLoadRoundTrips() throws IOException {
        ArenaStore store = new ArenaStore(dir, log);
        store.save(full());
        assertEquals(List.of(full()), store.loadAll(w -> true));
    }

    @Test
    void unknownWorldIsSkipped() throws IOException {
        ArenaStore store = new ArenaStore(dir, log);
        store.save(full());
        assertTrue(store.loadAll(w -> false).isEmpty());
    }

    @Test
    void brokenFileIsSkipped() throws IOException {
        ArenaStore store = new ArenaStore(dir, log);
        store.save(full());
        Files.writeString(dir.resolve("broken.yml"), "::: not yaml\n\t- [");
        assertEquals(List.of(full()), store.loadAll(w -> true));
    }

    @Test
    void deleteRemovesFile() throws IOException {
        ArenaStore store = new ArenaStore(dir, log);
        store.save(full());
        assertTrue(store.delete("main"));
        assertFalse(store.delete("main"));
        assertTrue(store.loadAll(w -> true).isEmpty());
    }
}
