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

    /** A new handler for one match; called when the match starts. */
    MatchHandler newMatch(MatchContext context);
}
