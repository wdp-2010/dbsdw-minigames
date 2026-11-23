package be.dbsdw.minigames.chess.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Move generation against the well-known perft node counts (chessprogramming.org "Perft Results").
 * These positions cover castling through and out of check, en passant (including discovered checks),
 * promotions with capture and pins.
 */
class PerftTest {
    private static long perft(Position p, int depth) {
        if (depth == 0) {
            return 1;
        }
        long n = 0;
        for (Move m : p.legalMoves()) {
            n += depth == 1 ? 1 : perft(p.play(m), depth - 1);
        }
        return n;
    }

    @Test
    void startPosition() {
        Position p = Position.start();
        assertEquals(20, perft(p, 1));
        assertEquals(400, perft(p, 2));
        assertEquals(8_902, perft(p, 3));
        assertEquals(197_281, perft(p, 4));
    }

    @Test
    void kiwipete() {
        Position p = Position.fen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1");
        assertEquals(48, perft(p, 1));
        assertEquals(2_039, perft(p, 2));
        assertEquals(97_862, perft(p, 3));
    }

    @Test
    void position3EnPassantAndPins() {
        Position p = Position.fen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1");
        assertEquals(14, perft(p, 1));
        assertEquals(191, perft(p, 2));
        assertEquals(2_812, perft(p, 3));
        assertEquals(43_238, perft(p, 4));
    }

    @Test
    void position4Promotions() {
        Position p = Position.fen("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1");
        assertEquals(6, perft(p, 1));
        assertEquals(264, perft(p, 2));
        assertEquals(9_467, perft(p, 3));
    }

    @Test
    void position5() {
        Position p = Position.fen("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8");
        assertEquals(44, perft(p, 1));
        assertEquals(1_486, perft(p, 2));
        assertEquals(62_379, perft(p, 3));
    }

    @Test
    void position6() {
        Position p = Position.fen("r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10");
        assertEquals(46, perft(p, 1));
        assertEquals(2_079, perft(p, 2));
        assertEquals(89_890, perft(p, 3));
    }
}
