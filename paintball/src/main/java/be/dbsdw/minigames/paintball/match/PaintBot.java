package be.dbsdw.minigames.paintball.match;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.paintball.Gun;
import be.dbsdw.minigames.paintball.PackAssets;
import be.dbsdw.minigames.paintball.bot.BotAim;
import be.dbsdw.minigames.paintball.bot.BotLevel;
import be.dbsdw.minigames.paintball.bot.BotNav;
import be.dbsdw.minigames.paintball.rules.Settings;
import be.dbsdw.minigames.paintball.rules.Spread;
import be.dbsdw.minigames.paintball.rules.WalkableArea.Cell;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Mannequin;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.util.Vector;

/**
 * One paintball bot: a mannequin that walks the arena floor along {@link BotNav} paths, looks for
 * enemies it can see, aims with {@link BotAim} and fires through the match. It never uses vanilla
 * AI or physics: the match moves it a little every tick. How well it plays is its {@link BotLevel}.
 */
final class PaintBot {
    /** An enemy the bot may shoot at: positions {x, y, z} and velocity in blocks per tick. */
    record Enemy(UUID id, double[] feet, double[] chest, double[] eye, double[] velocity) {}

    /** What the match decides for the bot and does for it. */
    interface Orders {
        /** Where the bot should go when it sees nobody, or empty to hunt the nearest enemy. */
        Optional<Location> objective(PaintBot bot);

        /** Fires one paintball in this unit direction. */
        void fire(PaintBot bot, double[] direction, long now);
    }

    private static final double EYE = 1.62;
    private static final double CHEST = 1.1;
    private static final int PATH_BUDGET = 6000;

    final UUID id;
    final String name;
    final int team;
    /** Position among the team's bots: in capture the flag odd ones defend, even ones attack. */
    final int index;
    final BotLevel level;
    final Mannequin body;
    private final BotNav nav;
    private final Settings settings;
    private final RandomGenerator rnd;

    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private boolean out;

    private List<Cell> path = List.of();
    private int step;
    private Cell goal;
    private long repathAt;

    private UUID target;
    private long sawAt;
    private long nextShot;
    private long nextSightCheck;
    private Cell strafeTo;
    private long strafeUntil;

    private PaintBot(Mannequin body, String name, int team, int index, BotLevel level, BotNav nav, Settings settings,
                     RandomGenerator rnd) {
        this.body = body;
        this.id = body.getUniqueId();
        this.name = name;
        this.team = team;
        this.index = index;
        this.level = level;
        this.nav = nav;
        this.settings = settings;
        this.rnd = rnd;
        Location l = body.getLocation();
        place(l);
    }

    static PaintBot spawn(Location at, String name, int team, int index, BotLevel level, BotNav nav, Settings settings,
                          Messages messages, Component levelName, RandomGenerator rnd) {
        World world = at.getWorld();
        Mannequin body = world.spawn(at, Mannequin.class, m -> {
            m.setPersistent(false);
            m.setInvulnerable(true);
            m.setGravity(false);
            m.setImmovable(true);
            m.setCollidable(false);
            m.setSilent(true);
            m.customName(Component.text(name, team == 0 ? NamedTextColor.RED : NamedTextColor.BLUE));
            m.setCustomNameVisible(true);
            m.setDescription(levelName);
            EntityEquipment eq = m.getEquipment();
            eq.setItemInMainHand(Gun.gun(messages, team));
            eq.setChestplate(dyed(Material.LEATHER_CHESTPLATE, team));
            eq.setLeggings(dyed(Material.LEATHER_LEGGINGS, team));
            eq.setBoots(dyed(Material.LEATHER_BOOTS, team));
        });
        return new PaintBot(body, name, team, index, level, nav, settings, rnd);
    }

