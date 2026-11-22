package be.dbsdw.minigames.paintball.match;

import be.dbsdw.minigames.core.pack.PackItems;
import be.dbsdw.minigames.paintball.PackAssets;
import be.dbsdw.minigames.paintball.rules.PowerupType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Floating, spinning power-ups. Each slot holds at most one, at a spot chosen when it appears.
 * No glow outline: the badge's coloured rim and full brightness make it readable on its own.
 */
final class PowerupView {
    private final Location[] points;
    private final ItemDisplay[] displays;
    private final PowerupType[] types;
    private float spin;
    private long ticks;

    PowerupView(int slots) {
        this.points = new Location[slots];
        this.displays = new ItemDisplay[slots];
        this.types = new PowerupType[slots];
    }

    static ItemStack item(PowerupType type) {
        return PackItems.item(Material.PAPER, PackAssets.powerup(type), null);
    }

    int size() {
        return points.length;
    }

    /** The spot of slot i; null when the slot is empty. */
    Location point(int i) {
        return points[i];
    }

    PowerupType type(int i) {
        return types[i];
    }

    void spawn(int i, Location point, PowerupType type) {
        remove(i);
        types[i] = type;
        points[i] = point;
        Location at = point.clone().add(0, 1.0, 0);
        displays[i] = at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(item(type));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setInterpolationDuration(2);
        });
    }

    void remove(int i) {
        if (displays[i] != null) {
            displays[i].remove();
            displays[i] = null;
        }
        types[i] = null;
        points[i] = null;
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
