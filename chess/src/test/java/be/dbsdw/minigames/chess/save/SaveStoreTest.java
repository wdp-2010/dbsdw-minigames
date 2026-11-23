package be.dbsdw.minigames.chess.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SaveStoreTest {
    private static final UUID ANN = new UUID(0, 1);
    private static final UUID BOB = new UUID(0, 2);
    private static final UUID CAT = new UUID(0, 3);

    private static SaveStore store(Path dir) {
        return new SaveStore(dir, Runnable::run, Logger.getAnonymousLogger());
    }

    private static SavedGame game(String id, UUID white, UUID black, long updated) {
        return new SavedGame(id, white, "w", black, "b", "chess:snel", List.of("e2e4", "e7e5"), 300_000, 3_000,
                250_000, 260_000, 1_000, updated);
    }

    @Test
    void savedGamesSurviveARestart(@TempDir Path dir) throws Exception {
        SaveStore s = store(dir);
        s.put(game("abc", ANN, BOB, 5_000));
        SaveStore again = store(dir);
        again.load();
        SavedGame g = again.get("abc").orElseThrow();
        assertEquals(List.of("e2e4", "e7e5"), g.moves());
        assertEquals(260_000, g.blackMillis());
        assertEquals(BOB, g.opponentOf(ANN));
        assertTrue(g.whiteToMove());
    }

    @Test
    void listsAPlayersGamesLastPlayedFirst(@TempDir Path dir) {
        SaveStore s = store(dir);
        s.put(game("old", ANN, BOB, 1_000));
        s.put(game("new", CAT, ANN, 9_000));
        s.put(game("other", BOB, CAT, 5_000));
        assertEquals(List.of("new", "old"), s.of(ANN).stream().map(SavedGame::id).toList());
        assertEquals(2, s.count(ANN));
    }

    @Test
    void deleteAndExpire(@TempDir Path dir) {
        SaveStore s = store(dir);
        s.put(game("a", ANN, BOB, 1_000));
        s.put(game("b", ANN, BOB, 50_000));
        assertEquals(List.of("a"), s.expire(60_000, 20_000).stream().map(SavedGame::id).toList());
        assertFalse(Files.exists(dir.resolve("a.yml")));
        s.delete("b");
        assertTrue(s.get("b").isEmpty());
        assertFalse(Files.exists(dir.resolve("b.yml")));
    }

    @Test
    void brokenFilesAreSkipped(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("bad.yml"), "white: nope\n");
        SaveStore s = store(dir);
        s.load();
        assertTrue(s.get("bad").isEmpty());
        assertEquals(8, s.newId().length());
    }
}
