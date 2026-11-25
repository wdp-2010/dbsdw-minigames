package be.dbsdw.minigames.paintball.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import be.dbsdw.minigames.paintball.rules.BombRounds.End;
import be.dbsdw.minigames.paintball.rules.BombRounds.Event;
import be.dbsdw.minigames.paintball.rules.BombRounds.Phase;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class BombRoundsTest {
    // win 3 of at most 4; buy 10, round 100, post 5, plant 4, fuse 50 ticks
    private final BombSettings s = new BombSettings(10, 1, 5, 3, 4, 10, 100, 5, 4, 50, 6, 3, 4);

    @Test
    void buyPhaseThenLive() {
        BombRounds r = new BombRounds(s, 0);
        assertEquals(Phase.BUY, r.phase());
        assertEquals(Event.NONE, r.tick(9, 2, 2));
        assertEquals(Event.GO, r.tick(10, 2, 2));
        assertEquals(Phase.LIVE, r.phase());
        assertEquals(100, r.ticksLeft(10));
    }

    @Test
    void timeRunsOutForDefenders() {
        BombRounds r = live();
        assertEquals(Event.ROUND_OVER, r.tick(110, 2, 2));
        assertEquals(new BombRounds.Result(1, End.TIME), r.last());
        assertEquals(1, r.wins(1));
    }

    @Test
    void plantedBombKeepsRoundGoingAfterAttackersAreOut() {
        BombRounds r = live();
        assertTrue(r.plant(50));
        assertEquals(Event.NONE, r.tick(60, 0, 2));
        assertEquals(Event.NONE, r.tick(99, 0, 2));
        assertEquals(Event.ROUND_OVER, r.tick(100, 0, 2));
        assertEquals(End.EXPLODED, r.last().how());
        assertEquals(0, r.last().winner());
    }

    @Test
    void defuseWinsForDefenders() {
        BombRounds r = live();
        assertFalse(r.defuse(20));
        r.plant(20);
        assertTrue(r.defuse(30));
        assertEquals(new BombRounds.Result(1, End.DEFUSED), r.last());
    }

    @Test
    void eliminationEndsRound() {
        BombRounds r = live();
        assertEquals(Event.ROUND_OVER, r.tick(20, 1, 0));
        assertEquals(new BombRounds.Result(0, End.ELIMINATION), r.last());
        r = live();
        assertEquals(Event.ROUND_OVER, r.tick(20, 0, 1));
        assertEquals(1, r.last().winner());
    }

    @Test
    void sidesSwapAtHalfAndMatchEnds() {
        BombRounds r = new BombRounds(s, 0);
        long t = 0;
        assertEquals(0, r.attackers());
        t = playRound(r, t, 0); // round 1: team 0 wins (attacking)
        assertEquals(Event.NEXT_ROUND, r.tick(t += 5, 1, 1));
        t = playRound(r, t, 0); // round 2
        assertEquals(Event.SIDES_SWAPPED, r.tick(t += 5, 1, 1));
        assertEquals(1, r.attackers());
        t = playRound(r, t, 0); // round 3: team 0 now defends, wins its third
        assertTrue(r.decided());
        assertEquals(Event.MATCH_OVER, r.tick(t + 5, 1, 1));
        assertEquals(OptionalInt.of(0), r.winner());
    }

    @Test
    void evenAfterLastRoundIsDraw() {
        BombSettings two = new BombSettings(10, 1, 5, 2, 2, 10, 100, 5, 4, 50, 6, 3, 4);
        BombRounds r = new BombRounds(two, 0);
        long t = playRound(r, 0, 0);
        assertEquals(Event.SIDES_SWAPPED, r.tick(t += 5, 1, 1));
        t = playRound(r, t, 1);
        assertTrue(r.decided());
        assertEquals(OptionalInt.empty(), r.winner());
    }

    /** Buy phase, then the given team knocks the other out. */
    private static long playRound(BombRounds r, long t, int winner) {
        r.tick(t += 10, 1, 1);
        int attackersAlive = winner == r.attackers() ? 1 : 0;
        r.tick(t += 1, attackersAlive, 1 - attackersAlive);
        return t;
    }

    private BombRounds live() {
        BombRounds r = new BombRounds(s, 0);
        r.tick(10, 2, 2);
        return r;
    }
}
