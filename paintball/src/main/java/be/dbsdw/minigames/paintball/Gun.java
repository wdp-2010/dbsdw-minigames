package be.dbsdw.minigames.paintball;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.pack.PackItems;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ChargedProjectiles;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import java.util.List;
import net.kyori.adventure.key.Key;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** The paintball gun and the paintball items, shown with the resource pack models in the team colour. */
public final class Gun {
    public static final NamespacedKey KEY = new NamespacedKey("paintball", "item");
    private static final String GUN = "gun";
    private static final String BALL = "ball";
    private static final String SCOPE = "scope";
    private static final String SPLASH = "splash";
    private static final String DUMMY = "dummy";
    private static final NamespacedKey PRACTICE = new NamespacedKey("paintball", "practice");

    private Gun() {
    }

    /**
     * The gun for a player of this team. A loaded crossbow underneath: the client then holds it up
     * with both arms, aiming (the only item it poses like that), and shows the cooldown animation.
     * The crossbow never fires: the plugin cancels every right-click with the gun.
     */
    public static ItemStack gun(Messages messages, int team) {
        ItemStack stack = PackItems.item(Material.CROSSBOW, PackAssets.GUN, PackAssets.teamRgb(team));
        stack.setData(DataComponentTypes.CHARGED_PROJECTILES,
                ChargedProjectiles.chargedProjectiles(List.of(ItemStack.of(Material.ARROW))));
        stack.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay()
                .addHiddenComponents(DataComponentTypes.CHARGED_PROJECTILES).build());
        ItemMeta meta = stack.getItemMeta();
        meta.itemName(messages.get("item.gun"));
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, GUN);
        stack.setItemMeta(meta);
        return stack;
    }

    /** The gun handed out on the practice range: a gun, marked so leaving the range takes only it. */
    public static ItemStack practiceGun(Messages messages, int team) {
        ItemStack stack = gun(messages, team);
        ItemMeta meta = stack.getItemMeta();
        meta.itemName(messages.get("item.practice-gun"));
        meta.getPersistentDataContainer().set(PRACTICE, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    public static boolean isPracticeGun(ItemStack stack) {
        return isGun(stack) && stack.getItemMeta().getPersistentDataContainer().has(PRACTICE);
    }

    /** Marks a practice-range dummy, so it can be told apart and protected. */
    public static void tagDummy(Entity entity) {
        entity.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, DUMMY);
    }

    public static boolean isDummy(Entity entity) {
        return DUMMY.equals(entity.getPersistentDataContainer().get(KEY, PersistentDataType.STRING));
    }

    public static boolean isGun(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.hasItemMeta()
                && GUN.equals(stack.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING));
    }

    /** The scope: worn on the head while aiming, it shows the scope overlay and nothing else. */
    public static ItemStack scope() {
        ItemStack stack = PackItems.item(Material.PAPER, PackAssets.SCOPE, null);
        stack.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(EquipmentSlot.HEAD)
                .cameraOverlay(Key.key(PackAssets.SCOPE_OVERLAY)).build());
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, SCOPE);
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * Paint on the goggles: {@code base} (a carried flag, which keeps showing on the head) or an
     * invisible item, worn on the head with a full-screen camera overlay. Swapping it makes no sound.
     */
    public static ItemStack splash(ItemStack base, String overlay) {
        ItemStack stack = base == null || base.isEmpty()
                ? PackItems.item(Material.PAPER, PackAssets.SCOPE, null) : base.clone();
        stack.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(EquipmentSlot.HEAD)
                .cameraOverlay(Key.key(overlay))
                .equipSound(Key.key("minecraft:intentionally_empty")).build());
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, SPLASH);
        stack.setItemMeta(meta);
        return stack;
    }

    public static boolean isSplash(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.hasItemMeta()
                && SPLASH.equals(stack.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING));
    }

    public static boolean isScope(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.hasItemMeta()
                && SCOPE.equals(stack.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING));
    }

    /** What a flying paintball of this team looks like. */
    public static ItemStack ball(int team) {
        return PackItems.item(Material.SNOWBALL, PackAssets.PAINTBALL,
                PackAssets.teamRgb(team));
    }

    /** The paint colour of a flying paintball's item, or null when it has none. */
    public static Color ballColour(ItemStack ball) {
        CustomModelData data = ball == null ? null : ball.getData(DataComponentTypes.CUSTOM_MODEL_DATA);
        return data == null || data.colors().isEmpty() ? null : data.colors().getFirst();
    }

    public static void tagBall(Entity ball) {
        ball.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, BALL);
    }

    public static boolean isBall(Entity entity) {
        return BALL.equals(entity.getPersistentDataContainer().get(KEY, PersistentDataType.STRING));
    }
}
