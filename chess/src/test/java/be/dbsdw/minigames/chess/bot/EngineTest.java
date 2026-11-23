package be.dbsdw.minigames.chess.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import be.dbsdw.minigames.chess.rules.Move;
import be.dbsdw.minigames.chess.rules.Position;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class EngineTest {
    private static final Engine.Limits STRONG = new Engine.Limits(4, 10_000, 0, 0);

    private static Engine.Result think(String fen, Engine.Limits limits) {
        Position p = Position.fen(fen);
        return new Engine(List.of(Engine.hash(p))).think(p, limits, new Random(7));
    }

    @Test
    void findsMateInOne() {
        // Back-rank mate: Ra8#.
        Engine.Result r = think("6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1", STRONG);
        assertEquals("a1a8", r.move().uci());
        assertTrue(r.score() > Engine.MATE - 10);
    }

    @Test
    void findsMateInOneForBlack() {
        Engine.Result r = think("r5k1/8/8/8/8/8/5PPP/6K1 b - - 0 1", STRONG);
        assertEquals("a8a1", r.move().uci());
    }

    @Test
    void findsMateInTwo() {
        // 1. Rd8+ Rxd8 2. Rxd8#.
        Engine.Result r = think("2r3k1/5ppp/8/8/8/8/3R1PPP/3R2K1 w - - 0 1", STRONG);
        assertTrue(r.score() > Engine.MATE - 10, "score " + r.score());
        Position after = Position.fen("2r3k1/5ppp/8/8/8/8/3R1PPP/3R2K1 w - - 0 1").play(r.move());
        assertTrue(after.inCheck());
    }

    @Test
    void takesAHangingQueen() {
        Engine.Result r = think("4k3/8/8/3q4/8/8/3R4/4K3 w - - 0 1", STRONG);
        assertEquals("d2d5", r.move().uci());
    }

    @Test
    void doesNotTakeAPoisonedPawn() {
        // Qxd5 wins a pawn but the e6 pawn takes the queen back.
        Engine.Result r = think("4k3/8/4p3/3p4/8/8/8/3QK3 w - - 0 1", STRONG);
        assertTrue(!r.move().uci().equals("d1d5"), "took a guarded pawn with the queen");
    }

    @Test
    void escapesCheck() {
        Position p = Position.fen("4k3/8/8/8/8/8/4q3/4K3 w - - 0 1");
        Engine.Result r = new Engine(List.of(Engine.hash(p))).think(p, STRONG, new Random(1));
        assertEquals("e1e2", r.move().uci());
    }

    @Test
    void everyLevelPlaysLegalMovesForAWholeGame() {
        Random rnd = new Random(3);
        for (BotLevel level : BotLevel.values()) {
            Position p = Position.start();
            java.util.List<Long> hashes = new java.util.ArrayList<>(List.of(Engine.hash(p)));
            for (int ply = 0; ply < 16 && !p.legalMoves().isEmpty(); ply++) {
                Engine.Limits limits = level.limitsFor(0, 0, ply);
                Engine.Limits quick = new Engine.Limits(Math.min(limits.maxDepth(), 3), 200, limits.noise(),
                        limits.randomChance());
                Move m = new Engine(hashes).think(p, quick, rnd).move();
                assertTrue(p.legalMoves().contains(m), level + " played " + m);
                p = p.play(m);
                hashes.add(Engine.hash(p));
            }
        }
    }

    @Test
    void evaluationIsSymmetric() {
        Position white = Position.fen("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3");
        Position black = Position.fen("rnbqkb1r/pppp1ppp/5n2/4p3/4P3/2N5/PPPP1PPP/R1BQKBNR b KQkq - 2 3");
        assertEquals(Engine.evaluate(white), Engine.evaluate(black));
    }

    @Test
    void timeLimitIsKept() {
        long start = System.nanoTime();
        Position p = Position.start();
        new Engine(List.of(Engine.hash(p))).think(p, new Engine.Limits(30, 300, 0, 0), new Random(1));
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertTrue(ms < 1500, "took " + ms + " ms");
    }
}
