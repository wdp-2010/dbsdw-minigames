package be.dbsdw.minigames.paintball.match;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.paintball.Gear;
import be.dbsdw.minigames.paintball.PackAssets;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * What the bomb mode shows in the world: the site sign and its ring, the planted bomb with its
 * countdown and blinking light, the dropped bomb, and the explosion.
 */
final class BombView {
    /** The bomb model sits on the bottom of its block; the display's centre is half a block up. */
    private static final double LIFT = 0.5;
    private static final Color SITE = Color.fromRGB(0xFF, 0xC2, 0x1A);

    private final Messages messages;
    private TextDisplay siteSign;
    private Location site;
    private ItemDisplay planted;
    private TextDisplay timer;
    private ItemDisplay dropped;
    private float spin;

    BombView(Messages messages) {
        this.messages = messages;
    }

    /** Puts the sign over this round's site (the defenders' flag spot). */
    void showSite(Location at) {
        site = at.clone();
        if (siteSign != null) {
            siteSign.remove();
        }
        siteSign = at.getWorld().spawn(at.clone().add(0, 3.2, 0), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.text(messages.get("bomb.site"));
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setBackgroundColor(Color.fromARGB(0x60, 0, 0, 0));
            d.setBrightness(new Display.Brightness(15, 15));
        });
    }

    /** A ring of yellow dust around the site; call now and then. */
    void ringSite(double radius) {
        if (site == null) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(SITE, 1.3f);
        int points = (int) Math.max(16, radius * 8);
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            site.getWorld().spawnParticle(Particle.DUST, site.getX() + Math.cos(a) * radius, site.getY() + 0.15,
                    site.getZ() + Math.sin(a) * radius, 1, 0, 0, 0, 0, dust);
        }
    }

    void plant(Location at) {
        removeDropped();
        removePlanted();
        Location spot = at.clone();
        spot.setPitch(0);
        planted = model(spot.clone().add(0, LIFT, 0), 1.0f, spot.getYaw());
        timer = at.getWorld().spawn(at.clone().add(0, 1.1, 0), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setBackgroundColor(Color.fromARGB(0x80, 0, 0, 0));
            d.setBrightness(new Display.Brightness(15, 15));
        });
    }

    /** The planted bomb's countdown, beep and light: faster the closer it gets. */
    void tickPlanted(long now, long ticksLeft) {
        if (planted == null || !planted.isValid()) {
            return;
        }
        long seconds = (ticksLeft + 19) / 20;
        if (timer != null && now % 5 == 0) {
            timer.text(messages.get("bomb.timer", Placeholder.unparsed("seconds", Long.toString(seconds))));
        }
        long every = ticksLeft > 200 ? 20 : ticksLeft > 100 ? 10 : ticksLeft > 40 ? 5 : 2;
        if (now % every == 0) {
            Location at = planted.getLocation();
            at.getWorld().playSound(at, Sound.BLOCK_NOTE_BLOCK_PLING, 1.2f, ticksLeft > 100 ? 1.6f : 2.0f);
            at.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, 0.05, 0), 6, 0.05, 0.05, 0.05, 0,
                    new Particle.DustOptions(Color.RED, 1.2f));
        }
    }

    /** The bomb lies on the ground for an attacker to pick up. */
    void drop(Location at) {
        removeDropped();
        dropped = model(at.clone().add(0, LIFT, 0), 0.8f, 0);
    }

    /** Spins a dropped bomb so it catches the eye. */
    void tickDropped() {
        if (dropped == null || !dropped.isValid()) {
            return;
        }
        spin += (float) Math.toRadians(6);
        dropped.setInterpolationDelay(0);
        dropped.setTransformation(transform(0.8f, spin));
    }

    /** A big burst of the attackers' paint where the planted bomb was. */
    void explode(int attackers) {
        if (planted == null) {
            return;
        }
        Location at = planted.getLocation();
        Color paint = Color.fromRGB(PackAssets.teamRgb(attackers));
        at.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, at, 1);
        at.getWorld().spawnParticle(Particle.DUST, at, 400, 3.5, 2.0, 3.5, 0, new Particle.DustOptions(paint, 3.0f));
        at.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.8f);
        removePlanted();
    }

    /** Sparks of the defenders' colour: the bomb is safe. */
    void defused(int defenders) {
        if (planted == null) {
            return;
        }
        Location at = planted.getLocation();
        at.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, 0.4, 0), 60, 0.6, 0.4, 0.6, 0,
                new Particle.DustOptions(Color.fromRGB(PackAssets.teamRgb(defenders)), 1.6f));
        at.getWorld().playSound(at, Sound.BLOCK_BEACON_DEACTIVATE, 1.5f, 1.4f);
        removePlanted();
    }

    void removeDropped() {
        if (dropped != null) {
            dropped.remove();
            dropped = null;
        }
    }

    void removePlanted() {
        if (planted != null) {
            planted.remove();
            planted = null;
        }
        if (timer != null) {
            timer.remove();
            timer = null;
        }
    }

    void removeAll() {
        removeDropped();
        removePlanted();
        if (siteSign != null) {
            siteSign.remove();
            siteSign = null;
        }
        site = null;
    }

    private static ItemDisplay model(Location at, float scale, float yaw) {
        Location spot = at.clone();
        spot.setYaw(yaw);
        spot.setPitch(0);
        return at.getWorld().spawn(spot, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(Gear.bombModel());
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setInterpolationDuration(2);
            d.setTransformation(transform(scale, 0));
        });
    }

    private static Transformation transform(float scale, float spin) {
        return new Transformation(new Vector3f(), new AxisAngle4f(spin, 0, 1, 0), new Vector3f(scale, scale, scale),
                new AxisAngle4f());
    }
}
