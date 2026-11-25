package be.dbsdw.minigames.core.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import be.dbsdw.minigames.core.progress.XpRules.Result;
import java.util.Map;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

class XpRulesTest {
    private final XpRules rules = new XpRules(20, 20, 10, 60, 0.5);
    private final ToIntFunction<String> weights = stat -> switch (stat) {
        case "hits" -> 1;
        case "eliminations" -> 3;
        default -> 0;
    };

    private long award(Result result, Map<String, Long> stats, long seconds, boolean practice, boolean present,
                       boolean bots) {
        return rules.award(result, stats, weights, seconds, practice, present, bots);
    }

    @Test
    void aLossGivesTheBaseAndStatXp() {
        assertEquals(20 + 5 + 2 * 3, award(Result.LOSS, Map.of("hits", 5L, "eliminations", 2L), 300, false, true, false));
    }

    @Test
    void winsAndDrawsAddABonus() {
        assertEquals(40, award(Result.WIN, Map.of(), 300, false, true, false));
        assertEquals(30, award(Result.DRAW, Map.of(), 300, false, true, false));
    }

    @Test
    void unknownStatsCountNothing() {
        assertEquals(20, award(Result.LOSS, Map.of("shots", 99L), 300, false, true, false));
    }

    @Test
    void practiceShortAndAbsentGiveNothing() {
        assertEquals(0, award(Result.WIN, Map.of("hits", 9L), 300, true, true, false));
        assertEquals(0, award(Result.WIN, Map.of("hits", 9L), 59, false, true, false));
        assertEquals(0, award(Result.WIN, Map.of("hits", 9L), 300, false, false, false));
    }

    @Test
    void botMatchesGiveReducedXp() {
        assertEquals(20, award(Result.WIN, Map.of(), 300, false, true, true));
    }

    @Test
    void negativeStatAmountsAreIgnored() {
        assertEquals(20, award(Result.LOSS, Map.of("hits", -4L), 300, false, true, false));
    }

    @Test
    void badSettingsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new XpRules(-1, 0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new XpRules(1, 0, 0, 0, 1.5));
    }
}
