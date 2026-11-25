package be.dbsdw.minigames.core.progress;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.GameType;
import be.dbsdw.minigames.core.api.ModeSpec;
import be.dbsdw.minigames.core.progress.UnlockLadder.Entry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Chest menu with the player's levels and what each level unlocks. Two screens: an overview (the
 * minigames level and one icon per game) and, per game, the ladder of its modes with what is still
 * locked and how much XP it takes, plus how XP is earned in that game.
 */
public final class ProgressMenu implements InventoryHolder {
    private static final int SIZE = 36;
    private static final int HEAD_SLOT = 4;
    private static final int BACK_SLOT = 27;
    private static final int EARN_SLOT = 31;
    /** Item slots, row by row: seven to a row in the middle of the chest. */
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    private static final int BAR_WIDTH = 10;

    private final ProgressService progress;
    private final Collection<GameType> games;
    private final Messages messages;
    private final Player viewer;
    private final Consumer<Player> back;
    private final Inventory inventory;
    private final Map<Integer, Runnable> actions = new HashMap<>();

    private ProgressMenu(ProgressService progress, Collection<GameType> games, Messages messages, Player viewer,
                         Consumer<Player> back, GameType game) {
        this.progress = progress;
        this.games = games;
        this.messages = messages;
        this.viewer = viewer;
        this.back = back;
        this.inventory = Bukkit.createInventory(this, SIZE, game == null
                ? messages.get("progress.menu.title")
                : messages.get("progress.menu.title-game", Placeholder.component("game", game.displayName())));
        fill();
        if (game == null) {
            overview();
        } else {
            gameScreen(game);
        }
    }

    /**
     * Opens the overview.
     *
     * @param back what the back arrow does (e.g. open the games menu), or null for no back arrow
     */
    public static void open(Player player, ProgressService progress, Collection<GameType> games, Messages messages,
                            Consumer<Player> back) {
        player.openInventory(new ProgressMenu(progress, games, messages, player, back, null).inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.3f);
    }

    /** A click in the menu (top inventory). */
    public void click(Player player, int slot) {
        Runnable action = actions.get(slot);
        if (action != null) {
            action.run();
        }
    }

    // ---------------------------------------------------------------- screens

    private void overview() {
        long account = Math.max(0, progress.xp(viewer.getUniqueId(), ProgressService.ACCOUNT));
        ItemStack head = ItemStack.of(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(viewer));
        label(head, messages.get("progress.menu.account",
                Placeholder.unparsed("level", String.valueOf(progress.curve().levelOf(account)))),
                levelLines(account, true));
        inventory.setItem(HEAD_SLOT, head);

        int i = 0;
        for (GameType game : games) {
            if (i >= SLOTS.length) {
                break;
            }
            List<Entry> ladder = ladder(game);
            long xp = Math.max(0, progress.xp(viewer.getUniqueId(), game.id()));
            List<Component> lore = levelLines(xp, false);
            lore.add(Component.empty());
            lore.add(messages.get(UnlockLadder.playable(ladder) == ladder.size()
                    ? "progress.menu.all-open" : "progress.menu.modes-open",
                    Placeholder.unparsed("open", String.valueOf(UnlockLadder.playable(ladder))),
                    Placeholder.unparsed("total", String.valueOf(ladder.size()))));
            lore.add(messages.get("progress.menu.click-game"));
            ItemStack icon = game.iconItem();
            label(icon, game.displayName(), lore);
            inventory.setItem(SLOTS[i], icon);
            actions.put(SLOTS[i], () -> open(game));
            i++;
        }
        if (back != null) {
            backButton(() -> back.accept(viewer));
        }
    }

    private void gameScreen(GameType game) {
        long xp = Math.max(0, progress.xp(viewer.getUniqueId(), game.id()));
        ItemStack head = game.iconItem();
        label(head, game.displayName(), levelLines(xp, false));
        inventory.setItem(HEAD_SLOT, head);

        List<Entry> ladder = ladder(game);
        for (int i = 0; i < ladder.size() && i < SLOTS.length; i++) {
            inventory.setItem(SLOTS[i], modeIcon(game, ladder.get(i)));
        }
        inventory.setItem(EARN_SLOT, earnIcon(game));
        backButton(() -> ProgressMenu.open(viewer, progress, games, messages, back));
    }

    // ---------------------------------------------------------------- items

