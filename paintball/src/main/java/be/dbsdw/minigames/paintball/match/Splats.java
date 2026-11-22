package be.dbsdw.minigames.paintball.match;

import be.dbsdw.minigames.core.pack.PackItems;
import be.dbsdw.minigames.paintball.PackAssets;
import be.dbsdw.minigames.paintball.rules.SplatFade;
import be.dbsdw.minigames.paintball.rules.SplatPlacement;
import be.dbsdw.minigames.paintball.rules.SplatQueue;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Paint splats: flat decals from the resource pack where a paintball hit, in the shooter's
 * colour, in one of three shapes; placed by {@link SplatPlacement} so they never hang over an edge. They fade out in steps near the end of their lifetime. The
 * world itself never changes.
 */
final class Splats {
    private static final int SHAPES = 3;

    /** One splat on the map; {@code stage} is the fade stage it currently shows. */
    private static final class Splat {
        final ItemDisplay display;
        final int team;
        final int shape;
        int stage;

        Splat(ItemDisplay display, int team, int shape) {
            this.display = display;
            this.team = team;
            this.shape = shape;
        }

        void remove() {
            display.remove();
        }
    }

    private final SplatQueue<Splat> queue;
    private final long lifetime;

    Splats(int cap, long lifetime) {
        this.queue = new SplatQueue<>(cap, lifetime);
        this.lifetime = lifetime;
    }

    /**
     * A splat where {@code ball} hit {@code face} of {@code block}. It sits exactly where the ball
     * hit and may run across the seam onto neighbouring blocks of the same flat surface, but never
     * hangs over an edge (top of a wall, a corner, a covered face). On slabs, stairs and other
     * partial blocks it stays on that block's own face.
     */
    void add(Block block, BlockFace face, Entity ball, int team, long now) {
        if (face == null || !face.isCartesian() || !carriesPaint(block)) {
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int n = face.getModX() + face.getModY() + face.getModZ();
        int axis = face.getModX() != 0 ? 0 : face.getModY() != 0 ? 1 : 2;
        int ua = axis == 0 ? 2 : 0;
        int va = axis == 1 ? 2 : 1;
        double[] origin = {block.getX(), block.getY(), block.getZ()};
        double h = (0.55 + rnd.nextDouble() * 0.45) / 2;
        boolean full = block.getBlockData().isFaceSturdy(face, BlockSupport.FULL);
        double plane;
        double[] lo = {0, 0};
        double[] hi = {1, 1};
        if (full) {
            plane = origin[axis] + (n > 0 ? 1 : 0);
        } else {
            BoundingBox box = block.getBoundingBox();
            double[] min = {box.getMinX(), box.getMinY(), box.getMinZ()};
            double[] max = {box.getMaxX(), box.getMaxY(), box.getMaxZ()};
            plane = n > 0 ? max[axis] : min[axis];
            lo = new double[] {min[ua] - origin[ua], min[va] - origin[va]};
            hi = new double[] {max[ua] - origin[ua], max[va] - origin[va]};
            double room = Math.min(hi[0] - lo[0], hi[1] - lo[1]);
            if (room < 0.25) {
                return; // fence, wall post, rod: too thin for paint
            }
            h = Math.min(h, room / 2 * 0.95);
        }
        double[] hit = hitPoint(ball, axis, plane);
        double u = hit[ua] - origin[ua];
        double v = hit[va] - origin[va];
        double[] c;
        if (full) {
            c = SplatPlacement.place(u, v, h, (du, dv) -> paintable(block, face, ua, va, du, dv));
        } else {
            c = new double[] {clamp(u, lo[0] + h, hi[0] - h), clamp(v, lo[1] + h, hi[1] - h)};
        }
        double[] p = new double[3];
        // a slightly different depth per splat, so overlapping splats don't flicker
        p[axis] = plane + n * (0.004 + rnd.nextDouble() * 0.008);
        p[ua] = origin[ua] + c[0];
        p[va] = origin[va] + c[1];
        Location at = new Location(block.getWorld(), p[0], p[1], p[2]);
        // The model is a one-block square centred on the entity and facing up: turn it to face
        // outwards, a quarter turn at random (edges stay along the block grid), and size it.
        Matrix4f m = new Matrix4f()
                .rotate(new Quaternionf().rotationTo(0, 1, 0, face.getModX(), face.getModY(), face.getModZ()))
                .rotateY(rnd.nextInt(4) * (float) (Math.PI / 2))
                .scale((float) (h * 2));
        int shape = rnd.nextInt(SHAPES);
        ItemDisplay display = block.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(item(team, shape, 0));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformationMatrix(m);
            d.setViewRange(0.6f);
        });
        queue.add(new Splat(display, team, shape), now).forEach(Splat::remove);
        PackItems.play(at, PackAssets.SOUND_SPLAT, 0.5f, 0.9f + rnd.nextFloat() * 0.2f);
    }

    /** Where the ball's path crosses the hit plane (its position and velocity at the hit). */
    private static double[] hitPoint(Entity ball, int axis, double plane) {
        Location l = ball.getLocation();
        Vector vel = ball.getVelocity();
        double[] pos = {l.getX(), l.getY(), l.getZ()};
        double[] vv = {vel.getX(), vel.getY(), vel.getZ()};
        if (Math.abs(vv[axis]) > 1e-6) {
            double t = (plane - pos[axis]) / vv[axis];
            if (t > -2 && t < 3) {
                return new double[] {pos[0] + vv[0] * t, pos[1] + vv[1] * t, pos[2] + vv[2] * t};
            }
        }
        pos[axis] = plane;
        return pos;
    }

    /** A neighbour in the hit plane can carry paint: a full face on that side, nothing in front. */
    private static boolean paintable(Block block, BlockFace face, int ua, int va, int du, int dv) {
        int[] off = new int[3];
        off[ua] = du;
        off[va] = dv;
        Block cell = block.getRelative(off[0], off[1], off[2]);
        return carriesPaint(cell) && cell.getBlockData().isFaceSturdy(face, BlockSupport.FULL)
                && cell.getRelative(face).isPassable();
    }

    /** Solid and visible: barriers (the invisible arena walls) never get paint. */
    private static boolean carriesPaint(Block block) {
        Material type = block.getType();
        return type.isSolid() && type != Material.BARRIER;
    }

    private static double clamp(double x, double lo, double hi) {
        return lo > hi ? (lo + hi) / 2 : Math.max(lo, Math.min(hi, x));
    }

    void tick(long now) {
        queue.expired(now).forEach(Splat::remove);
        for (Map.Entry<Splat, Long> e : queue.ages(now)) {
            Splat splat = e.getKey();
            int stage = SplatFade.stage(e.getValue(), lifetime);
            if (stage != splat.stage && splat.display.isValid()) {
                splat.stage = stage;
                splat.display.setItemStack(item(splat.team, splat.shape, stage));
            }
        }
    }

    void removeAll() {
        queue.clear().forEach(Splat::remove);
    }

    private static ItemStack item(int team, int shape, int stage) {
        return PackItems.item(Material.PAPER, PackAssets.SPLAT, PackAssets.teamRgb(team), shape, stage);
    }
}
