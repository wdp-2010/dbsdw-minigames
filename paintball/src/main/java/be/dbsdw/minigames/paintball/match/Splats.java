package be.dbsdw.minigames.paintball.match;

import be.dbsdw.minigames.paintball.rules.SplatQueue;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Paint splats: thin, flat displays on the face a paintball hit. The world itself never changes.
 * Placeholder look (a thin concrete square) until the pack provides decal textures.
 */
final class Splats {
    private final SplatQueue<BlockDisplay> queue;

    Splats(int cap, long lifetime) {
        this.queue = new SplatQueue<>(cap, lifetime);
    }

    void add(Block block, BlockFace face, int team, long now) {
        if (face == null || !block.getType().isSolid()) {
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        float nx = face.getModX();
        float ny = face.getModY();
        float nz = face.getModZ();
        float size = 0.45f + rnd.nextFloat() * 0.45f;
        Matrix4f m = new Matrix4f()
                .translate(nx * 0.502f, ny * 0.502f, nz * 0.502f)
                .rotate(new Quaternionf().rotationTo(0, 1, 0, nx, ny, nz))
                .rotateY(rnd.nextFloat() * (float) (Math.PI * 2))
                .scale(size, 0.01f, size)
                .translate(-0.5f, 0, -0.5f);
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        BlockDisplay splat = block.getWorld().spawn(center, BlockDisplay.class, d -> {
            d.setPersistent(false);
            d.setBlock((team == 0 ? Material.RED_CONCRETE : Material.BLUE_CONCRETE).createBlockData());
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformationMatrix(m);
            d.setViewRange(0.6f);
        });
        queue.add(splat, now).forEach(BlockDisplay::remove);
    }

    void tick(long now) {
        queue.expired(now).forEach(BlockDisplay::remove);
    }

    void removeAll() {
        queue.clear().forEach(BlockDisplay::remove);
    }
}
