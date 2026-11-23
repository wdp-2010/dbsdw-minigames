package be.dbsdw.minigames.chess.menu;

import be.dbsdw.minigames.chess.ChessGame;
import be.dbsdw.minigames.chess.PackAssets;
import be.dbsdw.minigames.chess.rules.ChessClock;
import be.dbsdw.minigames.chess.rules.Piece;
import be.dbsdw.minigames.chess.save.SavedGame;
import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.pack.PackItems;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The player's unfinished games: click to continue one (the opponent is invited), shift-click twice
 * to delete it without a result.
 */
public final class GamesMenu implements InventoryHolder {
    private static final int SIZE = 27;
    private static final int FIRST_SLOT = 10;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d/M HH:mm").withZone(ZoneId.of("Europe/Brussels"));

    private final ChessGame game;
    private final Inventory inventory;
    private final Map<Integer, String> gameAt = new HashMap<>();
    /** The game a first shift-click asked to delete. */
    private String armedDelete;

    private GamesMenu(ChessGame game, Player viewer) {
        this.game = game;
        Messages messages = game.messages();
        this.inventory = Bukkit.createInventory(this, SIZE, messages.get("menu.games.title"));
        List<SavedGame> games = game.store().of(viewer.getUniqueId());
        if (games.isEmpty()) {
            ItemStack none = ItemStack.of(Material.BARRIER);
            ItemMeta meta = none.getItemMeta();
            meta.itemName(messages.get("menu.games.none"));
            none.setItemMeta(meta);
            inventory.setItem(13, none);
        }
        int slot = FIRST_SLOT;
        for (SavedGame g : games) {
            if (slot >= FIRST_SLOT + 7) {
                break;
            }
            inventory.setItem(slot, icon(messages, g, viewer));
            gameAt.put(slot, g.id());
            slot++;
        }
    }

    private static ItemStack icon(Messages messages, SavedGame g, Player viewer) {
        boolean white = g.white().equals(viewer.getUniqueId());
        ItemStack icon = PackItems.item(Material.PAPER, PackAssets.piece(Piece.of(Piece.KING, white)), null);
        ItemMeta meta = icon.getItemMeta();
        meta.itemName(messages.get("menu.games.item", Placeholder.unparsed("player", g.opponentName(viewer.getUniqueId()))));
        boolean yourTurn = g.whiteToMove() == white;
        List<Component> lore = new ArrayList<>();
        lore.add(messages.get(white ? "menu.games.you-white" : "menu.games.you-black"));
        lore.add(messages.get("menu.games.moves", Placeholder.unparsed("moves", String.valueOf(g.moves().size()))));
        lore.add(messages.get(yourTurn ? "menu.games.your-turn" : "menu.games.their-turn"));
        if (g.initialMillis() > 0) {
            lore.add(messages.get("menu.games.clock", Placeholder.unparsed("white", ChessClock.format(g.whiteMillis())),
                    Placeholder.unparsed("black", ChessClock.format(g.blackMillis()))));
        }
        lore.add(messages.get("menu.games.when", Placeholder.unparsed("when", WHEN.format(Instant.ofEpochMilli(g.updatedAt())))));
        lore.add(Component.empty());
        lore.add(messages.get("menu.games.click"));
        lore.add(messages.get("menu.games.shift-click"));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    public static void open(Player player, ChessGame game) {
        player.openInventory(new GamesMenu(game, player).inventory);
    }

    public void click(Player player, int slot, boolean shift) {
        Optional<String> id = Optional.ofNullable(gameAt.get(slot));
        if (id.isEmpty()) {
            return;
        }
        if (shift) {
            if (id.get().equals(armedDelete)) {
                player.closeInventory();
                game.delete(player, id.get());
            } else {
                armedDelete = id.get();
                player.sendActionBar(game.messages().get("menu.delete-confirm"));
            }
            return;
        }
        player.closeInventory();
        game.requestResume(player, id.get());
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
