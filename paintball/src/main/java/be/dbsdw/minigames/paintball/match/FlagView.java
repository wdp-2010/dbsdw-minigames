package be.dbsdw.minigames.paintball.match;

import be.dbsdw.minigames.core.pack.PackItems;
import be.dbsdw.minigames.paintball.PackAssets;
import be.dbsdw.minigames.paintball.rules.Flags;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/** The flags as item displays: one at home while it is home, one where it lies while dropped. */
final class FlagView {
    private final Location[] homes;
    private final ItemDisplay[] home = new ItemDisplay[2];
    private final ItemDisplay[] dropped = new ItemDisplay[2];
    private float spin;

    /** The flag model is two blocks tall (centred on the entity); this makes it 2.5 blocks. */
    private static final float SCALE = 1.25f;
    /** Lifts the display so the bottom of the pole stands on the ground. */
    private static final double LIFT = 0.5 * SCALE;

    FlagView(Location[] homes) {
        this.homes = homes;
    }

    /** The flag model in the team colour; also worn on the carrier's head (the model's head view). */
    static ItemStack flag(int team) {
        return PackItems.item(Material.PAPER, PackAssets.FLAG, PackAssets.teamRgb(team));
    }

    /** Makes the displays match the flag states; spins them a little each call. */
    void sync(Flags flags) {
        spin += (float) Math.toRadians(9);
        for (int f = 0; f < 2; f++) {
            boolean atHome = flags.state(f) == Flags.State.HOME;
            home[f] = keep(home[f], atHome, homes[f].clone().add(0, LIFT, 0), f);
            Location drop = null;
            if (flags.state(f) == Flags.State.DROPPED) {
                double[] p = flags.dropPos(f);
                drop = new Location(homes[f].getWorld(), p[0], p[1] + LIFT, p[2]);
            }
            if (dropped[f] != null && drop != null && dropped[f].getLocation().distanceSquared(drop) > 0.01) {
                dropped[f].remove();
                dropped[f] = null;
            }
            dropped[f] = keep(dropped[f], drop != null, drop, f);
        }
    }

    void removeAll() {
        for (int f = 0; f < 2; f++) {
            if (home[f] != null) {
                home[f].remove();
                home[f] = null;
            }
            if (dropped[f] != null) {
                dropped[f].remove();
                dropped[f] = null;
            }
        }
    }

    private ItemDisplay keep(ItemDisplay current, boolean wanted, Location at, int team) {
        if (!wanted) {
            if (current != null) {
                current.remove();
            }
            return null;
        }
        if (current == null || !current.isValid()) {
            current = at.getWorld().spawn(at, ItemDisplay.class, d -> {
                d.setPersistent(false);
                d.setItemStack(flag(team));
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setInterpolationDuration(2);
            });
        }
        current.setInterpolationDelay(0);
        current.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(spin, 0, 1, 0),
                new Vector3f(SCALE, SCALE, SCALE), new AxisAngle4f()));
        return current;
    }
}
