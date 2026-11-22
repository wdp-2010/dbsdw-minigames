package be.dbsdw.minigames.core.api;

import java.util.List;
import java.util.OptionalInt;
import net.kyori.adventure.text.Component;

/**
 * How a match ended: a winning team or a draw, plus result lines shown to the players.
 * {@code gaveUp} is the team that left the match (all its players gone), when that ended it.
 */
public record MatchOutcome(OptionalInt winner, OptionalInt gaveUp, List<Component> lines) {
    public MatchOutcome {
        lines = List.copyOf(lines);
    }

    public static MatchOutcome win(int team, List<Component> lines) {
        return new MatchOutcome(OptionalInt.of(team), OptionalInt.empty(), lines);
    }

    public static MatchOutcome draw(List<Component> lines) {
        return new MatchOutcome(OptionalInt.empty(), OptionalInt.empty(), lines);
    }

    /** {@code winner} is the last team with players left; {@code gaveUp} the team whose players all left. */
    public static MatchOutcome forfeit(int winner, int gaveUp, List<Component> lines) {
        return new MatchOutcome(OptionalInt.of(winner), OptionalInt.of(gaveUp), lines);
    }
}
