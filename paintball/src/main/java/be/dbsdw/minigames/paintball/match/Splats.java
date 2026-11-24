package be.dbsdw.minigames.paintball.match;

import be.dbsdw.minigames.core.pack.PackItems;
import be.dbsdw.minigames.paintball.PackAssets;
import be.dbsdw.minigames.paintball.rules.DripPath;
import be.dbsdw.minigames.paintball.rules.PuddleFade;
import be.dbsdw.minigames.paintball.rules.SplatFade;
import be.dbsdw.minigames.paintball.rules.SplatPlacement;
import be.dbsdw.minigames.paintball.rules.SplatQueue;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Paint splats: flat decals from the resource pack where a paintball hit, in the shooter's
 * colour, in one of three shapes; placed by {@link SplatPlacement} so they never hang over an edge. They fade out in steps near the end of their lifetime.
 * Paint on a wall runs down it ({@link DripPath}) to the floor, where it forms a puddle that soaks
 * away after a few seconds ({@link PuddleFade}). The world itself never changes.
 */
final class Splats {
    private static final int SHAPES = 3;
    /** Longest run of paint down a wall, in blocks; a higher hit dries up on the way down. */
    private static final double MAX_DRIP = 8;
    /** Ticks between spawning a display and starting its movement, so the client has it first. */
    private static final int START_DELAY = 2;
    private static final Quaternionf NO_TURN = new Quaternionf();

    /**
     * Paint running down the wall under a splat: a stripe that grows downwards with a drop at its
     * end. Both move by display interpolation, so the client animates them smoothly on its own.
     */
    private static final class Drip {
        final ItemDisplay body;
        ItemDisplay head;
        final Transformation bodyEnd;
        final Transformation headEnd;
        final int ticks;
        final long startAt;
        /** The floor block the paint lands on, or null when it dries up on the wall. */
        final Block floor;
        /** World x and z where the run ends. */
        final double footX;
        final double footZ;
        boolean started;
        boolean ended;

        Drip(ItemDisplay body, ItemDisplay head, Transformation bodyEnd, Transformation headEnd, int ticks,
             long startAt, Block floor, double footX, double footZ) {
            this.body = body;
            this.head = head;
            this.bodyEnd = bodyEnd;
            this.headEnd = headEnd;
            this.ticks = ticks;
            this.startAt = startAt;
            this.floor = floor;
            this.footX = footX;
            this.footZ = footZ;
        }

        void restyle(int team, int stage) {
            body.setItemStack(dripItem(team, 0, stage));
            if (head != null) {
                head.setItemStack(dripItem(team, 1, stage));
            }
        }

        void remove() {
            body.remove();
            if (head != null) {
                head.remove();
            }
        }
    }

    /** A puddle at the foot of a wall where a drip landed; it spreads, lies a while and soaks away. */
    private static final class Puddle {
        final ItemDisplay display;
        final int team;
        final int shape;
        final long born;
        final Quaternionf turn;
        final float size;
        int stage;
        boolean spread;
        boolean soaking;

        Puddle(ItemDisplay display, int team, int shape, long born, Quaternionf turn, float size) {
            this.display = display;
            this.team = team;
            this.shape = shape;
            this.born = born;
            this.turn = turn;
            this.size = size;
        }
    }

    /** One splat on the map; {@code stage} is the fade stage it currently shows. */
    private static final class Splat {
        final ItemDisplay display;
        final int team;
        final int shape;
        int stage;
        Drip drip;

        Splat(ItemDisplay display, int team, int shape) {
            this.display = display;
            this.team = team;
            this.shape = shape;
        }

        void remove() {
            display.remove();
            if (drip != null) {
                drip.remove();
            }
        }
    }

    private final SplatQueue<Splat> queue;
    private final long lifetime;
    private final Deque<Puddle> puddles = new ArrayDeque<>();
    private final int puddleCap;

    Splats(int cap, long lifetime) {
        this.queue = new SplatQueue<>(cap, lifetime);
        this.lifetime = lifetime;
        this.puddleCap = cap / 4;
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
        Splat splat = new Splat(display, team, shape);
        if (full && face.getModY() == 0 && puddleCap > 0) {
            splat.drip = drip(block, face, axis, ua, origin, plane, c, h, team, now);
        }
        queue.add(splat, now).forEach(Splat::remove);
        PackItems.play(at, PackAssets.SOUND_SPLAT, 0.5f, 0.9f + rnd.nextFloat() * 0.2f);
    }

