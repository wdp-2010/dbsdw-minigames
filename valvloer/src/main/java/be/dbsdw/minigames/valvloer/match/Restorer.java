package be.dbsdw.minigames.valvloer.match;

import be.dbsdw.minigames.valvloer.rules.BlockKey;
import be.dbsdw.minigames.valvloer.rules.Snapshot;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/**
 * Puts arena floors back. A match saves its floor to {@code restore/<arena>.txt} when it starts and
 * deletes the file once the floor is back, so after a crash the next start finds and restores it.
 */
public final class Restorer {
    private final Path dir;
    private final Logger log;

    public Restorer(Path dir, Logger log) {
        this.dir = dir;
        this.log = log;
    }

    void save(String arena, Snapshot snapshot) {
        try {
            Files.createDirectories(dir);
            Files.write(file(arena), snapshot.toLines(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.log(Level.WARNING, "[valvloer] could not save the floor of " + arena + " (no crash recovery)", e);
        }
    }

    /** Sets every block of the snapshot back that differs now; returns how many changed. */
    static int restore(Snapshot snapshot, World world) {
        int changed = 0;
        for (var e : snapshot.blocks().entrySet()) {
            long k = e.getKey();
            Block block = world.getBlockAt(BlockKey.x(k), BlockKey.y(k), BlockKey.z(k));
            BlockData data = Bukkit.createBlockData(e.getValue());
            if (!block.getBlockData().equals(data)) {
                block.setBlockData(data, false);
                changed++;
            }
        }
        return changed;
    }

    void done(String arena) {
        try {
            Files.deleteIfExists(file(arena));
        } catch (IOException e) {
            log.log(Level.WARNING, "[valvloer] could not delete " + file(arena), e);
        }
    }

    /** At plugin start: floors of matches that never ended (crash, kill) go back. */
    public void restoreLeftovers() {
        if (!Files.isDirectory(dir)) {
            return;
        }
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> p.getFileName().toString().endsWith(".txt")).toList();
        } catch (IOException e) {
            log.log(Level.WARNING, "[valvloer] could not list " + dir, e);
            return;
        }
        for (Path f : files) {
            String arena = f.getFileName().toString().replaceFirst("\\.txt$", "");
            try {
                Snapshot snapshot = Snapshot.parse(Files.readAllLines(f, StandardCharsets.UTF_8));
                World world = Bukkit.getWorld(snapshot.world());
                if (world == null) {
                    log.warning("[valvloer] floor of " + arena + ": world " + snapshot.world() + " is not loaded, kept for later");
                    continue;
                }
                int changed = restore(snapshot, world);
                Files.delete(f);
                log.info("[valvloer] floor of " + arena + " restored after an unfinished match: " + changed + " blocks");
            } catch (IOException | IllegalArgumentException e) {
                log.log(Level.WARNING, "[valvloer] could not restore " + f, e);
            }
        }
    }

    private Path file(String arena) {
        return dir.resolve(arena + ".txt");
    }
}
