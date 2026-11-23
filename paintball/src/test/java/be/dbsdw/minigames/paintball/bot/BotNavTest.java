package be.dbsdw.minigames.paintball.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import be.dbsdw.minigames.paintball.rules.WalkableArea.Cell;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class BotNavTest {
    /** A 10x10 floor at y 64 with a wall at x = 5 for z 0..8 (a gap at z = 9). */
    private static BotNav walled() {
        List<Cell> cells = new ArrayList<>();
        for (int x = 0; x < 10; x++) {
            for (int z = 0; z < 10; z++) {
                if (x != 5 || z == 9) {
                    cells.add(new Cell(x, 64, z));
                }
            }
        }
        return new BotNav(cells);
    }

    @Test
    void pathGoesAroundTheWall() {
        BotNav nav = walled();
        List<Cell> path = nav.path(new Cell(0, 64, 0), new Cell(9, 64, 0), 10_000);
        assertEquals(new Cell(9, 64, 0), path.getLast());
        assertTrue(path.contains(new Cell(5, 64, 9)), "must pass the gap");
        Cell prev = new Cell(0, 64, 0);
        for (Cell c : path) {
            assertTrue(nav.neighbours(prev).contains(c), "every step is to a neighbour: " + prev + " -> " + c);
            prev = c;
        }
    }

    @Test
    void diagonalStepsDoNotCutCorners() {
        BotNav nav = walled();
        // (4, 8) -> (5, 9) would pass the wall corner at (5, 8): not a neighbour.
        assertFalse(nav.neighbours(new Cell(4, 64, 8)).contains(new Cell(5, 64, 9)));
        assertTrue(nav.neighbours(new Cell(4, 64, 9)).contains(new Cell(5, 64, 9)));
    }

    @Test
    void stepsUpOneAndDownThree() {
        BotNav nav = new BotNav(List.of(new Cell(0, 64, 0), new Cell(1, 65, 0), new Cell(2, 62, 0), new Cell(3, 66, 0)));
        assertEquals(List.of(new Cell(1, 65, 0)), nav.neighbours(new Cell(0, 64, 0)));
        assertTrue(nav.neighbours(new Cell(1, 65, 0)).contains(new Cell(2, 62, 0)));
        assertFalse(nav.neighbours(new Cell(2, 62, 0)).contains(new Cell(3, 66, 0)), "4 up is too high");
    }

    @Test
    void unreachableGoalGivesPathToClosestCell() {
        BotNav nav = new BotNav(List.of(new Cell(0, 64, 0), new Cell(1, 64, 0), new Cell(5, 64, 0)));
        assertEquals(List.of(new Cell(1, 64, 0)), nav.path(new Cell(0, 64, 0), new Cell(5, 64, 0), 1000));
    }

    @Test
    void nearestFindsFloorUnderAndBeside() {
        BotNav nav = walled();
        assertEquals(new Cell(3, 64, 3), nav.nearest(3.5, 64.0, 3.5).orElseThrow());
        assertTrue(nav.nearest(5.5, 64, 4.5).orElseThrow().x() != 5, "the wall column has no floor");
        assertTrue(nav.nearest(50, 64, 50).isEmpty());
        assertTrue(nav.randomNear(new Cell(3, 64, 3), 2, new Random(1)).isPresent());
    }
}
