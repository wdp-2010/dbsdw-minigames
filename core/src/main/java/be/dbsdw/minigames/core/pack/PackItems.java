package be.dbsdw.minigames.core.pack;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Helpers for any game to show resource pack items and play pack sounds. */
public final class PackItems {
    private PackItems() {
    }

    /**
     * An item showing a pack model. {@code tint} (RGB, or null) is custom_model_data colour 0,
     * {@code floats} are custom_model_data floats (e.g. a splat's shape and fade stage).
     */
    public static ItemStack item(Material base, String model, Integer tint, float... floats) {
        ItemStack stack = ItemStack.of(base);
        stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(model));
        if (tint != null || floats.length > 0) {
            CustomModelData.Builder data = CustomModelData.customModelData();
            if (tint != null) {
                data.addColor(Color.fromRGB(tint));
            }
            for (float f : floats) {
                data.addFloat(f);
            }
            stack.setData(DataComponentTypes.CUSTOM_MODEL_DATA, data.build());
        }
        return stack;
    }

    /** Plays a pack sound to one player only. */
    public static void play(Player player, String sound, float volume, float pitch) {
        player.playSound(Sound.sound(Key.key(sound), Sound.Source.PLAYER, volume, pitch));
    }

    /** Plays a pack sound in the world, for everyone near the location. */
    public static void play(Location at, String sound, float volume, float pitch) {
        at.getWorld().playSound(Sound.sound(Key.key(sound), Sound.Source.PLAYER, volume, pitch),
                at.getX(), at.getY(), at.getZ());
    }
}
