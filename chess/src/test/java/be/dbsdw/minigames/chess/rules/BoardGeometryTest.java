package be.dbsdw.minigames.chess.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BoardGeometryTest {
    // 3-block squares. White stands at z < 0 facing south (+z), so ranks run +z and files run -x
    // (west is on white's right).
    private static BoardGeometry southFacing() {
        return BoardGeometry.of(22.5, 1.5, 1.5, 22.5, 64, 12, -8);
    }

    @Test
    void centresAndSquares() {
        BoardGeometry g = southFacing();
        assertEquals(3, g.squareSize(), 1e-9);
        assertFalse(g.mirrored());
        assertEquals(22.5, g.centreX(0), 1e-9);
        assertEquals(1.5, g.centreZ(0), 1e-9);
        assertEquals(1.5, g.centreX(63), 1e-9);
        assertEquals(22.5, g.centreZ(63), 1e-9);
        assertEquals(Move.square("b1"), g.squareAt(19.5, 1.5));
        assertEquals(Move.square("a2"), g.squareAt(22.5, 4.5));
        assertEquals(0, g.squareAt(23.9, 0.1));
        assertEquals(-1, g.squareAt(24.1, 1.5));
        assertEquals(0f, g.whiteForwardYaw(), 1e-4);
    }

    @Test
    void lookRayPicksTheSquare() {
        BoardGeometry g = southFacing();
        // From above e4's centre, straight down.
        int e4 = Move.square("e4");
        assertEquals(e4, g.square(g.centreX(e4), 72, g.centreZ(e4), 0, -1, 0, 20));
        // From white's stand, looking forward and down at 45 degrees: lands 8 blocks ahead.
        assertEquals(g.squareAt(12, 0), g.square(12, 72, -8, 0, -1, 1, 40));
        assertEquals(-1, g.square(12, 72, -8, 0, 0.2, 1, 40), "looking up");
        assertEquals(-1, g.square(12, 72, -8, 0, -1, 1, 5), "out of reach");
    }

    @Test
    void wrongMarksAreCaught() {
        assertThrows(IllegalArgumentException.class, () -> BoardGeometry.of(0, 0, 10, 21, 64, 5, -5));
        assertThrows(IllegalArgumentException.class, () -> BoardGeometry.of(22.5, 1.5, 1.5, 22.5, 64, 12, 30),
                "white on black's side");
        assertTrue(BoardGeometry.of(1.5, 1.5, 22.5, 22.5, 64, 12, -8).mirrored());
    }

    @Test
    void ranksAlongX() {
        // White stands west (x < 0) facing east: right is south (+z), so files run +z.
        BoardGeometry g = BoardGeometry.of(1.5, 1.5, 22.5, 22.5, 64, -8, 12);
        assertFalse(g.mirrored());
        assertEquals(Move.square("a2"), g.squareAt(4.5, 1.5));
        assertEquals(Move.square("b1"), g.squareAt(1.5, 4.5));
        assertEquals(-90f, g.whiteForwardYaw(), 1e-4);
    }

    @Test
    void clockFormatAndIncrement() {
        ChessClock c = new ChessClock(300_000, 3_000);
        c.run(true, 61_000);
        c.moved(true);
        assertEquals(242_000, c.millis(true));
        assertEquals("4:02", ChessClock.format(c.millis(true)));
        assertEquals("0:09.4", ChessClock.format(9_450));
        assertEquals("1:00:00", ChessClock.format(3_600_000));
        c.run(false, 400_000);
        assertTrue(c.flagged(false));
        ChessClock free = new ChessClock(0, 0);
        free.run(true, 1_000_000);
        assertFalse(free.flagged(true));
        assertTrue(free.unlimited());
    }
}
