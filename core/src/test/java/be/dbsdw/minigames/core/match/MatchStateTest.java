package be.dbsdw.minigames.core.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatchStateTest {
    private final UUID a = new UUID(0, 1);
    private final UUID b = new UUID(0, 2);
    private final UUID c = new UUID(0, 3);
    private final UUID d = new UUID(0, 4);

    @Test
    void teamOfAndMembers() {
        MatchState s = new MatchState(List.of(List.of(a), List.of(b, c)), 200);
        assertEquals(OptionalInt.of(1), s.teamOf(c));
        assertEquals(List.of(b, c), s.members(1));
        assertEquals(MatchState.Phase.RUNNING, s.phase());
    }

    @Test
    void leaveKeepsMatchWhileTwoTeamsHavePlayers() {
        MatchState s = new MatchState(List.of(List.of(a, d), List.of(b, c)), 200);
        assertEquals(new MatchState.LeaveResult.None(), s.leave(a));
        assertEquals(OptionalInt.empty(), s.teamOf(a));
    }

    @Test
    void lastTeamStandingWins() {
        MatchState s = new MatchState(List.of(List.of(a), List.of(b, c)), 200);
        assertEquals(new MatchState.LeaveResult.LastTeamStanding(1), s.leave(a));
    }

    @Test
    void everyoneLeftEndsResultsAtOnce() {
        MatchState s = new MatchState(List.of(List.of(a), List.of(b)), 200);
        assertEquals(new MatchState.LeaveResult.LastTeamStanding(1), s.leave(a));
        assertTrue(s.finish());
        assertEquals(new MatchState.LeaveResult.None(), s.leave(b));
        assertTrue(s.tickEnding());
        assertEquals(MatchState.Phase.DONE, s.phase());
    }

    @Test
    void moveChangesTeamWithoutEndingTheMatch() {
        MatchState s = new MatchState(List.of(List.of(a), List.of(b, c)), 200);
        assertTrue(s.move(a, 1));
        assertEquals(OptionalInt.of(1), s.teamOf(a));
        assertEquals(List.of(), s.members(0));
        assertEquals(MatchState.Phase.RUNNING, s.phase());
        assertFalse(s.move(a, 1), "already in that team");
        assertFalse(s.move(d, 0), "not in the match");
        assertFalse(s.move(a, 2), "no such team");
        s.finish();
        assertFalse(s.move(b, 0), "not running");
    }

    @Test
    void finishOnlyOnce() {
        MatchState s = new MatchState(List.of(List.of(a), List.of(b)), 200);
        assertTrue(s.finish());
        assertFalse(s.finish());
    }

    @Test
    void resultsLastConfiguredTicks() {
        MatchState s = new MatchState(List.of(List.of(a), List.of(b)), 200);
        s.finish();
        for (int i = 0; i < 199; i++) {
            assertFalse(s.tickEnding());
        }
        assertTrue(s.tickEnding());
        assertEquals(MatchState.Phase.DONE, s.phase());
    }

    @Test
    void leaveDuringEndingJustRemoves() {
        MatchState s = new MatchState(List.of(List.of(a, c), List.of(b)), 200);
        s.finish();
        assertEquals(new MatchState.LeaveResult.None(), s.leave(b));
        assertEquals(List.of(a, c), s.everyone());
    }

    @Test
    void abortIsDone() {
        MatchState s = new MatchState(List.of(List.of(a), List.of(b)), 200);
        s.abort();
        assertEquals(MatchState.Phase.DONE, s.phase());
        assertFalse(s.finish());
    }
}
