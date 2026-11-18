package be.dbsdw.minigames.paintball.match;

import be.dbsdw.minigames.core.pack.PackItems;
import be.dbsdw.minigames.paintball.PackAssets;
import be.dbsdw.minigames.paintball.rules.SplatFade;
import be.dbsdw.minigames.paintball.rules.SplatQueue;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Paint splats: flat decals from the resource pack on the face a paintball hit, in the shooter's
 * colour, in one of three shapes. They fade out in steps near the end of their lifetime. The
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

    void add(Block block, BlockFace face, int team, long now) {
        if (face == null || !block.getType().isSolid()) {
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        float nx = face.getModX();
        float ny = face.getModY();
        float nz = face.getModZ();
        float size = 0.55f + rnd.nextFloat() * 0.45f;
        // The model is a one-block square centred on the entity and facing up: move it just off
        // the hit face, turn it to face outwards, give it a random spin and size.
        Matrix4f m = new Matrix4f()
                .translate(nx * 0.505f, ny * 0.505f, nz * 0.505f)
                .rotate(new Quaternionf().rotationTo(0, 1, 0, nx, ny, nz))
                .rotateY(rnd.nextFloat() * (float) (Math.PI * 2))
                .scale(size);
        int shape = rnd.nextInt(SHAPES);
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        ItemDisplay display = block.getWorld().spawn(center, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(item(team, shape, 0));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformationMatrix(m);
            d.setViewRange(0.6f);
        });
        queue.add(new Splat(display, team, shape), now).forEach(Splat::remove);
        PackItems.play(center, PackAssets.SOUND_SPLAT, 0.5f, 0.9f + rnd.nextFloat() * 0.2f);
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
