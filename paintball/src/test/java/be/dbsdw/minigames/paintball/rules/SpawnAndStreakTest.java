package be.dbsdw.minigames.paintball.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpawnAndStreakTest {
    @Test
    void safestSpawnAvoidsTheEnemy() {
        List<double[]> spawns = List.of(new double[] {0, 0, 0}, new double[] {30, 0, 0}, new double[] {31, 0, 2});
        List<double[]> enemies = List.of(new double[] {2, 0, 0});
        Set<Integer> picked = new HashSet<>();
        Random rng = new Random(4);
        for (int i = 0; i < 200; i++) {
            picked.add(SpawnPicker.safest(spawns, enemies, rng));
        }
        assertEquals(Set.of(1, 2), picked, "never the spawn next to the enemy; both far ones are used");
    }

    @Test
    void streaksCountAndReset() {
        Streaks s = new Streaks();
        UUID a = UUID.randomUUID();
        assertEquals(1, s.eliminated(a));
        s.eliminated(a);
        assertEquals(3, s.eliminated(a));
        assertTrue(Streaks.announce(3));
        assertFalse(Streaks.announce(4));
        assertTrue(Streaks.announce(5));
        assertEquals(3, s.out(a));
        assertEquals(0, s.out(a));
        assertEquals(1, s.eliminated(a));
        assertEquals(3, s.best(a));
    }
}
