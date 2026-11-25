package be.dbsdw.minigames.paintball.rules;

import java.util.OptionalInt;

/**
 * The rounds of one bomb match: buy phase, the round itself, a planted bomb, the pause after a
 * round, the side swap at half time and the match result. Team 0 attacks in the first half.
 */
public final class BombRounds {
    public enum Phase { BUY, LIVE, PLANTED, POST, OVER }

    /** How a round was won. */
    public enum End { ELIMINATION, EXPLODED, DEFUSED, TIME }

    public record Result(int winner, End how) {}

    /** What one tick changed. */
    public enum Event { NONE, GO, ROUND_OVER, NEXT_ROUND, SIDES_SWAPPED, MATCH_OVER }

    private final BombSettings s;
    private final int[] wins = new int[2];
    private Phase phase = Phase.BUY;
    private int round = 1;
    private long phaseStart;
    private long plantedAt;
    private Result last;

    public BombRounds(BombSettings settings, long now) {
        this.s = settings;
        this.phaseStart = now;
    }

    public Phase phase() {
        return phase;
    }

    /** 1-based. */
    public int round() {
        return round;
    }

    public int wins(int team) {
        return wins[team];
    }

    public int attackers() {
        return round <= s.half() ? 0 : 1;
    }

    public int defenders() {
        return 1 - attackers();
    }

    /** The last round's result, or null before the first one ended. */
    public Result last() {
        return last;
    }

    public long plantedAt() {
        return plantedAt;
    }

    /** Ticks left on the clock that matters now: buy time, round time, fuse or the pause. */
    public long ticksLeft(long now) {
        return Math.max(0, switch (phase) {
            case BUY -> s.buyTicks() - (now - phaseStart);
            case LIVE -> s.roundTicks() - (now - phaseStart);
            case PLANTED -> s.fuseTicks() - (now - plantedAt);
            case POST -> s.postTicks() - (now - phaseStart);
            case OVER -> 0;
        });
    }

    /** The length of the clock {@link #ticksLeft} counts down. */
    public long clockTicks() {
        return switch (phase) {
            case BUY -> s.buyTicks();
            case LIVE -> s.roundTicks();
            case PLANTED -> s.fuseTicks();
            case POST -> s.postTicks();
            case OVER -> 1;
        };
    }

    /** Moves the clock on; {@code aliveAttackers} and {@code aliveDefenders} count players and bots. */
    public Event tick(long now, int aliveAttackers, int aliveDefenders) {
        switch (phase) {
            case BUY -> {
                if (now - phaseStart >= s.buyTicks()) {
                    phase = Phase.LIVE;
                    phaseStart = now;
                    return Event.GO;
                }
            }
            case LIVE -> {
                if (aliveDefenders == 0) {
                    return end(attackers(), End.ELIMINATION, now);
                }
                if (aliveAttackers == 0) {
                    return end(defenders(), End.ELIMINATION, now);
                }
                if (now - phaseStart >= s.roundTicks()) {
                    return end(defenders(), End.TIME, now);
                }
            }
            case PLANTED -> {
                if (now - plantedAt >= s.fuseTicks()) {
                    return end(attackers(), End.EXPLODED, now);
                }
                // With the bomb down, the attackers being out does not end the round: it has to be defused.
                if (aliveDefenders == 0) {
                    return end(attackers(), End.ELIMINATION, now);
                }
            }
            case POST -> {
                if (now - phaseStart >= s.postTicks()) {
                    if (decided()) {
                        phase = Phase.OVER;
                        return Event.MATCH_OVER;
                    }
                    boolean swap = round == s.half();
                    round++;
                    phase = Phase.BUY;
                    phaseStart = now;
                    return swap ? Event.SIDES_SWAPPED : Event.NEXT_ROUND;
                }
            }
            case OVER -> {
            }
        }
        return Event.NONE;
    }

    /** The bomb went down; false if it could not (not during the round). */
    public boolean plant(long now) {
        if (phase != Phase.LIVE) {
            return false;
        }
        phase = Phase.PLANTED;
        plantedAt = now;
        return true;
    }

    /** The bomb was defused; false if there was none to defuse. */
    public boolean defuse(long now) {
        if (phase != Phase.PLANTED) {
            return false;
        }
        end(defenders(), End.DEFUSED, now);
        return true;
    }

    /** The match is decided: a team has enough round wins, or every round was played. */
    public boolean decided() {
        return wins[0] >= s.winRounds() || wins[1] >= s.winRounds() || round >= s.maxRounds();
    }

    /** The winner of a finished match, empty for a draw. */
    public OptionalInt winner() {
        if (wins[0] == wins[1]) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(wins[0] > wins[1] ? 0 : 1);
    }

    private Event end(int winner, End how, long now) {
        wins[winner]++;
        last = new Result(winner, how);
        phase = Phase.POST;
        phaseStart = now;
        return Event.ROUND_OVER;
    }
}
