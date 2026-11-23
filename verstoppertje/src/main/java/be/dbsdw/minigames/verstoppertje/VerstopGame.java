package be.dbsdw.minigames.verstoppertje;

import be.dbsdw.minigames.core.Messages;
import be.dbsdw.minigames.core.api.ArenaView;
import be.dbsdw.minigames.core.api.GameType;
import be.dbsdw.minigames.core.api.MatchContext;
import be.dbsdw.minigames.core.api.MatchHandler;
import be.dbsdw.minigames.core.api.ModeSpec;
import be.dbsdw.minigames.verstoppertje.match.VerstopMatch;
import be.dbsdw.minigames.verstoppertje.rules.Settings;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.random.RandomGenerator;
import net.kyori.adventure.text.Component;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;

/** Verstoppertje: hiders turn into blocks, seekers hunt them; found hiders join the seekers. */
final class VerstopGame implements GameType {
    static final String ID = "verstoppertje";

    private final Plugin plugin;
    private final Settings settings;
    private final List<Material> disguises;
    private final Messages messages;
    private final ModeSpec mode;
    private final Set<VerstopMatch> active = new CopyOnWriteArraySet<>();
    private final RandomGenerator rnd = RandomGenerator.getDefault();
    /** Blocks found in each arena, scanned on its first match. */
    private final Map<String, List<Material>> palettes = new ConcurrentHashMap<>();
    private static final int MIN_COUNT = 4;

    VerstopGame(Plugin plugin, Settings settings, List<Material> disguises, Messages messages) {
        this.plugin = plugin;
        this.settings = settings;
        this.disguises = List.copyOf(disguises);
        this.messages = messages;
        this.mode = new ModeSpec(ID, "klassiek", messages.raw("mode.klassiek"), 2, 1, settings.maxPerTeam(), Set.of());
    }

    Optional<VerstopMatch> matchOf(Player player) {
        return active.stream().filter(m -> m.has(player)).findFirst();
    }

    /** Whether the block is inside the arena of a running match. */
    boolean inAnyArena(org.bukkit.block.Block block) {
        return active.stream().anyMatch(m -> m.inArena(block));
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component displayName() {
        return messages.get("game.name");
    }

    @Override
    public Material icon() {
        return Material.BARREL;
    }

    @Override
    public List<ModeSpec> modes() {
        return List.of(mode);
    }

    @Override
    public List<String> stats() {
        return List.of("found", "hidden");
    }

    @Override
    public Component statName(String stat) {
        return messages.get("stat." + stat);
    }

    @Override
    public Component teamName(ModeSpec mode, int team) {
        return team == VerstopMatch.HIDERS ? messages.get("team.hiders")
                : team == VerstopMatch.SEEKERS ? messages.get("team.seekers") : null;
    }

    @Override
    public MatchHandler newMatch(MatchContext context) {
        List<Material> blocks = palettes.get(context.arena().name());
        if (blocks == null) {
            blocks = scan(context.arena());
            if (blocks != disguises) {
                palettes.put(context.arena().name(), blocks);
            }
        }
        return new VerstopMatch(plugin, context, settings, blocks, messages, plugin.getLogger(), rnd, active);
    }

    /** A whole cube (or leaves): hiding as a slab, fence or path block would look wrong. */
    private static boolean fullBlock(Material m) {
        return !m.isAir() && (m.isOccluding() || Tag.LEAVES.isTagged(m));
    }

    /** Something see-through next to it (above or a side within the chunk): not buried ground. */
    private static boolean visible(ChunkSnapshot snap, int x, int y, int z, World w) {
        if (y + 1 >= w.getMaxHeight() || !snap.getBlockType(x, y + 1, z).isOccluding()) {
            return true;
        }
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : sides) {
            int nx = x + d[0];
            int nz = z + d[1];
            if (nx >= 0 && nx < 16 && nz >= 0 && nz < 16 && !snap.getBlockType(nx, y, nz).isOccluding()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The blocks the arena is built from that players may use, most common first (up to 36: hotbar
     * plus inventory). Only whole blocks that can be seen count. Blocks seen fewer than {@link #MIN_COUNT} times are left out: hiding as one is
     * a giveaway. Falls back to the configured disguises when the scan finds nothing.
     */
    private List<Material> scan(ArenaView arena) {
        long t0 = System.nanoTime();
        BoundingBox b = arena.bounds();
        World w = arena.world();
        int x0 = (int) Math.floor(b.getMinX());
        int x1 = (int) Math.ceil(b.getMaxX()) - 1;
        int z0 = (int) Math.floor(b.getMinZ());
        int z1 = (int) Math.ceil(b.getMaxZ()) - 1;
        int y0 = Math.max(w.getMinHeight(), (int) Math.floor(b.getMinY()));
        int y1 = Math.min(w.getMaxHeight() - 1, (int) Math.ceil(b.getMaxY()) - 1);
        Map<Material, Integer> counts = new HashMap<>();
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                ChunkSnapshot snap = w.getChunkAt(cx, cz).getChunkSnapshot(false, false, false);
                for (int x = Math.max(x0, cx << 4); x <= Math.min(x1, (cx << 4) + 15); x++) {
                    for (int z = Math.max(z0, cz << 4); z <= Math.min(z1, (cz << 4) + 15); z++) {
                        for (int y = y0; y <= y1; y++) {
                            Material m = snap.getBlockType(x & 15, y, z & 15);
                            if (fullBlock(m) && visible(snap, x & 15, y, z & 15, w)) {
                                counts.merge(m, 1, Integer::sum);
                            }
                        }
                    }
                }
            }
        }
        List<Material> found = counts.entrySet().stream()
                .filter(e -> e.getValue() >= MIN_COUNT && e.getKey().isItem() && VerstopMatch.allowed(e.getKey()))
                .sorted(Map.Entry.<Material, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey).limit(36).toList();
        plugin.getLogger().info("[verstoppertje] " + arena.name() + ": " + found.size() + " blocks from the map in "
                + (System.nanoTime() - t0) / 1_000_000 + " ms: " + found);
        return found.isEmpty() ? disguises : found;
    }
}
