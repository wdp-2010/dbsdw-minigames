package be.dbsdw.minigames.paintball.match;

import be.dbsdw.minigames.core.pack.PackItems;
import be.dbsdw.minigames.paintball.PackAssets;
import be.dbsdw.minigames.paintball.rules.PowerupType;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/** Floating, spinning power-ups at the arena's spawn points. Placeholder items until the pack. */
final class PowerupView {
    private final List<Location> points;
    private final ItemDisplay[] displays;
    private final PowerupType[] types;
    private float spin;
    private long ticks;

    PowerupView(List<Location> points) {
        this.points = points;
        this.displays = new ItemDisplay[points.size()];
        this.types = new PowerupType[points.size()];
    }

    static ItemStack item(PowerupType type) {
        return PackItems.item(Material.PAPER, PackAssets.powerup(type), null);
    }

    int size() {
        return points.size();
    }

    Location point(int i) {
        return points.get(i);
    }

    PowerupType type(int i) {
        return types[i];
    }

    void spawn(int i, PowerupType type) {
        remove(i);
        types[i] = type;
        Location at = points.get(i).clone().add(0, 1.0, 0);
        displays[i] = at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(item(type));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setGlowing(true);
            d.setInterpolationDuration(2);
        });
    }

    void remove(int i) {
        if (displays[i] != null) {
            displays[i].remove();
            displays[i] = null;
        }
        types[i] = null;
    }

    /** Spin and bob; call every 2 ticks. */
    void animate() {
        ticks += 2;
        spin += (float) Math.toRadians(12);
        float bob = (float) (Math.sin(ticks / 10.0) * 0.15);
        for (ItemDisplay d : displays) {
            if (d != null && d.isValid()) {
                d.setInterpolationDelay(0);
                d.setTransformation(new Transformation(new Vector3f(0, bob, 0), new AxisAngle4f(spin, 0, 1, 0),
                        new Vector3f(0.9f, 0.9f, 0.9f), new AxisAngle4f()));
            }
        }
    }

    void removeAll() {
        for (int i = 0; i < displays.length; i++) {
            remove(i);
        }
    }
}