    /**
     * Paint running down the wall from just under the centre of a splat at {@code c} (face
     * coordinates of the hit {@code block}), or null when there is no wall under it to run down.
     */
    private Drip drip(Block block, BlockFace face, int axis, int ua, double[] origin, double plane, double[] c,
                      double h, int team, long now) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        float scale = (float) (h * 0.8);
        double half = scale * 3 / 16; // the stripe is 6 of the sprite's 16 pixels wide
        double su = c[0] + (rnd.nextDouble() * 2 - 1) * h * 0.35;
        int du = (int) Math.floor(su);
        su = clamp(su, du + half, du + 1 - half);
        double sv = c[1] - h * 0.15;
        int dv = (int) Math.floor(sv);
        int[] off = new int[3];
        off[ua] = du;
        off[1] = dv;
        Block start = block.getRelative(off[0], off[1], off[2]);
        if (!wall(start, face) || !start.getRelative(face).isPassable()) {
            return null;
        }
        DripPath.Run run = DripPath.run(sv - dv, MAX_DRIP, k -> below(start, face, k));
        if (run.length() < 0.15) {
            return null;
        }
        int n = face.getModX() + face.getModY() + face.getModZ();
        double depth = 0.0015 + rnd.nextDouble() * 0.002;
        double[] p = new double[3];
        p[axis] = plane + n * depth;
        p[ua] = origin[ua] + su;
        p[1] = origin[1] + sv;
        Location top = new Location(block.getWorld(), p[0], p[1], p[2]);
        Quaternionf turn = downTheWall(face);
        // a drop that dries up on the wall stops a little higher, so it never hangs over the edge
        double length = run.landed() ? run.length() : Math.max(0.05, run.length() - scale * 0.7);
        int ticks = DripPath.ticks(length);
        ItemDisplay body = spawnFlat(top, dripItem(team, 0, 0), stripe(turn, scale, 0.02));
        Location headAt = top.clone().add(face.getModX() * 0.0005, 0, face.getModZ() * 0.0005);
        ItemDisplay head = spawnFlat(headAt, dripItem(team, 1, 0), drop(turn, scale, 0.02));
        Block floor = run.landed() ? start.getRelative(0, -run.level(), 0).getRelative(face) : null;
        return new Drip(body, head, stripe(turn, scale, length), drop(turn, scale, length), ticks,
                now + START_DELAY, floor, p[0], p[2]);
    }

    /** What is {@code k} blocks under the drip's start block on this wall (see {@link DripPath}). */
    private static DripPath.Cell below(Block start, BlockFace face, int k) {
        Block wall = start.getRelative(0, -k, 0);
        Block front = wall.getRelative(face);
        if (!front.isPassable()) {
            return carriesPaint(front) && front.getBlockData().isFaceSturdy(BlockFace.UP, BlockSupport.FULL)
                    ? DripPath.Cell.FLOOR : DripPath.Cell.END;
        }
        return wall(wall, face) ? DripPath.Cell.WALL : DripPath.Cell.END;
    }

    private static boolean wall(Block cell, BlockFace face) {
        return carriesPaint(cell) && cell.getBlockData().isFaceSturdy(face, BlockSupport.FULL);
    }

    /**
     * Turns the flat model (a square facing up) to lie on the wall: its face outwards and its own
     * +z (the bottom of the sprite) pointing down.
     */
    private static Quaternionf downTheWall(BlockFace face) {
        Vector3f out = new Vector3f(face.getModX(), face.getModY(), face.getModZ());
        Vector3f down = new Vector3f(0, -1, 0);
        Vector3f side = out.cross(down, new Vector3f());
        return new Quaternionf().setFromNormalized(new Matrix3f(side, out, down));
    }

    /** The stripe hanging {@code length} blocks down from the entity. */
    private static Transformation stripe(Quaternionf turn, float scale, double length) {
        return new Transformation(new Vector3f(0, (float) (-length / 2), 0), turn,
                new Vector3f(scale, 1, (float) length), NO_TURN);
    }

    /** The drop at the end of a stripe of {@code length} blocks: its point overlaps the stripe's end. */
    private static Transformation drop(Quaternionf turn, float scale, double length) {
        return new Transformation(new Vector3f(0, (float) -(length + scale * 0.2), 0), turn,
                new Vector3f(scale, 1, scale), NO_TURN);
    }

    private static ItemDisplay spawnFlat(Location at, ItemStack item, Transformation shape) {
        return at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(item);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(shape);
            d.setViewRange(0.6f);
        });
    }

    /** Moves a display smoothly (on the client) to {@code to} over {@code ticks}. */
    private static void glide(ItemDisplay display, Transformation to, int ticks) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(to);
    }

    /** A puddle on top of {@code floor} where paint running down the wall at (x, z) lands. */
    private void puddle(Block floor, double x, double z, int team, long now) {
        if (!carriesPaint(floor) || !floor.getRelative(BlockFace.UP).isPassable()) {
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double h = 0.2 + rnd.nextDouble() * 0.12;
        double[] c = SplatPlacement.place(x - floor.getX(), z - floor.getZ(), h,
                (du, dv) -> paintable(floor, BlockFace.UP, 0, 2, du, dv));
        Location at = new Location(floor.getWorld(), floor.getX() + c[0],
                floor.getY() + 1 + 0.004 + rnd.nextDouble() * 0.008, floor.getZ() + c[1]);
        Quaternionf turn = new Quaternionf().rotateY(rnd.nextInt(4) * (float) (Math.PI / 2));
        float size = (float) (h * 2);
        int shape = rnd.nextInt(SHAPES);
        ItemDisplay display = spawnFlat(at, item(team, shape, 0), flat(turn, size * 0.3f));
        while (puddles.size() >= puddleCap) {
            puddles.removeFirst().display.remove();
        }
        puddles.addLast(new Puddle(display, team, shape, now, turn, size));
        at.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, 0.05, 0), 6, 0.12, 0.02, 0.12, 0,
                new Particle.DustOptions(Color.fromRGB(PackAssets.teamRgb(team)), 0.7f));
        PackItems.play(at, PackAssets.SOUND_SPLAT, 0.15f, 1.5f + rnd.nextFloat() * 0.2f);
    }

    private static Transformation flat(Quaternionf turn, float size) {
        return new Transformation(new Vector3f(), turn, new Vector3f(size, size, size), NO_TURN);
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
                if (splat.drip != null) {
                    splat.drip.restyle(splat.team, stage);
                }
            }
            if (splat.drip != null) {
                tick(splat.drip, splat.team, now);
            }
        }
        tickPuddles(now);
    }

    private void tick(Drip drip, int team, long now) {
        if (!drip.started && now >= drip.startAt) {
            drip.started = true;
            if (drip.body.isValid()) {
                glide(drip.body, drip.bodyEnd, drip.ticks);
            }
            if (drip.head != null && drip.head.isValid()) {
                glide(drip.head, drip.headEnd, drip.ticks);
            }
        }
        if (drip.started && !drip.ended && now >= drip.startAt + drip.ticks) {
            drip.ended = true;
            if (drip.floor != null) {
                // the drop runs into the floor and becomes the puddle
                if (drip.head != null) {
                    drip.head.remove();
                    drip.head = null;
                }
                puddle(drip.floor, drip.footX, drip.footZ, team, now);
            }
        }
    }

    private void tickPuddles(long now) {
        for (Iterator<Puddle> it = puddles.iterator(); it.hasNext(); ) {
            Puddle puddle = it.next();
            long age = now - puddle.born;
            if (PuddleFade.gone(age) || !puddle.display.isValid()) {
                puddle.display.remove();
                it.remove();
                continue;
            }
            if (!puddle.spread && age >= START_DELAY) {
                puddle.spread = true;
                glide(puddle.display, flat(puddle.turn, puddle.size), PuddleFade.SPREAD);
            }
            if (!puddle.soaking && age >= PuddleFade.HOLD) {
                puddle.soaking = true;
                glide(puddle.display, flat(puddle.turn, puddle.size * 0.2f), PuddleFade.DISSOLVE);
            }
            int stage = PuddleFade.stage(age);
            if (stage != puddle.stage) {
                puddle.stage = stage;
                puddle.display.setItemStack(item(puddle.team, puddle.shape, stage));
            }
        }
    }

    void removeAll() {
        queue.clear().forEach(Splat::remove);
        puddles.forEach(p -> p.display.remove());
        puddles.clear();
    }

    private static ItemStack item(int team, int shape, int stage) {
        return PackItems.item(Material.PAPER, PackAssets.SPLAT, PackAssets.teamRgb(team), shape, stage);
    }

    /** Part 0 is the stripe, part 1 the drop at its end. */
    private static ItemStack dripItem(int team, int part, int stage) {
        return PackItems.item(Material.PAPER, PackAssets.DRIP, PackAssets.teamRgb(team), part, stage);
    }
}
