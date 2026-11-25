package be.dbsdw.minigames.core.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
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

    /**
     * Team sizes (players per team) this mode's public queue is split into, e.g. {@code [1, 2, 3, 4]}
     * for 1v1 to 4v4: each size gets its own queue that starts with exactly that many per team, and
     * the modes menu lets players pick one. Empty (the default): one queue from the mode's minimum
     * to its maximum. Rooms always use the mode itself.
     */
    default List<Integer> queueSizes(ModeSpec mode) {
        return List.of();
    }

    /**
     * Whether the mode has a public queue. False: it is played in private rooms only, and the modes
     * menu says so. True by default.
     */
    default boolean publicQueue(ModeSpec mode) {
        return true;
    }

    /** The game's own stat names (the core adds matches, wins, losses, draws), in display order. */
    default List<String> stats() {
        return List.of();
    }

    /** XP a player earns for each count of this stat in a match (0 for stats that give none). */
    default int xpPerStat(String stat) {
        return 0;
    }

    /**
     * The level in this game a player needs to play the mode (1 or less: no level needed). The core
     * keeps players below it out of the queue and out of rooms they host.
     */
    default int requiredLevel(ModeSpec mode) {
        return 0;
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

    /**
     * Whether {@code /solo} lists this game: it can be played alone, against a bot, through
     * {@link #startAlone}. False by default.
     */
    default boolean supportsSolo() {
        return false;
    }

    /** An extra button in this game's modes menu for this player, or empty. */
    default Optional<MenuEntry> menuEntry(Player viewer) {
        return Optional.empty();
    }

    /** A button in a game's modes menu: its icon, and what a click does (the menu is closed first). */
    record MenuEntry(ItemStack icon, Consumer<Player> onClick) {}

    /**
     * The host of a private room pressed start while alone in it. Return true when the game takes this
     * over (e.g. opens a menu to pick a bot's difficulty); it then calls {@code startOnTeam} with the
     * team the host plays on, which starts the room's countdown with only that team filled. Return false
     * (the default) to start short-handed as usual.
     */
    default boolean startAlone(Player host, ModeSpec mode, IntConsumer startOnTeam) {
        return false;
    }

    /**
     * The levels of the bots a private room's host can add to the teams of this mode, easiest first.
     * Empty (the default) when the game has no such bots.
     */
    default List<Component> roomBotLevels(ModeSpec mode) {
        return List.of();
    }

    /**
     * A room match of this game is about to start with bots the host added: {@code perTeam} bots per
     * team at the level with this index in {@link #roomBotLevels}. {@code players} are the match's
     * players. Called with only zeros when that start fell through, to forget the bots again.
     */
    default void roomBots(List<UUID> players, ModeSpec mode, List<Integer> perTeam, int level) {}

    /**
     * On/off settings a private room's host can switch for this mode, e.g. paintball's shop. All start
     * off; public queue matches always play with every option off. A running match reads them through
     * {@link MatchContext#option}. Empty (the default): none.
     */
    default List<RoomOption> roomOptions(ModeSpec mode) {
        return List.of();
    }

    /**
     * One on/off room setting.
     *
     * @param id lower-case id the match asks for through {@link MatchContext#option}
     * @param name shown on the room menu button
     * @param lore what the option does, under the name
     * @param icon the button's item
     * @param requiredLevel the level in this game the host needs to switch it on (1 or less: none)
     */
    record RoomOption(String id, Component name, List<Component> lore, ItemStack icon, int requiredLevel) {
        public RoomOption {
            lore = List.copyOf(lore);
        }
    }

    /** A new handler for one match; called when the match starts. */
    MatchHandler newMatch(MatchContext context);
}
