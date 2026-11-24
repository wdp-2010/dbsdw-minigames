package be.dbsdw.minigames.paintball.match;

import be.dbsdw.minigames.core.pack.PackItems;
import be.dbsdw.minigames.paintball.Gun;
import be.dbsdw.minigames.paintball.PackAssets;
import be.dbsdw.minigames.paintball.rules.DripMotion;
import be.dbsdw.minigames.paintball.rules.DripPath;
import be.dbsdw.minigames.paintball.rules.PartialFace;
import be.dbsdw.minigames.paintball.rules.PuddleFade;
import be.dbsdw.minigames.paintball.rules.SplatFade;
import be.dbsdw.minigames.paintball.rules.SplatPlacement;
import be.dbsdw.minigames.paintball.rules.SplatQueue;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
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
 * Paint on a wall runs down it ({@link DripPath}, moving as {@link DripMotion}) to the floor, where it
 * pools against the foot of the wall and soaks away after a few seconds ({@link PuddleFade}); the
 * splat it came from shrinks a little as it drains. The world itself never changes.
 */
final class Splats {
    private static final int SHAPES = 3;
    /** Longest run of paint down a wall, in blocks; a higher hit dries up on the way down. */
    private static final double MAX_DRIP = 8;
    /** Highest fall of a drop off the bottom edge of a wall that still reaches the ground, in blocks. */
    private static final int MAX_FALL = 16;
    /** Ticks a drop hangs from the bottom edge before it lets go. */
    private static final int HANG = 8;
    /** Share of wall splats that have enough paint to run: 1 in 4. */
    private static final double DRIP_CHANCE = 0.25;
    /** Trail and drop sprites come in this many shapes. */
    private static final int DRIP_VARIANTS = 3;
    /** Half the widest part of the trail and drop sprites, as a share of their size (8.5 of 32 px). */
    private static final double DRIP_HALF_WIDTH = 8.5 / 32;
    /** At most this tilt off straight down (radians), as a wall is never perfectly smooth. */
    private static final double MAX_TILT = 0.05;
    /** How far a splat shrinks as paint drains out of it, and how fast (ticks). */
    private static final float DRAINED = 0.84f;
    private static final int DRAIN_TICKS = 40;
    /** Ticks between spawning a display and starting its movement, so the client has it first. */
    private static final int START_DELAY = 2;
    private static final Quaternionf NO_TURN = new Quaternionf();

    /**
     * Paint running down the wall under a splat: a trail that grows downwards with a drop at its
     * end. Every {@link DripMotion#STEP} ticks both get the next frame and the client glides there.
     */
    private static final class Drip {
        final ItemDisplay body;
        ItemDisplay head;
        final Quaternionf turn;
        /** Straight down the wall, tilted a little. */
        final Vector3f dir;
        final float scale;
        final int variant;
        final List<DripMotion.Frame> frames;
        final long startAt;
        /** The floor block the paint lands on, or null when it dries up on the wall. */
        final Block floor;
        /** The ground the drop falls onto from the bottom edge of the wall, or null. */
        final Block ground;
        /** The wall's face the paint runs down (the puddle pools against it). */
        final BlockFace face;
        /** Where the run ends (world coordinates). */
        final double footX;
        final double footY;
        final double footZ;
        boolean ended;

        Drip(ItemDisplay body, ItemDisplay head, Quaternionf turn, Vector3f dir, float scale, int variant,
             List<DripMotion.Frame> frames, long startAt, Block floor, Block ground, BlockFace face, double footX,
             double footY, double footZ) {
            this.body = body;
            this.head = head;
            this.turn = turn;
            this.dir = dir;
            this.scale = scale;
            this.variant = variant;
            this.frames = frames;
            this.startAt = startAt;
            this.floor = floor;
            this.ground = ground;
            this.face = face;
            this.footX = footX;
            this.footY = footY;
            this.footZ = footZ;
        }

        long endsAt() {
            return startAt + (long) frames.size() * DripMotion.STEP;
        }

        void restyle(int team, int stage) {
            body.setItemStack(dripItem(team, 0, stage, variant));
            if (head != null) {
                head.setItemStack(dripItem(team, 1, stage, variant));
            }
        }

        void remove() {
            body.remove();
            if (head != null) {
                head.remove();
            }
        }
    }

    /** A drop falling from the bottom edge of a wall: a small paintball that falls with gravity. */
    private static final class Fall {
        final ItemDisplay display;
        final long born;
        final List<Double> frames;
        final Block ground;
        final BlockFace face;
        final double x;
        final double z;
        final int team;

