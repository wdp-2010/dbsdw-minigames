package be.dbsdw.minigames.core.menu;

import be.dbsdw.minigames.core.api.GameType;
import be.dbsdw.minigames.core.api.ModeSpec;
import java.util.Collection;
import java.util.OptionalInt;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** What the queue menus show and do; implemented by the match manager. */
public interface MenuModel {
    Collection<GameType> games();

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

    /** Joins or leaves the mode's queue and tells the player. */
    void toggle(Player player, String modeKey);
}
