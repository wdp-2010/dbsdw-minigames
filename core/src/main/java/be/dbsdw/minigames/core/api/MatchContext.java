package be.dbsdw.minigames.core.api;

import java.util.List;
import java.util.OptionalInt;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** What the core offers a running match. */
public interface MatchContext {
    ModeSpec mode();

    ArenaView arena();

    int teamCount();

    /** Online players of the team still in the match. */
    List<Player> team(int team);

    OptionalInt teamOf(Player player);

    Component teamName(int team);

    /** Ticks since the match started. */
    long ticks();

    /** Ends the match with this outcome; ignored after the first call. */
    void finish(MatchOutcome outcome);
}
