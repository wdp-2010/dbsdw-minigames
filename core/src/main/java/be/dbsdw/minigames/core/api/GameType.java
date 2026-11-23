package be.dbsdw.minigames.core.api;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** A game a plugin registers with MinigameCore, e.g. paintball. */
public interface GameType {
    /** Lower-case id matching {@code [a-z0-9_-]+}; every mode's {@code gameId} equals it. */
    String id();

    Component displayName();

    /** Menu and NPC icon. */
    Material icon();

    /** The icon as shown in the games menu; games with pack models override this. */
    default ItemStack iconItem() {
        return ItemStack.of(icon());
    }

    /** The icon of one mode in the modes menu; by default the game's icon. */
    default ItemStack modeIcon(ModeSpec mode) {
        return iconItem();
    }

    List<ModeSpec> modes();

    /** The game's own stat names (the core adds matches, wins, losses, draws), in display order. */
    default List<String> stats() {
        return List.of();
    }

    /** How a stat of this game is shown in /stats, /top and leaderboards. */
    default Component statName(String stat) {
        return Component.text(stat);
    }

    /** A team's name in this game's matches (e.g. White and Black), or null for the core's team names. */
    default Component teamName(ModeSpec mode, int team) {
        return null;
    }

    /**
     * A core text (key from MinigameCore's messages.yml, e.g. {@code match.start}) in this game's own
     * words, or null to use the core's text. Gets the same placeholders as the core text.
     */
    default Component coreText(String key, TagResolver... resolvers) {
        return null;
    }

    /** Why this player may not queue for the mode right now (shown to them), or null if they may. */
    default Component queueBlock(Player player, ModeSpec mode) {
        return null;
    }

    /** An extra button in this game's modes menu for this player, or empty. */
    default Optional<MenuEntry> menuEntry(Player viewer) {
        return Optional.empty();
    }

    /** A button in a game's modes menu: its icon, and what a click does (the menu is closed first). */
    record MenuEntry(ItemStack icon, Consumer<Player> onClick) {}

    /** A new handler for one match; called when the match starts. */
    MatchHandler newMatch(MatchContext context);
}
