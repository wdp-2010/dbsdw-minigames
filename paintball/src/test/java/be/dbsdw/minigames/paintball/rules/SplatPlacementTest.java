package be.dbsdw.minigames.paintball.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SplatPlacementTest {
    private static final double EPS = 1e-9;

    /** A wall: every cell paintable. */
    private static final SplatPlacement.Cells WALL = (du, dv) -> true;

    @Test
    void staysWhereTheBallHitOnAWideWall() {
        double[] c = SplatPlacement.place(0.95, 0.1, 0.4, WALL);
        assertEquals(0.95, c[0], EPS);   // across the seam onto the next block: allowed
        assertEquals(0.1, c[1], EPS);
    }

    @Test
    void neverOverTheTopOfAWall() {
        // nothing paintable above (dv = +1 is up here)
        SplatPlacement.Cells topRow = (du, dv) -> dv <= 0;
        double[] c = SplatPlacement.place(0.5, 0.9, 0.4, topRow);
        assertEquals(0.5, c[0], EPS);
        assertEquals(0.6, c[1], EPS);     // pushed down so the top edge is the wall's top
    }

    @Test
    void neverPastTheEndOfAWall() {
        SplatPlacement.Cells endOfWall = (du, dv) -> du <= 0;
        double[] c = SplatPlacement.place(0.98, 0.5, 0.3, endOfWall);
        assertEquals(0.7, c[0], EPS);
    }

    @Test
    void cornerCellMissingPushesBackTheShorterWay() {
        // a wall with a window to the upper right: only that diagonal cell is open
        SplatPlacement.Cells window = (du, dv) -> !(du == 1 && dv == 1);
        double[] c = SplatPlacement.place(0.9, 0.75, 0.3, window);
        // u overlaps by 0.2, v by 0.05: v moves back
        assertEquals(0.9, c[0], EPS);
        assertEquals(0.7, c[1], EPS);
    }

    @Test
    void lonelyBlockKeepsTheSplatOnItsFace() {
        double[] c = SplatPlacement.place(0.0, 1.0, 0.5, (du, dv) -> false);
        assertEquals(0.5, c[0], EPS);
        assertEquals(0.5, c[1], EPS);
    }
}
