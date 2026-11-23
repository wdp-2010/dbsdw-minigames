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

    /**
     * Moves a player of this match to another team, e.g. a hider who was found joins the seekers.
     * Stats and the result count the new team. The core does not end the match when a team runs
     * empty this way: the game decides. False when the match is not running, the player is not in it
     * or already in that team.
     */
    boolean moveToTeam(Player player, int team);

    /** Ticks since the match started. */
    long ticks();

    /**
     * Adds to a stat of this game for the player (e.g. "eliminations"). Saved when the match ends;
     * the core itself counts matches, wins, losses and draws.
     */
    void addStat(Player player, String stat, long amount);

    /** Ends the match with this outcome; ignored after the first call. */
    void finish(MatchOutcome outcome);
}
