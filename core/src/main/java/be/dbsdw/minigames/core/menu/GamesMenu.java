package be.dbsdw.minigames.core.menu;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.GameType;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Chest menu with one icon per registered game. */
public final class GamesMenu implements InventoryHolder {
    private static final int SIZE = 27;
    private static final int FIRST_SLOT = 10;

    private final Inventory inventory;
    private final Map<Integer, String> gameBySlot = new HashMap<>();

    private GamesMenu(MenuModel model, Messages messages) {
        inventory = Bukkit.createInventory(this, SIZE, messages.get("menu.games.title"));
        int slot = FIRST_SLOT;
        for (GameType game : model.games()) {
            if (slot >= SIZE) {
                break;
            }
            ItemStack icon = ItemStack.of(game.icon());
            ItemMeta meta = icon.getItemMeta();
            meta.itemName(game.displayName());
            icon.setItemMeta(meta);
            inventory.setItem(slot, icon);
            gameBySlot.put(slot, game.id());
            slot++;
        }
    }

    public static void open(Player player, MenuModel model, Messages messages) {
        player.openInventory(new GamesMenu(model, messages).inventory);
    }

    Optional<String> gameAt(int slot) {
        return Optional.ofNullable(gameBySlot.get(slot));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
