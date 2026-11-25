package be.dbsdw.minigames.core.menu;

import be.dbsdw.minigames.core.api.GameType;
import be.dbsdw.minigames.core.api.ModeSpec;
import java.util.Collection;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** What the queue menus show and do; implemented by the match manager. */
public interface MenuModel {
    Collection<GameType> games();

    /**
     * The public queue keys of the mode: just {@code mode.key()}, or one per team size (see
     * {@code GameType#queueSizes}).
     */
    List<String> queueKeys(ModeSpec mode);

    /** A queue's name, e.g. {@code TDM 2v2}. */
    Component queueName(String queueKey);

    /** Players a queue needs before its countdown starts. */
    int needed(String queueKey);

    int queued(String modeKey);

    OptionalInt secondsLeft(String modeKey);

    boolean anyArena(ModeSpec mode);

    /** Menu line saying the viewer is below the mode's level, or null when the mode is open to them. */
    default Component lockLore(Player viewer, ModeSpec mode) {
        return null;
    }

    boolean isQueued(UUID player, String modeKey);

    boolean inMatch(UUID player);

    /** Opens the progress menu: levels and what each level unlocks. */
    void openProgress(Player player);

    /** Joins or leaves the queue (by queue key) and tells the player. */
    void toggle(Player player, String modeKey);
}
