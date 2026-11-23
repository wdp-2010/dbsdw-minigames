package be.dbsdw.minigames.chess.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class GameRecordTest {
    @Test
    void foolsMateIsCheckmate() {
        GameRecord g = GameRecord.replay(List.of("f2f3", "e7e5", "g2g4", "d8h4"));
        assertEquals(GameRecord.Status.CHECKMATE, g.status());
        assertTrue(g.position().whiteToMove(), "white is mated, so white is to move");
        assertTrue(g.over());
        assertFalse(g.play(Move.parse("a2a3")), "no moves after the game is over");
    }

    @Test
    void illegalMovesAreRefused() {
        GameRecord g = new GameRecord();
        assertFalse(g.play(Move.parse("e2e5")));
        assertFalse(g.play(Move.parse("e7e5")), "black cannot move first");
        assertTrue(g.play(Move.parse("e2e4")));
        assertThrows(IllegalArgumentException.class, () -> GameRecord.replay(List.of("e2e4", "e2e4")));
    }

    @Test
    void threefoldRepetitionDraws() {
        GameRecord g = GameRecord.replay(List.of("g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1"));
        assertEquals(GameRecord.Status.ONGOING, g.status());
        assertTrue(g.play(Move.parse("f6g8")));
        assertEquals(GameRecord.Status.THREEFOLD_REPETITION, g.status());
        assertTrue(g.status().draw());
    }

    @Test
    void stalemate() {
        Position p = Position.fen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1");
        assertTrue(p.legalMoves().isEmpty());
        assertFalse(p.inCheck());
    }

    @Test
    void castlingMovesTheRook() {
        Position p = Position.fen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        Position castled = p.play(Move.parse("e1g1"));
        assertEquals(Piece.KING, castled.at(Move.square("g1")));
        assertEquals(Piece.ROOK, castled.at(Move.square("f1")));
        assertEquals(0, castled.at(Move.square("h1")));
        Position.MoveEffect e = p.effect(Move.parse("e1c1"));
        assertEquals(Move.square("a1"), e.rookFrom());
        assertEquals(Move.square("d1"), e.rookTo());
        assertTrue(castled.fen().startsWith("r3k2r/8/8/8/8/8/8/R4RK1 b kq"));
    }

    @Test
    void noCastlingThroughCheck() {
        Position p = Position.fen("4k3/8/8/8/8/8/5r2/R3K2R w KQ - 0 1");
        assertFalse(p.legalMoves().contains(Move.parse("e1g1")), "f1 is attacked");
    }

    @Test
    void enPassantCapturesThePassedPawn() {
        GameRecord g = GameRecord.replay(List.of("e2e4", "a7a6", "e4e5", "d7d5"));
        Move ep = Move.parse("e5d6");
        assertTrue(g.legalMoves().contains(ep));
        assertEquals(Move.square("d5"), g.position().effect(ep).capturedSquare());
        g.play(ep);
        assertEquals(0, g.position().at(Move.square("d5")));
    }

    @Test
    void promotionOffersFourPieces() {
        Position p = Position.fen("8/P6k/8/8/8/8/8/K7 w - - 0 1");
        assertEquals(4, p.legalMovesFrom(Move.square("a7")).size());
        assertEquals(Piece.KNIGHT, p.play(Move.parse("a7a8n")).at(Move.square("a8")));
    }

    @Test
    void insufficientMaterial() {
        assertTrue(Position.fen("8/8/4k3/8/8/2K5/8/8 w - - 0 1").insufficientMaterial());
        assertTrue(Position.fen("8/8/4k3/8/8/2KN4/8/8 w - - 0 1").insufficientMaterial());
        assertTrue(Position.fen("8/8/4kb2/8/8/2K1B3/8/8 w - - 0 1").insufficientMaterial(), "same-colour bishops");
        assertFalse(Position.fen("8/8/4kb2/8/8/2KB4/8/8 w - - 0 1").insufficientMaterial(), "opposite bishops");
        assertFalse(Position.fen("8/8/4k3/8/8/2KNN3/8/8 w - - 0 1").insufficientMaterial());
        assertFalse(Position.fen("8/8/4k3/8/8/2K5/P7/8 w - - 0 1").insufficientMaterial());
    }

    @Test
    void cannotMateDecidesDrawOnTime() {
        Position p = Position.fen("8/8/4k3/8/8/2KN4/8/8 w - - 0 1");
        assertTrue(p.cannotMate(true));
        assertTrue(p.cannotMate(false));
        assertFalse(Position.fen("8/8/4k3/8/8/2KR4/8/8 w - - 0 1").cannotMate(true));
    }

    @Test
    void fiftyMoveRule() {
        GameRecord g = new GameRecord();
        String[] cycle = {"g1f3", "g8f6", "f3g5", "f6g4", "g5f3", "g4f6", "b1c3", "b8c6", "c3b5", "c6b4",
                "b5c3", "b4c6"};
        // Knight shuffles without repeating a position three times are hard to script; just check the
        // counter is reset by a pawn move and grows otherwise.
        for (String m : cycle) {
            assertTrue(g.play(Move.parse(m)), m);
        }
        assertEquals(cycle.length, g.position().halfmoveClock());
        g.play(Move.parse("e2e4"));
        assertEquals(0, g.position().halfmoveClock());
    }

    @Test
    void uciRoundTrip() {
        assertEquals("e7e8q", Move.parse("e7e8q").uci());
        assertEquals(Move.square("h8"), 63);
        assertThrows(IllegalArgumentException.class, () -> Move.parse("e9e4"));
        assertEquals(Position.START_FEN, Position.start().fen());
    }
}
