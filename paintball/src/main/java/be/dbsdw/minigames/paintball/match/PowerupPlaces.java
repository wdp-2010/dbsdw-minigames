package be.dbsdw.minigames.paintball.match;

import be.dbsdw.minigames.core.api.ArenaView;
import be.dbsdw.minigames.paintball.rules.PowerupDirector;
import be.dbsdw.minigames.paintball.rules.WalkableArea;
import be.dbsdw.minigames.paintball.rules.WalkableArea.Cell;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

/** Finds the arena floor players can walk to and builds the power-up director for one match. */
final class PowerupPlaces {
    private static final int LIMIT = 80_000;

    private PowerupPlaces() {
    }

    static PowerupDirector director(ArenaView arena, double catchUp) {
        World world = arena.world();
        List<Cell> spawns = new ArrayList<>();
        for (int team = 0; team < 2; team++) {
            for (Location l : arena.spawns(team)) {
                spawns.add(new Cell(l.getBlockX(), l.getBlockY(), l.getBlockZ()));
            }
        }
        BoundingBox b = arena.bounds();
        int[] min = {(int) Math.floor(b.getMinX()), (int) Math.floor(b.getMinY()), (int) Math.floor(b.getMinZ())};
        int[] max = {(int) Math.floor(b.getMaxX()), (int) Math.floor(b.getMaxY()), (int) Math.floor(b.getMaxZ())};
        WalkableArea.Blocks blocks = new WalkableArea.Blocks() {
            @Override
            public boolean solid(int x, int y, int z) {
                return world.getBlockAt(x, y, z).getType().isSolid();
            }

            @Override
            public boolean open(int x, int y, int z) {
                Block block = world.getBlockAt(x, y, z);
                return block.isPassable() && !block.isLiquid();
            }
        };
        List<Cell> cells = WalkableArea.flood(blocks, min, max, spawns, LIMIT);
        if (cells.isEmpty()) {
            // no floor found from the spawns (odd arena): fall back to the arena's power-up points
            for (Location l : arena.powerups()) {
                cells.add(new Cell(l.getBlockX(), l.getBlockY(), l.getBlockZ()));
            }
        }
        return new PowerupDirector(cells, spawns, catchUp, new Random());
    }
}
