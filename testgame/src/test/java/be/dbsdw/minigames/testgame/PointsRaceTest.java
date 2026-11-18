package be.dbsdw.minigames.testgame;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class PointsRaceTest {
    @Test
    void firstToTargetWins() {
        PointsRace race = new PointsRace(2, 3);
        assertEquals(OptionalInt.empty(), race.score(1));
        assertEquals(OptionalInt.empty(), race.score(1));
        assertEquals(OptionalInt.of(1), race.score(1));
        assertEquals(3, race.points(1));
    }

    @Test
    void leaderEmptyOnTie() {
        PointsRace race = new PointsRace(2, 3);
        race.score(0);
        race.score(1);
        assertEquals(OptionalInt.empty(), race.leader());
    }

    @Test
    void leaderIsHigherScore() {
        PointsRace race = new PointsRace(2, 3);
        race.score(0);
        race.score(1);
        race.score(1);
        assertEquals(OptionalInt.of(1), race.leader());
    }
}
