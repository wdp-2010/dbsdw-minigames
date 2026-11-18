package be.dbsdw.minigames.core.api;

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
}
