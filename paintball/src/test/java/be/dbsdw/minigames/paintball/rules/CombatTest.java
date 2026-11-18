package be.dbsdw.minigames.paintball.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import be.dbsdw.minigames.paintball.rules.Combat.HitResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CombatTest {
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();
    private Combat combat;

    @BeforeEach
    void setUp() {
        Map<UUID, Integer> teams = new LinkedHashMap<>();
        teams.put(a, 0);
        teams.put(b, 0);
        teams.put(c, 1);
        combat = new Combat(teams, Settings.defaults());
        combat.start(0);
    }

    @Test
    void protectedAtStartThenHittable() {
        assertEquals(HitResult.ABSORBED, combat.hit(c, a, 0, 39));
        assertEquals(HitResult.HIT, combat.hit(c, a, 0, 40));
    }

    @Test
    void shootingEndsProtection() {
        combat.shot(c);
        assertEquals(HitResult.HIT, combat.hit(c, a, 0, 1));
    }

    @Test
    void threeHitsIsOutAndRespawnResets() {
        assertEquals(HitResult.HIT, combat.hit(c, a, 0, 100));
        assertEquals(HitResult.HIT, combat.hit(c, a, 0, 100));
        assertEquals(HitResult.OUT, combat.hit(c, a, 0, 100));
        assertEquals(1, combat.eliminations(a));
        assertTrue(combat.isOut(c));
        assertEquals(0, combat.hitsLeft(c));
        assertEquals(HitResult.PASS_THROUGH, combat.hit(c, a, 0, 101));
        assertTrue(combat.respawnsDue(159).isEmpty());
        assertEquals(List.of(c), combat.respawnsDue(160));
        assertEquals(3, combat.hitsLeft(c));
        assertTrue(combat.isProtected(c, 199));
        assertFalse(combat.isProtected(c, 200));
    }

    @Test
    void teammatePassesThrough() {
        assertEquals(HitResult.PASS_THROUGH, combat.hit(b, a, 0, 100));
    }

    @Test
    void outOfBoundsGivesNoPoint() {
        assertTrue(combat.outOfBounds(c, 100));
        assertFalse(combat.outOfBounds(c, 101));
        assertEquals(0, combat.eliminations(a) + combat.eliminations(b));
    }

    @Test
    void removedPlayerIsIgnoredButTheirBallsStillCount() {
        combat.remove(c);
        assertEquals(HitResult.PASS_THROUGH, combat.hit(c, a, 0, 100));
        combat.hit(a, c, 1, 100);
        combat.hit(a, c, 1, 100);
        assertEquals(HitResult.OUT, combat.hit(a, c, 1, 100));
        assertTrue(combat.respawnsDue(1000).stream().noneMatch(c::equals));
    }

    @Test
    void topSortsByEliminationsThenJoinOrder() {
        combat.outOfBounds(a, 0);
        for (int i = 0; i < 3; i++) {
            combat.hit(c, b, 0, 100);
        }
        assertEquals(List.of(b, a, c), combat.top(3));
    }
}
