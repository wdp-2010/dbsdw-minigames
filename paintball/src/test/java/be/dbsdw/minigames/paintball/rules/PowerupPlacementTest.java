package be.dbsdw.minigames.paintball.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import be.dbsdw.minigames.paintball.rules.PowerupDirector.Mover;
import be.dbsdw.minigames.paintball.rules.WalkableArea.Cell;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PowerupPlacementTest {
    /** A flat floor at y=0 (players stand at y=1), x/z 0..39, with a 3-high wall at x=20 for z<30
     *  and a closed box (roof at y=5) on top of the floor at x 30..34, z 0..4. */
    private static final class Map implements WalkableArea.Blocks {
        final Set<String> solid = new HashSet<>();

        Map() {
            for (int x = 0; x < 40; x++) {
                for (int z = 0; z < 40; z++) {
                    solid.add(x + ",0," + z);
                }
            }
            for (int z = 0; z < 30; z++) {
                for (int y = 1; y <= 3; y++) {
                    solid.add("20," + y + "," + z);
                }
            }
            for (int x = 30; x <= 34; x++) {
                for (int z = 0; z <= 4; z++) {
                    solid.add(x + ",5," + z); // roof
                    if (x == 30 || x == 34 || z == 4) {
                        for (int y = 1; y <= 4; y++) {
                            solid.add(x + "," + y + "," + z);
                        }
                    }
                }
            }
        }

        @Override
        public boolean solid(int x, int y, int z) {
            return solid.contains(x + "," + y + "," + z);
        }

        @Override
        public boolean open(int x, int y, int z) {
            return !solid(x, y, z);
        }
    }

    private static List<Cell> flood() {
        return WalkableArea.flood(new Map(), new int[] {0, 0, 0}, new int[] {40, 10, 40},
                List.of(new Cell(2, 3, 2)), 100_000);
    }

    @Test
    void floodStaysOnReachableFloor() {
        List<Cell> cells = flood();
        Set<Cell> set = new HashSet<>(cells);
        assertTrue(set.contains(new Cell(2, 1, 2)), "the spawn drops onto the floor");
        assertTrue(set.contains(new Cell(25, 1, 10)), "behind the wall, round its open end");
        assertFalse(set.contains(new Cell(20, 4, 10)), "not on top of the 3-high wall");
        assertFalse(set.contains(new Cell(32, 1, 2)), "not inside the closed box");
        assertFalse(set.contains(new Cell(32, 6, 2)), "not on the box's roof");
        assertTrue(cells.stream().allMatch(c -> c.y() == 1), "only the floor level");
    }

    @Test
    void levelScoreHasNoLean() {
        List<Mover> players = List.of(new Mover(5, 1, 20, 0), new Mover(35, 1, 20, 1));
        PowerupDirector d = new PowerupDirector(flood(), List.of(), 1.0, new Random(1));
        int left = 0;
        for (int i = 0; i < 3000; i++) {
            left += d.pickSpot(players, List.of(), new int[] {5, 5}, 30).orElseThrow().x() < 20 ? 1 : 0;
        }
        assertEquals(0.5, left / 3000.0, 0.06);
    }

    @Test
    void trailingTeamGetsMoreButNotAll() {
        // team 0 plays on the left (x < 20) and is 10 behind
        List<Mover> players = List.of(new Mover(5, 1, 20, 0), new Mover(35, 1, 20, 1));
        PowerupDirector d = new PowerupDirector(flood(), List.of(), 1.0, new Random(2));
        int left = 0;
        int helps = 0;
        int helpsRight = 0;
        int right = 0;
        for (int i = 0; i < 4000; i++) {
            Cell c = d.pickSpot(players, List.of(), new int[] {2, 12}, 30).orElseThrow();
            PowerupType t = d.pickType(c, players, List.of(PowerupType.values()), new int[] {2, 12}, 30);
            boolean help = t == PowerupType.REPAIR || t == PowerupType.SHIELD;
            if (c.x() < 20) {
                left++;
                helps += help ? 1 : 0;
            } else {
                right++;
                helpsRight += help ? 1 : 0;
            }
        }
        double share = left / 4000.0;
        assertTrue(share > 0.56 && share < 0.75, "trailing side share " + share);
        assertTrue((double) helps / left > (double) helpsRight / right, "more repair/shield on the trailing side");
    }

    @Test
    void keepsAwayFromPlayersOthersAndSpawns() {
        List<Mover> players = new ArrayList<>(List.of(new Mover(10, 1, 10, 0)));
        Cell other = new Cell(30, 1, 30);
        Cell spawn = new Cell(2, 1, 2);
        PowerupDirector d = new PowerupDirector(flood(), List.of(spawn), 1.0, new Random(3));
        for (int i = 0; i < 500; i++) {
            Cell c = d.pickSpot(players, List.of(other), new int[] {0, 0}, 30).orElseThrow();
            assertTrue(c.distanceSquared(10, 1, 10) >= 16);
            assertTrue(c.distanceSquared(30.5, 1, 30.5) >= 49);
            assertTrue(c.distanceSquared(2.5, 1, 2.5) >= 25);
        }
    }
}
