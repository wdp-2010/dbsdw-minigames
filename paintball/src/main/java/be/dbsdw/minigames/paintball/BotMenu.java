package be.dbsdw.minigames.paintball;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.ModeSpec;
import be.dbsdw.minigames.paintball.bot.BotLevel;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Paintball against bots: pick how well the bots play and how big the teams are (your teammates are
 * bots too), then start. Opened when a room's host presses start alone in a paintball room, and so
 * by {@code /solo}.
 */
public final class BotMenu implements InventoryHolder {
    /** What the player picked; remembered for next time. */
    public record Choice(BotLevel level, int perTeam) {}

    /** What start does: the room's countdown with these bots. */
    public interface Start {
        void start(Choice choice);
    }

    private static final int SIZE = 36;
    private static final int[] LEVEL_SLOTS = {10, 12, 14, 16};
    private static final int[] SIZE_SLOTS = {19, 21, 23, 25};
    private static final int START_SLOT = 31;
    private static final int NO_BOTS_SLOT = 33;
    private static final Material[] LEVEL_ICONS = {
        Material.LIME_DYE, Material.YELLOW_DYE, Material.ORANGE_DYE, Material.RED_DYE,
    };

    private final Inventory inventory;
    private final PaintballGame game;
    private final Messages messages;
    private final int maxPerTeam;
    private final Start start;
    /** In a room with other players: starts without bots; null when playing alone. */
    private final Runnable noBots;
    private Choice choice;

    private BotMenu(Player player, PaintballGame game, ModeSpec mode, Start start, Runnable noBots) {
        this.game = game;
        this.messages = game.messages();
        this.maxPerTeam = Math.min(SIZE_SLOTS.length, mode.maxPerTeam());
        this.start = start;
        this.noBots = noBots;
        Choice last = game.botChoice(player.getUniqueId());
        this.choice = new Choice(last.level(), Math.min(last.perTeam(), maxPerTeam));
        this.inventory = Bukkit.createInventory(this, SIZE, messages.get("bot.menu.title"));
        draw();
    }

    public static void open(Player player, PaintballGame game, ModeSpec mode, Start start) {
        player.openInventory(new BotMenu(player, game, mode, start, null).inventory);
    }

    /** For a room with other players: bots fill the teams up to the chosen size; or start without bots. */
    public static void openForRoom(Player player, PaintballGame game, ModeSpec mode, Start start, Runnable noBots) {
        player.openInventory(new BotMenu(player, game, mode, start, noBots).inventory);
    }

    private void draw() {
        inventory.clear();
        ItemStack filler = ItemStack.of(Material.GRAY_STAINED_GLASS_PANE);
        filler.editMeta(m -> m.setHideTooltip(true));
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }
        BotLevel[] levels = BotLevel.values();
        for (int i = 0; i < levels.length; i++) {
            BotLevel l = levels[i];
            inventory.setItem(LEVEL_SLOTS[i], pick(ItemStack.of(LEVEL_ICONS[i]), messages.get("bot.level." + l.id()),
                    messages.get("bot.menu.level-lore." + l.id()), choice.level() == l));
        }
        for (int n = 1; n <= maxPerTeam; n++) {
            ItemStack icon = ItemStack.of(Material.SNOWBALL, n);
            inventory.setItem(SIZE_SLOTS[n - 1], pick(icon,
                    messages.get("bot.menu.size", Placeholder.unparsed("n", Integer.toString(n))),
                    noBots != null
                            ? messages.get("bot.menu.size-lore-room", Placeholder.unparsed("n", Integer.toString(n)))
                            : messages.get(n == 1 ? "bot.menu.size-lore-alone" : "bot.menu.size-lore",
                                    Placeholder.unparsed("allies", Integer.toString(n - 1)),
                                    Placeholder.unparsed("enemies", Integer.toString(n))),
                    choice.perTeam() == n));
        }
        inventory.setItem(START_SLOT, named(ItemStack.of(Material.LIME_CONCRETE), messages.get("bot.menu.start"),
                List.of(messages.get("bot.menu.start-lore",
                        Placeholder.component("level", messages.get("bot.level." + choice.level().id())),
                        Placeholder.unparsed("n", Integer.toString(choice.perTeam()))))));
        drawNoBots();
    }

    private void drawNoBots() {
        if (noBots != null) {
            inventory.setItem(NO_BOTS_SLOT, named(ItemStack.of(Material.BARRIER), messages.get("bot.menu.no-bots"),
                    List.of(messages.get("bot.menu.no-bots-lore"))));
        }
    }

    private ItemStack pick(ItemStack icon, Component name, Component lore, boolean chosen) {
        List<Component> lines = new ArrayList<>();
        lines.add(lore);
        lines.add(messages.get(chosen ? "bot.menu.chosen" : "bot.menu.click"));
        ItemStack out = named(icon, name, lines);
        if (chosen) {
            out.editMeta(m -> m.setEnchantmentGlintOverride(true));
        }
        return out;
    }

    private static ItemStack named(ItemStack icon, Component name, List<Component> lore) {
        ItemMeta meta = icon.getItemMeta();
        meta.itemName(name);
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    /** A click in the menu (top inventory). */
    public void click(Player player, int slot) {
        for (int i = 0; i < LEVEL_SLOTS.length; i++) {
            if (slot == LEVEL_SLOTS[i]) {
                choose(player, new Choice(BotLevel.values()[i], choice.perTeam()));
                return;
            }
        }
        for (int n = 1; n <= maxPerTeam; n++) {
            if (slot == SIZE_SLOTS[n - 1]) {
                choose(player, new Choice(choice.level(), n));
                return;
            }
        }
        if (slot == NO_BOTS_SLOT && noBots != null) {
            player.closeInventory();
            noBots.run();
            return;
        }
        if (slot == START_SLOT) {
            player.closeInventory();
            start.start(choice);
        }
    }

    private void choose(Player player, Choice next) {
        choice = next;
        game.rememberBotChoice(player.getUniqueId(), next);
        draw();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
