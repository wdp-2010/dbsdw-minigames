package be.dbsdw.minigames.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class MatchOutcomeTest {
    @Test
    void aForfeitNamesTheWinnerAndTheTeamThatGaveUp() {
        MatchOutcome o = MatchOutcome.forfeit(0, 1, List.of());
        assertEquals(OptionalInt.of(0), o.winner());
        assertEquals(OptionalInt.of(1), o.gaveUp());
    }

    @Test
    void anOrdinaryWinOrDrawHasNobodyGivingUp() {
        assertEquals(OptionalInt.empty(), MatchOutcome.win(1, List.of()).gaveUp());
        assertEquals(OptionalInt.empty(), MatchOutcome.draw(List.of()).gaveUp());
    }
}
