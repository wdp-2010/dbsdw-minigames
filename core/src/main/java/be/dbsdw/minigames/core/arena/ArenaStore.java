package be.dbsdw.minigames.core.arena;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Arena files: one {@code <name>.yml} per arena in the arenas folder. */
public final class ArenaStore {
    private static final String SUFFIX = ".yml";

    private final Path dir;
    private final Logger log;

    public ArenaStore(Path dir, Logger log) {
        this.dir = dir;
        this.log = log;
    }

    /** Every readable arena whose world is loaded. Others are skipped with one warning each. */
    public List<ArenaDefinition> loadAll(Predicate<String> worldLoaded) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Path> files;
        try (Stream<Path> listing = Files.list(dir)) {
            files = listing.filter(f -> f.getFileName().toString().endsWith(SUFFIX)).sorted().toList();
        }
        List<ArenaDefinition> arenas = new ArrayList<>();
        for (Path file : files) {
            String fileName = file.getFileName().toString();
            String name = fileName.substring(0, fileName.length() - SUFFIX.length());
            ArenaDefinition arena;
            try {
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.loadFromString(Files.readString(file, StandardCharsets.UTF_8));
                arena = read(name, yaml);
            } catch (InvalidConfigurationException | RuntimeException e) {
                log.warning("[arena] skipped " + fileName + ": unreadable (" + e.getMessage() + ")");
                continue;
            }
            if (!worldLoaded.test(arena.world())) {
                log.warning("[arena] skipped " + fileName + ": world '" + arena.world() + "' is not loaded");
                continue;
            }
            arenas.add(arena);
        }
        return arenas;
    }

    public void save(ArenaDefinition arena) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("world", arena.world());
        yaml.set("modes", arena.modes().stream().sorted().toList());
        if (arena.corner1() != null) {
            yaml.set("corner1", corner(arena.corner1()));
        }
        if (arena.corner2() != null) {
            yaml.set("corner2", corner(arena.corner2()));
        }
        arena.spawns().keySet().stream().sorted().forEach(team ->
                yaml.set("spawns." + team, arena.spawns().get(team).stream().map(ArenaStore::pos).toList()));
        arena.flags().keySet().stream().sorted().forEach(team -> yaml.set("flags." + team, pos(arena.flags().get(team))));
        yaml.set("powerups", arena.powerups().stream().map(ArenaStore::pos).toList());
        Files.createDirectories(dir);
        Path temp = dir.resolve(arena.name() + SUFFIX + ".tmp");
        Files.writeString(temp, yaml.saveToString(), StandardCharsets.UTF_8);
        Files.move(temp, dir.resolve(arena.name() + SUFFIX), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }

    public boolean delete(String name) throws IOException {
        return Files.deleteIfExists(dir.resolve(name + SUFFIX));
    }

    private static ArenaDefinition read(String name, YamlConfiguration yaml) {
        String world = yaml.getString("world");
        if (world == null || world.isBlank()) {
            throw new IllegalArgumentException("no world");
        }
        Map<Integer, List<Pos>> spawns = new LinkedHashMap<>();
        ConfigurationSection spawnSection = yaml.getConfigurationSection("spawns");
        if (spawnSection != null) {
            for (String team : spawnSection.getKeys(false)) {
                spawns.put(Integer.parseInt(team), spawnSection.getMapList(team).stream().map(ArenaStore::pos).toList());
            }
        }
        Map<Integer, Pos> flags = new LinkedHashMap<>();
        ConfigurationSection flagSection = yaml.getConfigurationSection("flags");
        if (flagSection != null) {
            for (String team : flagSection.getKeys(false)) {
                flags.put(Integer.parseInt(team), pos(section(flagSection, team)));
            }
        }
        List<Pos> powerups = yaml.getMapList("powerups").stream().map(ArenaStore::pos).toList();
        return new ArenaDefinition(name, world, new HashSet<>(yaml.getStringList("modes")), spawns, flags, powerups,
                optionalPos(yaml, "corner1"), optionalPos(yaml, "corner2"));
    }

    private static Pos optionalPos(YamlConfiguration yaml, String path) {
        return yaml.isConfigurationSection(path) ? pos(section(yaml, path)) : null;
    }

    private static ConfigurationSection section(ConfigurationSection parent, String path) {
        ConfigurationSection s = parent.getConfigurationSection(path);
        if (s == null) {
            throw new IllegalArgumentException(path + " is not a position");
        }
        return s;
    }

    private static Pos pos(ConfigurationSection s) {
        return pos(s.getValues(false));
    }

    private static Pos pos(Map<?, ?> m) {
        return new Pos(number(m, "x"), number(m, "y"), number(m, "z"),
                m.containsKey("yaw") ? (float) number(m, "yaw") : 0f,
                m.containsKey("pitch") ? (float) number(m, "pitch") : 0f);
    }

    private static double number(Map<?, ?> m, String key) {
        if (!(m.get(key) instanceof Number n)) {
            throw new IllegalArgumentException("position without a number for " + key);
        }
        return n.doubleValue();
    }

    private static Map<String, Object> pos(Pos p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("x", p.x());
        m.put("y", p.y());
        m.put("z", p.z());
        m.put("yaw", (double) p.yaw());
        m.put("pitch", (double) p.pitch());
        return m;
    }

    private static Map<String, Object> corner(Pos p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("x", p.x());
        m.put("y", p.y());
        m.put("z", p.z());
        return m;
    }
}
