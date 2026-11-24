package be.dbsdw.minigames.paintball.rules;

import static be.dbsdw.minigames.paintball.rules.DripPath.Cell.END;
import static be.dbsdw.minigames.paintball.rules.DripPath.Cell.FLOOR;
import static be.dbsdw.minigames.paintball.rules.DripPath.Cell.OPEN;
import static be.dbsdw.minigames.paintball.rules.DripPath.Cell.WALL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import be.dbsdw.minigames.paintball.rules.DripPath.Cell;
import org.junit.jupiter.api.Test;

class DripPathTest {
    private static DripPath.Cells column(Cell... below) {
        return k -> k <= below.length ? below[k - 1] : END;
    }

    @Test
    void floorRightBelowTheStartBlockLandsAfterTheRestOfThatBlock() {
        DripPath.Run run = DripPath.run(0.6, 8, column(FLOOR));
        assertTrue(run.landed());
        assertEquals(0.6, run.length(), 1e-9);
        assertEquals(1, run.level());
    }

    @Test
    void runsPastEveryWallBlockDownToTheFloor() {
        DripPath.Run run = DripPath.run(0.25, 8, column(WALL, WALL, FLOOR));
        assertTrue(run.landed());
        assertEquals(2.25, run.length(), 1e-9);
        assertEquals(3, run.level());
    }

    @Test
    void driesUpWhereTheWallStops() {
        DripPath.Run run = DripPath.run(0.5, 8, column(WALL, END));
        assertFalse(run.landed());
        assertEquals(1.5, run.length(), 1e-9);
    }

    @Test
    void fallsOffTheBottomOfAFloatingBlock() {
        DripPath.Run run = DripPath.run(0.4, 8, column(OPEN));
        assertTrue(run.falls());
        assertFalse(run.landed());
        assertEquals(0.4, run.length(), 1e-9);
        assertEquals(1, run.level());
    }

    @Test
    void fallsOffAnOverhangAfterRunningDownIt() {
        DripPath.Run run = DripPath.run(0.5, 8, column(WALL, WALL, OPEN));
        assertTrue(run.falls());
        assertEquals(2.5, run.length(), 1e-9);
        assertEquals(3, run.level());
    }

    @Test
    void aVeryHighHitDriesUpOnTheWay() {
        DripPath.Run run = DripPath.run(0.5, 3, column(WALL, WALL, WALL, WALL, WALL, FLOOR));
        assertFalse(run.landed());
        assertEquals(3, run.length(), 1e-9);
    }

    @Test
    void startIsKeptInsideItsBlock() {
        assertEquals(1, DripPath.run(1.7, 8, column(FLOOR)).length(), 1e-9);
        assertEquals(0, DripPath.run(-0.2, 8, column(FLOOR)).length(), 1e-9);
    }

    @Test
    void puddleHoldsThenFadesThroughThreeStagesAndGoes() {
        assertEquals(0, PuddleFade.stage(0));
        assertEquals(0, PuddleFade.stage(PuddleFade.HOLD - 1));
        assertEquals(1, PuddleFade.stage(PuddleFade.HOLD));
        assertEquals(2, PuddleFade.stage(PuddleFade.HOLD + PuddleFade.DISSOLVE / 3));
        assertEquals(3, PuddleFade.stage(PuddleFade.HOLD + PuddleFade.DISSOLVE * 2 / 3));
        assertFalse(PuddleFade.gone(PuddleFade.HOLD + PuddleFade.DISSOLVE - 1));
        assertTrue(PuddleFade.gone(PuddleFade.HOLD + PuddleFade.DISSOLVE));
    }
}
