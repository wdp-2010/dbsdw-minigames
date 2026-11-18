package be.dbsdw.minigames.core.api;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

/** A game a plugin registers with MinigameCore, e.g. paintball. */
public interface GameType {
    /** Lower-case id matching {@code [a-z0-9_-]+}; every mode's {@code gameId} equals it. */
    String id();

    Component displayName();

    /** Menu and NPC icon. */
    Material icon();

    List<ModeSpec> modes();

    /** The game's own stat names (the core adds matches, wins, losses, draws), in display order. */
    default List<String> stats() {
        return List.of();
    }

    /** How a stat of this game is shown in /stats, /top and leaderboards. */
    default Component statName(String stat) {
        return Component.text(stat);
    }

    /** A new handler for one match; called when the match starts. */
    MatchHandler newMatch(MatchContext context);
}
