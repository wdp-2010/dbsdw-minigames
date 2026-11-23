package be.dbsdw.minigames.chess.menu;

import be.dbsdw.minigames.chess.ChessGame;
import be.dbsdw.minigames.chess.PackAssets;
import be.dbsdw.minigames.chess.bot.BotLevel;
import be.dbsdw.minigames.chess.rules.Piece;
import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.ModeSpec;
import be.dbsdw.minigames.core.pack.PackItems;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Play against the bot: pick its difficulty, your colour and (outside a room) the time control, then
 * start. Opened by {@code /schaakbot}, and when a room's host presses start alone in a chess room (the
 * room has its mode already, so that row is left out and start begins the room's countdown).
 */
public final class BotMenu implements InventoryHolder {
    public enum Colour { WHITE, RANDOM, BLACK }

    /** What the player picked; remembered for next time. */
    public record Choice(BotLevel level, Colour colour, String modeId) {}

    /** What the start button does in a room: the level and whether the player plays white. */
    public interface RoomStart {
        void start(BotLevel level, boolean white);
    }

    private static final int SIZE = 45;
    private static final int[] LEVEL_SLOTS = {10, 12, 14, 16};
    private static final int[] COLOUR_SLOTS = {20, 22, 24};
    private static final int MODE_ROW = 28;
    private static final int START_SLOT = 40;

    private final Inventory inventory;
    private final ChessGame game;
    private final Messages messages;
    private final ModeSpec roomMode;
    private final RoomStart roomStart;
    private final List<ModeSpec> modes;
    private Choice choice;

    private BotMenu(Player player, ChessGame game, ModeSpec roomMode, RoomStart roomStart) {
        this.game = game;
        this.messages = game.messages();
        this.roomMode = roomMode;
        this.roomStart = roomStart;
        this.modes = game.modes();
        Choice last = game.botChoice(player.getUniqueId());
        String modeId = roomMode != null ? roomMode.id()
                : modes.stream().anyMatch(m -> m.id().equals(last.modeId())) ? last.modeId() : modes.getFirst().id();
        this.choice = new Choice(last.level(), last.colour(), modeId);
        this.inventory = Bukkit.createInventory(this, SIZE, messages.get("bot.menu.title"));
        draw();
    }

    /**
     * Opens the menu. {@code roomMode} and {@code roomStart} are set when a room's host started alone:
     * the mode is fixed and start hands the choice to the room. Both null: start puts the player on a
     * board at once.
     */
    public static void open(Player player, ChessGame game, ModeSpec roomMode, RoomStart roomStart) {
        player.openInventory(new BotMenu(player, game, roomMode, roomStart).inventory);
    }

    private void draw() {
        inventory.clear();
        ItemStack filler = ItemStack.of(Material.BROWN_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.setHideTooltip(true);
        filler.setItemMeta(fm);
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }
        inventory.setItem(1, named(ItemStack.of(Material.BOOK), messages.get("bot.menu.level-header"), List.of()));
        BotLevel[] levels = BotLevel.values();
        for (int i = 0; i < levels.length; i++) {
            BotLevel l = levels[i];
            ItemStack icon = PackItems.item(Material.PAPER, PackAssets.piece(-l.icon()), null);
            inventory.setItem(LEVEL_SLOTS[i], pick(icon, messages.get("bot.level." + l.id()),
                    messages.get("bot.menu.level-lore." + l.id()), choice.level() == l));
        }
        Colour[] colours = Colour.values();
        for (int i = 0; i < colours.length; i++) {
            Colour c = colours[i];
            ItemStack icon = switch (c) {
                case WHITE -> PackItems.item(Material.PAPER, PackAssets.piece(Piece.KING), null);
                case BLACK -> PackItems.item(Material.PAPER, PackAssets.piece(-Piece.KING), null);
                case RANDOM -> ItemStack.of(Material.COMPASS);
            };
            String key = c.name().toLowerCase(java.util.Locale.ROOT);
            inventory.setItem(COLOUR_SLOTS[i], pick(icon, messages.get("bot.menu.colour." + key),
                    messages.get("bot.menu.colour-lore." + key), choice.colour() == c));
        }
        if (roomMode == null) {
            int first = MODE_ROW + (7 - modes.size()) / 2;
            for (int i = 0; i < modes.size() && i < 7; i++) {
                ModeSpec m = modes.get(i);
                inventory.setItem(first + i, pick(game.modeIcon(m), MiniMessage.miniMessage().deserialize(m.displayName()),
                        messages.get("bot.menu.mode-lore"), choice.modeId().equals(m.id())));
            }
        } else {
            inventory.setItem(MODE_ROW + 3, named(game.modeIcon(roomMode),
                    MiniMessage.miniMessage().deserialize(roomMode.displayName()), List.of(messages.get("bot.menu.room-mode"))));
        }
        inventory.setItem(START_SLOT, named(ItemStack.of(Material.LIME_CONCRETE), messages.get("bot.menu.start"),
                List.of(messages.get("bot.menu.start-lore", Placeholder.parsed("level", messages.raw("bot.level." + choice.level().id()))))));
    }

    private ItemStack pick(ItemStack icon, Component name, Component lore, boolean chosen) {
        List<Component> lines = new ArrayList<>();
        lines.add(lore);
        lines.add(messages.get(chosen ? "bot.menu.chosen" : "bot.menu.click"));
        ItemStack out = named(icon, name, lines);
        if (chosen) {
            ItemMeta meta = out.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            out.setItemMeta(meta);
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
                choose(player, new Choice(BotLevel.values()[i], choice.colour(), choice.modeId()));
                return;
            }
        }
        for (int i = 0; i < COLOUR_SLOTS.length; i++) {
            if (slot == COLOUR_SLOTS[i]) {
                choose(player, new Choice(choice.level(), Colour.values()[i], choice.modeId()));
                return;
            }
        }
        if (roomMode == null) {
            int first = MODE_ROW + (7 - modes.size()) / 2;
            int i = slot - first;
            if (i >= 0 && i < modes.size() && i < 7) {
                choose(player, new Choice(choice.level(), choice.colour(), modes.get(i).id()));
                return;
            }
        }
        if (slot == START_SLOT) {
            player.closeInventory();
            boolean white = switch (choice.colour()) {
                case WHITE -> true;
                case BLACK -> false;
                case RANDOM -> ThreadLocalRandom.current().nextBoolean();
            };
            if (roomStart != null) {
                roomStart.start(choice.level(), white);
            } else {
                game.startBot(player, choice.level(), white, choice.modeId());
            }
        }
    }

    private void choose(Player player, Choice next) {
        choice = next;
        game.rememberBotChoice(player.getUniqueId(), next);
        draw();
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
