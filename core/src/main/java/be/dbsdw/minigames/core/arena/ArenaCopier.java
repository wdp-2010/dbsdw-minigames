package be.dbsdw.minigames.core.arena;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.Item;
import org.bukkit.plugin.Plugin;
import org.bukkit.structure.Structure;
import org.bukkit.util.BoundingBox;

/**
 * Places a template arena's blocks at one of its copies when a match starts there, and puts back what
 * was there before when the match ends. The template's blocks are read once (Paper's structure API)
 * and kept until the template changes; the land under each copy is read the first time that copy is
 * built after a server start. Entities are not copied; games spawn their own.
 */
public final class ArenaCopier {
    private final Plugin plugin;
    private final Logger log;
    private final Map<String, Snapshot> snapshots = new HashMap<>();
    /** What each copy's box held before it was first built, by copy name. */
    private final Map<String, Snapshot> backgrounds = new HashMap<>();

    private record Snapshot(ArenaDefinition template, Structure structure) {}

    public ArenaCopier(Plugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    /**
     * Blocks that lose their support while a box is rebuilt (plants, ladders, lanterns) drop as items;
     * remove those now and once more two ticks later.
     */
    private void removeDrops(World world, ArenaDefinition a) {
        BoundingBox box = new BoundingBox(minX(a) - 1, minY(a) - 1, minZ(a) - 1,
                Math.max(a.corner1().blockX(), a.corner2().blockX()) + 2,
                Math.max(a.corner1().blockY(), a.corner2().blockY()) + 2,
                Math.max(a.corner1().blockZ(), a.corner2().blockZ()) + 2);
        Runnable sweep = () -> world.getNearbyEntities(box, e -> e instanceof Item).forEach(org.bukkit.entity.Entity::remove);
        sweep.run();
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskLater(plugin, sweep, 2);
        }
    }

    /** Builds the copy from the template. Loads the chunks it needs. */
    public void place(World world, ArenaDefinition template, ArenaDefinition copy) {
        long started = System.nanoTime();
        Structure structure = snapshot(world, template);
        loadChunks(world, copy);
        Snapshot background = backgrounds.get(copy.name());
        if (background == null || !background.template().equals(copy)) {
            backgrounds.put(copy.name(), new Snapshot(copy, read(world, copy)));
        }
        structure.place(new Location(world, minX(copy), minY(copy), minZ(copy)), false, StructureRotation.NONE,
                Mirror.NONE, 0, 1f, new Random(0));
        removeDrops(world, copy);
        log.info("[arena] built copy " + copy.name() + " of " + template.name() + " in "
                + (System.nanoTime() - started) / 1_000_000 + " ms");
    }

    /**
     * Puts back what the copy's box held before it was built; without that record (it was never built
     * since the server started) every block of the box becomes air, without physics.
     */
    public void clear(World world, ArenaDefinition copy) {
        long started = System.nanoTime();
        loadChunks(world, copy);
        Snapshot background = backgrounds.get(copy.name());
        if (background != null && background.template().equals(copy)) {
            background.structure().place(new Location(world, minX(copy), minY(copy), minZ(copy)), false,
                    StructureRotation.NONE, Mirror.NONE, 0, 1f, new Random(0));
            removeDrops(world, copy);
            log.info("[arena] restored the land under copy " + copy.name() + " in "
                    + (System.nanoTime() - started) / 1_000_000 + " ms");
            return;
        }
        int x2 = Math.max(copy.corner1().blockX(), copy.corner2().blockX());
        int y2 = Math.max(copy.corner1().blockY(), copy.corner2().blockY());
        int z2 = Math.max(copy.corner1().blockZ(), copy.corner2().blockZ());
        for (int x = minX(copy); x <= x2; x++) {
            for (int z = minZ(copy); z <= z2; z++) {
                for (int y = minY(copy); y <= y2; y++) {
                    Block b = world.getBlockAt(x, y, z);
                    if (b.getType() != Material.AIR) {
                        b.setType(Material.AIR, false);
                    }
                }
            }
        }
        removeDrops(world, copy);
        log.info("[arena] cleared copy " + copy.name() + " in " + (System.nanoTime() - started) / 1_000_000 + " ms");
    }

    private Structure snapshot(World world, ArenaDefinition template) {
        Snapshot cached = snapshots.get(template.name());
        if (cached != null && cached.template().equals(template)) {
            return cached.structure();
        }
        loadChunks(world, template);
        Structure structure = read(world, template);
        snapshots.put(template.name(), new Snapshot(template, structure));
        log.info("[arena] read template " + template.name() + " (" + structure.getSize() + ")");
        return structure;
    }

    /** Every block of the arena's box. */
    private static Structure read(World world, ArenaDefinition a) {
        Structure structure = Bukkit.getStructureManager().createStructure();
        int x2 = Math.max(a.corner1().blockX(), a.corner2().blockX());
        int y2 = Math.max(a.corner1().blockY(), a.corner2().blockY());
        int z2 = Math.max(a.corner1().blockZ(), a.corner2().blockZ());
        // fill(corner, corner) takes the far corner exclusive: add one so the corner blocks are included.
        structure.fill(new Location(world, minX(a), minY(a), minZ(a)), new Location(world, x2 + 1, y2 + 1, z2 + 1), false);
        return structure;
    }

    private static void loadChunks(World world, ArenaDefinition arena) {
        for (int[] c : ArenaChunks.chunks(arena)) {
            world.getChunkAt(c[0], c[1]);
        }
    }

    private static int minX(ArenaDefinition a) {
        return Math.min(a.corner1().blockX(), a.corner2().blockX());
    }

    private static int minY(ArenaDefinition a) {
        return Math.min(a.corner1().blockY(), a.corner2().blockY());
    }

    private static int minZ(ArenaDefinition a) {
        return Math.min(a.corner1().blockZ(), a.corner2().blockZ());
    }
}
