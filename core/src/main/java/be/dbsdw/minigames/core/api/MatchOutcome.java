package be.dbsdw.minigames.core.api;

import java.util.List;
import java.util.OptionalInt;
import net.kyori.adventure.text.Component;

/**
 * How a match ended: a winning team, a draw, or no result at all (paused, to be continued later),
 * plus result lines shown to the players. {@code gaveUp} is the team that left the match (all its
 * players gone), when that ended it.
 */
public record MatchOutcome(OptionalInt winner, OptionalInt gaveUp, List<Component> lines, boolean paused) {
    public MatchOutcome {
        lines = List.copyOf(lines);
        if (paused && (winner.isPresent() || gaveUp.isPresent())) {
            throw new IllegalArgumentException("a paused match has no winner");
        }
    }

    public static MatchOutcome win(int team, List<Component> lines) {
        return new MatchOutcome(OptionalInt.of(team), OptionalInt.empty(), lines, false);
    }

    public static MatchOutcome draw(List<Component> lines) {
        return new MatchOutcome(OptionalInt.empty(), OptionalInt.empty(), lines, false);
    }

    /** {@code winner} is the last team with players left; {@code gaveUp} the team whose players all left. */
    public static MatchOutcome forfeit(int winner, int gaveUp, List<Component> lines) {
        return new MatchOutcome(OptionalInt.of(winner), OptionalInt.of(gaveUp), lines, false);
    }

    /**
     * The match stops without a result, to be continued another time (the game keeps its own save).
     * No win, loss, draw or match is counted.
     */
    public static MatchOutcome paused(List<Component> lines) {
        return new MatchOutcome(OptionalInt.empty(), OptionalInt.empty(), lines, true);
    }
}
