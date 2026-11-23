package be.dbsdw.minigames.chess.match;

import be.dbsdw.minigames.core.Messages;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** The chess hotbar: pick pieces, offer a draw, pause, resign; claim or keep while the opponent is away. */
public final class Hotbar {
    public enum Action {
        SELECT(Material.STICK, 0),
        DRAW(Material.PAPER, 6),
        PAUSE(Material.CLOCK, 7),
        RESIGN(Material.WHITE_BANNER, 8),
        CLAIM(Material.GOLD_INGOT, 6),
        KEEP(Material.WRITABLE_BOOK, 7);

        private final Material material;
        private final int slot;

        Action(Material material, int slot) {
            this.material = material;
            this.slot = slot;
        }
    }

    private static final NamespacedKey KEY = new NamespacedKey("dbsdw", "chess_action");

    private Hotbar() {}

    /** Hotbar while playing; with {@code away} the claim and keep buttons instead of draw and pause. */
    static void give(Player player, Messages messages, boolean away) {
        PlayerInventory inv = player.getInventory();
        inv.clear();
        put(inv, Action.SELECT, messages);
        if (away) {
            put(inv, Action.CLAIM, messages);
            put(inv, Action.KEEP, messages);
        } else {
            put(inv, Action.DRAW, messages);
            put(inv, Action.PAUSE, messages);
            put(inv, Action.RESIGN, messages);
        }
        inv.setHeldItemSlot(0);
    }

    private static void put(PlayerInventory inv, Action action, Messages messages) {
        ItemStack stack = ItemStack.of(action.material);
        ItemMeta meta = stack.getItemMeta();
        String key = "item." + action.name().toLowerCase();
        meta.itemName(messages.get(key));
        meta.lore(java.util.List.of(messages.get(key + "-lore")));
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, action.name());
        stack.setItemMeta(meta);
        inv.setItem(action.slot, stack);
    }

    /** The action of a chess hotbar item, if it is one. */
    public static Optional<Action> action(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        String name = stack.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (name == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Action.valueOf(name));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
