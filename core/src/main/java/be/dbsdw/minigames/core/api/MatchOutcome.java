package be.dbsdw.minigames.core.api;

import java.util.List;
import java.util.OptionalInt;
import net.kyori.adventure.text.Component;

/** How a match ended: a winning team or a draw, plus result lines shown to the players. */
public record MatchOutcome(OptionalInt winner, List<Component> lines) {
    public MatchOutcome {
        lines = List.copyOf(lines);
    }

    public static MatchOutcome win(int team, List<Component> lines) {
        return new MatchOutcome(OptionalInt.of(team), lines);
    }

    public static MatchOutcome draw(List<Component> lines) {
        return new MatchOutcome(OptionalInt.empty(), lines);
    }
}