        Fall(ItemDisplay display, long born, List<Double> frames, Block ground, BlockFace face, double x, double z,
             int team) {
            this.display = display;
            this.born = born;
            this.frames = frames;
            this.ground = ground;
            this.face = face;
            this.x = x;
            this.z = z;
            this.team = team;
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
        final Matrix4f shape;
        final int team;
        final int look;
        int stage;
        List<Drip> drips = List.of();
        boolean draining;

        Splat(ItemDisplay display, Matrix4f shape, int team, int look) {
            this.display = display;
            this.shape = shape;
            this.team = team;
            this.look = look;
        }

        void remove() {
            display.remove();
            drips.forEach(Drip::remove);
        }
    }

    private final SplatQueue<Splat> queue;
    private final long lifetime;
    private final Deque<Puddle> puddles = new ArrayDeque<>();
    private final List<Fall> falls = new ArrayList<>();
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
     * partial blocks it stays on the part of the block it hit (a stair's side is an L, not a
     * square). Fences, gates, panes and bars get no paint: they are mostly gaps.
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
            if (mostlyGaps(block)) {
                return;
            }
            List<double[]> boxes = new ArrayList<>();
            for (BoundingBox b : block.getCollisionShape().getBoundingBoxes()) {
                boxes.add(new double[] {b.getMinX(), b.getMinY(), b.getMinZ(), b.getMaxX(), b.getMaxY(), b.getMaxZ()});
            }
            PartialFace.Part part = PartialFace.pick(boxes, axis, n, ua, va, at -> {
                double[] w = hitPoint(ball, axis, origin[axis] + at);
                return new double[] {w[ua] - origin[ua], w[va] - origin[va]};
            });
            if (part == null || part.room() < 0.25) {
                return; // wall post, rod: too thin for paint
            }
            plane = origin[axis] + part.plane();
            lo = new double[] {part.loU(), part.loV()};
            hi = new double[] {part.hiU(), part.hiV()};
            h = Math.min(h, part.room() / 2 * 0.95);
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
        Splat splat = new Splat(display, m, team, shape);
        if (full && face.getModY() == 0 && puddleCap > 0 && rnd.nextDouble() < DRIP_CHANCE) {
            Drip drip = drip(block, face, axis, ua, origin, plane, c, h, team, now);
            if (drip != null) {
                splat.drips = List.of(drip);
            }
        }
        queue.add(splat, now).forEach(Splat::remove);
        PackItems.play(at, PackAssets.SOUND_SPLAT, 0.5f, 0.9f + rnd.nextFloat() * 0.2f);
    }

    /**
     * Paint running down the wall from under the splat at {@code c} (face coordinates of the hit
     * {@code block}), or null when there is no wall under it to run down.
     */
    private Drip drip(Block block, BlockFace face, int axis, int ua, double[] origin, double plane, double[] c,
                      double h, int team, long now) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        float scale = (float) h;
        double half = scale * DRIP_HALF_WIDTH;
        double su = c[0] + (rnd.nextDouble() * 2 - 1) * h * 0.4;
        int du = (int) Math.floor(su);
        su = clamp(su, du + half, du + 1 - half);
        double sv = c[1] - h * (0.1 + rnd.nextDouble() * 0.15);
        int dv = (int) Math.floor(sv);
        int[] off = new int[3];
        off[ua] = du;
        off[1] = dv;
        Block start = block.getRelative(off[0], off[1], off[2]);
        if (!wall(start, face) || !start.getRelative(face).isPassable()) {
            return null;
        }
        DripPath.Run run = DripPath.run(sv - dv, MAX_DRIP, k -> below(start, face, k));
        boolean landed = run.landed();
        Block ground = run.falls() ? groundUnder(start.getRelative(0, -run.level(), 0).getRelative(face)) : null;
        boolean moving = landed || ground != null;
        // a drop that dries up on the wall stops a little higher, so it never hangs over the edge;
        // one that falls off runs right to the bottom edge and hangs there for a moment
        double length = moving ? run.length() : run.length() - scale * 0.75;
        if (length < 0.15) {
            return null;
        }
        // a little off straight down, but never so much that it leaves its column of blocks
        double margin = Math.max(0, Math.min(su - du, du + 1 - su) - half);
        double tilt = (rnd.nextDouble() * 2 - 1) * Math.min(MAX_TILT, Math.atan(margin / length));
        Vector3f out = new Vector3f(face.getModX(), face.getModY(), face.getModZ());
        Quaternionf turn = new Quaternionf().rotationAxis((float) tilt, out).mul(downTheWall(face));
        Vector3f dir = turn.transform(new Vector3f(0, 0, 1));

