package be.dbsdw.minigames.verstoppertje.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class RulesTest {
    @Test
    void oneSeekerPerFivePlayersButAlwaysOneOfEach() {
        assertEquals(1, Roles.seekers(2, 5));
        assertEquals(1, Roles.seekers(5, 5));
        assertEquals(1, Roles.seekers(7, 5));
        assertEquals(2, Roles.seekers(8, 5));
        assertEquals(4, Roles.seekers(20, 5));
        assertEquals(2, Roles.seekers(3, 1), "never everyone a seeker");
    }

    @Test
    void seekersTeamGoesFirst() {
        UUID a = new UUID(0, 1);
        UUID b = new UUID(0, 2);
        UUID c = new UUID(0, 3);
        UUID d = new UUID(0, 4);
        RandomGenerator rnd = RandomGenerator.of("L64X128MixRandom");
        assertEquals(Set.of(c), Roles.pick(List.of(a, b), List.of(c), 1, rnd));
        Set<UUID> two = Roles.pick(List.of(a, b), List.of(c), 2, rnd);
        assertTrue(two.contains(c));
        assertEquals(2, two.size());
        Set<UUID> one = Roles.pick(List.of(a), List.of(c, d), 1, rnd);
        assertTrue(one.contains(c) || one.contains(d));
    }

    @Test
    void rayHitsTheBoxInFront() {
        // eye at x 0, looking along +x at a block from x 2 to 3
        assertEquals(2.0, RayBox.distance(0, 0.5, 0.5, 1, 0, 0, 2, 0, 0, 3, 1, 1), 1e-9);
        assertEquals(-1, RayBox.distance(0, 0.5, 0.5, -1, 0, 0, 2, 0, 0, 3, 1, 1), "behind");
        assertEquals(-1, RayBox.distance(0, 2.5, 0.5, 1, 0, 0, 2, 0, 0, 3, 1, 1), "above");
        assertEquals(0, RayBox.distance(2.5, 0.5, 0.5, 1, 0, 0, 2, 0, 0, 3, 1, 1), "inside");
        double diag = RayBox.distance(0, 1.62, 0, 0.6, -0.8, 0, 1, 0, -0.5, 2, 1, 0.5);
        assertTrue(diag > 0 && diag < 2, "looking down at a block in front: " + diag);
    }

    @Test
    void stillnessCountsFromTheLastMove() {
        Stillness s = new Stillness();
        assertTrue(s.update(Stillness.key(1, 2, 3), 10));
        assertFalse(s.update(Stillness.key(1, 2, 3), 50));
        assertEquals(40, s.stillFor(50));
        assertTrue(s.update(Stillness.key(1, 2, 4), 51));
        assertEquals(0, s.stillFor(51));
    }

    @Test
    void phases() {
        assertEquals(Phase.HIDE, Phase.at(0, 600, 4800));
        assertEquals(Phase.SEEK, Phase.at(600, 600, 4800));
        assertEquals(Phase.OVER, Phase.at(5400, 600, 4800));
    }

    @Test
    void settingsRejectNonsense() {
        assertThrows(IllegalArgumentException.class, () -> new Settings(0, 5, 600, 4800, 60, 600, 3.5));
        assertThrows(IllegalArgumentException.class, () -> new Settings(12, 5, 600, 0, 60, 600, 3.5));
        assertThrows(IllegalArgumentException.class, () -> new Settings(12, 5, 600, 4800, 60, 600, 9));
        assertFalse(new Settings(12, 5, 600, 4800, 60, 0, 3.5).hints());
    }
}
