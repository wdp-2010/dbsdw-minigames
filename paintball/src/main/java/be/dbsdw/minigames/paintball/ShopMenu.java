package be.dbsdw.minigames.paintball;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.pack.PackItems;
import be.dbsdw.minigames.paintball.rules.Loadout;
import be.dbsdw.minigames.paintball.rules.ShopItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * The shop (rooms with the shop on, the bomb mode): the player's cash on top, one button per item. Clicking an item buys
 * it through the match; the menu stays open and redraws, so a player can buy a few things in a row.
 */
public final class ShopMenu implements InventoryHolder {
    /** What the menu needs from the match the player is in. */
    public interface Shop {
        int cash(Player player);

        int owned(Player player, ShopItem item);

        /** Whether the player may buy the item now, and why not. */
        Loadout.Check check(Player player, ShopItem item);

        /** Buys it (the match takes the cash and hands out the item); the same check result. */
        Loadout.Check buy(Player player, ShopItem item);

        /** Whether the item is on sale in this mode at all. */
        boolean sells(ShopItem item);

        /** Shown under the cash, e.g. how long the buy phase still lasts; null for nothing. */
        Component note(Player player);
    }

    private static final int SIZE = 27;
    private static final int CASH_SLOT = 4;
    private static final Map<ShopItem, Integer> SLOTS = Map.of(
            ShopItem.VEST, 10, ShopItem.REPAIR, 11, ShopItem.BOOTS, 12, ShopItem.RAPID_CARTRIDGE, 13,
            ShopItem.TRIPLE_CARTRIDGE, 14, ShopItem.PAINT_GRENADE, 15, ShopItem.SMOKE_GRENADE, 16,
            ShopItem.DEFUSE_KIT, 22);

    private final Inventory inventory;
    private final Messages messages;
    private final Shop shop;

    private ShopMenu(Player player, Messages messages, Shop shop) {
        this.messages = messages;
        this.shop = shop;
        inventory = Bukkit.createInventory(this, SIZE, messages.get("shop.title"));
        draw(player);
    }

    public static void open(Player player, Messages messages, Shop shop) {
        player.openInventory(new ShopMenu(player, messages, shop).inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.2f);
    }

    private void draw(Player player) {
        ItemStack filler = ItemStack.of(Material.GRAY_STAINED_GLASS_PANE);
        filler.editMeta(m -> m.setHideTooltip(true));
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }
        ItemStack cash = ItemStack.of(Material.EMERALD);
        cash.editMeta(m -> {
            m.itemName(messages.get("shop.cash", Placeholder.unparsed("cash", Integer.toString(shop.cash(player)))));
            Component note = shop.note(player);
            m.lore(note == null ? List.of(messages.get("shop.cash-lore")) : List.of(messages.get("shop.cash-lore"), note));
        });
        inventory.setItem(CASH_SLOT, cash);
        for (ShopItem item : ShopItem.values()) {
            if (shop.sells(item)) {
                inventory.setItem(SLOTS.get(item), button(player, item));
            }
        }
    }

    private ItemStack button(Player player, ShopItem item) {
        ItemStack stack = PackItems.item(Material.PAPER, PackAssets.shopItem(item), null);
        Loadout.Check check = shop.check(player, item);
        int owned = shop.owned(player, item);
        stack.setAmount(Math.max(1, owned));
        String key = "shop.item." + item.key();
        stack.editMeta(m -> {
            m.itemName(messages.get(key + ".name"));
            List<Component> lore = new ArrayList<>();
            lore.add(messages.get(key + ".lore"));
            lore.add(messages.get("shop.price", Placeholder.unparsed("price", Integer.toString(item.price()))));
            if (item.kind() != ShopItem.Kind.INSTANT) {
                lore.add(messages.get("shop.owned", Placeholder.unparsed("owned", Integer.toString(owned)),
                        Placeholder.unparsed("max", Integer.toString(item.max()))));
            }
            lore.add(messages.get("shop.check." + check.name().toLowerCase()));
            m.lore(lore);
            m.setEnchantmentGlintOverride(check == Loadout.Check.OK ? Boolean.TRUE : null);
        });
        return stack;
    }

    /** A click in the menu: buy the item under the cursor, if any. */
    public void click(Player player, int rawSlot) {
        for (Map.Entry<ShopItem, Integer> e : SLOTS.entrySet()) {
            if (e.getValue() == rawSlot && shop.sells(e.getKey())) {
                Loadout.Check result = shop.buy(player, e.getKey());
                if (result == Loadout.Check.OK) {
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
                } else {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
                    player.sendActionBar(messages.get("shop.check." + result.name().toLowerCase()));
                }
                if (result == Loadout.Check.CLOSED) {
                    player.closeInventory();
                } else {
                    draw(player);
                }
                return;
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
