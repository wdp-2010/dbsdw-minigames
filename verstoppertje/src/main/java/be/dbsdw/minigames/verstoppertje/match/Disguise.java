package be.dbsdw.minigames.verstoppertje.match;

import be.dbsdw.minigames.verstoppertje.rules.RayBox;
import be.dbsdw.minigames.verstoppertje.rules.Stillness;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

/**
 * A hider's block: a block display that follows them, plus an interaction box seekers can hit. Once
 * the hider stands still long enough it is solid: lined up with the grid, and every other player in
 * the match gets a (fake, client-side) real block there, so it looks and feels like any other block.
 * While solid the display is hidden from everyone but the hider: drawn on the same spot as the fake
 * block, the two fought over every face (flickering, "cooked" textures). The hider does not get the
 * fake block (it would push them out of their spot), so they keep seeing themselves as the display.
 */
final class Disguise {
    static final String TAG = "dbsdw_verstoppertje";
    /** The hit box sticks out a little past the fake block, so a click lands on the box first. */
    private static final float SOLID_BOX = 1.04f;

    final Player hider;
    private final Plugin plugin;
    /** Players the display is hidden from while solid. */
    private final List<Player> hiddenFrom = new ArrayList<>();
    final Stillness still = new Stillness();
    private BlockData data;
    private final BlockDisplay display;
    private final Interaction box;
    private Block solid;

    Disguise(Plugin plugin, Player hider, BlockData data) {
        this.hider = hider;
        this.plugin = plugin;
        this.data = data;
        Location l = hider.getLocation();
        World w = l.getWorld();
        this.display = w.spawn(corner(l), BlockDisplay.class, d -> {
            d.setBlock(data);
            d.setPersistent(false);
            d.setTeleportDuration(2);
            d.addScoreboardTag(TAG);
        });
        this.box = w.spawn(feet(l), Interaction.class, i -> {
            i.setInteractionWidth(1f);
            i.setInteractionHeight(1f);
            i.setResponsive(true);
            i.setPersistent(false);
            i.addScoreboardTag(TAG);
        });
        // The hider sees their own block (third person shows it; their body is invisible) but cannot
        // hit their own box.
        hider.hideEntity(plugin, box);
    }

    BlockData data() {
        return data;
    }

    boolean solid() {
        return solid != null;
    }

    /** The block the hider stands in as a solid block, or null. */
    Block solidBlock() {
        return solid;
    }

    boolean owns(org.bukkit.entity.Entity e) {
        return e.equals(box) || e.equals(display);
    }

    /** Moves along with the hider (not solid). */
    void follow() {
        Location l = hider.getLocation();
        Location c = corner(l);
        if (display.getLocation().distanceSquared(c) > 0.0004) {
            display.teleport(c);
            box.teleport(feet(l));
        }
    }

    /** Can the hider turn solid where they stand: an empty spot with something solid under it. */
    static boolean canSolidify(Block feet) {
        return feet.isPassable() && !feet.isLiquid() && feet.getRelative(0, -1, 0).getType().isSolid()
                && feet.getRelative(0, 1, 0).isPassable();
    }

    void solidify(Block feet, Collection<Player> others) {
        solid = feet;
        for (Player o : others) {
            if (!o.equals(hider)) {
                o.hideEntity(plugin, display);
                hiddenFrom.add(o);
            }
        }
        display.teleport(feet.getLocation());
        box.teleport(feet.getLocation().add(0.5, -0.02, 0.5));
        box.setInteractionWidth(SOLID_BOX);
        box.setInteractionHeight(SOLID_BOX);
        sendFake(others);
    }

    /** The fake block again (clients can drop it, e.g. on a chunk resend). */
    void sendFake(Collection<Player> others) {
        if (solid != null) {
            others.stream().filter(p -> !p.equals(hider)).forEach(p -> p.sendBlockChange(solid.getLocation(), data));
        }
    }

    void loosen(Collection<Player> others) {
        if (solid == null) {
            return;
        }
        Block was = solid;
        solid = null;
        others.forEach(p -> p.sendBlockChange(was.getLocation(), was.getBlockData()));
        hiddenFrom.forEach(p -> p.showEntity(plugin, display));
        hiddenFrom.clear();
        box.setInteractionWidth(1f);
        box.setInteractionHeight(1f);
        follow();
    }

    /** A player who leaves the match gets the real block back. */
    void forget(Player p) {
        if (hiddenFrom.remove(p)) {
            p.showEntity(plugin, display);
        }
        if (solid != null) {
            p.sendBlockChange(solid.getLocation(), solid.getBlockData());
        }
    }

    void change(BlockData next, Collection<Player> others) {
        data = next;
        display.setBlock(next);
        sendFake(others);
    }

    void remove(Collection<Player> others) {
        loosen(others);
        display.remove();
        box.remove();
    }

    /** How far along the look direction a seeker's eye would hit this block, or -1. */
    double hit(Location eye, Vector dir) {
        double x1;
        double y1;
        double z1;
        if (solid != null) {
            x1 = solid.getX();
            y1 = solid.getY();
            z1 = solid.getZ();
        } else {
            Location c = corner(hider.getLocation());
            x1 = c.getX();
            y1 = c.getY();
            z1 = c.getZ();
        }
        return RayBox.distance(eye.getX(), eye.getY(), eye.getZ(), dir.getX(), dir.getY(), dir.getZ(),
                x1, y1, z1, x1 + 1, y1 + 1, z1 + 1);
    }

    private static Location corner(Location l) {
        return new Location(l.getWorld(), l.getX() - 0.5, l.getY(), l.getZ() - 0.5);
    }

    private static Location feet(Location l) {
        return new Location(l.getWorld(), l.getX(), l.getY(), l.getZ());
    }
}
