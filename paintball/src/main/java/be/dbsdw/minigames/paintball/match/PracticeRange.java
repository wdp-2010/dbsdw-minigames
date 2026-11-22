package be.dbsdw.minigames.paintball.match;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.pack.PackItems;
import be.dbsdw.minigames.paintball.Gun;
import be.dbsdw.minigames.paintball.PackAssets;
import be.dbsdw.minigames.paintball.PaintballGame;
import be.dbsdw.minigames.paintball.rules.FireGate;
import be.dbsdw.minigames.paintball.rules.PopupTarget;
import be.dbsdw.minigames.paintball.rules.RangeBox;
import be.dbsdw.minigames.paintball.rules.Settings;
import be.dbsdw.minigames.paintball.rules.Spread;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.util.Vector;

/**
 * The practice range in the lobby: walking into its zone hands out a gun (it goes again on the way
 * out), and pop-up dummies take paint: a hit marker per hit, and after a few hits a dummy sinks into
 * the floor and rises again a little later. Paintballs splat on the walls as in a match; nothing
 * is counted or saved, and other players are not hit.
 */
public final class PracticeRange {
    /** Where a dummy stands and which way it faces. */
    public record Spot(double x, double y, double z, float yaw) {
    }

    private static final int GUN_SLOT = 2;
    private static final int SPLAT_CAP = 120;
    /** Vanilla snowball gravity per tick. */
    private static final double BALL_GRAVITY = 0.03;
    private static final Title.Times MARKER_TIMES = Title.Times.times(
            Duration.ZERO, Duration.ofMillis(700), Duration.ofMillis(250));
    private static final Title.Times HINT_TIMES = Title.Times.times(
            Duration.ofMillis(150), Duration.ofMillis(2500), Duration.ofMillis(500));

    private final PaintballGame game;
    private final Settings settings;
    private final Messages messages;
    private final String worldName;
    private final RangeBox box;
    private final List<Dummy> dummies = new ArrayList<>();
    private final Scope scope;
    private final Splats splats;
    private final Set<UUID> inside = new HashSet<>();
    private final Map<UUID, FireGate> gates = new HashMap<>();
    /** Flying practice balls: ball id -> [team, spawn tick]. */
    private final Map<UUID, long[]> balls = new HashMap<>();
    private long now;
    private boolean worldMissingLogged;

    private final class Dummy {
        final Spot spot;
        final PopupTarget target;
        ArmorStand stand;
        double shownSink;

        Dummy(Spot spot, PopupTarget target) {
            this.spot = spot;
            this.target = target;
        }

        Location at(World world, double sink) {
            return new Location(world, spot.x(), spot.y() - sink, spot.z(), spot.yaw(), 0);
        }
    }

    public PracticeRange(PaintballGame game, String worldName, RangeBox box, List<Spot> spots,
                         int targetHits, long targetDownTicks) {
        this.game = game;
        this.settings = game.settings();
        this.messages = game.messages();
        this.worldName = worldName;
        this.box = box;
        for (Spot spot : spots) {
            dummies.add(new Dummy(spot, new PopupTarget(targetHits, targetDownTicks)));
        }
        this.scope = new Scope(settings, messages);
        this.splats = new Splats(SPLAT_CAP, settings.splatLifetimeTicks());
    }