    private ItemStack modeIcon(GameType game, Entry entry) {
        ModeSpec mode = entry.mode();
        Component name = MiniMessage.miniMessage().deserialize(mode.displayName());
        TagResolver[] need = {Placeholder.unparsed("need", String.valueOf(entry.need())),
                Placeholder.unparsed("missing", String.valueOf(entry.xpMissing())),
                Placeholder.component("game", game.displayName())};
        return switch (entry.state()) {
            case OPEN -> label(game.modeIcon(mode), name, List.of(messages.get("progress.menu.mode-open")));
            case UNLOCKED -> label(game.modeIcon(mode), name,
                    List.of(messages.get("progress.menu.mode-unlocked", need)));
            case NEXT -> {
                ItemStack icon = label(ItemStack.of(Material.GRAY_DYE), name,
                        List.of(messages.get("progress.menu.mode-next", need),
                                messages.get("progress.menu.mode-next-hint", need)));
                icon.editMeta(m -> m.setEnchantmentGlintOverride(true));
                yield icon;
            }
            case LOCKED -> label(ItemStack.of(Material.GRAY_DYE), name,
                    List.of(messages.get("progress.menu.mode-locked", need)));
        };
    }

    /** What earns XP in this game: the base, the win bonus, and each stat the game rewards. */
    private ItemStack earnIcon(GameType game) {
        XpRules rules = progress.rules();
        List<Component> lore = new ArrayList<>();
        lore.add(messages.get("progress.menu.earn-base", Placeholder.unparsed("xp", String.valueOf(rules.played()))));
        lore.add(messages.get("progress.menu.earn-result", Placeholder.unparsed("win", String.valueOf(rules.win())),
                Placeholder.unparsed("draw", String.valueOf(rules.draw()))));
        for (String stat : game.stats()) {
            int xp = game.xpPerStat(stat);
            if (xp > 0) {
                lore.add(messages.get("progress.menu.earn-stat", Placeholder.component("stat", game.statName(stat)),
                        Placeholder.unparsed("xp", String.valueOf(xp))));
            }
        }
        lore.add(Component.empty());
        lore.add(messages.get("progress.menu.earn-min", Placeholder.unparsed("seconds", String.valueOf(rules.minSeconds()))));
        return label(ItemStack.of(Material.EXPERIENCE_BOTTLE), messages.get("progress.menu.earn"), lore);
    }

    /** The level, its progress bar and what is missing for the next level. */
    private List<Component> levelLines(long xp, boolean account) {
        LevelCurve curve = progress.curve();
        int level = curve.levelOf(xp);
        long missing = curve.xpToNext(xp);
        List<Component> lines = new ArrayList<>();
        lines.add(messages.get(account ? "progress.menu.account-level" : "progress.menu.level",
                Placeholder.unparsed("level", String.valueOf(level))));
        lines.add(messages.get("progress.menu.bar", Placeholder.parsed("bar", ProgressBar.mini(curve.fraction(xp), BAR_WIDTH)),
                Placeholder.unparsed("percent", String.valueOf((int) Math.floor(curve.fraction(xp) * 100)))));
        lines.add(missing == 0 ? messages.get("progress.menu.max")
                : messages.get("progress.menu.to-next", Placeholder.unparsed("xp", String.valueOf(xp)),
                        Placeholder.unparsed("missing", String.valueOf(missing)),
                        Placeholder.unparsed("next", String.valueOf(level + 1))));
        return lines;
    }

    private List<Entry> ladder(GameType game) {
        return UnlockLadder.build(game.modes(), game::requiredLevel, progress.curve(),
                Math.max(0, progress.xp(viewer.getUniqueId(), game.id())));
    }

    private void open(GameType game) {
        viewer.openInventory(new ProgressMenu(progress, games, messages, viewer, back, game).inventory);
        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.3f);
    }

    private void backButton(Runnable action) {
        inventory.setItem(BACK_SLOT, label(ItemStack.of(Material.ARROW), messages.get("progress.menu.back"), List.of()));
        actions.put(BACK_SLOT, action);
    }

    private void fill() {
        ItemStack filler = ItemStack.of(Material.GRAY_STAINED_GLASS_PANE);
        filler.editMeta(m -> m.setHideTooltip(true));
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }
    }

    private static ItemStack label(ItemStack icon, Component name, List<Component> lore) {
        icon.editMeta(meta -> {
            meta.itemName(name);
            meta.lore(lore);
        });
        return icon;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