        int n = face.getModX() + face.getModY() + face.getModZ();
        double depth = 0.0015 + rnd.nextDouble() * 0.002;
        double[] p = new double[3];
        p[axis] = plane + n * depth;
        p[ua] = origin[ua] + su;
        p[1] = origin[1] + sv;
        Location top = new Location(block.getWorld(), p[0], p[1], p[2]);
        List<DripMotion.Frame> frames = DripMotion.plan(length, moving, rnd);
        int variant = rnd.nextInt(DRIP_VARIANTS);
        ItemDisplay body = spawnFlat(top, dripItem(team, 0, 0, variant), trail(turn, dir, scale, 0.02));
        Location headAt = top.clone().add(out.x * 0.0005, 0, out.z * 0.0005);
        ItemDisplay head = spawnFlat(headAt, dripItem(team, 1, 0, variant), drop(turn, dir, scale, 0.02));
        Block floor = landed ? start.getRelative(0, -run.level(), 0).getRelative(face) : null;
        return new Drip(body, head, turn, dir, scale, variant, frames, now + START_DELAY, floor, ground, face,
                p[0] + dir.x * length, p[1] + dir.y * length, p[2] + dir.z * length);
    }

    /** The first block a drop falling from {@code open} (open air) down lands on, or null if none near. */
    private static Block groundUnder(Block open) {
        for (int j = 1; j <= MAX_FALL; j++) {
            Block b = open.getRelative(0, -j, 0);
            if (b.isLiquid()) {
                return null;
            }
            if (!b.isPassable()) {
                return b;
            }
        }
        return null;
    }

    /** What is {@code k} blocks under the drip's start block on this wall (see {@link DripPath}). */
    private static DripPath.Cell below(Block start, BlockFace face, int k) {
        Block wall = start.getRelative(0, -k, 0);
        Block front = wall.getRelative(face);
        if (!front.isPassable()) {
            return carriesPaint(front) && front.getBlockData().isFaceSturdy(BlockFace.UP, BlockSupport.FULL)
                    ? DripPath.Cell.FLOOR : DripPath.Cell.END;
        }
        if (wall(wall, face)) {
            return DripPath.Cell.WALL;
        }
        return wall.isPassable() ? DripPath.Cell.OPEN : DripPath.Cell.END;
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

    /** The trail hanging {@code length} blocks from the entity along {@code dir}. */
    private static Transformation trail(Quaternionf turn, Vector3f dir, float scale, double length) {
        return new Transformation(new Vector3f(dir).mul((float) (length / 2)), turn,
                new Vector3f(scale, 1, (float) length), NO_TURN);
    }

    /** The drop at the end of a trail of {@code length} blocks: its neck overlaps the trail's end. */
    private static Transformation drop(Quaternionf turn, Vector3f dir, float size, double length) {
        return new Transformation(new Vector3f(dir).mul((float) (length + size * 0.2)), turn,
                new Vector3f(size, 1, size), NO_TURN);
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

    /**
     * A puddle on top of {@code floor} where paint from the wall (facing {@code face}) at (x, z)
     * lands. Paint that ran down the wall ({@code atWall}) pools against its foot: centred on the
     * line where wall and floor meet, so the wall hides half of it. A drop that fell lies free.
     */
    private void puddle(Block floor, BlockFace face, double x, double z, int team, long now, boolean atWall) {
        if (!carriesPaint(floor) || !floor.getBlockData().isFaceSturdy(BlockFace.UP, BlockSupport.FULL)
                || !floor.getRelative(BlockFace.UP).isPassable()) {
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double h = 0.2 + rnd.nextDouble() * 0.12;
        int fx = face.getModX();
        int fz = face.getModZ();
        // the wall's own block on the floor's level: it has to be solid to hide half the puddle
        boolean against = atWall && floor.getRelative(face.getOppositeFace()).getType().isOccluding();
        double[] c = SplatPlacement.place(x - floor.getX(), z - floor.getZ(), h, (du, dv) ->
                (against && (du == -fx && fx != 0 || dv == -fz && fz != 0)) || paintable(floor, BlockFace.UP, 0, 2, du, dv));
        if (against) {
            if (fx != 0) {
                c[0] = fx > 0 ? 0 : 1;
            } else {
                c[1] = fz > 0 ? 0 : 1;
            }
        }
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
        at.getWorld().spawnParticle(Particle.DUST, at.clone().add(fx * 0.15, 0.05, fz * 0.15), 6, 0.12, 0.02, 0.12, 0,
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

    /** Fences, gates, glass panes and iron bars: rails and gaps, no surface for a splat. */
    private static boolean mostlyGaps(Block block) {
        Material type = block.getType();
        return Tag.FENCES.isTagged(type) || Tag.FENCE_GATES.isTagged(type) || type == Material.IRON_BARS
                || type.name().endsWith("_PANE");
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
                splat.display.setItemStack(item(splat.team, splat.look, stage));
                for (Drip drip : splat.drips) {
                    drip.restyle(splat.team, stage);
                }
            }
            for (Drip drip : splat.drips) {
                if (!splat.draining && now >= drip.startAt && splat.display.isValid()) {
                    // paint drains out of the splat into its runs: it shrinks a little
                    splat.draining = true;
                    splat.display.setInterpolationDelay(0);
                    splat.display.setInterpolationDuration(DRAIN_TICKS);
                    splat.display.setTransformationMatrix(new Matrix4f(splat.shape).scale(DRAINED));
                }
                tick(drip, splat.team, now);
            }
        }
        tickFalls(now);
        tickPuddles(now);
    }

    private void tick(Drip drip, int team, long now) {
        long since = now - drip.startAt;
        if (since >= 0 && since % DripMotion.STEP == 0 && since / DripMotion.STEP < drip.frames.size()) {
            int i = (int) (since / DripMotion.STEP);
            DripMotion.Frame frame = drip.frames.get(i);
            if (i == 0 || !frame.equals(drip.frames.get(i - 1))) {
                double length = Math.max(0.02, frame.distance());
                if (drip.body.isValid()) {
                    glide(drip.body, trail(drip.turn, drip.dir, drip.scale, length), DripMotion.STEP);
                }
                if (drip.head != null && drip.head.isValid()) {
                    glide(drip.head, drop(drip.turn, drip.dir, drip.scale, length), DripMotion.STEP);
                }
            }
        }
        if (!drip.ended && now >= drip.endsAt() && drip.floor != null) {
            // the drop runs into the floor and becomes the puddle
            drip.ended = true;
            removeHead(drip);
            puddle(drip.floor, drip.face, drip.footX, drip.footZ, team, now, true);
        }
        if (!drip.ended && now >= drip.endsAt() + HANG && drip.ground != null) {
            // it has hung from the bottom edge for a moment and lets go
            drip.ended = true;
            removeHead(drip);
            fall(drip, team, now);
        }
    }

    private static void removeHead(Drip drip) {
        if (drip.head != null) {
            drip.head.remove();
            drip.head = null;
        }
    }

    /** The drop at the end of {@code drip} lets go of the bottom edge and falls to the ground. */
    private void fall(Drip drip, int team, long now) {
        Vector3f out = new Vector3f(drip.face.getModX(), 0, drip.face.getModZ());
        Location at = new Location(drip.ground.getWorld(), drip.footX + out.x * 0.08,
                drip.footY - drip.scale * 0.45, drip.footZ + out.z * 0.08);
        double height = at.getY() - drip.ground.getBoundingBox().getMaxY();
        if (height <= 0) {
            return;
        }
        float size = drip.scale * 0.5f;
        ItemDisplay display = at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(Gun.ball(team));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(Display.Billboard.CENTER);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(dropped(size, 0));
            d.setViewRange(0.6f);
        });
        falls.add(new Fall(display, now, DripMotion.fall(height), drip.ground, drip.face, at.getX(), at.getZ(), team));
    }

    private static Transformation dropped(float size, double fallen) {
        return new Transformation(new Vector3f(0, (float) -fallen, 0), NO_TURN, new Vector3f(size, size, size), NO_TURN);
    }

    private void tickFalls(long now) {
        for (Iterator<Fall> it = falls.iterator(); it.hasNext(); ) {
            Fall fall = it.next();
            long age = now - fall.born;
            if (age > 0 && age % DripMotion.STEP == 0 && age / DripMotion.STEP < fall.frames.size()
                    && fall.display.isValid()) {
                float size = fall.display.getTransformation().getScale().x;
                glide(fall.display, dropped(size, fall.frames.get((int) (age / DripMotion.STEP))), DripMotion.STEP);
            }
            if (age >= (long) fall.frames.size() * DripMotion.STEP) {
                fall.display.remove();
                it.remove();
                puddle(fall.ground, fall.face, fall.x, fall.z, fall.team, now, false);
                Location at = new Location(fall.ground.getWorld(), fall.x, fall.ground.getBoundingBox().getMaxY() + 0.05,
                        fall.z);
                at.getWorld().spawnParticle(Particle.DUST, at, 5, 0.1, 0.02, 0.1, 0,
                        new Particle.DustOptions(Color.fromRGB(PackAssets.teamRgb(fall.team)), 0.7f));
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
        falls.forEach(f -> f.display.remove());
        falls.clear();
    }

    private static ItemStack item(int team, int shape, int stage) {
        return PackItems.item(Material.PAPER, PackAssets.SPLAT, PackAssets.teamRgb(team), shape, stage);
    }

    /** Part 0 is the trail, part 1 the drop at its end; {@code variant} picks one of their shapes. */
    private static ItemStack dripItem(int team, int part, int stage, int variant) {
        return PackItems.item(Material.PAPER, PackAssets.DRIP, PackAssets.teamRgb(team), part, stage, variant);
    }
}
