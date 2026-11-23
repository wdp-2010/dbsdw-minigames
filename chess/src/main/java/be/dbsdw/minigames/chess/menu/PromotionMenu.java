package be.dbsdw.minigames.chess.menu;

import be.dbsdw.minigames.chess.PackAssets;
import be.dbsdw.minigames.chess.rules.Piece;
import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.pack.PackItems;
import java.util.Optional;
import java.util.function.IntConsumer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Pick what a pawn on the last rank becomes. Closing the menu cancels the move. */
public final class PromotionMenu implements InventoryHolder {
    private static final int[] TYPES = {Piece.QUEEN, Piece.ROOK, Piece.BISHOP, Piece.KNIGHT};
    private static final int FIRST_SLOT = 1;

    private final Inventory inventory;
    private final IntConsumer onPick;

    private PromotionMenu(Messages messages, boolean white, IntConsumer onPick) {
        this.onPick = onPick;
        this.inventory = Bukkit.createInventory(this, 9, messages.get("menu.promotion.title"));
        for (int i = 0; i < TYPES.length; i++) {
            ItemStack icon = PackItems.item(Material.PAPER, PackAssets.piece(Piece.of(TYPES[i], white)), null);
            ItemMeta meta = icon.getItemMeta();
            meta.itemName(messages.get("piece." + Piece.name(TYPES[i])));
            icon.setItemMeta(meta);
            inventory.setItem(FIRST_SLOT + 2 * i, icon);
        }
    }

    /** Opens the menu; {@code onPick} gets the chosen piece type. */
    public static void open(Player player, Messages messages, boolean white, IntConsumer onPick) {
        player.openInventory(new PromotionMenu(messages, white, onPick).inventory);
    }

    /** The piece type in this slot, if any. */
    public Optional<Integer> typeAt(int slot) {
        int i = (slot - FIRST_SLOT) / 2;
        if (slot < FIRST_SLOT || (slot - FIRST_SLOT) % 2 != 0 || i >= TYPES.length) {
            return Optional.empty();
        }
        return Optional.of(TYPES[i]);
    }

    public void pick(int type) {
        onPick.accept(type);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
