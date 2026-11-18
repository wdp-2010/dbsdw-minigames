package be.dbsdw.minigames.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TeamBalancerTest {
    @Test
    void picksSmallestTeam() {
        assertEquals(1, TeamBalancer.pickTeam(new int[] {3, 2}));
    }

    @Test
    void tieGoesToFirstTeam() {
        assertEquals(0, TeamBalancer.pickTeam(new int[] {2, 2}));
    }

    @Test
    void rejectsNoTeams() {
        assertThrows(IllegalArgumentException.class, () -> TeamBalancer.pickTeam(new int[0]));
    }
}
