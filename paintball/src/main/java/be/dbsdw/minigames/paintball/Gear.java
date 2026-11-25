package be.dbsdw.minigames.paintball;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.pack.PackItems;
import be.dbsdw.minigames.paintball.rules.ShopItem;
import java.util.List;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

/** The items bought in the shop and the bomb, as they sit in the hotbar or on the body. */
public final class Gear {
    private static final String PREFIX = "gear:";
    private static final String BOMB = "bomb";
    /** Olive drab, like the rest of the army gear. */
    private static final Color OLIVE = Color.fromRGB(0x62, 0x6C, 0x38);

    /** Hotbar slots: gun 0, the bomb 1, used items 2-5, the defuse kit 6, the shop 8. */
    public static final int BOMB_SLOT = 1;

    private Gear() {
    }

    public static int slot(ShopItem item) {
        return switch (item) {
            case RAPID_CARTRIDGE -> 2;
            case TRIPLE_CARTRIDGE -> 3;
            case PAINT_GRENADE -> 4;
            case SMOKE_GRENADE -> 5;
            case DEFUSE_KIT -> 6;
            default -> -1;
        };
    }

    /** The item for a bought shop item: a hotbar item, or the vest and boots worn. */
    public static ItemStack item(Messages messages, ShopItem item, int count) {
        Material base = switch (item) {
            case VEST -> Material.LEATHER_CHESTPLATE;
            case BOOTS -> Material.LEATHER_BOOTS;
            default -> Material.PAPER;
        };
        ItemStack stack = PackItems.item(base, PackAssets.shopItem(item), null);
        stack.setAmount(Math.max(1, count));
        ItemMeta meta = stack.getItemMeta();
        meta.itemName(messages.get("shop.item." + item.key() + ".name"));
        meta.lore(List.of(messages.get("shop.item." + item.key() + ".use")));
        meta.getPersistentDataContainer().set(Gun.KEY, PersistentDataType.STRING, PREFIX + item.key());
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(OLIVE);
            leather.setUnbreakable(true);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    /** The bomb, carried by one attacker. */
    public static ItemStack bomb(Messages messages) {
        ItemStack stack = PackItems.item(Material.PAPER, PackAssets.BOMB, null);
        ItemMeta meta = stack.getItemMeta();
        meta.itemName(messages.get("bomb.item"));
        meta.lore(List.of(messages.get("bomb.item-lore")));
        meta.getPersistentDataContainer().set(Gun.KEY, PersistentDataType.STRING, PREFIX + BOMB);
        stack.setItemMeta(meta);
        return stack;
    }

    /** The planted or dropped bomb on the ground (an item display), with no name or lore. */
    public static ItemStack bombModel() {
        return PackItems.item(Material.PAPER, PackAssets.BOMB, null);
    }

    /** A thrown grenade as it flies. */
    public static ItemStack thrown(ShopItem item) {
        return PackItems.item(Material.SNOWBALL, PackAssets.shopItem(item), null);
    }

    private static String tag(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasItemMeta()) {
            return null;
        }
        String tag = stack.getItemMeta().getPersistentDataContainer().get(Gun.KEY, PersistentDataType.STRING);
        return tag != null && tag.startsWith(PREFIX) ? tag.substring(PREFIX.length()) : null;
    }

    /** The shop item this stack is, or null. */
    public static ShopItem of(ItemStack stack) {
        String tag = tag(stack);
        if (tag == null || tag.equals(BOMB)) {
            return null;
        }
        for (ShopItem item : ShopItem.values()) {
            if (item.key().equals(tag)) {
                return item;
            }
        }
        return null;
    }

    public static boolean isBomb(ItemStack stack) {
        return BOMB.equals(tag(stack));
    }

    /** A bought item or the bomb: kept in place like the gun. */
    public static boolean isGear(ItemStack stack) {
        return tag(stack) != null;
    }
}