    private static ItemStack dyed(Material type, int team) {
        ItemStack item = ItemStack.of(type);
        item.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(Color.fromRGB(PackAssets.teamRgb(team))));
        return item;
    }

    boolean isOut() {
        return out;
    }

    Location location() {
        return new Location(body.getWorld(), x, y, z, yaw, pitch);
    }

    double[] feet() {
        return new double[] {x, y, z};
    }

    double[] chest() {
        return new double[] {x, y + CHEST, z};
    }

    double[] eye() {
        return new double[] {x, y + EYE, z};
    }

    /** Out: stops where it is until {@link #respawn}. */
    void knockOut() {
        out = true;
        target = null;
        path = List.of();
    }

    void respawn(Location at) {
        out = false;
        place(at);
        body.teleport(location());
        path = List.of();
        goal = null;
        target = null;
        nextShot = 0;
    }

    void remove() {
        body.remove();
    }

    private void place(Location l) {
        x = l.getX();
        y = l.getY();
        z = l.getZ();
        yaw = l.getYaw();
        pitch = 0;
    }

    // --- every tick ---

    void tick(long now, List<Enemy> enemies, Orders orders) {
        if (out || !body.isValid()) {
            return;
        }
        Enemy seen = pickTarget(now, enemies);
        if (seen != null) {
            fight(now, seen, orders);
        } else {
            target = null;
            Optional<Location> objective = orders.objective(this);
            Cell want = objective.flatMap(o -> nav.nearest(o.getX(), o.getY(), o.getZ()))
                    .or(() -> nearestEnemyCell(enemies))
                    .orElse(null);
            walkTowards(now, want);
            if (!path.isEmpty() && step < path.size()) {
                Cell w = path.get(step);
                lookAt(w.x() + 0.5 - x, 0, w.z() + 0.5 - z);
            }
        }
        body.teleport(location());
        body.setBodyYaw(yaw);
    }

    private void fight(long now, Enemy e, Orders orders) {
        if (!e.id().equals(target)) {
            target = e.id();
            sawAt = now;
        }
        double dx = e.feet()[0] - x;
        double dz = e.feet()[2] - z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        Optional<BotAim.Solution> aim = BotAim.solve(muzzle(), e.chest(), e.velocity(), settings.speed(),
                settings.ballGravity(), (int) settings.ballLifetimeTicks(), level.leads());
        if (aim.isPresent()) {
            BotAim.Solution s = aim.get();
            lookAt(s.x(), s.y(), s.z());
            boolean inRange = dist <= level.fightRange() * 1.5;
            if (inRange && now - sawAt >= level.reactionTicks() && now >= nextShot) {
                orders.fire(this, shot(s), now);
                body.swingMainHand();
                nextShot = now + level.shotInterval(settings.shotIntervalTicks()) + rnd.nextInt(3);
            }
        } else {
            lookAt(e.chest()[0] - x, e.chest()[1] - (y + EYE), e.chest()[2] - z);
        }
        // Close in until the enemy is in fight range (from far away nobody hits anything), then fight:
        // strong bots step sideways, others stand.
        if (dist > level.fightRange()) {
            strafeTo = null;
            nav.nearest(e.feet()[0], e.feet()[1], e.feet()[2]).ifPresent(c -> walkTowards(now, c));
        } else if (level.strafes()) {
            Optional<Cell> here = nav.nearest(x, y, z);
            if ((strafeTo == null || now >= strafeUntil) && here.isPresent()) {
                strafeTo = nav.randomNear(here.get(), 3, rnd).orElse(here.get());
                strafeUntil = now + 12 + rnd.nextInt(18);
                path = nav.path(here.get(), strafeTo, 200);
                step = 0;
                goal = strafeTo;
            }
            followPath();
        }
    }

    /**
     * The direction a shot really goes: off the perfect aim by the bot's own error (mostly small, a
     * folded normal capped at three times the level's typical error), then the gun's spread.
     */
    private double[] shot(BotAim.Solution s) {
        double off = Math.min(3, Math.abs(rnd.nextGaussian())) * level.aimErrorDegrees();
        double[] d = Spread.tilt(s.x(), s.y(), s.z(), off, rnd);
        return Spread.apply(d[0], d[1], d[2], settings.spreadDegrees(), rnd);
    }

    /** The closest enemy in sight range with a clear line from the bot's eyes; checked every few ticks. */
    private Enemy pickTarget(long now, List<Enemy> enemies) {
        if (now < nextSightCheck && target != null) {
            for (Enemy e : enemies) {
                if (e.id().equals(target)) {
                    return e;
                }
            }
            return null;
        }
        nextSightCheck = now + 3;
        Enemy best = null;
        double bestD = level.sightRange() * level.sightRange();
        double[] eye = eye();
        for (Enemy e : enemies) {
            double d = dist2(eye, e.chest());
            if (d < bestD && (canSee(eye, e.eye()) || canSee(eye, e.chest()))) {
                best = e;
                bestD = d;
            }
        }
        return best;
    }

    private boolean canSee(double[] from, double[] to) {
        Vector dir = new Vector(to[0] - from[0], to[1] - from[1], to[2] - from[2]);
        double len = dir.length();
        if (len < 0.01) {
            return true;
        }
        Location start = new Location(body.getWorld(), from[0], from[1], from[2]);
        return body.getWorld().rayTraceBlocks(start, dir.multiply(1 / len), len, FluidCollisionMode.NEVER, true) == null;
    }

    private Optional<Cell> nearestEnemyCell(List<Enemy> enemies) {
        Enemy best = null;
        double bestD = Double.MAX_VALUE;
        for (Enemy e : enemies) {
            double d = dist2(feet(), e.feet());
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best == null ? Optional.empty() : nav.nearest(best.feet()[0], best.feet()[1], best.feet()[2]);
    }

    // --- walking ---

    /** Heads for {@code want}; once there it wanders around it. Null: wander anywhere near. */
    private void walkTowards(long now, Cell want) {
        Optional<Cell> here = nav.nearest(x, y, z);
        if (here.isEmpty()) {
            return;
        }
        boolean arrived = step >= path.size();
        if (arrived && (want == null || near(here.get(), want, 2))) {
            // There (or nowhere to go): wander a little around the spot, now and then.
            if (now >= repathAt) {
                Cell around = want != null ? want : here.get();
                goTo(here.get(), nav.randomNear(around, 5, rnd).orElse(around));
                repathAt = now + 30 + rnd.nextInt(40);
            }
        } else if (want != null && (arrived || goal == null || !near(goal, want, 2) || now >= repathAt)) {
            goTo(here.get(), want);
            repathAt = now + 30 + rnd.nextInt(15);
        }
        followPath();
    }

    private void goTo(Cell here, Cell to) {
        goal = to;
        path = nav.path(here, to, PATH_BUDGET);
        step = 0;
    }

    private void followPath() {
        double budget = level.walkSpeed();
        while (budget > 0 && step < path.size()) {
            Cell w = path.get(step);
            double tx = w.x() + 0.5;
            double tz = w.z() + 0.5;
            double dx = tx - x;
            double dz = tz - z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d <= budget) {
                x = tx;
                z = tz;
                budget -= d;
                step++;
            } else {
                x += dx / d * budget;
                z += dz / d * budget;
                budget = 0;
            }
            // Up a step quickly (a jump), down a little slower (a fall).
            if (w.y() > y) {
                y = Math.min(w.y(), y + 0.45);
            } else if (w.y() < y) {
                y = Math.max(w.y(), y - 0.6);
            }
        }
    }

    private void lookAt(double dx, double dy, double dz) {
        double h = Math.sqrt(dx * dx + dz * dz);
        if (h < 1e-6 && Math.abs(dy) < 1e-6) {
            return;
        }
        yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        pitch = (float) -Math.toDegrees(Math.atan2(dy, h));
    }

    private double[] muzzle() {
        return new double[] {x, y + EYE - 0.1, z};
    }

    private static boolean near(Cell a, Cell b, int blocks) {
        return Math.abs(a.x() - b.x()) <= blocks && Math.abs(a.z() - b.z()) <= blocks && Math.abs(a.y() - b.y()) <= 2;
    }

    private static double dist2(double[] a, double[] b) {
        double dx = a[0] - b[0];
        double dy = a[1] - b[1];
        double dz = a[2] - b[2];
        return dx * dx + dy * dy + dz * dz;
    }
}