    public void tick() {
        now++;
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            if (!worldMissingLogged) {
                worldMissingLogged = true;
                game.plugin().getLogger().warning("[practice] world " + worldName + " is not loaded; no range");
            }
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            Location loc = p.getLocation();
            boolean in = !game.players().containsKey(id) && p.getWorld().equals(world)
                    && box.contains(loc.getX(), loc.getY(), loc.getZ());
            if (in && !inside.contains(id)) {
                enter(p);
            } else if (!in && inside.contains(id)) {
                leave(p);
            }
            if (in) {
                scope.update(p, true);
                FireGate gate = gates.get(id);
                if (gate != null && gate.pollQueued(now, settings.shotIntervalTicks())) {
                    fire(p);
                }
            }
        }
        tickBalls();
        splats.tick(now);
        tickDummies(world);
    }

    /** A right-click with the gun; false when the player is not on the range. */
    public boolean trigger(Player p) {
        FireGate gate = gates.get(p.getUniqueId());
        if (gate == null) {
            return false;
        }
        if (gate.request(now, settings.shotIntervalTicks()) == FireGate.Decision.FIRE) {
            fire(p);
        }
        return true;
    }

    /** A hotbar change on the range; true when the scope used it as a zoom step. */
    public boolean scroll(Player p, int from, int to) {
        if (!inside.contains(p.getUniqueId())) {
            return false;
        }
        Component marker = scope.scroll(p, from, to);
        if (marker == null) {
            return false;
        }
        p.showTitle(Title.title(Component.empty(), marker, MARKER_TIMES));
        return true;
    }

    /** A practice paintball hit something; false when it is not one of ours. */
    public boolean onBallHit(ProjectileHitEvent event) {
        UUID ballId = event.getEntity().getUniqueId();
        long[] ball = balls.get(ballId);
        if (ball == null) {
            return false;
        }
        int team = (int) ball[0];
        Entity hit = event.getHitEntity();
        if (hit instanceof Player) {
            event.setCancelled(true); // practice paint never hits people: it flies on
            return true;
        }
        balls.remove(ballId);
        if (hit != null && Gun.isDummy(hit)) {
            hitDummy(hit, team, event.getEntity().getShooter() instanceof Player p ? p : null);
        } else if (event.getHitBlock() != null) {
            splats.add(event.getHitBlock(), event.getHitBlockFace(), event.getEntity(), team, now);
        }
        return true;
    }

    public void quit(Player p) {
        if (inside.contains(p.getUniqueId())) {
            leave(p);
        }
    }

    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            quit(p);
        }
        for (Dummy d : dummies) {
            if (d.stand != null) {
                d.stand.remove();
                d.stand = null;
            }
        }
        splats.removeAll();
        for (UUID id : balls.keySet()) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        balls.clear();
    }

    // --- players ---

    private void enter(Player p) {
        UUID id = p.getUniqueId();
        inside.add(id);
        gates.put(id, new FireGate());
        PlayerInventory inv = p.getInventory();
        inv.setItem(GUN_SLOT, Gun.practiceGun(messages, team(p)));
        inv.setHeldItemSlot(GUN_SLOT);
        p.showTitle(Title.title(messages.get("practice.title"), messages.get("practice.hint"), HINT_TIMES));
    }

    private void leave(Player p) {
        UUID id = p.getUniqueId();
        inside.remove(id);
        gates.remove(id);
        scope.forget(p);
        PlayerInventory inv = p.getInventory();
        boolean held = Gun.isPracticeGun(inv.getItemInMainHand());
        for (int slot = 0; slot < inv.getSize(); slot++) {
            if (Gun.isPracticeGun(inv.getItem(slot))) {
                inv.setItem(slot, null);
            }
        }
        if (held && !game.players().containsKey(id)) {
            inv.setHeldItemSlot(0);
        }
    }

    /** The colour of a player's practice paint: fixed per player, red or blue. */
    private static int team(Player p) {
        return p.getUniqueId().hashCode() & 1;
    }

    // --- shooting ---

    private void fire(Player p) {
        int team = team(p);
        Vector aim = p.getLocation().getDirection();
        double[] d = Spread.apply(aim.getX(), aim.getY(), aim.getZ(), scope.spread(p.getUniqueId()),
                ThreadLocalRandom.current());
        Snowball ball = p.launchProjectile(Snowball.class, new Vector(d[0], d[1], d[2]).multiply(settings.speed()));
        ball.setItem(Gun.ball(team));
        Gun.tagBall(ball);
        balls.put(ball.getUniqueId(), new long[] {team, now});
        ItemStack held = p.getInventory().getItemInMainHand();
        if (Gun.isGun(held)) {
            p.setCooldown(held, settings.shotIntervalTicks());
        }
        PackItems.play(p.getLocation(), PackAssets.SOUND_SHOT, 0.8f, 0.95f + ThreadLocalRandom.current().nextFloat() * 0.1f);
    }

    private void tickBalls() {
        Iterator<Map.Entry<UUID, long[]>> it = balls.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, long[]> e = it.next();
            Entity ball = Bukkit.getEntity(e.getKey());
            if (ball == null || !ball.isValid() || now - e.getValue()[1] >= settings.ballLifetimeTicks()) {
                if (ball != null) {
                    ball.remove();
                }
                it.remove();
            } else if (settings.ballGravity() < 1) {
                ball.setVelocity(ball.getVelocity().add(new Vector(0, BALL_GRAVITY * (1 - settings.ballGravity()), 0)));
            }
        }
    }

    // --- dummies ---

    private void hitDummy(Entity stand, int team, Player shooter) {
        for (Dummy d : dummies) {
            if (d.stand == null || !d.stand.getUniqueId().equals(stand.getUniqueId())) {
                continue;
            }
            PopupTarget.Hit result = d.target.hit(now);
            if (result == PopupTarget.Hit.IGNORED) {
                return;
            }
            Location chest = stand.getLocation().add(0, 1.3, 0);
            stand.getWorld().spawnParticle(Particle.DUST, chest, 24, 0.2, 0.3, 0.2, 0,
                    new Particle.DustOptions(Color.fromRGB(PackAssets.teamRgb(team)), 1.6f));
            PackItems.play(chest, PackAssets.SOUND_HIT, 0.8f, 1f);
            if (shooter != null) {
                boolean down = result == PopupTarget.Hit.DOWN;
                PackItems.play(shooter, down ? PackAssets.SOUND_ELIMINATE : PackAssets.SOUND_HITMARKER, 0.9f, 1f);
                shooter.showTitle(Title.title(Component.empty(),
                        messages.get(down ? "practice.down" : "hud.marker"), MARKER_TIMES));
            }
            return;
        }
    }

    private void tickDummies(World world) {
        for (Dummy d : dummies) {
            boolean loaded = world.isChunkLoaded((int) Math.floor(d.spot.x()) >> 4, (int) Math.floor(d.spot.z()) >> 4);
            if (!loaded) {
                d.stand = null; // not saved: it went with its chunk
                continue;
            }
            double sink = d.target.sink(now);
            if (d.stand == null || !d.stand.isValid()) {
                d.stand = spawn(world, d.at(world, sink));
                d.shownSink = sink;
                if (!d.stand.isValid()) {
                    game.plugin().getLogger().warning("[practice] dummy at " + d.spot + " did not spawn");
                }
            } else if (sink != d.shownSink) {
                d.stand.teleport(d.at(world, sink));
                d.shownSink = sink;
            }
        }
    }

    private static ArmorStand spawn(World world, Location at) {
        return world.spawn(at, ArmorStand.class, s -> {
            s.setPersistent(false);
            s.setGravity(false);
            s.setInvulnerable(true);
            s.setArms(true);
            s.setBasePlate(false);
            s.setSilent(true);
            Gun.tagDummy(s);
            s.getEquipment().setHelmet(armour(Material.LEATHER_HELMET, 0x4B5320));
            s.getEquipment().setChestplate(armour(Material.LEATHER_CHESTPLATE, 0x6B7A3A));
            s.getEquipment().setLeggings(armour(Material.LEATHER_LEGGINGS, 0xB5A270));
            s.getEquipment().setBoots(armour(Material.LEATHER_BOOTS, 0x3B2F22));
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (s.canUseEquipmentSlot(slot)) {
                    s.addEquipmentLock(slot, ArmorStand.LockType.REMOVING_OR_CHANGING);
                    s.addEquipmentLock(slot, ArmorStand.LockType.ADDING);
                }
            }
        });
    }

    private static ItemStack armour(Material material, int rgb) {
        ItemStack stack = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) stack.getItemMeta();
        meta.setColor(Color.fromRGB(rgb));
        stack.setItemMeta(meta);
        return stack;
    }
}
