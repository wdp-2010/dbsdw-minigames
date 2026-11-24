package be.dbsdw.minigames.paintball.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class WinScoreTest {
    @Test
    void winScoreGrowsWithTheBiggerTeamUpToTheCap() {
        ModeRules tdm = new ModeRules("tdm", 1, 8, 30, 10, 12000);
        assertEquals(10, tdm.winScoreFor(1), "a 1v1 plays to 10");
        assertEquals(20, tdm.winScoreFor(2));
        assertEquals(30, tdm.winScoreFor(3));
        assertEquals(30, tdm.winScoreFor(8), "never past win-eliminations");
        assertEquals(10, tdm.winScoreFor(0), "an empty team still needs a goal");
    }

    @Test
    void zeroPerPlayerKeepsTheFixedScore() {
        ModeRules ctf = new ModeRules("ctf", 1, 8, 3, 0, 12000);
        assertEquals(3, ctf.winScoreFor(1));
        assertEquals(3, ctf.winScoreFor(8));
    }

    @Test
    void negativePerPlayerIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ModeRules("tdm", 1, 8, 30, -1, 12000));
    }

    @Test
    void scoreKnowsItsTarget() {
        assertEquals(20, new TeamScore(2, 20).target());
    }
}
