package be.dbsdw.minigames.core.api;

import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** A game's logic for one match. The core calls these on the main thread. */
public interface MatchHandler {
    /** Players are split into teams, teleported to their team spawns and reset. */
    void start();

    /** Every server tick while the match is running. */
    void tick();

    /** After the player was removed from the match (quit, /leave, transfer). */
    void playerLeft(Player player, int team);

    /**
     * Exactly once when the match stops for any reason (win, draw, last team standing, abort,
     * shutdown). Remove everything the game spawned.
     */
    void end();

    /**
     * Whether the match goes on when only one team still has players. By default the core ends it
     * right away as a forfeit; a game that returns true decides itself (with {@link MatchContext#finish})
     * and may take players back with {@link MatchContext#rejoin}. When nobody at all is left the core
     * pauses the match if the game has not ended it in {@link #playerLeft}.
     */
    default boolean keepsRunningWhenAlone() {
        return false;
    }

    /** Shared result lines when the core ends the match itself (last team standing). */
    default List<Component> resultLines() {
        return List.of();
    }

    /** One line for this player after the result lines (their own numbers), or null. */
    default Component personalLine(UUID player) {
        return null;
    }
}
