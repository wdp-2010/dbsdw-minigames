package be.dbsdw.minigames.paintball;

import be.dbsdw.minigames.core.Messages;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** The paintball gun and the paintball items. Placeholder materials until the resource pack (plan 6). */
public final class Gun {
    public static final NamespacedKey KEY = new NamespacedKey("paintball", "item");
    private static final String GUN = "gun";
    private static final String BALL = "ball";

    private Gun() {
    }

    public static ItemStack gun(Messages messages) {
        ItemStack stack = ItemStack.of(Material.CARROT_ON_A_STICK);
        ItemMeta meta = stack.getItemMeta();
        meta.itemName(messages.get("item.gun"));
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, GUN);
        stack.setItemMeta(meta);
        return stack;
    }

    public static boolean isGun(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.hasItemMeta()
                && GUN.equals(stack.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING));
    }

    /** What a flying paintball of this team looks like. */
    public static ItemStack ball(int team) {
        return ItemStack.of(team == 0 ? Material.RED_DYE : Material.BLUE_DYE);
    }

    public static void tagBall(Entity ball) {
        ball.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, BALL);
    }

    public static boolean isBall(Entity entity) {
        return BALL.equals(entity.getPersistentDataContainer().get(KEY, PersistentDataType.STRING));
    }
}
